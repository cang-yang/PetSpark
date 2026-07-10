package com.petspark.dashboard;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台仪表盘端点（PR-DASHBOARD-01）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/dashboard}：返回全局聚合指标（计数 + 状态分布），</li>
 * </ul>
 *
 * <p>权限={@code dashboard:read}（V029 迁移登记，绑定到 ADMIN 角色 .102）。
 * Spring Security 已要求 /api/** 认证；{@link RequirePermission} 在路由层叠加
 * 权限码校验，缺失则 403。
 *
 * <p>端点只读、不含业务写操作；聚合查询由 {@link DashboardQueryService} 执行，
 * 每个指标 = 一次聚合 SQL（COUNT 或 GROUP BY），满足 NFR-PERF-002「无 N+1」。
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequirePermission("dashboard:read")
public class DashboardController {

    private final DashboardQueryService queryService;

    public DashboardController(DashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<DashboardSummaryView> summary() {
        return ApiResponse.ok(queryService.snapshot());
    }
}
