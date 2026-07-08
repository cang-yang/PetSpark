package com.petspark.notification;

import com.petspark.common.event.OutboxEvent;
import com.petspark.common.event.OutboxRepository;
import org.springframework.stereotype.Service;

/**
 * Outbox 可观测查询服务：汇总 {@code outbox_event} 各状态计数，供运营/运维端点暴露
 * （NFR-OBS-001「积压和失败可观测」）。
 *
 * <p>对四个状态各执行一次 {@link OutboxRepository#countByStatus}。计数查询若失败，
 * 让异常向上传播由 {@code GlobalExceptionHandler} 转 500——观测端点的信号必须真实，
 * 返回伪造的全 0 会让运营误判「outbox 已清空」，掩盖 DB 抖动。
 * （区别于 {@code OutboxDispatcher.dispatchOnce} 必须吞异常：调度器抛出会按
 * {@code scheduleWithFixedDelay} 契约静默杀死后续所有轮询；本服务是请求级，
 * 单次 500 不影响其他请求。）
 */
@Service
public class OutboxStatusService {

    private final OutboxRepository outboxRepository;

    public OutboxStatusService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * 汇总当前 outbox 各状态计数。任一状态查询失败则整体抛出（→ 500），
     * 运营结合日志判断「真的空」还是「查询失败」。
     */
    public OutboxStatusView snapshot() {
        return new OutboxStatusView(
                outboxRepository.countByStatus(OutboxEvent.Status.PENDING),
                outboxRepository.countByStatus(OutboxEvent.Status.PROCESSING),
                outboxRepository.countByStatus(OutboxEvent.Status.SENT),
                outboxRepository.countByStatus(OutboxEvent.Status.DEAD));
    }
}
