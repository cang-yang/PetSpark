package com.petspark.notification;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Outbox 投递状态计数视图，用于运营/运维观测积压与失败（NFR-OBS-001）。
 *
 * <p>四态计数：PENDING（待投递积压）、PROCESSING（认领中）、SENT（已完成）、
 * DEAD（死信，达 maxAttempts 仍失败）。运营通过 PENDING 与 DEAD 两个指标判断
 * 投递链路健康：PENDING 持续增长说明下游堵塞，DEAD 增长说明载荷/下游不可恢复失败。
 *
 * <p>本视图只读，不含任何业务载荷或用户标识，泄露面最小。
 */
@JsonPropertyOrder({"pending", "processing", "sent", "dead"})
public final class OutboxStatusView {

    private final long pending;
    private final long processing;
    private final long sent;
    private final long dead;

    public OutboxStatusView(long pending, long processing, long sent, long dead) {
        this.pending = pending;
        this.processing = processing;
        this.sent = sent;
        this.dead = dead;
    }

    public long getPending() { return pending; }
    public long getProcessing() { return processing; }
    public long getSent() { return sent; }
    public long getDead() { return dead; }
}
