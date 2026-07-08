package com.petspark.file;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final String MODULE = "file";
    private static final String OBJECT_TYPE = "file";

    private final ImageValidator imageValidator;
    private final FileStorage storage;
    private final FileObjectRepository repository;
    private final AuditService auditService;

    public FileService(ImageValidator imageValidator, FileStorage storage,
            FileObjectRepository repository, AuditService auditService) {
        this.imageValidator = imageValidator;
        this.storage = storage;
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public FileUploadResponse uploadImage(MultipartFile upload, String businessType, String ownerId) {
        String normalizedBusinessType = normalizeBusinessType(businessType);
        ImageMetadata metadata = imageValidator.validate(upload);
        String id = UUID.randomUUID().toString();
        String objectKey = UUID.randomUUID() + "." + metadata.extension();
        storage.store(objectKey, metadata.content());
        try {
            repository.insert(new FileObject(
                    id, objectKey, upload.getOriginalFilename(), metadata.mediaType(), metadata.extension(),
                    metadata.sizeBytes(), metadata.sha256(), metadata.width(), metadata.height(), "STAGED",
                    ownerId, normalizedBusinessType, null));
        } catch (RuntimeException ex) {
            storage.delete(objectKey);
            // 元数据落库失败：审计失败事件不阻断业务（AuditServiceImpl 内部已吞异常），
            // 此处记录便于事后追溯为何出现孤儿清理。
            auditService.recordFailure(auditContext(ownerId, "upload", id), ex.getClass().getSimpleName());
            throw ex;
        }
        auditService.recordSuccess(auditContext(ownerId, "upload", id));
        return response(id, "STAGED");
    }

    @Transactional
    public FileUploadResponse confirm(String id, String ownerId) {
        FileObject file = repository.findAvailable(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND_001));
        requireOwner(file, ownerId);
        if ("STAGED".equals(file.status()) && repository.confirm(id, ownerId) != 1) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
        }
        auditService.recordSuccess(auditContext(ownerId, "confirm", id));
        return response(id, "ACTIVE");
    }

    public StoredFile read(String id, String requesterId) {
        FileObject file = repository.findAvailable(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND_001));
        requireOwner(file, requesterId);
        return new StoredFile(file.mediaType(), file.originalName(), storage.read(file.objectKey()));
    }

    private void requireOwner(FileObject file, String userId) {
        if (userId == null || !file.ownerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("file owner required");
        }
    }

    private String normalizeBusinessType(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{1,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private FileUploadResponse response(String id, String status) {
        return new FileUploadResponse(id, "/api/v1/files/" + id, status);
    }

    /**
     * 组装文件操作审计上下文。{@code actorRole} 暂以 owner 充当——完整角色
     * 由 PR-RBAC-01 接入权限树后从安全上下文补全，此处仅满足审计可追溯。
     */
    private AuditContext auditContext(String ownerId, String action, String objectId) {
        return AuditContext.builder()
                .actorId(ownerId)
                .actorRole("owner")
                .module(MODULE)
                .action(action)
                .objectType(OBJECT_TYPE)
                .objectId(objectId)
                .build();
    }
}
