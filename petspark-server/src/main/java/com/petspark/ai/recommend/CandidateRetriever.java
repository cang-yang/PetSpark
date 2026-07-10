package com.petspark.ai.recommend;

import java.util.List;

/**
 * 候选检索器接口（PR-AI-03）。
 *
 * <p>每个实现负责一种候选类型（PET / GOODS / SERVICE），提供：
 * <ul>
 *   <li>{@link #retrieve}：只读检索 ≤20 条当前可见候选，白名单投影为 {@link Candidate}；</li>
 *   <li>{@link #isStillValid}：对模型选出的 id 做实时再校验，确保展示项在请求时仍有效。</li>
 * </ul>
 *
 * <p>再校验是 NFR-AI-001 的安全核心：模型返回的 id 必须通过 isStillValid 才能出现在结果中。
 */
public interface CandidateRetriever {

    /** 返回此检索器负责的候选类型：PET / GOODS / SERVICE。 */
    String type();

    /**
     * 检索当前可见候选。只读、确定性预过滤，≤20 条。
     *
     * @param species 宠物物种（PET 类型时按此过滤；GOODS/SERVICE 可忽略或仅作上下文）
     * @param age     宠物月龄（上下文，不直接过滤）
     * @param userId  当前登录用户 ID（PET 类型用于可见性判定：自己的私有 + 所有公开）
     * @return 候选列表，按 created_at DESC 排序
     */
    List<Candidate> retrieve(String species, int age, String userId);

    /**
     * 实时再校验：给定 id 在当前时刻是否仍可见/有效。
     * 用于对模型输出做服务端再校验（NFR-AI-001 安全核心）。
     */
    boolean isStillValid(String id, String userId);
}
