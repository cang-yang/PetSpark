package com.petspark.common.event;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 事件仓储端口。PR-BASE-02 提供同事务追加与查询能力；
 * PR-NOTIFY-01 在此基础上补齐投递状态机（认领、成功、失败重试/死信）、僵死事件回收
 * 与可观测计数。
 *
 * <p>实现必须保证 {@link #save} 与触发它的业务写操作处于同一事务：
 * 业务回滚时事件一并丢弃，业务提交时事件一并可见。这是 Outbox 模式的核心约束。
 *
 * <p>状态机方法（{@link #claimPending}、{@link #markSent}、{@link #markFailed}、
 * {@link #reclaimStaleProcessing}）各自独立提交，不与业务事务共享：投递失败只推进事件
 * 状态，绝不回滚已提交的业务（验收：业务成功不因通知发送失败回滚）。
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

    /**
     * 原子认领一条 PENDING 事件为 PROCESSING（仅当当前状态仍为 PENDING 时成功）。
     * 返回受影响行数：1 表示认领成功，0 表示已被其他消费者认领或状态已变。
     * 单实例调度下用于避免重复消费；多实例下提供乐观互斥。
     */
    int claimPending(String id);

    /**
     * 回收僵死事件：将 PROCESSING 且早于 {@code staleBefore} 仍卡住的事件重置回 PENDING，
     * 返回回收条数。用于消费者认领后崩溃、重启后恢复待投递事件，
     * 满足验收「Outbox 保留待恢复事件」与「停止消费者后 Outbox 保留待恢复事件」的回滚要求。
     */
    int reclaimStaleProcessing(java.time.Instant staleBefore);

    /**
     * 标记事件投递成功：状态置 SENT，记录处理时间，清空下次尝试时间。
     */
    void markSent(String id);

    /**
     * 标记事件投递失败并推进重试/死信状态。
     *
     * @param id            事件 id
     * @param attemptCount  失败后的累计尝试次数（调用方计算 = 当前 + 1）
     * @param nextAttemptAt 下次尝试时间；{@code dead=true} 时传 null
     * @param dead          是否已达上限进入死信
     */
    void markFailed(String id, int attemptCount, java.time.Instant nextAttemptAt, boolean dead);

    /**
     * 按状态统计事件数量，用于积压与失败可观测（NFR-OBS-001）。
     */
    long countByStatus(OutboxEvent.Status status);
}
