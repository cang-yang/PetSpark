package com.petspark.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "角色编码必须为大写字母、数字或下划线")
        String code,
        @NotBlank
        @Size(max = 64)
        String name,
        List<String> permissionCodes
) {}
