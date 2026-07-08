package com.petspark.user;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findById(String userId) {
        return jdbcTemplate.query("""
                SELECT id, username, email, nickname, avatar_file_id, phone_ciphertext,
                       profile_bio, version, updated_at
                FROM sys_user
                WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return Optional.of(new UserProfile(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("nickname"),
                    rs.getString("avatar_file_id"),
                    rs.getString("phone_ciphertext"),
                    rs.getString("profile_bio"),
                    rs.getInt("version"),
                    updatedAt == null ? null : updatedAt.toInstant()));
        }, userId);
    }

    public int update(String userId, int expectedVersion, String nickname,
            String phoneCiphertext, String avatarFileId, String bio) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET nickname = ?, phone_ciphertext = ?, avatar_file_id = ?, profile_bio = ?,
                    version = version + 1
                WHERE id = ? AND version = ?
                """, nickname, phoneCiphertext, avatarFileId, bio, userId, expectedVersion);
    }
}
