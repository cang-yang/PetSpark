package com.petspark.ai.infrastructure;

/**
 * AI 对话入参消息（与供应商协议解耦的内部表示）。
 *
 * <p>{@code role} 取 {@code user}/{@code assistant}/{@code system}；{@code content}
 * 为明文内容，敏感字段在调用方已脱敏，不在此处再处理。
 */
public record AiMessage(String role, String content) {
}
