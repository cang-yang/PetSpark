package com.petspark.auth;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final AuthCookieService cookieService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, CaptchaService captchaService,
            AuthCookieService cookieService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.cookieService = cookieService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha(@Valid @RequestBody CaptchaRequest request) {
        return ApiResponse.ok(captchaService.issue(request));
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthLoginResult result = authService.login(request);
        setRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(result.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthLoginResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(result.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
        }
        authService.logout(user.getId(), refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearRefreshCookie().toString());
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset-codes")
    public ApiResponse<Void> requestPasswordResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        passwordResetService.requestCode(request);
        return ApiResponse.ok();
    }

    @PostMapping("/password-resets")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(request);
        return ApiResponse.ok();
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.refreshCookie(rawToken).toString());
    }
}
