package com.petspark.adoption;

import com.petspark.adoption.AdoptionDtos.AdminApplicationQuery;
import com.petspark.adoption.AdoptionDtos.AdoptablePetQuery;
import com.petspark.adoption.AdoptionDtos.ApplicationCreateRequest;
import com.petspark.adoption.AdoptionDtos.ApplicationView;
import com.petspark.adoption.AdoptionDtos.HandoverRequest;
import com.petspark.adoption.AdoptionDtos.MyApplicationQuery;
import com.petspark.adoption.AdoptionDtos.ReviewRequest;
import com.petspark.adoption.AdoptionDtos.WithdrawRequest;
import com.petspark.adoption.AdoptionRepository.ApplicationRow;
import com.petspark.adoption.AdoptionRepository.PetLockInfo;
import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.notification.NotificationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 领养申请应用服务。覆盖 API-ADOPT-001~008：可领养宠物浏览、本人申请/撤回、
 * 管理员审核与交接闭环。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>同一宠物仅允许一条未结束申请（PENDING/APPROVED/COMPLETED）。MySQL 无 partial
 *       unique index，故提交申请时先 {@code SELECT ... FOR UPDATE} 锁 pet 行后复查
 *       申请，配合 {@code pet.version} 乐观锁兜底并发双申请。</li>
 *   <li>审核通过：pet ADOPTABLE→ADOPTING；交接成功：pet ADOPTING→ADOPTED 且
 *       owner_user_id 置为申请人；交接失败：pet 回到 ADOPTABLE 并抛
 *       {@code ADOPTION_HANDOVER_001}，申请置 CANCELLED。</li>
 *   <li>本人撤回 / 审核拒绝：若 pet 已 ADOPTING 则回滚到 ADOPTABLE。</li>
 *   <li>幂等：{@code Idempotency-Key} 命中已有申请即原样返回（重放），不重复插入。</li>
 *   <li>状态机 UPDATE 带 status 前置条件 + version 乐观锁；0 行时区分版本冲突与
 *       状态非法（与 order 模块同语义）。</li>
 *   <li>通知：审核通过/拒绝、本人撤回、交接完成/失败均向申请人发通知；
 *       审计：每次状态迁移落 {@code audit_log}（审计失败不阻断业务）。</li>
 * </ul>
 */
@Service
public class AdoptionService {

    private static final String MODULE = "adoption";

    private final AdoptionRepository repository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AdoptionService(AdoptionRepository repository,
            NotificationService notificationService,
            AuditService auditService) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ========== 可领养宠物浏览（API-ADOPT-001） ==========

    /** 可领养宠物分页列表：public_status='PUBLISHED' 且 adoption_status='ADOPTABLE'。 */
    public PageResult<AdoptionDtos.AdoptablePetView> listAdoptable(AdoptablePetQuery q) {
        return repository.findAdoptablePets(q);
    }

    /** 可领养宠物详情；不存在或不符合条件返回 null（Controller 转 404）。 */
    public AdoptionDtos.AdoptablePetView getAdoptable(String petId) {
        return repository.findAdoptablePet(petId);
    }

    // ========== 本人申请（API-ADOPT-002 / 003 / 004 / 008） ==========

    /**
     * 提交申请：幂等优先；锁 pet 行 + 复查未结束申请 + 落库。pet 必须 ADOPTABLE。
     * 重复申请抛 {@link ErrorCode#ADOPTION_DUPLICATE_001}；pet 不可领养抛
     * {@link ErrorCode#PET_STATE_001}。
     */
    @Transactional
    public ApplicationView create(ApplicationCreateRequest req, String applicantUserId, String idemKey) {
        if (StringUtils.hasText(idemKey)) {
            ApplicationRow existing = repository.findByIdempotency(applicantUserId, idemKey).orElse(null);
            if (existing != null) {
                return viewOf(existing, "applicant");
            }
        }
        // 锁 pet 行，阻塞并发双申请；锁内复查未结束申请。
        PetLockInfo pet = repository.lockPetForUpdate(req.petId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001, "宠物不存在或不可领养"));
        if (!"PUBLISHED".equals(pet.publicStatus()) || !"ADOPTABLE".equals(pet.adoptionStatus())) {
            throw new BusinessException(ErrorCode.PET_STATE_001, "宠物当前不可领养");
        }
        if (repository.existsActiveForPet(req.petId())) {
            throw new BusinessException(ErrorCode.ADOPTION_DUPLICATE_001);
        }
        String id = UUID.randomUUID().toString();
        String applicationNo = generateApplicationNo();
        repository.insert(id, applicationNo, req.petId(), applicantUserId,
                req.statement(), req.profileSnapshot(), idemKey);
        auditService.recordSuccess(audit(applicantUserId, "user", "create_adoption_application", id));
        return viewOf(repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND_001)), "applicant");
    }

    /** 本人申请列表。 */
    public PageResult<ApplicationView> listMine(String applicantUserId, MyApplicationQuery q) {
        return repository.findByApplicant(applicantUserId, q);
    }

    /**
     * 申请详情：本人或审核角色可查（API-ADOPT-008）。非本人且无审核权限 →
     * {@link ErrorCode#ACCESS_OWNERSHIP_001}。
     */
    public ApplicationView get(String id, String userId, boolean canReview) {
        ApplicationRow row = load(id);
        boolean owner = row.applicantUserId().equals(userId);
        if (!owner && !canReview) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        return viewOf(row, owner ? "applicant" : "reviewer");
    }

    /**
     * 本人撤回：PENDING/APPROVED → WITHDRAWN。若 pet 已 ADOPTING 则回滚到 ADOPTABLE。
     * 通知申请人；失败时区分版本冲突与状态非法。
     */
    @Transactional
    public ApplicationView withdraw(String id, WithdrawRequest req, String userId) {
        ApplicationRow row = load(id);
        if (!row.applicantUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        int affected = repository.withdraw(id, req.reason(), req.version());
        if (affected == 0) {
            ApplicationRow current = load(id);
            if (!"PENDING".equals(current.status()) && !"APPROVED".equals(current.status())) {
                throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
        }
        // 若 pet 已因审核通过进入 ADOPTING，撤回时恢复可领养。
        rollbackPetToAdoptableIfAdopting(row.petId());
        auditService.recordSuccess(audit(userId, "user", "withdraw_adoption_application", id));
        notificationService.send(row.applicantUserId(), "ADOPTION_WITHDRAWN", "领养申请已撤回",
                "您的领养申请 " + row.applicationNo() + " 已撤回", "ADOPTION", id);
        return viewOf(load(id), "applicant");
    }

    // ========== 管理员审核（API-ADOPT-005 / 006） ==========

    /** 管理员审核列表。 */
    public PageResult<ApplicationView> listAdmin(AdminApplicationQuery q) {
        return repository.findAdmin(q);
    }

    /**
     * 审核决策：PENDING → APPROVED/REJECTED。通过则 pet ADOPTABLE→ADOPTING；
     * 拒绝则通知申请人。失败时区分版本冲突与状态非法。
     */
    @Transactional
    public ApplicationView review(String id, ReviewRequest req, String reviewerUserId) {
        ApplicationRow row = load(id);
        if (!"PENDING".equals(row.status())) {
            // 状态非法优先于版本陈旧。
            throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
        }
        int affected = repository.review(id, req.decision(), req.reason(), reviewerUserId, req.version());
        if (affected == 0) {
            ApplicationRow current = load(id);
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
        }
        if ("APPROVED".equals(req.decision())) {
            // pet ADOPTABLE→ADOPTING；若并发被改则视作状态非法。
            PetLockInfo pet = repository.findPetInfo(row.petId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PET_STATE_001, "宠物不存在"));
            int petAffected = repository.transitionPetToAdopting(row.petId(), pet.version());
            if (petAffected == 0) {
                throw new BusinessException(ErrorCode.PET_STATE_001, "宠物状态已变化，无法锁定为领养中");
            }
            notificationService.send(row.applicantUserId(), "ADOPTION_APPROVED", "领养申请已通过",
                    "您的领养申请 " + row.applicationNo() + " 已通过，请等待工作人员联系完成交接", "ADOPTION", id);
        } else {
            notificationService.send(row.applicantUserId(), "ADOPTION_REJECTED", "领养申请未通过",
                    "您的领养申请 " + row.applicationNo() + " 未通过审核", "ADOPTION", id);
        }
        auditService.recordSuccess(audit(reviewerUserId, "operator", "review_adoption_application", id));
        return viewOf(load(id), "reviewer");
    }

    // ========== 交接闭环（API-ADOPT-007） ==========

    /**
     * 记录线下交接结果。SUCCESS：APPROVED→COMPLETED 且 pet ADOPTING→ADOPTED，
     * owner_user_id 置为申请人；通知申请人。FAILURE：APPROVED→CANCELLED 且 pet
     * 回到 ADOPTABLE，通知申请人并抛 {@link ErrorCode#ADOPTION_HANDOVER_001}
     * （业务事务仍提交：状态与通知一致落库，错误码交给上层映射为 422 响应）。
     *
     * <p>注意：失败路径在所有写操作（状态、pet 回滚、通知、审计）完成后再抛
     * {@link HandoverFailedException}，该异常被 {@code noRollbackFor} 排除回滚，
     * 从而保证“交接失败”这一业务结果与通知一致落库；而 SUCCESS 路径上的前置校验
     * 异常（版本冲突/状态非法）仍按默认行为回滚，避免出现申请已 COMPLETED 但 pet
     * 未 ADOPTED 的不一致状态。
     */
    @Transactional(noRollbackFor = HandoverFailedException.class)
    public ApplicationView handover(String id, HandoverRequest req, String operatorId) {
        ApplicationRow row = load(id);
        if (!"APPROVED".equals(row.status())) {
            throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
        }
        if ("SUCCESS".equals(req.outcome())) {
            int affected = repository.complete(id, req.note(), req.version());
            if (affected == 0) {
                ApplicationRow current = load(id);
                if (current.version() != req.version()) {
                    throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
                }
                throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
            }
            PetLockInfo pet = repository.findPetInfo(row.petId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PET_STATE_001, "宠物不存在"));
            int petAffected = repository.transitionPetToAdopted(row.petId(), row.applicantUserId(), pet.version());
            if (petAffected == 0) {
                throw new BusinessException(ErrorCode.PET_STATE_001, "宠物状态已变化，无法完成领养");
            }
            auditService.recordSuccess(audit(operatorId, "operator", "complete_adoption_handover", id));
            notificationService.send(row.applicantUserId(), "ADOPTION_COMPLETED", "领养交接完成",
                    "您的领养申请 " + row.applicationNo() + " 已完成交接，恭喜成为新主人", "ADOPTION", id);
            return viewOf(load(id), "reviewer");
        }
        // FAILURE：申请置 CANCELLED，pet 回到 ADOPTABLE，通知申请人，抛业务错误。
        int affected = repository.cancelForHandoverFailure(id, req.note(), req.version());
        if (affected == 0) {
            ApplicationRow current = load(id);
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ADOPTION_STATE_001);
        }
        rollbackPetToAdoptableIfAdopting(row.petId());
        auditService.recordSuccess(audit(operatorId, "operator", "fail_adoption_handover", id));
        notificationService.send(row.applicantUserId(), "ADOPTION_HANDOVER_FAILED", "领养交接未完成",
                "您的领养申请 " + row.applicationNo() + " 交接未完成，宠物已恢复可领养", "ADOPTION", id);
        // 事务正常提交（状态、通知一致落库），但对外返回 422 提示交接失败。
        // 用 HandoverFailedException（BusinessException 子类）以便 noRollbackFor 精准排除，
        // 不影响 SUCCESS 路径上其它 BusinessException 的默认回滚语义。
        throw new HandoverFailedException();
    }

    // ========== 内部辅助 ==========

    private ApplicationRow load(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND_001));
    }

    /** 若 pet 当前为 ADOPTING，回滚到 ADOPTABLE；否则不动（幂等）。 */
    private void rollbackPetToAdoptableIfAdopting(String petId) {
        repository.findPetInfo(petId).ifPresent(pet -> {
            if ("ADOPTING".equals(pet.adoptionStatus())) {
                repository.transitionPetBackToAdoptable(petId, pet.version());
            }
        });
    }

    /** 组装视图并填充状态面板信息（NFR-UX-001）。 */
    private ApplicationView viewOf(ApplicationRow row, String role) {
        AdoptionDtos.ApplicationView base = repository.findView(row.id());
        if (base == null) {
            throw new BusinessException(ErrorCode.ADOPTION_NOT_FOUND_001);
        }
        StatusPanel panel = panelOf(base.status(), role);
        return new ApplicationView(
                base.id(), base.applicationNo(), base.petId(), base.pet(),
                base.applicantUserId(), base.applicantName(), base.statement(), base.profileSnapshot(),
                base.status(), panel.label, panel.styleClass, role, panel.nextStep, panel.allowedActions,
                base.reviewerUserId(), base.reviewerName(), base.reviewNote(), base.decidedAt(),
                base.withdrawnAt(), base.withdrawReason(), base.handoverNote(), base.handoverAt(),
                base.createdAt(), base.version());
    }

    private StatusPanel panelOf(String status, String role) {
        return switch (status) {
            case "PENDING" -> new StatusPanel("审核中", "info",
                    "reviewer".equals(role) ? "请审核：通过或拒绝该申请" : "等待工作人员审核",
                    "reviewer".equals(role) ? "REVIEW" : "WAIT");
            case "APPROVED" -> new StatusPanel("已通过，待交接", "warning",
                    "reviewer".equals(role) ? "请与申请人线下交接并记录结果" : "工作人员将与您联系完成交接",
                    "reviewer".equals(role) ? "HANDOVER" : "WAIT");
            case "REJECTED" -> new StatusPanel("未通过", "danger",
                    "该申请未通过审核", "NONE");
            case "WITHDRAWN" -> new StatusPanel("已撤回", "info",
                    "申请人已撤回该申请", "NONE");
            case "COMPLETED" -> new StatusPanel("已完成", "success",
                    "领养交接已完成", "NONE");
            case "CANCELLED" -> new StatusPanel("已取消", "danger",
                    "交接失败，申请已取消", "NONE");
            default -> new StatusPanel(status, "info", "", "NONE");
        };
    }

    private record StatusPanel(String label, String styleClass, String nextStep, String allowedActions) {
    }

    private String generateApplicationNo() {
        long ts = System.currentTimeMillis();
        int rand = UUID.randomUUID().hashCode() & 0xffff;
        return "ADOPT-" + ts + "-" + String.format("%04x", rand);
    }

    private AuditContext audit(String actorId, String role, String action, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType("adoption_application")
                .objectId(objectId)
                .build();
    }
}
