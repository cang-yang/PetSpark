package com.petspark.catalog;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private static final String MODULE = "catalog";
    private static final String ROLE = "operator";

    private final CatalogRepository repository;
    private final AuditService auditService;

    public CatalogService(CatalogRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public PageResult<GoodsView> publicGoods(String categoryId, String keyword, int page, int size) {
        return repository.findPublicGoods(categoryId, keyword, page, size);
    }

    public GoodsView publicGoodsDetail(String id) {
        return repository.findPublicGoodsById(id).orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND_001));
    }

    public PageResult<GoodsView> adminGoods(String categoryId, String keyword, String status, int page, int size) {
        return repository.findAdminGoods(categoryId, keyword, status, page, size);
    }

    public GoodsView adminGoodsDetail(String id) {
        return requireGoods(id);
    }

    @Transactional
    public GoodsView createGoods(GoodsUpsertRequest request, String operatorId) {
        validateGoodsRequest(request, operatorId);
        String id = UUID.randomUUID().toString();
        try {
            repository.insertGoods(id, request);
        } catch (RuntimeException ex) {
            if (repository.isDuplicateKey(ex)) {
                throw new BusinessException(ErrorCode.GOODS_SKU_001);
            }
            throw ex;
        }
        auditService.recordSuccess(audit(operatorId, "create_goods", "goods", id));
        return requireGoods(id);
    }

    @Transactional
    public GoodsView updateGoods(String id, GoodsUpsertRequest request, String operatorId) {
        requireGoods(id);
        validateGoodsRequest(request, operatorId);
        try {
            int updated = repository.updateGoods(id, request);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
        } catch (RuntimeException ex) {
            if (repository.isDuplicateKey(ex)) {
                throw new BusinessException(ErrorCode.GOODS_SKU_001);
            }
            throw ex;
        }
        auditService.recordSuccess(audit(operatorId, "update_goods", "goods", id));
        return requireGoods(id);
    }

    @Transactional
    public GoodsView updateStatus(String id, GoodsStatusRequest request, String operatorId) {
        requireGoods(id);
        int updated = repository.updateStatus(id, request.status(), request.version());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "update_goods_status", "goods", id));
        return requireGoods(id);
    }

    @Transactional
    public GoodsView adjustStock(String id, StockAdjustmentRequest request, String operatorId) {
        GoodsView before = requireGoods(id);
        int updated = repository.adjustStock(id, request.delta(), request.version());
        if (updated != 1) {
            GoodsView current = requireGoods(id);
            if (current.version() != request.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ORDER_STOCK_001);
        }
        GoodsView after = requireGoods(id);
        repository.insertStockAdjustment(UUID.randomUUID().toString(), id, request.delta(),
                before.stock(), after.stock(), request.reason(), operatorId);
        auditService.recordSuccess(audit(operatorId, "adjust_stock", "goods", id));
        return after;
    }

    public List<GoodsCategoryView> categories(boolean includeInactive) {
        return repository.findCategories(includeInactive);
    }

    @Transactional
    public GoodsCategoryView createCategory(GoodsCategoryRequest request, String operatorId) {
        String id = UUID.randomUUID().toString();
        try {
            repository.insertCategory(id, request);
        } catch (RuntimeException ex) {
            if (repository.isDuplicateKey(ex)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_001, "分类编码已存在");
            }
            throw ex;
        }
        auditService.recordSuccess(audit(operatorId, "create_category", "goods_category", id));
        return repository.findCategories(true).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public GoodsCategoryView updateCategory(String id, GoodsCategoryRequest request, String operatorId) {
        int updated = repository.updateCategory(id, request);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        auditService.recordSuccess(audit(operatorId, "update_category", "goods_category", id));
        return repository.findCategories(true).stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
    }

    private void validateGoodsRequest(GoodsUpsertRequest request, String operatorId) {
        if (!repository.categoryExistsActive(request.categoryId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
        }
        if (!repository.coverIsUsable(request.coverFileId(), operatorId)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
        }
    }

    private GoodsView requireGoods(String id) {
        return repository.findAdminGoodsById(id).orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND_001));
    }

    private AuditContext audit(String actorId, String action, String objectType, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(ROLE)
                .module(MODULE)
                .action(action)
                .objectType(objectType)
                .objectId(objectId)
                .build();
    }
}
