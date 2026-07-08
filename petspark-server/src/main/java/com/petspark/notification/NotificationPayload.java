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
    /** 与 notification 表列长对齐，防止超长载荷落库时被 MySQL 截断（严格模式下会抛错）。 */
    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 512;
    private static final int TYPE_MAX = 32;

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
        if (type.length() > TYPE_MAX) {
            throw new IllegalArgumentException("type exceeds " + TYPE_MAX + " chars");
        }
        if (title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("title exceeds " + TITLE_MAX + " chars");
        }
        if (content.length() > CONTENT_MAX) {
            throw new IllegalArgumentException("content exceeds " + CONTENT_MAX + " chars");
        }
    }
}
