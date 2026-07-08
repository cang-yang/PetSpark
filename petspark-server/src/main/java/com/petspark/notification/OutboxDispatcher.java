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
 * <p>幂等保证：notification.id = outbox 事件 id，落库用 INSERT IGNORE，主键冲突返回 0，
 * 视为重复消费并直接标记 SENT，不会产生第二条通知。
 *
 * <p>状态机：
 * <ul>
 *   <li>PENDING → PROCESSING（claimPending 原子认领，0 行表示已被消费）→ SENT；</li>
 *   <li>失败：attemptCount++，未达上限回 PENDING + nextAttemptAt 退避，达上限进 DEAD；</li>
 *   <li>崩溃恢复：每轮先回收 stuckProcessingThreshold 之前仍卡在 PROCESSING 的事件
 *   （认领后未及 markSent/markFailed 进程即崩溃的情况），重置回 PENDING 重投，
 *   满足验收「停止消费者后 Outbox 保留待恢复事件」。</li>
 * </ul>
 *
 * <p>健壮性：{@link #dispatchOnce} 与 {@link #deliver} 的所有 DB 调用都包在 try-catch 内，
 * 任何单条事件或单轮查询的瞬时异常都被捕获并记日志，绝不向上抛——
 * 否则按 {@code scheduleWithFixedDelay} 契约会静默终止后续所有轮询。
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
    /** 认领后超过该时长仍卡在 PROCESSING 的事件视为僵死，下一轮回收回 PENDING。 */
    private final Duration stuckProcessingThreshold;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxDispatcher(
            OutboxRepository outboxRepository,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            @Value("${petspark.notification.dispatcher.batch-size:20}") int batchSize,
            @Value("${petspark.notification.dispatcher.poll-interval-seconds:5}") long pollIntervalSeconds,
            @Value("${petspark.notification.dispatcher.max-attempts:5}") int maxAttempts,
            @Value("${petspark.notification.dispatcher.backoff-seconds:30}") long backoffSeconds,
            @Value("${petspark.notification.dispatcher.stuck-processing-seconds:120}") long stuckSeconds) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.maxAttempts = maxAttempts;
        this.backoff = Duration.ofSeconds(backoffSeconds);
        this.stuckProcessingThreshold = Duration.ofSeconds(stuckSeconds);
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
        log.info("OutboxDispatcher started: batchSize={} pollInterval={}s maxAttempts={} backoff={}s stuckThreshold={}s",
                batchSize, pollIntervalSeconds, maxAttempts, backoff.getSeconds(), stuckProcessingThreshold.getSeconds());
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 单轮投递：先回收僵死 PROCESSING，再拉取一批 pending 逐条处理。包级可见便于测试同步调用。
     * 整体包在 try-catch：任何瞬时异常都记 WARN 并结束本轮，绝不向上抛——
     * 否则会按 scheduleWithFixedDelay 契约静默杀死后续所有轮询。
     */
    void dispatchOnce() {
        if (!running.get()) {
            return;
        }
        try {
            reclaimStaleProcessing();
            List<OutboxEvent> pending = outboxRepository.findPending(batchSize);
            if (pending.isEmpty()) {
                return;
            }
            int delivered = 0;
            for (OutboxEvent event : pending) {
                // 单条事件的处理异常由 deliver 内部捕获，不波及同批其他事件。
                if (NOTIFICATION_EVENT_TYPE.equals(event.getEventType())
                        && deliver(event) == DeliveryOutcome.SENT) {
                    delivered++;
                }
            }
            log.debug("OutboxDispatcher processed {} event(s), delivered {}", pending.size(), delivered);
        } catch (RuntimeException ex) {
            // 兜底：findPending/reclaim 等本轮 DB 操作异常，吞掉并等下一轮，避免杀死调度器。
            log.warn("OutboxDispatcher dispatchOnce failed, will retry next cycle: {}", ex.getMessage());
        }
    }

    /**
     * 回收认领后崩溃的僵死事件：created_at 早于 {@code now - stuckProcessingThreshold}
     * 且仍处 PROCESSING 的，重置回 PENDING。下次 findPending 即可重投。
     */
    private void reclaimStaleProcessing() {
        Instant cutoff = Instant.now().minus(stuckProcessingThreshold);
        int reclaimed = outboxRepository.reclaimStaleProcessing(cutoff);
        if (reclaimed > 0) {
            log.warn("OutboxDispatcher reclaimed {} stuck PROCESSING event(s) older than {}s",
                    reclaimed, stuckProcessingThreshold.getSeconds());
        }
    }

    /**
     * 处理单条事件的状态机。包级可见便于测试。
     * claimPending 与 markSent/markFailed 都在 try-catch 内：任一 DB 异常都走失败重试路径，
     * 不向上抛（避免单条异常杀死整批或调度器）。
     */
    DeliveryOutcome deliver(OutboxEvent event) {
        int claimed;
        try {
            // 原子认领，避免重复消费。
            claimed = outboxRepository.claimPending(event.getId());
        } catch (RuntimeException ex) {
            log.warn("OutboxDispatcher claimPending failed for {}: {}", event.getId(), ex.getMessage());
            return DeliveryOutcome.FAILED;
        }
        if (claimed == 0) {
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
            // 幂等落库：INSERT IGNORE 使主键冲突返回 false（重复消费），仍视为成功投递。
            notificationRepository.insert(notification);
            outboxRepository.markSent(event.getId());
            return DeliveryOutcome.SENT;
        } catch (RuntimeException | java.io.IOException ex) {
            int attempt = event.getAttemptCount() + 1;
            boolean dead = attempt >= maxAttempts;
            Instant nextAttemptAt = dead ? null : Instant.now().plus(backoff);
            try {
                outboxRepository.markFailed(event.getId(), attempt, nextAttemptAt, dead);
            } catch (RuntimeException markEx) {
                // markFailed 自身失败（DB 不可用）：事件停在 PROCESSING，由下一轮 reclaim 回收重投。
                log.error("OutboxDispatcher markFailed failed for {} (will be reclaimed next cycle): {}",
                        event.getId(), markEx.getMessage());
            }
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
