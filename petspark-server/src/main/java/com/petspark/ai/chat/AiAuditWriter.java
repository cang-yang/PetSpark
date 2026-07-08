package com.petspark.ai.chat;

import com.petspark.ai.chat.AiChatRepository.AiCallRecordRow;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 调用记录与失败消息的独立事务写手。沿用 {@code RefreshTokenFamilyRevoker} 的
 * REQUIRES_NEW 模式：当外层 {@link AiChatService#send} 在 {@code @Transactional} 中
 * 因注入/降级/网关失败而抛出 {@link com.petspark.common.error.BusinessException} 时，
 * 外层事务会整体回滚——但审计与失败用户消息仍需独立提交，便于统计、限流与可追溯。
 *
 * <p>必须放在独立的 Spring bean，REQUIRES_NEW 才能绕过外层事务代理（同类内部调用
 * 不走代理，REQUIRES_NEW 不生效）。
 */
@Service
public class AiAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AiAuditWriter.class);

    private final AiChatRepository repository;

    public AiAuditWriter(AiChatRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCall(String requestId, String userId, String scene, String provider, String model,
            int promptTokens, int completionTokens, String outcome, String errorCode, int latencyMs,
            String sanitizedInput, java.security.MessageDigest digest) {
        AiCallRecordRow row = new AiCallRecordRow(
                UUID.randomUUID().toString(),
                requestId,
                userId,
                scene,
                provider,
                model,
                sha256Hex(digest, sanitizedInput),
                outcome,
                errorCode,
                promptTokens,
                completionTokens,
                latencyMs);
        repository.insertCallRecord(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertFailedUserMessage(String id, String conversationId, String ciphertext,
            String safetyLabel) {
        repository.insertMessage(id, conversationId, "user", ciphertext, safetyLabel, 0);
    }

    private static String sha256Hex(java.security.MessageDigest digest, String input) {
        String safe = input == null ? "" : input;
        byte[] bytes;
        try {
            bytes = (digest == null ? java.security.MessageDigest.getInstance("SHA-256") : digest)
                    .digest(safe.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
