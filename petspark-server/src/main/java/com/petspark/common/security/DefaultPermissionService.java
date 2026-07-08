package com.petspark.common.security;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Security 上下文的默认权限校验。从
 * {@link AuthenticatedUser#getAuthorities()} 读取已认证用户的权限集合，
 * 满足 {@code 资源:动作} 之一即放行。
 *
 * <p>PR-RBAC-01 实现从数据库加载完整权限树后会替换此默认实现；接口契约不变，
 * {@link PermissionInterceptor} 无需改动。
 */
@Component
public class DefaultPermissionService implements PermissionService {

    @Override
    public boolean hasPermission(String userId, String permissionCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            if (userId != null && !userId.equals(user.getId())) {
                return false;
            }
            return hasAuthority(user.getAuthorities(), permissionCode);
        }
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        return hasAuthority(authorities, permissionCode);
    }

    private boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String code) {
        if (authorities == null) {
            return false;
        }
        for (GrantedAuthority a : authorities) {
            if (code.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
