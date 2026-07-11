package com.petspark.ai.chat;

import com.petspark.ai.chat.AiChatDtos.AiChatReplyView;
import com.petspark.ai.chat.AiChatDtos.AiConsentRequest;
import com.petspark.ai.chat.AiChatDtos.AiConsentView;
import com.petspark.ai.chat.AiChatDtos.AiConversationCreateRequest;
import com.petspark.ai.chat.AiChatDtos.AiConversationView;
import com.petspark.ai.chat.AiChatDtos.AiMessageRequest;
import com.petspark.ai.chat.AiChatDtos.AiStatusView;
import com.petspark.ai.chat.AiChatDtos.AiUsageView;
import com.petspark.ai.chat.AiChatDtos.CareQaReplyView;
import com.petspark.ai.chat.AiChatRepository.AiCallRecordRow;
import com.petspark.ai.chat.AiChatRepository.AiConsentRow;
import com.petspark.ai.chat.AiChatRepository.AiConvRow;
import com.petspark.ai.chat.AiChatRepository.AiMessageRow;
import com.petspark.ai.chat.CareQaOutputPolicy.CareQaReplyPayload;
import com.petspark.ai.infrastructure.AiChatGateway;
import com.petspark.ai.infrastructure.AiChatRequest;
import com.petspark.ai.infrastructure.AiChatResult;
import com.petspark.ai.infrastructure.AiMessage;
import com.petspark.ai.infrastructure.AiUsage;
import com.petspark.ai.infrastructure.SparkAiProperties;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话应用服务。覆盖同意、会话、消息、调用记录、限流与降级。
 *
 * <p>关键安全约束：
 * <ul>
 *   <li>所有写操作要求登录 + 有效同意；</li>
 *   <li>用户输入先脱敏再送模型；注入直接拒答，不调用网关；</li>
 *   <li>消息正文 AES-GCM 加密落库；调用记录仅存 SHA-256 输入哈希；</li>
 *   <li>宠物归属：会话绑定 pet 时校验 owner_user_id 或 public_status=PUBLISHED；</li>
 *   <li>场景开关：status 反映 enabled；createConversation/send 要求 enabled；</li>
 *   <li>限流：每用户每分钟 10 次（内存滑动窗口，单实例够用）；</li>
 *   <li>降级：网关异常映射为 AI_PROVIDER_*；模型空输出映射为 AI_OUTPUT_001。</li>
 * </ul>
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final String SCENE_PET_CHAT = "PET_CHAT";
    private static final String SCENE_CARE_QA = "CARE_QA";
    private static final String PROVIDER = "spark";
    private static final int CONVERSATION_TTL_DAYS = 30;
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final double TEMPERATURE = 0.4;
    private static final long STREAM_TIMEOUT_MS = 35_000L;

    private final AiChatGateway gateway;
    private final AiChatRepository repository;
    private final AiMessageCrypto crypto;
    private final AiSafetyPolicy safetyPolicy;
    private final AiPromptBuilder promptBuilder;
    private final SparkAiProperties properties;
    private final CareQaProperties careQaProperties;
    private final CareQaOutputPolicy careQaOutputPolicy;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiAuditWriter auditWriter;
    private final Clock clock;
    private final int rateLimitPerMinute;

    // 单实例内存限流：userId -> 调用时间戳列表。够训练项目使用；分布式场景需换 Redis。
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> rateLimitBuckets = new ConcurrentHashMap<>();

    public AiChatService(
            AiChatGateway gateway,
            AiChatRepository repository,
            AiMessageCrypto crypto,
            AiSafetyPolicy safetyPolicy,
            AiPromptBuilder promptBuilder,
            SparkAiProperties properties,
            CareQaProperties careQaProperties,
            CareQaOutputPolicy careQaOutputPolicy,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            AiAuditWriter auditWriter,
            Clock clock,
            @Value("${petspark.ai.rate-limit-per-minute:10}") int rateLimitPerMinute) {
        this.gateway = gateway;
        this.repository = repository;
        this.crypto = crypto;
        this.safetyPolicy = safetyPolicy;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.careQaProperties = careQaProperties;
        this.careQaOutputPolicy = careQaOutputPolicy;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.rateLimitPerMinute = rateLimitPerMinute <= 0 ? 10 : rateLimitPerMinute;
    }

    public AiStatusView status(String userId) {
        boolean enabled = properties.isAvailable();
        boolean consentGranted = repository.findActiveConsent(userId).isPresent();
        String policyVersion = repository.findActiveConsent(userId)
                .map(AiConsentRow::policyVersion).orElse(null);
        String reason = enabled ? "" : properties.unavailableReason();
        // status 的 scene 字段保持 PET_CHAT，向后兼容已有前端；护理问答的可用性通过
        // 独立端点或扩展字段查询，这里不改 PET_CHAT 语义以避免破坏既有契约。
        return new AiStatusView(enabled, SCENE_PET_CHAT, consentGranted, policyVersion, reason);
    }

    /**
     * 护理问答场景可用性（PR-AI-04）。全局 AI 可用 且 护理问答开关打开 时才可用。
     */
    public boolean isCareQaAvailable() {
        return properties.isAvailable() && careQaProperties.isEnabled();
    }

    @Transactional
    public AiConsentView grantConsent(String userId, AiConsentRequest req) {
        // 同意是幂等自服务：先撤回所有现有同意，再写入新一条；保证同时只有一条生效。
        repository.withdrawAllActiveConsents(userId);
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        AiConsentRow row = new AiConsentRow(id, userId, req.policyVersion(), req.scopes(), now, null);
        repository.insertConsent(row);
        return new AiConsentView(id, req.policyVersion(), req.scopes(), now, null, true);
    }

    @Transactional
    public AiConsentView withdrawConsent(String userId) {
        // 幂等撤回：无生效同意时返回 active=false 视图，不报错。
        var active = repository.findActiveConsent(userId);
        if (active.isEmpty()) {
            return new AiConsentView(null, null, null, null, null, false);
        }
        repository.withdrawConsent(active.get().id());
        Instant now = clock.instant();
        return new AiConsentView(active.get().id(), active.get().policyVersion(), active.get().scopes(),
                active.get().grantedAt(), now, false);
    }

    @Transactional
    public AiConversationView createConversation(String userId, AiConversationCreateRequest req) {
        // 同意校验先于降级（启用）校验：未同意时无论 AI 是否启用都应返回 AI_CONSENT_001，
        // 与 AI 设计文档的 "Consent & Ownership Guard" 优先一致，避免降级掩盖未同意语义。
        requireConsent(userId);
        requireEnabledForScene(req.scene());
        String petId = req.petId() == null || req.petId().isBlank() ? null : req.petId().trim();
        if (petId != null) {
            verifyPetAccessible(petId, userId);
        }
        Instant expiresAt = clock.instant().plus(CONVERSATION_TTL_DAYS, ChronoUnit.DAYS);
        String id = repository.insertConversation(userId, req.scene(), petId, req.title(), expiresAt);
        return toConvView(repository.findConversation(id).orElseThrow());
    }

    @Transactional
    public AiChatReplyView send(String conversationId, AiMessageRequest req, String userId) {
        return doChat(conversationId, req, userId);
    }

    /** 返回当前用户最近的未删除会话，供页面刷新或重新登录后恢复历史入口。 */
    @Transactional(readOnly = true)
    public List<AiConversationView> listConversations(String userId) {
        return repository.findActiveConversationsForUser(userId, 50).stream()
                .map(this::toConvView)
                .toList();
    }

    /**
     * 护理问答非流式发送（PR-AI-04）。返回结构化 {@link CareQaReplyView}。
     * 路由：会话 scene 必须为 CARE_QA，否则回退到 {@link #doChat} 的 PET_CHAT 路径。
     */
    @Transactional
    public CareQaReplyView sendCareQa(String conversationId, AiMessageRequest req, String userId) {
        AiConvRow conv = repository.findConversation(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (!conv.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        if (!SCENE_CARE_QA.equals(conv.scene())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "该会话不是护理问答场景");
        }
        return doCareQaChat(conversationId, req, userId, conv);
    }

    /**
     * 流式发送：MVP 实现先用 {@link #doChat} 同步完成，再以 SSE 事件序列输出。
     *
     * <p>真正的分块流式（增量 delta）需要供应商原生支持 stream=true 与异步解析；
     * 当前 Spark 网关按非流式调用一次性返回，前端仍以 SSE 事件拿到 meta/delta/usage/done。
     * 客户端断连时 SseEmitter 的 onCompletion/onTimeout 回调会终止本次发送，
     * 满足"流式取消"语义；后端不再继续处理（已发起的网关调用无法撤回，但不再持久化结果）。
     */
    public SseEmitter stream(String conversationId, AiMessageRequest req, String userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("ai stream emitter error userId={} reason={}", userId, ex.toString()));
        try {
            AiConvRow conv = repository.findConversation(conversationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
            if (SCENE_CARE_QA.equals(conv.scene())) {
                CareQaReplyView reply = doCareQaChat(conversationId, req, userId, conv);
                emit(emitter, "meta", java.util.Map.of(
                        "requestId", reply.requestId(),
                        "model", properties.model(),
                        "scene", SCENE_CARE_QA,
                        "riskLevel", reply.riskLevel()));
                emit(emitter, "delta", java.util.Map.of(
                        "riskLevel", reply.riskLevel(),
                        "generalAdvice", reply.generalAdvice(),
                        "warningSigns", reply.warningSigns(),
                        "seekHelp", reply.seekHelp(),
                        "disclaimer", reply.disclaimer()));
                emit(emitter, "usage", java.util.Map.of(
                        "promptTokens", reply.usage().promptTokens(),
                        "completionTokens", reply.usage().completionTokens(),
                        "totalTokens", reply.usage().totalTokens()));
                emit(emitter, "done", java.util.Map.of());
            } else {
                AiChatReplyView reply = doChat(conversationId, req, userId);
                emit(emitter, "meta", java.util.Map.of(
                        "requestId", reply.requestId(),
                        "model", properties.model(),
                        "scene", conv.scene()));
                emit(emitter, "delta", java.util.Map.of("content", reply.content()));
                emit(emitter, "usage", java.util.Map.of(
                        "promptTokens", reply.usage().promptTokens(),
                        "completionTokens", reply.usage().completionTokens(),
                        "totalTokens", reply.usage().totalTokens()));
                emit(emitter, "done", java.util.Map.of());
            }
            emitter.complete();
        } catch (BusinessException ex) {
            emitError(emitter, ex.errorCode().code(), ex.getMessage());
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("ai stream unexpected error userId={} reason={}", userId, ex.toString());
            emitError(emitter, ErrorCode.INTERNAL_ERROR_001.code(), ErrorCode.INTERNAL_ERROR_001.defaultMessage());
            emitter.complete();
        }
        return emitter;
    }

    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        AiConvRow conv = repository.findConversation(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (!conv.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        repository.softDeleteMessages(conversationId);
        repository.softDeleteConversation(conversationId);
    }

    /** 历史消息列表（已解密）。会话不存在或非本人 → 403/404。 */
    public List<AiChatDtos.AiMessageView> listMessages(String conversationId, String userId) {
        AiConvRow conv = repository.findConversation(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (!conv.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        List<AiMessageRow> rows = repository.findMessagesForConversation(conversationId);
        List<AiChatDtos.AiMessageView> views = new ArrayList<>(rows.size());
        for (AiMessageRow row : rows) {
            String plain = crypto.decrypt(row.contentCiphertext());
            views.add(new AiChatDtos.AiMessageView(row.id(), row.role(), plain, row.safetyLabel(), row.createdAt()));
        }
        return views;
    }

    // ---- 内部：核心对话流程（send 与 stream 共用）----

    private AiChatReplyView doChat(String conversationId, AiMessageRequest req, String userId) {
        AiConvRow conv = repository.findConversation(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (!conv.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        // 场景路由：CARE_QA 走独立分支（jsonOutput + 输出策略 + 护理问答视图）；
        // 其他场景保持 PET_CHAT 原路径，字节不变。
        if (SCENE_CARE_QA.equals(conv.scene())) {
            // doChat 被现有 PET_CHAT 测试与 send() 复用，CARE_QA 走 doCareQaChat 后
            // 把结构化结果包装回 AiChatReplyView（content=JSON 字符串）以兼容既有契约。
            CareQaReplyView careView = doCareQaChat(conversationId, req, userId, conv);
            String jsonContent;
            try {
                jsonContent = objectMapper.writeValueAsString(careView);
            } catch (Exception ex) {
                log.warn("care-qa reply serialization failed requestId={}", careView.requestId(), ex);
                throw new BusinessException(ErrorCode.AI_OUTPUT_001);
            }
            return new AiChatReplyView(careView.requestId(), jsonContent, careView.disclaimer(), careView.usage());
        }
        requireConsent(userId);
        enforceRateLimit(userId);

        String rawInput = req.message() == null ? "" : req.message();
        String sanitized = safetyPolicy.sanitizeUserInput(rawInput);
        String safetyLabel = safetyPolicy.safetyLabelFor(rawInput);

        // 注入检查先于 enabled（Input Policy 在 Scene Orchestrator/网关之前）：
        // 即使 AI 未启用，注入也必须以 AI_SAFETY_001 拒答并留审计痕迹，
        // 而不是被降级的 AI_DISABLED_001 掩盖。失败消息与调用记录走独立事务（REQUIRES_NEW），
        // 确保外层 send() 抛出异常回滚后审计痕迹仍提交。不调用网关。
        if (safetyPolicy.isInjection(rawInput)) {
            String userMsgId = UUID.randomUUID().toString();
            auditWriter.insertFailedUserMessage(userMsgId, conversationId,
                    crypto.encrypt(sanitized), "INJECTION");
            String injectionRequestId = UUID.randomUUID().toString();
            auditWriter.recordCall(injectionRequestId, userId, conv.scene(), PROVIDER, properties.model(),
                    0, 0, "REJECTED", ErrorCode.AI_SAFETY_001.code(), 0, sanitized, null);
            throw new BusinessException(ErrorCode.AI_SAFETY_001);
        }

        // 降级检查：未启用时先持久化用户消息 + 调用记录（独立事务），再抛 AI_DISABLED_001。
        if (!properties.isAvailable()) {
            String disabledRequestId = UUID.randomUUID().toString();
            String userMsgId = UUID.randomUUID().toString();
            auditWriter.insertFailedUserMessage(userMsgId, conversationId,
                    crypto.encrypt(sanitized), safetyLabel);
            auditWriter.recordCall(disabledRequestId, userId, conv.scene(), PROVIDER, properties.model(),
                    0, 0, "FAILURE", ErrorCode.AI_DISABLED_001.code(), 0, sanitized, null);
            throw new BusinessException(ErrorCode.AI_DISABLED_001,
                    properties.unavailableReason().isBlank() ? ErrorCode.AI_DISABLED_001.defaultMessage()
                            : properties.unavailableReason());
        }

        // 上下文：取最近 N 条历史，解密后拼成对话，再附加当前用户消息。
        List<AiMessageRow> history = repository.findRecentMessages(conversationId, promptBuilder.MAX_CONTEXT_MESSAGES);
        List<AiMessage> messages = new ArrayList<>(history.size() + 1);
        for (AiMessageRow row : history) {
            String plain = crypto.decrypt(row.contentCiphertext());
            if (plain == null) {
                continue;
            }
            messages.add(new AiMessage(row.role(), plain));
        }
        messages.add(new AiMessage("user", sanitized));

        String requestId = UUID.randomUUID().toString();
        AiChatRequest request = new AiChatRequest(
                requestId,
                promptBuilder.systemPromptForPetChat(),
                messages,
                MAX_OUTPUT_TOKENS,
                TEMPERATURE,
                false);
        long started = System.currentTimeMillis();
        AiChatResult result;
        try {
            result = gateway.chat(request);
        } catch (BusinessException ex) {
            // 失败也要落调用记录（不含内容，便于审计与限流统计）；用户消息仍持久化。
            // 走独立事务，确保外层回滚后审计仍提交。
            int latency = (int) (System.currentTimeMillis() - started);
            auditWriter.recordCall(requestId, userId, conv.scene(), PROVIDER, properties.model(),
                    0, 0, "FAILURE", ex.errorCode().code(), latency, sanitized, null);
            String userMsgId = UUID.randomUUID().toString();
            auditWriter.insertFailedUserMessage(userMsgId, conversationId,
                    crypto.encrypt(sanitized), safetyLabel);
            throw ex;
        }
        int latency = (int) (System.currentTimeMillis() - started);
        AiUsage usage = result.usage() == null ? AiUsage.empty() : result.usage();

        // 持久化用户与助手消息（用户消息明文加密；助手消息原文加密，边界提示在视图层附加）。
        String userMsgId = UUID.randomUUID().toString();
        repository.insertMessage(userMsgId, conversationId, "user",
                crypto.encrypt(sanitized), safetyLabel, usage.promptTokens());
        String assistantMsgId = UUID.randomUUID().toString();
        repository.insertMessage(assistantMsgId, conversationId, "assistant",
                crypto.encrypt(result.content()), "OK", usage.completionTokens());

        recordCall(requestId, userId, conv.scene(), usage.promptTokens(), usage.completionTokens(),
                "SUCCESS", null, latency, sanitized);

        AiUsageView usageView = new AiUsageView(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
        return new AiChatReplyView(requestId, result.content(), promptBuilder.boundaryNotice(), usageView);
    }

    /**
     * 护理问答对话流程（PR-AI-04 / 06A §6.4）。
     *
     * <p>与 PET_CHAT {@link #doChat} 的差异：
     * <ul>
     *   <li>使用 {@link AiPromptBuilder#systemPromptForCareQa()} 固定系统提示；</li>
     *   <li>{@code jsonOutput=true}，要求模型返回严格 JSON；</li>
     *   <li>输出经 {@link CareQaOutputPolicy#parseAndValidate} 校验：违规或高风险输入 →
     *       固定 URGENT 兜底，不回显模型原文；</li>
     *   <li>视图为 {@link CareQaReplyView}，前端按 riskLevel 渲染；固定非诊断声明
     *       由服务层附加，模型无法抑制；</li>
     *   <li>场景开关：调用方已通过 {@link #requireEnabledForScene} 或
     *       {@link #isCareQaAvailable} 校验，这里再做一次防御性检查。</li>
     * </ul>
     *
     * <p>同意与限流复用 PET_CHAT 的 {@link #requireConsent} / {@link #enforceRateLimit}，
     * 保持用户级治理一致。注入检查与降级失败记录路径与 PET_CHAT 相同（审计独立事务）。
     */
    private CareQaReplyView doCareQaChat(String conversationId, AiMessageRequest req, String userId, AiConvRow conv) {
        requireConsent(userId);
        enforceRateLimit(userId);

        // 防御性场景开关：即使通过路由进入，开关关闭也立即降级。
        if (!isCareQaAvailable()) {
            throw new BusinessException(ErrorCode.AI_DISABLED_001, "护理问答场景暂未开放");
        }

        String rawInput = req.message() == null ? "" : req.message();
        String sanitized = safetyPolicy.sanitizeUserInput(rawInput);
        String safetyLabel = safetyPolicy.safetyLabelFor(rawInput);

        if (safetyPolicy.isInjection(rawInput)) {
            String userMsgId = UUID.randomUUID().toString();
            auditWriter.insertFailedUserMessage(userMsgId, conversationId,
                    crypto.encrypt(sanitized), "INJECTION");
            String injectionRequestId = UUID.randomUUID().toString();
            auditWriter.recordCall(injectionRequestId, userId, conv.scene(), PROVIDER, properties.model(),
                    0, 0, "REJECTED", ErrorCode.AI_SAFETY_001.code(), 0, sanitized, null);
            throw new BusinessException(ErrorCode.AI_SAFETY_001);
        }

        boolean highRiskInput = careQaOutputPolicy.isHighRiskInput(rawInput);

        // 上下文：护理问答同样取最近 N 条历史拼对话。
        List<AiMessageRow> history = repository.findRecentMessages(conversationId, promptBuilder.MAX_CONTEXT_MESSAGES);
        List<AiMessage> messages = new ArrayList<>(history.size() + 1);
        for (AiMessageRow row : history) {
            String plain = crypto.decrypt(row.contentCiphertext());
            if (plain == null) {
                continue;
            }
            messages.add(new AiMessage(row.role(), plain));
        }
        messages.add(new AiMessage("user", sanitized));

        String requestId = UUID.randomUUID().toString();
        AiChatRequest request = new AiChatRequest(
                requestId,
                promptBuilder.systemPromptForCareQa(),
                messages,
                MAX_OUTPUT_TOKENS,
                TEMPERATURE,
                true);
        long started = System.currentTimeMillis();
        AiChatResult result;
        try {
            result = gateway.chat(request);
        } catch (BusinessException ex) {
            int latency = (int) (System.currentTimeMillis() - started);
            auditWriter.recordCall(requestId, userId, conv.scene(), PROVIDER, properties.model(),
                    0, 0, "FAILURE", ex.errorCode().code(), latency, sanitized, null);
            String userMsgId = UUID.randomUUID().toString();
            auditWriter.insertFailedUserMessage(userMsgId, conversationId,
                    crypto.encrypt(sanitized), safetyLabel);
            throw ex;
        }
        int latency = (int) (System.currentTimeMillis() - started);
        AiUsage usage = result.usage() == null ? AiUsage.empty() : result.usage();

        // 输出策略：解析 + 校验 + 违规降级。高风险输入强制 URGENT。
        CareQaReplyPayload payload = careQaOutputPolicy.parseAndValidate(result.content(), highRiskInput);

        // 持久化用户与助手消息。助手消息存 payload 的 JSON（便于历史回放结构化结果）。
        String userMsgId = UUID.randomUUID().toString();
        repository.insertMessage(userMsgId, conversationId, "user",
                crypto.encrypt(sanitized), safetyLabel, usage.promptTokens());
        String assistantMsgId = UUID.randomUUID().toString();
        String assistantPersist;
        try {
            assistantPersist = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("care-qa payload serialization failed requestId={} reason={}", requestId, ex.toString());
            assistantPersist = "";
        }
        repository.insertMessage(assistantMsgId, conversationId, "assistant",
                crypto.encrypt(assistantPersist), "OK", usage.completionTokens());

        recordCall(requestId, userId, conv.scene(), usage.promptTokens(), usage.completionTokens(),
                "SUCCESS", null, latency, sanitized);

        AiUsageView usageView = new AiUsageView(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
        return new CareQaReplyView(requestId, payload.riskLevel(), payload.generalAdvice(),
                payload.warningSigns(), payload.seekHelp(), promptBuilder.careQaDisclaimer(), usageView);
    }

    private void recordCall(String requestId, String userId, String scene, int promptTokens, int completionTokens,
            String outcome, String errorCode, int latencyMs, String sanitizedInput) {
        try {
            AiCallRecordRow row = new AiCallRecordRow(
                    UUID.randomUUID().toString(),
                    requestId,
                    userId,
                    scene,
                    PROVIDER,
                    properties.model(),
                    sha256Hex(sanitizedInput),
                    outcome,
                    errorCode,
                    promptTokens,
                    completionTokens,
                    latencyMs);
            repository.insertCallRecord(row);
        } catch (RuntimeException ex) {
            // 审计/统计可用性优先：记录失败不阻断主流程。
            log.warn("ai call record insert failed requestId={} reason={}", requestId, ex.toString());
        }
    }

    private void requireEnabled() {
        if (!properties.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_DISABLED_001,
                    properties.unavailableReason().isBlank() ? ErrorCode.AI_DISABLED_001.defaultMessage()
                            : properties.unavailableReason());
        }
    }

    /**
     * 按场景校验启用状态（PR-AI-04）。
     *
     * <p>PET_CHAT 等既有场景仅校验全局 AI 可用性（保持原有行为不变）；
     * CARE_QA 在此基础上额外要求护理问答独立开关 {@code petspark.ai.care-qa.enabled=true}，
     * 默认关闭，上线门禁通过前任何 CARE_QA 会话创建/发送都返回 AI_DISABLED_001。
     */
    private void requireEnabledForScene(String scene) {
        if (SCENE_CARE_QA.equals(scene)) {
            if (!properties.isAvailable() || !careQaProperties.isEnabled()) {
                throw new BusinessException(ErrorCode.AI_DISABLED_001,
                        careQaProperties.isEnabled()
                                ? (properties.unavailableReason().isBlank()
                                        ? ErrorCode.AI_DISABLED_001.defaultMessage()
                                        : properties.unavailableReason())
                                : "护理问答场景暂未开放");
            }
            return;
        }
        if (!properties.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_DISABLED_001,
                    properties.unavailableReason().isBlank() ? ErrorCode.AI_DISABLED_001.defaultMessage()
                            : properties.unavailableReason());
        }
    }

    private void requireConsent(String userId) {
        if (repository.findActiveConsent(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CONSENT_001);
        }
    }

    private void enforceRateLimit(String userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(1, ChronoUnit.MINUTES);
        CopyOnWriteArrayList<Instant> bucket = rateLimitBuckets.computeIfAbsent(userId,
                k -> new CopyOnWriteArrayList<>());
        // 滑动窗口：丢弃超过 1 分钟的旧记录，再判断是否超限。
        bucket.removeIf(ts -> ts.isBefore(cutoff));
        if (bucket.size() >= rateLimitPerMinute) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_001);
        }
        bucket.add(now);
    }

    private void verifyPetAccessible(String petId, String userId) {
        jdbcTemplate.query("""
                SELECT owner_user_id, public_status
                FROM pet
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> {
            if (!rs.next()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
            }
            String ownerId = rs.getString("owner_user_id");
            String publicStatus = rs.getString("public_status");
            if (!userId.equals(ownerId) && !"PUBLISHED".equals(publicStatus)) {
                throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
            }
        }, petId);
    }

    private AiConversationView toConvView(AiConvRow row) {
        return new AiConversationView(row.id(), row.scene(), row.petId(), row.title(),
                row.status(), row.createdAt(), row.expiresAt());
    }

    private void emit(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception ex) {
            log.debug("ai sse emit failed event={} reason={}", eventName, ex.toString());
        }
    }

    private void emitError(SseEmitter emitter, String code, String message) {
        emit(emitter, "error", java.util.Map.of("code", code, "message", message));
    }

    private static String sha256Hex(String input) {
        if (input == null) {
            input = "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
