package com.petspark.auth;

import java.time.Instant;

public record AuthCaptcha(
        String id,
        String answerHash,
        Instant expiresAt,
        Instant consumedAt,
        int attemptCount
) {}
