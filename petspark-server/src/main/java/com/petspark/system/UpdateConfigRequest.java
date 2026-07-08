package com.petspark.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateConfigRequest(
        @NotBlank @Size(max = 512) String configValue,
        @NotBlank @Size(max = 16) String valueType,
        @Size(max = 255) String description,
        @NotNull Integer version
) {}
