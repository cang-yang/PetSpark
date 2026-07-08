package com.petspark.system;

import java.time.Instant;

public record AuditLogView(
        String id,
        String requestId,
        String actorId,
        String actorRole,
        String module,
        String action,
        String objectType,
        String objectId,
        String result,
        String reasonCode,
        String ipHash,
        Instant createdAt
) {}
