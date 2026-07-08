package com.petspark.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link PermissionInterceptor}，仅作用于 {@code /api/**}。
 *
 * <p>拦截器在 Spring Security 过滤链之后运行：未认证请求先被 SecurityFilterChain
 * 以 401 拦截；已认证但缺权限的请求由本拦截器抛 403（经 GlobalExceptionHandler
 * 输出信封）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**");
    }
}
