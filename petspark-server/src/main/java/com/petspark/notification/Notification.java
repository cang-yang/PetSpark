package com.petspark.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

/**
 * 站内通知领域模型，对应 {@code notification} 表（05-数据库设计说明书 §9）。
 *
 * <p>通知只有 {@code UNREAD/READ} 语义：{@code readAt} 为空即未读，置位后不可恢复。
 * {@code id} 与触发它的 outbox 事件 id 一致，使 {@link NotificationRepository#insert}
 * 天然按事件 id 幂等——重复投递同一事件不会产生第二条通知。
 */
@JsonPropertyOrder({"id", "recipientId", "type", "title", "content",
        "businessType", "businessId", "readAt", "createdAt"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Notification {

    private final String id;
    private final String recipientId;
    private final String type;
    private final String title;
    private final String content;
    private final String businessType;
    private final String businessId;
    private final Instant readAt;
    private final Instant createdAt;

    public Notification(String id, String recipientId, String type, String title, String content,
                       String businessType, String businessId, Instant readAt, Instant createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.businessType = businessType;
        this.businessId = businessId;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getBusinessType() { return businessType; }
    public String getBusinessId() { return businessId; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isRead() {
        return readAt != null;
    }
}
