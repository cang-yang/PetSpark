package com.petspark.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SparkSensitiveDataMaskingTests {

    @Test
    void masksSparkApiPasswordInKeyValueLogs() {
        SensitiveDataMaskingConverter converter = new SensitiveDataMaskingConverter();
        converter.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        converter.start();
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("SPARK_API_PASSWORD=real-secret Authorization: Bearer token-value");

        String masked = converter.convert(event);

        assertThat(masked).contains("SPARK_API_PASSWORD=***");
        assertThat(masked).contains("Authorization: bearer ***");
        assertThat(masked).doesNotContain("real-secret");
        assertThat(masked).doesNotContain("token-value");
    }
}
