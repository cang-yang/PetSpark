package com.petspark.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDictItemRequest(
        @NotBlank @Size(max = 128) String itemLabel,
        int sortOrder,
        @NotBlank @Size(max = 16) String status,
        @NotNull Integer version
) {}
