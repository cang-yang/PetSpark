package com.petspark.service;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.notification.NotificationService;
import com.petspark.user.PhoneCrypto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 服务预约应用服务。覆盖服务项目浏览/详情、资源浏览、窗口查询、创建预约、
 * 我的预约、取消、异常终止、履约流转与后台管理（API-SVC-001~013）。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>创建预约：事务内对 service_slot 行 SELECT ... FOR UPDATE 加行锁，
 *       再用条件 UPDATE（booked_count < capacity）原子增占，确保同窗口不超卖。</li>
 *   <li>取消/异常终止：状态前置（CONFIRMED/IN_PROGRESS）+ 乐观锁 version，
 *       释放窗口 booked_count，写 service_cancellation 轨迹行，通知主人。</li>
 *   <li>履约流转：CONFIRMED→IN_PROGRESS→COMPLETED，仅 service:fulfill/service:manage 角色。</li>
 *   <li>宠物归属校验：若绑 petId，必须为当前用户的宠物，否则 SERVICE_PET_OWNERSHIP_001。</li>
 *   <li>通知走 NotificationService.send 落 outbox，type = {@code SERVICE_<EVENT>}，
 *       scene + event 组合键，不在迁移里插 notification_type 静态行。</li>
 *   <li>手机号用 PhoneCrypto 加密落库，明文仅返回归属用户/管理员。</li>
 * </ul>
 */
@Service
public class ServiceBookingService {

    private static final String MODULE = "service";
    private static final String SCENE = "SERVICE";
    private static final String BIZ_TYPE = "SERVICE";

    private final ServiceBookingRepository repository;
    private final PhoneCrypto phoneCrypto;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public ServiceBookingService(ServiceBookingRepository repository,
            PhoneCrypto phoneCrypto,
            NotificationService notificationService,
            AuditService auditService,
            JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.phoneCrypto = phoneCrypto;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== API-SVC-001~002 服务项目浏览/详情 =====

    /** 浏览服务项目（登录即可，含透明度字段）。 */
    public PageResult<ServiceDtos.ServiceItemView> listItems(ServiceDtos.ServiceItemQuery q) {
        return repository.findItems(q);
    }

    /** 服务项目详情（含规格列表与透明度字段）。 */
    public ServiceDtos.ServiceItemView getItem(String id) {
        ServiceDtos.ServiceItemView view = repository.loadItemView(id);
        if (view == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001);
        }
        return view;
    }

    // ===== API-SVC-003 服务资源浏览 =====

    public PageResult<ServiceDtos.ServiceResourceView> listResources(ServiceDtos.ServiceResourceQuery q) {
        return repository.findResources(q);
    }

    // ===== API-SVC-004 可用窗口查询 =====

    public PageResult<ServiceDtos.ServiceSlotView> listSlots(ServiceDtos.ServiceSlotQuery q) {
        if (!StringUtils.hasText(q.getResourceId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return repository.findSlots(q);
    }

    // ===== API-SVC-005 创建预约 =====

    /**
     * 创建预约：事务内加行锁 + 条件增占窗口容量，落预约头，通知主人。
     * 满员/不可预约返回 SERVICE_SLOT_UNAVAILABLE_001（409）。
     */
    @Transactional
    public ServiceDtos.ServiceBookingView create(ServiceDtos.ServiceBookingCreateRequest req, String userId) {
        // 1. 校验服务项目/资源/窗口存在且可预约
        ServiceRecords.ServiceItemRow item = repository.findItem(req.serviceItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001));
        if (!"ACTIVE".equals(item.status())) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001);
        }
        ServiceRecords.ServiceResourceRow resource = repository.findResource(req.resourceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001));
        if (!"ACTIVE".equals(resource.status())) {
            throw new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001);
        }
        if (!resource.serviceItemId().equals(item.id())) {
            throw new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001);
        }
        // 2. 宠物归属校验
        if (StringUtils.hasText(req.petId())) {
            ensurePetOwnedBy(req.petId(), userId);
        }
        // 3. 规格校验 + 定价
        BigDecimal unitPrice = item.basePrice() == null ? BigDecimal.ZERO : item.basePrice();
        if (StringUtils.hasText(req.specificationId())) {
            ServiceRecords.ServiceSpecificationRow spec = repository.findSpecification(req.specificationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001));
            if (!spec.serviceItemId().equals(item.id())) {
                throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001);
            }
            if (spec.priceDelta() != null) {
                unitPrice = unitPrice.add(spec.priceDelta());
            }
        }
        // 4. 事务内对窗口加行锁 + 条件增占容量
        ServiceRecords.ServiceSlotRow slot = repository.selectSlotForUpdate(req.slotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_SLOT_NOT_FOUND_001));
        if (!"OPEN".equals(slot.status())) {
            throw new BusinessException(ErrorCode.SERVICE_SLOT_UNAVAILABLE_001);
        }
        if (!slot.resourceId().equals(resource.id())) {
            throw new BusinessException(ErrorCode.SERVICE_SLOT_NOT_FOUND_001);
        }
        int reserved = repository.reserveSlot(slot.id(), slot.version());
        if (reserved == 0) {
            throw new BusinessException(ErrorCode.SERVICE_SLOT_UNAVAILABLE_001);
        }
        // 5. 落预约
        String bookingId = UUID.randomUUID().toString();
        String bookingNo = generateBookingNo();
        String phoneCiphertext = phoneCrypto.encrypt(req.customerPhone());
        String kind = item.kind();
        repository.insertBooking(bookingId, bookingNo, userId,
                StringUtils.hasText(req.petId()) ? req.petId() : null,
                item.id(),
                StringUtils.hasText(req.specificationId()) ? req.specificationId() : null,
                resource.id(), slot.id(), kind, "CONFIRMED",
                slot.startAt(), slot.endAt(), unitPrice,
                req.customerName(), phoneCiphertext, req.remark());

        auditService.recordSuccess(audit(userId, "user", "create_booking", bookingId));
        notificationService.send(userId, type("BOOKING_CONFIRMED"), "服务预约已确认",
                "您的服务预约 " + bookingNo + " 已确认", BIZ_TYPE, bookingId);

        ServiceDtos.ServiceBookingView view = repository.loadBookingView(bookingId, true);
        return decryptPhone(view, userId, false);
    }

    // ===== API-SVC-006 我的预约列表 / API-SVC-007 详情 =====

    public PageResult<ServiceDtos.ServiceBookingView> listMy(String userId, ServiceDtos.MyBookingQuery q) {
        PageResult<ServiceDtos.ServiceBookingView> page = repository.findBookingsByUser(userId, q);
        List<ServiceDtos.ServiceBookingView> decrypted = page.getItems().stream()
                .map(v -> decryptPhone(v, userId, false))
                .toList();
        return new PageResult<>(decrypted, page.getPage(), page.getSize(), page.getTotal());
    }

    public ServiceDtos.ServiceBookingView getForUser(String id, String userId, boolean isAdmin) {
        ServiceRecords.ServiceBookingRow row = loadBookingRow(id);
        ensureOwnership(row, userId, isAdmin);
        ServiceDtos.ServiceBookingView view = repository.loadBookingView(id, true);
        return decryptPhone(view, userId, isAdmin);
    }

    // ===== API-SVC-008 取消预约 =====

    @Transactional
    public ServiceDtos.ServiceBookingView cancel(String id, ServiceDtos.ServiceBookingCancelRequest req,
            String userId, boolean isAdmin) {
        ServiceRecords.ServiceBookingRow row = loadBookingRow(id);
        ensureOwnership(row, userId, isAdmin);
        int affected = repository.cancelBooking(id, req.reason(), "CANCELLED", req.version());
        if (affected == 0) {
            ServiceRecords.ServiceBookingRow current = loadBookingRow(id);
            if (!"CONFIRMED".equals(current.status()) && !"IN_PROGRESS".equals(current.status())) {
                throw new BusinessException(ErrorCode.SERVICE_BOOKING_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.SERVICE_BOOKING_STATE_001);
        }
        // 释放窗口容量 + 写取消轨迹
        repository.releaseSlot(row.slotId());
        repository.insertCancellation(UUID.randomUUID().toString(), id, req.reason(), "CANCEL", userId);
        auditService.recordSuccess(audit(userId, isAdmin ? "operator" : "user", "cancel_booking", id));
        notificationService.send(row.userId(), type("BOOKING_CANCELLED"), "服务预约已取消",
                "您的服务预约 " + row.bookingNo() + " 已取消", BIZ_TYPE, id);
        ServiceDtos.ServiceBookingView view = repository.loadBookingView(id, true);
        return decryptPhone(view, userId, isAdmin);
    }

    // ===== API-SVC-009 异常终止 =====

    @Transactional
    public ServiceDtos.ServiceBookingView exception(String id, ServiceDtos.ServiceBookingExceptionRequest req,
            String userId, boolean isAdmin) {
        ServiceRecords.ServiceBookingRow row = loadBookingRow(id);
        ensureOwnership(row, userId, isAdmin);
        int affected = repository.cancelBooking(id, req.note(), "EXCEPTION", req.version());
        if (affected == 0) {
            ServiceRecords.ServiceBookingRow current = loadBookingRow(id);
            if (!"CONFIRMED".equals(current.status()) && !"IN_PROGRESS".equals(current.status())) {
                throw new BusinessException(ErrorCode.SERVICE_BOOKING_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.SERVICE_BOOKING_STATE_001);
        }
        repository.releaseSlot(row.slotId());
        repository.insertCancellation(UUID.randomUUID().toString(), id, req.note(), "EXCEPTION", userId);
        auditService.recordSuccess(audit(userId, isAdmin ? "operator" : "user", "exception_booking", id));
        notificationService.send(row.userId(), type("BOOKING_EXCEPTION"), "服务预约异常终止",
                "您的服务预约 " + row.bookingNo() + " 已异常终止：" + req.note(), BIZ_TYPE, id);
        ServiceDtos.ServiceBookingView view = repository.loadBookingView(id, true);
        return decryptPhone(view, userId, isAdmin);
    }

    // ===== API-SVC-010 履约状态流转（管理员/SERVICE 角色）=====

    @Transactional
    public ServiceDtos.ServiceBookingView transition(String id, ServiceDtos.ServiceBookingTransitionRequest req,
            String operatorId) {
        ServiceRecords.ServiceBookingRow row = loadBookingRow(id);
        int affected;
        String event;
        String title;
        String content;
        if ("IN_PROGRESS".equals(req.status())) {
            affected = repository.startBooking(id, req.note(), req.version());
            event = "BOOKING_STARTED";
            title = "服务预约已开始";
            content = "您的服务预约 " + row.bookingNo() + " 已开始履约";
        } else { // COMPLETED
            affected = repository.completeBooking(id, req.note(), req.version());
            event = "BOOKING_COMPLETED";
            title = "服务预约已完成";
            content = "您的服务预约 " + row.bookingNo() + " 已完成履约";
        }
        if (affected == 0) {
            ServiceRecords.ServiceBookingRow current = loadBookingRow(id);
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.SERVICE_BOOKING_STATE_001);
        }
        auditService.recordSuccess(audit(operatorId, "operator", "transition_booking", id));
        notificationService.send(row.userId(), type(event), title, content, BIZ_TYPE, id);
        ServiceDtos.ServiceBookingView view = repository.loadBookingView(id, true);
        // 履约流转由 operator 发起，仅当 operator 即预约主人时才回解手机号；
        // 管理员视角已通过 isAdmin=true 解密，普通 service 角色不解密他人手机号。
        boolean isAdminView = operatorId.equals(row.userId());
        return decryptPhone(view, operatorId, isAdminView);
    }

    // ===== API-SVC-011~013 后台管理：服务项目/资源/窗口 =====

    @Transactional
    public ServiceDtos.ServiceItemView upsertItem(String id, ServiceDtos.ServiceItemUpsertRequest req, boolean insert) {
        if (insert) {
            String newId = StringUtils.hasText(id) ? id : UUID.randomUUID().toString();
            repository.upsertItem(newId, req, true);
            return repository.loadItemView(newId);
        }
        ServiceRecords.ServiceItemRow existing = repository.findItem(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001));
        repository.upsertItem(id, req, false);
        return repository.loadItemView(id);
    }

    @Transactional
    public int deleteItem(String id) {
        ServiceRecords.ServiceItemRow existing = repository.findItem(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001));
        return repository.deleteItem(id);
    }

    @Transactional
    public ServiceDtos.ServiceResourceView upsertResource(String id, ServiceDtos.ServiceResourceUpsertRequest req, boolean insert) {
        repository.findItem(req.serviceItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001));
        if (insert) {
            String newId = StringUtils.hasText(id) ? id : UUID.randomUUID().toString();
            repository.upsertResource(newId, req, true);
            return repository.loadResourceView(newId);
        }
        repository.findResource(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001));
        repository.upsertResource(id, req, false);
        return repository.loadResourceView(id);
    }

    /** 后台批量创建窗口。 */
    @Transactional
    public List<ServiceDtos.ServiceSlotView> createSlots(ServiceDtos.ServiceSlotCreateRequest req) {
        repository.findResource(req.resourceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001));
        List<ServiceDtos.ServiceSlotView> created = new ArrayList<>();
        List<ServiceDtos.ServiceSlotRange> ranges = req.slots() == null || req.slots().isEmpty()
                ? List.of(new ServiceDtos.ServiceSlotRange(req.startAt(), req.endAt(),
                        req.capacity() == null ? 1 : req.capacity()))
                : req.slots();
        for (ServiceDtos.ServiceSlotRange range : ranges) {
            Instant startAt = parseInstant(range.startAt());
            Instant endAt = parseInstant(range.endAt());
            if (!endAt.isAfter(startAt)) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
            }
            LocalDate slotDate = startAt.atZone(ZoneOffset.UTC).toLocalDate();
            int capacity = range.capacity() == null ? (req.capacity() == null ? 1 : req.capacity()) : range.capacity();
            String slotId = UUID.randomUUID().toString();
            repository.insertSlot(slotId, req.resourceId(), slotDate, startAt, endAt, capacity);
            created.add(repository.loadSlotView(slotId));
        }
        return created;
    }

    // ===== 管理员预约列表 =====

    public PageResult<ServiceDtos.ServiceBookingView> listAdmin(ServiceDtos.AdminBookingQuery q) {
        return repository.findBookingsAdmin(q);
    }

    // ===== 内部辅助 =====

    private ServiceRecords.ServiceBookingRow loadBookingRow(String id) {
        return repository.findBookingById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_BOOKING_NOT_FOUND_001));
    }

    private void ensureOwnership(ServiceRecords.ServiceBookingRow row, String userId, boolean isAdmin) {
        if (!isAdmin && !row.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
    }

    private void ensurePetOwnedBy(String petId, String userId) {
        try {
            String owner = jdbcTemplate.queryForObject(
                    "SELECT owner_user_id FROM pet WHERE id = ? AND deleted_at IS NULL",
                    String.class, petId);
            if (owner == null || !owner.equals(userId)) {
                throw new BusinessException(ErrorCode.SERVICE_PET_OWNERSHIP_001);
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.SERVICE_PET_OWNERSHIP_001);
        }
    }

    /** 解密手机号：仅归属用户或管理员可见，其他视角抹为 null。 */
    private ServiceDtos.ServiceBookingView decryptPhone(ServiceDtos.ServiceBookingView view,
            String actorId, boolean isAdmin) {
        if (view == null) {
            return null;
        }
        boolean canSee = isAdmin || view.userId().equals(actorId);
        if (!canSee) {
            return new ServiceDtos.ServiceBookingView(
                    view.id(), view.bookingNo(), view.userId(), view.petId(),
                    view.serviceItemId(), view.specificationId(), view.resourceId(), view.slotId(),
                    view.kind(), view.status(), view.startAt(), view.endAt(), view.unitPrice(),
                    view.customerName(), null, view.remark(), view.cancelReason(),
                    view.cancelledAt(), view.fulfilledAt(), view.exceptionNote(),
                    view.version(), view.createdAt(), view.serviceItemName(),
                    view.resourceName(), view.specificationName());
        }
        // view.customerPhone 当前是 ciphertext（来自 repository.loadBookingView），此处解密
        String plain = phoneCrypto.decrypt(view.customerPhone());
        return new ServiceDtos.ServiceBookingView(
                view.id(), view.bookingNo(), view.userId(), view.petId(),
                view.serviceItemId(), view.specificationId(), view.resourceId(), view.slotId(),
                view.kind(), view.status(), view.startAt(), view.endAt(), view.unitPrice(),
                view.customerName(), plain, view.remark(), view.cancelReason(),
                view.cancelledAt(), view.fulfilledAt(), view.exceptionNote(),
                view.version(), view.createdAt(), view.serviceItemName(),
                view.resourceName(), view.specificationName());
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
    }

    private String generateBookingNo() {
        long ts = System.currentTimeMillis();
        int rand = UUID.randomUUID().hashCode() & 0xffff;
        return "SVC-" + ts + "-" + String.format("%04x", rand);
    }

    /** 通知 type = SCENE + "_" + event，例如 SERVICE_BOOKING_CONFIRMED。 */
    private String type(String event) {
        return SCENE + "_" + event;
    }

    private AuditContext audit(String actorId, String role, String action, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType("service_booking")
                .objectId(objectId)
                .build();
    }
}
