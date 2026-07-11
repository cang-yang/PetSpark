package com.petspark.ai.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.ai.chat.AiAuditWriter;
import com.petspark.ai.chat.AiChatDtos.AiRecommendItemView;
import com.petspark.ai.chat.AiChatDtos.AiRecommendReplyView;
import com.petspark.ai.chat.AiChatDtos.AiRecommendRequest;
import com.petspark.ai.chat.AiChatDtos.AiUsageView;
import com.petspark.ai.chat.AiChatRepository;
import com.petspark.ai.chat.AiPromptBuilder;
import com.petspark.ai.chat.AiSafetyPolicy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 真实候选智能推荐服务（PR-AI-03 / API-AI-007 / NFR-AI-001 / US-019）。
 *
 * <p>核心安全约束（NFR-AI-001）：
 * <ul>
 *   <li>100% 展示项必须来自请求时仍有效的真实候选；</li>
 *   <li>推荐功能绝不创建或修改业务对象；</li>
 *   <li>模型输出经服务端再校验：候选集外 ID 拒绝、重复拒绝、空白 reason 拒绝、
 *       敏感/越权 reason 拒绝、实时可见性再校验、≤5 项不填充未校验项；</li>
 *   <li>非法 JSON 或模型失败 → 确定性规则兜底排序（记为 DEGRADED）；</li>
 *   <li>审计只存 SHA-256 哈希，不存明文偏好或模型输出。</li>
 * </ul>
 *
 * <p>流程：
 * <ol>
 *   <li>同意校验（AI_CONSENT_001）；</li>
 *   <li>偏好脱敏 + 注入检查（AI_SAFETY_001，先于 enabled 检查）；</li>
 *   <li>限流（10/min/用户，单实例内存滑动窗口）；</li>
 *   <li>候选检索（≤20 条当前可见真实候选，白名单投影）；</li>
 *   <li>候选为空 → 返回空结果 + 记 DEGRADED；</li>
 *   <li>AI 未启用 → 规则兜底排序 + 记 DEGRADED；</li>
 *   <li>AI 启用 → 调网关 + 解析 JSON + 逐项再校验；</li>
 *   <li>解析失败/网关异常 → 规则兜底 + 记 DEGRADED；</li>
 *   <li>记审计（ai_call_record，scene=RECOMMENDATION）。</li>
 * </ol>
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String SCENE = "RECOMMENDATION";
    private static final String PROVIDER = "spark";
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_ITEMS = 5;
    private static final int MAX_REASON_CHARS = 80;

    private final AiChatGateway gateway;
    private final AiChatRepository repository;
    private final AiSafetyPolicy safetyPolicy;
    private final AiPromptBuilder promptBuilder;
    private final SparkAiProperties properties;
    private final AiAuditWriter auditWriter;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final int rateLimitPerMinute;
    private final Map<String, CandidateRetriever> retrieversByType;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> rateLimitBuckets = new ConcurrentHashMap<>();

    public RecommendationService(
            AiChatGateway gateway,
            AiChatRepository repository,
            AiSafetyPolicy safetyPolicy,
            AiPromptBuilder promptBuilder,
            SparkAiProperties properties,
            AiAuditWriter auditWriter,
            Clock clock,
            List<CandidateRetriever> retrievers,
            ObjectMapper objectMapper,
            @Value("${petspark.ai.rate-limit-per-minute:10}") int rateLimitPerMinute) {
        this.gateway = gateway;
        this.repository = repository;
        this.safetyPolicy = safetyPolicy;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.rateLimitPerMinute = rateLimitPerMinute <= 0 ? 10 : rateLimitPerMinute;
        this.retrieversByType = new java.util.HashMap<>();
        for (CandidateRetriever r : retrievers) {
            this.retrieversByType.put(r.type(), r);
        }
    }

    /**
     * 执行推荐。所有展示项经服务端再校验，确保来自请求时仍有效的真实候选。
     *
     * @param userId 当前登录用户 ID
     * @param req    推荐请求（species + age + preference + candidateType + 可选 petId）
     * @return 推荐回复（items ≤5，boundaryNotice 边界提示）
     */
    public AiRecommendReplyView recommend(String userId, AiRecommendRequest req) {
        // 1. 同意校验（先于 enabled / 注入检查，与 AiChatService 一致）。
        requireConsent(userId);

        // 2. 偏好脱敏 + 注入检查（先于 enabled，注入即使 AI 未启用也须拒答并留审计）。
        String rawPreference = req.preference() == null ? "" : req.preference();
        String sanitizedPreference = safetyPolicy.sanitizeUserInput(rawPreference);

        if (safetyPolicy.isInjection(rawPreference)) {
            String injectionRequestId = UUID.randomUUID().toString();
            auditWriter.recordCall(injectionRequestId, userId, SCENE, PROVIDER, properties.model(),
                    0, 0, "REJECTED", ErrorCode.AI_SAFETY_001.code(), 0, sanitizedPreference, null);
            throw new BusinessException(ErrorCode.AI_SAFETY_001);
        }

        // 3. 限流。
        enforceRateLimit(userId);

        // 4. 候选检索。
        CandidateRetriever retriever = retrieversByType.get(req.candidateType());
        if (retriever == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ENUM_001);
        }
        List<Candidate> candidates = retriever.retrieve(req.species(), req.age(), userId);

        String requestId = UUID.randomUUID().toString();
        long started = System.currentTimeMillis();

        // 5. 候选为空 → 返回空结果 + 记 DEGRADED。
        if (candidates.isEmpty()) {
            int latency = (int) (System.currentTimeMillis() - started);
            auditWriter.recordCall(requestId, userId, SCENE, PROVIDER, properties.model(),
                    0, 0, "DEGRADED", null, latency, sanitizedPreference, null);
            return new AiRecommendReplyView(requestId, List.of(), emptyUsage(), promptBuilder.boundaryNotice());
        }

        // 6. AI 未启用 → 规则兜底排序 + 记 DEGRADED。
        if (!properties.isAvailable()) {
            List<AiRecommendItemView> items = ruleFallback(candidates, sanitizedPreference);
            int latency = (int) (System.currentTimeMillis() - started);
            auditWriter.recordCall(requestId, userId, SCENE, PROVIDER, properties.model(),
                    0, 0, "DEGRADED", null, latency, sanitizedPreference, null);
            return new AiRecommendReplyView(requestId, items, emptyUsage(), promptBuilder.boundaryNotice());
        }

        // 7. AI 启用 → 调网关 + 解析 JSON + 逐项再校验。
        try {
            String userMessage = buildUserMessage(candidates, sanitizedPreference, req.species(), req.age());
            AiChatRequest chatRequest = new AiChatRequest(
                    requestId,
                    promptBuilder.systemPromptForRecommendation(),
                    List.of(new AiMessage("user", userMessage)),
                    MAX_OUTPUT_TOKENS,
                    TEMPERATURE,
                    true);

            AiChatResult result = gateway.chat(chatRequest);
            int latency = (int) (System.currentTimeMillis() - started);
            AiUsage usage = result.usage() == null ? AiUsage.empty() : result.usage();

            // 解析 JSON 并再校验。
            List<AiRecommendItemView> items = parseAndValidate(result.content(), candidates, retriever, userId);
            String outcome = items.isEmpty() && !candidates.isEmpty() ? "DEGRADED" : "SUCCESS";
            // 如果模型返回的内容无法解析出任何有效项，走规则兜底。
            if (items.isEmpty()) {
                items = ruleFallback(candidates, sanitizedPreference);
                outcome = "DEGRADED";
            }

            auditWriter.recordCall(requestId, userId, SCENE, PROVIDER, properties.model(),
                    usage.promptTokens(), usage.completionTokens(), outcome, null, latency,
                    sanitizedPreference, null);

            AiUsageView usageView = new AiUsageView(usage.promptTokens(), usage.completionTokens(),
                    usage.totalTokens());
            return new AiRecommendReplyView(requestId, items, usageView, promptBuilder.boundaryNotice());

        } catch (BusinessException ex) {
            // 网关异常 → 规则兜底。
            int latency = (int) (System.currentTimeMillis() - started);
            log.warn("recommend gateway failed requestId={} reason={}", requestId, ex.toString());
            List<AiRecommendItemView> items = ruleFallback(candidates, sanitizedPreference);
            auditWriter.recordCall(requestId, userId, SCENE, PROVIDER, properties.model(),
                    0, 0, "DEGRADED", ex.errorCode().code(), latency, sanitizedPreference, null);
            return new AiRecommendReplyView(requestId, items, emptyUsage(), promptBuilder.boundaryNotice());
        } catch (RuntimeException ex) {
            // 解析异常或其他 → 规则兜底。
            int latency = (int) (System.currentTimeMillis() - started);
            log.warn("recommend unexpected error requestId={} reason={}", requestId, ex.toString());
            List<AiRecommendItemView> items = ruleFallback(candidates, sanitizedPreference);
            auditWriter.recordCall(requestId, userId, SCENE, PROVIDER, properties.model(),
                    0, 0, "DEGRADED", ErrorCode.INTERNAL_ERROR_001.code(), latency,
                    sanitizedPreference, null);
            return new AiRecommendReplyView(requestId, items, emptyUsage(), promptBuilder.boundaryNotice());
        }
    }

    // ---- 再校验核心 ----

    /**
     * 解析模型 JSON 输出并逐项再校验。
     *
     * <p>再校验规则（NFR-AI-001 安全核心）：
     * <ol>
     *   <li>id 必须在候选集内（按 id 精确匹配）；</li>
     *   <li>type 必须与 candidateType 一致；</li>
     *   <li>reason 不得为空；</li>
     *   <li>reason 不得含注入/越权短语（AiSafetyPolicy.isInjection）；</li>
     *   <li>id 不得重复；</li>
     *   <li>id 必须通过 retriever.isStillValid 实时可见性再校验；</li>
     *   <li>≤5 项，不填充未校验项。</li>
     * </ol>
     *
     * <p>任何一项校验失败则丢弃该项（不抛异常），不填充未校验项。
     * 所有项均失败时返回空列表，由调用方决定是否走规则兜底。
     */
    List<AiRecommendItemView> parseAndValidate(String content, List<Candidate> candidates,
            CandidateRetriever retriever, String userId) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(stripMarkdownFence(content));
        } catch (Exception ex) {
            log.debug("recommend json parse failed reason={}", ex.toString());
            return List.of();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray()) {
            return List.of();
        }

        // 候选 id 集合（用于 O(1) 查找）。
        Set<String> candidateIds = new HashSet<>();
        Map<String, Candidate> candidateById = new java.util.HashMap<>();
        for (Candidate c : candidates) {
            candidateIds.add(c.id());
            candidateById.put(c.id(), c);
        }

        List<AiRecommendItemView> validated = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (JsonNode item : itemsNode) {
            if (validated.size() >= MAX_ITEMS) {
                break;
            }
            String id = item.path("id").asText("");
            String type = item.path("type").asText("");
            String reason = item.path("reason").asText("");

            // 1. id 在候选集内。
            if (!candidateIds.contains(id)) {
                log.debug("recommend reject out-of-set id={}", id);
                continue;
            }
            // 2. type 匹配。
            if (!retriever.type().equals(type)) {
                log.debug("recommend reject type mismatch id={} expected={} got={}", id, retriever.type(), type);
                continue;
            }
            // 3. reason 非空。
            if (reason.isBlank()) {
                log.debug("recommend reject blank reason id={}", id);
                continue;
            }
            // 4. reason 不含注入/越权短语。
            if (safetyPolicy.isInjection(reason)) {
                log.debug("recommend reject injection reason id={}", id);
                continue;
            }
            // 5. id 不重复。
            if (seenIds.contains(id)) {
                log.debug("recommend reject duplicate id={}", id);
                continue;
            }
            // 6. 实时可见性再校验。
            if (!retriever.isStillValid(id, userId)) {
                log.debug("recommend reject stale id={} (no longer valid)", id);
                continue;
            }

            // reason 截断到 80 字 + 脱敏。
            String safeReason = safetyPolicy.sanitizeUserInput(reason);
            if (safeReason.length() > MAX_REASON_CHARS) {
                safeReason = safeReason.substring(0, MAX_REASON_CHARS);
            }

            validated.add(toView(candidateById.get(id), safeReason));
            seenIds.add(id);
        }

        return validated;
    }

    private String stripMarkdownFence(String content) {
        String normalized = content.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        int firstLineEnd = normalized.indexOf('\n');
        int closingFence = normalized.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return normalized;
        }
        return normalized.substring(firstLineEnd + 1, closingFence).trim();
    }

    // ---- 规则兜底排序 ----

    /**
     * 确定性规则兜底排序。不调用模型，直接从候选集排序取前 5。
     *
     * <p>排序策略：偏好关键词在 publicSummary/matchedFacts 中命中者优先，
     * 然后按候选原始顺序（created_at DESC，由 retriever 保证）。
     * reason 由 matchedFacts 生成，不编造事实。
     */
    List<AiRecommendItemView> ruleFallback(List<Candidate> candidates, String sanitizedPreference) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        // 简单确定性排序：偏好关键词命中者靠前，保持原始顺序作 tiebreaker。
        String pref = sanitizedPreference == null ? "" : sanitizedPreference.toLowerCase(Locale.ROOT);
        sorted.sort((a, b) -> {
            int scoreA = matchScore(a, pref);
            int scoreB = matchScore(b, pref);
            return Integer.compare(scoreB, scoreA); // 高分靠前
        });

        List<AiRecommendItemView> items = new ArrayList<>();
        for (Candidate c : sorted) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            String reason = buildRuleReason(c);
            items.add(toView(c, reason));
        }
        return items;
    }

    private AiRecommendItemView toView(Candidate candidate, String reason) {
        return new AiRecommendItemView(candidate.id(), candidate.type(), reason,
                candidate.displayName(), candidate.imageUrl(), candidate.subtitle(),
                candidate.price(), candidate.targetPath());
    }

    private int matchScore(Candidate c, String prefLower) {
        if (prefLower == null || prefLower.isBlank()) {
            return 0;
        }
        int score = 0;
        String summary = c.publicSummary() == null ? "" : c.publicSummary().toLowerCase(Locale.ROOT);
        String facts = c.matchedFacts() == null ? "" : String.join(" ", c.matchedFacts()).toLowerCase(Locale.ROOT);
        // 简单子串匹配：偏好中每个非空 token 在 summary/facts 中命中 +1。
        for (String token : prefLower.split("[\\s,，;；、]+")) {
            if (token.isBlank()) {
                continue;
            }
            if (summary.contains(token) || facts.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private String buildRuleReason(Candidate c) {
        List<String> facts = c.matchedFacts();
        String factStr = facts == null || facts.isEmpty() ? "" : String.join("，", facts);
        String reason = "规则推荐：" + factStr;
        if (reason.length() > MAX_REASON_CHARS) {
            reason = reason.substring(0, MAX_REASON_CHARS);
        }
        return reason;
    }

    // ---- 用户消息构造 ----

    /**
     * 构造发给模型的用户消息：包含候选清单（id/type/summary/matchedFacts）+ 用户偏好。
     * 候选清单为白名单投影，不含敏感字段。
     */
    private String buildUserMessage(List<Candidate> candidates, String preference, String species, int age) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户偏好：").append(preference);
        if (species != null && !species.isBlank()) {
            sb.append("\n宠物物种：").append(species);
        }
        if (age > 0) {
            sb.append("\n宠物月龄：").append(age);
        }
        sb.append("\n\n候选清单（仅允许从此清单内选择）：");
        for (Candidate c : candidates) {
            sb.append("\n- id=").append(c.id());
            sb.append(" type=").append(c.type());
            sb.append(" 描述=").append(c.publicSummary());
            if (c.matchedFacts() != null && !c.matchedFacts().isEmpty()) {
                sb.append(" 匹配事实=").append(String.join("；", c.matchedFacts()));
            }
        }
        sb.append("\n\n请在以上候选清单内排序，返回 JSON：");
        sb.append("{\"items\":[{\"id\":\"候选id\",\"type\":\"候选类型\",\"reason\":\"≤80字推荐理由\"}]}");
        sb.append("，最多 5 项，按推荐度从高到低排序。");
        return sb.toString();
    }

    // ---- 辅助 ----

    private AiUsageView emptyUsage() {
        return new AiUsageView(0, 0, 0);
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
        bucket.removeIf(ts -> ts.isBefore(cutoff));
        if (bucket.size() >= rateLimitPerMinute) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_001);
        }
        bucket.add(now);
    }
}
