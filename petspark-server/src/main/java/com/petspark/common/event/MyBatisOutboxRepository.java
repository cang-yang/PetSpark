package com.petspark.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link OutboxRepository} 的 MyBatis 实现。{@link #save} 依赖调用方已开启事务，
 * 与业务写操作在同一事务提交/回滚；这是同事务 Outbox 的核心保证。
 *
 * <p>状态机方法（claimPending / markSent / markFailed / countByStatus）由
 * {@link com.petspark.notification.OutboxDispatcher} 在调度线程调用，使用各自的
 * 自动提交事务，与业务事务解耦——投递失败只推进事件状态，不回滚已提交业务。
 */
@Repository
public class MyBatisOutboxRepository implements OutboxRepository {

    private final OutboxEventMapper mapper;

    public MyBatisOutboxRepository(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OutboxEvent event) {
        mapper.insert(event);
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return mapper.findPending(Instant.now(), limit);
    }

    @Override
    public Optional<OutboxEvent> findById(String id) {
        return mapper.findById(id);
    }

    @Override
    public int claimPending(String id) {
        return mapper.claimPending(id);
    }

    @Override
    public void markSent(String id) {
        mapper.markSent(id, Instant.now());
    }

    @Override
    public void markFailed(String id, int attemptCount, Instant nextAttemptAt, boolean dead) {
        mapper.markFailed(id, attemptCount, nextAttemptAt, dead);
    }

    @Override
    public long countByStatus(OutboxEvent.Status status) {
        return mapper.countByStatus(status.name());
    }
}
