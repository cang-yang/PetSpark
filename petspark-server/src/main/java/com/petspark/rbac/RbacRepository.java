package com.petspark.rbac;

import com.petspark.common.api.PageResult;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RbacRepository {

    private final JdbcTemplate jdbcTemplate;

    public RbacRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<AdminUserSummary> findUsers(String keyword, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (username LIKE ? OR email LIKE ? OR nickname LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ? ");
            args.add(status.trim());
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<AdminUserSummary> items = jdbcTemplate.query("""
                SELECT id, username, email, nickname, status, version, created_at, updated_at
                FROM sys_user
                %s
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> new AdminUserSummary(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("nickname"),
                rs.getString("status"),
                findUserRoleCodes(rs.getString("id")),
                rs.getInt("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))), pageArgs.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Optional<AdminUserSummary> findUser(String id) {
        return jdbcTemplate.query("""
                SELECT id, username, email, nickname, status, version, created_at, updated_at
                FROM sys_user WHERE id = ?
                """, rs -> rs.next() ? Optional.of(new AdminUserSummary(
                rs.getString("id"), rs.getString("username"), rs.getString("email"), rs.getString("nickname"),
                rs.getString("status"), findUserRoleCodes(id), rs.getInt("version"),
                toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("updated_at")))) : Optional.empty(), id);
    }

    public int updateUserStatus(String id, String status, int version) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET status = ?, token_version = token_version + 1, version = version + 1
                WHERE id = ? AND version = ?
                """, status, id, version);
    }

    public List<String> findUserRoleCodes(String userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.code
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.code
                """, String.class, userId);
    }

    public void replaceUserRoles(String userId, List<String> roleCodes) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (String roleId : findRoleIds(roleCodes)) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        jdbcTemplate.update("UPDATE sys_user SET version = version + 1 WHERE id = ?", userId);
    }

    public List<RoleView> findRoles() {
        return jdbcTemplate.query("""
                SELECT id, code, name, built_in, status
                FROM sys_role
                ORDER BY built_in DESC, code
                """, (rs, rowNum) -> new RoleView(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBoolean("built_in"),
                rs.getString("status"),
                findRolePermissionCodes(rs.getString("id"))));
    }

    public Optional<RoleView> findRoleByCode(String code) {
        return jdbcTemplate.query("""
                SELECT id, code, name, built_in, status
                FROM sys_role WHERE code = ?
                """, rs -> rs.next() ? Optional.of(new RoleView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("built_in"),
                rs.getString("status"), findRolePermissionCodes(rs.getString("id")))) : Optional.empty(), code);
    }

    public List<PermissionView> findPermissions() {
        return jdbcTemplate.query("""
                SELECT code, resource, action, description
                FROM sys_permission
                ORDER BY resource, action, code
                """, (rs, rowNum) -> new PermissionView(
                rs.getString("code"), rs.getString("resource"), rs.getString("action"), rs.getString("description")));
    }

    public void createRole(String id, String code, String name, List<String> permissionCodes) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, code, name, built_in, status)
                VALUES (?, ?, ?, 0, 'ACTIVE')
                """, id, code, name);
        replaceRolePermissions(code, permissionCodes);
    }

    public void replaceRolePermissions(String roleCode, List<String> permissionCodes) {
        RoleView role = findRoleByCode(roleCode).orElseThrow();
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", role.id());
        for (String permissionId : findPermissionIds(permissionCodes)) {
            jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)", role.id(), permissionId);
        }
    }

    public long countActiveAdmins() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.status = 'ACTIVE' AND r.code = 'ADMIN' AND r.status = 'ACTIVE'
                """, Long.class);
        return count == null ? 0 : count;
    }

    public boolean userHasRole(String userId, String roleCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.code = ?
                """, Integer.class, userId, roleCode);
        return count != null && count > 0;
    }

    public boolean existsAllRoles(List<String> roleCodes) {
        return findRoleIds(roleCodes).size() == roleCodes.stream().distinct().count();
    }

    public boolean existsAllPermissions(List<String> permissionCodes) {
        return findPermissionIds(permissionCodes).size() == permissionCodes.stream().distinct().count();
    }

    private List<String> findRoleIds(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", roleCodes.stream().map(v -> "?").toList());
        return jdbcTemplate.queryForList("SELECT id FROM sys_role WHERE code IN (" + placeholders + ") AND status = 'ACTIVE'",
                String.class, roleCodes.toArray());
    }

    private List<String> findPermissionIds(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", permissionCodes.stream().map(v -> "?").toList());
        return jdbcTemplate.queryForList("SELECT id FROM sys_permission WHERE code IN (" + placeholders + ")",
                String.class, permissionCodes.toArray());
    }

    private List<String> findRolePermissionCodes(String roleId) {
        return jdbcTemplate.queryForList("""
                SELECT p.code
                FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                ORDER BY p.code
                """, String.class, roleId);
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
