package com.petspark.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class BannerFlowIT extends AbstractControllerTest {

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
        adminId = createUser("banner_admin");
        userId = createUser("banner_user");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userId, USER_ROLE_ID);
        adminToken = token(adminId, "banner_admin");
        userToken = token(userId, "banner_user");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'banner' AND actor_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM operation_banner WHERE title LIKE 'IT-BANNER-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", adminId, userId);
    }

    @Test
    void adminCanCreatePublishReorderAndPublicOnlySeesActiveWindowedBanners() throws Exception {
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/banners")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("IT-BANNER-ACTIVE", "DRAFT", 10, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("IT-BANNER-ACTIVE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString()).path("data");
        String bannerId = created.path("id").asText();
        int version = created.path("version").asInt();

        mockMvc.perform(get("/api/v1/banners").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'IT-BANNER-ACTIVE')]").doesNotExist());

        JsonNode active = objectMapper.readTree(mockMvc.perform(patch("/api/v1/admin/banners/{id}/status", bannerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString()).path("data");
        version = active.path("version").asInt();

        JsonNode ordered = objectMapper.readTree(mockMvc.perform(patch("/api/v1/admin/banners/{id}/order", bannerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":1,\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn().getResponse().getContentAsString()).path("data");
        version = ordered.path("version").asInt();

        mockMvc.perform(get("/api/v1/banners?limit=10").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'IT-BANNER-ACTIVE')]").exists());

        mockMvc.perform(get("/api/v1/admin/banners")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "IT-BANNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '%s')]".formatted(bannerId)).exists());

        mockMvc.perform(delete("/api/v1/admin/banners/{id}", bannerId)
                        .header("Authorization", bearer(adminToken))
                        .param("version", String.valueOf(version)))
                .andExpect(status().isOk());

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_banner WHERE id = ? AND deleted_at IS NOT NULL", Integer.class, bannerId);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void validationPermissionsAndVersionConflictsAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banners")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("IT-BANNER-DENIED", "ACTIVE", 1, 0)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/banners")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("IT-BANNER-BAD", "ACTIVE", 1, 0)
                                .replace("\"targetUrl\":\"/goods\"", "\"targetUrl\":\"javascript:alert(1)\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_001"));

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/banners")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("IT-BANNER-STALE", "ACTIVE", 2, 0)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");

        mockMvc.perform(put("/api/v1/admin/banners/{id}", created.path("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("IT-BANNER-STALE-UPDATED", "ACTIVE", 3, created.path("version").asInt())))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/banners/{id}/status", created.path("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":" + created.path("version").asInt() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));
    }

    private String payload(String title, String status, int sortOrder, int version) {
        return """
                {
                  "title":"%s",
                  "subtitle":"首页运营位",
                  "imageUrl":"https://example.com/%s.png",
                  "targetType":"GOODS",
                  "targetUrl":"/goods",
                  "status":"%s",
                  "sortOrder":%d,
                  "startsAt":"%s",
                  "endsAt":"%s",
                  "version":%d
                }
                """.formatted(title, title.toLowerCase(), status, sortOrder,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), version);
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", username);
        return id;
    }

    private void assignRole(String id, String roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
    }

    private String token(String id, String username) {
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", username, "ACTIVE", 0);
        return jwtService.issue(user, List.of()).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
