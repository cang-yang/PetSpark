package com.petspark.user;

import java.time.Instant;

public record UserProfile(
        String id,
        String username,
        String email,
        String nickname,
        String avatarFileId,
        String phoneCiphertext,
        String bio,
        int version,
        Instant updatedAt
) {}
