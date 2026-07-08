package com.petspark.common.security;

import com.petspark.common.error.ErrorCode;
import com.petspark.common.error.ErrorResponse;
import com.petspark.common.web.RequestIdContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401/403 统一信封出口。Spring Security 抛出 {@link AuthenticationException} 或
 * {@link AccessDeniedException} 时，由这里转成 {@link ErrorResponse} 而非默认
 * HTML 错误页，保证前端拿到与业务错误一致的 JSON 契约（接口设计 §2）。
 *
 * <p>JWT 过滤器内部捕获的认证失败也转发到这里，避免堆栈外泄。
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, ErrorCode.AUTH_TOKEN_001);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, ErrorCode.ACCESS_DENIED_001);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage());
        // requestId 已由 RequestIdFilter 注入 MDC，ErrorResponse 构造时自动取。
        response.setHeader(RequestIdContext.HEADER, RequestIdContext.current() != null
                ? RequestIdContext.current() : "");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
