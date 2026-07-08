package com.petspark.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens) {

    public static AiUsage empty() {
        return new AiUsage(0, 0, 0);
    }
}
