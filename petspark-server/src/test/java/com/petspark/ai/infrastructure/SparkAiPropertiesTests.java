package com.petspark.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SparkAiPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void aiGatewayIsUnavailableWhenPasswordIsMissing() {
        contextRunner
                .withPropertyValues(
                        "petspark.ai.enabled=true",
                        "petspark.ai.base-url=https://spark-api-open.xf-yun.com/v2",
                        "petspark.ai.model=spark-x")
                .run(context -> {
                    SparkAiProperties properties = context.getBean(SparkAiProperties.class);

                    assertThat(properties.isAvailable()).isFalse();
                    assertThat(properties.unavailableReason()).isEqualTo("SPARK_API_PASSWORD is not configured");
                });
    }

    @Test
    void sparkPasswordCanBeReadFromConfigurationWithoutExposingRawValue() {
        contextRunner
                .withPropertyValues(
                        "petspark.ai.enabled=true",
                        "petspark.ai.api-password=test-secret-value",
                        "petspark.ai.base-url=https://spark-api-open.xf-yun.com/v2/",
                        "petspark.ai.model=spark-x")
                .run(context -> {
                    SparkAiProperties properties = context.getBean(SparkAiProperties.class);

                    assertThat(properties.isAvailable()).isTrue();
                    assertThat(properties.normalizedBaseUrl()).isEqualTo("https://spark-api-open.xf-yun.com/v2");
                    assertThat(properties.maskedApiPassword()).isEqualTo("te***ue");
                    assertThat(properties.toString()).doesNotContain("test-secret-value");
                });
    }

    @Configuration
    @EnableConfigurationProperties(SparkAiProperties.class)
    static class PropertiesConfig {
    }
}
