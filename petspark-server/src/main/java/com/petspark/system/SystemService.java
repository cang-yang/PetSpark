package com.petspark.system;

import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemService {

    private final SystemRepository repository;

    public SystemService(SystemRepository repository) {
        this.repository = repository;
    }

    public List<DictTypeView> listDictTypes() {
        return repository.findDictTypes();
    }

    @Transactional
    public DictTypeView createDictType(CreateDictTypeRequest request) {
        String code = normalizeKey(request.code(), "[a-z][a-z0-9_:-]{1,63}");
        if (isSensitiveKey(code) || repository.findDictType(code).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001);
        }
        repository.createDictType(UUID.randomUUID().toString(), code, request.name().trim());
        return repository.findDictType(code).orElseThrow();
    }

    public List<DictItemView> listDictItems(String typeCode) {
        String code = normalizeKey(typeCode, "[a-z][a-z0-9_:-]{1,63}");
        repository.findDictType(code).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        return repository.findDictItems(code);
    }

    @Transactional
    public DictItemView createDictItem(String typeCode, CreateDictItemRequest request) {
        String code = normalizeKey(typeCode, "[a-z][a-z0-9_:-]{1,63}");
        repository.findDictType(code).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        String itemKey = normalizeKey(request.itemKey(), "[A-Z][A-Z0-9_:-]{1,63}");
        String status = normalizeStatus(request.status());
        repository.createDictItem(UUID.randomUUID().toString(), code, itemKey, request.itemLabel().trim(),
                request.sortOrder(), status);
        return repository.findDictItems(code).stream()
                .filter(item -> item.itemKey().equals(itemKey))
                .findFirst().orElseThrow();
    }

    @Transactional
    public DictItemView updateDictItem(String id, UpdateDictItemRequest request) {
        DictItemView before = repository.findDictItem(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        int updated = repository.updateDictItem(id, request.itemLabel().trim(), request.sortOrder(),
                normalizeStatus(request.status()), request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return repository.findDictItem(before.id()).orElseThrow();
    }

    public List<SystemConfigView> listConfigs() {
        return repository.findConfigs().stream().filter(config -> !isSensitiveKey(config.configKey())).toList();
    }

    @Transactional
    public SystemConfigView updateConfig(String key, UpdateConfigRequest request) {
        String normalized = normalizeConfigKey(key);
        if (isSensitiveKey(normalized)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED_001);
        }
        SystemConfigView current = repository.findConfig(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (current.protectedKey()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "protected config cannot be changed here");
        }
        String valueType = normalizeValueType(request.valueType());
        validateValue(request.configValue(), valueType);
        int updated = repository.updateConfig(normalized, request.configValue().trim(), valueType,
                request.description(), request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return repository.findConfig(normalized).orElseThrow();
    }

    public PageResult<AuditLogView> listAuditLogs(String actorId, String module, String result, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return repository.findAuditLogs(actorId, module, result, safePage, safeSize);
    }

    private String normalizeStatus(String status) {
        String value = normalizeKey(status, "[A-Z][A-Z0-9_]{1,15}");
        if (!List.of("ACTIVE", "DISABLED").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value;
    }

    private String normalizeValueType(String valueType) {
        String value = normalizeKey(valueType, "[A-Z][A-Z0-9_]{1,15}");
        if (!List.of("STRING", "BOOLEAN", "INTEGER").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value;
    }

    private void validateValue(String value, String valueType) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        if ("BOOLEAN".equals(valueType) && !List.of("true", "false").contains(value.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        if ("INTEGER".equals(valueType)) {
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
            }
        }
    }

    private String normalizeConfigKey(String key) {
        return normalizeKey(key, "[a-z][a-z0-9_.-]{1,127}");
    }

    private String normalizeKey(String value, String pattern) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        String trimmed = value.trim();
        if (!trimmed.matches(pattern)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return trimmed;
    }

    boolean isSensitiveKey(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("jwt")
                || lower.contains("db.")
                || lower.contains("database")
                || lower.contains("spark")
                || lower.contains("api-key")
                || lower.contains("apikey");
    }
}
