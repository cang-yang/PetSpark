package com.petspark.health;

import com.petspark.common.api.PageResult;
import java.sql.Date;
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
 * 健康记录持久层。仅存储已加密的 {@code detailCiphertext}，加密由服务层完成，
 * 仓储层不感知密钥。修订链通过 {@code revision_of_id} 自引用实现：修订创建新行，
 * 原行不动；隐私清除置空密文与附件，保留审计外壳（id、pet、type、occurred_on、summary、
 * author、erased_at、reason）。
 */
@Repository
class HealthRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    HealthRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    PageResult<HealthRecordRow> findByPet(String petId, String recordType, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE pet_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(petId);
        if (StringUtils.hasText(recordType)) {
            where.append(" AND record_type = ? ");
            args.add(recordType.trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pet_health_record" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<HealthRecordRow> items = jdbcTemplate.query("""
                SELECT id, pet_id, record_type, occurred_on, summary, detail_ciphertext,
                       attachment_file_id, source_role, author_id, revision_of_id, status,
                       privacy_erased_at, erase_reason, erased_by, version, created_at
                FROM pet_health_record
                %s
                ORDER BY occurred_on DESC, created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> mapRow(rs), pageArgs.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    Optional<HealthRecordRow> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, pet_id, record_type, occurred_on, summary, detail_ciphertext,
                       attachment_file_id, source_role, author_id, revision_of_id, status,
                       privacy_erased_at, erase_reason, erased_by, version, created_at
                FROM pet_health_record
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), id);
    }

    void insert(HealthRecordRow row) {
        jdbcTemplate.update("""
                INSERT INTO pet_health_record
                    (id, pet_id, record_type, occurred_on, summary, detail_ciphertext,
                     attachment_file_id, source_role, author_id, revision_of_id, status, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.id(), row.petId(), row.recordType(), Date.valueOf(row.occurredOn()),
                row.summary(), row.detailCiphertext(), blankToNull(row.attachmentFileId()),
                row.sourceRole(), row.authorId(), blankToNull(row.revisionOfId()),
                row.status() == null ? "ACTIVE" : row.status(), row.version());
    }

    int markErased(String id, String reason, String erasedBy, int version) {
        return jdbcTemplate.update("""
                UPDATE pet_health_record
                SET status = 'ERASED',
                    detail_ciphertext = NULL,
                    attachment_file_id = NULL,
                    privacy_erased_at = CURRENT_TIMESTAMP(3),
                    erase_reason = ?,
                    erased_by = ?,
                    version = version + 1
                WHERE id = ? AND version = ? AND status = 'ACTIVE'
                """, reason, erasedBy, id, version);
    }

    private HealthRecordRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date occurred = rs.getDate("occurred_on");
        Timestamp created = rs.getTimestamp("created_at");
        return new HealthRecordRow(
                rs.getString("id"),
                rs.getString("pet_id"),
                rs.getString("record_type"),
                occurred == null ? null : occurred.toLocalDate(),
                rs.getString("summary"),
                rs.getString("detail_ciphertext"),
                rs.getString("attachment_file_id"),
                rs.getString("source_role"),
                rs.getString("author_id"),
                rs.getString("revision_of_id"),
                rs.getString("status"),
                created == null ? null : created.toInstant(),
                rs.getInt("version"));
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    record HealthRecordRow(String id, String petId, String recordType, LocalDate occurredOn,
                           String summary, String detailCiphertext, String attachmentFileId,
                           String sourceRole, String authorId, String revisionOfId,
                           String status, Instant createdAt, int version) {

        HealthRecordRow withCiphertext(String ciphertext) {
            return new HealthRecordRow(id, petId, recordType, occurredOn, summary, ciphertext,
                    attachmentFileId, sourceRole, authorId, revisionOfId, status, createdAt, version);
        }
    }
}
