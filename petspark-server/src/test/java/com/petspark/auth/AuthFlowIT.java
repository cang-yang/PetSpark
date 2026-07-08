package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.common.error.ErrorCode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PR-AUTH-01 验收流：验证码、注册、登录、访问令牌与受保护接口认证。
 */
class AuthFlowIT extends AbstractControllerTest {

    private static final Pattern CHALLENGE = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM auth_captcha WHERE client_hash LIKE 'test-auth-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'auth_it_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'auth_it_%'");
    }

    @Test
    void registerThenLoginAndAccessProtectedEndpoint() throws Exception {
        Captcha registerCaptcha = issueCaptcha("test-auth-register");
        String username = "auth_it_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", "Str0ngPass!",
                                "nickname", "Cookie",
                                "captchaId", registerCaptcha.id(),
                                "captchaAnswer", registerCaptcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = ?", String.class, username);
        assertThat(storedHash).startsWith("$2");
        assertThat(storedHash).doesNotContain("Str0ngPass!");

        Captcha loginCaptcha = issueCaptcha("test-auth-login");
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", username,
                                "password", "Str0ngPass!",
                                "captchaId", loginCaptcha.id(),
                                "captchaAnswer", loginCaptcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("allowed"));
    }

    @Test
    void captchaIsConsumedOnlyOnce() throws Exception {
        Captcha captcha = issueCaptcha("test-auth-once");
        String username = "auth_it_" + System.nanoTime();
        Map<String, Object> payload = Map.of(
                "username", username,
                "email", username + "@example.com",
                "password", "Str0ngPass!",
                "nickname", "Once",
                "captchaId", captcha.id(),
                "captchaAnswer", captcha.answer());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CAPTCHA_001.code()));
    }

    @Test
    void malformedBearerTokenReturnsUnified401() throws Exception {
        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_TOKEN_001.code()));
    }

    @Test
    void duplicateUsernameAndWeakPasswordAreRejected() throws Exception {
        String username = "auth_it_" + System.nanoTime();
        Captcha first = issueCaptcha("test-auth-duplicate-1");
        register(username, first).andExpect(status().isOk());

        Captcha duplicate = issueCaptcha("test-auth-duplicate-2");
        register(username, duplicate)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_DUPLICATE_001.code()));

        Captcha weak = issueCaptcha("test-auth-weak");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username + "_weak",
                                "email", username + "_weak@example.com",
                                "password", "123456",
                                "nickname", "Weak",
                                "captchaId", weak.id(),
                                "captchaAnswer", weak.answer()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FIELD_001.code()));
    }

    @Test
    void badCredentialsAndDisabledUserAreRejectedUniformly() throws Exception {
        String username = "auth_it_" + System.nanoTime();
        register(username, issueCaptcha("test-auth-login-fail-register")).andExpect(status().isOk());

        Captcha wrongPassword = issueCaptcha("test-auth-wrong-password");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", username,
                                "password", "WrongPass1!",
                                "captchaId", wrongPassword.id(),
                                "captchaAnswer", wrongPassword.answer()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CREDENTIAL_001.code()));

        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE username = ?", username);
        Captcha disabled = issueCaptcha("test-auth-disabled");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", username,
                                "password", "Str0ngPass!",
                                "captchaId", disabled.id(),
                                "captchaAnswer", disabled.answer()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_ACCOUNT_001.code()));
    }

    private org.springframework.test.web.servlet.ResultActions register(String username, Captcha captcha) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", "Str0ngPass!",
                        "nickname", "Cookie",
                        "captchaId", captcha.id(),
                        "captchaAnswer", captcha.answer()))));
    }

    private Captcha issueCaptcha(String clientHash) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/captcha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientHash", clientHash))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.data.challengeText").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        String challenge = data.path("challengeText").asText();
        Matcher matcher = CHALLENGE.matcher(challenge);
        assertThat(matcher.find()).as("captcha challenge should be arithmetic").isTrue();
        return new Captcha(data.path("captchaId").asText(),
                String.valueOf(Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2))));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record Captcha(String id, String answer) {}
}
