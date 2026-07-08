package com.petspark.ai.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "petspark.ai")
public record SparkAiProperties(
        boolean enabled,
        String baseUrl,
        String model,
        String apiPassword,
        Duration connectTimeout,
        Duration readTimeout) {

    private static final String DEFAULT_BASE_URL = "https://spark-api-open.xf-yun.com/v2";
    private static final String DEFAULT_MODEL = "spark-x";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    public SparkAiProperties {
        baseUrl = defaultIfBlank(baseUrl, DEFAULT_BASE_URL);
        model = defaultIfBlank(model, DEFAULT_MODEL);
        apiPassword = apiPassword == null ? "" : apiPassword.trim();
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }

    public boolean isAvailable() {
        return enabled && !apiPassword.isBlank();
    }

    public String unavailableReason() {
        if (!enabled) {
            return "AI gateway is disabled";
        }
        if (apiPassword.isBlank()) {
            return "SPARK_API_PASSWORD is not configured";
        }
        return "";
    }

    public String normalizedBaseUrl() {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public String maskedApiPassword() {
        if (apiPassword.isBlank()) {
            return "";
        }
        if (apiPassword.length() <= 4) {
            return "***";
        }
        return apiPassword.substring(0, 2) + "***" + apiPassword.substring(apiPassword.length() - 2);
    }

    @Override
    public String toString() {
        return "SparkAiProperties[enabled=%s, baseUrl=%s, model=%s, apiPassword=%s, connectTimeout=%s, readTimeout=%s]"
                .formatted(enabled, normalizedBaseUrl(), model, maskedApiPassword(), connectTimeout, readTimeout);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
