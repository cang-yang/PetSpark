package com.petspark.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String userId, String hash, String familyId, Instant expiresAt,
            String clientFingerprint) {
        jdbcTemplate.update("""
                INSERT INTO auth_refresh_token
                    (id, user_id, token_hash, family_id, expires_at, client_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, hash, familyId, Timestamp.from(expiresAt), clientFingerprint);
    }

    public Optional<RefreshTokenRecord> findByHash(String hash) {
        return jdbcTemplate.query("""
                SELECT id, user_id, family_id, expires_at, revoked_at, replaced_by_id
                FROM auth_refresh_token
                WHERE token_hash = ?
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            Timestamp revoked = rs.getTimestamp("revoked_at");
            return Optional.of(new RefreshTokenRecord(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("family_id"),
                    rs.getTimestamp("expires_at").toInstant(),
                    revoked == null ? null : revoked.toInstant(),
                    rs.getString("replaced_by_id")));
        }, hash);
    }

    public int replace(String id, String replacementId) {
        return jdbcTemplate.update("""
                UPDATE auth_refresh_token
                SET revoked_at = CURRENT_TIMESTAMP(3), replaced_by_id = ?
                WHERE id = ? AND revoked_at IS NULL
                """, replacementId, id);
    }

    public void revokeFamily(String familyId) {
        jdbcTemplate.update("""
                UPDATE auth_refresh_token
                SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP(3))
                WHERE family_id = ?
                """, familyId);
    }

    public void revokeAllForUser(String userId) {
        jdbcTemplate.update("""
                UPDATE auth_refresh_token
                SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP(3))
                WHERE user_id = ?
                """, userId);
    }
}
