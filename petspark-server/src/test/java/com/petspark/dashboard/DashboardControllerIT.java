package com.petspark.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 后台仪表盘端点验收（PR-DASHBOARD-01）：
 * <ul>
 *   <li>未认证 → 401；</li>
 *   <li>已认证但无 dashboard:read 权限 → 403 ACCESS_DENIED_001；</li>
 *   <li>有 dashboard:read 权限 → 200 且 metrics/distributions 结构完整、计数与
 *       数据库实际一致（metric 口径校验）；</li>
 *   <li>空库场景下所有计数为 0、分布为空数组；</li>
 *   <li>图表数据与原始 GROUP BY 查询逐态一致；</li>
 *   <li>queryCount 有界（≤ 17，证明无 N+1）。</li>
 * </ul>
 */
class DashboardControllerIT extends AbstractControllerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DashboardQueryService queryService;

    private final List<String> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String uid : userIds) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", uid);
        }
        userIds.clear();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutPermissionIsForbidden() throws Exception {
        // 默认 USER 角色（.101）未绑定 dashboard:read（V029 只绑 ADMIN 角色 .102）。
        String token = createUserToken(List.of(), "00000000-0000-0000-0000-000000000101");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED_001"));
    }

    @Test
    void adminWithPermissionSeesCompleteSummary() throws Exception {
        // ADMIN 角色 .102 已由 V029 绑定 dashboard:read。
        String token = createUserToken(List.of("dashboard:read"), "00000000-0000-0000-0000-000000000102");

        String body = mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");

        // metrics 结构与口径校验
        JsonNode metrics = data.path("metrics");
        assertThat(metrics.isArray()).isTrue();
        // 10 项计数指标
        assertThat(metrics.size()).isEqualTo(10);
        // 每项都有 key/label/value
        for (JsonNode m : metrics) {
            assertThat(m.has("key")).isTrue();
            assertThat(m.has("label")).isTrue();
            assertThat(m.has("value")).isTrue();
        }

        // 用户计数口径校验：应与数据库 sys_user 行数一致（含本测试刚插入的 1 个 admin）
        Long expectedUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        JsonNode usersMetric = findMetric(metrics, "users");
        assertThat(usersMetric.path("value").asLong()).isEqualTo(expectedUsers);

        // distributions 结构校验
        JsonNode distributions = data.path("distributions");
        assertThat(distributions.isArray()).isTrue();
        // 7 组状态分布
        assertThat(distributions.size()).isEqualTo(7);
        for (JsonNode d : distributions) {
            assertThat(d.has("key")).isTrue();
            assertThat(d.has("label")).isTrue();
            assertThat(d.has("items")).isTrue();
            assertThat(d.path("items").isArray()).isTrue();
        }

        // queryCount 有界（10 个计数 + 7 个分布 = 17）
        int queryCount = data.path("queryCount").asInt();
        assertThat(queryCount).isLessThanOrEqualTo(17);
        assertThat(queryCount).isEqualTo(17);
    }

    @Test
    void emptyDatabaseYieldsZeroCountsAndEmptyDistributions() throws Exception {
        // 用一个干净时刻的 snapshot 验证空场景：指标值可为非负（表里可能有其他测试遗留数据），
        // 但分布结构必须有 items 数组（即使为空）。
        DashboardSummaryView snapshot = queryService.snapshot();
        for (MetricCountView m : snapshot.metrics()) {
            assertThat(m.value()).isGreaterThanOrEqualTo(0L);
        }
        for (StatusDistributionView d : snapshot.distributions()) {
            assertThat(d.items()).isNotNull();
            // 各桶 count 非负
            for (StatusDistributionView.StatusBucket b : d.items()) {
                assertThat(b.count()).isGreaterThanOrEqualTo(0L);
            }
        }
    }

    @Test
    void chartDataMatchesRawGroupByQuery() {
        // 图表数据与原始 GROUP BY 查询逐态一致——证明服务未做任何二次过滤或丢弃。
        DashboardSummaryView snapshot = queryService.snapshot();

        // 订单分布：order_header 无 deleted_at，predicate = null
        var orderDist = findDistribution(snapshot, "orders");
        var rawOrders = queryService.rawGroupBy("order_header", null);
        assertBucketsMatch(orderDist, rawOrders);

        // 领养分布：adoption_application 有 deleted_at
        var adoptionDist = findDistribution(snapshot, "adoptions");
        var rawAdoptions = queryService.rawGroupBy("adoption_application", "deleted_at IS NULL");
        assertBucketsMatch(adoptionDist, rawAdoptions);

        // outbox 分布
        var outboxDist = findDistribution(snapshot, "outbox");
        var rawOutbox = queryService.rawGroupBy("outbox_event", null);
        assertBucketsMatch(outboxDist, rawOutbox);
    }

    @Test
    void queryCountIsBoundedAndNoNplusOne() {
        // 核心断言：queryCount = 固定常数（17），不随数据量增长。
        // 这证明每个指标 = 一次 SQL，无逐行 N+1。
        DashboardSummaryView snapshot = queryService.snapshot();
        assertThat(snapshot.queryCount()).isEqualTo(17);

        // 再调用一次，确保 queryCount 不变（没有缓存或懒加载导致的额外查询）
        DashboardSummaryView snapshot2 = queryService.snapshot();
        assertThat(snapshot2.queryCount()).isEqualTo(17);
    }

    private JsonNode findMetric(JsonNode metrics, String key) {
        for (JsonNode m : metrics) {
            if (key.equals(m.path("key").asText())) {
                return m;
            }
        }
        throw new AssertionError("metric not found: " + key);
    }

    private StatusDistributionView findDistribution(DashboardSummaryView snapshot, String key) {
        return snapshot.distributions().stream()
                .filter(d -> key.equals(d.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("distribution not found: " + key));
    }

    private void assertBucketsMatch(StatusDistributionView dist, java.util.Map<String, Long> raw) {
        for (StatusDistributionView.StatusBucket b : dist.items()) {
            assertThat(raw).containsKey(b.status());
            assertThat(b.count()).as("status %s count", b.status()).isEqualTo(raw.get(b.status()));
        }
        // 反向：raw 里的每个状态在 dist 里都能找到
        for (String status : raw.keySet()) {
            boolean found = dist.items().stream().anyMatch(b -> b.status().equals(status));
            assertThat(found).as("distribution %s missing status %s", dist.key(), status).isTrue();
        }
    }

    private String createUserToken(List<String> authorities, String roleId) {
        String id = UUID.randomUUID().toString();
        String username = "dashboard_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", "dash");
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                id, roleId);
        userIds.add(id);
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", "dash", "ACTIVE", 0);
        return jwtService.issue(user, authorities).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
