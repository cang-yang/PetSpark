package com.petspark.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * notification 表的 MyBatis 映射。
 *
 * <p>落库走 XML {@code insert}（含受影响行数回填，用于幂等判定）；
 * 查询、已读、全部已读、统计用注解 SQL，避免为简单 CRUD 引入额外 XML。
 *
 * <p>{@link #insert} 以通知 id（= outbox 事件 id）为主键，重复投递同一事件时
 * 主键冲突使受影响行数为 0，从而实现按事件 id 幂等。
 */
@Mapper
public interface NotificationMapper {

    /**
     * 插入一条通知。主键为 id（与 outbox 事件 id 一致），重复插入主键冲突返回 0 行。
     */
    int insert(Notification notification);

    /**
     * 查询本人通知列表：按 read_at（未读优先）、created_at 降序分页。
     * {@code onlyUnread=true} 时只返回未读。
     */
    @Select("<script>"
            + "SELECT id, recipient_id, type, title, content, business_type, business_id, read_at, created_at "
            + "FROM notification "
            + "WHERE recipient_id = #{recipientId} "
            + "<if test='onlyUnread'> AND read_at IS NULL </if>"
            + "ORDER BY (read_at IS NULL) DESC, created_at DESC "
            + "LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<Notification> findByRecipient(@Param("recipientId") String recipientId,
                                       @Param("onlyUnread") boolean onlyUnread,
                                       @Param("offset") long offset,
                                       @Param("size") int size);

    /**
     * 按 id + 接收者查询，用于归属校验（本人隔离）。
     */
    @Select("SELECT id, recipient_id, type, title, content, business_type, business_id, read_at, created_at "
            + "FROM notification WHERE id = #{id} AND recipient_id = #{recipientId}")
    Optional<Notification> findByIdAndRecipient(@Param("id") String id,
                                                @Param("recipientId") String recipientId);

    /**
     * 幂等标记已读：仅当 read_at 为空时置位当前时间，返回受影响行数（0 表示已读或不存在）。
     * 已读不可恢复——再次调用不重置 read_at。
     */
    @Update("UPDATE notification SET read_at = #{readAt} "
            + "WHERE id = #{id} AND recipient_id = #{recipientId} AND read_at IS NULL")
    int markRead(@Param("id") String id,
                 @Param("recipientId") String recipientId,
                 @Param("readAt") Instant readAt);

    /**
     * 幂等全部已读：把本人所有未读置位当前时间，返回受影响行数。
     */
    @Update("UPDATE notification SET read_at = #{readAt} "
            + "WHERE recipient_id = #{recipientId} AND read_at IS NULL")
    int markAllRead(@Param("recipientId") String recipientId,
                    @Param("readAt") Instant readAt);

    /**
     * 统计本人未读数。
     */
    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id = #{recipientId} AND read_at IS NULL")
    long countUnread(@Param("recipientId") String recipientId);

    /**
     * 统计本人通知总数。
     */
    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id = #{recipientId}")
    long countTotal(@Param("recipientId") String recipientId);
}
