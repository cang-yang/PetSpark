package com.petspark.ai.infrastructure;

import java.util.List;

/**
 * AI 对话请求。{@code requestId} 由调用方生成用于落审计/调用记录与重试去重；
 * {@code systemPrompt} 为系统边界提示，{@code messages} 为多轮上下文；
 * {@code jsonOutput} 为 true 时要求供应商返回 JSON 对象（探针/结构化场景）。
 */
public record AiChatRequest(
        String requestId,
        String systemPrompt,
        List<AiMessage> messages,
        int maxOutputTokens,
        double temperature,
        boolean jsonOutput) {
}
