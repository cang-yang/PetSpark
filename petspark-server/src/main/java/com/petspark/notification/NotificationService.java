package com.petspark.notification;

import com.petspark.common.event.OutboxEvent;
import com.petspark.common.event.OutboxService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知发送应用服务。业务模块在事务内调用 {@link #send} 追加一条 outbox 事件，
 * 与业务数据原子提交：业务回滚则事件丢弃（不投递），业务提交则事件可见（投递）。
 *
 * <p>本服务不在事务内直接写 notification 表——通知落库是异步副作用，由
 * {@link OutboxDispatcher} 在业务事务之外完成，从而满足验收：
 * 业务成功不因通知发送（落库）失败回滚。
 *
 * <p>{@link OutboxService#append} 在 DEBUG 日志校验事务存在；此处 {@code @Transactional}
 * 保证调用方即使未显式开事务，事件也与业务写一致提交。
 *
 * <p>返回值是 outbox 事件的真实主键 id（由 append 内部生成），它同时是将要落库的
 * notification.id——以此实现按事件 id 幂等投递。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String EVENT_TYPE = "notification.send";
    static final String AGGREGATE_TYPE = "notification";

    private final OutboxService outboxService;

    public NotificationService(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * 在当前事务内追加一条通知事件，返回事件 id（= 将来 notification.id，幂等键）。
     * 调用方应已开启业务事务，使事件与业务写原子提交。
     */
    @Transactional
    public String send(NotificationPayload payload) {
        // 用 recipientId 作为 aggregate_id 便于按接收者关联事件；真实主键 id 由 append 生成。
        OutboxEvent event = outboxService.append(
                EVENT_TYPE, AGGREGATE_TYPE, payload.recipientId(), payload);
        log.debug("Appended notification event {} for recipient {} type {}",
                event.getId(), payload.recipientId(), payload.type());
        return event.getId();
    }

    /**
     * 便捷重载：直接给字段，组装 payload 后追加事件。
     */
    @Transactional
    public String send(String recipientId, String type, String title, String content,
                       String businessType, String businessId) {
        return send(new NotificationPayload(recipientId, type, title, content, businessType, businessId));
    }

    /**
     * 返回当前时间戳，供 dispatcher 落库 created_at 使用。包级可见便于测试桩。
     */
    Instant now() {
        return Instant.now();
    }
}
