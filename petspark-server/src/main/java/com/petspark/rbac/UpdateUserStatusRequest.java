package com.petspark.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|DISABLED|LOCKED", message = "用户状态不合法")
        String status,
        @NotNull
        Integer version
) {}
