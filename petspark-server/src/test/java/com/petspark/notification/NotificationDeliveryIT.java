package com.petspark.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.petspark.AbstractIntegrationTest;
import com.petspark.common.event.OutboxEvent;
import com.petspark.common.event.OutboxRepository;
import com.petspark.common.event.OutboxService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR-NOTIFY-01 核心契约验收（T-NOTIFY）：
 * <ul>
 *   <li>事件与业务事务原子性：业务回滚 → outbox 事件一并丢弃，notification 不出现；</li>
 *   <li>投递成功 → 事件推进 SENT，notification 行落库；</li>
 *   <li>重复消费幂等：同事件 id 重复投递只产生一条 notification；</li>
 *   <li>失败重试/死信：载荷非法时 attemptCount 累加，达上限进 DEAD；</li>
 *   <li>业务成功不因通知投递失败回滚——投递失败只推进 outbox 状态，业务事件仍在。</li>
 * </ul>
 */
class NotificationDeliveryIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final List<String> userIds = new ArrayList<>();
    private final List<String> eventIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : eventIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM outbox_event WHERE id = ?", id);
        }
        eventIds.clear();
        for (String uid : userIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE recipient_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", uid);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", uid);
        }
        userIds.clear();
    }

    @Test
    void eventSharesBusinessTransactionRollback() {
        String userId = createUser("notif_rb");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            notificationService.send(new NotificationPayload(
                    userId, "SYSTEM", "标题", "正文", null, null));
            status.setRollbackOnly();
        });

        // 业务回滚：outbox 事件应一并丢弃，notification 自然不出现。
        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'notification.send' "
                        + "AND payload LIKE ?",
                Long.class, "%" + userId + "%");
        assertThat(eventCount).isZero();
        Long notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE recipient_id = ?", Long.class, userId);
        assertThat(notifCount).isZero();
    }

    @Test
    void dispatchedEventProducesNotificationAndMovesToSent() {
        String userId = createUser("notif_ok");
        String eventId = notificationService.send(new NotificationPayload(
                userId, "ADOPTION_RESULT", "审核通过", "您的领养申请已通过", "ADOPTION", "app-1"));
        eventIds.add(eventId);

        dispatcher.dispatchOnce();

        OutboxEvent reloaded = outboxRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEvent.Status.SENT);

        Notification n = notificationRepository.findByIdAndRecipient(eventId, userId).orElseThrow();
        assertThat(n.getType()).isEqualTo("ADOPTION_RESULT");
        assertThat(n.getTitle()).isEqualTo("审核通过");
        assertThat(n.getContent()).isEqualTo("您的领养申请已通过");
        assertThat(n.getBusinessType()).isEqualTo("ADOPTION");
        assertThat(n.getBusinessId()).isEqualTo("app-1");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void duplicateDispatchIsIdempotent() {
        String userId = createUser("notif_idem");
        String eventId = notificationService.send(new NotificationPayload(
                userId, "SYSTEM", "重复投递", "应只产生一条", null, null));
        eventIds.add(eventId);

        dispatcher.dispatchOnce();
        // 第二轮：事件已 SENT，claimPending 返回 0，不重复落库。
        dispatcher.dispatchOnce();

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?", Long.class, eventId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void failedDispatchRetriesThenDeadLetters() {
        String userId = createUser("notif_fail");
        // 直接追加一条载荷非法的事件（content 缺失），dispatcher 解析失败应重试/死信。
        String badId = UUID.randomUUID().toString();
        eventIds.add(badId);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbcTemplate.update(
                        "INSERT INTO outbox_event (id, event_type, aggregate_type, aggregate_id, payload, status, attempt_count, created_at) "
                                + "VALUES (?, ?, ?, ?, CAST(? AS JSON), 'PENDING', 0, CURRENT_TIMESTAMP(3))",
                        badId, "notification.send", "notification", badId,
                        "{\"recipientId\":\"" + userId + "\",\"type\":\"SYSTEM\",\"title\":\"t\"}"));

        // dispatcher maxAttempts 默认 5；连续 6 轮后应进入 DEAD。
        for (int i = 0; i < 6; i++) {
            dispatcher.dispatchOnce();
        }

        OutboxEvent reloaded = outboxRepository.findById(badId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEvent.Status.DEAD);
        assertThat(reloaded.getAttemptCount()).isGreaterThanOrEqualTo(5);
        // 死信事件不产生 notification。
        Long notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?", Long.class, badId);
        assertThat(notifCount).isZero();
    }

    @Test
    void businessCommitSurvivesNotificationDeliveryFailure() {
        // "业务成功 + 通知投递失败"：事件已随业务事务提交（库中存在），
        // 随后投递失败只推进 outbox 状态，不回滚已提交业务。
        String userId = createUser("notif_survive");
        String okId = notificationService.send(new NotificationPayload(
                userId, "SYSTEM", "正常", "正常投递", null, null));
        eventIds.add(okId);
        // 业务事务已提交（send 返回）。制造投递失败：把 payload 改成缺 content。
        jdbcTemplate.update(
                "UPDATE outbox_event SET payload = CAST(? AS JSON) WHERE id = ?",
                "{\"recipientId\":\"" + userId + "\",\"type\":\"SYSTEM\",\"title\":\"t\"}", okId);

        dispatcher.dispatchOnce();

        // 业务事件仍在（未回滚）。
        Long eventExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE id = ?", Long.class, okId);
        assertThat(eventExists).isEqualTo(1L);
        // notification 未落库（投递失败）。
        Long notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?", Long.class, okId);
        assertThat(notifCount).isZero();
    }

    @Test
    void dispatcherExposesBacklogAndDeadCounters() {
        // NFR-OBS-001：积压与失败可观测。计数查询不抛异常且语义非负。
        assertThat(outboxRepository.countByStatus(OutboxEvent.Status.PENDING)).isGreaterThanOrEqualTo(0L);
        assertThat(outboxRepository.countByStatus(OutboxEvent.Status.DEAD)).isGreaterThanOrEqualTo(0L);
    }

    private String createUser(String prefix) {
        String id = UUID.randomUUID().toString();
        String username = prefix + "_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", "$2a$10$test", prefix);
        userIds.add(id);
        return id;
    }
}
