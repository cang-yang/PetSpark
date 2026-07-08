package com.petspark.order;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（API-ORDER-001~007）。
 *
 * <ul>
 *   <li>POST /orders/preview — 预览（登录即可）；</li>
 *   <li>POST /orders — 下单，支持 Idempotency-Key 幂等重放；</li>
 *   <li>GET /orders — 当前用户订单列表；</li>
 *   <li>GET /orders/{id} — 订单详情（归属或管理员）；</li>
 *   <li>POST /orders/{id}/cancel — 取消（归属或管理员）；</li>
 *   <li>GET /admin/orders — 管理员订单列表（order:manage）；</li>
 *   <li>POST /admin/orders/{id}/transition — 管理员状态流转（order:manage）。</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private static final String ORDER_MANAGE = "order:manage";

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders/preview")
    public ApiResponse<OrderDtos.OrderPreviewResult> preview(@Valid @RequestBody OrderDtos.OrderPreviewRequest request) {
        return ApiResponse.ok(service.preview(request));
    }

    @PostMapping("/orders")
    public ApiResponse<OrderDtos.OrderView> create(
            @Valid @RequestBody OrderDtos.OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId(), idempotencyKey));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResult<OrderDtos.OrderView>> listMy(
            @Valid @ModelAttribute OrderDtos.OrderQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listForUser(user.getId(), query));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDtos.OrderView> getDetail(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getForUser(id, user.getId(), hasOrderManage(user)));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderDtos.OrderView> cancel(
            @PathVariable String id,
            @Valid @RequestBody OrderDtos.OrderCancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.cancel(id, request, user.getId(), hasOrderManage(user)));
    }

    @GetMapping("/admin/orders")
    @RequirePermission(ORDER_MANAGE)
    public ApiResponse<PageResult<OrderDtos.OrderView>> adminList(
            @Valid @ModelAttribute OrderDtos.AdminOrderQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @PostMapping("/admin/orders/{id}/transition")
    @RequirePermission(ORDER_MANAGE)
    public ApiResponse<OrderDtos.OrderView> transition(
            @PathVariable String id,
            @Valid @RequestBody OrderDtos.OrderTransitionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.transition(id, request, user.getId()));
    }

    private boolean hasOrderManage(AuthenticatedUser user) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : user.getAuthorities()) {
            if (ORDER_MANAGE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
