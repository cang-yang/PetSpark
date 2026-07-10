package com.petspark.dashboard;

/**
 * 单项计数指标视图（例如「宠物总数」「订单总数」）。
 *
 * <p>用于 {@link DashboardSummaryView#metrics()} 列表中的一个条目，
 * 由前端渲染为指标卡片。{@code key} 是稳定标识符，{@code label} 是中文显示名，
 * {@code value} 是聚合计数结果。
 */
public record MetricCountView(
        String key,
        String label,
        long value) {
}
