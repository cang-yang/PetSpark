package com.petspark.common.security;

/**
 * 权限校验端口。业务模块（如 RBAC）提供实现，将用户拥有的权限集合暴露给
 * {@link PermissionInterceptor}。PR-BASE-02 只定义端口与一个基于
 * {@link AuthenticatedUser#getAuthorities()} 的默认实现；真实权限加载在
 * PR-RBAC-01 落地。
 *
 * <p>端口隔离使 common 不依赖任何业务模块，符合架构 §4 模块依赖规则
 * （common 无业务依赖）。
 */
public interface PermissionService {

    /**
     * 判断用户是否拥有给定权限码。
     *
     * @param userId       字符串化的用户 ID
     * @param permissionCode 权限码 {@code 资源:动作}
     * @return true 表示允许
     */
    boolean hasPermission(String userId, String permissionCode);
}
