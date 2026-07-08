package com.petspark.notification;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.petspark.common.api.PageResult;
import java.util.List;

/**
 * 通知分页结果视图。{@code unreadCount} 为本人未读总数，便于前端角标展示。
 * {@code items} 为当前页通知；{@code total} 为查询条件命中总数。
 */
@JsonPropertyOrder({"items", "page", "size", "total", "unreadCount"})
public final class NotificationPageView {

    private final List<NotificationView> items;
    private final long page;
    private final long size;
    private final long total;
    private final long unreadCount;

    public NotificationPageView(List<NotificationView> items, long page, long size, long total, long unreadCount) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.unreadCount = unreadCount;
    }

    public List<NotificationView> getItems() { return items; }
    public long getPage() { return page; }
    public long getSize() { return size; }
    public long getTotal() { return total; }
    public long getUnreadCount() { return unreadCount; }

    public static NotificationPageView from(PageResult<NotificationView> page, long unreadCount) {
        return new NotificationPageView(page.getItems(), page.getPage(), page.getSize(), page.getTotal(), unreadCount);
    }
}
