package com.petspark.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.petspark.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 Outbox 同事务提交与回滚契约（PR-BASE-02 验收）：
 * <ul>
 *   <li>事务提交 → 业务行与 outbox 事件一并可见；</li>
 *   <li>事务回滚 → outbox 事件一并丢弃，不留半状态；</li>
 *   <li>载荷以 JSON 形式落库且可读回。</li>
 * </ul>
 */
class OutboxTransactionIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> insertedEventIds = new ArrayList<>();

    @AfterEach
    void removeInsertedEvents() {
        insertedEventIds.forEach(id ->
                jdbcTemplate.update("DELETE FROM outbox_event WHERE id = ?", id));
        insertedEventIds.clear();
    }

    @Test
    void appendPersistsEventOnCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String aggregateId = UUID.randomUUID().toString();

        OutboxEvent saved = tx.execute(status ->
                outboxService.append("test.pet.created", "pet", aggregateId,
                        Map.of("name", "Cookie", "species", "dog")));
        insertedEventIds.add(saved.getId());

        Optional<OutboxEvent> reloaded = outboxRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getEventType()).isEqualTo("test.pet.created");
        assertThat(reloaded.get().getAggregateId()).isEqualTo(aggregateId);
        assertThat(reloaded.get().getPayload()).contains("Cookie");
        assertThat(reloaded.get().getStatus()).isEqualTo(OutboxEvent.Status.PENDING);

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE id = ?", Long.class, saved.getId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void appendDiscardsEventOnRollback() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String aggregateId = UUID.randomUUID().toString();

        tx.executeWithoutResult(status -> {
            outboxService.append("test.pet.created", "pet", aggregateId, Map.of("name", "Rollback"));
            status.setRollbackOnly();
        });

        // 事件 id 由 append 生成，但事务回滚后不应在库里出现。
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?", Long.class, aggregateId);
        assertThat(count).isZero();
    }

    @Test
    void findPendingReturnsOnlyReadyEvents() {
        OutboxEvent saved = outboxService.append("test.notification.send", "notification",
                UUID.randomUUID().toString(), Map.of("body", "hi"));
        insertedEventIds.add(saved.getId());

        // 没有 nextAttemptAt 的 PENDING 事件应被查到。
        var pending = outboxRepository.findPending(1000);
        assertThat(pending).isNotEmpty();
        assertThat(pending)
                .anySatisfy(e -> {
                    assertThat(e.getId()).isEqualTo(saved.getId());
                    assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
                });
    }
}
