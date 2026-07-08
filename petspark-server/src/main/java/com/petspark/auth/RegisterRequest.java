package com.petspark.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 64) String nickname,
        @NotBlank String captchaId,
        @NotBlank String captchaAnswer
) {}
