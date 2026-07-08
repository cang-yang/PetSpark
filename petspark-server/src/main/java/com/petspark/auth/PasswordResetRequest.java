package com.petspark.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 16) String code,
        @NotBlank @Size(max = 128) String newPassword
) {}
