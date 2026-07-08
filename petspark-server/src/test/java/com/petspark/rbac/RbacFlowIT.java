package com.petspark.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class RbacFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE_ID = "00000000-0000-0000-0000-000000000101";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private String adminId;
    private String userId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminId = createUser("rbac_admin");
        userId = createUser("rbac_user");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userId, USER_ROLE_ID);
        adminToken = token(adminId, "rbac_admin");
        userToken = token(userId, "rbac_user");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE code = 'CUSTOM_OP')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE code = 'CUSTOM_OP'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", adminId, userId);
    }

    @Test
    void adminCanListUsersAndReadRolesButRegularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "rbac")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].roleCodes").isArray());

        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'ADMIN')]").exists());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_001"));
    }

    @Test
    void roleAssignmentTakesEffectOnNextRequestWithoutReissuingToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"USER\",\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCodes[?(@ == 'ADMIN')]").exists());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());
    }

    @Test
    void disablingUserInvalidatesOldAccessTokenAndDoesNotModifyProtectedFields() throws Exception {
        int version = version(userId);
        mockMvc.perform(put("/api/v1/admin/users/{id}/status", userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":%d,"email":"evil@example.com"}
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        String email = jdbcTemplate.queryForObject("SELECT email FROM sys_user WHERE id = ?", String.class, userId);
        assertThat(email).isEqualTo("rbac_user@example.com");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotDisableOrRemoveLastAdminRole() throws Exception {
        int version = version(adminId);
        mockMvc.perform(put("/api/v1/admin/users/{id}/status", adminId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","version":%d}
                                """.formatted(version)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_001"));

        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", adminId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"USER\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_001"));
    }

    @Test
    void adminCanCreateCustomRoleAndUpdateItsPermissionsButBuiltInRoleIsProtected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CUSTOM_OP","name":"自定义运营","permissionCodes":["user:read"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("CUSTOM_OP"))
                .andExpect(jsonPath("$.data.permissionCodes[0]").value("user:read"));

        mockMvc.perform(put("/api/v1/admin/roles/CUSTOM_OP/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"user:read\",\"role:read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCodes[?(@ == 'role:read')]").exists());

        mockMvc.perform(put("/api/v1/admin/roles/ADMIN/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"user:read\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_001"));
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", username);
        return id;
    }

    private void assignRole(String userId, String roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private int version(String id) {
        return jdbcTemplate.queryForObject("SELECT version FROM sys_user WHERE id = ?", Integer.class, id);
    }

    private String token(String id, String username) {
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", username, "ACTIVE", 0);
        return jwtService.issue(user, List.of()).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
