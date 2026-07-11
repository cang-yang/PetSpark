package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final CaptchaService captchaService;
    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final PasswordResetNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final long ttlSeconds;
    private final Duration resendCooldown;

    public PasswordResetService(CaptchaService captchaService, UserRepository userRepository,
            VerificationCodeRepository codeRepository, PasswordResetNotifier notifier,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            RefreshTokenService refreshTokenService, Clock clock,
            @Value("${petspark.auth.password-reset.ttl-seconds:600}") long ttlSeconds,
            @Value("${petspark.auth.password-reset.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.captchaService = captchaService;
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
        this.resendCooldown = Duration.ofSeconds(Math.max(1, resendCooldownSeconds));
    }

    public void requestCode(PasswordResetCodeRequest request) {
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        if (!notifier.isAvailable()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_001, "密码重置邮件服务暂不可用");
        }
        String email = AuthEmail.normalize(request.email());
        if (codeRepository.issuedSince("PASSWORD_RESET", email, clock.instant().minus(resendCooldown))) {
            return;
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            String code = "%06d".formatted(random.nextInt(1_000_000));
            codeRepository.insert(UUID.randomUUID().toString(), email, passwordEncoder.encode(code),
                    clock.instant().plusSeconds(ttlSeconds));
            try {
                notifier.sendCode(email, code);
            } catch (RuntimeException ex) {
                // The public response remains indistinguishable from an unknown account.
                log.warn("Password reset delivery failed; request accepted without exposing account existence");
            }
        });
    }

    @Transactional
    public void reset(PasswordResetRequest request) {
        passwordPolicy.validate(request.newPassword());
        VerificationCodeRecord code = codeRepository.findLatestPasswordReset(request.email())
                .orElseThrow(this::invalidCode);
        if (code.consumedAt() != null || !code.expiresAt().isAfter(clock.instant()) || code.attemptCount() >= 5) {
            throw invalidCode();
        }
        if (!passwordEncoder.matches(request.code(), code.codeHash())) {
            codeRepository.recordFailure(code.id());
            throw invalidCode();
        }
        if (codeRepository.consume(code.id()) != 1) {
            throw invalidCode();
        }
        SysUser user = userRepository.findByEmail(request.email()).orElseThrow(this::invalidCode);
        userRepository.updatePasswordAndIncrementTokenVersion(
                user.id(), passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.id());
    }

    private BusinessException invalidCode() {
        return new BusinessException(ErrorCode.AUTH_CODE_001);
    }
}
