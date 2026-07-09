package com.petspark.service;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * 服务预约接口（API-SVC-001~013）。
 *
 * <ul>
 *   <li>GET  /services/items — 服务项目浏览（登录即可）；</li>
 *   <li>GET  /services/items/{id} — 服务项目详情；</li>
 *   <li>GET  /services/resources — 服务资源浏览；</li>
 *   <li>GET  /services/slots — 可用窗口查询；</li>
 *   <li>POST /services/bookings — 创建预约（登录即可）；</li>
 *   <li>GET  /services/bookings — 我的预约列表；</li>
 *   <li>GET  /services/bookings/{id} — 预约详情（归属或管理员）；</li>
 *   <li>POST /services/bookings/{id}/cancel — 取消预约（归属或管理员）；</li>
 *   <li>POST /services/bookings/{id}/exception — 异常终止（归属或管理员）；</li>
 *   <li>POST /admin/services/bookings/{id}/transition — 履约状态流转（service:fulfill）；</li>
 *   <li>GET  /admin/services/bookings — 管理员预约列表（service:manage）；</li>
 *   <li>POST /admin/services/items — 新增服务项目（service:manage）；</li>
 *   <li>PUT  /admin/services/items/{id} — 更新服务项目（service:manage）；</li>
 *   <li>DELETE /admin/services/items/{id} — 删除服务项目（service:manage）；</li>
 *   <li>POST /admin/services/resources — 新增服务资源（service:manage）；</li>
 *   <li>PUT  /admin/services/resources/{id} — 更新服务资源（service:manage）；</li>
 *   <li>POST /admin/services/slots — 批量创建窗口（service:manage）。</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class ServiceBookingController {

    private static final String SERVICE_MANAGE = "service:manage";
    private static final String SERVICE_FULFILL = "service:fulfill";

    private final ServiceBookingService service;

    public ServiceBookingController(ServiceBookingService service) {
        this.service = service;
    }

    // ===== API-SVC-001 服务项目浏览 =====
    @GetMapping("/services/items")
    public ApiResponse<PageResult<ServiceDtos.ServiceItemView>> listItems(
            @Valid @ModelAttribute ServiceDtos.ServiceItemQuery query) {
        return ApiResponse.okWithPage(service.listItems(query));
    }

    // ===== API-SVC-002 服务项目详情 =====
    @GetMapping("/services/items/{id}")
    public ApiResponse<ServiceDtos.ServiceItemView> getItem(@PathVariable String id) {
        return ApiResponse.ok(service.getItem(id));
    }

    // ===== API-SVC-003 服务资源浏览 =====
    @GetMapping("/services/resources")
    public ApiResponse<PageResult<ServiceDtos.ServiceResourceView>> listResources(
            @Valid @ModelAttribute ServiceDtos.ServiceResourceQuery query) {
        return ApiResponse.okWithPage(service.listResources(query));
    }

    // ===== API-SVC-004 可用窗口查询 =====
    @GetMapping("/services/slots")
    public ApiResponse<PageResult<ServiceDtos.ServiceSlotView>> listSlots(
            @Valid @ModelAttribute ServiceDtos.ServiceSlotQuery query) {
        return ApiResponse.okWithPage(service.listSlots(query));
    }

    // ===== API-SVC-005 创建预约 =====
    @PostMapping("/services/bookings")
    public ApiResponse<ServiceDtos.ServiceBookingView> createBooking(
            @Valid @RequestBody ServiceDtos.ServiceBookingCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId()));
    }

    // ===== API-SVC-006 我的预约列表 =====
    @GetMapping("/services/bookings")
    public ApiResponse<PageResult<ServiceDtos.ServiceBookingView>> listMyBookings(
            @Valid @ModelAttribute ServiceDtos.MyBookingQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMy(user.getId(), query));
    }

    // ===== API-SVC-007 预约详情 =====
    @GetMapping("/services/bookings/{id}")
    public ApiResponse<ServiceDtos.ServiceBookingView> getBooking(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getForUser(id, user.getId(), hasServiceManage(user)));
    }

    // ===== API-SVC-008 取消预约 =====
    @PostMapping("/services/bookings/{id}/cancel")
    public ApiResponse<ServiceDtos.ServiceBookingView> cancelBooking(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingCancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.cancel(id, request, user.getId(), hasServiceManage(user)));
    }

    // ===== API-SVC-009 异常终止 =====
    @PostMapping("/services/bookings/{id}/exception")
    public ApiResponse<ServiceDtos.ServiceBookingView> exceptionBooking(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingExceptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.exception(id, request, user.getId(), hasServiceManage(user)));
    }

    // ===== API-SVC-010 履约状态流转（service:fulfill 或 service:manage）=====
    @PostMapping("/admin/services/bookings/{id}/transition")
    @RequirePermission({SERVICE_FULFILL, SERVICE_MANAGE})
    public ApiResponse<ServiceDtos.ServiceBookingView> transitionBooking(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingTransitionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.transition(id, request, user.getId()));
    }

    // ===== API-SVC-011 管理员预约列表 =====
    @GetMapping("/admin/services/bookings")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<PageResult<ServiceDtos.ServiceBookingView>> adminListBookings(
            @Valid @ModelAttribute ServiceDtos.AdminBookingQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    // ===== API-SVC-012 后台服务项目管理 =====
    @PostMapping("/admin/services/items")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceItemView> createItem(
            @Valid @RequestBody ServiceDtos.ServiceItemUpsertRequest request) {
        return ApiResponse.ok(service.upsertItem(null, request, true));
    }

    @org.springframework.web.bind.annotation.PutMapping("/admin/services/items/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceItemView> updateItem(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceItemUpsertRequest request) {
        return ApiResponse.ok(service.upsertItem(id, request, false));
    }

    @DeleteMapping("/admin/services/items/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<Void> deleteItem(@PathVariable String id) {
        service.deleteItem(id);
        return ApiResponse.ok();
    }

    // ===== API-SVC-013 后台服务资源/窗口管理 =====
    @PostMapping("/admin/services/resources")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceResourceView> createResource(
            @Valid @RequestBody ServiceDtos.ServiceResourceUpsertRequest request) {
        return ApiResponse.ok(service.upsertResource(null, request, true));
    }

    @org.springframework.web.bind.annotation.PutMapping("/admin/services/resources/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceResourceView> updateResource(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceResourceUpsertRequest request) {
        return ApiResponse.ok(service.upsertResource(id, request, false));
    }

    @PostMapping("/admin/services/slots")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<java.util.List<ServiceDtos.ServiceSlotView>> createSlots(
            @Valid @RequestBody ServiceDtos.ServiceSlotCreateRequest request) {
        return ApiResponse.ok(service.createSlots(request));
    }

    private boolean hasServiceManage(AuthenticatedUser user) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : user.getAuthorities()) {
            if (SERVICE_MANAGE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
