package com.petspark.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoodsCategoryRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{1,63}") String code,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        int sortOrder,
        @Min(0) int version) {
}
