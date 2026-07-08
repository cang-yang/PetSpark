package com.petspark.ai.infrastructure;

import com.petspark.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiHealthController {

    private final AiChatGateway aiChatGateway;

    public AiHealthController(AiChatGateway aiChatGateway) {
        this.aiChatGateway = aiChatGateway;
    }

    @GetMapping("/health")
    public ApiResponse<AiHealthStatus> health() {
        return ApiResponse.ok(aiChatGateway.health());
    }
}
