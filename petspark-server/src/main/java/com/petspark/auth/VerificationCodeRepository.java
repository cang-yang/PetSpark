package com.petspark.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationCodeRepository {

    private final JdbcTemplate jdbcTemplate;

    public VerificationCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String principal, String codeHash, Instant expiresAt) {
        insert(id, "PASSWORD_RESET", principal, codeHash, expiresAt);
    }

    public void insert(String id, String purpose, String principal, String codeHash, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_verification_code
                    (id, purpose, principal, code_hash, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, purpose, principal, codeHash, Timestamp.from(expiresAt));
    }

    public Optional<VerificationCodeRecord> findLatestPasswordReset(String principal) {
        return findLatest("PASSWORD_RESET", principal);
    }

    public Optional<VerificationCodeRecord> findLatest(String purpose, String principal) {
        return jdbcTemplate.query("""
                SELECT id, code_hash, expires_at, consumed_at, attempt_count
                FROM auth_verification_code
                WHERE purpose = ? AND principal = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            Timestamp consumed = rs.getTimestamp("consumed_at");
            return Optional.of(new VerificationCodeRecord(
                    rs.getString("id"),
                    rs.getString("code_hash"),
                    rs.getTimestamp("expires_at").toInstant(),
                    consumed == null ? null : consumed.toInstant(),
                    rs.getInt("attempt_count")));
        }, purpose, principal);
    }

    public boolean issuedSince(String purpose, String principal, Instant since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM auth_verification_code
                WHERE purpose = ? AND principal = ? AND created_at >= ?
                """, Integer.class, purpose, principal, Timestamp.from(since));
        return count != null && count > 0;
    }

    public void recordFailure(String id) {
        jdbcTemplate.update("""
                UPDATE auth_verification_code
                SET attempt_count = attempt_count + 1
                WHERE id = ? AND consumed_at IS NULL
                """, id);
    }

    public int consume(String id) {
        return jdbcTemplate.update("""
                UPDATE auth_verification_code
                SET consumed_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND consumed_at IS NULL
                """, id);
    }
}
