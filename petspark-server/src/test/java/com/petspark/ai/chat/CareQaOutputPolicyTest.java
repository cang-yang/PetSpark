package com.petspark.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.ai.chat.CareQaOutputPolicy.CareQaReplyPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CareQaOutputPolicy} 确定性单测（PR-AI-04 / 06A §6.4 §7 §10）。
 *
 * <p>纯函数测试，不依赖真实 AI key。覆盖：
 * <ul>
 *   <li>高风险输入关键词识别；</li>
 *   <li>正常 JSON 解析与字段兜底；</li>
 *   <li>违规措辞（确诊/药物剂量/延迟就医）→ 固定 URGENT 兜底；</li>
 *   <li>高风险输入但模型未给 URGENT 或 seekHelp 未指向兽医 → 兜底；</li>
 *   <li>解析失败/空内容 → 兜底；</li>
 *   <li>兜底不回显模型原文。</li>
 * </ul>
 */
class CareQaOutputPolicyTest {

    private CareQaOutputPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CareQaOutputPolicy(new ObjectMapper());
    }

    // ---- 高风险输入关键词 ----

    @Nested
    @DisplayName("高风险输入关键词识别")
    class HighRiskInput {

        @Test
        void detectsBreathingDifficulty() {
            assertThat(policy.isHighRiskInput("我的狗突然呼吸困难")).isTrue();
        }

        @Test
        void detectsPoisoning() {
            assertThat(policy.isHighRiskInput("可能误食了巧克力，疑似中毒")).isTrue();
        }

        @Test
        void detectsSeizure() {
            assertThat(policy.isHighRiskInput("猫咪在抽搐")).isTrue();
        }

        @Test
        void detectsHeavyBleeding() {
            assertThat(policy.isHighRiskInput("脚被划伤大量出血")).isTrue();
        }

        @Test
        void detectsConvulsionAlias() {
            assertThat(policy.isHighRiskInput("小狗抽风了怎么办")).isTrue();
        }

        @Test
        void benignInputNotHighRisk() {
            assertThat(policy.isHighRiskInput("我家狗今天食欲不太好")).isFalse();
        }

        @Test
        void blankInputNotHighRisk() {
            assertThat(policy.isHighRiskInput(null)).isFalse();
            assertThat(policy.isHighRiskInput("")).isFalse();
            assertThat(policy.isHighRiskInput("   ")).isFalse();
        }
    }

    // ---- 正常解析 ----

    @Nested
    @DisplayName("正常 JSON 解析与字段兜底")
    class ValidParse {

        @Test
        void parsesGeneralRiskPayload() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["保持观察","充足饮水"],
                     "warningSigns":["若持续呕吐需关注"],
                     "seekHelp":"如有异常可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("GENERAL");
            assertThat(payload.generalAdvice()).containsExactly("保持观察", "充足饮水");
            assertThat(payload.warningSigns()).containsExactly("若持续呕吐需关注");
            assertThat(payload.seekHelp()).isEqualTo("如有异常可联系兽医");
        }

        @Test
        void parsesUrgentRiskWithVetSeekHelp() {
            String json = """
                    {"riskLevel":"URGENT",
                     "generalAdvice":["避免自行用药"],
                     "warningSigns":["持续恶化"],
                     "seekHelp":"请立即送往宠物急诊就医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            // seekHelp 指向急诊/就医，合规。
            assertThat(payload.seekHelp()).contains("急诊");
        }

        @Test
        void normalizesLowercaseRisk() {
            String json = """
                    {"riskLevel":"urgent",
                     "generalAdvice":["a"],
                     "warningSigns":["b"],
                     "seekHelp":"立即就医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
        }

        @Test
        void unknownRiskNormalizedToGeneral() {
            String json = """
                    {"riskLevel":"CRITICAL",
                     "generalAdvice":["a"],
                     "warningSigns":["b"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("GENERAL");
        }

        @Test
        void emptyAdviceGetsDefault() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":[],
                     "warningSigns":[],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.generalAdvice()).isNotEmpty();
            assertThat(payload.warningSigns()).isNotEmpty();
        }

        @Test
        void emptySeekHelpGetsDefault() {
            String json = """
                    {"riskLevel":"ATTENTION",
                     "generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":""}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.seekHelp()).isNotBlank();
        }
    }

    // ---- 违规措辞 → 兜底 ----

    @Nested
    @DisplayName("违规措辞降级为固定 URGENT 兜底")
    class ViolationFallback {

        @Test
        void definitiveDiagnosisInAdviceTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["这就是细小病毒，确诊了"],
                     "warningSigns":["加重"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            // 兜底不回显模型原文。
            assertThat(payload.generalAdvice()).doesNotContain("这就是细小病毒，确诊了");
            assertThat(payload.seekHelp()).contains("兽医");
        }

        @Test
        void drugDoseInWarningSignsTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["观察"],
                     "warningSigns":["可喂 5 毫克阿莫西林"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.warningSigns()).doesNotContain("可喂 5 毫克阿莫西林");
        }

        @Test
        void drugDoseMgUnitTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["给 2 mg 药物"],
                     "warningSigns":["加重"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
        }

        @Test
        void delayCareAdviceTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["不用管，先观察几天就好"],
                     "warningSigns":["加重"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.generalAdvice()).doesNotContain("不用管，先观察几天就好");
        }

        @Test
        void delayCareInSeekHelpTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":"不需要看医生，不用去"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.seekHelp()).doesNotContain("不需要看医生");
        }

        @Test
        void fallbackNeverEchoesModelContent() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["确诊为犬瘟热，吃 3 片药"],
                     "warningSigns":["不用就医"],
                     "seekHelp":"不用管"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            // 所有字段都应是固定兜底文案，不含任何模型原文关键词。
            assertThat(payload.generalAdvice().toString()).doesNotContain("犬瘟热");
            assertThat(payload.warningSigns().toString()).doesNotContain("不用就医");
            assertThat(payload.seekHelp()).doesNotContain("不用管");
        }
    }

    // ---- 高风险输入强制 URGENT ----

    @Nested
    @DisplayName("高风险输入强制 URGENT 且 seekHelp 指向兽医/急救")
    class HighRiskInputEnforcement {

        @Test
        void highRiskInputButModelGeneralTriggersFallback() {
            String json = """
                    {"riskLevel":"GENERAL",
                     "generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, true);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
        }

        @Test
        void highRiskInputUrgentButSeekHelpMissingVetTriggersFallback() {
            String json = """
                    {"riskLevel":"URGENT",
                     "generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":"多喝水休息"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, true);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            // seekHelp 被替换为兜底，包含兽医/急救。
            assertThat(payload.seekHelp()).contains("兽医");
        }

        @Test
        void highRiskInputUrgentWithVetSeekHelpPasses() {
            String json = """
                    {"riskLevel":"URGENT",
                     "generalAdvice":["避免自行用药"],
                     "warningSigns":["持续恶化"],
                     "seekHelp":"请立即就近联系兽医或宠物急诊就医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, true);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.seekHelp()).contains("兽医");
        }

        @Test
        void urgentWithoutHighRiskInputButSeekHelpMissingVetTriggersFallback() {
            String json = """
                    {"riskLevel":"URGENT",
                     "generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":"多喝水休息即可"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            // URGENT 即使非高风险输入，seekHelp 也必须指向兽医/急救。
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.seekHelp()).contains("兽医");
        }
    }

    // ---- 解析失败 ----

    @Nested
    @DisplayName("解析失败/空内容 → 固定 URGENT 兜底")
    class ParseFailure {

        @Test
        void emptyContentFallback() {
            CareQaReplyPayload payload = policy.parseAndValidate("", false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.seekHelp()).contains("兽医");
        }

        @Test
        void nullContentFallback() {
            CareQaReplyPayload payload = policy.parseAndValidate(null, false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
        }

        @Test
        void invalidJsonFallback() {
            CareQaReplyPayload payload = policy.parseAndValidate("not a json", false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
            assertThat(payload.seekHelp()).contains("兽医");
        }

        @Test
        void jsonArrayNotObjectFallback() {
            CareQaReplyPayload payload = policy.parseAndValidate("[1,2,3]", false);
            assertThat(payload.riskLevel()).isEqualTo("URGENT");
        }

        @Test
        void missingRiskLevelFallbackToGeneralThenValidated() {
            // riskLevel 缺失 → normalize 为 GENERAL；其他字段合规 → 不降级。
            String json = """
                    {"generalAdvice":["观察"],
                     "warningSigns":["加重"],
                     "seekHelp":"可联系兽医"}""";
            CareQaReplyPayload payload = policy.parseAndValidate(json, false);
            assertThat(payload.riskLevel()).isEqualTo("GENERAL");
        }
    }
}
