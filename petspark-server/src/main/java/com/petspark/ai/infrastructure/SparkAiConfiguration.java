package com.petspark.ai.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SparkAiProperties.class)
public class SparkAiConfiguration {

    @Bean
    public AiChatGateway aiChatGateway(SparkAiProperties properties, RestClient.Builder restClientBuilder) {
        return new SparkAiChatGateway(properties, restClientBuilder);
    }
}
