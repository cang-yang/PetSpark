package com.petspark.notification;

import com.petspark.common.api.PageQuery;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 通知查询应用服务：本人通知列表、单条已读、全部已读、未读计数。
 *
 * <p>本人隔离：所有查询与已读都以 {@code recipientId = 当前用户} 为条件，
 * 不允许跨用户读取或标记他人通知（API-NOTIFY-002 权限=通知本人）。
 * 单条已读按 id + recipientId 查询，找不到一律返回 RESOURCE_NOT_FOUND_001，
 * 不区分"不存在"与"不属于本人"，避免泄露存在性。
 *
 * <p>幂等已读：{@link NotificationRepository#markRead} 仅当 read_at 为空时置位，
 * 重复调用返回幂等成功；已读不可恢复——不存在取消已读接口。
 */
@Service
public class NotificationQueryService {

    private final NotificationRepository repository;

    public NotificationQueryService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询本人通知分页。
     *
     * @param recipientId 当前用户
     * @param query        分页参数（page 从 1 起，size 1..100）
     * @param onlyUnread   是否只看未读
     */
    public NotificationPageView list(String recipientId, PageQuery query, boolean onlyUnread) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
        }
        List<Notification> rows = repository.findByRecipient(
                recipientId, onlyUnread, query.offset(), query.getSize());
        List<NotificationView> items = rows.stream().map(NotificationView::new).toList();
        long total = onlyUnread ? repository.countUnread(recipientId) : repository.countTotal(recipientId);
        long unreadCount = repository.countUnread(recipientId);
        PageResult<NotificationView> page = new PageResult<>(items, query.getPage(), query.getSize(), total);
        return NotificationPageView.from(page, unreadCount);
    }

    /**
     * 幂等标记单条已读。已读或不存在都视为成功（幂等）；
     * 但为防止"他人通知被静默标读"，按 id+recipientId 定位，命中失败抛 NOT_FOUND。
     */
    public void markRead(String id, String recipientId) {
        if (id == null || id.isBlank() || recipientId == null || recipientId.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
        }
        // 先做归属校验：不存在或不属于本人都返回 NOT_FOUND，不泄露存在性。
        if (repository.findByIdAndRecipient(id, recipientId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
        }
        // 幂等置位：已读返回 false 也算成功。
        repository.markRead(id, recipientId);
    }

    /**
     * 幂等全部已读。返回实际置位条数。
     */
    public int markAllRead(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
        }
        return repository.markAllRead(recipientId);
    }

    /**
     * 本人未读计数。
     */
    public long countUnread(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
        }
        return repository.countUnread(recipientId);
    }
}
