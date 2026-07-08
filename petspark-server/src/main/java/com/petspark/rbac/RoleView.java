package com.petspark.rbac;

import java.util.List;

public record RoleView(
        String id,
        String code,
        String name,
        boolean builtIn,
        String status,
        List<String> permissionCodes
) {}
