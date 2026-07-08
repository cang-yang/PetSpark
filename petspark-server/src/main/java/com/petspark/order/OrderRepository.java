package com.petspark.order;

import com.petspark.common.api.PageResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 订单持久化仓储。基于 JdbcTemplate，与 catalog/pet 模块同风格。
 *
 * <p>关键点：
 * <ul>
 *   <li>库存扣减 {@link #deductStock} 用条件 UPDATE（{@code stock >= ?}）保证原子性与不超卖，
 *       返回影响行数 0 即库存不足或商品不可下单；</li>
 *   <li>库存回补 {@link #restoreStock} 用于取消订单时一次性归还；</li>
 *   <li>取消/流转 SQL 在 WHERE 中带状态前置条件，配合乐观锁 version，确保状态机合法；</li>
 *   <li>幂等：{@code uk_order_idem(user_id, idempotency_key)} 唯一索引，NULL key 多行兼容。</li>
 * </ul>
 */
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 商品下单快照（仅 ACTIVE 商品可下单）。 */
    public record GoodsSnapshot(
            String id,
            String sku,
            String name,
            BigDecimal price,
            int stock,
            String status) {
    }

    /** 订单头原始行（ciphertext 未解密）。 */
    public record OrderRow(
            String id,
            String orderNo,
            String userId,
            String status,
            BigDecimal totalAmount,
            String recipientName,
            String recipientPhoneCiphertext,
            String addressCiphertext,
            String cancelReason,
            Instant createdAt,
            Instant cancelledAt,
            Instant fulfilledAt,
            int version) {
    }

    /** 订单行项原始行。 */
    public record OrderItemRow(
            String id,
            String orderId,
            String goodsId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount) {
    }

    /** 查询 ACTIVE 商品快照（下单/预览用）。 */
    public Optional<GoodsSnapshot> findActiveGoodsForOrder(String goodsId) {
        return jdbcTemplate.query("""
                SELECT id, sku, name, price, stock, status
                FROM goods
                WHERE id = ? AND deleted_at IS NULL AND status = 'ACTIVE'
                """, rs -> rs.next() ? Optional.of(new GoodsSnapshot(
                rs.getString("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("status"))) : Optional.empty(), goodsId);
    }

    /** 扣减库存：条件 UPDATE 保证不超卖。返回影响行数（0=库存不足或商品非 ACTIVE）。 */
    public int deductStock(String goodsId, int quantity) {
        return jdbcTemplate.update("""
                UPDATE goods
                SET stock = stock - ?, version = version + 1
                WHERE id = ? AND deleted_at IS NULL AND status = 'ACTIVE' AND stock >= ?
                """, quantity, goodsId, quantity);
    }

    /** 回补库存（取消订单时一次性归还）。 */
    public int restoreStock(String goodsId, int quantity) {
        return jdbcTemplate.update("""
                UPDATE goods
                SET stock = stock + ?, version = version + 1
                WHERE id = ? AND deleted_at IS NULL
                """, quantity, goodsId);
    }

    /** 插入订单头。 */
    public void insertHeader(String id, String orderNo, String userId, BigDecimal totalAmount,
            String recipientName, String phoneCiphertext, String addressCiphertext,
            String idempotencyKey) {
        jdbcTemplate.update("""
                INSERT INTO order_header
                    (id, order_no, user_id, status, total_amount, recipient_name,
                     recipient_phone_ciphertext, address_ciphertext, idempotency_key, version)
                VALUES (?, ?, ?, 'CREATED', ?, ?, ?, ?, ?, 0)
                """, id, orderNo, userId, totalAmount, recipientName, phoneCiphertext,
                addressCiphertext, StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
    }

    /** 插入订单行项。 */
    public void insertItems(List<OrderItemRow> items) {
        for (OrderItemRow item : items) {
            jdbcTemplate.update("""
                    INSERT INTO order_item
                        (id, order_id, goods_id, sku_snapshot, name_snapshot, unit_price, quantity, line_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, item.id(), item.orderId(), item.goodsId(), item.sku(), item.name(),
                    item.unitPrice(), item.quantity(), item.lineAmount());
        }
    }

    /** 按订单 id 加载订单头（含 ciphertext，供归属/状态校验）。 */
    public Optional<OrderRow> findHeaderById(String id) {
        return jdbcTemplate.query("""
                SELECT id, order_no, user_id, status, total_amount, recipient_name,
                       recipient_phone_ciphertext, address_ciphertext, cancel_reason,
                       cancelled_at, fulfilled_at, version, created_at
                FROM order_header
                WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapHeader(rs)) : Optional.empty(), id);
    }

    /** 按幂等键加载订单头（同一 user_id + idempotency_key 命中即幂等重放）。 */
    public Optional<OrderRow> findByIdempotency(String userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, order_no, user_id, status, total_amount, recipient_name,
                       recipient_phone_ciphertext, address_ciphertext, cancel_reason,
                       cancelled_at, fulfilled_at, version, created_at
                FROM order_header
                WHERE user_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(mapHeader(rs)) : Optional.empty(),
                userId, idempotencyKey);
    }

    /** 加载订单行项。 */
    public List<OrderItemRow> findItems(String orderId) {
        return jdbcTemplate.query("""
                SELECT id, order_id, goods_id, sku_snapshot, name_snapshot, unit_price, quantity, line_amount
                FROM order_item
                WHERE order_id = ?
                ORDER BY sku_snapshot ASC
                """, (rs, rowNum) -> new OrderItemRow(
                rs.getString("id"),
                rs.getString("order_id"),
                rs.getString("goods_id"),
                rs.getString("sku_snapshot"),
                rs.getString("name_snapshot"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_amount")), orderId);
    }

    /** 用户订单列表（状态过滤 + 分页，按 created_at DESC）。 */
    public PageResult<OrderDtos.OrderView> findByUser(String userId, OrderDtos.OrderQuery q) {
        StringBuilder where = new StringBuilder(" WHERE user_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_header" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<OrderDtos.OrderView> items = jdbcTemplate.query("""
                SELECT id FROM order_header %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where),
                (rs, rowNum) -> loadView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 管理员订单列表（状态 + 订单号模糊 + 分页）。 */
    public PageResult<OrderDtos.OrderView> findAdmin(OrderDtos.AdminOrderQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND order_no LIKE ? ");
            args.add("%" + q.getKeyword().trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_header" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<OrderDtos.OrderView> items = jdbcTemplate.query("""
                SELECT id FROM order_header %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where),
                (rs, rowNum) -> loadView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 取消订单：状态前置 CREATED/PROCESSING，乐观锁。返回影响行数。 */
    public int cancel(String id, String reason, int version) {
        return jdbcTemplate.update("""
                UPDATE order_header
                SET status = 'CANCELLED', cancel_reason = ?, cancelled_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND status IN ('CREATED', 'PROCESSING')
                """, reason, id, version);
    }

    /** 状态流转：仅 CREATED→PROCESSING 或 PROCESSING→COMPLETED。 */
    public int transitionToProcessing(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE order_header
                SET status = 'PROCESSING', transition_note = ?, version = version + 1
                WHERE id = ? AND version = ? AND status = 'CREATED'
                """, note, id, version);
    }

    /** 状态流转：PROCESSING→COMPLETED，落 fulfilled_at。 */
    public int transitionToCompleted(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE order_header
                SET status = 'COMPLETED', transition_note = ?, fulfilled_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND status = 'PROCESSING'
                """, note, id, version);
    }

    private OrderDtos.OrderView loadView(String id) {
        return findHeaderById(id).map(row -> new OrderDtos.OrderView(
                row.id(),
                row.orderNo(),
                row.userId(),
                row.status(),
                row.totalAmount(),
                row.recipientName(),
                row.recipientPhoneCiphertext(),
                row.addressCiphertext(),
                row.cancelReason(),
                row.createdAt(),
                row.cancelledAt(),
                row.fulfilledAt(),
                row.version(),
                findItems(id).stream()
                        .map(it -> new OrderDtos.OrderItemView(
                                it.id(), it.goodsId(), it.sku(), it.name(),
                                it.unitPrice(), it.quantity(), it.lineAmount()))
                        .toList()))
                .orElse(null);
    }

    private OrderRow mapHeader(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        Timestamp fulfilled = rs.getTimestamp("fulfilled_at");
        return new OrderRow(
                rs.getString("id"),
                rs.getString("order_no"),
                rs.getString("user_id"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("recipient_name"),
                rs.getString("recipient_phone_ciphertext"),
                rs.getString("address_ciphertext"),
                rs.getString("cancel_reason"),
                created == null ? null : created.toInstant(),
                cancelled == null ? null : cancelled.toInstant(),
                fulfilled == null ? null : fulfilled.toInstant(),
                rs.getInt("version"));
    }
}
