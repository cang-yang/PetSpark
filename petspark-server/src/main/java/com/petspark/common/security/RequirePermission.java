package com.petspark.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口所需权限声明。Controller 方法或类上标注，由
 * {@link PermissionInterceptor} 在路由前校验。值为 {@code 资源:动作}，
 * 例如 {@code @RequirePermission("pet:read")}、{@code @RequirePermission("adoption:review")}。
 *
 * <p>路由权限只提供前端体验一致性，后端仍在资源归属层二次校验（架构 §6）。
 * 即便前端路由放行，直接调用未授权接口仍返回 403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 所需权限码，{@code 资源:动作}。多个时满足其一即可（OR 语义）。 */
    String[] value();

    /** 是否要求资源归属检查（水平越权），默认 false；归属在应用服务层做。 */
    boolean requireOwnership() default false;
}
