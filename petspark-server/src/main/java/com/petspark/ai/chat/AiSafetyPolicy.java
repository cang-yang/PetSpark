package com.petspark.ai.chat;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AI 输入安全策略：
 * <ul>
 *   <li>{@link #sanitizeUserInput(String)} 将用户文本中的手机号、邮箱、身份证号、
 *       地址片段以 {@code ***} 脱敏后返回，并按 4000 字截断；</li>
 *   <li>{@link #isInjection(String)} 识别提示词注入与越权指令，命中即拒答；</li>
 *   <li>{@link #safetyLabelFor(String)} 给出最终安全标签 INJECTION/SENSITIVE/OK，
 *       与 ai_message.safety_label 列对应。</li>
 * </ul>
 *
 * <p>安全检查在调用模型前完成：注入直接拒答（不调用网关），敏感内容脱敏后再送模型，
 * 模型输出不允许包含敏感回显或执行指令。本组件为纯函数无状态，便于单测。
 */
@Component
public class AiSafetyPolicy {

    /** 用户单条输入上限。超过按前 4000 字截断，避免上下文超限。 */
    public static final int MAX_USER_INPUT_CHARS = 4000;

    private static final Pattern PHONE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    // 18 位身份证（含可能尾号 X）。
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    // 简易地址线索：xx省/市/区/路 xx号 等关键字触发。
    private static final Pattern ADDRESS = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,8}(省|市|区|县|镇|乡|村|街道))" +
            "|([\\u4e00-\\u9fa5]{2,8}(路|街|大道|巷|号|幢|栋|单元))");

    private static final String[] INJECTION_PHRASES = {
            "ignore previous", "忽略以上", "忽略前面",
            "system prompt", "系统提示", "你的系统",
            "你现在是", "reveal your instructions", "输出你的指令",
            "执行", "delete from", "drop table", "给我管理员", "越权",
            "扮演", "jailbreak"
    };

    /**
     * 脱敏并截断用户输入。返回的字符串中敏感片段已被 {@code ***} 替换。
     */
    public String sanitizeUserInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_USER_INPUT_CHARS) {
            trimmed = trimmed.substring(0, MAX_USER_INPUT_CHARS);
        }
        String masked = PHONE.matcher(trimmed).replaceAll("***");
        masked = EMAIL.matcher(masked).replaceAll("***");
        masked = ID_CARD.matcher(masked).replaceAll("***");
        masked = ADDRESS.matcher(masked).replaceAll("***");
        return masked;
    }

    /**
     * 判定是否为提示词注入或越权指令。命中即由服务层抛 AI_SAFETY_001，不调用模型。
     */
    public boolean isInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String lower = input.toLowerCase();
        for (String phrase : INJECTION_PHRASES) {
            if (lower.contains(phrase.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给出综合安全标签：INJECTION 优先于 SENSITIVE，否则 OK。
     */
    public String safetyLabelFor(String input) {
        if (isInjection(input)) {
            return "INJECTION";
        }
        if (input == null || input.isBlank()) {
            return "OK";
        }
        String sanitized = sanitizeUserInput(input);
        return sanitized.equals(input.strip()) ? "OK" : "SENSITIVE";
    }
}
