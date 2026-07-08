package com.petspark.common.error;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用的控制器，用于驱动 WebErrorContractTest 覆盖各 HTTP 错误状态。位于
 * 测试源集，不会进入生产构建。包名在 {@code com.petspark.**} 下，被主应用的
 * {@code @SpringBootApplication} 组件扫描自动注册为 Controller。
 *
 * <p>路由分两组：
 * <ul>
 *   <li>{@code /test/contract/**}：落在 {@code anyRequest().permitAll()} 段，用于
 *       纯业务/异常处理器契约（400/403/404/409/422/429/503/500），不受
 *       SecurityFilterChain 认证拦截；</li>
 *   <li>{@code /api/test/contract/require-permission}：落在 {@code /api/**}
 *       认证段，用于验证匿名访问受保护端点被 SecurityFilterChain 以 401 拦截。</li>
 * </ul>
 */
@RestController
public class ErrorContractTestController {

    @GetMapping("/test/contract/ok")
    public ApiResponse<String> ok() {
        return ApiResponse.ok("hello");
    }

    @GetMapping("/test/contract/page")
    public ApiResponse<PageResult<String>> page() {
        return ApiResponse.okWithPage(new PageResult<>(List.of("a", "b"), 1, 20, 2));
    }

    @GetMapping("/test/contract/validation")
    public ApiResponse<String> validation(@RequestParam @Min(10) int value) {
        return ApiResponse.ok(String.valueOf(value));
    }

    @PostMapping("/test/contract/body-validation")
    public ApiResponse<String> bodyValidation(@Valid @RequestBody SampleDto dto) {
        return ApiResponse.ok(dto.getName());
    }

    @GetMapping("/test/contract/not-found-resource")
    public ApiResponse<String> notFoundResource() {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001, "missing demo");
    }

    @GetMapping("/test/contract/business-409")
    public ApiResponse<String> business409() {
        throw new BusinessException(ErrorCode.ORDER_STOCK_001);
    }

    @GetMapping("/test/contract/business-422")
    public ApiResponse<String> business422() {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_001);
    }

    @GetMapping("/test/contract/business-429")
    public ApiResponse<String> business429() {
        throw new BusinessException(ErrorCode.RATE_LIMIT_001);
    }

    @GetMapping("/test/contract/business-503")
    public ApiResponse<String> business503() {
        throw new BusinessException(ErrorCode.AI_PROVIDER_001);
    }

    @GetMapping("/test/contract/access-denied")
    public ApiResponse<String> accessDenied() {
        throw new AccessDeniedException("nope");
    }

    /**
     * 落在 {@code /api/**} 认证段：匿名访问由 SecurityFilterChain 拦截返回 401，
     * 不触及 {@link RequirePermission} 注解语义（注解语义由后续 PR 带认证上下文测试）。
     */
    @GetMapping("/api/test/contract/require-permission")
    @RequirePermission("pet:read")
    public ApiResponse<String> requirePermission(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok("allowed");
    }

    @GetMapping("/test/contract/server-error")
    public ApiResponse<String> serverError() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/test/contract/illegal-arg")
    public ResponseEntity<String> illegalArg() {
        throw new IllegalArgumentException("bad input");
    }

    public record SampleDto(@NotBlank String name) {
        public String getName() { return name(); }
    }
}