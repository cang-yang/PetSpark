package com.petspark.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.common.event.OutboxEvent;
import com.petspark.common.event.OutboxRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Outbox 投递调度器：周期性认领 PENDING 的 {@code notification.send} 事件，
 * 解析载荷落库为 notification 行，并推进事件状态机。
 *
 * <p>投递不在业务事务内执行：认领（PROCESSING）→ 落库 notification → 标记 SENT。
 * 任一步失败只推进 outbox 事件状态（attemptCount++、退避重试或 DEAD），不回滚
 * 已提交的业务——满足验收「业务成功不因通知发送失败回滚」。
 *
 * <p>幂等保证：notification.id = outbox 事件 id，主键冲突时落库返回 0，
 * 视为重复消费并直接标记 SENT，不会产生第二条通知。
 *
 * <p>状态机：
 * <ul>
 *   <li>PENDING → PROCESSING（claimPending 原子认领，0 行表示已被消费）→ SENT；</li>
 *   <li>失败：attemptCount++，未达上限回 PENDING + nextAttemptAt 退避，达上限进 DEAD。</li>
 * </ul>
 *
 * <p>可观测：日志记录每轮认领数与失败事件，{@link OutboxRepository#countByStatus}
 * 暴露积压（PENDING）与失败（DEAD）计数（NFR-OBS-001）。
 *
 * <p>本类用 {@link ScheduledExecutorService} 单线程轮询，适合单实例实训项目；
 * 多实例部署时 claimPending 的原子条件更新提供乐观互斥，避免重复消费。
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    static final String NOTIFICATION_EVENT_TYPE = "notification.send";

    private final OutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    private final int batchSize;
    private final long pollIntervalSeconds;
    private final int maxAttempts;
    private final Duration backoff;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxDispatcher(
            OutboxRepository outboxRepository,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            @Value("${petspark.notification.dispatcher.batch-size:20}") int batchSize,
            @Value("${petspark.notification.dispatcher.poll-interval-seconds:5}") long pollIntervalSeconds,
            @Value("${petspark.notification.dispatcher.max-attempts:5}") int maxAttempts,
            @Value("${petspark.notification.dispatcher.backoff-seconds:30}") long backoffSeconds) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.maxAttempts = maxAttempts;
        this.backoff = Duration.ofSeconds(backoffSeconds);
    }

    @PostConstruct
    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "petspark-outbox-dispatcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::dispatchOnce, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("OutboxDispatcher started: batchSize={} pollInterval={}s maxAttempts={} backoff={}s",
                batchSize, pollIntervalSeconds, maxAttempts, backoff.getSeconds());
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 单轮投递：拉取一批 pending 事件逐条处理。包级可见便于测试同步调用。
     */
    void dispatchOnce() {
        if (!running.get()) {
            return;
        }
        List<OutboxEvent> pending;
        try {
            pending = outboxRepository.findPending(batchSize);
        } catch (RuntimeException ex) {
            log.warn("OutboxDispatcher findPending failed: {}", ex.getMessage());
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (OutboxEvent event : pending) {
            if (!NOTIFICATION_EVENT_TYPE.equals(event.getEventType())) {
                continue;
            }
            if (deliver(event) == DeliveryOutcome.SENT) {
                delivered++;
            }
        }
        log.debug("OutboxDispatcher processed {} event(s), delivered {}", pending.size(), delivered);
    }

    /**
     * 处理单条事件的状态机。包级可见便于测试。
     */
    DeliveryOutcome deliver(OutboxEvent event) {
        // 原子认领，避免重复消费。
        if (outboxRepository.claimPending(event.getId()) == 0) {
            return DeliveryOutcome.ALREADY_CLAIMED;
        }
        try {
            NotificationPayload payload = objectMapper.readValue(event.getPayload(), NotificationPayload.class);
            Notification notification = new Notification(
                    event.getId(),
                    payload.recipientId(),
                    payload.type(),
                    payload.title(),
                    payload.content(),
                    payload.businessType(),
                    payload.businessId(),
                    null,
                    Instant.now());
            // 幂等落库：主键冲突返回 false（重复消费），仍视为成功投递。
            notificationRepository.insert(notification);
            outboxRepository.markSent(event.getId());
            return DeliveryOutcome.SENT;
        } catch (RuntimeException | java.io.IOException ex) {
            int attempt = event.getAttemptCount() + 1;
            boolean dead = attempt >= maxAttempts;
            Instant nextAttemptAt = dead ? null : Instant.now().plus(backoff);
            outboxRepository.markFailed(event.getId(), attempt, nextAttemptAt, dead);
            if (dead) {
                log.error("OutboxDispatcher event {} moved to DEAD after {} attempts: {}",
                        event.getId(), attempt, ex.getMessage());
            } else {
                log.warn("OutboxDispatcher event {} failed attempt {}/{}, retry in {}: {}",
                        event.getId(), attempt, maxAttempts, backoff, ex.getMessage());
            }
            return DeliveryOutcome.FAILED;
        }
    }

    enum DeliveryOutcome {
        SENT, FAILED, ALREADY_CLAIMED
    }
}
