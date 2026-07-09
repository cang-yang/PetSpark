package com.petspark.adoption;

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

/**
 * PR-ADOPT-01 领养申请、审核与交接闭环集成验收：可领养浏览、本人申请/撤回、
 * 管理员审核、交接成功/失败、并发双申请、重复审核、状态机与版本冲突、通知与审计。
 *
 * <p>覆盖 API-ADOPT-001~008 关键路径。注意：本测试不运行（PR 指令禁止运行 DB 集成
 * 测试）；仅作编译级与契约校验。
 */
class AdoptionFlowIT extends AbstractControllerTest {

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
    private String breedId;

    @BeforeEach
    void setUp() {
        // 用户名带随机后缀，避免上次未清理干净时 uk_sys_user_username 撞键。
        String runTag = UUID.randomUUID().toString().substring(0, 8);
        adminId = createUser("adopt_admin_" + runTag);
        userAId = createUser("adopt_user_a_" + runTag);
        userBId = createUser("adopt_user_b_" + runTag);
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userAId, USER_ROLE_ID);
        assignRole(userBId, USER_ROLE_ID);
        adminToken = token(adminId, "adopt_admin_" + runTag);
        userAToken = token(userAId, "adopt_user_a_" + runTag);
        userBToken = token(userBId, "adopt_user_b_" + runTag);
        breedId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO pet_breed (id, species, name, status) VALUES (?, 'DOG', 'IT-AdoptBreed', 'ACTIVE')",
                breedId);
    }

    @AfterEach
    void cleanup() {
        // 子表先于父表；application 全部由 userA/B 提交，按申请人删除即可覆盖。
        jdbcTemplate.update("DELETE FROM adoption_application WHERE applicant_user_id IN (?, ?, ?)",
                adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM pet_image WHERE pet_id IN (SELECT id FROM pet WHERE name LIKE 'IT-ADOPT-%')");
        jdbcTemplate.update("DELETE FROM pet WHERE name LIKE 'IT-ADOPT-%'");
        jdbcTemplate.update("DELETE FROM pet_breed WHERE name LIKE 'IT-AdoptBreed'");
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'adoption' AND actor_id IN (?, ?, ?)",
                adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", adminId, userAId, userBId);
    }

    @Test
    void adoptableListOnlyShowsPublishedAdoptablePets() throws Exception {
        String breed = breedId;
        pet("IT-ADOPT-Visible", breed, adminId, "PUBLISHED", "ADOPTABLE");
        pet("IT-ADOPT-Adopting", breed, adminId, "PUBLISHED", "ADOPTING");
        pet("IT-ADOPT-Private", breed, adminId, "PRIVATE", "ADOPTABLE");
        mockMvc.perform(get("/api/v1/pets/adoptable").param("keyword", "IT-ADOPT-")
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("IT-ADOPT-Visible"));
    }

    @Test
    void createApplicationAndIdempotencyReplay() throws Exception {
        String petId = pet("IT-ADOPT-IDEM", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String idemKey = "adopt-idem-" + UUID.randomUUID();
        String payload = applyPayload(petId);
        String first = mockMvc.perform(post("/api/v1/adoptions")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.statusLabel").value("审核中"))
                .andExpect(jsonPath("$.data.role").value("applicant"))
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).path("data").path("id").asText();

        // 幂等重放：同 idempotency_key 返回同一申请，不重复插入。
        String second = mockMvc.perform(post("/api/v1/adoptions")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).path("data").path("id").asText();
        assertThat(secondId).isEqualTo(firstId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adoption_application WHERE applicant_user_id = ? AND idempotency_key = ?",
                Long.class, userAId, idemKey);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void duplicateActiveApplicationRejected() throws Exception {
        String petId = pet("IT-ADOPT-DUP", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        applyApplication(userAToken, petId);
        // 同一宠物已有 PENDING 申请：另一申请人提交应被拒。
        mockMvc.perform(post("/api/v1/adoptions")
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload(petId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADOPTION_DUPLICATE_001"));
    }

    @Test
    void selfWithdrawRestoresPetToAdoptable() throws Exception {
        String petId = pet("IT-ADOPT-WITHDRAW", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        int version = versionOf(appId);

        // 审核通过 → pet ADOPTING
        mockMvc.perform(post("/api/v1/admin/adoptions/{id}/decision", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reason\":\"合适\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTING");

        // 本人撤回 APPROVED → WITHDRAWN，pet 回到 ADOPTABLE
        int v1 = versionOf(appId);
        mockMvc.perform(post("/api/v1/adoptions/{id}/withdraw", appId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"暂无时间\",\"version\":" + v1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTABLE");

        // 重复撤回 → ADOPTION_STATE_001
        int v2 = versionOf(appId);
        mockMvc.perform(post("/api/v1/adoptions/{id}/withdraw", appId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"再次撤回\",\"version\":" + v2 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADOPTION_STATE_001"));
    }

    @Test
    void reviewApproveLocksPetAndDuplicateReviewRejected() throws Exception {
        String petId = pet("IT-ADOPT-REVIEW", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        int version = versionOf(appId);

        mockMvc.perform(post("/api/v1/admin/adoptions/{id}/decision", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reason\":\"合适\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTING");

        // 重复审核 → ADOPTION_STATE_001（已非 PENDING）
        int v1 = versionOf(appId);
        mockMvc.perform(post("/api/v1/admin/adoptions/{id}/decision", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"重复\",\"version\":" + v1 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADOPTION_STATE_001"));
    }

    @Test
    void reviewRejectNotifiesApplicantAndLeavesPetAdoptable() throws Exception {
        String petId = pet("IT-ADOPT-REJECT", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        int version = versionOf(appId);

        mockMvc.perform(post("/api/v1/admin/adoptions/{id}/decision", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"条件不符\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTABLE");

        // 拒绝后通知事件应已事务内追加到 outbox（payload 含 recipientId + type）。
        Long notif = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? "
                        + "AND payload LIKE '%ADOPTION_REJECTED%'",
                Long.class, userAId);
        assertThat(notif).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void handoverSuccessCompletesAndTransfersOwnership() throws Exception {
        String petId = pet("IT-ADOPT-OK", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        approve(appId, versionOf(appId));
        int v1 = versionOf(appId);

        mockMvc.perform(post("/api/v1/adoptions/{id}/handover", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\",\"note\":\"当面交接\",\"version\":" + v1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTED");
        assertThat(petOwner(petId)).isEqualTo(userAId);

        // 审计落库。
        Long audit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'adoption' AND action = 'complete_adoption_handover'",
                Long.class);
        assertThat(audit).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void handoverFailureCancelsApplicationAndRestoresPet() throws Exception {
        String petId = pet("IT-ADOPT-FAIL", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        approve(appId, versionOf(appId));
        int v1 = versionOf(appId);

        mockMvc.perform(post("/api/v1/adoptions/{id}/handover", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILURE\",\"note\":\"未到场\",\"version\":" + v1 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADOPTION_HANDOVER_001"));
        assertThat(applicationStatus(appId)).isEqualTo("CANCELLED");
        assertThat(petAdoptionStatus(petId)).isEqualTo("ADOPTABLE");

        // 失败通知事件已追加到 outbox。
        Long notif = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? "
                        + "AND payload LIKE '%ADOPTION_HANDOVER_FAILED%'",
                Long.class, userAId);
        assertThat(notif).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void ownershipIsolationOnDetailAndWithdraw() throws Exception {
        String petId = pet("IT-ADOPT-OWNER", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        // 非申请人、无审核权限 → 403
        mockMvc.perform(get("/api/v1/adoptions/{id}", appId).header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
        // 非申请人撤回 → 403
        mockMvc.perform(post("/api/v1/adoptions/{id}/withdraw", appId)
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"冒充\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void versionConflictOnStaleWithdraw() throws Exception {
        String petId = pet("IT-ADOPT-VC", breedId, adminId, "PUBLISHED", "ADOPTABLE");
        String appId = applyApplication(userAToken, petId);
        int v0 = versionOf(appId);
        // 管理员先审核通过，版本推进到 v1，申请人再用 v0 撤回 → VERSION_CONFLICT_001。
        approve(appId, v0);
        mockMvc.perform(post("/api/v1/adoptions/{id}/withdraw", appId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"过期版本\",\"version\":" + v0 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));
    }

    @Test
    void adminListRequiresReviewPermission() throws Exception {
        // 普通用户无 adoption:review → 403
        mockMvc.perform(get("/api/v1/admin/adoptions").header("Authorization", bearer(userAToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void petNotAdoptableRejectsApplication() throws Exception {
        String petId = pet("IT-ADOPT-NOTAVAIL", breedId, adminId, "PUBLISHED", "ADOPTING");
        mockMvc.perform(post("/api/v1/adoptions")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload(petId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PET_STATE_001"));
    }

    // ----- helpers -----

    private String applyApplication(String token, String petId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/adoptions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload(petId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String applyPayload(String petId) {
        return """
                {"petId":"%s","statement":"我有稳定的住所和时间，愿意照顾它一生","profileSnapshot":"已养过两只狗"}
                """.formatted(petId);
    }

    private void approve(String appId, int version) throws Exception {
        mockMvc.perform(post("/api/v1/admin/adoptions/{id}/decision", appId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reason\":\"合适\",\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private String pet(String name, String breed, String owner, String visibility, String adoption) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO pet (id, name, species, breed_id, owner_user_id, ownership_type, "
                + "adoption_status, boarding_status, public_status, info_updated_at) "
                + "VALUES (?, ?, 'DOG', ?, ?, 'USER', ?, 'NONE', ?, CURRENT_TIMESTAMP(3))",
                id, name, breed, owner, adoption, visibility);
        return id;
    }

    private int versionOf(String appId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT version FROM adoption_application WHERE id = ?", Integer.class, appId);
        return v == null ? -1 : v;
    }

    private String petAdoptionStatus(String petId) {
        return jdbcTemplate.queryForObject(
                "SELECT adoption_status FROM pet WHERE id = ?", String.class, petId);
    }

    private String petOwner(String petId) {
        return jdbcTemplate.queryForObject(
                "SELECT owner_user_id FROM pet WHERE id = ?", String.class, petId);
    }

    private String applicationStatus(String appId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM adoption_application WHERE id = ?", String.class, appId);
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
