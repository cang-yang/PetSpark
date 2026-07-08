package com.petspark.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * PR-ORDER-01 订单全流程集成验收：服务端定价、库存原子扣减、幂等重放、归属隔离、
 * 状态机、快照不变性、下架商品不可下单。覆盖 API-ORDER-001~007 关键路径。
 *
 * <p>注意：本测试不运行（PR 指令禁止运行 DB 集成测试）；仅作编译级与契约校验。
 */
class OrderFlowIT extends AbstractControllerTest {

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
    private String categoryId;

    @BeforeEach
    void setUp() {
        adminId = createUser("order_admin");
        userAId = createUser("order_user_a");
        userBId = createUser("order_user_b");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userAId, USER_ROLE_ID);
        assignRole(userBId, USER_ROLE_ID);
        adminToken = token(adminId, "order_admin");
        userAToken = token(userAId, "order_user_a");
        userBToken = token(userBId, "order_user_b");
        categoryId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order)
                VALUES (?, 'ORD_CAT', '订单测试分类', 'ACTIVE', 1)
                """, categoryId);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM order_header WHERE user_id IN (?, ?, ?))",
                adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM order_header WHERE user_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM goods_stock_adjustment WHERE goods_id IN (SELECT id FROM goods WHERE sku LIKE 'ORD-%')");
        jdbcTemplate.update("DELETE FROM goods WHERE sku LIKE 'ORD-%'");
        jdbcTemplate.update("DELETE FROM goods_category WHERE code LIKE 'ORD_%'");
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'order' AND actor_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", adminId, userAId, userBId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", adminId, userAId, userBId);
    }

    @Test
    void serverSidePricingAndStockDeduction() throws Exception {
        String g1 = createGoods("ORD-PRICE-1", new BigDecimal("19.90"), 10);
        String g2 = createGoods("ORD-PRICE-2", new BigDecimal("5.50"), 10);
        String orderId = createOrder(userAToken, """
                {"lines":[{"goodsId":"%s","quantity":2},{"goodsId":"%s","quantity":3}],
                 "recipientName":"张三","recipientPhone":"13800000000","address":"北京市朝阳区测试路1号"}
                """.formatted(g1, g2));
        // totalAmount = 19.90*2 + 5.50*3 = 39.80 + 16.50 = 56.30
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(56.30))
                .andExpect(jsonPath("$.data.items[?(@.goodsId == '%s')].unitPrice".formatted(g1)).value(19.90))
                .andExpect(jsonPath("$.data.items[?(@.goodsId == '%s')].lineAmount".formatted(g1)).value(39.80));
        assertThat(stockOf(g1)).isEqualTo(8);
        assertThat(stockOf(g2)).isEqualTo(7);
    }

    @Test
    void stockAtomicAndNotOversold() throws Exception {
        String g = createGoods("ORD-STOCK-ATOMIC", new BigDecimal("10.00"), 5);
        // First qty=3 succeeds: stock 5 -> 2
        createOrder(userAToken, orderPayload(g, 3));
        assertThat(stockOf(g)).isEqualTo(2);
        // Second qty=3 fails: stock stays 2 (not -1)
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(g, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STOCK_001"));
        assertThat(stockOf(g)).isEqualTo(2);
    }

    @Test
    void idempotencyReplaysSameOrder() throws Exception {
        String g = createGoods("ORD-IDEM", new BigDecimal("8.00"), 20);
        String idemKey = "idem-" + UUID.randomUUID();
        String payload = orderPayload(g, 2);
        String first = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).path("data").path("id").asText();

        // Replay with same key -> same order, no new row.
        String second = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(userAToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).path("data").path("id").asText();

        assertThat(secondId).isEqualTo(firstId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_header WHERE user_id = ? AND idempotency_key = ?",
                Long.class, userAId, idemKey);
        assertThat(count).isEqualTo(1L);
        // Idempotent replay should not double-deduct stock.
        assertThat(stockOf(g)).isEqualTo(18);
    }

    @Test
    void ownershipIsolationEnforced() throws Exception {
        String g = createGoods("ORD-OWNER", new BigDecimal("1.00"), 10);
        String orderId = createOrder(userAToken, orderPayload(g, 1));
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void cancelRestoresStockOnceAndGuardsState() throws Exception {
        String g = createGoods("ORD-CANCEL", new BigDecimal("3.00"), 10);
        String orderId = createOrder(userAToken, orderPayload(g, 2));
        assertThat(stockOf(g)).isEqualTo(8);
        int version = versionOf(orderId);

        // Cancel: stock 8 -> 10
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不想要了\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(stockOf(g)).isEqualTo(10);

        // Cancel again with stale version -> ORDER_STATE_001（状态已变为 CANCELLED，非版本冲突），库存保持 10。
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"再次取消\",\"version\":" + version + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_001"));
        assertThat(stockOf(g)).isEqualTo(10);
    }

    @Test
    void adminTransitionStateMachine() throws Exception {
        String g = createGoods("ORD-TRANS", new BigDecimal("2.00"), 10);
        String orderId = createOrder(userAToken, orderPayload(g, 1));
        int v0 = versionOf(orderId);

        // CREATED -> PROCESSING OK
        mockMvc.perform(post("/api/v1/admin/orders/{id}/transition", orderId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\",\"note\":\"开始处理\",\"version\":" + v0 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        int v1 = versionOf(orderId);

        // PROCESSING -> COMPLETED OK
        mockMvc.perform(post("/api/v1/admin/orders/{id}/transition", orderId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"note\":\"完成\",\"version\":" + v1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        int v2 = versionOf(orderId);

        // COMPLETED -> PROCESSING illegal
        mockMvc.perform(post("/api/v1/admin/orders/{id}/transition", orderId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\",\"note\":\"回退\",\"version\":" + v2 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_001"));

        // Cancel a COMPLETED order -> illegal
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"完成后取消\",\"version\":" + v2 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_001"));
    }

    @Test
    void historicalSnapshotPreservedAfterPriceChange() throws Exception {
        String g = createGoods("ORD-SNAP", new BigDecimal("12.00"), 10);
        String orderId = createOrder(userAToken, orderPayload(g, 2));
        // Admin edits goods price to 99.00
        updateGoodsPrice(g, new BigDecimal("99.00"));
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(12.00))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(24.00));
    }

    @Test
    void inactiveGoodsNotOrderable() throws Exception {
        String g = createGoods("ORD-INACTIVE", new BigDecimal("7.00"), 10);
        // Mark inactive via admin status endpoint
        int v = goodsVersion(g);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/goods/{id}/status", g)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":" + v + "}"))
                .andExpect(status().isOk());

        // Preview: line unavailable
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(g, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        // Create: ORDER_STOCK_001 (deductStock WHERE status='ACTIVE' returns 0)
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(g, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STOCK_001"));
    }

    // ----- helpers -----

    private String createOrder(String token, String payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String orderPayload(String goodsId, int qty) {
        return """
                {"lines":[{"goodsId":"%s","quantity":%d}],
                 "recipientName":"李四","recipientPhone":"13900000000","address":"上海市测试街2号"}
                """.formatted(goodsId, qty);
    }

    private String createGoods(String sku, BigDecimal price, int stock) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO goods (id, category_id, sku, name, description, cover_file_id, price, stock, status, version)
                VALUES (?, ?, ?, '测试商品', '测试', NULL, ?, ?, 'ACTIVE', 0)
                """, id, categoryId, sku, price, stock);
        return id;
    }

    private void updateGoodsPrice(String goodsId, BigDecimal newPrice) {
        jdbcTemplate.update("UPDATE goods SET price = ?, version = version + 1 WHERE id = ?", newPrice, goodsId);
    }

    private int stockOf(String goodsId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM goods WHERE id = ?", Integer.class, goodsId);
        return stock == null ? -1 : stock;
    }

    private int versionOf(String orderId) {
        Integer version = jdbcTemplate.queryForObject("SELECT version FROM order_header WHERE id = ?", Integer.class, orderId);
        return version == null ? -1 : version;
    }

    private int goodsVersion(String goodsId) {
        Integer version = jdbcTemplate.queryForObject("SELECT version FROM goods WHERE id = ?", Integer.class, goodsId);
        return version == null ? -1 : version;
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
