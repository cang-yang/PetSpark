package com.petspark.service;

import com.petspark.common.api.PageResult;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 服务预约持久化仓储。基于 JdbcTemplate，与 order/pet 模块同风格。
 *
 * <p>关键并发控制：
 * <ul>
 *   <li>预约：{@link #reserveSlot} 对 service_slot 行做条件 UPDATE
 *       （{@code booked_count < capacity AND status='OPEN'}），原子增占，返回 0 即满员/不可预约。
 *       调用方在事务内先 {@link #selectSlotForUpdate} 加行锁再 reserveSlot，串行化同窗口并发。</li>
 *   <li>取消/异常终止：{@link #releaseSlot} 原子回退 booked_count，释放窗口（容量可被后续预约复用）。</li>
 *   <li>状态流转：UPDATE WHERE 携带状态前置 + 乐观锁 version，确保状态机合法。</li>
 * </ul>
 */
@Repository
public class ServiceBookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceBookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== Service Item =====

    /** 浏览服务项目（ACTIVE 优先，支持 kind/status/keyword 过滤 + 分页）。 */
    public PageResult<ServiceDtos.ServiceItemView> findItems(ServiceDtos.ServiceItemQuery q) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getKind())) {
            where.append(" AND kind = ? ");
            args.add(q.getKind().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND (name LIKE ? OR code LIKE ?) ");
            String kw = "%" + q.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_item" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<ServiceDtos.ServiceItemView> items = jdbcTemplate.query(
                "SELECT id FROM service_item %s ORDER BY status DESC, created_at DESC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadItemView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 加载服务项目原始行。 */
    public Optional<ServiceRecords.ServiceItemRow> findItem(String id) {
        return jdbcTemplate.query("""
                SELECT id, kind, code, name, description, qualification, availability_note,
                       exception_rule, base_price, status
                FROM service_item
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new ServiceRecords.ServiceItemRow(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("qualification"),
                rs.getString("availability_note"),
                rs.getString("exception_rule"),
                rs.getBigDecimal("base_price"),
                rs.getString("status"))) : Optional.empty(), id);
    }

    /** 加载服务项目视图（含规格列表）。 */
    public ServiceDtos.ServiceItemView loadItemView(String id) {
        return findItem(id).map(row -> new ServiceDtos.ServiceItemView(
                row.id(), row.kind(), row.code(), row.name(), row.description(),
                row.qualification(), row.availabilityNote(), row.exceptionRule(),
                row.basePrice(), row.status(),
                findSpecifications(id).stream()
                        .map(s -> new ServiceDtos.ServiceSpecificationView(
                                s.id(), s.name(), s.priceDelta(), s.sortOrder(), s.status()))
                        .toList()))
                .orElse(null);
    }

    /** 加载服务项目规格列表。 */
    public List<ServiceRecords.ServiceSpecificationRow> findSpecifications(String itemId) {
        return jdbcTemplate.query("""
                SELECT id, service_item_id, name, price_delta, sort_order, status
                FROM service_specification
                WHERE service_item_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """, (rs, rowNum) -> new ServiceRecords.ServiceSpecificationRow(
                rs.getString("id"),
                rs.getString("service_item_id"),
                rs.getString("name"),
                rs.getBigDecimal("price_delta"),
                rs.getInt("sort_order"),
                rs.getString("status")), itemId);
    }

    /** 按 id 查规格，返回价格增量用于预约定价。 */
    public Optional<ServiceRecords.ServiceSpecificationRow> findSpecification(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, service_item_id, name, price_delta, sort_order, status
                FROM service_specification
                WHERE id = ? AND status = 'ACTIVE'
                """, rs -> rs.next() ? Optional.of(new ServiceRecords.ServiceSpecificationRow(
                rs.getString("id"),
                rs.getString("service_item_id"),
                rs.getString("name"),
                rs.getBigDecimal("price_delta"),
                rs.getInt("sort_order"),
                rs.getString("status"))) : Optional.empty(), id);
    }

    /** 管理端新增/更新服务项目。 */
    public void upsertItem(String id, ServiceDtos.ServiceItemUpsertRequest req, boolean insert) {
        String kind = StringUtils.hasText(req.kind()) ? req.kind() : "GENERIC";
        String status = StringUtils.hasText(req.status()) ? req.status() : "ACTIVE";
        BigDecimal price = req.basePrice() == null ? BigDecimal.ZERO : req.basePrice();
        if (insert) {
            jdbcTemplate.update("""
                    INSERT INTO service_item
                        (id, kind, code, name, description, qualification, availability_note,
                         exception_rule, base_price, status, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, id, kind, req.code(), req.name(), req.description(),
                    req.qualification(), req.availabilityNote(), req.exceptionRule(),
                    price, status);
        } else {
            jdbcTemplate.update("""
                    UPDATE service_item
                    SET kind = ?, code = ?, name = ?, description = ?, qualification = ?,
                        availability_note = ?, exception_rule = ?, base_price = ?, status = ?,
                        version = version + 1
                    WHERE id = ? AND deleted_at IS NULL
                    """, kind, req.code(), req.name(), req.description(),
                    req.qualification(), req.availabilityNote(), req.exceptionRule(),
                    price, status, id);
        }
    }

    /** 软删服务项目。 */
    public int deleteItem(String id) {
        return jdbcTemplate.update(
                "UPDATE service_item SET deleted_at = CURRENT_TIMESTAMP(3), status = 'INACTIVE', version = version + 1 "
                        + "WHERE id = ? AND deleted_at IS NULL", id);
    }

    // ===== Service Resource =====

    /** 浏览服务资源（按服务项目 + 状态过滤 + 分页）。 */
    public PageResult<ServiceDtos.ServiceResourceView> findResources(ServiceDtos.ServiceResourceQuery q) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getServiceItemId())) {
            where.append(" AND service_item_id = ? ");
            args.add(q.getServiceItemId().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_resource" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<ServiceDtos.ServiceResourceView> items = jdbcTemplate.query(
                "SELECT id FROM service_resource %s ORDER BY status DESC, created_at DESC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadResourceView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 加载服务资源原始行。 */
    public Optional<ServiceRecords.ServiceResourceRow> findResource(String id) {
        return jdbcTemplate.query("""
                SELECT id, service_item_id, name, qualification, availability_note,
                       exception_rule, status, capacity
                FROM service_resource
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new ServiceRecords.ServiceResourceRow(
                rs.getString("id"),
                rs.getString("service_item_id"),
                rs.getString("name"),
                rs.getString("qualification"),
                rs.getString("availability_note"),
                rs.getString("exception_rule"),
                rs.getString("status"),
                rs.getInt("capacity"))) : Optional.empty(), id);
    }

    /** 加载资源视图。 */
    public ServiceDtos.ServiceResourceView loadResourceView(String id) {
        return findResource(id).map(row -> new ServiceDtos.ServiceResourceView(
                row.id(), row.serviceItemId(), row.name(), row.qualification(),
                row.availabilityNote(), row.exceptionRule(), row.status(), row.capacity()))
                .orElse(null);
    }

    /** 新增/更新服务资源。 */
    public void upsertResource(String id, ServiceDtos.ServiceResourceUpsertRequest req, boolean insert) {
        String status = StringUtils.hasText(req.status()) ? req.status() : "ACTIVE";
        int capacity = req.capacity() == null ? 1 : req.capacity();
        if (insert) {
            jdbcTemplate.update("""
                    INSERT INTO service_resource
                        (id, service_item_id, name, qualification, availability_note,
                         exception_rule, status, capacity, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, id, req.serviceItemId(), req.name(), req.qualification(),
                    req.availabilityNote(), req.exceptionRule(), status, capacity);
        } else {
            jdbcTemplate.update("""
                    UPDATE service_resource
                    SET service_item_id = ?, name = ?, qualification = ?, availability_note = ?,
                        exception_rule = ?, status = ?, capacity = ?, version = version + 1
                    WHERE id = ? AND deleted_at IS NULL
                    """, req.serviceItemId(), req.name(), req.qualification(),
                    req.availabilityNote(), req.exceptionRule(), status, capacity, id);
        }
    }

    // ===== Service Slot =====

    /** 查询可用窗口（OPEN 优先，按时间升序）。 */
    public PageResult<ServiceDtos.ServiceSlotView> findSlots(ServiceDtos.ServiceSlotQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getResourceId())) {
            where.append(" AND resource_id = ? ");
            args.add(q.getResourceId().trim());
        }
        if (StringUtils.hasText(q.getSlotDate())) {
            where.append(" AND slot_date = ? ");
            args.add(java.sql.Date.valueOf(q.getSlotDate().trim()));
        }
        where.append(" AND status = 'OPEN' ");
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_slot" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<ServiceDtos.ServiceSlotView> items = jdbcTemplate.query(
                "SELECT id FROM service_slot %s ORDER BY start_at ASC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadSlotView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 加载窗口视图。 */
    public ServiceDtos.ServiceSlotView loadSlotView(String id) {
        return findSlotById(id).map(row -> new ServiceDtos.ServiceSlotView(
                row.id(), row.resourceId(),
                row.slotDate() == null ? null : row.slotDate().toString(),
                row.startAt(), row.endAt(), row.capacity(), row.bookedCount(), row.status()))
                .orElse(null);
    }

    /** 按 id 加载窗口原始行（无锁）。 */
    public Optional<ServiceRecords.ServiceSlotRow> findSlotById(String id) {
        return jdbcTemplate.query("""
                SELECT id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status, version
                FROM service_slot
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapSlot(rs)) : Optional.empty(), id);
    }

    /**
     * 事务内对窗口行加排他锁（SELECT ... FOR UPDATE），串行化同窗口并发预约。
     * 必须在开启事务的上下文内调用。
     */
    public Optional<ServiceRecords.ServiceSlotRow> selectSlotForUpdate(String id) {
        return jdbcTemplate.query("""
                SELECT id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status, version
                FROM service_slot
                WHERE id = ?
                FOR UPDATE
                """, rs -> rs.next() ? Optional.of(mapSlot(rs)) : Optional.empty(), id);
    }

    /**
     * 原子增占窗口容量：条件 UPDATE（OPEN + booked_count < capacity），
     * 返回影响行数（0 = 满员或不可预约）。配合 {@link #selectSlotForUpdate} 行锁串行化。
     */
    public int reserveSlot(String slotId, int version) {
        return jdbcTemplate.update("""
                UPDATE service_slot
                SET booked_count = booked_count + 1, version = version + 1
                WHERE id = ? AND version = ? AND status = 'OPEN' AND booked_count < capacity
                """, slotId, version);
    }

    /** 原子释放窗口容量（取消/异常终止时回退 booked_count）。 */
    public int releaseSlot(String slotId) {
        return jdbcTemplate.update("""
                UPDATE service_slot
                SET booked_count = booked_count - 1, version = version + 1
                WHERE id = ? AND booked_count > 0
                """, slotId);
    }

    /** 新增窗口。 */
    public void insertSlot(String id, String resourceId, LocalDate slotDate,
            Instant startAt, Instant endAt, int capacity) {
        jdbcTemplate.update("""
                INSERT INTO service_slot
                    (id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'OPEN', 0)
                """, id, resourceId, java.sql.Date.valueOf(slotDate),
                Timestamp.from(startAt), Timestamp.from(endAt), capacity);
    }

    private ServiceRecords.ServiceSlotRow mapSlot(ResultSet rs) throws SQLException {
        java.sql.Date d = rs.getDate("slot_date");
        Timestamp start = rs.getTimestamp("start_at");
        Timestamp end = rs.getTimestamp("end_at");
        return new ServiceRecords.ServiceSlotRow(
                rs.getString("id"),
                rs.getString("resource_id"),
                d == null ? null : d.toLocalDate(),
                start == null ? null : start.toInstant(),
                end == null ? null : end.toInstant(),
                rs.getInt("capacity"),
                rs.getInt("booked_count"),
                rs.getString("status"),
                rs.getInt("version"));
    }

    // ===== Service Booking =====

    /** 插入预约头。 */
    public void insertBooking(String id, String bookingNo, String userId, String petId,
            String serviceItemId, String specificationId, String resourceId, String slotId,
            String kind, String status, Instant startAt, Instant endAt, BigDecimal unitPrice,
            String customerName, String phoneCiphertext, String remark) {
        jdbcTemplate.update("""
                INSERT INTO service_booking
                    (id, booking_no, user_id, pet_id, service_item_id, specification_id,
                     resource_id, slot_id, kind, status, start_at, end_at, unit_price,
                     customer_name, customer_phone_ciphertext, remark, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, bookingNo, userId, petId, serviceItemId, specificationId,
                resourceId, slotId, kind, status, Timestamp.from(startAt), Timestamp.from(endAt),
                unitPrice, customerName, phoneCiphertext, remark);
    }

    /** 按 id 加载预约原始行（ciphertext 未解密）。 */
    public Optional<ServiceRecords.ServiceBookingRow> findBookingById(String id) {
        return jdbcTemplate.query("""
                SELECT id, booking_no, user_id, pet_id, service_item_id, specification_id,
                       resource_id, slot_id, kind, status, start_at, end_at, unit_price,
                       customer_name, customer_phone_ciphertext, remark, cancel_reason,
                       cancelled_at, fulfilled_at, exception_note, version, created_at
                FROM service_booking
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapBooking(rs)) : Optional.empty(), id);
    }

    /** 用户预约列表（状态过滤 + 分页，按 created_at DESC）。 */
    public PageResult<ServiceDtos.ServiceBookingView> findBookingsByUser(String userId, ServiceDtos.MyBookingQuery q) {
        StringBuilder where = new StringBuilder(" WHERE user_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKind())) {
            where.append(" AND kind = ? ");
            args.add(q.getKind().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_booking" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<ServiceDtos.ServiceBookingView> items = jdbcTemplate.query(
                "SELECT id FROM service_booking %s ORDER BY created_at DESC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadBookingView(rs.getString("id"), false), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 管理员预约列表（状态 + 订单号模糊 + 分页）。 */
    public PageResult<ServiceDtos.ServiceBookingView> findBookingsAdmin(ServiceDtos.AdminBookingQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKind())) {
            where.append(" AND kind = ? ");
            args.add(q.getKind().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND booking_no LIKE ? ");
            args.add("%" + q.getKeyword().trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_booking" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<ServiceDtos.ServiceBookingView> items = jdbcTemplate.query(
                "SELECT id FROM service_booking %s ORDER BY created_at DESC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadBookingView(rs.getString("id"), true), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 加载预约视图（含服务项目/资源/规格名称快照）。 */
    public ServiceDtos.ServiceBookingView loadBookingView(String id, boolean admin) {
        return findBookingById(id).map(row -> {
            ServiceRecords.ServiceItemRow item = findItem(row.serviceItemId()).orElse(null);
            ServiceRecords.ServiceResourceRow res = findResource(row.resourceId()).orElse(null);
            ServiceRecords.ServiceSpecificationRow spec = findSpecification(row.specificationId()).orElse(null);
            return new ServiceDtos.ServiceBookingView(
                    row.id(), row.bookingNo(), row.userId(), row.petId(),
                    row.serviceItemId(), row.specificationId(), row.resourceId(), row.slotId(),
                    row.kind(), row.status(), row.startAt(), row.endAt(), row.unitPrice(),
                    row.customerName(),
                    // ciphertext 原样返回，由 service 层在归属/管理员视角下解密
                    row.customerPhoneCiphertext(),
                    row.remark(), row.cancelReason(), row.cancelledAt(), row.fulfilledAt(),
                    row.exceptionNote(), row.version(), row.createdAt(),
                    item == null ? null : item.name(),
                    res == null ? null : res.name(),
                    spec == null ? null : spec.name());
        }).orElse(null);
    }

    /** 取消/异常终止：状态前置 + 乐观锁。 */
    public int cancelBooking(String id, String reason, String targetStatus, int version) {
        if ("CANCELLED".equals(targetStatus)) {
            return jdbcTemplate.update("""
                    UPDATE service_booking
                    SET status = 'CANCELLED', cancel_reason = ?, cancelled_at = CURRENT_TIMESTAMP(3),
                        version = version + 1
                    WHERE id = ? AND version = ? AND status IN ('CONFIRMED','IN_PROGRESS')
                    """, reason, id, version);
        }
        // EXCEPTION
        return jdbcTemplate.update("""
                UPDATE service_booking
                SET status = 'EXCEPTION', exception_note = ?, cancelled_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND status IN ('CONFIRMED','IN_PROGRESS')
                """, reason, id, version);
    }

    /** 履约开始：CONFIRMED → IN_PROGRESS。 */
    public int startBooking(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE service_booking
                SET status = 'IN_PROGRESS', exception_note = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'CONFIRMED'
                """, note, id, version);
    }

    /** 履约完成：IN_PROGRESS → COMPLETED，落 fulfilled_at。 */
    public int completeBooking(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE service_booking
                SET status = 'COMPLETED', exception_note = ?, fulfilled_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND status = 'IN_PROGRESS'
                """, note, id, version);
    }

    /** 写取消/异常轨迹行。 */
    public void insertCancellation(String id, String bookingId, String reason, String type, String operatorId) {
        jdbcTemplate.update("""
                INSERT INTO service_cancellation (id, booking_id, reason, cancellation_type, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, id, bookingId, reason, type, operatorId);
    }

    private ServiceRecords.ServiceBookingRow mapBooking(ResultSet rs) throws SQLException {
        Timestamp start = rs.getTimestamp("start_at");
        Timestamp end = rs.getTimestamp("end_at");
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        Timestamp fulfilled = rs.getTimestamp("fulfilled_at");
        Timestamp created = rs.getTimestamp("created_at");
        return new ServiceRecords.ServiceBookingRow(
                rs.getString("id"),
                rs.getString("booking_no"),
                rs.getString("user_id"),
                rs.getString("pet_id"),
                rs.getString("service_item_id"),
                rs.getString("specification_id"),
                rs.getString("resource_id"),
                rs.getString("slot_id"),
                rs.getString("kind"),
                rs.getString("status"),
                start == null ? null : start.toInstant(),
                end == null ? null : end.toInstant(),
                rs.getBigDecimal("unit_price"),
                rs.getString("customer_name"),
                rs.getString("customer_phone_ciphertext"),
                rs.getString("remark"),
                rs.getString("cancel_reason"),
                cancelled == null ? null : cancelled.toInstant(),
                fulfilled == null ? null : fulfilled.toInstant(),
                rs.getString("exception_note"),
                rs.getInt("version"),
                created == null ? null : created.toInstant());
    }
}
