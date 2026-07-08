package com.petspark.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDictTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 64) String name
) {}
