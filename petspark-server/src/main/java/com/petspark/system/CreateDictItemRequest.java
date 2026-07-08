package com.petspark.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDictItemRequest(
        @NotBlank @Size(max = 64) String itemKey,
        @NotBlank @Size(max = 128) String itemLabel,
        int sortOrder,
        @NotBlank @Size(max = 16) String status
) {}
