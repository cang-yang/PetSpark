package com.petspark.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaptchaRequest(
        @NotBlank @Size(max = 128) String clientHash
) {}
