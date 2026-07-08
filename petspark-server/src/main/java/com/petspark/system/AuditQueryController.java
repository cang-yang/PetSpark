package com.petspark.system;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audits")
public class AuditQueryController {

    private final SystemService service;

    public AuditQueryController(SystemService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("audit:read")
    public ApiResponse<PageResult<AuditLogView>> list(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listAuditLogs(actorId, module, result, page, size));
    }
}
