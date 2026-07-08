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
        jdbcTemplate.update("""
                INSERT INTO auth_verification_code
                    (id, purpose, principal, code_hash, expires_at)
                VALUES (?, 'PASSWORD_RESET', ?, ?, ?)
                """, id, principal, codeHash, Timestamp.from(expiresAt));
    }

    public Optional<VerificationCodeRecord> findLatestPasswordReset(String principal) {
        return jdbcTemplate.query("""
                SELECT id, code_hash, expires_at, consumed_at, attempt_count
                FROM auth_verification_code
                WHERE purpose = 'PASSWORD_RESET' AND principal = ?
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
        }, principal);
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
