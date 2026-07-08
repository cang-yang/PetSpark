package com.petspark.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * AI 对话相关 DTO 集合。统一放在 chat 包，避免散落多个文件。
 *
 * <p>视图对象（{@code AiStatusView}/{@code AiConsentView}/{@code AiConversationView}/
 * {@code AiMessageView}/{@code AiChatReplyView}/{@code AiUsageView}）为对外响应；
 * 请求对象（{@code AiConsentRequest}/{@code AiConversationCreateRequest}/
 * {@code AiMessageRequest}）携带 Bean Validation 注解，由 Controller {@code @Valid} 触发。
 */
public final class AiChatDtos {

    private AiChatDtos() {}

    /** GET /ai/status 响应：开关 + 同意状态 + 降级原因。 */
    public record AiStatusView(
            boolean enabled,
            String scene,
            boolean consentGranted,
            String consentPolicyVersion,
            String degradationReason) {}

    /** PUT /ai/consent 请求。 */
    public record AiConsentRequest(
            @NotBlank @Size(max = 32) String policyVersion,
            @NotBlank @Size(max = 255) String scopes) {}

    /** 同意记录视图。{@code active} = withdrawnAt == null。 */
    public record AiConsentView(
            String id,
            String policyVersion,
            String scopes,
            Instant grantedAt,
            Instant withdrawnAt,
            boolean active) {}

    /** POST /ai/conversations 请求。 */
    public record AiConversationCreateRequest(
            @NotBlank @Pattern(regexp = "PET_CHAT|CARE_QA|RECOMMENDATION") String scene,
            String petId,
            @Size(max = 120) String title) {}

    /** 会话视图。 */
    public record AiConversationView(
            String id,
            String scene,
            String petId,
            String title,
            String status,
            Instant createdAt,
            Instant expiresAt) {}

    /** 消息视图，content 已解密。 */
    public record AiMessageView(
            String id,
            String role,
            String content,
            String safetyLabel,
            Instant createdAt) {}

    /** 发送消息请求（非流式与流式共用）。 */
    public record AiMessageRequest(
            @NotBlank @Size(max = 4000) String message) {}

    /** 非流式回复视图。 */
    public record AiChatReplyView(
            String requestId,
            String content,
            String boundaryNotice,
            AiUsageView usage) {}

    /** Token 用量视图。 */
    public record AiUsageView(
            int promptTokens,
            int completionTokens,
            int totalTokens) {}
}
