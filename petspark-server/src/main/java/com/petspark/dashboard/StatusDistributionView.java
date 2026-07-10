package com.petspark.dashboard;

/**
 * 某业务实体的状态分布条目视图。例如订单在 CREATED / PROCESSING / COMPLETED /
 * CANCELLED 四态上的计数分布。
 *
 * <p>用于 {@link DashboardSummaryView#distributions()} 列表中的一组条目，
 * 由前端 ECharts 饼图渲染。{@code key} 是实体标识符，{@code label} 是中文显示名，
 * {@code items} 是各状态计数列表。
 */
public record StatusDistributionView(
        String key,
        String label,
        java.util.List<StatusBucket> items) {

    /**
     * 单个状态计数桶。
     *
     * @param status 状态枚举字面值（与数据库 CHECK 约束一致）
     * @param count  该状态的行数
     */
    public record StatusBucket(String status, long count) {
    }
}
