package com.petspark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller 层测试基类。复用 {@link AbstractIntegrationTest} 的本机 MySQL 连接，
 * 额外注入 {@link MockMvc} 与 {@link ObjectMapper} 用于驱动 HTTP 契约断言。
 *
 * <p>用 {@code @AutoConfigureMockMvc} 而非 {@code @WebMvcTest}：本类目标是验证
 * 全栈错误契约（过滤链 + 异常处理器 + Security 出口 + JSON 序列化），切片测试会
 * 漏掉 SecurityFilterChain 与 RequestIdFilter 的协作。
 */
@AutoConfigureMockMvc
public abstract class AbstractControllerTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}