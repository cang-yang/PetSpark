package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** PR-AUTH-03 验收流：保留用户名/邮箱账号模型，并以真实邮件验证码确认注册。 */
class AuthFlowIT extends AbstractControllerTest {

    private static final Pattern CHALLENGE = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RegistrationEmailNotifier registrationEmailNotifier;

    @BeforeEach
    void enableRegistrationMail() {
        given(registrationEmailNotifier.isAvailable()).willReturn(true);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM auth_verification_code WHERE principal LIKE 'auth_it_%@example.com'");
        jdbcTemplate.update("DELETE FROM auth_captcha WHERE client_hash LIKE 'test-auth-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE email LIKE 'auth_it_%@example.com')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE email LIKE 'auth_it_%@example.com'");
    }

    @Test
    void registerNormalizesEmailThenLoginAndAccessProtectedEndpoint() throws Exception {
        String email = "auth_it_" + System.nanoTime() + "@example.com";
        String username = "auth_user_" + System.nanoTime();
        String code = requestRegistrationCode(email.toUpperCase(), "test-auth-register");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email.toUpperCase(),
                                "emailCode", code,
                                "password", "Str0ngPass!",
                                "nickname", "Cookie"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT username, email, password_hash FROM sys_user WHERE email = ?", email);
        assertThat(stored.get("username").toString()).isEqualTo(username);
        assertThat(stored.get("password_hash").toString()).startsWith("$2").doesNotContain("Str0ngPass!");

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
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Captcha emailLoginCaptcha = issueCaptcha("test-auth-email-login");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", email.toUpperCase(),
                                "password", "Str0ngPass!",
                                "captchaId", emailLoginCaptcha.id(),
                                "captchaAnswer", emailLoginCaptcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void registrationCodeIsHashedAndCanOnlyBeConsumedOnce() throws Exception {
        String email = "auth_it_" + System.nanoTime() + "@example.com";
        String code = requestRegistrationCode(email, "test-auth-code-once");
        String hash = jdbcTemplate.queryForObject("""
                SELECT code_hash FROM auth_verification_code
                WHERE purpose = 'REGISTRATION' AND principal = ?
                ORDER BY created_at DESC LIMIT 1
                """, String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain(code);

        register(email, code, "Str0ngPass!").andExpect(status().isOk());
        register(email, code, "Str0ngPass!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CODE_001.code()));
    }

    @Test
    void arithmeticCaptchaForSendingMailCanOnlyBeConsumedOnce() throws Exception {
        String email = "auth_it_" + System.nanoTime() + "@example.com";
        Captcha captcha = issueCaptcha("test-auth-captcha-once");
        Map<String, Object> payload = Map.of(
                "email", email,
                "captchaId", captcha.id(),
                "captchaAnswer", captcha.answer());

        mockMvc.perform(post("/api/v1/auth/registration-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/registration-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CAPTCHA_001.code()));
    }

    @Test
    void duplicateUsernameIsRejectedAfterBothEmailsAreVerified() throws Exception {
        String username = "auth_user_" + System.nanoTime();
        String firstEmail = "auth_it_" + System.nanoTime() + "@example.com";
        String firstCode = requestRegistrationCode(firstEmail, "test-auth-duplicate-first");
        register(username, firstEmail, firstCode, "Str0ngPass!").andExpect(status().isOk());

        String secondEmail = "auth_it_" + System.nanoTime() + "@example.com";
        String secondCode = requestRegistrationCode(secondEmail, "test-auth-duplicate-second");
        register(username, secondEmail, secondCode, "Str0ngPass!")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_DUPLICATE_001.code()));
    }

    @Test
    void badCredentialsAndDisabledUserRemainDistinctAccountFailures() throws Exception {
        String email = "auth_it_" + System.nanoTime() + "@example.com";
        String username = email.substring(0, email.indexOf('@'));
        String code = requestRegistrationCode(email, "test-auth-login-failure-register");
        register(username, email, code, "Str0ngPass!").andExpect(status().isOk());

        Captcha wrongPassword = issueCaptcha("test-auth-wrong-password");
        login(username, "WrongPass1!", wrongPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CREDENTIAL_001.code()));

        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE username = ?", username);
        Captcha disabled = issueCaptcha("test-auth-disabled");
        login(username, "Str0ngPass!", disabled)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_ACCOUNT_001.code()));
    }

    @Test
    void unavailableRegistrationMailReturns503() throws Exception {
        given(registrationEmailNotifier.isAvailable()).willReturn(false);
        Captcha captcha = issueCaptcha("test-auth-mail-unavailable");
        mockMvc.perform(post("/api/v1/auth/registration-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "auth_it_unavailable@example.com",
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_SERVICE_001.code()));
    }

    @Test
    void repeatedRegistrationEmailRequestIsRateLimited() throws Exception {
        String email = "auth_it_" + System.nanoTime() + "@example.com";
        requestRegistrationCode(email, "test-auth-rate-first");
        Captcha captcha = issueCaptcha("test-auth-rate-second");
        mockMvc.perform(post("/api/v1/auth/registration-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EMAIL_001.code()));
    }

    @Test
    void weakPasswordAndWrongEmailCodeAreRejected() throws Exception {
        String wrongEmail = "auth_it_" + System.nanoTime() + "@example.com";
        requestRegistrationCode(wrongEmail, "test-auth-wrong-code");
        register(wrongEmail, "000000", "Str0ngPass!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CODE_001.code()));

        String weakEmail = "auth_it_" + System.nanoTime() + "@example.com";
        String code = requestRegistrationCode(weakEmail, "test-auth-weak");
        register(weakEmail, code, "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FIELD_001.code()));
    }

    @Test
    void malformedBearerTokenReturnsUnified401() throws Exception {
        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_TOKEN_001.code()));
    }

    private String requestRegistrationCode(String emailValue, String clientHash) throws Exception {
        String email = emailValue.toLowerCase();
        Captcha captcha = issueCaptcha(clientHash);
        mockMvc.perform(post("/api/v1/auth/registration-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", emailValue,
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registrationEmailNotifier).sendCode(eq(email), captor.capture());
        assertThat(captor.getValue()).matches("\\d{6}");
        return captor.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email, String code, String password) throws Exception {
        String username = email.substring(0, email.indexOf('@'));
        return register(username, email, code, password);
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String username, String email, String code, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "username", username,
                        "email", email,
                        "emailCode", code,
                        "password", password,
                        "nickname", "测试用户"))));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String principal, String password, Captcha captcha) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "principal", principal,
                        "password", password,
                        "captchaId", captcha.id(),
                        "captchaAnswer", captcha.answer()))));
    }

    private Captcha issueCaptcha(String clientHash) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/captcha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("clientHash", clientHash))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        Matcher matcher = CHALLENGE.matcher(data.path("challengeText").asText());
        assertThat(matcher.find()).isTrue();
        return new Captcha(data.path("captchaId").asText(),
                String.valueOf(Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2))));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record Captcha(String id, String answer) {}
}
