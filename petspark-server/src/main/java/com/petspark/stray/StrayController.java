package com.petspark.stray;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
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

/** 流浪救助线索接口。本人提交/查看与后台受理闭环分离授权。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class StrayController {

    private static final String STRAY_READ = "stray:read";
    private static final String STRAY_MANAGE = "stray:manage";

    private final StrayService service;

    public StrayController(StrayService service) {
        this.service = service;
    }

    @PostMapping("/stray-clues")
    public ApiResponse<StrayDtos.ClueView> create(
            @Valid @RequestBody StrayDtos.ClueCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId(), idempotencyKey));
    }

    @GetMapping("/stray-clues/mine")
    public ApiResponse<PageResult<StrayDtos.ClueView>> listMine(
            @Valid @ModelAttribute StrayDtos.MyClueQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMine(user.getId(), query));
    }

    @GetMapping("/stray-clues/{id}")
    public ApiResponse<StrayDtos.ClueView> getMine(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getMine(id, user.getId()));
    }

    @GetMapping("/admin/stray-clues")
    @RequirePermission({STRAY_READ, STRAY_MANAGE})
    public ApiResponse<PageResult<StrayDtos.ClueView>> adminList(
            @Valid @ModelAttribute StrayDtos.AdminClueQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @GetMapping("/admin/stray-clues/{id}")
    @RequirePermission({STRAY_READ, STRAY_MANAGE})
    public ApiResponse<StrayDtos.ClueView> adminDetail(@PathVariable String id) {
        return ApiResponse.ok(service.getAdmin(id));
    }

    @PostMapping("/admin/stray-clues/{id}/assign")
    @RequirePermission(STRAY_MANAGE)
    public ApiResponse<StrayDtos.ClueView> assign(
            @PathVariable String id,
            @Valid @RequestBody StrayDtos.AssignRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.assign(id, request, user.getId()));
    }

    @PostMapping("/admin/stray-clues/{id}/transition")
    @RequirePermission(STRAY_MANAGE)
    public ApiResponse<StrayDtos.ClueView> transition(
            @PathVariable String id,
            @Valid @RequestBody StrayDtos.TransitionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.transition(id, request, user.getId()));
    }
}
