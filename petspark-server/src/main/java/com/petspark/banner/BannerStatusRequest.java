package com.petspark.banner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BannerStatusRequest(
        @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE", message = "status 必须为 DRAFT/ACTIVE/INACTIVE") String status,
        @NotNull Integer version) {
}
