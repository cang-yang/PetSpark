package com.petspark.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationCodeRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank String captchaId,
        @NotBlank String captchaAnswer
) {}
