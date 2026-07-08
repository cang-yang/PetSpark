package com.petspark.rbac;

import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {

    private static final String ADMIN = "ADMIN";

    private final RbacRepository repository;

    public RbacService(RbacRepository repository) {
        this.repository = repository;
    }

    public PageResult<AdminUserSummary> listUsers(String keyword, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String normalizedStatus = normalizeStatus(status, true);
        return repository.findUsers(keyword, normalizedStatus, safePage, safeSize);
    }

    public AdminUserSummary getUser(String id) {
        return repository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
    }

    @Transactional
    public AdminUserSummary updateStatus(String id, UpdateUserStatusRequest request) {
        AdminUserSummary user = getUser(id);
        String status = normalizeStatus(request.status(), false);
        if (!"ACTIVE".equals(status) && repository.userHasRole(id, ADMIN) && repository.countActiveAdmins() <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "不能禁用或锁定最后一个可用管理员");
        }
        int updated = repository.updateUserStatus(id, status, request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return getUser(user.id());
    }

    @Transactional
    public AdminUserSummary assignRoles(String id, AssignUserRolesRequest request) {
        getUser(id);
        List<String> roleCodes = normalizeRoleCodes(request.roleCodes());
        if (roleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001, "用户至少需要一个角色");
        }
        if (!repository.existsAllRoles(roleCodes)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001, "角色不存在或不可用");
        }
        if (repository.userHasRole(id, ADMIN) && !roleCodes.contains(ADMIN) && repository.countActiveAdmins() <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "不能移除最后一个管理员角色");
        }
        repository.replaceUserRoles(id, roleCodes);
        return getUser(id);
    }

    public List<RoleView> listRoles() {
        return repository.findRoles();
    }

    public List<PermissionView> listPermissions() {
        return repository.findPermissions();
    }

    @Transactional
    public RoleView createRole(CreateRoleRequest request) {
        String code = normalizeRoleCode(request.code());
        if (repository.findRoleByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "角色编码已存在");
        }
        List<String> permissions = normalizePermissionCodes(request.permissionCodes());
        ensurePermissionsExist(permissions);
        repository.createRole(UUID.randomUUID().toString(), code, request.name().trim(), permissions);
        return repository.findRoleByCode(code).orElseThrow();
    }

    @Transactional
    public RoleView updateRolePermissions(String code, UpdateRolePermissionsRequest request) {
        String normalized = normalizeRoleCode(code);
        RoleView role = repository.findRoleByCode(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (role.builtIn()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "内置角色权限不允许直接修改");
        }
        List<String> permissions = normalizePermissionCodes(request.permissionCodes());
        ensurePermissionsExist(permissions);
        repository.replaceRolePermissions(normalized, permissions);
        return repository.findRoleByCode(normalized).orElseThrow();
    }

    private void ensurePermissionsExist(List<String> permissions) {
        if (!repository.existsAllPermissions(permissions)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001, "权限码不存在");
        }
    }

    private String normalizeStatus(String status, boolean allowBlank) {
        if (status == null || status.isBlank()) {
            if (allowBlank) {
                return null;
            }
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED", "LOCKED").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value;
    }

    private List<String> normalizeRoleCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(this::normalizeRoleCode)
                .distinct()
                .toList();
    }

    private List<String> normalizePermissionCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(this::normalizePermissionCode)
                .distinct()
                .toList();
    }

    private String normalizeRoleCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{1,31}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value;
    }

    private String normalizePermissionCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        String value = code.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_-]{1,63}:[a-z][a-z0-9_-]{1,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value;
    }
}
