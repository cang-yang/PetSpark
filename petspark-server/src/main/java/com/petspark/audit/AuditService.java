package com.petspark.audit;

/**
 * 审计端口。业务模块在关键操作（登录、领养审批、健康记录修订、订单状态变更等）
 * 完成后调用 {@link #record} 落审计。
 *
 * <p>PR-BASE-02 提供默认实现 {@link AuditServiceImpl}，直接写 {@code audit_log}
 * 表；后续 PR 可替换为异步/Outbox 驱动实现，端口契约不变。
 *
 * <p>审计写入失败不应阻断业务主流程：默认实现捕获并记录错误日志，不向上抛。
 * 这与“审计可用性优先于一致性”的运维取舍一致（详见 NFR-OBS-002）。
 */
public interface AuditService {

    /**
     * 记录一条审计日志。
     *
     * @param context 操作上下文（actor、module、action、object 等）
     */
    void record(AuditContext context);

    /**
     * 记录成功操作。便捷重载，等价于 {@code record(context.withResult(SUCCESS))}。
     */
    void recordSuccess(AuditContext context);

    /**
     * 记录失败操作。便捷重载，等价于
     * {@code record(context.withResult(FAILURE).reasonCode(reasonCode))}。
     */
    void recordFailure(AuditContext context, String reasonCode);
}
