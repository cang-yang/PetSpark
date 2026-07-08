package com.petspark.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class CatalogFlowIT extends AbstractControllerTest {

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
    private String categoryId;
    private String coverFileId;

    @BeforeEach
    void setUp() {
        adminId = createUser("catalog_admin");
        userId = createUser("catalog_user");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userId, USER_ROLE_ID);
        adminToken = token(adminId, "catalog_admin");
        userToken = token(userId, "catalog_user");
        coverFileId = createActiveGoodsCover(adminId);
        categoryId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order)
                VALUES (?, 'CAT_TOY', '玩具', 'ACTIVE', 1)
                """, categoryId);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'catalog' AND actor_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM goods_stock_adjustment WHERE goods_id IN (SELECT id FROM goods WHERE sku LIKE 'CAT-%')");
        jdbcTemplate.update("DELETE FROM goods WHERE sku LIKE 'CAT-%'");
        jdbcTemplate.update("DELETE FROM goods_category WHERE code LIKE 'CAT_%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE owner_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", adminId, userId);
    }

    @Test
    void adminCanCreatePublishAdjustStockAndPublicOnlySeesPublishedGoods() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-1", "ACTIVE", 10, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("CAT-SKU-1"))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andReturn().getResponse().getContentAsString();
        String goodsId = objectMapper.readTree(created).path("data").path("id").asText();
        int version = objectMapper.readTree(created).path("data").path("version").asInt();

        mockMvc.perform(get("/api/v1/goods")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.sku == 'CAT-SKU-1')]").exists());

        String inactive = mockMvc.perform(patch("/api/v1/admin/goods/{id}/status", goodsId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andReturn().getResponse().getContentAsString();
        version = objectMapper.readTree(inactive).path("data").path("version").asInt();

        mockMvc.perform(get("/api/v1/goods")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.sku == 'CAT-SKU-1')]").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.sku == 'CAT-SKU-1')]").exists());

        mockMvc.perform(post("/api/v1/admin/goods/{id}/stock-adjustments", goodsId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-3,\"reason\":\"盘点扣减\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(7));

        Integer adjustmentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_stock_adjustment
                WHERE goods_id = ? AND delta_quantity = -3 AND reason = '盘点扣减'
                """, Integer.class, goodsId);
        assertThat(adjustmentCount).isEqualTo(1);
    }

    @Test
    void skuUniquePriceStockCoverAndStockBoundariesAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-2", "ACTIVE", 2, 0)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-2", "ACTIVE", 2, 0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOODS_SKU_001"));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-NEG", "ACTIVE", -1, 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-PRICE", "ACTIVE", 1, -20)))
                .andExpect(status().isBadRequest());

        String otherCover = createActiveGoodsCover(userId);
        mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-FILE", "ACTIVE", 1, 0).replace(coverFileId, otherCover)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND_001"));
    }

    @Test
    void staleVersionIsRejectedForUpdateStatusAndStockAdjustment() throws Exception {
        JsonNode data = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/goods")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-3", "DRAFT", 5, 0)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        String goodsId = data.path("id").asText();
        int version = data.path("version").asInt();

        mockMvc.perform(put("/api/v1/admin/goods/{id}", goodsId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodsPayload("CAT-SKU-3A", "DRAFT", 5, 0).replace("\"version\":0", "\"version\":" + version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("CAT-SKU-3A"));

        mockMvc.perform(patch("/api/v1/admin/goods/{id}/status", goodsId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_001"));

        mockMvc.perform(post("/api/v1/admin/goods/{id}/stock-adjustments", goodsId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-99,\"reason\":\"越界\",\"version\":" + (version + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STOCK_001"));
    }

    private String goodsPayload(String sku, String status, int stock, int priceOffset) {
        BigDecimal price = new BigDecimal("19.90").add(BigDecimal.valueOf(priceOffset));
        return """
                {
                  "categoryId":"%s",
                  "sku":"%s",
                  "name":"猫玩具",
                  "description":"耐咬玩具",
                  "coverFileId":"%s",
                  "price":%s,
                  "stock":%d,
                  "status":"%s",
                  "version":0
                }
                """.formatted(categoryId, sku, coverFileId, price, stock, status);
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

    private String createActiveGoodsCover(String ownerId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO file_object (id, object_key, original_name, media_type, extension, size_bytes, sha256,
                    width, height, status, owner_id, business_type, confirmed_at)
                VALUES (?, ?, 'cover.png', 'image/png', 'png', 68, REPEAT('a', 64), 1, 1, 'ACTIVE', ?, 'GOODS_COVER', CURRENT_TIMESTAMP(3))
                """, id, UUID.randomUUID() + ".png", ownerId);
        return id;
    }

    private String token(String id, String username) {
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", username, "ACTIVE", 0);
        return jwtService.issue(user, List.of()).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
