package com.petspark.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PR-HEALTH-01 端到端契约验收。覆盖：
 * <ol>
 *   <li>主人新增健康记录，列表回显解密详情。</li>
 *   <li>非主人且无 health 权限 → 403 {@code ACCESS_OWNERSHIP_001}。</li>
 *   <li>ADMIN（持 health:manage）可对他人宠物新增/查看。</li>
 *   <li>修订创建新行（revision_of_id 指向原记录），原行不覆盖。</li>
 *   <li>隐私清除后详情/附件为空、审计外壳保留；二次清除 → 422 {@code HEALTH_RETENTION_001}。</li>
 *   <li>审计日志写入且不含健康明文。</li>
 *   <li>非作者且无 health:correct 修订 → 403。</li>
 * </ol>
 *
 * <p>由 main controller 在共享本机 MySQL 上串行执行；本类不自行启 Spring 上下文。
 */
class HealthRecordFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE = "00000000-0000-0000-0000-000000000101";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;

    private String adminId;
    private String ownerId;
    private String otherId;
    private String adminToken;
    private String ownerToken;
    private String otherToken;
    private final List<String> petIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        adminId = createUser("health_admin", ADMIN_ROLE);
        ownerId = createUser("health_owner", USER_ROLE);
        otherId = createUser("health_other", USER_ROLE);
        adminToken = token(adminId, "health_admin", List.of("health:manage", "health:correct", "privacy:manage"));
        ownerToken = token(ownerId, "health_owner", List.of());
        otherToken = token(otherId, "health_other", List.of());
    }

    @AfterEach
    void cleanup() {
        // 修订行引用原行（fk_health_revision）→ 子行先于父行删除。
        jdbcTemplate.update("DELETE FROM pet_health_record WHERE revision_of_id IN "
                + "(SELECT id FROM (SELECT id FROM pet_health_record WHERE pet_id IN "
                + "(SELECT id FROM pet WHERE name LIKE 'IT-HEALTH-%')) AS x)");
        jdbcTemplate.update("DELETE FROM pet_health_record WHERE pet_id IN (SELECT id FROM pet WHERE name LIKE 'IT-HEALTH-%')");
        jdbcTemplate.update("DELETE FROM pet_health_record WHERE author_id IN (?, ?, ?)", adminId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM pet WHERE name LIKE 'IT-HEALTH-%'");
        jdbcTemplate.update("DELETE FROM pet_breed WHERE name LIKE 'IT-HEALTH-%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE original_name LIKE 'it-health-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", adminId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE actor_id IN (?, ?, ?)", adminId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", adminId, ownerId, otherId);
    }

    @Test
    void ownerCreatesRecordAndSeesDecryptedDetail() throws Exception {
        String petId = pet("IT-HEALTH-Pet", ownerId, "PUBLISHED");
        String body = mockMvc.perform(post("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"VACCINATION","occurredOn":"2026-01-02","summary":"狂犬疫苗","detail":"批次A123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("狂犬疫苗"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("detail").asText()).isEqualTo("批次A123");
        assertThat(data.path("status").asText()).isEqualTo("ACTIVE");

        mockMvc.perform(get("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].detail").value("批次A123"));
    }

    @Test
    void nonOwnerWithoutHealthPermissionGetsOwnershipDenied() throws Exception {
        String petId = pet("IT-HEALTH-Private", ownerId, "PRIVATE");
        mockMvc.perform(get("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void adminWithHealthManageCanListAndCreateForOthersPet() throws Exception {
        String petId = pet("IT-HEALTH-Admin", ownerId, "PUBLISHED");
        mockMvc.perform(get("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"CHECKUP","occurredOn":"2026-02-01","summary":"年度体检","detail":"一切正常"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("年度体检"));
    }

    @Test
    void revisionCreatesNewRowAndLeavesOriginalUntouched() throws Exception {
        String petId = pet("IT-HEALTH-Revise", ownerId, "PUBLISHED");
        String created = mockMvc.perform(post("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"MEDICATION","occurredOn":"2026-03-01","summary":"驱虫","detail":"第一次"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String originalId = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/health-records/{id}/revisions", originalId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"MEDICATION","occurredOn":"2026-03-01","summary":"驱虫-修订","detail":"第二次"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionOfId").value(originalId))
                .andExpect(jsonPath("$.data.detail").value("第二次"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ?", Integer.class, petId);
        assertThat(count).isEqualTo(2);
        String originalSummary = jdbcTemplate.queryForObject(
                "SELECT summary FROM pet_health_record WHERE id = ?", String.class, originalId);
        assertThat(originalSummary).isEqualTo("驱虫");
        // 审计追溯存在且不含明文
        assertAuditExistsWithoutPlaintext(originalId, "第一次");
    }

    @Test
    void ownerErasesRecordAndSecondEraseIsRejected() throws Exception {
        String petId = pet("IT-HEALTH-Erase", ownerId, "PUBLISHED");
        String created = mockMvc.perform(post("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"SURGERY","occurredOn":"2026-04-01","summary":"绝育","detail":"敏感内容"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String recordId = objectMapper.readTree(created).path("data").path("id").asText();
        // 审计外壳存在但日志不含明文
        assertAuditExistsWithoutPlaintext(recordId, "敏感内容");

        mockMvc.perform(delete("/api/v1/health-records/{id}/content", recordId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"主体撤回授权"}
                                """))
                .andExpect(status().isOk());

        String detailCiphertext = jdbcTemplate.queryForObject(
                "SELECT detail_ciphertext FROM pet_health_record WHERE id = ?", String.class, recordId);
        assertThat(detailCiphertext).isNull();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM pet_health_record WHERE id = ?", String.class, recordId);
        assertThat(status).isEqualTo("ERASED");

        mockMvc.perform(get("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("ERASED"))
                .andExpect(jsonPath("$.data.items[0].detail").doesNotExist());

        // 二次清除回到 422
        mockMvc.perform(delete("/api/v1/health-records/{id}/content", recordId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"再次清除"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HEALTH_RETENTION_001"));
    }

    @Test
    void nonAuthorWithoutCorrectPermissionCannotRevise() throws Exception {
        String petId = pet("IT-HEALTH-NoCorrect", ownerId, "PUBLISHED");
        String created = mockMvc.perform(post("/api/v1/pets/{petId}/health-records", petId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"OTHER","occurredOn":"2026-05-01","summary":"记录一","detail":"x"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String recordId = objectMapper.readTree(created).path("data").path("id").asText();
        // other 用户既不是作者也没有 health:correct（无任何权限） → 403
        mockMvc.perform(post("/api/v1/health-records/{id}/revisions", recordId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordType":"OTHER","occurredOn":"2026-05-02","summary":"篡改","detail":"y"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    private void assertAuditExistsWithoutPlaintext(String objectId, String plaintext) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'health' AND object_id = ?",
                Integer.class, objectId);
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
        Integer leaked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'health' AND object_type = 'pet_health_record' AND object_id = ? "
                        + "AND (COALESCE(action,'') LIKE ? OR COALESCE(actor_role,'') LIKE ? OR COALESCE(reason_code,'') LIKE ?)",
                Integer.class, objectId, "%" + plaintext + "%", "%" + plaintext + "%", "%" + plaintext + "%");
        assertThat(leaked).isNotNull().isZero();
    }

    private String createUser(String name, String roleId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)
                """, id, name, name + "@example.com", name);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
        return id;
    }

    private String token(String id, String name, List<String> authorities) {
        return jwtService.issue(
                new SysUser(id, name, name + "@example.com", "$2a$10$test", name, "ACTIVE", 0),
                authorities).value();
    }

    private String pet(String name, String owner, String visibility) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pet (id, name, species, owner_user_id, ownership_type, adoption_status,
                                 boarding_status, public_status, info_updated_at)
                VALUES (?, ?, 'DOG', ?, 'USER', 'NOT_FOR_ADOPTION', 'NONE', ?, CURRENT_TIMESTAMP(3))
                """, id, name, owner, visibility);
        petIds.add(id);
        return id;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}