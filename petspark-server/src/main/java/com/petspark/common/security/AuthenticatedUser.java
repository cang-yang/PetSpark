package com.petspark.common.security;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 已认证用户。{@code id} 为字符串化的雪花 ID（数据库 BIGINT UNSIGNED，接口以字符串
 * 传输避免 JS 精度丢失）。权限编码为 {@code 资源:动作}（架构 §6），通过
 * {@link #authorities} 暴露给 Spring Security。
 *
 * <p>本类在 PR-BASE-02 只承载身份与权限的载体契约；JWT 解析与登录认证在
 * PR-AUTH-01 实现。{@link com.petspark.common.security.PermissionInterceptor} 只
 * 依赖本类暴露的 id 与 authority 集合。
 */
public final class AuthenticatedUser implements UserDetails {

    public static final String ID_KEY = "petspark.userId";

    private final String id;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(String id, String username, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public String getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
