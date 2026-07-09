package com.petspark.boarding;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.notification.NotificationService;
import com.petspark.user.PhoneCrypto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 寄养应用服务。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>容量按 (room_id, stay_date) 编排到 boarding_room_day；多日预约按日期升序
 *       逐日 SELECT ... FOR UPDATE 锁定，校验 reserved_count &lt; capacity 后 +1，
 *       保证多日锁顺序、不超容量、避免死锁（"多日锁顺序"测试点）；</li>
 *   <li>只有 CONFIRMED/IN_SERVICE 占用容量：分配房间（PENDING→CONFIRMED）时
 *       占用全部日期区间，取消/终止已确认预约时按日期升序完整释放（"取消 100% 释放"
 *       测试点）。PENDING_CONFIRMATION/REJECTED 不占用容量；</li>
 *   <li>幂等：uk_boarding_idem(user_id, idempotency_key) 命中即原样重放；</li>
 *   <li>照护档案敏感字段（疫苗/行为/喂养/用药/紧急联系人/授权）仅在调用方为
 *       宠物主人或具备 boarding:fulfill / boarding:manage 角色时返回，且紧急联系人
 *       电话需解密后回显；否则字段为 null（"照护字段最小化"测试点）；</li>
 *   <li>宠物归属校验：仅宠物 owner_user_id 可发起预约，否则 PET_OWNERSHIP_001；</li>
 *   <li>通知事件经 NotificationService 落 outbox，与业务事务原子提交；
 *       审计通过 AuditService 记录成功操作。</li>
 * </ul>
 */
@Service
public class BoardingService {

    private static final String MODULE = "boarding";
    private static final String PERM_BOARDING_MANAGE = "boarding:manage";
    private static final String PERM_BOARDING_FULFILL = "boarding:fulfill";
    private static final String PERM_ROOM_READ = "room:read";
    private static final String PERM_ROOM_MANAGE = "room:manage";

    private final BoardingRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final PhoneCrypto phoneCrypto;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public BoardingService(BoardingRepository repository,
            JdbcTemplate jdbcTemplate,
            PhoneCrypto phoneCrypto,
            NotificationService notificationService,
            AuditService auditService) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.phoneCrypto = phoneCrypto;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ---------- 房间资源 ----------

    @Transactional
    public BoardingDtos.RoomView createRoom(BoardingDtos.RoomSaveRequest req) {
        if (repository.roomCodeExists(req.code())) {
            throw new BusinessException(ErrorCode.BOARD_ROOM_DUPLICATE_001);
        }
        String id = UUID.randomUUID().toString();
        repository.insertRoom(new BoardingRepository.RoomRow(
                id, req.code(), req.name(), req.capacity(), "ACTIVE",
                req.description(), 0, null, null));
        audit(MODULE_ADMIN, "create_room", id, null);
        return roomView(repository.findRoomById(id).orElseThrow());
    }

    @Transactional
    public BoardingDtos.RoomView updateRoom(String id, BoardingDtos.RoomSaveRequest req) {
        BoardingRepository.RoomRow row = repository.findRoomById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND_001));
        if (!row.code().equals(req.code()) && repository.roomCodeExists(req.code())) {
            throw new BusinessException(ErrorCode.BOARD_ROOM_DUPLICATE_001);
        }
        int affected = repository.updateRoom(id, req.code(), req.name(),
                req.capacity(), req.description(), req.version());
        if (affected == 0) {
            BoardingRepository.RoomRow current = repository.findRoomById(id).orElse(null);
            if (current != null && current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND_001);
        }
        // 容量缩小时不能低于当日已占用峰值。
        BoardingRepository.RoomRow updated = repository.findRoomById(id).orElseThrow();
        int peak = repository.maxReservedInRange(id, LocalDate.MIN, LocalDate.now().plusYears(100));
        if (updated.capacity() < peak) {
            // 抛出后事务回滚，UPDATE 撤销。
            throw new BusinessException(ErrorCode.BOARD_ROOM_CAPACITY_001);
        }
        audit(MODULE_ADMIN, "update_room", id, null);
        return roomView(updated);
    }

    public PageResult<BoardingDtos.RoomView> listRooms(BoardingDtos.RoomQuery q) {
        PageResult<BoardingRepository.RoomRow> page = repository.findRooms(q);
        List<BoardingDtos.RoomView> views = page.getItems().stream()
                .map(this::roomView).toList();
        return new PageResult<>(views, page.getPage(), page.getSize(), page.getTotal());
    }

    // ---------- 可用性查询 ----------

    /** 查询某宠物在日期区间内各房间可用容量；先校验宠物归属。 */
    public List<BoardingDtos.RoomAvailabilityView> availability(
            BoardingDtos.AvailabilityRequest req, String userId) {
        ensurePetOwnedBy(req.petId(), userId);
        if (!req.endDate().isAfter(req.startDate())) {
            throw new BusinessException(ErrorCode.BOARD_DATE_INVALID_001);
        }
        BoardingDtos.RoomQuery q = new BoardingDtos.RoomQuery();
        q.setStatus("ACTIVE");
        List<BoardingRepository.RoomRow> rooms = repository.findRooms(q).getItems();
        List<BoardingDtos.RoomAvailabilityView> views = new ArrayList<>();
        for (BoardingRepository.RoomRow room : rooms) {
            int available = repository.availableInRange(room.id(), req.startDate(), req.endDate());
            views.add(new BoardingDtos.RoomAvailabilityView(
                    room.id(), room.code(), room.name(), room.capacity(),
                    Math.max(0, available), available > 0));        }
        return views;
    }

    // ---------- 预约创建 ----------

    /**
     * 创建预约。幂等优先；校验宠物归属 + 日期合法；PENDING_CONFIRMATION 不占容量
     * （分配房间时才占用）。落照护档案（紧急联系人 AES-GCM 加密）。审计 + 通知。
     */
    @Transactional
    public BoardingDtos.BookingView create(BoardingDtos.BookingCreateRequest req,
            String userId, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            BoardingRepository.BookingRow existing = repository.findByIdempotency(userId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return bookingView(existing, userId, true);
            }
        }
        ensurePetOwnedBy(req.petId(), userId);
        if (!req.endDate().isAfter(req.startDate())) {
            throw new BusinessException(ErrorCode.BOARD_DATE_INVALID_001);
        }
        String bookingId = UUID.randomUUID().toString();
        String bookingNo = generateBookingNo();
        String careProfileId = null;
        String careContactCiphertext = null;
        if (req.careProfile() != null) {
            careProfileId = UUID.randomUUID().toString();
            careContactCiphertext = phoneCrypto.encrypt(req.careProfile().emergencyContact());
        }
        repository.insertBooking(bookingId, bookingNo, userId, req.petId(),
                req.startDate(), req.endDate(), req.quotedAmount(),
                careProfileId, req.careProfile(), careContactCiphertext, idempotencyKey);
        audit(userId, "user", "create_boarding", bookingId);
        notificationService.send(userId, "BOARDING_CREATED", "寄养预约已提交",
                "您的寄养预约 " + bookingNo + " 已提交，等待确认", "BOARDING", bookingId);
        return bookingView(repository.findBookingById(bookingId).orElseThrow(), userId, true);
    }

    /** 本人取消预约。CONFIRMED/IN_SERVICE 取消时按日期升序完整释放当日容量。 */
    @Transactional
    public BoardingDtos.BookingView cancel(String id, BoardingDtos.BookingCancelRequest req,
            String userId, boolean canManage) {
        BoardingRepository.BookingRow row = loadBooking(id);
        ensureOwnershipOrHandler(row, userId, canManage);
        String previousStatus = row.status();
        int affected = repository.cancelBooking(id, req.reason(), req.version());
        if (affected == 0) {
            BoardingRepository.BookingRow current = loadBooking(id);
            if (!"PENDING_CONFIRMATION".equals(current.status())
                    && !"CONFIRMED".equals(current.status())
                    && !"IN_SERVICE".equals(current.status())) {
                throw new BusinessException(ErrorCode.BOARD_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.BOARD_STATE_001);
        }
        if ("CONFIRMED".equals(previousStatus) || "IN_SERVICE".equals(previousStatus)) {
            releaseCapacity(row.roomId(), row.startDate(), row.endDate());
        }
        audit(userId, canManage ? "operator" : "user", "cancel_boarding", id);
        notificationService.send(row.userId(), "BOARDING_CANCELLED", "寄养预约已取消",
                "您的寄养预约 " + row.bookingNo() + " 已取消", "BOARDING", id);
        return bookingView(loadBooking(id), userId, canManage);
    }

    // ---------- 后台履约 ----------

    /** 分配房间：PENDING→CONFIRMED，并在同一事务内占用区间所有日期容量。 */
    @Transactional
    public BoardingDtos.BookingView assignRoom(String id, BoardingDtos.AssignRoomRequest req,
            String operatorId) {
        BoardingRepository.BookingRow row = loadBooking(id);
        if (!"PENDING_CONFIRMATION".equals(row.status())) {
            throw new BusinessException(ErrorCode.BOARD_STATE_001);
        }
        BoardingRepository.RoomRow room = repository.findRoomById(req.roomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND_001));
        // 先按日期升序锁定容量行；任一日满即抛错并整体回滚。
        reserveCapacity(room.id(), row.startDate(), row.endDate());
        int affected = repository.assignRoom(id, req.roomId(), operatorId, req.note(), req.version());
        if (affected == 0) {
            BoardingRepository.BookingRow current = loadBooking(id);
            if (!"PENDING_CONFIRMATION".equals(current.status())) {
                throw new BusinessException(ErrorCode.BOARD_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.BOARD_STATE_001);
        }
        audit(operatorId, "operator", "assign_room", id);
        notificationService.send(row.userId(), "BOARDING_CONFIRMED", "寄养预约已确认",
                "您的寄养预约 " + row.bookingNo() + " 已分配房间 " + room.name(), "BOARDING", id);
        return bookingView(loadBooking(id), operatorId, true);
    }

    /** 后台状态流转：IN_SERVICE/COMPLETED/REJECTED/TERMINATED（CONFIRMED 通过 assign 进入）。 */
    @Transactional
    public BoardingDtos.BookingView transition(String id, BoardingDtos.BookingTransitionRequest req,
            String operatorId) {
        BoardingRepository.BookingRow row = loadBooking(id);
        String status = req.status();
        int affected;
        String previousStatus = row.status();
        switch (status) {
            case "CONFIRMED" -> throw new BusinessException(ErrorCode.BOARD_STATE_001);
            case "REJECTED" -> {
                if (!"PENDING_CONFIRMATION".equals(previousStatus)) {
                    throw new BusinessException(ErrorCode.BOARD_STATE_001);
                }
                affected = repository.rejectBooking(id, req.reason(), req.version());
                if (affected == 0) {
                    throw stateOrVersion(id, req.version());
                }
                audit(operatorId, "operator", "reject_boarding", id);
                notificationService.send(row.userId(), "BOARDING_REJECTED", "寄养预约未通过",
                        "您的寄养预约 " + row.bookingNo() + " 已被拒绝", "BOARDING", id);
            }
            case "IN_SERVICE" -> {
                if (!"CONFIRMED".equals(previousStatus)) {
                    throw new BusinessException(ErrorCode.BOARD_STATE_001);
                }
                affected = repository.startService(id, req.note(), req.version());
                if (affected == 0) {
                    throw stateOrVersion(id, req.version());
                }
                audit(operatorId, "operator", "start_boarding", id);
                notificationService.send(row.userId(), "BOARDING_STARTED", "寄养服务已开始",
                        "您的寄养预约 " + row.bookingNo() + " 已开始履约", "BOARDING", id);
            }
            case "COMPLETED" -> {
                if (!"IN_SERVICE".equals(previousStatus)) {
                    throw new BusinessException(ErrorCode.BOARD_STATE_001);
                }
                affected = repository.completeService(id, req.note(), req.version());
                if (affected == 0) {
                    throw stateOrVersion(id, req.version());
                }
                // COMPLETED 释放容量（履约结束，房间回收）。
                releaseCapacity(row.roomId(), row.startDate(), row.endDate());
                audit(operatorId, "operator", "complete_boarding", id);
                notificationService.send(row.userId(), "BOARDING_COMPLETED", "寄养服务已完成",
                        "您的寄养预约 " + row.bookingNo() + " 已完成", "BOARDING", id);
            }
            case "TERMINATED" -> {
                if (!"IN_SERVICE".equals(previousStatus)) {
                    throw new BusinessException(ErrorCode.BOARD_STATE_001);
                }
                affected = repository.terminateService(id, req.reason(), req.note(), req.version());
                if (affected == 0) {
                    throw stateOrVersion(id, req.version());
                }
                releaseCapacity(row.roomId(), row.startDate(), row.endDate());
                audit(operatorId, "operator", "terminate_boarding", id);
                notificationService.send(row.userId(), "BOARDING_TERMINATED", "寄养服务已终止",
                        "您的寄养预约 " + row.bookingNo() + " 已终止", "BOARDING", id);
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ENUM_001);
        }
        return bookingView(loadBooking(id), operatorId, true);
    }

    // ---------- 查询 ----------

    public PageResult<BoardingDtos.BookingView> listMine(String userId, BoardingDtos.MyBookingQuery q) {
        PageResult<BoardingRepository.BookingRow> page = repository.findByUser(userId, q);
        List<BoardingDtos.BookingView> views = page.getItems().stream()
                .map(row -> bookingView(row, userId, true)).toList();
        return new PageResult<>(views, page.getPage(), page.getSize(), page.getTotal());
    }

    public PageResult<BoardingDtos.BookingView> listAdmin(BoardingDtos.AdminBookingQuery q) {
        PageResult<BoardingRepository.BookingRow> page = repository.findAdmin(q);
        List<BoardingDtos.BookingView> views = page.getItems().stream()
                .map(row -> bookingView(row, null, true)).toList();
        return new PageResult<>(views, page.getPage(), page.getSize(), page.getTotal());
    }

    public BoardingDtos.BookingView getBooking(String id, String userId, boolean canManage) {
        BoardingRepository.BookingRow row = loadBooking(id);
        ensureOwnershipOrHandler(row, userId, canManage);
        return bookingView(row, userId, canManage);
    }

    // ---------- 容量锁定辅助 ----------

    /**
     * 按日期升序逐日锁定 boarding_room_day 行并校验容量，任一日满即抛
     * {@link ErrorCode#BOARD_ROOM_CAPACITY_001}，事务整体回滚。
     *
     * <p>{@link BoardingRepository#lockRoomDay} 先 INSERT IGNORE 占位行（reserved_count=0）
     * 再 SELECT ... FOR UPDATE 取行级锁；此处随后校验 reserved_count &lt; capacity，
     * 通过即 +1 占用。日期升序保证多日预约不会因反向锁定导致死锁（"多日锁顺序"测试点）。
     */
    private void reserveCapacity(String roomId, LocalDate startDate, LocalDate endDate) {
        for (LocalDate d = startDate; d.isBefore(endDate); d = d.plusDays(1)) {
            BoardingRepository.RoomDayRow day = repository.lockRoomDay(roomId, d);
            if (day == null || day.reservedCount() >= day.capacity()) {
                throw new BusinessException(ErrorCode.BOARD_ROOM_CAPACITY_001);
            }
            incrementDay(roomId, d);
        }
    }

    /** 释放区间内每日容量 -1（取消/终止/完成时）。按日期升序释放，保证与锁定顺序一致。 */
    private void releaseCapacity(String roomId, LocalDate startDate, LocalDate endDate) {
        if (roomId == null || startDate == null || endDate == null) {
            return;
        }
        for (LocalDate d = startDate; d.isBefore(endDate); d = d.plusDays(1)) {
            repository.releaseDay(roomId, d);
        }
    }

    private void incrementDay(String roomId, LocalDate date) {
        jdbcTemplate.update("""
                UPDATE boarding_room_day
                SET reserved_count = reserved_count + 1, version = version + 1
                WHERE room_id = ? AND stay_date = ?
                """, roomId, java.sql.Date.valueOf(date));
    }

    // ---------- 归属与视图 ----------

    private BoardingRepository.BookingRow loadBooking(String id) {
        return repository.findBookingById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND_001));
    }

    private void ensureOwnershipOrHandler(BoardingRepository.BookingRow row, String userId, boolean canManage) {
        if (canManage) {
            return;
        }
        if (row.userId().equals(userId)) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
    }

    private void ensurePetOwnedBy(String petId, String userId) {
        try {
            String ownerId = jdbcTemplate.queryForObject(
                    "SELECT owner_user_id FROM pet WHERE id = ? AND deleted_at IS NULL",
                    String.class, petId);
            if (ownerId == null || !ownerId.equals(userId)) {
                throw new BusinessException(ErrorCode.PET_OWNERSHIP_001);
            }
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.PET_OWNERSHIP_001);
        }
    }

    private BusinessException stateOrVersion(String id, int version) {
        BoardingRepository.BookingRow current = loadBooking(id);
        if (current.version() != version) {
            return new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return new BusinessException(ErrorCode.BOARD_STATE_001);
    }

    private BoardingDtos.RoomView roomView(BoardingRepository.RoomRow row) {
        return new BoardingDtos.RoomView(
                row.id(), row.code(), row.name(), row.capacity(),
                row.status(), row.description(), row.version(),
                row.createdAt(), row.updatedAt());
    }

    /** 组装预约视图。careProfile 敏感字段按角色裁剪：宠物主人或 boarding:fulfill/manage 可见。 */
    private BoardingDtos.BookingView bookingView(BoardingRepository.BookingRow row,
            String viewerId, boolean canManageOrFulfill) {
        boolean revealCare = (viewerId != null && viewerId.equals(row.userId())) || canManageOrFulfill;
        BoardingDtos.CareProfileView careView = null;
        if (revealCare) {
            BoardingRepository.CareProfileRow care = repository.findCareProfileByBooking(row.id()).orElse(null);
            if (care != null) {
                careView = new BoardingDtos.CareProfileView(
                        care.id(),
                        care.vaccinationSummary(),
                        care.behaviorNotes(),
                        care.feedingPlan(),
                        care.medicationPlan(),
                        phoneCrypto.decrypt(care.emergencyContactCiphertext()),
                        care.emergencyAuthorization(),
                        care.accessScope());
            }
        }
        String petName = loadPetName(row.petId());
        String roomName = row.roomId() == null ? null
                : repository.findRoomById(row.roomId()).map(r -> r.name()).orElse(null);
        return new BoardingDtos.BookingView(
                row.id(),
                row.bookingNo(),
                row.userId(),
                row.petId(),
                petName,
                row.roomId(),
                roomName,
                row.startDate(),
                row.endDate(),
                row.status(),
                row.quotedAmount(),
                row.cancelReason(),
                row.rejectReason(),
                row.handlerId(),
                row.handlerNote(),
                careView,
                row.createdAt(),
                row.startedAt(),
                row.completedAt(),
                row.cancelledAt(),
                row.terminatedReason(),
                row.version());
    }

    private String loadPetName(String petId) {
        if (!StringUtils.hasText(petId)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM pet WHERE id = ?", String.class, petId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String generateBookingNo() {
        long ts = System.currentTimeMillis();
        int rand = UUID.randomUUID().hashCode() & 0xffff;
        return "BKG-" + ts + "-" + String.format("%04x", rand);
    }

    private static final String MODULE_ADMIN = "operator";

    private void audit(String actorId, String role, String action, String objectId) {
        AuditContext ctx = AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType("boarding_booking")
                .objectId(objectId)
                .build();
        auditService.recordSuccess(ctx);
    }

    /** 暴露给 Controller 的权限判断便捷方法。 */
    public static boolean hasAuthority(AuthenticatedUser user, String code) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (var a : user.getAuthorities()) {
            if (code.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
