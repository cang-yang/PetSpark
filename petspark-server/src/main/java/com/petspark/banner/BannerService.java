package com.petspark.banner;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BannerService {

    private static final String MODULE = "banner";
    private static final String ROLE = "operator";

    private final BannerRepository repository;
    private final AuditService auditService;

    public BannerService(BannerRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<BannerView> activeBanners(int limit) {
        return repository.findPublicActive(Math.min(Math.max(limit, 1), 20));
    }

    public PageResult<BannerView> adminBanners(String keyword, String status, int page, int size) {
        return repository.findAdmin(keyword, status, page, size);
    }

    public BannerView adminBanner(String id) {
        return requireBanner(id);
    }

    @Transactional
    public BannerView create(BannerUpsertRequest request, String operatorId) {
        validate(request);
        String id = UUID.randomUUID().toString();
        repository.insert(id, request);
        auditService.recordSuccess(audit(operatorId, "create_banner", id));
        return requireBanner(id);
    }

    @Transactional
    public BannerView update(String id, BannerUpsertRequest request, String operatorId) {
        requireBanner(id);
        validate(request);
        int updated = repository.update(id, request);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "update_banner", id));
        return requireBanner(id);
    }

    @Transactional
    public BannerView updateStatus(String id, BannerStatusRequest request, String operatorId) {
        requireBanner(id);
        int updated = repository.updateStatus(id, request.status(), request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "update_banner_status", id));
        return requireBanner(id);
    }

    @Transactional
    public BannerView updateOrder(String id, BannerOrderRequest request, String operatorId) {
        requireBanner(id);
        int updated = repository.updateSortOrder(id, request.sortOrder(), request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "update_banner_order", id));
        return requireBanner(id);
    }

    @Transactional
    public void delete(String id, int version, String operatorId) {
        requireBanner(id);
        int updated = repository.softDelete(id, version);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "delete_banner", id));
    }

    private BannerView requireBanner(String id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
    }

    private void validate(BannerUpsertRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "开始时间必须早于结束时间");
        }
        if (StringUtils.hasText(request.targetType()) && !StringUtils.hasText(request.targetUrl())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "配置跳转类型时必须填写跳转地址");
        }
        if (StringUtils.hasText(request.targetUrl())
                && request.targetUrl().trim().toLowerCase(java.util.Locale.ROOT).startsWith("javascript:")) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "跳转地址不允许使用 javascript 协议");
        }
    }

    private AuditContext audit(String actorId, String action, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(ROLE)
                .module(MODULE)
                .action(action)
                .objectType("operation_banner")
                .objectId(objectId)
                .build();
    }
}
