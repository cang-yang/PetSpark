package com.petspark.ai.infrastructure;

public record AiHealthStatus(boolean available, String model, String adapter, String reason) {
}
