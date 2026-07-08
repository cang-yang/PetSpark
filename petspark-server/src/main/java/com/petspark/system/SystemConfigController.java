package com.petspark.system;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/configs")
public class SystemConfigController {

    private final SystemService service;

    public SystemConfigController(SystemService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("config:read")
    public ApiResponse<List<SystemConfigView>> list() {
        return ApiResponse.ok(service.listConfigs());
    }

    @PutMapping("/{key}")
    @RequirePermission("config:update")
    public ApiResponse<SystemConfigView> update(
            @PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest request) {
        return ApiResponse.ok(service.updateConfig(key, request));
    }
}
