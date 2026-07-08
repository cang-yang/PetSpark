package com.petspark.rbac;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class RoleController {

    private final RbacService service;

    public RoleController(RbacService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    @RequirePermission("role:read")
    public ApiResponse<List<RoleView>> roles() {
        return ApiResponse.ok(service.listRoles());
    }

    @PostMapping("/roles")
    @RequirePermission("role:update")
    public ApiResponse<RoleView> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(service.createRole(request));
    }

    @PutMapping("/roles/{code}/permissions")
    @RequirePermission("role:update")
    public ApiResponse<RoleView> updateRolePermissions(
            @PathVariable String code,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.ok(service.updateRolePermissions(code, request));
    }

    @GetMapping("/permissions")
    @RequirePermission("role:read")
    public ApiResponse<List<PermissionView>> permissions() {
        return ApiResponse.ok(service.listPermissions());
    }
}
