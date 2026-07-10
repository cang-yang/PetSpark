package com.petspark.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 护理问答输出策略（PR-AI-04 / REQ-AI-004 / 06A §6.4 §7）。
 *
 * <p>模型按 {@code jsonOutput=true} 返回一段 JSON 文本，结构应为：
 * <pre>
 * { "riskLevel": "GENERAL|ATTENTION|URGENT",
 *   "generalAdvice": ["..."],
 *   "warningSigns": ["..."],
 *   "seekHelp": "..." }
 * </pre>
 * 本组件：
 * <ul>
 *   <li>解析与校验该 JSON；字段缺失或非法 → 返回固定 URGENT 兜底
 *       （不回显模型正文，避免泄露不可信内容），并按 {@link ErrorCode#AI_OUTPUT_001} 抛出；</li>
 *   <li>强制安全规则：
 *     <ul>
 *       <li>不得出现确定性诊断措辞（"确诊/诊断为/就是XX病"等）；</li>
 *       <li>不得出现具体药物剂量（"吃X毫克/X粒"等具体数值）；</li>
 *       <li>不得建议延迟就医（"不用管/先观察几天就好"等）；</li>
 *       <li>高风险输入关键词或模型 URGENT → seekHelp 必须指向线下兽医/急救。</li>
 *     </ul>
 *   </li>
 *   <li>命中违规 → 降级为固定 URGENT 安全兜底回复（不把模型原文带给前端）；</li>
 *   <li>始终返回固定的非诊断声明 {@link AiPromptBuilder#careQaDisclaimer()}，
 *       由服务层附加在视图上，模型无法抑制或改写。</li>
 * </ul>
 *
 * <p>本组件为纯函数（除只读 ObjectMapper），便于不依赖真实 AI key 的确定性单测。
 */
@Component
public class CareQaOutputPolicy {

    private static final Logger log = LoggerFactory.getLogger(CareQaOutputPolicy.class);

    /** 风险等级枚举值。 */
    public static final String RISK_GENERAL = "GENERAL";
    public static final String RISK_ATTENTION = "ATTENTION";
    public static final String RISK_URGENT = "URGENT";

    /** 高风险输入关键词（06A §10 健康高风险样例）。命中即强制 URGENT。 */
    public static final String[] HIGH_RISK_KEYWORDS = {
            "呼吸困难", "中毒", "抽搐", "大量出血", "抽风"
    };

    /** 禁止的诊断性措辞（出现即判定违规并降级）。 */
    private static final String[] DEFINITIVE_DIAGNOSIS_PHRASES = {
            "确诊", "诊断为", "诊断是", "就是", "明确是", "可以确定是"
    };

    /** 禁止的药物剂量措辞（具体数值）。 */
    private static final String[] DRUG_DOSE_PHRASES = {
            "毫克", "mg", "毫升", "ml", "粒", "片", "滴"
    };

    /** 禁止的延迟就医措辞。 */
    private static final String[] DELAY_CARE_PHRASES = {
            "不用管", "不用就医", "不用去", "先观察几天就好", "不需要看医生", "可以不处理"
    };

    /** 兽医/急救求助兜底文案，URGENT 或违规时强制使用。 */
    static final String FALLBACK_SEEK_HELP_URGENT =
            "请立即就近联系兽医或宠物急诊就医，不要自行用药或等待。";

    static final String FALLBACK_SEEK_HELP_ATTENTION =
            "建议尽快联系兽医进行专业评估，不要自行用药。";

    private final ObjectMapper objectMapper;

    public CareQaOutputPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 判定输入是否命中高风险关键词。
     *
     * @param rawInput 原始用户输入（未脱敏亦可，关键词不含敏感片段）
     * @return true 表示应强制 URGENT
     */
    public boolean isHighRiskInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return false;
        }
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (rawInput.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析并校验模型 JSON 输出，返回安全的 {@link CareQaReplyPayload}。
     *
     * <p>解析失败、字段缺失、违规措辞、或高风险输入但模型未给 URGENT/seekHelp 不指向
     * 兽医时，返回固定 URGENT 兜底 payload（不回显模型原文）。
     *
     * @param modelContent 模型返回的 JSON 文本
     * @param highRiskInput 用户输入是否命中高风险关键词
     * @return 安全的回复载荷（始终带固定 seekHelp 与 disclaimer 由服务层附加）
     */
    public CareQaReplyPayload parseAndValidate(String modelContent, boolean highRiskInput) {
        if (modelContent == null || modelContent.isBlank()) {
            log.warn("care-qa empty model content, fallback to URGENT safety reply");
            return urgentFallback();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(modelContent);
        } catch (IOException ex) {
            log.warn("care-qa model content not valid JSON, fallback to URGENT safety reply");
            return urgentFallback();
        }
        if (root == null || !root.isObject()) {
            return urgentFallback();
        }
        String riskLevel = textOf(root, "riskLevel");
        List<String> generalAdvice = stringList(root, "generalAdvice");
        List<String> warningSigns = stringList(root, "warningSigns");
        String seekHelp = textOf(root, "seekHelp");

        // 规范化风险等级
        String normalizedRisk = normalizeRisk(riskLevel);

        // 安全规则：检查所有字段是否含违规措辞
        boolean violation = containsViolation(generalAdvice)
                || containsViolation(warningSigns)
                || containsViolation(List.of(seekHelp));

        // 高风险输入 → 必须为 URGENT，且 seekHelp 必须指向兽医/急救
        if (highRiskInput) {
            if (!RISK_URGENT.equals(normalizedRisk)) {
                log.warn("care-qa high-risk input but model risk={} → force URGENT", normalizedRisk);
                violation = true;
            }
            if (!mentionsVetOrEmergency(seekHelp)) {
                log.warn("care-qa high-risk input but seekHelp does not mention vet/ER → fallback");
                violation = true;
            }
        }

        // URGENT 但 seekHelp 没有指向兽医/急救 → 违规
        if (RISK_URGENT.equals(normalizedRisk) && !mentionsVetOrEmergency(seekHelp)) {
            log.warn("care-qa URGENT but seekHelp lacks vet/ER → fallback");
            violation = true;
        }

        if (violation) {
            return urgentFallback();
        }

        // 字段兜底：空数组/空串给默认
        if (generalAdvice.isEmpty()) {
            generalAdvice = List.of("请结合宠物具体情况观察，如有异常及时联系兽医。");
        }
        if (warningSigns.isEmpty()) {
            warningSigns = RISK_URGENT.equals(normalizedRisk)
                    ? List.of("持续恶化、精神萎靡、食欲废绝等情况需立即就医。")
                    : List.of("若症状加重或持续，请及时联系兽医。");
        }
        if (seekHelp == null || seekHelp.isBlank()) {
            seekHelp = RISK_URGENT.equals(normalizedRisk)
                    ? FALLBACK_SEEK_HELP_URGENT
                    : FALLBACK_SEEK_HELP_ATTENTION;
        }

        return new CareQaReplyPayload(normalizedRisk, generalAdvice, warningSigns, seekHelp);
    }

    /** 固定 URGENT 兜底回复：不回显模型原文，只给安全文案。 */
    private CareQaReplyPayload urgentFallback() {
        return new CareQaReplyPayload(
                RISK_URGENT,
                List.of("该情况可能存在健康风险，请避免自行判断与用药。"),
                List.of("请立即就医，不要等待症状自行缓解。"),
                FALLBACK_SEEK_HELP_URGENT);
    }

    private static boolean containsViolation(List<String> texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String phrase : DEFINITIVE_DIAGNOSIS_PHRASES) {
                if (text.contains(phrase)) {
                    return true;
                }
            }
            for (String phrase : DRUG_DOSE_PHRASES) {
                if (text.contains(phrase)) {
                    return true;
                }
            }
            for (String phrase : DELAY_CARE_PHRASES) {
                if (text.contains(phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mentionsVetOrEmergency(String seekHelp) {
        if (seekHelp == null || seekHelp.isBlank()) {
            return false;
        }
        return seekHelp.contains("兽医") || seekHelp.contains("宠物急诊")
                || seekHelp.contains("急诊") || seekHelp.contains("就医")
                || seekHelp.contains("医院") || seekHelp.contains("立即");
    }

    private static String normalizeRisk(String risk) {
        if (risk == null) {
            return RISK_GENERAL;
        }
        String upper = risk.trim().toUpperCase();
        return switch (upper) {
            case RISK_GENERAL, RISK_ATTENTION, RISK_URGENT -> upper;
            default -> RISK_GENERAL;
        };
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isTextual()) {
            return child.asText();
        }
        return child.toString();
    }

    private static List<String> stringList(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || !child.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(child.size());
        for (JsonNode item : child) {
            if (item != null && !item.isNull()) {
                String text = item.isTextual() ? item.asText() : item.toString();
                if (text != null && !text.isBlank()) {
                    list.add(text);
                }
            }
        }
        return list;
    }

    /**
     * 护理问答安全回复载荷。服务层将其装入 {@code AiChatReplyView} 并附加固定声明。
     */
    public record CareQaReplyPayload(
            String riskLevel,
            List<String> generalAdvice,
            List<String> warningSigns,
            String seekHelp) {
    }
}
