package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final RefreshTokenFamilyRevoker familyRevoker;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final long ttlSeconds;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
            RefreshTokenFamilyRevoker familyRevoker, Clock clock,
            @Value("${petspark.auth.refresh.expires-in-seconds:604800}") long ttlSeconds) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.familyRevoker = familyRevoker;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedRefreshToken issue(String userId, String clientFingerprint) {
        return issue(userId, UUID.randomUUID().toString(), clientFingerprint);
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        RefreshTokenRecord current = find(rawToken);
        if (current.revokedAt() != null) {
            if (current.replacedById() != null) {
                familyRevoker.revokeInIndependentTransaction(current.familyId());
            }
            throw invalid();
        }
        if (!current.expiresAt().isAfter(clock.instant())) {
            familyRevoker.revokeInIndependentTransaction(current.familyId());
            throw invalid();
        }
        SysUser user = userRepository.findById(current.userId())
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .orElseThrow(this::invalid);
        IssuedRefreshToken next = issue(user.id(), current.familyId(), null);
        if (repository.replace(current.id(), next.id()) != 1) {
            familyRevoker.revokeInIndependentTransaction(current.familyId());
            throw invalid();
        }
        return new RotatedRefreshToken(user, next.rawToken());
    }

    @Transactional
    public String revokeFamily(String rawToken, String expectedUserId) {
        RefreshTokenRecord current = find(rawToken);
        if (expectedUserId != null && !current.userId().equals(expectedUserId)) {
            throw invalid();
        }
        repository.revokeFamily(current.familyId());
        return current.userId();
    }

    public void revokeAllForUser(String userId) {
        repository.revokeAllForUser(userId);
    }

    private IssuedRefreshToken issue(String userId, String familyId, String clientFingerprint) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        repository.insert(id, userId, sha256(raw), familyId, clock.instant().plusSeconds(ttlSeconds), clientFingerprint);
        return new IssuedRefreshToken(id, raw);
    }

    private RefreshTokenRecord find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        return repository.findByHash(sha256(rawToken)).orElseThrow(this::invalid);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.AUTH_REFRESH_001);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record IssuedRefreshToken(String id, String rawToken) {}
    public record RotatedRefreshToken(SysUser user, String rawToken) {}
}
