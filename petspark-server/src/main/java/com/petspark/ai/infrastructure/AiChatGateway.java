package com.petspark.ai.infrastructure;

import java.util.List;
import java.util.function.Consumer;

public interface AiChatGateway {

    AiHealthStatus health();

    AiProbeResult probe(AiProbeRequest request);

    AiChatResult chat(AiChatRequest request);

    AiChatResult stream(AiChatRequest request, Consumer<String> onDelta);
}
