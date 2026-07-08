package com.petspark.ai.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * AI 对话持久层。使用 JdbcTemplate 直写表，与 RBAC/catalog 等模块一致。
 *
 * <p>消息正文以密文（{@link AiMessageCrypto#encrypt}）落 ai_message.content_ciphertext；
 * 读取由服务层解密后回填视图。调用记录 ai_call_record 不存任何提示词或回复内容，
 * 仅存 SHA-256 哈希用于审计关联。
 *
 * <p>软删除：会话与消息都用 deleted_at 软删；调用记录永久保留（无内容，无隐私风险）。
 */
@Repository
public class AiChatRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 同意记录行。withdrawnAt 为空表示当前生效。 */
    public record AiConsentRow(
            String id,
            String userId,
            String policyVersion,
            String scopes,
            Instant grantedAt,
            Instant withdrawnAt) {}

    /** 会话行。deletedAt 为空表示未删除。 */
    public record AiConvRow(
            String id,
            String userId,
            String scene,
            String petId,
            String title,
            String status,
            Instant expiresAt,
            Instant deletedAt) {}

    /** 消息行。contentCiphertext 为密文，由服务层解密。 */
    public record AiMessageRow(
            String id,
            String conversationId,
            String role,
            String contentCiphertext,
            String safetyLabel,
            int tokenCount,
            Instant createdAt) {}

    /** 调用记录行。inputHash 为 SHA-256，不含明文。 */
    public record AiCallRecordRow(
            String id,
            String requestId,
            String userId,
            String scene,
            String provider,
            String model,
            String inputHash,
            String outcome,
            String errorCode,
            int promptTokens,
            int completionTokens,
            int latencyMs) {}

    public Optional<AiConsentRow> findActiveConsent(String userId) {
        return jdbcTemplate.query("""
                SELECT id, user_id, policy_version, scopes, granted_at, withdrawn_at
                FROM ai_consent
                WHERE user_id = ? AND withdrawn_at IS NULL
                ORDER BY granted_at DESC
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(new AiConsentRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("policy_version"),
                rs.getString("scopes"),
                tsToInstant(rs.getTimestamp("granted_at")),
                tsToInstant(rs.getTimestamp("withdrawn_at")))) : Optional.empty(), userId);
    }

    public void insertConsent(AiConsentRow row) {
        jdbcTemplate.update("""
                INSERT INTO ai_consent (id, user_id, policy_version, scopes, granted_at)
                VALUES (?, ?, ?, ?, ?)
                """, row.id(), row.userId(), row.policyVersion(), row.scopes(),
                Timestamp.from(row.grantedAt()));
    }

    public int withdrawConsent(String consentId) {
        return jdbcTemplate.update("""
                UPDATE ai_consent
                SET withdrawn_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND withdrawn_at IS NULL
                """, consentId);
    }

    public int withdrawAllActiveConsents(String userId) {
        return jdbcTemplate.update("""
                UPDATE ai_consent
                SET withdrawn_at = CURRENT_TIMESTAMP(3)
                WHERE user_id = ? AND withdrawn_at IS NULL
                """, userId);
    }

    public String insertConversation(String userId, String scene, String petId, String title, Instant expiresAt) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_conversation (id, user_id, scene, pet_id, title, status, expires_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, id, userId, scene, petId, title, expiresAt == null ? null : Timestamp.from(expiresAt));
        return id;
    }

    public Optional<AiConvRow> findConversation(String id) {
        return jdbcTemplate.query("""
                SELECT id, user_id, scene, pet_id, title, status, expires_at, deleted_at
                FROM ai_conversation
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new AiConvRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("scene"),
                rs.getString("pet_id"),
                rs.getString("title"),
                rs.getString("status"),
                tsToInstant(rs.getTimestamp("expires_at")),
                tsToInstant(rs.getTimestamp("deleted_at")))) : Optional.empty(), id);
    }

    public int softDeleteConversation(String id) {
        return jdbcTemplate.update("""
                UPDATE ai_conversation
                SET deleted_at = CURRENT_TIMESTAMP(3), status = 'DELETED'
                WHERE id = ? AND deleted_at IS NULL
                """, id);
    }

    public int softDeleteMessages(String conversationId) {
        return jdbcTemplate.update("""
                UPDATE ai_message
                SET deleted_at = CURRENT_TIMESTAMP(3)
                WHERE conversation_id = ? AND deleted_at IS NULL
                """, conversationId);
    }

    public void insertMessage(String id, String conversationId, String role, String ciphertext,
            String safetyLabel, int tokenCount) {
        jdbcTemplate.update("""
                INSERT INTO ai_message (id, conversation_id, role, content_ciphertext, safety_label, token_count)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, conversationId, role, ciphertext, safetyLabel, tokenCount);
    }

    /**
     * 返回最近 limit 条消息（按时间倒序取，再反转成升序），仅未软删。
     * 服务层负责解密 contentCiphertext。
     */
    public List<AiMessageRow> findRecentMessages(String conversationId, int limit) {
        List<AiMessageRow> rows = jdbcTemplate.query("""
                SELECT id, conversation_id, role, content_ciphertext, safety_label, token_count, created_at
                FROM ai_message
                WHERE conversation_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new AiMessageRow(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("role"),
                rs.getString("content_ciphertext"),
                rs.getString("safety_label"),
                rs.getInt("token_count"),
                tsToInstant(rs.getTimestamp("created_at"))), conversationId, limit);
        Collections.reverse(rows);
        return rows;
    }

    public void insertCallRecord(AiCallRecordRow row) {
        jdbcTemplate.update("""
                INSERT INTO ai_call_record
                    (id, request_id, user_id, scene, provider, model, input_hash,
                     outcome, error_code, prompt_tokens, completion_tokens, latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.id(), row.requestId(), row.userId(), row.scene(), row.provider(),
                row.model(), row.inputHash(), row.outcome(), row.errorCode(),
                row.promptTokens(), row.completionTokens(), row.latencyMs());
    }

    /** 查询某用户最近 N 条调用记录，便于测试与审计观察。 */
    public List<AiCallRecordRow> findRecentCallRecords(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, request_id, user_id, scene, provider, model, input_hash,
                       outcome, error_code, prompt_tokens, completion_tokens, latency_ms, created_at
                FROM ai_call_record
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new AiCallRecordRow(
                rs.getString("id"),
                rs.getString("request_id"),
                rs.getString("user_id"),
                rs.getString("scene"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("input_hash"),
                rs.getString("outcome"),
                rs.getString("error_code"),
                rs.getInt("prompt_tokens"),
                rs.getInt("completion_tokens"),
                rs.getInt("latency_ms")), userId, limit);
    }

    /** 查询某会话所有未删消息（按时间升序），便于服务层返回历史。 */
    public List<AiMessageRow> findMessagesForConversation(String conversationId) {
        return new ArrayList<>(jdbcTemplate.query("""
                SELECT id, conversation_id, role, content_ciphertext, safety_label, token_count, created_at
                FROM ai_message
                WHERE conversation_id = ? AND deleted_at IS NULL
                ORDER BY created_at ASC
                """, (rs, rowNum) -> new AiMessageRow(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("role"),
                rs.getString("content_ciphertext"),
                rs.getString("safety_label"),
                rs.getInt("token_count"),
                tsToInstant(rs.getTimestamp("created_at"))), conversationId));
    }

    private static Instant tsToInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
