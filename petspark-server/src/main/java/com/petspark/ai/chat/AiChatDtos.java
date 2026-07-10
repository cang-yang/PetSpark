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

    // ---- PR-AI-03 真实候选智能推荐（API-AI-007）----

    /** POST /ai/recommend 请求。candidateType 指明推荐对象类型。 */
    public record AiRecommendRequest(
            @NotBlank @Size(max = 32) String species,
            @jakarta.validation.constraints.Min(0) int age,
            @NotBlank @Size(max = 4000) String preference,
            @NotBlank @Pattern(regexp = "PET|GOODS|SERVICE") String candidateType,
            String petId) {}

    /** 推荐结果单项：id + 对象类型 + 模型/规则给出的推荐理由。 */
    public record AiRecommendItemView(
            String id,
            String type,
            String reason) {}

    /** POST /ai/recommend 响应。items 已通过服务端再校验，boundaryNotice 为边界提示。 */
    public record AiRecommendReplyView(
            String requestId,
            java.util.List<AiRecommendItemView> items,
            AiUsageView usage,
            String boundaryNotice) {}
}
