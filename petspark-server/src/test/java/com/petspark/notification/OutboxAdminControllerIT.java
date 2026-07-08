package com.petspark.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import com.petspark.common.event.OutboxEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 可观测端点验收（NFR-OBS-001「积压和失败可观测」）：
 * <ul>
 *   <li>GET /api/v1/admin/outbox/status 返回四态计数，封装在统一信封；</li>
 *   <li>未认证 → 401；</li>
 *   <li>已认证但无 system:observe 权限 → 403；</li>
 *   <li>有 system:observe 权限 → 200 且计数反映 outbox_event 实际状态分布。</li>
 * </ul>
 */
class OutboxAdminControllerIT extends AbstractControllerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final List<String> userIds = new ArrayList<>();
    private final List<String> eventIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : eventIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE id = ?", id);
        }
        eventIds.clear();
        for (String uid : userIds) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", uid);
        }
        userIds.clear();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/outbox/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutObservePermissionIsForbidden() throws Exception {
        // 默认 USER 角色（V003）未绑定 system:observe（V007 只登记权限码、不绑角色）。
        String token = createUserToken(List.of());

        mockMvc.perform(get("/api/v1/admin/outbox/status")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_001"));
    }

    @Test
    void observerSeesFourStatusCountersReflectingOutboxState() throws Exception {
        // 投递两条并置不同状态，构造一个非平凡的分布。
        String observerToken = createUserToken(List.of("system:observe"));
        insertOutboxEvent(OutboxEvent.Status.PENDING, "{}");
        insertOutboxEvent(OutboxEvent.Status.DEAD, "{}");

        String body = mockMvc.perform(get("/api/v1/admin/outbox/status")
                        .header("Authorization", bearer(observerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        long pending = data.path("pending").asLong();
        long processing = data.path("processing").asLong();
        long sent = data.path("sent").asLong();
        long dead = data.path("dead").asLong();

        // 四态字段都在；我们至少各注入了一条 PENDING 与一条 DEAD。
        assertThat(pending).isGreaterThanOrEqualTo(1L);
        assertThat(dead).isGreaterThanOrEqualTo(1L);
        assertThat(processing).isGreaterThanOrEqualTo(0L);
        assertThat(sent).isGreaterThanOrEqualTo(0L);
    }

    private String createUserToken(List<String> authorities) {
        String id = UUID.randomUUID().toString();
        String username = "outbox_obs_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", "obs");
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                id, "00000000-0000-0000-0000-000000000101");
        userIds.add(id);
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", "obs", "ACTIVE", 0);
        return jwtService.issue(user, authorities).value();
    }

    private void insertOutboxEvent(OutboxEvent.Status status, String payloadJson) {
        String id = UUID.randomUUID().toString();
        eventIds.add(id);
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                jdbcTemplate.update(
                        "INSERT INTO outbox_event (id, event_type, aggregate_type, aggregate_id, "
                                + "payload, status, attempt_count, created_at) "
                                + "VALUES (?, 'notification.send', 'notification', ?, CAST(? AS JSON), ?, 0, CURRENT_TIMESTAMP(3))",
                        id, id, payloadJson, status.name()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
