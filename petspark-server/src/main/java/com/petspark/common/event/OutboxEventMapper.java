package com.petspark.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * outbox_event 表的 MyBatis 映射。
 *
 * <p>PR-BASE-02 只需查询能力（save 通过 XML/Provider 由 MyBatis-Plus BaseMapper
 * 的 insert 覆盖，本接口为后续 PR 扩展查询保留显式签名）。{@code findPending}
 * 直接用注解 SQL，避免为单查询引入 XML。
 */
@Mapper
public interface OutboxEventMapper {

    /**
     * 插入一条 outbox 事件。字段与 OutboxEvent 一一对应。
     */
    void insert(OutboxEvent event);

    /**
     * 待投递事件：status=PENDING 且 next_attempt_at 为空或已到期，按 created_at 升序。
     */
    @Select("SELECT id, event_type, aggregate_type, aggregate_id, payload, status, "
            + "attempt_count, next_attempt_at, created_at, processed_at "
            + "FROM outbox_event "
            + "WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= #{now}) "
            + "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> findPending(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 按 id 查询。
     */
    @Select("SELECT id, event_type, aggregate_type, aggregate_id, payload, status, "
            + "attempt_count, next_attempt_at, created_at, processed_at "
            + "FROM outbox_event WHERE id = #{id}")
    Optional<OutboxEvent> findById(@Param("id") String id);
}
