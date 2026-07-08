package com.petspark.audit;

import com.petspark.common.web.RequestIdContext;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link AuditService} 默认实现：同步写 {@code audit_log} 表。
 *
 * <p>审计写入失败不阻断业务主流程——捕获异常并记录错误日志（含 requestId 便于追溯），
 * 与“审计可用性优先”的运维取舍一致（NFR-OBS-002）。当且仅当审计与业务数据需要强一致
 * 时，调用方应将 {@code record} 放在同一事务内，使业务回滚连带着放弃审计；
 * 默认实现不强制事务边界，由调用方决定。
 *
 * <p>{@code requestId} 自动从 {@link RequestIdContext} 取，缺失则留空——审计日志不
 * 强制 requestId 存在（系统操作可能无 HTTP 请求上下文）。
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogMapper mapper;

    public AuditServiceImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(AuditContext context) {
        AuditLog entry = new AuditLog(
                UUID.randomUUID().toString(),
                RequestIdContext.current(),
                context.getActorId(),
                context.getActorRole(),
                context.getModule(),
                context.getAction(),
                context.getObjectType(),
                context.getObjectId(),
                context.getResult() != null ? context.getResult() : AuditLog.RESULT_SUCCESS,
                context.getReasonCode(),
                context.getIpHash(),
                Instant.now());
        try {
            mapper.insert(entry);
        } catch (RuntimeException e) {
            // 审计可用性优先：不阻断主流程，记录错误日志供事后追溯。
            log.error("Failed to persist audit log [requestId={}, module={}, action={}]: {}",
                    entry.getRequestId(), entry.getModule(), entry.getAction(), e.getMessage(), e);
        }
    }

    @Override
    public void recordSuccess(AuditContext context) {
        record(context.withResult(AuditLog.RESULT_SUCCESS));
    }

    @Override
    public void recordFailure(AuditContext context, String reasonCode) {
        record(context.withResult(AuditLog.RESULT_FAILURE).withReasonCode(reasonCode));
    }
}
