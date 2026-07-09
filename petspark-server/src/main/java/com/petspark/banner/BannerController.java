package com.petspark.banner;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class BannerController {

    private final BannerService service;

    public BannerController(BannerService service) {
        this.service = service;
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerView>> activeBanners(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return ApiResponse.ok(service.activeBanners(limit));
    }

    @GetMapping("/admin/banners")
    @RequirePermission("banner:manage")
    public ApiResponse<PageResult<BannerView>> adminBanners(@Valid BannerQuery query) {
        return ApiResponse.okWithPage(service.adminBanners(query.getKeyword(), query.getStatus(), query.getPage(), query.getSize()));
    }

    @GetMapping("/admin/banners/{id}")
    @RequirePermission("banner:manage")
    public ApiResponse<BannerView> adminBanner(@PathVariable String id) {
        return ApiResponse.ok(service.adminBanner(id));
    }

    @PostMapping("/admin/banners")
    @RequirePermission("banner:manage")
    public ApiResponse<BannerView> create(@Valid @RequestBody BannerUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId()));
    }

    @PutMapping("/admin/banners/{id}")
    @RequirePermission("banner:manage")
    public ApiResponse<BannerView> update(@PathVariable String id,
            @Valid @RequestBody BannerUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.update(id, request, user.getId()));
    }

    @PatchMapping("/admin/banners/{id}/status")
    @RequirePermission("banner:manage")
    public ApiResponse<BannerView> updateStatus(@PathVariable String id,
            @Valid @RequestBody BannerStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.updateStatus(id, request, user.getId()));
    }

    @PatchMapping("/admin/banners/{id}/order")
    @RequirePermission("banner:manage")
    public ApiResponse<BannerView> updateOrder(@PathVariable String id,
            @Valid @RequestBody BannerOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.updateOrder(id, request, user.getId()));
    }

    @DeleteMapping("/admin/banners/{id}")
    @RequirePermission("banner:manage")
    public ApiResponse<Void> delete(@PathVariable String id,
            @RequestParam @Min(0) int version,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, version, user.getId());
        return ApiResponse.ok();
    }
}
