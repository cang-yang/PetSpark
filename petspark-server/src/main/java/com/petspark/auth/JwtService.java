package com.petspark.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;
    private final long expiresInSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${petspark.auth.jwt.secret:dev-only-change-me-petspark-auth-secret-32bytes}") String secret,
            @Value("${petspark.auth.jwt.expires-in-seconds:1800}") long expiresInSeconds) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresInSeconds = expiresInSeconds;
    }

    public IssuedToken issue(SysUser user, List<String> authorities) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(expiresInSeconds);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.id());
        payload.put("username", user.username());
        payload.put("nickname", user.nickname());
        payload.put("authorities", authorities);
        payload.put("tokenVersion", user.tokenVersion());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        String unsigned = base64Json(header) + "." + base64Json(payload);
        String token = unsigned + "." + base64Url(hmac(unsigned));
        return new IssuedToken(token, expiresInSeconds, expiresAt);
    }

    public AuthenticatedToken verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
            }
            String unsigned = parts[0] + "." + parts[1];
            String expected = base64Url(hmac(unsigned));
            if (!constantTimeEquals(expected, parts[2])) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.ofEpochSecond(exp).isBefore(clock.instant())) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
            }
            @SuppressWarnings("unchecked")
            List<String> authorities = (List<String>) payload.getOrDefault("authorities", List.of());
            return new AuthenticatedToken(
                    String.valueOf(payload.get("sub")),
                    String.valueOf(payload.get("username")),
                    authorities);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_001);
        }
    }

    private String base64Json(Object value) {
        try {
            return base64Url(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encode JWT", ex);
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign JWT", ex);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigestUtil.equals(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String value, long expiresInSeconds, Instant expiresAt) {}

    public record AuthenticatedToken(String userId, String username, List<String> authorities) {}

    private static final class MessageDigestUtil {
        private static boolean equals(byte[] a, byte[] b) {
            return java.security.MessageDigest.isEqual(a, b);
        }
    }
}
