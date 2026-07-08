package com.petspark.rbac;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRolePermissionsRequest(@NotNull List<String> permissionCodes) {}
