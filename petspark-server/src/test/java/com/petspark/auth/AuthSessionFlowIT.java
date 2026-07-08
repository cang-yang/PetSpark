package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.common.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.BDDMockito.given;

class AuthSessionFlowIT extends AbstractControllerTest {

    private static final Pattern CHALLENGE = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)");
    private static final String REFRESH_COOKIE = "petspark_refresh";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordResetNotifier passwordResetNotifier;

    @BeforeEach
    void configurePasswordResetNotifier() {
        given(passwordResetNotifier.isAvailable()).willReturn(true);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM auth_verification_code WHERE principal LIKE 'session_it_%@example.com'");
        jdbcTemplate.update("DELETE FROM auth_refresh_token WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'session_it_%')");
        jdbcTemplate.update("DELETE FROM auth_captcha WHERE client_hash LIKE 'session-auth-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'session_it_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'session_it_%'");
    }

    @Test
    void loginIssuesSecureRefreshCookieAndRefreshRotatesIt() throws Exception {
        Credentials credentials = registerUser();
        LoginResult login = login(credentials);

        assertThat(login.refreshCookie().isHttpOnly()).isTrue();
        assertThat(login.refreshCookie().getSecure()).isTrue();
        assertThat(login.refreshCookie().getPath()).isEqualTo("/api/v1/auth");
        assertThat(login.refreshCookie().getMaxAge()).isPositive();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = refreshResult.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(login.refreshCookie().getValue());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REFRESH_001.code()));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REFRESH_001.code()));
    }

    @Test
    void logoutRevokesRefreshFamilyAndCurrentAccessToken() throws Exception {
        Credentials credentials = registerUser();
        LoginResult login = login(credentials);

        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REFRESH_001.code()));

        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_TOKEN_001.code()));
    }

    @Test
    void logoutCanRevokeSessionUsingRefreshCookieAfterAccessTokenExpires() throws Exception {
        LoginResult login = login(registerUser());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(login.refreshCookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REFRESH_001.code()));
    }

    @Test
    void logoutRejectsRefreshTokenOwnedByAnotherUser() throws Exception {
        LoginResult first = login(registerUser());
        LoginResult second = login(registerUser());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + first.accessToken())
                        .cookie(second.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REFRESH_001.code()));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(second.refreshCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetCodeRequestIsUniformAndStoresOnlyAHash() throws Exception {
        Credentials credentials = registerUser();
        Captcha captcha = issueCaptcha("session-auth-reset-code");

        mockMvc.perform(post("/api/v1/auth/password-reset-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", credentials.email(),
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT code_hash, consumed_at, expires_at
                FROM auth_verification_code
                WHERE principal = ? AND purpose = 'PASSWORD_RESET'
                ORDER BY created_at DESC
                LIMIT 1
                """, credentials.email());
        assertThat(stored.get("code_hash").toString()).startsWith("$2");
        assertThat(stored.get("consumed_at")).isNull();

        Captcha unknownCaptcha = issueCaptcha("session-auth-reset-code-unknown");
        mockMvc.perform(post("/api/v1/auth/password-reset-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "session_it_missing@example.com",
                                "captchaId", unknownCaptcha.id(),
                                "captchaAnswer", unknownCaptcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void passwordResetCodeRequestDoesNotLeakMailDeliveryFailure() throws Exception {
        Credentials credentials = registerUser();
        Captcha captcha = issueCaptcha("session-auth-reset-mail-failure");
        doThrow(new IllegalStateException("mail unavailable"))
                .when(passwordResetNotifier).sendCode(eq(credentials.email()), org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", credentials.email(),
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void passwordResetConsumesCodeAndInvalidatesExistingCredentials() throws Exception {
        Credentials credentials = registerUser();
        LoginResult oldSession = login(credentials);
        Captcha captcha = issueCaptcha("session-auth-reset-complete");

        mockMvc.perform(post("/api/v1/auth/password-reset-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", credentials.email(),
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetNotifier).sendCode(eq(credentials.email()), codeCaptor.capture());
        String code = codeCaptor.getValue();
        assertThat(code).matches("\\d{6}");

        String newPassword = "N3wStrongPass!";
        Map<String, String> resetPayload = Map.of(
                "email", credentials.email(),
                "code", code,
                "newPassword", newPassword);
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(resetPayload)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(resetPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CODE_001"));

        mockMvc.perform(get("/api/test/contract/require-permission")
                        .header("Authorization", "Bearer " + oldSession.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldSession.refreshCookie()))
                .andExpect(status().isUnauthorized());

        Captcha oldPasswordCaptcha = issueCaptcha("session-auth-old-password");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", credentials.username(),
                                "password", credentials.password(),
                                "captchaId", oldPasswordCaptcha.id(),
                                "captchaAnswer", oldPasswordCaptcha.answer()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_CREDENTIAL_001.code()));

        login(new Credentials(credentials.username(), credentials.email(), newPassword));
    }

    private Credentials registerUser() throws Exception {
        String username = "session_it_" + System.nanoTime();
        String email = username + "@example.com";
        Captcha captcha = issueCaptcha("session-auth-register-" + username);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "password", "Str0ngPass!",
                                "nickname", "Session",
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk());
        return new Credentials(username, email, "Str0ngPass!");
    }

    private LoginResult login(Credentials credentials) throws Exception {
        Captcha captcha = issueCaptcha("session-auth-login-" + credentials.username());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "principal", credentials.username(),
                                "password", credentials.password(),
                                "captchaId", captcha.id(),
                                "captchaAnswer", captcha.answer()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Cookie refreshCookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refreshCookie).isNotNull();
        return new LoginResult(data.path("accessToken").asText(), refreshCookie);
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
    private record Credentials(String username, String email, String password) {}
    private record LoginResult(String accessToken, Cookie refreshCookie) {}
}
