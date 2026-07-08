package com.petspark.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * 敏感信息日志脱敏转换器。在写入日志前对常见敏感模式做掩码替换，
 * 满足 NFR-SEC-004（密钥与环境敏感配置不得进入日志）与 NFR-OBS-001
 * （日志含必要上下文且不泄露敏感信息）。
 *
 * <p>覆盖模式：密码/令牌/密钥/API key/Bearer/Authorization 头。仅做尽力脱敏，
 * 不替代上游不输出敏感数据的纪律——业务代码仍不应主动打印这些字段。
 *
 * <p>替换顺序很关键，避免一个模式把另一个模式的掩码当成新值再次消费：
 * <ol>
 *   <li>{@link #AUTH_HEADER}：{@code Authorization: Bearer <token>} 整体替换为
 *       {@code Authorization: bearer ***}（保留键名便于排查）；</li>
 *   <li>{@link #BEARER}：裸 {@code Bearer <token>} 替换为 {@code bearer ***}；</li>
 *   <li>{@link #JSON_KV}：JSON 里的 {@code "password":"..."} 替换为
 *       {@code "password":"***"}；</li>
 *   <li>{@link #PASSWORD_KV}：{@code password=...} / {@code api_key=...} 替换为
 *       {@code password=***}。不含 {@code authorization}，避免二次消费 Bearer 结果。</li>
 * </ol>
 */
public class SensitiveDataMaskingConverter extends MessageConverter {

    private static final String MASK = "***";

    // Authorization: Bearer xxx  ——  整体保留键名，仅掩令牌。
    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)bearer\\s+[A-Za-z0-9._~+/=\\-]+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._~+/=\\-]+");
    // JSON 里的 "password":"..."、"access_token":"..."
    private static final Pattern JSON_KV = Pattern.compile(
            "(?i)\"(password|passwd|secret|api[-_]?key|access[-_]?token|refresh[-_]?token|authorization)\"\\s*:\\s*\"[^\"]*\"");
    // 形如 password=secret / api_key=sk-... （不含 authorization，由 AUTH_HEADER/BEARER 处理）
    private static final Pattern PASSWORD_KV = Pattern.compile(
            "(?i)(password|passwd|secret|api[-_]?key|api[-_]?password|spark[-_]?api[-_]?password|access[-_]?token|refresh[-_]?token)\\s*[:=]\\s*['\"]?[A-Za-z0-9._~+/=\\-]+");

    @Override
    public String convert(ILoggingEvent event) {
        String original = super.convert(event);
        if (original == null || original.isEmpty()) {
            return original;
        }
        String masked = AUTH_HEADER.matcher(original).replaceAll(
                m -> quoteReplacement(m.group(1)) + "bearer " + MASK);
        masked = BEARER.matcher(masked).replaceAll("bearer " + MASK);
        masked = JSON_KV.matcher(masked).replaceAll(
                m -> "\"" + quoteReplacement(m.group(1)) + "\":\"" + MASK + "\"");
        masked = PASSWORD_KV.matcher(masked).replaceAll(
                m -> quoteReplacement(m.group(1)) + "=" + MASK);
        return masked;
    }

    private static String quoteReplacement(String input) {
        return input.replace("\\", "\\\\").replace("$", "\\$");
    }
}
