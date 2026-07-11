package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    static final String PURPOSE = "REGISTRATION";
    private static final int MAX_ATTEMPTS = 5;

    private final CaptchaService captchaService;
    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final RegistrationEmailNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final Duration resendCooldown;

    public RegistrationService(
            CaptchaService captchaService,
            UserRepository userRepository,
            VerificationCodeRepository codeRepository,
            RegistrationEmailNotifier notifier,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${petspark.auth.registration.ttl-seconds:600}") long ttlSeconds,
            @Value("${petspark.auth.registration.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.captchaService = captchaService;
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
        this.resendCooldown = Duration.ofSeconds(Math.max(1, resendCooldownSeconds));
    }

    @Transactional
    public void requestCode(RegistrationCodeRequest request) {
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        String email = AuthEmail.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_DUPLICATE_001, "该邮箱已注册");
        }
        if (!notifier.isAvailable()) {
            throw new BusinessException(ErrorCode.EMAIL_SERVICE_001, "注册邮件服务未配置");
        }
        if (codeRepository.issuedSince(PURPOSE, email, clock.instant().minus(resendCooldown))) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EMAIL_001);
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        codeRepository.insert(UUID.randomUUID().toString(), PURPOSE, email,
                passwordEncoder.encode(code), clock.instant().plus(ttl));
        try {
            notifier.sendCode(email, code);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.EMAIL_SERVICE_001, "注册验证码发送失败，请稍后重试");
        }
    }

    public String verifyAndConsume(String emailValue, String codeValue) {
        String email = AuthEmail.normalize(emailValue);
        VerificationCodeRecord code = codeRepository.findLatest(PURPOSE, email)
                .orElseThrow(this::invalidCode);
        if (code.consumedAt() != null
                || !code.expiresAt().isAfter(clock.instant())
                || code.attemptCount() >= MAX_ATTEMPTS) {
            throw invalidCode();
        }
        if (!passwordEncoder.matches(codeValue, code.codeHash())) {
            codeRepository.recordFailure(code.id());
            throw invalidCode();
        }
        if (codeRepository.consume(code.id()) != 1) {
            throw invalidCode();
        }
        return email;
    }

    private BusinessException invalidCode() {
        return new BusinessException(ErrorCode.AUTH_CODE_001);
    }
}
