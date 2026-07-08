package com.petspark.common.web;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求标识上下文。{@code requestId} 由 {@link RequestIdFilter} 在请求入口生成或
 * 从 {@code X-Request-Id} 头读取，写入 MDC 供日志关联，并通过本类暴露给
 * {@code ApiResponse}/{@code ErrorResponse} 写入响应体。
 *
 * <p>同时存在 ThreadLocal 兜底：当响应序列化发生在过滤器链之外的线程
 * （例如异步响应包装器）时，MDC 已被清理，ThreadLocal 仍可提供最近值。
 */
public final class RequestIdContext {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";
    private static final String REQ_ATTR = "petspark.requestId";
    private static final ThreadLocal<String> FALLBACK = new ThreadLocal<>();

    private RequestIdContext() {
    }

    public static String current() {
        String fromAttr = readRequestAttribute();
        if (fromAttr != null && !fromAttr.isBlank()) {
            return fromAttr;
        }
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return FALLBACK.get();
    }

    /** 过滤器在请求开始时调用：写入 request 属性 + MDC + ThreadLocal。 */
    public static void set(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        writeRequestAttribute(requestId);
        MDC.put(MDC_KEY, requestId);
        FALLBACK.set(requestId);
    }

    /** 过滤器在请求结束时调用：清理 MDC 与 ThreadLocal，避免线程池泄漏。 */
    public static void clear() {
        MDC.remove(MDC_KEY);
        FALLBACK.remove();
    }

    private static String readRequestAttribute() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            Object value = sra.getRequest().getAttribute(REQ_ATTR);
            return value instanceof String s ? s : null;
        }
        return null;
    }

    private static void writeRequestAttribute(String requestId) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            sra.getRequest().setAttribute(REQ_ATTR, requestId);
        }
    }
}
