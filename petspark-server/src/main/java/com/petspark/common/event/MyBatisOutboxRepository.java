package com.petspark.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link OutboxRepository} 的 MyBatis 实现。{@link #save} 依赖调用方已开启事务，
 * 与业务写操作在同一事务提交/回滚。
 *
 * <p>这是 common 层对 outbox_event 的唯一实现；后续 PR 若引入复杂投递调度，
 * 可扩展或替换，但同事务写入的语义不变。
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
}
