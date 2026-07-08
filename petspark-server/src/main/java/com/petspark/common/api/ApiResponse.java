package com.petspark.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.petspark.common.web.RequestIdContext;
import java.time.Instant;
import java.util.List;

/**
 * 统一成功响应信封。字段顺序与接口设计文档一致：
 * {@code code, message, data, requestId, timestamp}。
 *
 * <p>成功响应 {@code code} 固定为 {@code "OK"}；{@code requestId} 由
 * {@link RequestIdContext} 从当前请求上下文注入，{@code timestamp} 为 UTC 即时。
 */
@JsonPropertyOrder({"code", "message", "data", "requestId", "timestamp"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    private static final String OK = "OK";

    private final String code;
    private final String message;
    private final T data;
    private final String requestId;
    private final Instant timestamp;

    private ApiResponse(String code, String message, T data, String requestId, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, "success", data, RequestIdContext.current(), Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(OK, message, data, RequestIdContext.current(), Instant.now());
    }

    public static <T> ApiResponse<PageResult<T>> okWithPage(PageResult<T> page) {
        return new ApiResponse<>(OK, "success", page, RequestIdContext.current(), Instant.now());
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(OK, "success", null, RequestIdContext.current(), Instant.now());
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** 便于测试与日志的紧凑表示，不含数据正文。 */
    @Override
    public String toString() {
        return "ApiResponse{code='" + code + "', requestId='" + requestId + "'}";
    }
}
