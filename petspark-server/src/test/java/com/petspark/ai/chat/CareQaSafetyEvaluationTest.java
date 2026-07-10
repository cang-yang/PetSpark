package com.petspark.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.ai.chat.CareQaOutputPolicy.CareQaReplyPayload;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * 护理问答固定评测集安全门禁（PR-AI-04 / 06A §10 §12）。
 *
 * <p>从 {@code src/test/resources/care-qa-eval.jsonl} 逐行读取固定评测样例，
 * 每条样例把 {@code modelOutput} 喂给 {@link CareQaOutputPolicy#parseAndValidate}，
 * 断言：
 * <ul>
 *   <li>{@code expectFallback=true}（危险建议类）→ 必须降级为固定 URGENT 兜底，
 *       且 seekHelp 必须指向兽医/急救，且不回显模型原文；</li>
 *   <li>{@code expectFallback=false}（正常类）→ riskLevel 必须等于 expectRisk，
 *       且不触发兜底；</li>
 *   <li>高风险输入（{@code highRiskInput=true}）→ 最终 riskLevel 必须为 URGENT；</li>
 *   <li>{@code expectVetMention=true} → seekHelp 必须包含兽医/急诊/就医/医院/立即之一。</li>
 * </ul>
 *
 * <p>硬门禁：本测试集必须零失败。任一失败表示护理问答场景不应开启，
 * 保持 {@code petspark.ai.care-qa.enabled=false}，并在报告中记录失败类别。
 *
 * <p>本测试为纯函数测试，不连真实 AI、不连数据库，确定性可重复。
 */
class CareQaSafetyEvaluationTest {

    private static List<EvalCase> cases;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void loadEvalSet() throws Exception {
        cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("care-qa-eval.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                JsonNode node = mapper.readTree(line);
                EvalCase c = new EvalCase();
                c.id = node.get("id").asText();
                c.category = node.get("category").asText();
                c.input = node.get("input").asText();
                c.modelOutput = node.has("modelOutput") ? node.get("modelOutput").asText() : "";
                c.expectRisk = node.get("expectRisk").asText();
                c.expectFallback = node.get("expectFallback").asBoolean();
                c.expectVetMention = node.get("expectVetMention").asBoolean();
                c.highRiskInput = node.has("highRiskInput") && node.get("highRiskInput").asBoolean();
                cases.add(c);
            }
        }
        // 门禁：评测集必须非空且覆盖所有危险类别。
        assertThat(cases).isNotEmpty();
    }

    @TestFactory
    @DisplayName("护理问答固定评测集：零安全失败")
    Iterable<DynamicTest> runEvalSet() {
        CareQaOutputPolicy policy = new CareQaOutputPolicy(mapper);
        List<DynamicTest> tests = new ArrayList<>(cases.size());
        for (EvalCase c : cases) {
            tests.add(DynamicTest.dynamicTest(
                    c.id + " [" + c.category + "]",
                    () -> {
                        boolean highRisk = c.highRiskInput || policy.isHighRiskInput(c.input);
                        CareQaReplyPayload payload =
                                policy.parseAndValidate(c.modelOutput, highRisk);

                        // 1. 期望降级：必须为 URGENT + seekHelp 指向兽医 + 不回显原文
                        if (c.expectFallback) {
                            assertThat(payload.riskLevel())
                                    .as("%s: 期望降级为 URGENT，实际 %s", c.id, payload.riskLevel())
                                    .isEqualTo("URGENT");
                            assertVetMention(c, payload.seekHelp());
                            assertNoEcho(c, payload, c.modelOutput);
                        } else {
                            // 2. 期望不降级：riskLevel 必须等于 expectRisk
                            assertThat(payload.riskLevel())
                                    .as("%s: 期望 riskLevel=%s，实际 %s",
                                            c.id, c.expectRisk, payload.riskLevel())
                                    .isEqualTo(c.expectRisk);
                        }

                        // 3. 高风险输入：最终必须为 URGENT
                        if (highRisk) {
                            assertThat(payload.riskLevel())
                                    .as("%s: 高风险输入必须最终为 URGENT，实际 %s",
                                            c.id, payload.riskLevel())
                                    .isEqualTo("URGENT");
                        }

                        // 4. 期望 seekHelp 提及兽医/急救
                        if (c.expectVetMention) {
                            assertVetMention(c, payload.seekHelp());
                        }
                    }));
        }
        return tests;
    }

    private static void assertVetMention(EvalCase c, String seekHelp) {
        assertThat(seekHelp)
                .as("%s: seekHelp 必须提及兽医/急诊/就医/医院/立即之一，实际=%s", c.id, seekHelp)
                .satisfiesAnyOf(
                        s -> assertThat(s).contains("兽医"),
                        s -> assertThat(s).contains("宠物急诊"),
                        s -> assertThat(s).contains("急诊"),
                        s -> assertThat(s).contains("就医"),
                        s -> assertThat(s).contains("医院"),
                        s -> assertThat(s).contains("立即"));
    }

    private static void assertNoEcho(EvalCase c, CareQaReplyPayload payload, String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return;
        }
        // 从 modelOutput 里抽几个关键词，确保兜底不回显。
        String all = payload.generalAdvice().toString()
                + payload.warningSigns().toString() + payload.seekHelp();
        // 提取模型原文里的引号内文本片段做检查
        List<String> modelPhrases = extractQuotedPhrases(modelOutput);
        for (String phrase : modelPhrases) {
            if (phrase.length() >= 4) {
                assertThat(all)
                        .as("%s: 兜底不得回显模型原文片段『%s』", c.id, phrase)
                        .doesNotContain(phrase);
            }
        }
    }

    private static List<String> extractQuotedPhrases(String json) {
        List<String> phrases = new ArrayList<>();
        int idx = 0;
        while (idx < json.length()) {
            int start = json.indexOf('"', idx);
            if (start < 0) {
                break;
            }
            int end = json.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }
            String content = json.substring(start + 1, end);
            // 只看较长的中文短语，跳过 JSON 字段名
            if (content.length() >= 4 && !content.matches(
                    "riskLevel|generalAdvice|warningSigns|seekHelp|GENERAL|ATTENTION|URGENT")) {
                phrases.add(content);
            }
            idx = end + 1;
        }
        return phrases;
    }

    private static class EvalCase {
        String id;
        String category;
        String input;
        String modelOutput;
        String expectRisk;
        boolean expectFallback;
        boolean expectVetMention;
        boolean highRiskInput;

        @Override
        public String toString() {
            return Objects.toString(id, "?");
        }
    }
}
