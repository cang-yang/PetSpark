package com.petspark.system;

public record SystemConfigView(
        String id,
        String configKey,
        String configValue,
        String valueType,
        String description,
        boolean protectedKey,
        int version
) {}
