package com.petspark.catalog;

import java.math.BigDecimal;
import java.time.Instant;

public record GoodsView(
        String id,
        String categoryId,
        String categoryName,
        String sku,
        String name,
        String description,
        String coverFileId,
        String coverUrl,
        BigDecimal price,
        int stock,
        String status,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
