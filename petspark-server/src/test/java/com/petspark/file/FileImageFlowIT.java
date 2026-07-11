package com.petspark.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "petspark.file.storage-root=target/test-files")
class FileImageFlowIT extends AbstractControllerTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private final List<String> userIds = new ArrayList<>();
    private final List<String> goodsIds = new ArrayList<>();
    private final List<String> categoryIds = new ArrayList<>();
    private final List<String> petIds = new ArrayList<>();
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUpUsers() {
        ownerToken = createUserToken("file_owner");
        otherToken = createUserToken("file_other");
    }

    @AfterEach
    void cleanup() throws Exception {
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT object_key FROM file_object WHERE owner_id IN (?, ?)",
                String.class, userIds.get(0), userIds.get(1));
        for (String petId : petIds) {
            jdbcTemplate.update("DELETE FROM pet_image WHERE pet_id = ?", petId);
            jdbcTemplate.update("DELETE FROM pet WHERE id = ?", petId);
        }
        for (String goodsId : goodsIds) {
            jdbcTemplate.update("DELETE FROM goods WHERE id = ?", goodsId);
        }
        for (String categoryId : categoryIds) {
            jdbcTemplate.update("DELETE FROM goods_category WHERE id = ?", categoryId);
        }
        jdbcTemplate.update("DELETE FROM file_object WHERE owner_id IN (?, ?)", userIds.get(0), userIds.get(1));
        for (String key : keys) {
            Files.deleteIfExists(Path.of("target/test-files").resolve(key));
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE actor_id IN (?, ?)", userIds.get(0), userIds.get(1));
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", userIds.get(0), userIds.get(1));
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", userIds.get(0), userIds.get(1));
    }

    @Test
    void validPngIsStagedWithRandomKeyThenConfirmedAndDownloadedByOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, PNG);

        String body = mockMvc.perform(multipart("/api/v1/files/images")
                        .file(file)
                        .param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").isNotEmpty())
                .andExpect(jsonPath("$.data.previewUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("STAGED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        String fileId = data.path("fileId").asText();
        String previewUrl = data.path("previewUrl").asText();
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM file_object WHERE id = ?", String.class, fileId);
        assertThat(objectKey).doesNotContain("avatar").endsWith(".png");
        assertThat(Files.exists(Path.of("target/test-files").resolve(objectKey))).isTrue();

        mockMvc.perform(get(previewUrl).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        mockMvc.perform(post("/api/v1/files/{id}/confirm", fileId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 上传与确认各落一条 file 模块审计（REQ-SYS-002 / NFR-OBS-001）。
        Long fileAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_id = ? AND module = 'file'",
                Long.class, userIds.get(0));
        assertThat(fileAuditCount).isNotNull().isGreaterThanOrEqualTo(2L);
        Integer confirmAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_id = ? AND module = 'file' AND action = 'confirm'",
                Integer.class, userIds.get(0));
        assertThat(confirmAudit).isEqualTo(1);
    }

    @Test
    void rejectsContentSpoofingOversizeAndTraversalFilename() throws Exception {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "not-an-image".getBytes());
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(spoofed).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_001"));

        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, PNG);
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(mismatched).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_001"));

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", MediaType.IMAGE_PNG_VALUE, new byte[5 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(oversized).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_SIZE_001"));

        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../avatar.png", MediaType.IMAGE_PNG_VALUE, PNG);
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(traversal).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_001"));

        MockMultipartFile headerInjection = new MockMultipartFile(
                "file", "avatar\r\nInjected.png", MediaType.IMAGE_PNG_VALUE, PNG);
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(headerInjection).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_001"));

        byte[] fakeWebp = "RIFF0000WEBPJUNK".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile invalidWebp = new MockMultipartFile(
                "file", "avatar.webp", "image/webp", fakeWebp);
        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(invalidWebp).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_001"));
    }

    @Test
    void stagedImageCannotBeDownloadedAnonymouslyOrByAnotherUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.png", MediaType.IMAGE_PNG_VALUE, PNG);
        String body = mockMvc.perform(multipart("/api/v1/files/images")
                        .file(file).param("businessType", "PROFILE_AVATAR")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String previewUrl = objectMapper.readTree(body).path("data").path("previewUrl").asText();

        // 下载路由允许匿名请求进入，以便公开业务图片可展示；私有图片仍由服务层拒绝。
        mockMvc.perform(get(previewUrl)).andExpect(status().isForbidden());
        mockMvc.perform(get(previewUrl).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmedPublicGoodsAndPetImagesAreReadableButPrivateReferencesStayProtected() throws Exception {
        String goodsFileId = uploadAndConfirm("goods.png", "GOODS_COVER");
        String categoryId = UUID.randomUUID().toString();
        String goodsId = UUID.randomUUID().toString();
        categoryIds.add(categoryId);
        goodsIds.add(goodsId);
        jdbcTemplate.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order)
                VALUES (?, ?, '公开图片测试', 'ACTIVE', 0)
                """, categoryId, "FILE_TEST_" + System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO goods (id, category_id, sku, name, cover_file_id, price, stock, status)
                VALUES (?, ?, ?, '公开商品', ?, 10, 1, 'ACTIVE')
                """, goodsId, categoryId, "FILE-SKU-" + System.nanoTime(), goodsFileId);

        mockMvc.perform(get("/api/v1/files/{id}", goodsFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        jdbcTemplate.update("UPDATE goods SET status = 'INACTIVE' WHERE id = ?", goodsId);
        mockMvc.perform(get("/api/v1/files/{id}", goodsFileId))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/files/{id}", goodsFileId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        String petFileId = uploadAndConfirm("pet.png", "PET_IMAGE");
        String petId = UUID.randomUUID().toString();
        petIds.add(petId);
        jdbcTemplate.update("""
                INSERT INTO pet
                    (id, name, species, sex, ownership_type, adoption_status, boarding_status,
                     public_status, info_updated_at)
                VALUES (?, '公开宠物', 'CAT', 'UNKNOWN', 'PLATFORM', 'AVAILABLE', 'NONE',
                        'PUBLISHED', CURRENT_TIMESTAMP(3))
                """, petId);
        jdbcTemplate.update("""
                INSERT INTO pet_image (id, pet_id, file_id, sort_order, cover_flag)
                VALUES (?, ?, ?, 0, 1)
                """, UUID.randomUUID().toString(), petId, petFileId);

        mockMvc.perform(get("/api/v1/files/{id}", petFileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        jdbcTemplate.update("UPDATE pet SET public_status = 'PRIVATE' WHERE id = ?", petId);
        mockMvc.perform(get("/api/v1/files/{id}", petFileId))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/files/{id}", petFileId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    private String uploadAndConfirm(String filename, String businessType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, MediaType.IMAGE_PNG_VALUE, PNG);
        String body = mockMvc.perform(multipart("/api/v1/files/images")
                        .file(file).param("businessType", businessType)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = objectMapper.readTree(body).path("data").path("fileId").asText();
        mockMvc.perform(post("/api/v1/files/{id}/confirm", fileId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        return fileId;
    }

    private String createUserToken(String prefix) {
        String id = UUID.randomUUID().toString();
        String username = prefix + "_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", prefix);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                id, "00000000-0000-0000-0000-000000000101");
        userIds.add(id);
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", prefix, "ACTIVE", 0);
        return jwtService.issue(user, List.of("file:upload")).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
