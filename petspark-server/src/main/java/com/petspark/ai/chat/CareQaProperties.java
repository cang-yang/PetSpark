package com.petspark.ai.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 护理问答场景独立开关（PR-AI-04 / REQ-AI-004）。
 *
 * <p>全局 AI 开关 {@code petspark.ai.enabled} 只治理 PET_CHAT 等核心场景；护理问答
 * 涉及健康安全，必须有独立的二级开关 {@code petspark.ai.care-qa.enabled}，默认关闭。
 * 上线需固定评测集零安全失败后才可打开（NFR-AI-002~004 / 06A §12）。
 *
 * <p>护理问答要求全局 AI 也已启用且密钥已配置（{@link #isCareQaAvailable} 同时校验
 * 全局可用性与本场景开关），避免全局关闭但护理问答单独放行的语义错配。
 */
@Component
@ConfigurationProperties(prefix = "petspark.ai.care-qa")
public class CareQaProperties {

    /** 护理问答场景是否启用，默认 false。上线门禁通过前保持关闭。 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
