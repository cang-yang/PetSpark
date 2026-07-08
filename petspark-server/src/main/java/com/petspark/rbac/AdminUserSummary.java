package com.petspark.rbac;

import java.time.Instant;
import java.util.List;

public record AdminUserSummary(
        String id,
        String username,
        String email,
        String nickname,
        String status,
        List<String> roleCodes,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
