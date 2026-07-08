package com.petspark.common.error;

/**
 * 字段级校验错误明细。对应接口设计文档错误示例中的 {@code details} 数组项：
 * {@code {field, reason}}。由 {@link GlobalExceptionHandler} 从
 * {@link org.springframework.validation.FieldError} 转换而来。
 */
public final class FieldViolation {

    private final String field;
    private final String reason;

    public FieldViolation(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
