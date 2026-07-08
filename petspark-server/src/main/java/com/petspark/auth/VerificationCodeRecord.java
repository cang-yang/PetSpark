package com.petspark.auth;

import java.time.Instant;

record VerificationCodeRecord(
        String id,
        String codeHash,
        Instant expiresAt,
        Instant consumedAt,
        int attemptCount
) {}
