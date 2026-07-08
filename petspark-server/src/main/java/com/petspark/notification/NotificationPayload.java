package com.petspark.notification;

/**
 * 通知投递事件载荷。业务模块在事务内通过
 * {@link com.petspark.common.event.OutboxService#append} 追加此载荷，
 * {@link OutboxDispatcher} 异步读出并落库为 notification 行。
 *
 * <p>字段与 notification 表的展示字段对齐：type/title/content 为必填，
 * businessType/businessId 可空。recipientId 必填，决定通知归属与隔离。
 */
public record NotificationPayload(
        String recipientId,
        String type,
        String title,
        String content,
        String businessType,
        String businessId
) {
    public NotificationPayload {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
