package com.petspark.catalog;

public record GoodsCategoryView(
        String id,
        String code,
        String name,
        String status,
        int sortOrder,
        int version) {
}
