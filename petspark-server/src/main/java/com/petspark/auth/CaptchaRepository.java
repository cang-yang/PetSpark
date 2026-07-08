package com.petspark.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaptchaRepository {

    private final JdbcTemplate jdbcTemplate;

    public CaptchaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String challengeHash, String answerHash, String clientHash, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_captcha (id, challenge_hash, answer_hash, client_hash, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, challengeHash, answerHash, clientHash, Timestamp.from(expiresAt));
    }

    public Optional<AuthCaptcha> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, answer_hash, expires_at, consumed_at, attempt_count
                FROM auth_captcha
                WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            Timestamp consumedAt = rs.getTimestamp("consumed_at");
            return Optional.of(new AuthCaptcha(
                    rs.getString("id"),
                    rs.getString("answer_hash"),
                    rs.getTimestamp("expires_at").toInstant(),
                    consumedAt == null ? null : consumedAt.toInstant(),
                    rs.getInt("attempt_count")));
        }, id);
    }

    public int consume(String id) {
        return jdbcTemplate.update("""
                UPDATE auth_captcha
                SET consumed_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND consumed_at IS NULL
                """, id);
    }

    public void recordFailure(String id) {
        jdbcTemplate.update("UPDATE auth_captcha SET attempt_count = attempt_count + 1 WHERE id = ?", id);
    }
}
