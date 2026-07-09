package com.petspark.boarding;

import com.petspark.common.api.PageResult;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 寄养持久化仓储。基于 JdbcTemplate，与 order/health 模块同风格。
 *
 * <p>关键点：
 * <ul>
 *   <li>容量按 (room_id, stay_date) 编排到 {@code boarding_room_day}，唯一索引
 *       {@code uk_room_day}；预约按日期升序 SELECT ... FOR UPDATE 锁定容量行，
 *       校验 {@code reserved_count < capacity} 后 +1，保证多日锁顺序、不超容量；</li>
 *   <li>取消释放：CONFIRMED/IN_SERVICE 状态的预约取消或终止时按日期顺序 -1；</li>
 *   <li>预约状态机 SQL 内置状态前置条件，配合乐观锁 version；</li>
 *   <li>幂等：{@code uk_boarding_idem(user_id, idempotency_key)} 唯一索引，NULL key 多行兼容。</li>
 * </ul>
 */
@Repository
public class BoardingRepository {

    private final JdbcTemplate jdbcTemplate;

    public BoardingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- 房间资源 ----------

    /** 房间行。 */
    public record RoomRow(
            String id,
            String code,
            String name,
            int capacity,
            String status,
            String description,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public Optional<RoomRow> findRoomById(String id) {
        return jdbcTemplate.query("""
                SELECT id, code, name, capacity, status, description, version, created_at, updated_at
                FROM boarding_room
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapRoom(rs)) : Optional.empty(), id);
    }

    public boolean roomCodeExists(String code) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM boarding_room WHERE code = ? AND deleted_at IS NULL",
                Long.class, code);
        return count != null && count > 0;
    }

    public void insertRoom(RoomRow row) {
        jdbcTemplate.update("""
                INSERT INTO boarding_room (id, code, name, capacity, status, description, version)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, row.id(), row.code(), row.name(), row.capacity(),
                row.status() == null ? "ACTIVE" : row.status(),
                blankToNull(row.description()));
    }

    public int updateRoom(String id, String code, String name, int capacity,
            String description, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_room
                SET code = ?, name = ?, capacity = ?, description = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, code, name, capacity, blankToNull(description), id, version);
    }

    public PageResult<RoomRow> findRooms(BoardingDtos.RoomQuery q) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND (code LIKE ? OR name LIKE ?) ");
            String kw = "%" + q.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM boarding_room" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<RoomRow> items = jdbcTemplate.query("""
                SELECT id, code, name, capacity, status, description, version, created_at, updated_at
                FROM boarding_room %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> mapRoom(rs), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    private RoomRow mapRoom(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new RoomRow(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("capacity"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getInt("version"),
                created == null ? null : created.toInstant(),
                updated == null ? null : updated.toInstant());
    }

    // ---------- 日期容量 ----------

    /** 房间+日期容量行。 */
    public record RoomDayRow(
            String id,
            String roomId,
            LocalDate stayDate,
            int reservedCount,
            int version,
            int capacity) {
    }

    /**
     * 锁定房间+日期容量行（FOR UPDATE），不存在则插入占位行后再锁。返回该日已占用数与容量上限。
     * 调用方需在同一事务内调用，按日期升序逐日锁定，保证多日锁顺序、避免死锁。
     */
    public RoomDayRow lockRoomDay(String roomId, LocalDate date) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO boarding_room_day (id, room_id, stay_date, reserved_count, version)
                VALUES (?, ?, ?, 0, 0)
                """, UUID.randomUUID().toString(), roomId, Date.valueOf(date));
        return jdbcTemplate.query("""
                SELECT d.id, d.room_id, d.stay_date, d.reserved_count, d.version, r.capacity
                FROM boarding_room_day d
                JOIN boarding_room r ON r.id = d.room_id
                WHERE d.room_id = ? AND d.stay_date = ?
                FOR UPDATE
                """, rs -> rs.next() ? new RoomDayRow(
                        rs.getString("id"),
                        rs.getString("room_id"),
                        rs.getDate("stay_date").toLocalDate(),
                        rs.getInt("reserved_count"),
                        rs.getInt("version"),
                        rs.getInt("capacity")) : null, roomId, Date.valueOf(date));
    }

    /** 释放当日 -1（取消/终止已确认预约时）。 */
    public void releaseDay(String roomId, LocalDate date) {
        jdbcTemplate.update("""
                UPDATE boarding_room_day
                SET reserved_count = reserved_count - 1, version = version + 1
                WHERE room_id = ? AND stay_date = ? AND reserved_count > 0
                """, roomId, Date.valueOf(date));
    }

    /** 某房间在某日期区间内每日最大已占用数。 */
    public int maxReservedInRange(String roomId, LocalDate startDate, LocalDate endDate) {
        Integer max = jdbcTemplate.query("""
                SELECT COALESCE(MAX(d.reserved_count), 0) AS m
                FROM boarding_room_day d
                WHERE d.room_id = ? AND d.stay_date >= ? AND d.stay_date < ?
                """, rs -> rs.next() ? rs.getInt("m") : 0, roomId, Date.valueOf(startDate), Date.valueOf(endDate));
        return max == null ? 0 : max;
    }

    /** 房间某日期区间内的可用容量（capacity - 区间内每日最大占用）。 */
    public int availableInRange(String roomId, LocalDate startDate, LocalDate endDate) {
        Integer capacity = jdbcTemplate.queryForObject(
                "SELECT capacity FROM boarding_room WHERE id = ? AND deleted_at IS NULL",
                Integer.class, roomId);
        if (capacity == null) {
            return 0;
        }
        return capacity - maxReservedInRange(roomId, startDate, endDate);
    }

    // ---------- 预约 ----------

    /** 预约原始行。 */
    public record BookingRow(
            String id,
            String bookingNo,
            String userId,
            String petId,
            String roomId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String careProfileId,
            BigDecimal quotedAmount,
            String cancelReason,
            String rejectReason,
            String handlerId,
            String handlerNote,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            String terminatedReason,
            int version) {
    }

    public Optional<BookingRow> findBookingById(String id) {
        return jdbcTemplate.query("""
                SELECT id, booking_no, user_id, pet_id, room_id, start_date, end_date, status,
                       care_profile_id, quoted_amount, cancel_reason, reject_reason,
                       handler_id, handler_note, started_at, completed_at, cancelled_at,
                       terminated_reason, version, created_at
                FROM boarding_booking
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapBooking(rs)) : Optional.empty(), id);
    }

    public Optional<BookingRow> findByIdempotency(String userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, booking_no, user_id, pet_id, room_id, start_date, end_date, status,
                       care_profile_id, quoted_amount, cancel_reason, reject_reason,
                       handler_id, handler_note, started_at, completed_at, cancelled_at,
                       terminated_reason, version, created_at
                FROM boarding_booking
                WHERE user_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(mapBooking(rs)) : Optional.empty(), userId, idempotencyKey);
    }

    /** 插入预约（无房间，状态 PENDING_CONFIRMATION）+ 可选照护档案。 */
    public void insertBooking(String bookingId, String bookingNo, String userId, String petId,
            LocalDate startDate, LocalDate endDate, BigDecimal quotedAmount,
            String careProfileId, BoardingDtos.CareProfileRequest care,
            String careContactCiphertext, String idempotencyKey) {
        jdbcTemplate.update("""
                INSERT INTO boarding_booking
                    (id, booking_no, user_id, pet_id, room_id, start_date, end_date, status,
                     care_profile_id, quoted_amount, idempotency_key, version)
                VALUES (?, ?, ?, ?, NULL, ?, ?, 'PENDING_CONFIRMATION', ?, ?, ?, 0)
                """, bookingId, bookingNo, userId, petId,
                Date.valueOf(startDate), Date.valueOf(endDate),
                blankToNull(careProfileId), quotedAmount,
                StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        if (careProfileId != null && care != null) {
            jdbcTemplate.update("""
                    INSERT INTO boarding_care_profile
                        (id, booking_id, vaccination_summary, behavior_notes, feeding_plan,
                         medication_plan, emergency_contact_ciphertext, emergency_authorization, access_scope)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'HANDLER')
                    """, careProfileId, bookingId,
                    blankToNull(care.vaccinationSummary()),
                    blankToNull(care.behaviorNotes()),
                    blankToNull(care.feedingPlan()),
                    blankToNull(care.medicationPlan()),
                    blankToNull(careContactCiphertext),
                    blankToNull(care.emergencyAuthorization()));
        }
    }

    /** 取消预约（仅 PENDING_CONFIRMATION/CONFIRMED/IN_SERVICE 可取消）。 */
    public int cancelBooking(String id, String reason, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'CANCELLED', cancel_reason = ?, cancelled_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'IN_SERVICE')
                """, reason, id, version);
    }

    /** 分配房间：PENDING_CONFIRMATION → CONFIRMED。 */
    public int assignRoom(String id, String roomId, String handlerId, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'CONFIRMED', room_id = ?, handler_id = ?, handler_note = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'PENDING_CONFIRMATION'
                """, roomId, handlerId, blankToNull(note), id, version);
    }

    /** 拒绝预约：PENDING_CONFIRMATION → REJECTED。 */
    public int rejectBooking(String id, String reason, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'REJECTED', reject_reason = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'PENDING_CONFIRMATION'
                """, reason, id, version);
    }

    /** CONFIRMED → IN_SERVICE，落 started_at。 */
    public int startService(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'IN_SERVICE', handler_note = ?, started_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND status = 'CONFIRMED'
                """, blankToNull(note), id, version);
    }

    /** IN_SERVICE → COMPLETED，落 completed_at。 */
    public int completeService(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'COMPLETED', handler_note = ?, completed_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND status = 'IN_SERVICE'
                """, blankToNull(note), id, version);
    }

    /** IN_SERVICE → TERMINATED，落 reason。 */
    public int terminateService(String id, String reason, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE boarding_booking
                SET status = 'TERMINATED', terminated_reason = ?, handler_note = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'IN_SERVICE'
                """, reason, blankToNull(note), id, version);
    }

    /** 本人预约列表（状态过滤 + 分页，按 created_at DESC）。 */
    public PageResult<BookingRow> findByUser(String userId, BoardingDtos.MyBookingQuery q) {
        StringBuilder where = new StringBuilder(" WHERE user_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM boarding_booking" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<BookingRow> items = jdbcTemplate.query(bookingSelect(where), (rs, rowNum) -> mapBooking(rs), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 后台预约列表（状态 + 房间 + 预约号模糊 + 分页）。 */
    public PageResult<BookingRow> findAdmin(BoardingDtos.AdminBookingQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getRoomId())) {
            where.append(" AND room_id = ? ");
            args.add(q.getRoomId().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND booking_no LIKE ? ");
            args.add("%" + q.getKeyword().trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM boarding_booking" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<BookingRow> items = jdbcTemplate.query(bookingSelect(where), (rs, rowNum) -> mapBooking(rs), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    private String bookingSelect(StringBuilder where) {
        return """
                SELECT id, booking_no, user_id, pet_id, room_id, start_date, end_date, status,
                       care_profile_id, quoted_amount, cancel_reason, reject_reason,
                       handler_id, handler_note, started_at, completed_at, cancelled_at,
                       terminated_reason, version, created_at
                FROM boarding_booking %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where);
    }

    // ---------- 照护档案 ----------

    /** 照护档案原始行（密文未解密）。 */
    public record CareProfileRow(
            String id,
            String bookingId,
            String vaccinationSummary,
            String behaviorNotes,
            String feedingPlan,
            String medicationPlan,
            String emergencyContactCiphertext,
            String emergencyAuthorization,
            String accessScope) {
    }

    public Optional<CareProfileRow> findCareProfileByBooking(String bookingId) {
        return jdbcTemplate.query("""
                SELECT id, booking_id, vaccination_summary, behavior_notes, feeding_plan,
                       medication_plan, emergency_contact_ciphertext, emergency_authorization, access_scope
                FROM boarding_care_profile
                WHERE booking_id = ?
                """, rs -> rs.next() ? Optional.of(new CareProfileRow(
                rs.getString("id"),
                rs.getString("booking_id"),
                rs.getString("vaccination_summary"),
                rs.getString("behavior_notes"),
                rs.getString("feeding_plan"),
                rs.getString("medication_plan"),
                rs.getString("emergency_contact_ciphertext"),
                rs.getString("emergency_authorization"),
                rs.getString("access_scope"))) : Optional.empty(), bookingId);
    }

    // ---------- helpers ----------

    private BookingRow mapBooking(java.sql.ResultSet rs) throws java.sql.SQLException {
        String roomIdStr = rs.getString("room_id");
        java.sql.Date startDate = rs.getDate("start_date");
        java.sql.Date endDate = rs.getDate("end_date");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp started = rs.getTimestamp("started_at");
        Timestamp completed = rs.getTimestamp("completed_at");
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        return new BookingRow(
                rs.getString("id"),
                rs.getString("booking_no"),
                rs.getString("user_id"),
                rs.getString("pet_id"),
                roomIdStr,
                startDate == null ? null : startDate.toLocalDate(),
                endDate == null ? null : endDate.toLocalDate(),
                rs.getString("status"),
                rs.getString("care_profile_id"),
                rs.getBigDecimal("quoted_amount"),
                rs.getString("cancel_reason"),
                rs.getString("reject_reason"),
                rs.getString("handler_id"),
                rs.getString("handler_note"),
                created == null ? null : created.toInstant(),
                started == null ? null : started.toInstant(),
                completed == null ? null : completed.toInstant(),
                cancelled == null ? null : cancelled.toInstant(),
                rs.getString("terminated_reason"),
                rs.getInt("version"));
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
