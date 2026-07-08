package com.petspark.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemAdminFlowIT extends AbstractControllerTest {

    private static final String USER_ROLE = "00000000-0000-0000-0000-000000000101";
    private static final String ADMIN_ROLE = "00000000-0000-0000-0000-000000000102";
    private static final String AUDITOR_ROLE = "00000000-0000-0000-0000-000000000105";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private final java.util.List<String> users = new java.util.ArrayList<>();
    private final java.util.List<String> dictTypes = new java.util.ArrayList<>();
    private final java.util.List<String> auditIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String code : dictTypes) {
            jdbcTemplate.update("DELETE FROM sys_dict_item WHERE type_code = ?", code);
            jdbcTemplate.update("DELETE FROM sys_dict_type WHERE code = ?", code);
        }
        for (String id : auditIds) {
            jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", id);
        }
        for (String userId : users) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
    }

    @Test
    void adminCanManageDictionaryAndVersionConflictIsDetected() throws Exception {
        String admin = tokenWithRole(ADMIN_ROLE);
        String suffix = Long.toString(System.nanoTime());
        String typeCode = "demo_" + suffix;
        dictTypes.add(typeCode);

        mockMvc.perform(post("/api/v1/admin/dictionaries/types")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", typeCode, "name", "演示字典"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(typeCode));

        String itemBody = mockMvc.perform(post("/api/v1/admin/dictionaries/{typeCode}/items", typeCode)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemKey", "DEMO", "itemLabel", "演示项",
                                "sortOrder", 10, "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemKey").value("DEMO"))
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(itemBody).path("data");

        mockMvc.perform(put("/api/v1/admin/dictionaries/items/{id}", item.path("id").asText())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemLabel", "演示项2", "sortOrder", 20,
                                "status", "ACTIVE", "version", item.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemLabel").value("演示项2"));

        mockMvc.perform(put("/api/v1/admin/dictionaries/items/{id}", item.path("id").asText())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemLabel", "过期版本", "sortOrder", 30,
                                "status", "ACTIVE", "version", item.path("version").asInt()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));
    }

    @Test
    void sensitiveConfigKeysAreNotReadableOrWritable() throws Exception {
        String admin = tokenWithRole(ADMIN_ROLE);
        mockMvc.perform(get("/api/v1/admin/configs").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.configKey == 'site.notice')]").exists())
                .andExpect(jsonPath("$.data[?(@.configKey == 'jwt.secret')]").doesNotExist());

        mockMvc.perform(put("/api/v1/admin/configs/{key}", "jwt.secret")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("configValue", "leak", "valueType", "STRING",
                                "description", "bad", "version", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_001"));
    }

    @Test
    void adminUpdatesNonSensitiveConfigWithValidationAndVersioning() throws Exception {
        String admin = tokenWithRole(ADMIN_ROLE);
        String body = mockMvc.perform(get("/api/v1/admin/configs").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode config = objectMapper.readTree(body).path("data").findValuesAsText("configKey").contains("site.notice")
                ? findConfig(body, "site.notice") : null;
        assertThat(config).isNotNull();

        mockMvc.perform(put("/api/v1/admin/configs/{key}", "site.notice")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("configValue", "欢迎来到派宠", "valueType", "STRING",
                                "description", "首页公告", "version", config.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configValue").value("欢迎来到派宠"));
    }

    @Test
    void auditorCanReadSanitizedAuditButCannotUpdateConfig() throws Exception {
        String auditor = tokenWithRole(AUDITOR_ROLE);
        String auditId = UUID.randomUUID().toString();
        auditIds.add(auditId);
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, request_id, actor_id, actor_role, module, action, object_type,
                                       object_id, result, reason_code, ip_hash, created_at)
                VALUES (?, ?, ?, 'tester', 'system', 'update', 'config', 'site.notice',
                        'SUCCESS', NULL, ?, ?)
                """, auditId, "req-" + auditId, users.get(0), "hash-only", Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/v1/admin/audits")
                        .param("module", "system")
                        .header("Authorization", bearer(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(auditId))
                .andExpect(jsonPath("$.data.items[0].ipHash").value("hash-only"));

        mockMvc.perform(put("/api/v1/admin/configs/{key}", "site.notice")
                        .header("Authorization", bearer(auditor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("configValue", "x", "valueType", "STRING",
                                "description", "x", "version", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalUserCannotAccessSystemAdminApis() throws Exception {
        String user = tokenWithRole(USER_ROLE);

        mockMvc.perform(get("/api/v1/admin/audits").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/configs").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    private JsonNode findConfig(String body, String key) throws Exception {
        for (JsonNode node : objectMapper.readTree(body).path("data")) {
            if (key.equals(node.path("configKey").asText())) {
                return node;
            }
        }
        return null;
    }

    private String tokenWithRole(String roleId) {
        String id = UUID.randomUUID().toString();
        String username = "system_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", "system");
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
        users.add(id);
        return jwtService.issue(new SysUser(id, username, username + "@example.com", "$2a$10$test",
                "system", "ACTIVE", 0), List.of()).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
