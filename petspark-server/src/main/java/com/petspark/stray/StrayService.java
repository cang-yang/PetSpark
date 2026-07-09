package com.petspark.stray;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.file.FileObjectRepository;
import com.petspark.notification.NotificationService;
import com.petspark.stray.StrayDtos.AdminClueQuery;
import com.petspark.stray.StrayDtos.AssignRequest;
import com.petspark.stray.StrayDtos.ClueCreateRequest;
import com.petspark.stray.StrayDtos.ClueView;
import com.petspark.stray.StrayDtos.MyClueQuery;
import com.petspark.stray.StrayDtos.TransitionRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 流浪救助应用服务。线索独立闭环，不直接驱动领养/宠物状态机。 */
@Service
public class StrayService {

    private static final String MODULE = "stray";

    private final StrayRepository repository;
    private final FileObjectRepository files;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public StrayService(StrayRepository repository, FileObjectRepository files,
            NotificationService notificationService, AuditService auditService) {
        this.repository = repository;
        this.files = files;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public ClueView create(ClueCreateRequest req, String reporterUserId, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            StrayRepository.ClueRow existing = repository.findByIdempotency(reporterUserId, idempotencyKey).orElse(null);
            if (existing != null) {
                return repository.toView(existing);
            }
        }
        List<String> imageIds = normalizeImages(req.imageFileIds());
        for (String imageId : imageIds) {
            if (!files.existsActiveOwned(imageId, reporterUserId)) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
            }
        }
        String id = UUID.randomUUID().toString();
        repository.insert(id, generateClueNo(), reporterUserId, req.animalType(), req.location(),
                req.description(), req.contactPhone(), idempotencyKey);
        for (int i = 0; i < imageIds.size(); i++) {
            repository.insertImage(id, imageIds.get(i), i);
        }
        auditService.recordSuccess(audit(reporterUserId, "user", "create_stray_clue", id));
        return getMine(id, reporterUserId);
    }

    public PageResult<ClueView> listMine(String reporterUserId, MyClueQuery query) {
        return repository.findByReporter(reporterUserId, query);
    }

    public ClueView getMine(String id, String reporterUserId) {
        StrayRepository.ClueRow row = load(id);
        if (!row.reporterUserId().equals(reporterUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        return repository.toView(row);
    }

    public PageResult<ClueView> listAdmin(AdminClueQuery query) {
        return repository.findAdmin(query);
    }

    public ClueView getAdmin(String id) {
        return repository.toView(load(id));
    }

    @Transactional
    public ClueView assign(String id, AssignRequest req, String operatorId) {
        StrayRepository.ClueRow row = load(id);
        if (!"SUBMITTED".equals(row.status())) {
            throw new BusinessException(ErrorCode.STRAY_STATE_001);
        }
        if (!repository.userExists(req.assignedUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001, "指派用户不存在或不可用");
        }
        int affected = repository.assign(id, req.assignedUserId(), req.note(), req.version());
        if (affected == 0) {
            throw stateOrVersion(id, req.version());
        }
        auditService.recordSuccess(audit(operatorId, "operator", "assign_stray_clue", id));
        notificationService.send(row.reporterUserId(), "STRAY_ASSIGNED", "流浪救助线索已受理",
                "您的线索 " + row.clueNo() + " 已指派救助负责人", "STRAY", id);
        return getAdmin(id);
    }

    @Transactional
    public ClueView transition(String id, TransitionRequest req, String operatorId) {
        StrayRepository.ClueRow row = load(id);
        ensureTransitionAllowed(row.status(), req.status());
        int affected = repository.transition(id, req.status(), req.note(), req.handoffPetId(), req.handoffNote(), req.version());
        if (affected == 0) {
            throw stateOrVersion(id, req.version());
        }
        String action = switch (req.status()) {
            case "IN_RESCUE" -> "start_stray_rescue";
            case "RESOLVED" -> "resolve_stray_clue";
            case "CLOSED" -> "close_stray_clue";
            default -> "transition_stray_clue";
        };
        auditService.recordSuccess(audit(operatorId, "operator", action, id));
        notificationService.send(row.reporterUserId(), "STRAY_" + req.status(), title(req.status()),
                content(row.clueNo(), req.status()), "STRAY", id);
        return getAdmin(id);
    }

    private List<String> normalizeImages(List<String> imageFileIds) {
        if (imageFileIds == null || imageFileIds.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String imageId : imageFileIds) {
            if (StringUtils.hasText(imageId)) {
                ids.add(imageId.trim());
            }
        }
        if (ids.size() > 6) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001, "最多上传 6 张线索图片");
        }
        return List.copyOf(ids);
    }

    private void ensureTransitionAllowed(String current, String target) {
        boolean allowed = switch (target) {
            case "IN_RESCUE" -> "ASSIGNED".equals(current);
            case "RESOLVED" -> "ASSIGNED".equals(current) || "IN_RESCUE".equals(current);
            case "CLOSED" -> "SUBMITTED".equals(current) || "ASSIGNED".equals(current)
                    || "IN_RESCUE".equals(current) || "RESOLVED".equals(current);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.STRAY_STATE_001);
        }
    }

    private BusinessException stateOrVersion(String id, int version) {
        StrayRepository.ClueRow current = load(id);
        if (current.version() != version) {
            return new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return new BusinessException(ErrorCode.STRAY_STATE_001);
    }

    private StrayRepository.ClueRow load(String id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.STRAY_NOT_FOUND_001));
    }

    private String title(String status) {
        return switch (status) {
            case "IN_RESCUE" -> "流浪救助已开始";
            case "RESOLVED" -> "流浪救助线索已解决";
            case "CLOSED" -> "流浪救助线索已关闭";
            default -> "流浪救助状态更新";
        };
    }

    private String content(String clueNo, String status) {
        return switch (status) {
            case "IN_RESCUE" -> "您的线索 " + clueNo + " 已进入现场救助阶段";
            case "RESOLVED" -> "您的线索 " + clueNo + " 已完成处理，请查看处理备注";
            case "CLOSED" -> "您的线索 " + clueNo + " 已关闭，请查看处理备注";
            default -> "您的线索 " + clueNo + " 状态已更新";
        };
    }

    private String generateClueNo() {
        long ts = System.currentTimeMillis();
        int rand = UUID.randomUUID().hashCode() & 0xffff;
        return "STRAY-" + ts + "-" + String.format("%04x", rand);
    }

    private AuditContext audit(String actorId, String role, String action, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType("stray_clue")
                .objectId(objectId)
                .build();
    }
}
