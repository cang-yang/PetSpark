package com.petspark.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求标识过滤器。最早执行（{@code Ordered.HIGHEST_PRECEDENCE + 10}），确保后续
 * 日志、安全过滤链与异常处理都能拿到 {@code requestId}。
 *
 * <p>规则（架构 §11、接口设计 §1）：
 * <ul>
 *   <li>若客户端传入 {@code X-Request-Id}，复用之（便于跨服务追踪）；</li>
 *   <li>否则生成 UUID；</li>
 *   <li>写入 MDC（日志关联）、request 属性（响应体注入）与响应头；</li>
 *   <li>请求结束清理 MDC，防止线程池复用导致跨请求污染。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = RequestIdContext.HEADER;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        RequestIdContext.set(requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdContext.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && !incoming.isBlank() && isAcceptable(incoming)) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /** 限制复用客户端 requestId 的字符集，避免日志注入。 */
    private boolean isAcceptable(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return value.length() <= 64;
    }
}
