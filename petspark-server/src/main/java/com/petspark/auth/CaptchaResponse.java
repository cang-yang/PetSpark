package com.petspark.auth;

import java.time.Instant;

public record CaptchaResponse(
        String captchaId,
        String challengeText,
        Instant expiresAt
) {}
