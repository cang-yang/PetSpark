package com.petspark.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetCodeRequest(
        @NotBlank @Email String email,
        @NotBlank String captchaId,
        @NotBlank String captchaAnswer
) {}
