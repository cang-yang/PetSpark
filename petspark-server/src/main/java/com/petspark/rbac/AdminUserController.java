package com.petspark.rbac;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final RbacService service;

    public AdminUserController(RbacService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("user:read")
    public ApiResponse<PageResult<AdminUserSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listUsers(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("user:read")
    public ApiResponse<AdminUserSummary> get(@PathVariable String id) {
        return ApiResponse.ok(service.getUser(id));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("user:update")
    public ApiResponse<AdminUserSummary> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request));
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("user:update")
    public ApiResponse<AdminUserSummary> assignRoles(
            @PathVariable String id,
            @Valid @RequestBody AssignUserRolesRequest request) {
        return ApiResponse.ok(service.assignRoles(id, request));
    }
}
