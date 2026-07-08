package com.petspark.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GoodsUpsertRequest(
        @NotBlank String categoryId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{1,63}") String sku,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        String coverFileId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @Min(0) int stock,
        @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status,
        @Min(0) int version) {
}
