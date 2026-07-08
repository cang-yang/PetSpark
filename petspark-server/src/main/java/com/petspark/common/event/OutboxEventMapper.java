package com.petspark.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * outbox_event 表的 MyBatis 映射。
 *
 * <p>PR-BASE-02 只需查询能力（save 通过 XML 由映射覆盖，findPending/findById 用注解 SQL）；
 * PR-NOTIFY-01 增补投递状态机：claimPending 原子认领，markSent/markFailed 推进状态，
 * countByStatus 用于积压与失败可观测。
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

    /**
     * 原子认领：仅当状态仍为 PENDING 时改为 PROCESSING，返回受影响行数（0/1）。
     */
    @Update("UPDATE outbox_event SET status = 'PROCESSING' "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int claimPending(@Param("id") String id);

    /**
     * 投递成功：状态置 SENT，记录处理时间，清空下次尝试时间。
     */
    @Update("UPDATE outbox_event SET status = 'SENT', processed_at = #{at}, next_attempt_at = NULL "
            + "WHERE id = #{id}")
    void markSent(@Param("id") String id, @Param("at") Instant at);

    /**
     * 投递失败：累加尝试次数，更新状态与下次尝试时间。dead=true 时状态置 DEAD、下次尝试置空。
     */
    @Update("<script>"
            + "UPDATE outbox_event SET attempt_count = #{attemptCount}, "
            + "status = <choose><when test='dead'> 'DEAD' </when><otherwise> 'PENDING' </otherwise></choose>, "
            + "next_attempt_at = "
            + "<choose><when test='nextAttemptAt != null'> #{nextAttemptAt} </when><otherwise> NULL </otherwise></choose> "
            + "WHERE id = #{id}"
            + "</script>")
    void markFailed(@Param("id") String id, @Param("attemptCount") int attemptCount,
                    @Param("nextAttemptAt") Instant nextAttemptAt, @Param("dead") boolean dead);

    /**
     * 按状态统计事件数量。
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
