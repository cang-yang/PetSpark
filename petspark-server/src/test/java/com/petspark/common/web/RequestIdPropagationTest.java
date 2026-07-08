package com.petspark.common.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petspark.AbstractControllerTest;
import org.junit.jupiter.api.Test;

/**
 * 验证请求 ID 传播契约（PR-BASE-02）：客户端未带 {@code X-Request-Id} 时服务端生成
 * 并回写响应头；客户端带可接受值时原样回写；非法字符被忽略并新生成。错误响应
 * 也透传同一 requestId，便于跨日志/响应关联。
 */
class RequestIdPropagationTest extends AbstractControllerTest {

    @Test
    void generatesRequestIdWhenAbsent() throws Exception {
        mockMvc.perform(get("/test/contract/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void echoesClientRequestIdWhenAcceptable() throws Exception {
        String provided = "client-abc-123";
        mockMvc.perform(get("/test/contract/ok").header("X-Request-Id", provided))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", provided))
                .andExpect(jsonPath("$.requestId").value(provided));
    }

    @Test
    void ignoresInvalidRequestIdAndGeneratesNew() throws Exception {
        // 含空格和分号，违反可接受字符集，应被忽略重新生成。
        String invalid = "bad id;injection";
        mockMvc.perform(get("/test/contract/ok").header("X-Request-Id", invalid))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").value(not(invalid)));
    }

    @Test
    void propagatesRequestIdToErrorEnvelope() throws Exception {
        String provided = "err-trace-001";
        mockMvc.perform(get("/test/contract/business-409").header("X-Request-Id", provided))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", provided))
                .andExpect(jsonPath("$.requestId").value(provided));
    }
}