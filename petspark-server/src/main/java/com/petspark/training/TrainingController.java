package com.petspark.training;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import com.petspark.service.ServiceDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 训练服务 thin wrapper：路由层强制 kind=TRAINING，底层复用 service_booking 状态机。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class TrainingController {

    private static final String SERVICE_MANAGE = "service:manage";
    private static final String SERVICE_FULFILL = "service:fulfill";

    private final TrainingService service;

    public TrainingController(TrainingService service) {
        this.service = service;
    }

    @GetMapping("/training/items")
    public ApiResponse<PageResult<TrainingDtos.TrainingItemView>> listItems(
            @Valid @ModelAttribute TrainingDtos.TrainingItemQuery query) {
        return ApiResponse.okWithPage(service.listItems(query));
    }

    @GetMapping("/training/items/{id}")
    public ApiResponse<TrainingDtos.TrainingItemView> getItem(@PathVariable String id) {
        return ApiResponse.ok(service.getItem(id));
    }

    @GetMapping("/training/resources")
    public ApiResponse<PageResult<ServiceDtos.ServiceResourceView>> listResources(
            @Valid @ModelAttribute ServiceDtos.ServiceResourceQuery query) {
        return ApiResponse.okWithPage(service.listResources(query));
    }

    @GetMapping("/training/slots")
    public ApiResponse<PageResult<ServiceDtos.ServiceSlotView>> listSlots(
            @Valid @ModelAttribute ServiceDtos.ServiceSlotQuery query) {
        return ApiResponse.okWithPage(service.listSlots(query));
    }

    @PostMapping("/training/bookings")
    public ApiResponse<TrainingDtos.TrainingBookingView> create(
            @Valid @RequestBody TrainingDtos.TrainingApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId()));
    }

    @GetMapping("/training/bookings")
    public ApiResponse<PageResult<TrainingDtos.TrainingBookingView>> listMy(
            @Valid @ModelAttribute TrainingDtos.TrainingBookingQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMy(user.getId(), query));
    }

    @GetMapping("/training/bookings/{id}")
    public ApiResponse<TrainingDtos.TrainingBookingView> getBooking(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getForUser(id, user.getId(), hasServiceManage(user)));
    }

    @PostMapping("/training/bookings/{id}/cancel")
    public ApiResponse<TrainingDtos.TrainingBookingView> cancel(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingCancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.cancel(id, request, user.getId(), hasServiceManage(user)));
    }

    @PostMapping("/training/bookings/{id}/exception")
    public ApiResponse<TrainingDtos.TrainingBookingView> exception(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingExceptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.exception(id, request, user.getId(), hasServiceManage(user)));
    }

    @GetMapping("/admin/training/bookings")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<PageResult<TrainingDtos.TrainingBookingView>> adminList(
            @Valid @ModelAttribute TrainingDtos.TrainingBookingQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @PostMapping("/admin/training/bookings/{id}/transition")
    @RequirePermission({SERVICE_FULFILL, SERVICE_MANAGE})
    public ApiResponse<TrainingDtos.TrainingBookingView> transition(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceBookingTransitionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.transition(id, request, user.getId()));
    }

    @PostMapping("/admin/training/items")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<TrainingDtos.TrainingItemView> createItem(
            @Valid @RequestBody ServiceDtos.ServiceItemUpsertRequest request) {
        return ApiResponse.ok(service.upsertItem(null, request, true));
    }

    @org.springframework.web.bind.annotation.PutMapping("/admin/training/items/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<TrainingDtos.TrainingItemView> updateItem(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceItemUpsertRequest request) {
        return ApiResponse.ok(service.upsertItem(id, request, false));
    }

    @DeleteMapping("/admin/training/items/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<Void> deleteItem(@PathVariable String id) {
        service.deleteItem(id);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/training/resources")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceResourceView> createResource(
            @Valid @RequestBody ServiceDtos.ServiceResourceUpsertRequest request) {
        return ApiResponse.ok(service.upsertResource(null, request, true));
    }

    @org.springframework.web.bind.annotation.PutMapping("/admin/training/resources/{id}")
    @RequirePermission(SERVICE_MANAGE)
    public ApiResponse<ServiceDtos.ServiceResourceView> updateResource(
            @PathVariable String id,
            @Valid @RequestBody ServiceDtos.ServiceResourceUpsertRequest request) {
        return ApiResponse.ok(service.upsertResource(id, request, false));
    }

    @PostMapping("/admin/training/slots")
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
