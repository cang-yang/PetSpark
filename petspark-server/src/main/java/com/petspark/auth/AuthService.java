package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CaptchaService captchaService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RegistrationService registrationService;

    public AuthService(
            CaptchaService captchaService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            RegistrationService registrationService) {
        this.captchaService = captchaService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.registrationService = registrationService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        passwordPolicy.validate(request.password());
        String email = registrationService.verifyAndConsume(request.email(), request.emailCode());
        if (userRepository.existsByUsernameOrEmail(request.username(), email)) {
            throw new BusinessException(ErrorCode.USER_DUPLICATE_001);
        }
        SysUser user = new SysUser(
                UUID.randomUUID().toString(),
                request.username(),
                email,
                passwordEncoder.encode(request.password()),
                request.nickname(),
                "ACTIVE",
                0);
        userRepository.insert(user);
        return new RegisterResponse(user.id(), user.username(), user.nickname());
    }

    @Transactional
    public AuthLoginResult login(LoginRequest request) {
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        SysUser user = userRepository.findByPrincipal(request.principal())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_CREDENTIAL_001));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_CREDENTIAL_001);
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_001);
        }
        userRepository.markLogin(user.id());
        return issueSession(user, refreshTokenService.issue(user.id(), null).rawToken());
    }

    @Transactional
    public AuthLoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        return issueSession(rotated.user(), rotated.rawToken());
    }

    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        String sessionUserId = refreshTokenService.revokeFamily(rawRefreshToken, userId);
        userRepository.incrementTokenVersion(sessionUserId);
    }

    private AuthLoginResult issueSession(SysUser user, String rawRefreshToken) {
        List<String> authorities = userRepository.findAuthorities(user.id());
        JwtService.IssuedToken token = jwtService.issue(user, authorities);
        Instant expiresAt = token.expiresAt();
        LoginResponse response = new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                expiresAt,
                new UserSummary(user.id(), user.username(), user.nickname()));
        return new AuthLoginResult(response, rawRefreshToken);
    }
}
