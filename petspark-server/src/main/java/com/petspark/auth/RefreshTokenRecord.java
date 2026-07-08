package com.petspark.auth;

import java.time.Instant;

record RefreshTokenRecord(
        String id,
        String userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt,
        String replacedById
) {}
