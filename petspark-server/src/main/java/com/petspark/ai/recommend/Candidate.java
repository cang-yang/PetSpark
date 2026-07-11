package com.petspark.ai.recommend;

import java.util.List;
import java.math.BigDecimal;

/**
 * 推荐候选值对象（PR-AI-03）。
 *
 * <p>白名单投影：仅暴露 id、type、publicSummary 与 matchedFacts 给模型，
 * 不含任何敏感字段（owner 信息、价格内部值等）。publicSummary 由各 retriever
 * 从业务对象的公开字段构造，经过截断处理。
 *
 * <p>NFR-AI-001：候选必须是请求时仍有效的真实对象，推荐功能绝不创建或修改业务对象。
 */
public record Candidate(
        String id,
        String type,
        String publicSummary,
        List<String> matchedFacts,
        String displayName,
        String imageUrl,
        String subtitle,
        BigDecimal price,
        String targetPath) {
    Candidate(String id, String type, String publicSummary, List<String> matchedFacts) {
        this(id, type, publicSummary, matchedFacts, id, null, null, null,
                "/" + type.toLowerCase(java.util.Locale.ROOT) + "/" + id);
    }
}
