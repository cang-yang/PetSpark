package com.petspark.banner;

import jakarta.validation.constraints.NotNull;

public record BannerOrderRequest(@NotNull Integer sortOrder, @NotNull Integer version) {
}
