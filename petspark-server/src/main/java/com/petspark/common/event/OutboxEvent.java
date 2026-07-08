package com.petspark.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

/**
 * Outbox 事件领域模型。与 {@code outbox_event} 表一一对应，承载与业务数据同事务
 * 写入的事件载荷，异步消费者据此完成对外发布（通知、AI、第三方）。
 *
 * <p>状态机：{@code PENDING → PROCESSING → SENT}；失败重试到上限进入
 * {@code DEAD}。{@code nextAttemptAt} 为空表示立即可投递。
 *
 * <p>接口侧不直接暴露 OutboxEvent；它由 {@link OutboxService} 在事务内追加，
 * 由调度器/消费者轮询投递。
 */
@JsonPropertyOrder({"id", "eventType", "aggregateType", "aggregateId", "payload", "status",
        "attemptCount", "nextAttemptAt", "createdAt", "processedAt"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OutboxEvent {

    public enum Status {
        PENDING, PROCESSING, SENT, DEAD
    }

    private final String id;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final String payload;
    private final Status status;
    private final int attemptCount;
    private final Instant nextAttemptAt;
    private final Instant createdAt;
    private final Instant processedAt;

    public OutboxEvent(String id, String eventType, String aggregateType, String aggregateId,
                       String payload, Status status, int attemptCount,
                       Instant nextAttemptAt, Instant createdAt, Instant processedAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public String getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
