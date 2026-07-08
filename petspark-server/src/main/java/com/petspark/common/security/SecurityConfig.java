package com.petspark.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.petspark.auth.JwtAuthenticationFilter;
import com.petspark.common.web.RequestIdFilter;

/**
 * Security 基线配置。PR-BASE-02 只建立无状态过滤链骨架与 401/403 信封出口；
 * JWT 解析过滤链与登录/刷新接口在 PR-AUTH-01 接入。
 *
 * <p>当前策略：
 * <ul>
 *   <li>会话无状态（{@code STATELESS}）；</li>
 *   <li>{@code /api/v1/auth/**}、Actuator {@code health/info}、错误页放行
 *       （认证由具体接口自管，登录接口本身公开）；</li>
 *   <li>其余 {@code /api/**} 要求认证；</li>
 *   <li>401/403 经 {@link SecurityErrorHandlers} 输出统一信封；</li>
 *   <li>静态资源与前端路由由反向代理处理，应用不开放静态目录。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityErrorHandlers errorHandlers;
    private final RequestIdFilter requestIdFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(SecurityErrorHandlers errorHandlers,
            RequestIdFilter requestIdFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.errorHandlers = errorHandlers;
        this.requestIdFilter = requestIdFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                // requestId 必须先于认证过滤链，确保 401/403 响应也带 requestId。
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
