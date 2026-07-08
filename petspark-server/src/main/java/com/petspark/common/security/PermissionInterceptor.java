package com.petspark.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限拦截器。在 Controller 方法执行前读取 {@link RequirePermission} 注解，
 * 通过 {@link PermissionService} 校验当前用户是否满足任一所需权限。
 *
 * <p>顺序：JWT 认证（SecurityFilterChain）→ 本拦截器（路由权限）→ Controller →
 * 应用服务（资源归属）。无注解的端点不校验权限（仍受 Spring Security
 * 鉴权规则约束）。命中失败抛 {@link org.springframework.security.access.AccessDeniedException}，
 * 由 {@link com.petspark.common.error.GlobalExceptionHandler} 转 403。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission requirement = resolveRequirement(handlerMethod);
        if (requirement == null) {
            return true;
        }
        AuthenticatedUser user = currentUser();
        if (user == null) {
            // 未认证交给 SecurityFilterChain 的 EntryPoint 处理 401；此处防御性拒绝。
            throw new org.springframework.security.access.AccessDeniedException("anonymous");
        }
        for (String code : requirement.value()) {
            if (permissionService.hasPermission(user.getId(), code)) {
                return true;
            }
        }
        throw new org.springframework.security.access.AccessDeniedException("missing permission");
    }

    private RequirePermission resolveRequirement(HandlerMethod handlerMethod) {
        RequirePermission methodLevel = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
