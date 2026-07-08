package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private final CaptchaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public CaptchaService(
            CaptchaRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${petspark.auth.captcha.ttl-seconds:300}") long ttlSeconds) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public CaptchaResponse issue(CaptchaRequest request) {
        int left = random.nextInt(40) + 10;
        int right = random.nextInt(40) + 10;
        String challenge = left + " + " + right + " = ?";
        String id = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(ttl);
        repository.insert(
                id,
                sha256(challenge),
                passwordEncoder.encode(String.valueOf(left + right)),
                request.clientHash(),
                expiresAt);
        return new CaptchaResponse(id, challenge, expiresAt);
    }

    public void verify(String captchaId, String answer) {
        AuthCaptcha captcha = repository.findById(captchaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_CAPTCHA_001));
        if (captcha.consumedAt() != null || !captcha.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.AUTH_CAPTCHA_001);
        }
        if (captcha.attemptCount() >= 5 || !passwordEncoder.matches(answer, captcha.answerHash())) {
            repository.recordFailure(captcha.id());
            throw new BusinessException(ErrorCode.AUTH_CAPTCHA_001);
        }
        if (repository.consume(captcha.id()) != 1) {
            throw new BusinessException(ErrorCode.AUTH_CAPTCHA_001);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
