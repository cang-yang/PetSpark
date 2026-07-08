package com.petspark.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR-NOTIFY-01 HTTP 契约验收（API-NOTIFY-001~003）：
 * <ul>
 *   <li>API-NOTIFY-001 GET /notifications：本人分页，含 unreadCount；</li>
 *   <li>API-NOTIFY-002 PUT /notifications/{id}/read：幂等标记已读，本人隔离；</li>
 *   <li>API-NOTIFY-003 PUT /notifications/read-all：幂等全部已读；</li>
 *   <li>本人隔离：他人看不到、也标不了我的通知；</li>
 *   <li>幂等已读：重复标记已读返回成功；</li>
 *   <li>已读不可恢复：无"标记未读"接口，已读 read_at 不被重置。</li>
 * </ul>
 */
class NotificationHttpIT extends AbstractControllerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final List<String> userIds = new ArrayList<>();
    private String ownerToken;
    private String otherToken;
    private String ownerId;
    private String otherId;

    @BeforeEach
    void setUpUsers() {
        ownerId = createUser("notif_owner");
        otherId = createUser("notif_other");
        ownerToken = tokenFor(ownerId, "owner");
        otherToken = tokenFor(otherId, "other");
    }

    @AfterEach
    void cleanup() {
        for (String uid : userIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE recipient_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", uid);
        }
        userIds.clear();
    }

    @Test
    void listsOwnNotificationsWithUnreadCount() throws Exception {
        insertNotification("n-1", ownerId, "SYSTEM", "标题1", "内容1", null, null, null);
        insertNotification("n-2", ownerId, "ADOPTION", "标题2", "内容2", "ADOPTION", "app-2", null);
        insertNotification("n-3", ownerId, "SYSTEM", "标题3", "内容3", null, null,
                java.sql.Timestamp.from(java.time.Instant.now()));

        String body = mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode items = objectMapper.readTree(body).path("data").path("items");
        // 未读优先：n-1/n-2 在前，n-3（已读）在后。
        assertThat(items.get(0).path("read").asBoolean()).isFalse();
        assertThat(items.get(2).path("read").asBoolean()).isTrue();
    }

    @Test
    void onlyUnreadFilterReturnsUnreadOnly() throws Exception {
        insertNotification("u-1", ownerId, "SYSTEM", "未读", "内容", null, null, null);
        insertNotification("u-2", ownerId, "SYSTEM", "已读", "内容", null, null,
                java.sql.Timestamp.from(java.time.Instant.now()));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("onlyUnread", "true")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("u-1"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void markReadIsIdempotentAndIsolated() throws Exception {
        insertNotification("m-1", ownerId, "SYSTEM", "待读", "内容", null, null, null);

        // 本人标记已读成功。
        mockMvc.perform(put("/api/v1/notifications/m-1/read")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // 重复标记幂等成功。
        mockMvc.perform(put("/api/v1/notifications/m-1/read")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // read_at 只置位一次。
        Integer readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = 'm-1' AND read_at IS NOT NULL",
                Integer.class);
        assertThat(readCount).isEqualTo(1);

        // 他人无法标记/读取我的通知：列表看不到，单条标记返回 404（NOT_FOUND，不泄露存在性）。
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(put("/api/v1/notifications/m-1/read")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND_001"));
    }

    @Test
    void markAllReadIsIdempotent() throws Exception {
        insertNotification("a-1", ownerId, "SYSTEM", "1", "内容", null, null, null);
        insertNotification("a-2", ownerId, "SYSTEM", "2", "内容", null, null, null);

        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        Integer readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE recipient_id = ? AND read_at IS NOT NULL",
                Integer.class, ownerId);
        assertThat(readCount).isEqualTo(2);

        // 再次全部已读：无未读，幂等成功。
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void readCannotBeUndone() throws Exception {
        insertNotification("r-1", ownerId, "SYSTEM", "已读", "内容", null, null,
                java.sql.Timestamp.from(java.time.Instant.now()));
        // 标记已读：read_at 已非空，markRead 命中 0 行，但接口仍幂等成功；
        // 关键是不存在"标记未读"接口，已读 read_at 不会被清空。
        mockMvc.perform(put("/api/v1/notifications/r-1/read")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // 仍为已读，read_at 未被重置为空。
        Integer unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = 'r-1' AND read_at IS NULL",
                Integer.class);
        assertThat(unreadCount).isZero();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    private String createUser(String prefix) {
        String id = UUID.randomUUID().toString();
        String username = prefix + "_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", prefix);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                id, "00000000-0000-0000-0000-000000000101");
        userIds.add(id);
        return id;
    }

    private String tokenFor(String userId, String nickname) {
        SysUser user = new SysUser(userId, "u_" + userId, "u_" + userId + "@x.com",
                "$2a$10$test", nickname, "ACTIVE", 0);
        return jwtService.issue(user, List.of()).value();
    }

    private void insertNotification(String id, String recipientId, String type, String title,
                                    String content, String businessType, String businessId,
                                    java.sql.Timestamp readAt) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (readAt == null) {
                jdbcTemplate.update(
                        "INSERT INTO notification (id, recipient_id, type, title, content, business_type, business_id, read_at, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP(3))",
                        id, recipientId, type, title, content, businessType, businessId);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO notification (id, recipient_id, type, title, content, business_type, business_id, read_at, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))",
                        id, recipientId, type, title, content, businessType, businessId, readAt);
            }
        });
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
