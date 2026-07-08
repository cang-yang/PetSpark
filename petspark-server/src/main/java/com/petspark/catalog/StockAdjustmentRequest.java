package com.petspark.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        int delta,
        @NotBlank @Size(max = 255) String reason,
        @Min(0) int version) {
}
