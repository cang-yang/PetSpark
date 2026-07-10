package com.petspark.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * 后台仪表盘汇总视图（PR-DASHBOARD-01）。
 *
 * <p>一个 GET 请求返回运营/管理所需的全局聚合指标：
 * <ul>
 *   <li>{@code metrics}：单项计数列表（用户数、宠物数、商品数、订单数、
 *       领养申请数、寄养预订数、服务预订数、社区帖子数、流浪线索数、横幅数）；</li>
 *   <li>{@code distributions}：状态分布列表（订单、领养、寄养、服务预订、
 *       社区帖子、流浪线索、outbox 事件），每组由一次 {@code GROUP BY status} 查询得到；</li>
 *   <li>{@code queryCount}：本次响应实际执行的 SQL 查询数，用于自检 NFR-PERF-002
 *       「无 N+1、查询数有界」——测试断言此值不超过上界。</li>
 * </ul>
 *
 * <p>口径约束：
 * <ul>
 *   <li>每个指标 = 一次聚合 SQL（COUNT 或 GROUP BY），命中索引；</li>
 *   <li>软删除：有 {@code deleted_at} 列的表过滤 {@code deleted_at IS NULL}；
 *       无该列的表（order_header / boarding_booking / service_booking / outbox_event /
 *       sys_user）不做软删除过滤；</li>
 *   <li>本视图只读、不含个人数据字段，泄露面最小。</li>
 * </ul>
 */
@JsonPropertyOrder({"metrics", "distributions", "queryCount"})
public record DashboardSummaryView(
        List<MetricCountView> metrics,
        List<StatusDistributionView> distributions,
        int queryCount) {
}
