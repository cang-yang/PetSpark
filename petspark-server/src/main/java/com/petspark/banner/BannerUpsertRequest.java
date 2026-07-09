package com.petspark.banner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record BannerUpsertRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 255) String subtitle,
        @NotBlank @Size(max = 500) String imageUrl,
        @Pattern(regexp = "GOODS|SERVICE|ADOPTION|COMMUNITY|EXTERNAL", message = "targetType 必须为 GOODS/SERVICE/ADOPTION/COMMUNITY/EXTERNAL") String targetType,
        @Size(max = 500) String targetUrl,
        @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE", message = "status 必须为 DRAFT/ACTIVE/INACTIVE") String status,
        @NotNull Integer sortOrder,
        Instant startsAt,
        Instant endsAt,
        @NotNull Integer version) {
}
