package com.petspark.ai.infrastructure;

/**
 * AI 对话返回结果。{@code providerRequestId} 为供应商返回的调用标识（可为空），
 * {@code content} 为模型回复正文，{@code usage} 为 token 用量，{@code model} 为
 * 实际使用的模型名（便于审计与降级排查）。
 */
public record AiChatResult(
        String providerRequestId,
        String content,
        AiUsage usage,
        String model) {
}
