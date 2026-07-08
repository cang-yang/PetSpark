package com.petspark.common.error;

/**
 * 统一错误码。格式为 {@code 模块_类别_序号}（见架构设计 §7），例如
 * {@code AUTH_CREDENTIAL_001}、{@code ORDER_STOCK_001}。
 *
 * <p>每个枚举绑定一个 HTTP 状态。{@link BusinessException} 携带 {@link ErrorCode}
 * 抛出，由 {@link GlobalExceptionHandler} 统一转成 {@link ErrorResponse}。
 * 业务模块新增错误码时在此枚举追加，禁止散落在各处用裸字符串。
 */
public enum ErrorCode {

    // 校验类 400
    VALIDATION_FIELD_001(400, "VALIDATION_FIELD_001", "字段校验失败"),
    VALIDATION_PAGE_001(400, "VALIDATION_PAGE_001", "分页参数非法"),
    VALIDATION_IDEMPOTENCY_001(400, "VALIDATION_IDEMPOTENCY_001", "幂等键格式非法"),

    // 认证类 401
    AUTH_CREDENTIAL_001(401, "AUTH_CREDENTIAL_001", "凭据无效"),
    AUTH_TOKEN_001(401, "AUTH_TOKEN_001", "访问令牌缺失或无效"),
    AUTH_REFRESH_001(401, "AUTH_REFRESH_001", "刷新令牌无效或已撤销"),
    AUTH_CAPTCHA_001(400, "AUTH_CAPTCHA_001", "验证码无效或已过期"),
    AUTH_CODE_001(400, "AUTH_CODE_001", "验证码无效或已过期"),
    AUTH_ACCOUNT_001(401, "AUTH_ACCOUNT_001", "账号已禁用或锁定"),

    // 鉴权类 403
    ACCESS_DENIED_001(403, "ACCESS_DENIED_001", "权限不足"),
    ACCESS_OWNERSHIP_001(403, "ACCESS_OWNERSHIP_001", "无权访问该资源"),

    // 资源不存在 404
    RESOURCE_NOT_FOUND_001(404, "RESOURCE_NOT_FOUND_001", "资源不存在或不可见"),

    // 状态/冲突 409
    VERSION_CONFLICT_001(409, "VERSION_CONFLICT_001", "对象已被他人修改，请刷新后重试"),
    BOOKING_CONFLICT_001(409, "BOOKING_CONFLICT_001", "所选资源已被预约"),
    ORDER_STOCK_001(409, "ORDER_STOCK_001", "库存不足"),

    // 文件大小 413
    FILE_SIZE_001(413, "FILE_SIZE_001", "文件大小超过上限"),

    // 业务规则 422
    BUSINESS_RULE_001(422, "BUSINESS_RULE_001", "业务规则校验未通过"),
    PET_STATE_001(422, "PET_STATE_001", "宠物状态迁移非法"),
    USER_DUPLICATE_001(422, "USER_DUPLICATE_001", "账号或邮箱已存在"),

    // 限流 429
    RATE_LIMIT_001(429, "RATE_LIMIT_001", "请求过于频繁，请稍后重试"),
    RATE_LIMIT_CAPTCHA_001(429, "RATE_LIMIT_CAPTCHA_001", "验证码获取过于频繁"),
    RATE_LIMIT_EMAIL_001(429, "RATE_LIMIT_EMAIL_001", "邮件发送过于频繁"),

    // 外部服务 502/503
    AI_PROVIDER_001(503, "AI_PROVIDER_001", "AI 服务暂不可用"),
    EXTERNAL_SERVICE_001(502, "EXTERNAL_SERVICE_001", "外部服务调用失败"),

    // 兜底 500
    INTERNAL_ERROR_001(500, "INTERNAL_ERROR_001", "系统内部错误");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
