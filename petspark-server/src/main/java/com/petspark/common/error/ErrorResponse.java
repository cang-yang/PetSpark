package com.petspark.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.petspark.common.web.RequestIdContext;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 统一错误响应信封。字段顺序与接口设计文档一致：
 * {@code code, message, details, requestId, timestamp}。
 *
 * <p>异常响应绝不包含堆栈、SQL、内部路径或供应商密钥（架构 §7）。{@code requestId}
 * 关联日志中的完整内部错误，前端据此上报问题。
 */
@JsonPropertyOrder({"code", "message", "details", "requestId", "timestamp"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    private final String code;
    private final String message;
    private final List<FieldViolation> details;
    private final String requestId;
    private final Instant timestamp;

    public ErrorResponse(String code, String message, List<FieldViolation> details) {
        this.code = code;
        this.message = message;
        this.details = details == null ? null : (details.isEmpty() ? null : details);
        this.requestId = RequestIdContext.current();
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message) {
        this(code, message, Collections.emptyList());
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<FieldViolation> getDetails() {
        return details;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
