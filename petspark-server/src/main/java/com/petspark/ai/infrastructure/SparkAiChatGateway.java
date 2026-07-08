package com.petspark.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class SparkAiChatGateway implements AiChatGateway {

    private static final Logger log = LoggerFactory.getLogger(SparkAiChatGateway.class);
    private static final String ADAPTER = "spark-openai-compatible-restclient";

    private final SparkAiProperties properties;
    private final RestClient restClient;

    public SparkAiChatGateway(SparkAiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.normalizedBaseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiPassword())
                .build();
    }

    @Override
    public AiHealthStatus health() {
        if (!properties.isAvailable()) {
            return new AiHealthStatus(false, properties.model(), ADAPTER, properties.unavailableReason());
        }
        return new AiHealthStatus(true, properties.model(), ADAPTER, "configured");
    }

    @Override
    public AiProbeResult probe(AiProbeRequest request) {
        ensureAvailable();
        SparkChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toSparkRequest(request))
                    .retrieve()
                    .body(SparkChatResponse.class);
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("spark ai probe timed out model={} adapter={}", properties.model(), ADAPTER);
                throw new BusinessException(ErrorCode.AI_PROVIDER_001, "AI 服务调用超时，请稍后重试");
            }
            log.warn("spark ai probe network error model={} adapter={} reason={}", properties.model(), ADAPTER,
                    ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_PROVIDER_001);
        } catch (RestClientException ex) {
            log.warn("spark ai probe client error model={} adapter={} reason={}", properties.model(), ADAPTER,
                    ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_PROVIDER_001);
        }
        if (response == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_001, "AI 服务返回为空");
        }
        validateProviderCode(response);
        String content = response.firstContent();
        return new AiProbeResult(content, response.usage() == null ? AiUsage.empty() : response.usage());
    }

    private void ensureAvailable() {
        if (properties.enabled() && properties.apiPassword().isBlank()) {
            throw new BusinessException(ErrorCode.AI_DISABLED_001, "AI 服务未配置密钥");
        }
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.AI_DISABLED_001, "AI 服务未启用");
        }
    }

    private SparkChatRequest toSparkRequest(AiProbeRequest request) {
        String system = request.system() == null || request.system().isBlank()
                ? "You are a PetSpark AI compatibility probe."
                : request.system();
        String user = request.user() == null ? "" : request.user();
        ResponseFormat responseFormat = request.jsonObject() ? new ResponseFormat("json_object") : null;
        return new SparkChatRequest(
                properties.model(),
                List.of(new SparkMessage("system", system), new SparkMessage("user", user)),
                0.4,
                1024,
                false,
                responseFormat);
    }

    private BusinessException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        log.warn("spark ai http error status={} model={} adapter={}", status, properties.model(), ADAPTER);
        if (status == 401 || status == 403) {
            return new BusinessException(ErrorCode.AI_PROVIDER_AUTH_001, "AI 供应商鉴权失败");
        }
        if (status == 429) {
            return new BusinessException(ErrorCode.AI_PROVIDER_LIMIT_001, "AI 供应商额度或频率限制");
        }
        return new BusinessException(ErrorCode.AI_PROVIDER_001);
    }

    private void validateProviderCode(SparkChatResponse response) {
        if (response.code() == null || response.code() == 0) {
            return;
        }
        int code = response.code();
        log.warn("spark ai business error providerCode={} model={} adapter={}", code, properties.model(), ADAPTER);
        if (code == 10007 || code == 10013 || code == 10014 || code == 10019) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_AUTH_001, "AI 供应商授权或模型权限异常");
        }
        if (code == 10907 || (code >= 11200 && code <= 11203)) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_LIMIT_001, "AI 供应商额度或频率限制");
        }
        throw new BusinessException(ErrorCode.AI_PROVIDER_001);
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private record SparkChatRequest(
            String model,
            List<SparkMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonProperty("response_format") ResponseFormat responseFormat) {
    }

    private record SparkMessage(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SparkChatResponse(Integer code, String message, List<SparkChoice> choices, AiUsage usage) {

        String firstContent() {
            if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
                return "";
            }
            return choices.get(0).message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SparkChoice(SparkMessage message) {
    }
}
