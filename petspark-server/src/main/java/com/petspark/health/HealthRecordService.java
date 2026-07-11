package com.petspark.health;

import com.petspark.audit.AuditService;
import com.petspark.audit.AuditContext;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.file.FileObjectRepository;
import com.petspark.health.HealthRecordRepository.HealthRecordRow;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 宠物健康记录服务。负责授权、加密、修订链与隐私清除：
 * <ul>
 *   <li>授权：列出/创建为「宠物主人 或 持有 {@code health:manage}/{@code health:correct}」，
 *       修订为「原作者 或 {@code health:correct}」，清除为「宠物主人 或 {@code privacy:manage}」。</li>
 *   <li>加密：{@code detail} 经 {@link HealthDetailCrypto} 加密后入库；明文不出现在
 *       仓储层与审计上下文。</li>
 *   <li>修订：创建新行并指向 {@code revision_of_id}，原行保持不变。</li>
 *   <li>隐私清除：置空密文与附件，保留审计外壳；二次清除抛 {@link ErrorCode#HEALTH_RETENTION_001}。</li>
 * </ul>
 */
@Service
class HealthRecordService {

    private static final String PERM_HEALTH_MANAGE = "health:manage";
    private static final String PERM_HEALTH_CORRECT = "health:correct";
    private static final String PERM_PRIVACY_MANAGE = "privacy:manage";

    private final HealthRecordRepository records;
    private final HealthDetailCrypto crypto;
    private final FileObjectRepository files;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    @Autowired
    HealthRecordService(HealthRecordRepository records, HealthDetailCrypto crypto,
                        FileObjectRepository files, JdbcTemplate jdbcTemplate,
                        AuditService auditService) {
        this.records = records;
        this.crypto = crypto;
        this.files = files;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional
    PageResult<HealthRecordView> listForPet(String petId, HealthQuery query, AuthenticatedUser requester) {
        PetOwnership pet = loadPet(petId);
        boolean authorized = Objects.equals(pet.ownerId, requester.getId())
                || hasAuthority(requester, PERM_HEALTH_MANAGE)
                || hasAuthority(requester, PERM_HEALTH_CORRECT);
        if (!authorized) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        boolean revealDetail = Objects.equals(pet.ownerId, requester.getId())
                || hasAuthority(requester, PERM_HEALTH_MANAGE)
                || hasAuthority(requester, PERM_HEALTH_CORRECT);
        var pageRows = records.findByPet(petId, query.getRecordType(), query.getPage(), query.getSize());
        List<HealthRecordView> views = pageRows.getItems().stream()
                .map(row -> toView(row, revealDetail, true))
                .toList();
        return new PageResult<>(views, pageRows.getPage(), pageRows.getSize(), pageRows.getTotal());
    }

    @Transactional
    HealthRecordView create(String petId, HealthRecordRequest req, AuthenticatedUser author) {
        PetOwnership pet = loadPet(petId);
        boolean authorized = Objects.equals(pet.ownerId, author.getId())
                || hasAuthority(author, PERM_HEALTH_MANAGE);
        if (!authorized) {
            throw new BusinessException(ErrorCode.HEALTH_SCOPE_001);
        }
        ensureAttachmentOwnedOrAvailable(req.attachmentFileId(), author, pet);
        String id = UUID.randomUUID().toString();
        String ciphertext = crypto.encrypt(req.detail());
        String sourceRole = resolveSourceRole(req.sourceRole(), author);
        HealthRecordRow row = new HealthRecordRow(id, petId, req.recordType(), req.occurredOn(),
                req.summary(), ciphertext, req.attachmentFileId(), sourceRole, author.getId(),
                null, "ACTIVE", null, 0);
        records.insert(row);
        audit(author, "create_health_record", id);
        return toView(records.findById(id).orElseThrow(), true, true);
    }

    @Transactional
    HealthRecordView revise(String recordId, HealthRevisionRequest req, AuthenticatedUser author) {
        HealthRecordRow original = records.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        if (!original.status().equals("ACTIVE")) {
            throw new BusinessException(ErrorCode.HEALTH_RETENTION_001);
        }
        boolean authorized = author.getId().equals(original.authorId())
                || hasAuthority(author, PERM_HEALTH_CORRECT);
        if (!authorized) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        PetOwnership pet = loadPet(original.petId());
        ensureAttachmentOwnedOrAvailable(req.attachmentFileId(), author, pet);
        String id = UUID.randomUUID().toString();
        String ciphertext = crypto.encrypt(req.detail());
        String sourceRole = resolveSourceRole(req.sourceRole(), author);
        HealthRecordRow row = new HealthRecordRow(id, original.petId(), req.recordType(), req.occurredOn(),
                req.summary(), ciphertext, req.attachmentFileId(), sourceRole, author.getId(),
                original.id(), "ACTIVE", null, 0);
        records.insert(row);
        audit(author, "revise_health_record", id);
        return toView(records.findById(id).orElseThrow(), true, true);
    }

    @Transactional
    void erase(String recordId, HealthEraseRequest req, AuthenticatedUser actor) {
        HealthRecordRow row = records.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        PetOwnership pet = loadPet(row.petId());
        boolean authorized = Objects.equals(pet.ownerId, actor.getId())
                || hasAuthority(actor, PERM_PRIVACY_MANAGE);
        if (!authorized) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        int affected = records.markErased(recordId, req.reason(), actor.getId(), row.version());
        if (affected == 0) {
            // 0 行既可能是版本号已变（并发修订），也可能是已清除（status != 'ACTIVE'）。
            HealthRecordRow latest = records.findById(recordId).orElseThrow();
            if (!"ACTIVE".equals(latest.status())) {
                throw new BusinessException(ErrorCode.HEALTH_RETENTION_001);
            }
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        audit(actor, "erase_health_record", recordId);
    }

    private PetOwnership loadPet(String petId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT id, owner_user_id, public_status FROM pet
                    WHERE id = ? AND deleted_at IS NULL
                    """, petId);
            String owner = row.get("owner_user_id") == null ? null : String.valueOf(row.get("owner_user_id"));
            return new PetOwnership(petId, owner, String.valueOf(row.get("public_status")));
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
        }
    }

    private void ensureAttachmentOwnedOrAvailable(String attachmentFileId, AuthenticatedUser actor, PetOwnership pet) {
        if (!StringUtils.hasText(attachmentFileId)) {
            return;
        }
        boolean owner = Objects.equals(pet.ownerId, actor.getId());
        boolean ok = owner
                ? files.existsActiveOwned(attachmentFileId, actor.getId())
                : files.existsAvailable(attachmentFileId);
        if (!ok) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
        }
    }

    private String resolveSourceRole(String requested, AuthenticatedUser author) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return author.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null && (a.startsWith("ROLE:") || a.equals("ADMIN") || a.equals("SERVICE")
                        || a.equals("OP") || a.equals("AUDITOR") || a.equals("USER")))
                .findFirst()
                .orElse("USER");
    }

    private boolean hasAuthority(AuthenticatedUser user, String code) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (var a : user.getAuthorities()) {
            if (code.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private HealthRecordView toView(HealthRecordRow row, boolean revealDetail, boolean includeAttachmentUrl) {
        String detail = revealDetail && "ACTIVE".equals(row.status()) ? crypto.decrypt(row.detailCiphertext()) : null;
        String attachmentUrl = includeAttachmentUrl && StringUtils.hasText(row.attachmentFileId())
                ? "/api/v1/files/" + row.attachmentFileId() : null;
        String authorName = findAuthorName(row.authorId());
        return new HealthRecordView(row.id(), row.petId(), row.recordType(), row.occurredOn(),
                row.summary(), detail, row.attachmentFileId(), attachmentUrl, row.sourceRole(),
                row.authorId(), authorName, row.revisionOfId(), row.status(), row.createdAt(), row.version());
    }

    private String findAuthorName(String authorId) {
        if (!StringUtils.hasText(authorId)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT nickname FROM sys_user WHERE id = ?", String.class, authorId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void audit(AuthenticatedUser actor, String action, String objectId) {
        AuditContext ctx = AuditContext.builder()
                .actorId(actor.getId())
                .actorRole(resolveSourceRole(null, actor))
                .module("health")
                .action(action)
                .objectType("pet_health_record")
                .objectId(objectId)
                .build();
        auditService.recordSuccess(ctx);
    }

    private record PetOwnership(String id, String ownerId, String publicStatus) {}
}
