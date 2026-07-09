package com.petspark.stray;

import com.petspark.common.api.PageResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 流浪救助线索持久化仓储。基于 JdbcTemplate，保持与 adoption/boarding 模块一致。 */
@Repository
public class StrayRepository {

    private final JdbcTemplate jdbcTemplate;

    public StrayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ClueRow(
            String id,
            String clueNo,
            String reporterUserId,
            String animalType,
            String location,
            String description,
            String contactPhone,
            String status,
            String assignedUserId,
            String adminNote,
            String handoffPetId,
            String handoffNote,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt,
            int version) {
    }

    public Optional<ClueRow> findByIdempotency(String reporterUserId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, clue_no, reporter_user_id, animal_type, location, description, contact_phone,
                       status, assigned_user_id, admin_note, handoff_pet_id, handoff_note,
                       created_at, updated_at, closed_at, version
                FROM stray_clue
                WHERE reporter_user_id = ? AND idempotency_key = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), reporterUserId, idempotencyKey);
    }

    public void insert(String id, String clueNo, String reporterUserId, String animalType,
            String location, String description, String contactPhone, String idempotencyKey) {
        jdbcTemplate.update("""
                INSERT INTO stray_clue
                    (id, clue_no, reporter_user_id, animal_type, location, description,
                     contact_phone, status, idempotency_key, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, 0)
                """, id, clueNo, reporterUserId, animalType, location, description,
                blankToNull(contactPhone), StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
    }

    public void insertImage(String clueId, String fileId, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO stray_clue_image (id, clue_id, file_id, sort_order)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), clueId, fileId, sortOrder);
    }

    public Optional<ClueRow> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, clue_no, reporter_user_id, animal_type, location, description, contact_phone,
                       status, assigned_user_id, admin_note, handoff_pet_id, handoff_note,
                       created_at, updated_at, closed_at, version
                FROM stray_clue
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), id);
    }

    public PageResult<StrayDtos.ClueView> findByReporter(String reporterUserId, StrayDtos.MyClueQuery q) {
        StringBuilder where = new StringBuilder(" WHERE reporter_user_id = ? AND deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        args.add(reporterUserId);
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stray_clue" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<StrayDtos.ClueView> items = jdbcTemplate.query("""
                SELECT id FROM stray_clue %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> toView(findById(rs.getString("id")).orElseThrow()),
                pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    public PageResult<StrayDtos.ClueView> findAdmin(StrayDtos.AdminClueQuery q) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getAssignedUserId())) {
            where.append(" AND assigned_user_id = ? ");
            args.add(q.getAssignedUserId().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND (clue_no LIKE ? OR location LIKE ? OR description LIKE ?) ");
            String kw = "%" + q.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stray_clue" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<StrayDtos.ClueView> items = jdbcTemplate.query("""
                SELECT id FROM stray_clue %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> toView(findById(rs.getString("id")).orElseThrow()),
                pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    public int assign(String id, String assignedUserId, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE stray_clue
                SET assigned_user_id = ?, admin_note = ?, status = 'ASSIGNED', version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND status = 'SUBMITTED'
                """, assignedUserId, blankToNull(note), id, version);
    }

    public int transition(String id, String status, String note, String handoffPetId, String handoffNote, int version) {
        String closedAtExpr = ("RESOLVED".equals(status) || "CLOSED".equals(status))
                ? "CURRENT_TIMESTAMP(3)" : "closed_at";
        return jdbcTemplate.update("""
                UPDATE stray_clue
                SET status = ?, admin_note = ?, handoff_pet_id = ?, handoff_note = ?,
                    closed_at = %s, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                  AND ((? = 'IN_RESCUE' AND status = 'ASSIGNED')
                    OR (? = 'RESOLVED' AND status IN ('ASSIGNED','IN_RESCUE'))
                    OR (? = 'CLOSED' AND status IN ('SUBMITTED','ASSIGNED','IN_RESCUE','RESOLVED')))
                """.formatted(closedAtExpr), status, blankToNull(note), blankToNull(handoffPetId),
                blankToNull(handoffNote), id, version, status, status, status);
    }

    public StrayDtos.ClueView toView(ClueRow row) {
        StatusPanel panel = panelOf(row.status());
        return new StrayDtos.ClueView(
                row.id(), row.clueNo(), row.reporterUserId(), nickname(row.reporterUserId()),
                row.animalType(), row.location(), row.description(), row.contactPhone(),
                row.status(), panel.label(), panel.styleClass(), panel.nextStep(),
                row.assignedUserId(), nickname(row.assignedUserId()), row.adminNote(),
                row.handoffPetId(), row.handoffNote(), images(row.id()),
                row.createdAt(), row.updatedAt(), row.closedAt(), row.version());
    }

    public boolean userExists(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND status = 'ACTIVE'", Integer.class, userId);
        return count != null && count > 0;
    }

    private List<StrayDtos.ImageRef> images(String clueId) {
        return jdbcTemplate.query("""
                SELECT file_id, sort_order FROM stray_clue_image WHERE clue_id = ? ORDER BY sort_order
                """, (rs, rowNum) -> new StrayDtos.ImageRef(
                rs.getString("file_id"), rs.getInt("sort_order"), "/api/v1/files/" + rs.getString("file_id")), clueId);
    }

    private String nickname(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("SELECT nickname FROM sys_user WHERE id = ?", String.class, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private StatusPanel panelOf(String status) {
        return switch (status) {
            case "SUBMITTED" -> new StatusPanel("待受理", "info", "等待救助团队受理线索");
            case "ASSIGNED" -> new StatusPanel("已指派", "warning", "救助负责人将现场核实");
            case "IN_RESCUE" -> new StatusPanel("救助中", "warning", "救助行动进行中");
            case "RESOLVED" -> new StatusPanel("已解决", "success", "线索已完成，可在备注查看后续安排");
            case "CLOSED" -> new StatusPanel("已关闭", "info", "线索已关闭");
            default -> new StatusPanel(status, "info", "");
        };
    }

    private record StatusPanel(String label, String styleClass, String nextStep) {
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ClueRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        Timestamp closed = rs.getTimestamp("closed_at");
        return new ClueRow(
                rs.getString("id"),
                rs.getString("clue_no"),
                rs.getString("reporter_user_id"),
                rs.getString("animal_type"),
                rs.getString("location"),
                rs.getString("description"),
                rs.getString("contact_phone"),
                rs.getString("status"),
                rs.getString("assigned_user_id"),
                rs.getString("admin_note"),
                rs.getString("handoff_pet_id"),
                rs.getString("handoff_note"),
                created == null ? null : created.toInstant(),
                updated == null ? null : updated.toInstant(),
                closed == null ? null : closed.toInstant(),
                rs.getInt("version"));
    }
}
