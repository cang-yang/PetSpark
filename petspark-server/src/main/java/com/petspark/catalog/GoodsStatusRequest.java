package com.petspark.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GoodsStatusRequest(
        @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status,
        @Min(0) int version) {
}
