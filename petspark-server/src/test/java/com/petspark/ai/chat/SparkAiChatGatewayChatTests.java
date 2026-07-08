package com.petspark.ai.chat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.petspark.ai.infrastructure.AiChatGateway;
import com.petspark.ai.infrastructure.AiChatRequest;
import com.petspark.ai.infrastructure.AiChatResult;
import com.petspark.ai.infrastructure.AiMessage;
import com.petspark.ai.infrastructure.SparkAiChatGateway;
import com.petspark.ai.infrastructure.SparkAiProperties;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * {@link SparkAiChatGateway#chat} 的 WireMock 契约单测。沿用既有 probe 测试风格，
 * 只验证 chat 路径：系统提示与多轮上下文被正确拼入请求体、空内容映射为
 * {@link ErrorCode#AI_OUTPUT_001}、成功路径返回 {@link AiChatResult}。
 *
 * <p>不连真实 Spark；不连数据库；可独立运行。
 */
@WireMockTest
class SparkAiChatGatewayChatTests {

    @Test
    void chatSendsSystemPromptAndHistoryAndMapsResult(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-password"))
                .withRequestBody(containing("\"role\":\"system\""))
                .withRequestBody(containing("\"content\":\"boundary-system-prompt\""))
                .withRequestBody(containing("\"role\":\"user\""))
                .withRequestBody(containing("\"content\":\"hello\""))
                .withRequestBody(containing("\"role\":\"assistant\""))
                .withRequestBody(containing("\"content\":\"hi\""))
                .withRequestBody(containing("\"max_tokens\":1024"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": 0,
                                  "choices": [{"message": {"content": "I am fine"}}],
                                  "usage": {"prompt_tokens": 20, "completion_tokens": 8, "total_tokens": 28}
                                }
                                """)));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        AiChatRequest request = new AiChatRequest("req-1", "boundary-system-prompt",
                List.of(new AiMessage("user", "hello"), new AiMessage("assistant", "hi"),
                        new AiMessage("user", "again")),
                1024, 0.4, false);

        AiChatResult result = gateway.chat(request);

        assertThat(result.content()).isEqualTo("I am fine");
        assertThat(result.usage().promptTokens()).isEqualTo(20);
        assertThat(result.usage().completionTokens()).isEqualTo(8);
        assertThat(result.usage().totalTokens()).isEqualTo(28);
        assertThat(result.model()).isEqualTo("spark-x");
        wireMock.getWireMock().verifyThat(1, postRequestedFor(urlEqualTo("/v2/chat/completions")));
    }

    @Test
    void emptyContentMapsToOutputFormatError(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": 0,
                                  "choices": [{"message": {"content": ""}}],
                                  "usage": {"prompt_tokens": 5, "completion_tokens": 0, "total_tokens": 5}
                                }
                                """)));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        AiChatRequest request = new AiChatRequest("req-2", "sys", List.of(new AiMessage("user", "hi")),
                512, 0.4, false);

        assertThatThrownBy(() -> gateway.chat(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_OUTPUT_001));
    }

    @Test
    void defaultMaxTokensAppliedWhenNonPositive(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .withRequestBody(containing("\"max_tokens\":1024"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": 0,
                                  "choices": [{"message": {"content": "ok"}}],
                                  "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
                                }
                                """)));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        AiChatRequest request = new AiChatRequest("req-3", "sys",
                List.of(new AiMessage("user", "hi")), 0, 0.4, false);

        AiChatResult result = gateway.chat(request);
        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void jsonOutputRequestCarriesResponseFormat(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .withRequestBody(containing("\"response_format\":{\"type\":\"json_object\"}"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        // 用 \" 序列对 JSON 串字面量二次转义：content 是返回模型正文的字符串，
                        // 这里给的是一段 JSON 文本 {"ok":true}，在 JSON 字符串里需写为 "{\"ok\":true}"。
                        .withBody("""
                                {
                                  "code": 0,
                                  "choices": [{"message": {"content": "{\\"ok\\":true}"}}],
                                  "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
                                }
                                """)));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        AiChatRequest request = new AiChatRequest("req-4", "sys",
                List.of(new AiMessage("user", "hi")), 256, 0.2, true);

        AiChatResult result = gateway.chat(request);
        assertThat(result.content()).isEqualTo("{\"ok\":true}");
    }

    private AiChatGateway gateway(String baseUrl) {
        return new SparkAiChatGateway(
                new SparkAiProperties(true, baseUrl + "/v2", "spark-x", "test-password",
                        Duration.ofSeconds(2), Duration.ofSeconds(2)),
                RestClient.builder());
    }
}
