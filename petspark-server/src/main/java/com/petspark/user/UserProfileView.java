package com.petspark.user;

import java.time.Instant;

public record UserProfileView(
        String id,
        String username,
        String email,
        String nickname,
        String avatarFileId,
        String avatarUrl,
        String phoneMasked,
        String bio,
        int version,
        Instant updatedAt
) {}
