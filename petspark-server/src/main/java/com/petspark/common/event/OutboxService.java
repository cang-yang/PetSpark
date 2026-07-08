package com.petspark.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbox 写入服务。业务应用服务在事务内调用 {@link #append} 追加事件，
 * 与该事务的业务写操作原子提交/回滚。
 *
 * <p>与业务数据“同事务”的保证依赖调用方已开启事务（通常在应用服务方法上
 * 标注 {@code @Transactional}）。本服务在 DEBUG 日志中校验当前是否处于事务，
 * 非事务调用会记录告警——避免静默漏发事件。
 *
 * <p>载荷序列化失败是编程错误（领域对象不可序列化），抛
 * {@link IllegalStateException} 使当前事务回滚，绝不吞掉。
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加一条事件。
     *
     * @param eventType     事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param payload       事件载荷，将被 JSON 序列化存入 outbox
     * @return 已持久化的事件（含生成的 id 与时间戳）
     */
    public OutboxEvent append(String eventType, String aggregateType, String aggregateId, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // 不抛异常以免阻断主流程，但记录告警便于排查漏发事件。
            OutboxServiceLogger.warn("OutboxService.append called without active transaction; "
                    + "event " + eventType + " may not share business commit.");
        }
        String json = serialize(payload);
        Instant now = Instant.now();
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID().toString(),
                eventType,
                aggregateType,
                aggregateId,
                json,
                OutboxEvent.Status.PENDING,
                0,
                null,
                now,
                null);
        repository.save(event);
        return event;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + e.getLocation(), e);
        }
    }

    /** 间接依赖，避免 OutboxService 静态持有具体日志器导致测试困难——保留扩展点。 */
    static final class OutboxServiceLogger {
        static void warn(String msg) {
            // 使用 SLF4J 日志通过外层日志门面；此处保持解耦，由实际日志器桥接。
            org.slf4j.LoggerFactory.getLogger(OutboxService.class).warn(msg);
        }
    }
}
