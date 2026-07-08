package com.petspark.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

class UserProfileFlowIT extends AbstractControllerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private String ownerId;
    private String otherId;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUpUsers() {
        ownerId = createUser("profile_owner");
        otherId = createUser("profile_other");
        ownerToken = token(ownerId, "profile_owner");
        otherToken = token(otherId, "profile_other");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM file_object WHERE owner_id IN (?, ?)", ownerId, otherId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", ownerId, otherId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ownerId, otherId);
    }

    @Test
    void userCanReadAndUpdateOwnProfileWithMaskedPhoneAndActiveAvatar() throws Exception {
        String avatarId = insertActiveAvatar(ownerId);

        String currentBody = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownerId))
                .andExpect(jsonPath("$.data.username").value("profile_owner"))
                .andExpect(jsonPath("$.data.phoneMasked").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int version = objectMapper.readTree(currentBody).path("data").path("version").asInt();

        String updateBody = """
                {
                  "nickname": "  阳阳铲屎官  ",
                  "phone": "13800138000",
                  "avatarFileId": "%s",
                  "bio": "喜欢猫猫狗狗",
                  "status": "DISABLED",
                  "version": %d
                }
                """.formatted(avatarId, version);

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("阳阳铲屎官"))
                .andExpect(jsonPath("$.data.avatarFileId").value(avatarId))
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/files/" + avatarId))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****8000"))
                .andExpect(jsonPath("$.data.bio").value("喜欢猫猫狗狗"))
                .andExpect(jsonPath("$.data.version").value(version + 1));

        JsonNode row = jdbcTemplate.queryForObject("""
                SELECT nickname, phone_ciphertext, avatar_file_id, profile_bio, status
                FROM sys_user WHERE id = ?
                """, (rs, rowNum) -> objectMapper.createObjectNode()
                        .put("nickname", rs.getString("nickname"))
                        .put("phone", rs.getString("phone_ciphertext"))
                        .put("avatar", rs.getString("avatar_file_id"))
                        .put("bio", rs.getString("profile_bio"))
                        .put("status", rs.getString("status")), ownerId);
        assertThat(row.path("nickname").asText()).isEqualTo("阳阳铲屎官");
        assertThat(row.path("phone").asText()).isNotEqualTo("13800138000").startsWith("v1:");
        assertThat(row.path("avatar").asText()).isEqualTo(avatarId);
        assertThat(row.path("bio").asText()).isEqualTo("喜欢猫猫狗狗");
        assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsAnonymousInvalidFieldsStagedOrForeignAvatarAndVersionConflict() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        int version = currentVersion();
        String badPhone = """
                {"phone":"abc","version":%d}
                """.formatted(version);
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPhone))
                .andExpect(status().isBadRequest());

        String stagedAvatar = insertAvatar(ownerId, "STAGED");
        String stagedBody = """
                {"avatarFileId":"%s","version":%d}
                """.formatted(stagedAvatar, version);
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stagedBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_001"));

        String foreignAvatar = insertActiveAvatar(otherId);
        String foreignBody = """
                {"avatarFileId":"%s","version":%d}
                """.formatted(foreignAvatar, version);
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foreignBody))
                .andExpect(status().isForbidden());

        String staleBody = """
                {"nickname":"旧版本","version":%d}
                """.formatted(version + 99);
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));
    }

    private int currentVersion() {
        return jdbcTemplate.queryForObject("SELECT version FROM sys_user WHERE id = ?", Integer.class, ownerId);
    }

    private String insertActiveAvatar(String userId) {
        return insertAvatar(userId, "ACTIVE");
    }

    private String insertAvatar(String userId, String status) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO file_object
                    (id, object_key, original_name, media_type, extension, size_bytes, sha256,
                     width, height, status, owner_id, business_type, confirmed_at)
                VALUES (?, ?, 'avatar.png', 'image/png', 'png', 68, ?, 1, 1, ?, ?, 'PROFILE_AVATAR',
                        CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP(3) ELSE NULL END)
                """, id, id + ".png", "sha-" + id, status, userId, status);
        return id;
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", username);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                VALUES (?, '00000000-0000-0000-0000-000000000101')
                """, id);
        return id;
    }

    private String token(String userId, String username) {
        SysUser user = new SysUser(userId, username, username + "@example.com", "$2a$10$test", username, "ACTIVE", 0);
        return jwtService.issue(user, List.of("user:profile", "file:upload")).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
