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
    ORDER_IDEMPOTENCY_001(409, "ORDER_IDEMPOTENCY_001", "幂等键已关联不同请求"),
    GOODS_NOT_FOUND_001(404, "GOODS_NOT_FOUND", "商品不存在或不可见"),
    GOODS_SKU_001(422, "GOODS_SKU_001", "商品 SKU 已存在"),
    GOODS_STATE_001(422, "GOODS_STATE_001", "商品状态不允许该操作"),
    ORDER_NOT_FOUND_001(404, "ORDER_NOT_FOUND_001", "订单不存在或不可见"),
    ORDER_STATE_001(422, "ORDER_STATE_001", "订单状态不允许该操作"),

    // 文件大小 413
    FILE_SIZE_001(413, "FILE_SIZE_001", "文件大小超过上限"),

    // 文件校验 400/404
    FILE_TYPE_001(400, "FILE_TYPE_001", "文件类型或文件名不合法"),
    FILE_CONTENT_001(400, "FILE_CONTENT_001", "文件内容与声明类型不一致"),
    FILE_NOT_FOUND_001(404, "FILE_NOT_FOUND_001", "文件不存在或不可用"),

    // 业务规则 422
    BUSINESS_RULE_001(422, "BUSINESS_RULE_001", "业务规则校验未通过"),
    PET_BREED_001(422, "PET_BREED_001", "宠物品种不可用"),
    BREED_DUPLICATE_001(422, "BREED_DUPLICATE_001", "品种名称已存在"),
    PET_REFERENCED_001(422, "PET_REFERENCED_001", "宠物已被业务引用，不能删除"),
    PET_STATE_001(422, "PET_STATE_001", "宠物状态迁移非法"),
    ADOPTION_NOT_FOUND_001(404, "ADOPTION_NOT_FOUND_001", "领养申请不存在或不可见"),
    ADOPTION_DUPLICATE_001(409, "ADOPTION_DUPLICATE_001", "该宠物已有未结束的领养申请"),
    ADOPTION_STATE_001(422, "ADOPTION_STATE_001", "领养申请状态不允许该操作"),
    ADOPTION_HANDOVER_001(422, "ADOPTION_HANDOVER_001", "交接失败，已恢复宠物可领养状态"),
    HEALTH_SCOPE_001(422, "HEALTH_SCOPE_001", "不在健康记录授权范围"),
    HEALTH_RETENTION_001(422, "HEALTH_RETENTION_001", "健康记录保留策略不允许该操作"),
    USER_DUPLICATE_001(422, "USER_DUPLICATE_001", "账号或邮箱已存在"),

    // 限流 429
    RATE_LIMIT_001(429, "RATE_LIMIT_001", "请求过于频繁，请稍后重试"),
    RATE_LIMIT_CAPTCHA_001(429, "RATE_LIMIT_CAPTCHA_001", "验证码获取过于频繁"),
    RATE_LIMIT_EMAIL_001(429, "RATE_LIMIT_EMAIL_001", "邮件发送过于频繁"),

    // AI 网关 401/403/422/429/502/503
    AI_DISABLED_001(503, "AI_DISABLED_001", "AI 服务未启用或未配置"),
    AI_PROVIDER_AUTH_001(401, "AI_PROVIDER_AUTH_001", "AI 供应商鉴权失败"),
    AI_PROVIDER_LIMIT_001(429, "AI_PROVIDER_LIMIT_001", "AI 供应商额度或频率限制"),
    AI_CONSENT_001(403, "AI_CONSENT_001", "未同意或已撤回 AI 服务协议"),
    AI_SAFETY_001(422, "AI_SAFETY_001", "输入违反安全策略，已拒答"),
    AI_CONTEXT_001(422, "AI_CONTEXT_001", "上下文超限，请缩短输入"),
    AI_OUTPUT_001(502, "AI_OUTPUT_001", "AI 输出格式无效"),

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
