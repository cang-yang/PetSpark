package com.petspark.notification;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 可观测端点（NFR-OBS-001「积压和失败可观测」）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/outbox/status}：返回 outbox_event 各状态计数
 *       （PENDING/PROCESSING/SENT/DEAD），供运营/运维判断投递链路健康。</li>
 * </ul>
 *
 * <p>权限=system:observe（V007 迁移登记，未绑定到默认 USER 角色——PR-RBAC-01 引入
 * ADMIN 角色后再绑定，使该端点对普通用户不可见）。Spring Security 已要求 /api/**
 * 认证；{@link RequirePermission} 在路由层叠加权限码校验，缺失则 403。
 *
 * <p>端点只读、不含业务载荷或用户标识，泄露面最小；不做写操作、不触发投递。
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequirePermission("system:observe")
public class OutboxAdminController {

    private final OutboxStatusService statusService;

    public OutboxAdminController(OutboxStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ApiResponse<OutboxStatusView> status() {
        return ApiResponse.ok(statusService.snapshot());
    }
}
