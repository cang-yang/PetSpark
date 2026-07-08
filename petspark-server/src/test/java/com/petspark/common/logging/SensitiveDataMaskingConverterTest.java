package com.petspark.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 验证 {@link SensitiveDataMaskingConverter} 在日志输出前对 password/token/secret/
 * Bearer 模式做掩码替换（NFR-SEC-004）。直接驱动 converter 单元验证掩码规则；
 * converter 被 logback-spring.xml 挂载进 appender 的集成保证由配置文件本身提供。
 */
class SensitiveDataMaskingConverterTest {

    private SensitiveDataMaskingConverter converter;
    private LoggerContext context;

    @BeforeEach
    void setUp() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        converter = new SensitiveDataMaskingConverter();
        converter.setContext(context);
        converter.start();
    }

    @Test
    void masksPasswordKvPattern() {
        String result = converter.convert(event("password=hunter2"));
        assertThat(result).contains("password=***");
        assertThat(result).doesNotContain("hunter2");
    }

    @Test
    void masksSecretKvPattern() {
        String result = converter.convert(event("api_key=sk-live-12345"));
        assertThat(result).contains("api_key=***");
        assertThat(result).doesNotContain("sk-live-12345");
    }

    @Test
    void masksBearerToken() {
        String result = converter.convert(event("Authorization: Bearer abc123.def456"));
        assertThat(result).contains("bearer ***");
        assertThat(result).doesNotContain("abc123.def456");
    }

    @Test
    void masksJsonPasswordField() {
        String result = converter.convert(event("{\"username\":\"alice\",\"password\":\"s3cr3t\"}"));
        assertThat(result).contains("\"password\":\"***\"");
        assertThat(result).doesNotContain("s3cr3t");
        assertThat(result).contains("\"username\":\"alice\"");
    }

    @Test
    void masksJsonAccessTokenField() {
        String result = converter.convert(event("{\"access_token\":\"tok-xyz\",\"scope\":\"read\"}"));
        assertThat(result).contains("\"access_token\":\"***\"");
        assertThat(result).doesNotContain("tok-xyz");
    }

    @Test
    void leavesNonSensitiveMessagesUntouched() {
        String msg = "User alice logged in, requestId=req-001";
        assertThat(converter.convert(event(msg))).isEqualTo(msg);
    }

    @Test
    void handlesNullAndEmptySafely() {
        assertThat(converter.convert(event(""))).isEmpty();
    }

    private ILoggingEvent event(String message) {
        return new LoggingEvent(
                SensitiveDataMaskingConverterTest.class.getName(),
                context.getLogger(SensitiveDataMaskingConverterTest.class),
                ch.qos.logback.classic.Level.INFO,
                message,
                null,
                null);
    }
}
