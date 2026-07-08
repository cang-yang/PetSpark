package com.petspark.auth;

public record UserSummary(
        String userId,
        String username,
        String nickname
) {}
