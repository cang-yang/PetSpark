package com.petspark.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 仪表盘聚合查询服务（PR-DASHBOARD-01）。
 *
 * <p>核心约束 NFR-PERF-002：每个指标 = 一次聚合 SQL（COUNT 或 GROUP BY），
 * 命中索引；禁止逐行 N+1。本服务顺序执行固定数量的聚合查询，结果组合为
 * {@link DashboardSummaryView}。{@link #snapshot()} 返回的 {@code queryCount}
 * 即本服务实际执行的 SQL 数，测试据此断言查询数有界。
 *
 * <p>软删除口径：
 * <ul>
 *   <li>有 {@code deleted_at} 列的表（adoption_application / community_post /
 *       stray_clue / goods / pet / pet_breed / operation_banner）过滤
 *       {@code deleted_at IS NULL}；</li>
 *   <li>无该列的表（order_header / boarding_booking / service_booking /
 *       outbox_event / sys_user）不过滤；</li>
 *   <li>状态分布均按全量（含软删除已过滤后的全量）聚合，不叠加额外状态白名单——
 *       分布本身就是运营想看到的全景。</li>
 * </ul>
 *
 * <p>本服务不做写操作、不触发任何事件投递，对系统副作用为 0。
 */
@Service
public class DashboardQueryService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汇总后台仪表盘所有指标。每条指标对应一次 SQL，查询总数随指标数线性增长、
     * 不随行数增长。
     *
     * @return 仪表盘汇总视图，含 metrics / distributions / queryCount
     */
    public DashboardSummaryView snapshot() {
        List<MetricCountView> metrics = new ArrayList<>();
        List<StatusDistributionView> distributions = new ArrayList<>();
        int queryCount = 0;

        // —— 计数指标（每项一次 COUNT(*)）——
        metrics.add(count("users", "注册用户", "sys_user", null));
        queryCount++;
        metrics.add(count("pets", "宠物", "pet", "deleted_at IS NULL"));
        queryCount++;
        metrics.add(count("goods", "上架商品", "goods", "deleted_at IS NULL AND status = 'ACTIVE'"));
        queryCount++;
        metrics.add(count("orders", "订单", "order_header", null));
        queryCount++;
        metrics.add(count("adoptions", "领养申请", "adoption_application", "deleted_at IS NULL"));
        queryCount++;
        metrics.add(count("boarding", "寄养预订", "boarding_booking", null));
        queryCount++;
        metrics.add(count("service_bookings", "服务预订", "service_booking", null));
        queryCount++;
        metrics.add(count("posts", "社区帖子", "community_post", "deleted_at IS NULL"));
        queryCount++;
        metrics.add(count("stray_clues", "流浪线索", "stray_clue", "deleted_at IS NULL"));
        queryCount++;
        metrics.add(count("banners", "运营横幅", "operation_banner", "deleted_at IS NULL"));
        queryCount++;

        // —— 状态分布（每组一次 GROUP BY status）——
        distributions.add(groupBy("orders", "订单状态分布", "order_header", null));
        queryCount++;
        distributions.add(groupBy("adoptions", "领养申请状态分布", "adoption_application", "deleted_at IS NULL"));
        queryCount++;
        distributions.add(groupBy("boarding", "寄养预订状态分布", "boarding_booking", null));
        queryCount++;
        distributions.add(groupBy("service_bookings", "服务预订状态分布", "service_booking", null));
        queryCount++;
        distributions.add(groupBy("posts", "社区帖子状态分布", "community_post", "deleted_at IS NULL"));
        queryCount++;
        distributions.add(groupBy("stray_clues", "流浪线索状态分布", "stray_clue", "deleted_at IS NULL"));
        queryCount++;
        distributions.add(groupBy("outbox", "Outbox 事件状态分布", "outbox_event", null));
        queryCount++;

        return new DashboardSummaryView(metrics, distributions, queryCount);
    }

    private MetricCountView count(String key, String label, String table, String predicate) {
        String sql = "SELECT COUNT(*) FROM " + table
                + (predicate != null && !predicate.isBlank() ? " WHERE " + predicate : "");
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return new MetricCountView(key, label, value != null ? value : 0L);
    }

    private StatusDistributionView groupBy(String key, String label, String table, String predicate) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM " + table
                + (predicate != null && !predicate.isBlank() ? " WHERE " + predicate : "")
                + " GROUP BY status";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<StatusDistributionView.StatusBucket> buckets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long cnt = ((Number) row.getOrDefault("cnt", 0)).longValue();
            buckets.add(new StatusDistributionView.StatusBucket(status, cnt));
        }
        return new StatusDistributionView(key, label, buckets);
    }

    /**
     * 用于测试「图表数据与原始查询一致」的辅助方法：按指定表与谓词做一次
     * 原始 GROUP BY 查询，返回 {@code status → count} 映射。测试侧用它独立
     * 复算一次，再和 {@link #snapshot()} 返回的分布做逐态对比。
     */
    Map<String, Long> rawGroupBy(String table, String predicate) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM " + table
                + (predicate != null && !predicate.isBlank() ? " WHERE " + predicate : "")
                + " GROUP BY status";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long cnt = ((Number) row.getOrDefault("cnt", 0)).longValue();
            result.put(status, cnt);
        }
        return result;
    }
}
