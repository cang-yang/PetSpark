package com.petspark.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * notification 表仓储。落库走 MyBatis Mapper（主键冲突即幂等）；
 * 统计与清理用 JdbcTemplate 复用同一数据源。
 */
@Repository
public class NotificationRepository {

    private final NotificationMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(NotificationMapper mapper, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 落库一条通知。返回 1 表示新插入，0 表示已存在（幂等命中，重复投递同事件）。
     */
    public boolean insert(Notification notification) {
        return mapper.insert(notification) == 1;
    }

    public Optional<Notification> findByIdAndRecipient(String id, String recipientId) {
        return mapper.findByIdAndRecipient(id, recipientId);
    }

    public List<Notification> findByRecipient(String recipientId, boolean onlyUnread, long offset, int size) {
        return mapper.findByRecipient(recipientId, onlyUnread, offset, size);
    }

    /**
     * 幂等标记已读。返回 1 表示本次置位成功，0 表示已读或不存在。
     */
    public boolean markRead(String id, String recipientId) {
        return mapper.markRead(id, recipientId, Instant.now()) == 1;
    }

    /**
     * 幂等全部已读。返回实际置位条数。
     */
    public int markAllRead(String recipientId) {
        return mapper.markAllRead(recipientId, Instant.now());
    }

    public long countUnread(String recipientId) {
        return mapper.countUnread(recipientId);
    }

    public long countTotal(String recipientId) {
        return mapper.countTotal(recipientId);
    }

    /**
     * 清理本人所有通知，仅测试用于隔离。
     */
    public void deleteByRecipient(String recipientId) {
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id = ?", recipientId);
    }
}
