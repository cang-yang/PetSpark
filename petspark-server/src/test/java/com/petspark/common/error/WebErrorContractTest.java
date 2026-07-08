package com.petspark.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petspark.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 验证 Web 错误契约稳定性（PR-BASE-02 验收标准）：400/401/403/404/409/422/429/503/500
 * 一律以 {@code ErrorResponse{code,message,details,requestId,timestamp}} 返回，
 * 不泄露堆栈，且响应头带 {@code X-Request-Id}。
 *
 * <p>{@link ErrorContractTestController} 与主应用同包，被组件扫描自动注册，无需 @Import。
 */
class WebErrorContractTest extends AbstractControllerTest {

    @Test
    void okReturnsSuccessEnvelope() throws Exception {
        mockMvc.perform(get("/test/contract/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value("hello"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void pageReturnsPageEnvelope() throws Exception {
        mockMvc.perform(get("/test/contract/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0]").value("a"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void validationFailureReturns400() throws Exception {
        mockMvc.perform(get("/test/contract/validation").param("value", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FIELD_001.code()))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void bodyValidationFailureReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/test/contract/body-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FIELD_001.code()))
                .andExpect(jsonPath("$.details[0].field").exists());
    }

    @Test
    void notFoundBusinessExceptionReturns404() throws Exception {
        mockMvc.perform(get("/test/contract/not-found-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND_001.code()));
    }

    @Test
    void businessConflictReturns409() throws Exception {
        mockMvc.perform(get("/test/contract/business-409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_STOCK_001.code()));
    }

    @Test
    void businessRuleReturns422() throws Exception {
        mockMvc.perform(get("/test/contract/business-422"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.BUSINESS_RULE_001.code()));
    }

    @Test
    void rateLimitReturns429() throws Exception {
        mockMvc.perform(get("/test/contract/business-429"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_001.code()));
    }

    @Test
    void aiProviderReturns503() throws Exception {
        mockMvc.perform(get("/test/contract/business-503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.AI_PROVIDER_001.code()));
    }

    @Test
    void accessDeniedReturns403() throws Exception {
        mockMvc.perform(get("/test/contract/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED_001.code()));
    }

    @Test
    void protectedEndpointWithoutAuthReturns401() throws Exception {
        // 落在 /api/** 认证段：匿名访问由 SecurityFilterChain 拦截返回 401。
        mockMvc.perform(get("/api/test/contract/require-permission"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_TOKEN_001.code()));
    }

    @Test
    void uncaughtExceptionReturns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/contract/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR_001.code()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void illegalArgumentReturns400() throws Exception {
        mockMvc.perform(get("/test/contract/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }
}