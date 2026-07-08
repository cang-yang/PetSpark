package com.petspark.order;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.notification.NotificationService;
import com.petspark.user.PhoneCrypto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 订单应用服务。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>下单：按 goodsId ASC 排序后顺序扣减库存，单事务原子回滚（避免死锁 + 超卖）；</li>
 *   <li>幂等：Idempotency-Key 命中已有订单即原样返回（重放），不重复下单；</li>
 *   <li>取消：状态机 CREATED/PROCESSING → CANCELLED，库存一次性回补；</li>
 *   <li>流转：CREATED → PROCESSING → COMPLETED，仅管理员；</li>
 *   <li>手机/地址用 PhoneCrypto 加密落库，明文仅返回给归属用户/管理员。</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final String MODULE = "order";

    private final OrderRepository repository;
    private final PhoneCrypto phoneCrypto;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public OrderService(OrderRepository repository,
            PhoneCrypto phoneCrypto,
            NotificationService notificationService,
            AuditService auditService) {
        this.repository = repository;
        this.phoneCrypto = phoneCrypto;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** 预览订单：不扣库存、不落库，逐行走查可用性与总额。 */
    public OrderDtos.OrderPreviewResult preview(OrderDtos.OrderPreviewRequest req) {
        List<OrderDtos.OrderPreviewLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean allAvailable = true;
        String unavailableReason = null;
        for (OrderDtos.OrderLineRequest line : req.lines()) {
            OrderRepository.GoodsSnapshot goods = repository.findActiveGoodsForOrder(line.goodsId())
                    .orElse(null);
            if (goods == null) {
                lines.add(new OrderDtos.OrderPreviewLine(
                        line.goodsId(), null, null, null, line.quantity(),
                        BigDecimal.ZERO, 0, false));
                allAvailable = false;
                if (unavailableReason == null) {
                    unavailableReason = "商品不可下单";
                }
                continue;
            }
            BigDecimal lineAmount = goods.price().multiply(BigDecimal.valueOf(line.quantity()));
            boolean available = goods.stock() >= line.quantity();
            lines.add(new OrderDtos.OrderPreviewLine(
                    goods.id(), goods.sku(), goods.name(), goods.price(),
                    line.quantity(), lineAmount, goods.stock(), available));
            if (available) {
                total = total.add(lineAmount);
            } else {
                allAvailable = false;
                if (unavailableReason == null) {
                    unavailableReason = "库存不足";
                }
            }
        }
        return new OrderDtos.OrderPreviewResult(lines, total, allAvailable, unavailableReason);
    }

    /**
     * 下单：幂等优先；按 goodsId 排序顺序扣库存（单事务原子回滚），落订单头/行项，
     * 审计 + 通知买家。返回订单归属视角的视图（解密手机/地址）。
     */
    @Transactional
    public OrderDtos.OrderView create(OrderDtos.OrderCreateRequest req, String userId, String idemKey) {
        if (StringUtils.hasText(idemKey)) {
            OrderRepository.OrderRow existing = repository.findByIdempotency(userId, idemKey).orElse(null);
            if (existing != null) {
                return ownerView(existing, true);
            }
        }
        List<OrderDtos.OrderLineRequest> sorted = new ArrayList<>(req.lines());
        sorted.sort(Comparator.comparing(OrderDtos.OrderLineRequest::goodsId));

        List<OrderRepository.OrderItemRow> itemRows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String orderId = UUID.randomUUID().toString();
        for (OrderDtos.OrderLineRequest line : sorted) {
            int affected = repository.deductStock(line.goodsId(), line.quantity());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.ORDER_STOCK_001);
            }
            OrderRepository.GoodsSnapshot snapshot = repository.findActiveGoodsForOrder(line.goodsId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND_001));
            BigDecimal lineAmount = snapshot.price().multiply(BigDecimal.valueOf(line.quantity()));
            total = total.add(lineAmount);
            itemRows.add(new OrderRepository.OrderItemRow(
                    UUID.randomUUID().toString(), orderId, snapshot.id(),
                    snapshot.sku(), snapshot.name(), snapshot.price(),
                    line.quantity(), lineAmount));
        }

        String orderNo = generateOrderNo();
        String phoneCiphertext = phoneCrypto.encrypt(req.recipientPhone());
        String addressCiphertext = phoneCrypto.encrypt(req.address());
        repository.insertHeader(orderId, orderNo, userId, total, req.recipientName(),
                phoneCiphertext, addressCiphertext, idemKey);
        repository.insertItems(itemRows);

        auditService.recordSuccess(audit(userId, "user", "create_order", orderId));
        notificationService.send(userId, "ORDER_CREATED", "订单已创建",
                "您的订单 " + orderNo + " 已创建", "ORDER", orderId);

        return ownerView(repository.findHeaderById(orderId).orElseThrow(), true);
    }

    /** 归属用户或管理员查单：归属校验后解密手机/地址。 */
    public OrderDtos.OrderView getForUser(String id, String userId, boolean isAdmin) {
        OrderRepository.OrderRow row = loadHeader(id);
        ensureOwnership(row, userId, isAdmin);
        return ownerView(row, true);
    }

    private PageResult<OrderDtos.OrderView> decryptedPage(PageResult<OrderDtos.OrderView> page) {
        List<OrderDtos.OrderView> decrypted = page.getItems().stream()
                .map(this::decryptView)
                .toList();
        return new PageResult<>(decrypted, page.getPage(), page.getSize(), page.getTotal());
    }

    /** 用户订单列表。 */
    public PageResult<OrderDtos.OrderView> listForUser(String userId, OrderDtos.OrderQuery q) {
        return decryptedPage(repository.findByUser(userId, q));
    }

    /** 管理员订单列表。 */
    public PageResult<OrderDtos.OrderView> listAdmin(OrderDtos.AdminOrderQuery q) {
        return decryptedPage(repository.findAdmin(q));
    }

    /**
     * 取消订单：归属/管理员可发起。状态机 CREATED/PROCESSING→CANCELLED，库存一次性回补
     * （状态前置保证只回补一次）。失败时区分版本冲突与状态非法。
     */
    @Transactional
    public OrderDtos.OrderView cancel(String id, OrderDtos.OrderCancelRequest req,
            String userId, boolean isAdmin) {
        OrderRepository.OrderRow row = loadHeader(id);
        ensureOwnership(row, userId, isAdmin);
        int affected = repository.cancel(id, req.reason(), req.version());
        if (affected == 0) {
            // 取消失败时优先判定状态非法（非 CREATED/PROCESSING），再判定版本冲突，
            // 与错误码语义对齐：状态不可转优先于版本陈旧。
            OrderRepository.OrderRow current = loadHeader(id);
            if (!"CREATED".equals(current.status()) && !"PROCESSING".equals(current.status())) {
                throw new BusinessException(ErrorCode.ORDER_STATE_001);
            }
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ORDER_STATE_001);
        }
        for (OrderRepository.OrderItemRow item : repository.findItems(id)) {
            repository.restoreStock(item.goodsId(), item.quantity());
        }
        auditService.recordSuccess(audit(userId, isAdmin ? "operator" : "user", "cancel_order", id));
        notificationService.send(row.userId(), "ORDER_CANCELLED", "订单已取消",
                "您的订单 " + row.orderNo() + " 已取消", "ORDER", id);
        return ownerView(loadHeader(id), true);
    }

    /**
     * 状态流转（管理员）：CREATED→PROCESSING 或 PROCESSING→COMPLETED。
     * 失败时区分版本冲突与状态非法。
     */
    @Transactional
    public OrderDtos.OrderView transition(String id, OrderDtos.OrderTransitionRequest req, String operatorId) {
        OrderRepository.OrderRow row = loadHeader(id);
        boolean legal;
        int affected;
        if ("PROCESSING".equals(req.status())) {
            legal = "CREATED".equals(row.status());
            affected = repository.transitionToProcessing(id, req.note(), req.version());
        } else if ("COMPLETED".equals(req.status())) {
            legal = "PROCESSING".equals(row.status());
            affected = repository.transitionToCompleted(id, req.note(), req.version());
        } else {
            throw new BusinessException(ErrorCode.ORDER_STATE_001);
        }
        if (affected == 0) {
            OrderRepository.OrderRow current = loadHeader(id);
            if (current.version() != req.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.ORDER_STATE_001);
        }
        if (!legal) {
            throw new BusinessException(ErrorCode.ORDER_STATE_001);
        }
        auditService.recordSuccess(audit(operatorId, "operator", "transition_order", id));
        notificationService.send(row.userId(), "ORDER_TRANSITION", "订单状态更新",
                "您的订单 " + row.orderNo() + " 已更新为 " + req.status(), "ORDER", id);
        return ownerView(loadHeader(id), true);
    }

    private OrderRepository.OrderRow loadHeader(String id) {
        return repository.findHeaderById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND_001));
    }

    private void ensureOwnership(OrderRepository.OrderRow row, String userId, boolean isAdmin) {
        if (!isAdmin && !row.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
    }

    private OrderDtos.OrderView ownerView(OrderRepository.OrderRow row, boolean decrypt) {
        List<OrderDtos.OrderItemView> items = repository.findItems(row.id()).stream()
                .map(it -> new OrderDtos.OrderItemView(
                        it.id(), it.goodsId(), it.sku(), it.name(),
                        it.unitPrice(), it.quantity(), it.lineAmount()))
                .toList();
        return new OrderDtos.OrderView(
                row.id(),
                row.orderNo(),
                row.userId(),
                row.status(),
                row.totalAmount(),
                row.recipientName(),
                decrypt ? phoneCrypto.decrypt(row.recipientPhoneCiphertext()) : null,
                decrypt ? phoneCrypto.decrypt(row.addressCiphertext()) : null,
                row.cancelReason(),
                row.createdAt(),
                row.cancelledAt(),
                row.fulfilledAt(),
                row.version(),
                items);
    }

    private OrderDtos.OrderView decryptView(OrderDtos.OrderView view) {
        if (view == null) {
            return null;
        }
        OrderRepository.OrderRow row = repository.findHeaderById(view.id()).orElse(null);
        if (row == null) {
            return view;
        }
        return new OrderDtos.OrderView(
                view.id(),
                view.orderNo(),
                view.userId(),
                view.status(),
                view.totalAmount(),
                view.recipientName(),
                phoneCrypto.decrypt(row.recipientPhoneCiphertext()),
                phoneCrypto.decrypt(row.addressCiphertext()),
                view.cancelReason(),
                view.createdAt(),
                view.cancelledAt(),
                view.fulfilledAt(),
                view.version(),
                view.items());
    }

    private String generateOrderNo() {
        long ts = System.currentTimeMillis();
        int rand = UUID.randomUUID().hashCode() & 0xffff;
        return "ORD-" + ts + "-" + String.format("%04x", rand);
    }

    private AuditContext audit(String actorId, String role, String action, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType("order")
                .objectId(objectId)
                .build();
    }
}
