package com.petspark.auth;

public record RegisterResponse(
        String userId,
        String username,
        String nickname
) {}
