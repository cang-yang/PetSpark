package com.petspark.health;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物健康记录接口。归属/授权角色在 {@link HealthRecordService} 中二次校验，
 * 路由权限拦截器只在受特定管理端点上做粗粒度校验。
 */
@RestController
@RequestMapping("/api/v1")
class HealthRecordController {

    private final HealthRecordService service;

    HealthRecordController(HealthRecordService service) {
        this.service = service;
    }

    // API-HEALTH-001：宠物主人或授权角色可查看健康记录列表。
    @GetMapping("/pets/{petId}/health-records")
    ApiResponse<PageResult<HealthRecordView>> list(@PathVariable String petId,
                                                   @Valid @ModelAttribute HealthQuery query,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listForPet(petId, query, user));
    }

    // API-HEALTH-002：宠物主人或持有 health:manage 角色可新增记录。
    @PostMapping("/pets/{petId}/health-records")
    ApiResponse<HealthRecordView> create(@PathVariable String petId,
                                         @Valid @RequestBody HealthRecordRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(petId, request, user));
    }

    // API-HEALTH-003：修订创建新记录，原记录保留不动。
    @PostMapping("/health-records/{id}/revisions")
    ApiResponse<HealthRecordView> revise(@PathVariable String id,
                                         @Valid @RequestBody HealthRevisionRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.revise(id, request, user));
    }

    // API-HEALTH-004：隐私清除——抹除详情与附件，保留审计外壳。归属/角色二次校验在服务层。
    @DeleteMapping("/health-records/{id}/content")
    ApiResponse<Void> erase(@PathVariable String id,
                            @Valid @RequestBody HealthEraseRequest request,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        service.erase(id, request, user);
        return ApiResponse.ok();
    }
}