package com.petspark.adoption;

import com.petspark.adoption.AdoptionDtos.AdminApplicationQuery;
import com.petspark.adoption.AdoptionDtos.AdoptablePetQuery;
import com.petspark.adoption.AdoptionDtos.ApplicationCreateRequest;
import com.petspark.adoption.AdoptionDtos.ApplicationView;
import com.petspark.adoption.AdoptionDtos.HandoverRequest;
import com.petspark.adoption.AdoptionDtos.MyApplicationQuery;
import com.petspark.adoption.AdoptionDtos.ReviewRequest;
import com.petspark.adoption.AdoptionDtos.WithdrawRequest;
import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
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
 * 领养接口（API-ADOPT-001~008）。
 *
 * <ul>
 *   <li>GET /pets/adoptable — 可领养宠物分页（登录）；</li>
 *   <li>POST /adoptions — 提交申请，支持 Idempotency-Key 幂等重放；</li>
 *   <li>GET /adoptions — 本人申请列表；</li>
 *   <li>GET /adoptions/{id} — 申请详情（本人或审核角色）；</li>
 *   <li>POST /adoptions/{id}/withdraw — 本人撤回；</li>
 *   <li>GET /admin/adoptions — 管理员审核列表（adoption:review）；</li>
 *   <li>POST /admin/adoptions/{id}/decision — 审核决策（adoption:review）；</li>
 *   <li>POST /adoptions/{id}/handover — 记录交接结果（adoption:handover）。</li>
 * </ul>
 *
 * <p>本人申请/撤回/查详情走登录态 + 归属校验，不设独立权限行（与 order/health 本人
 * 操作一致）。审核与交接端点用 {@code @RequirePermission} 做路由级声明，归属/状态
 * 二次校验在服务层完成。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class AdoptionController {

    private static final String ADOPTION_REVIEW = "adoption:review";
    private static final String ADOPTION_HANDOVER = "adoption:handover";

    private final AdoptionService service;

    public AdoptionController(AdoptionService service) {
        this.service = service;
    }

    @GetMapping("/pets/adoptable")
    public ApiResponse<PageResult<AdoptionDtos.AdoptablePetView>> listAdoptable(
            @Valid @ModelAttribute AdoptablePetQuery query) {
        return ApiResponse.okWithPage(service.listAdoptable(query));
    }

    @PostMapping("/adoptions")
    public ApiResponse<ApplicationView> create(
            @Valid @RequestBody ApplicationCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId(), idempotencyKey));
    }

    @GetMapping("/adoptions")
    public ApiResponse<PageResult<ApplicationView>> listMine(
            @Valid @ModelAttribute MyApplicationQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMine(user.getId(), query));
    }

    @GetMapping("/adoptions/{id}")
    public ApiResponse<ApplicationView> getDetail(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.get(id, user.getId(), hasAdoptionReview(user)));
    }

    @PostMapping("/adoptions/{id}/withdraw")
    public ApiResponse<ApplicationView> withdraw(
            @PathVariable String id,
            @Valid @RequestBody WithdrawRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.withdraw(id, request, user.getId()));
    }

    @GetMapping("/admin/adoptions")
    @RequirePermission(ADOPTION_REVIEW)
    public ApiResponse<PageResult<ApplicationView>> adminList(
            @Valid @ModelAttribute AdminApplicationQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @PostMapping("/admin/adoptions/{id}/decision")
    @RequirePermission(ADOPTION_REVIEW)
    public ApiResponse<ApplicationView> decision(
            @PathVariable String id,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.review(id, request, user.getId()));
    }

    @PostMapping("/adoptions/{id}/handover")
    @RequirePermission(ADOPTION_HANDOVER)
    public ApiResponse<ApplicationView> handover(
            @PathVariable String id,
            @Valid @RequestBody HandoverRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        // 交接失败时服务层抛 ADOPTION_HANDOVER_001（422），此处不吞错。
        return ApiResponse.ok(service.handover(id, request, user.getId()));
    }

    private boolean hasAdoptionReview(AuthenticatedUser user) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : user.getAuthorities()) {
            if (ADOPTION_REVIEW.equals(authority.getAuthority())
                    || ADOPTION_HANDOVER.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
