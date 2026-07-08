package com.petspark.catalog;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/goods")
    public ApiResponse<PageResult<GoodsView>> goods(@Valid GoodsQuery query) {
        return ApiResponse.okWithPage(service.publicGoods(query.getCategoryId(), query.getKeyword(), query.getPage(), query.getSize()));
    }

    @GetMapping("/goods/{id}")
    public ApiResponse<GoodsView> goodsDetail(@PathVariable String id) {
        return ApiResponse.ok(service.publicGoodsDetail(id));
    }

    @GetMapping("/goods/categories")
    public ApiResponse<List<GoodsCategoryView>> categories() {
        return ApiResponse.ok(service.categories(false));
    }

    @GetMapping("/admin/goods")
    @RequirePermission("goods:manage")
    public ApiResponse<PageResult<GoodsView>> adminGoods(@Valid GoodsQuery query) {
        return ApiResponse.okWithPage(service.adminGoods(query.getCategoryId(), query.getKeyword(), query.getStatus(),
                query.getPage(), query.getSize()));
    }

    @GetMapping("/admin/goods/{id}")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsView> adminGoodsDetail(@PathVariable String id) {
        return ApiResponse.ok(service.adminGoodsDetail(id));
    }

    @PostMapping("/admin/goods")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsView> createGoods(@Valid @RequestBody GoodsUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.createGoods(request, user.getId()));
    }

    @PutMapping("/admin/goods/{id}")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsView> updateGoods(@PathVariable String id,
            @Valid @RequestBody GoodsUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.updateGoods(id, request, user.getId()));
    }

    @PatchMapping("/admin/goods/{id}/status")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsView> updateGoodsStatus(@PathVariable String id,
            @Valid @RequestBody GoodsStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.updateStatus(id, request, user.getId()));
    }

    @PostMapping("/admin/goods/{id}/stock-adjustments")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsView> adjustStock(@PathVariable String id,
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.adjustStock(id, request, user.getId()));
    }

    @GetMapping("/admin/goods/categories")
    @RequirePermission("goods:manage")
    public ApiResponse<List<GoodsCategoryView>> adminCategories() {
        return ApiResponse.ok(service.categories(true));
    }

    @PostMapping("/admin/goods/categories")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsCategoryView> createCategory(@Valid @RequestBody GoodsCategoryRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.createCategory(request, user.getId()));
    }

    @PutMapping("/admin/goods/categories/{id}")
    @RequirePermission("goods:manage")
    public ApiResponse<GoodsCategoryView> updateCategory(@PathVariable String id,
            @Valid @RequestBody GoodsCategoryRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.updateCategory(id, request, user.getId()));
    }
}
