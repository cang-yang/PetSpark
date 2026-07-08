package com.petspark.rbac;

public record PermissionView(String code, String resource, String action, String description) {}
