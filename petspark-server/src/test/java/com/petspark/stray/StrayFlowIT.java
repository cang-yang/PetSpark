package com.petspark.stray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** PR-STRAY-01 流浪救助线索提交、本人列表、后台指派与状态流转验收。 */
class StrayFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE_ID = "00000000-0000-0000-0000-000000000101";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private String adminId;
    private String userAId;
    private String userBId;
    private String adminToken;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        String runTag = UUID.randomUUID().toString().substring(0, 8);
        adminId = createUser("stray_admin_" + runTag);
        userAId = createUser("stray_user_a_" + runTag);
        userBId = createUser("stray_user_b_" + runTag);
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userAId, USER_ROLE_ID);
        assignRole(userBId, USER_ROLE_ID);
        adminToken = token(adminId, "stray_admin_" + runTag);
        userAToken = token(userAId, "stray_user_a_" + runTag);
        userBToken = token(userBId, "stray_user_b_" + runTag);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM stray_clue_image WHERE clue_id IN (SELECT id FROM stray_clue WHERE reporter_user_id IN (?, ?, ?))",
                adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM stray_clue WHERE reporter_user_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?, ?)", userAId, userBId, adminId);
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'stray' AND actor_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", adminId, userAId, userBId);
    }

    @Test
    void createClueAndIdempotencyReplay() throws Exception {
        String idemKey = "stray-idem-" + UUID.randomUUID();
        String first = mockMvc.perform(post("/api/v1/stray-clues")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cluePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.statusLabel").value("待受理"))
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).path("data").path("id").asText();

        String second = mockMvc.perform(post("/api/v1/stray-clues")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cluePayload()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).path("data").path("id").asText();
        assertThat(secondId).isEqualTo(firstId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stray_clue WHERE reporter_user_id = ? AND idempotency_key = ?",
                Long.class, userAId, idemKey);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void listMineAndOwnershipIsolation() throws Exception {
        String clueId = createClue(userAToken);

        mockMvc.perform(get("/api/v1/stray-clues/mine")
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(clueId));

        mockMvc.perform(get("/api/v1/stray-clues/{id}", clueId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void adminAssignAndTransitionClosesLoopWithNotificationsAndAudit() throws Exception {
        String clueId = createClue(userAToken);
        int v0 = versionOf(clueId);

        mockMvc.perform(post("/api/v1/admin/stray-clues/{id}/assign", clueId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUserId\":\"" + adminId + "\",\"note\":\"已联系救助员\",\"version\":" + v0 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedUserId").value(adminId));

        int v1 = versionOf(clueId);
        mockMvc.perform(post("/api/v1/admin/stray-clues/{id}/transition", clueId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_RESCUE\",\"note\":\"现场核实中\",\"version\":" + v1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_RESCUE"));

        int v2 = versionOf(clueId);
        mockMvc.perform(post("/api/v1/admin/stray-clues/{id}/transition", clueId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"note\":\"已送医安置\",\"handoffNote\":\"后续可建档领养\",\"version\":" + v2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.handoffNote").value("后续可建档领养"));

        Long notif = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND payload LIKE '%STRAY_RESOLVED%'",
                Long.class, userAId);
        assertThat(notif).isGreaterThanOrEqualTo(1L);
        Long audit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'stray' AND action = 'resolve_stray_clue'",
                Long.class);
        assertThat(audit).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void invalidTransitionAndVersionConflictAreRejected() throws Exception {
        String clueId = createClue(userAToken);

        mockMvc.perform(post("/api/v1/admin/stray-clues/{id}/transition", clueId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_RESCUE\",\"note\":\"跳过指派\",\"version\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STRAY_STATE_001"));

        mockMvc.perform(post("/api/v1/admin/stray-clues/{id}/assign", clueId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUserId\":\"" + adminId + "\",\"note\":\"过期版本\",\"version\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));
    }

    @Test
    void adminEndpointsRequirePermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stray-clues").header("Authorization", bearer(userAToken)))
                .andExpect(status().isForbidden());
    }

    private String createClue(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stray-clues")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cluePayload()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String cluePayload() {
        return """
                {"animalType":"CAT","location":"东门花坛","description":"一只橘猫疑似受伤，躲在灌木后","contactPhone":"13800000000"}
                """;
    }

    private int versionOf(String clueId) {
        Integer v = jdbcTemplate.queryForObject("SELECT version FROM stray_clue WHERE id = ?", Integer.class, clueId);
        return v == null ? -1 : v;
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version) "
                        + "VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)",
                id, username, username + "@example.com", username);
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
