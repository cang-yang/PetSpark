package com.petspark.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

/**
 * 通知列表项 / 详情视图。字段顺序与接口设计 §8 一致。
 * {@code read} 派生自 {@code readAt != null}，便于前端按未读/已读展示。
 */
@JsonPropertyOrder({"id", "type", "title", "content", "businessType", "businessId",
        "read", "readAt", "createdAt"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class NotificationView {

    private final String id;
    private final String type;
    private final String title;
    private final String content;
    private final String businessType;
    private final String businessId;
    private final boolean read;
    private final Instant readAt;
    private final Instant createdAt;

    public NotificationView(Notification n) {
        this.id = n.getId();
        this.type = n.getType();
        this.title = n.getTitle();
        this.content = n.getContent();
        this.businessType = n.getBusinessType();
        this.businessId = n.getBusinessId();
        this.read = n.isRead();
        this.readAt = n.getReadAt();
        this.createdAt = n.getCreatedAt();
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getBusinessType() { return businessType; }
    public String getBusinessId() { return businessId; }
    public boolean isRead() { return read; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
