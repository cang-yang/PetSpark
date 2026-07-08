package com.petspark.common.event;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 事件仓储端口。PR-BASE-02 只提供同事务追加与查询能力；
 * 投递调度、状态推进、死信处理在 PR-NOTIF-01 实现。
 *
 * <p>实现必须保证 {@link #save} 与触发它的业务写操作处于同一事务：
 * 业务回滚时事件一并丢弃，业务提交时事件一并可见。这是 Outbox 模式的核心约束。
 */
public interface OutboxRepository {

    /**
     * 追加一条 PENDING 事件。调用方负责提供唯一 id。
     */
    void save(OutboxEvent event);

    /**
     * 查询待投递事件（PENDING 且已到投递时间），按创建时间升序，最多 limit 条。
     */
    List<OutboxEvent> findPending(int limit);

    /**
     * 按 id 查询，主要用于幂等校验与重试。
     */
    Optional<OutboxEvent> findById(String id);
}
