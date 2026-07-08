package com.petspark.ai.infrastructure;

import java.util.List;

public interface AiChatGateway {

    AiHealthStatus health();

    AiProbeResult probe(AiProbeRequest request);

    AiChatResult chat(AiChatRequest request);
}
