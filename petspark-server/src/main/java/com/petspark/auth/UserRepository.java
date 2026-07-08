package com.petspark.auth;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String DEFAULT_ROLE_ID = "00000000-0000-0000-0000-000000000101";

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByUsernameOrEmail(String username, String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ? OR email = ?",
                Integer.class,
                username,
                email);
        return count != null && count > 0;
    }

    public void insert(SysUser user) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                user.id(),
                user.username(),
                user.email(),
                user.passwordHash(),
                user.nickname(),
                user.status(),
                user.tokenVersion());
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                VALUES (?, ?)
                """, user.id(), DEFAULT_ROLE_ID);
    }

    public Optional<SysUser> findByPrincipal(String principal) {
        return jdbcTemplate.query("""
                SELECT id, username, email, password_hash, nickname, status, token_version
                FROM sys_user
                WHERE username = ? OR email = ?
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(map(rs));
        }, principal, principal);
    }

    public List<String> findAuthorities(String userId) {
        return jdbcTemplate.queryForList("""
                SELECT p.code
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE ur.user_id = ?
                """, String.class, userId);
    }

    public void markLogin(String userId) {
        jdbcTemplate.update("UPDATE sys_user SET last_login_at = ? WHERE id = ?",
                Timestamp.from(java.time.Instant.now()), userId);
    }

    private SysUser map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SysUser(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("status"),
                rs.getInt("token_version"));
    }
}
