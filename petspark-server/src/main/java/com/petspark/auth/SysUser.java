package com.petspark.auth;

public record SysUser(
        String id,
        String username,
        String email,
        String passwordHash,
        String nickname,
        String status,
        int tokenVersion
) {}
