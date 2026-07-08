package com.petspark.rbac;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignUserRolesRequest(@NotNull List<String> roleCodes) {}
