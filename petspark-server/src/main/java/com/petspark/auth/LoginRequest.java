package com.petspark.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 128) String principal,
        @NotBlank @Size(max = 128) String password,
        @NotBlank String captchaId,
        @NotBlank String captchaAnswer
) {}
