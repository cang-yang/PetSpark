package com.petspark.ai.infrastructure;

public interface AiChatGateway {

    AiHealthStatus health();

    AiProbeResult probe(AiProbeRequest request);
}
