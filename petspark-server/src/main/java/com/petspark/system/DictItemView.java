package com.petspark.system;

public record DictItemView(
        String id,
        String typeCode,
        String itemKey,
        String itemLabel,
        int sortOrder,
        String status,
        int version
) {}
