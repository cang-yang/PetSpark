package com.petspark.ai.chat;

import com.petspark.ai.chat.AiChatDtos.AiChatReplyView;
import com.petspark.ai.chat.AiChatDtos.AiConsentRequest;
import com.petspark.ai.chat.AiChatDtos.AiConversationCreateRequest;
import com.petspark.ai.chat.AiChatDtos.AiConversationView;
import com.petspark.ai.chat.AiChatDtos.AiMessageRequest;
import com.petspark.ai.chat.AiChatDtos.AiMessageView;
import com.petspark.ai.chat.AiChatDtos.AiRecommendReplyView;
import com.petspark.ai.chat.AiChatDtos.AiRecommendRequest;
import com.petspark.ai.chat.AiChatDtos.AiStatusView;
import com.petspark.ai.recommend.RecommendationService;
import com.petspark.ai.chat.AiChatDtos.CareQaReplyView;
import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口（API-AI-001~006）。
 *
 * <ul>
 *   <li>API-AI-001 {@code GET /ai/status}：开关/同意状态/降级原因（登录自服务）；</li>
 *   <li>API-AI-002 {@code PUT /ai/consent}：同意 AI 服务协议；</li>
 *   <li>API-AI-002b {@code DELETE /ai/consent}：撤回同意，撤回后阻止新会话；</li>
 *   <li>API-AI-003 {@code POST /ai/conversations}：创建会话（场景+可选 petId+标题）；</li>
 *   <li>API-AI-004 {@code POST /ai/conversations/{id}/messages}：非流式发送消息；</li>
 *   <li>API-AI-004b {@code POST /ai/conversations/{id}/messages:care-qa}：护理问答非流式发送，
 *       返回结构化 {@link CareQaReplyView}（PR-AI-04）；</li>
 *   <li>API-AI-004c {@code GET /ai/care-qa/status}：护理问答场景可用性查询；</li>
 *   <li>API-AI-005 {@code POST /ai/conversations/{id}/messages:stream}：SSE 流式发送；</li>
 *   <li>API-AI-006 {@code DELETE /ai/conversations/{id}}：软删会话与消息；</li>
 *   <li>API-AI-006b {@code GET /ai/conversations/{id}/messages}：列出会话历史消息；</li>
 *   <li>API-AI-007 {@code POST /ai/recommend}：真实候选智能推荐（PET/GOODS/SERVICE）。</li>
 * </ul>
 *
 * <p>所有端点权限=登录（无 @RequirePermission）；资源归属在 {@link AiChatService} 内按
 * userId 校验。健康检查 {@code GET /ai/health} 仍由 {@code AiHealthController} 提供。
 */
@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService service;
    private final RecommendationService recommendationService;

    public AiChatController(AiChatService service, RecommendationService recommendationService) {
        this.service = service;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/status")
    public ApiResponse<AiStatusView> status(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.status(user.getId()));
    }

    @PutMapping("/consent")
    public ApiResponse<AiChatDtos.AiConsentView> grantConsent(
            @Valid @RequestBody AiConsentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.grantConsent(user.getId(), request));
    }

    @DeleteMapping("/consent")
    public ApiResponse<AiChatDtos.AiConsentView> withdrawConsent(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.withdrawConsent(user.getId()));
    }

    @PostMapping("/conversations")
    public ApiResponse<AiConversationView> createConversation(
            @Valid @RequestBody AiConversationCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.createConversation(user.getId(), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AiConversationView>> listConversations(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.listConversations(user.getId()));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<AiChatReplyView> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.send(id, request, user.getId()));
    }

    /**
     * 护理问答非流式发送（PR-AI-04 / API-AI-004b）。
     *
     * <p>返回结构化 {@link CareQaReplyView}：riskLevel/generalAdvice/warningSigns/seekHelp
     * + 固定非诊断声明。会话 scene 必须为 CARE_QA，否则 BUSINESS_RULE_001。
     * 场景开关关闭时 AI_DISABLED_001。
     */
    @PostMapping("/conversations/{id}/messages:care-qa")
    public ApiResponse<CareQaReplyView> sendCareQaMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.sendCareQa(id, request, user.getId()));
    }

    /**
     * 护理问答场景可用性查询（PR-AI-04 / API-AI-004c）。
     *
     * <p>前端进入护理问答页前查询：全局 AI 可用且 care-qa 开关打开时 enabled=true。
     */
    @GetMapping("/care-qa/status")
    public ApiResponse<Map<String, Object>> careQaStatus(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(Map.of(
                "enabled", service.isCareQaAvailable(),
                "scene", "CARE_QA"));
    }

    @PostMapping(value = "/conversations/{id}/messages:stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.stream(id, request, user.getId());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiMessageView>> listMessages(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.listMessages(id, user.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteConversation(id, user.getId());
        return ApiResponse.ok();
    }

    /**
     * API-AI-007：真实候选智能推荐。
     *
     * <p>请求需登录 + 有效同意。服务端检索真实可见候选，喂给模型排序+给出理由，
     * 再对模型输出做服务端再校验（NFR-AI-001：100% 展示项来自请求时仍有效的真实候选）。
     * AI 未启用或模型失败时走确定性规则兜底排序。
     */
    @PostMapping("/recommend")
    public ApiResponse<AiRecommendReplyView> recommend(
            @Valid @RequestBody AiRecommendRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(recommendationService.recommend(user.getId(), request));
    }
}
