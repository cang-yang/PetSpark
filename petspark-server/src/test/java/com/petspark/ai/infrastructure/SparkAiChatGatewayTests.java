package com.petspark.ai.infrastructure;

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
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class SparkAiChatGatewayTests {

    @Test
    void nonStreamingProbeMapsContentAndUsage(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-password"))
                .withRequestBody(containing("\"model\":\"spark-x\""))
                .withRequestBody(containing("\"temperature\":0.4"))
                .withRequestBody(containing("\"max_tokens\":1024"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": 0,
                                  "choices": [{"message": {"content": "pong"}}],
                                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                                }
                                """)));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        AiProbeResult result = gateway.probe(new AiProbeRequest("system boundary", "ping", false));

        assertThat(result.content()).isEqualTo("pong");
        assertThat(result.usage().promptTokens()).isEqualTo(10);
        assertThat(result.usage().completionTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        wireMock.getWireMock().verifyThat(postRequestedFor(urlEqualTo("/v2/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-password")));
    }

    @Test
    void provider401MapsToAuthError(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"bad credential\"}")));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        assertThatThrownBy(() -> gateway.probe(new AiProbeRequest("system", "ping", false)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_AUTH_001));
    }

    @Test
    void provider429MapsToLimitError(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .willReturn(aResponse().withStatus(429).withBody("{\"message\":\"quota exceeded\"}")));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        assertThatThrownBy(() -> gateway.probe(new AiProbeRequest("system", "ping", false)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_LIMIT_001));
    }

    @Test
    void providerBusinessCodeMapsToProviderError(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":1200,\"message\":\"provider business error\"}")));
        AiChatGateway gateway = gateway(wireMock.getHttpBaseUrl());

        assertThatThrownBy(() -> gateway.probe(new AiProbeRequest("system", "ping", false)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_001));
    }

    @Test
    void timeoutMapsToProviderUnavailable(WireMockRuntimeInfo wireMock) {
        wireMock.getWireMock().register(post(urlEqualTo("/v2/chat/completions"))
                .willReturn(aResponse()
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":0,\"choices\":[{\"message\":{\"content\":\"late\"}}]}")));
        AiChatGateway gateway = new SparkAiChatGateway(
                new SparkAiProperties(true, wireMock.getHttpBaseUrl() + "/v2", "spark-x", "test-password",
                        Duration.ofMillis(10), Duration.ofMillis(10)),
                RestClient.builder());

        assertThatThrownBy(() -> gateway.probe(new AiProbeRequest("system", "ping", false)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_001));
    }

    @Test
    void disabledGatewayDegradesWithoutCallingProvider(WireMockRuntimeInfo wireMock) {
        AiChatGateway gateway = new SparkAiChatGateway(
                new SparkAiProperties(false, wireMock.getHttpBaseUrl(), "spark-x", "", Duration.ofMillis(500), Duration.ofMillis(500)),
                RestClient.builder());

        assertThat(gateway.health().available()).isFalse();
        assertThat(gateway.health().reason()).contains("AI 服务未启用");
        assertThatThrownBy(() -> gateway.probe(new AiProbeRequest("system", "ping", false)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.AI_DISABLED_001));
        wireMock.getWireMock().verifyThat(0, postRequestedFor(urlEqualTo("/v2/chat/completions")));
    }

    private AiChatGateway gateway(String baseUrl) {
        return new SparkAiChatGateway(
                new SparkAiProperties(true, baseUrl + "/v2", "spark-x", "test-password", Duration.ofSeconds(2), Duration.ofSeconds(2)),
                RestClient.builder());
    }
}
