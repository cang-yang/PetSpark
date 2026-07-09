package com.petspark.banner;

import java.time.Instant;

public record BannerView(
        String id,
        String title,
        String subtitle,
        String imageUrl,
        String targetType,
        String targetUrl,
        String status,
        int sortOrder,
        Instant startsAt,
        Instant endsAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
