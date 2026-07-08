package com.petspark.order;

import com.petspark.common.api.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单模块接口 DTO。包括预览/创建请求、订单与订单项视图、状态变更请求与分页查询。
 *
 * <p>订单视图中的 {@code recipientPhone}/{@code address} 为服务端解密后的明文，
 * 仅在订单归属用户或具备 {@code order:manage} 权限的管理员可见；其他调用方在
 * 服务层就被归属校验拦截，不会拿到解密内容。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /** 订单行项视图（含下单时的商品快照：sku、name、单价、数量、行金额）。 */
    public record OrderItemView(
            String id,
            String goodsId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount) {
    }

    /** 订单视图。phone/address 已解密，仅在归属用户或管理员可见。 */
    public record OrderView(
            String id,
            String orderNo,
            String userId,
            String status,
            BigDecimal totalAmount,
            String recipientName,
            String recipientPhone,
            String address,
            String cancelReason,
            Instant createdAt,
            Instant cancelledAt,
            Instant fulfilledAt,
            int version,
            List<OrderItemView> items) {
    }

    /** 下单/预览行请求。goodsId 必填，数量 1~99。 */
    public record OrderLineRequest(
            @NotBlank String goodsId,
            @Min(1) @Max(99) int quantity) {
    }

    /** 订单预览请求：1~20 个行项。预览不扣库存、不落库。 */
    public record OrderPreviewRequest(
            @Valid @NotNull @Size(min = 1, max = 20) List<OrderLineRequest> lines) {
    }

    /** 下单请求：行项 + 收货人信息（姓名明文，手机/地址在服务端加密落库）。 */
    public record OrderCreateRequest(
            @Valid @NotNull @Size(min = 1, max = 20) List<OrderLineRequest> lines,
            @NotBlank @Size(max = 64) String recipientName,
            @NotBlank @Size(max = 32) String recipientPhone,
            @NotBlank @Size(max = 500) String address) {
    }

    /** 预览行结果：商品快照、可用库存、是否可下单。 */
    public record OrderPreviewLine(
            String goodsId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount,
            int availableStock,
            boolean available) {
    }

    /** 预览结果：各行走查 + 总额 + 是否整体可下单。 */
    public record OrderPreviewResult(
            List<OrderPreviewLine> lines,
            BigDecimal totalAmount,
            boolean available,
            String unavailableReason) {
    }

    /** 取消订单请求：原因 + 当前版本（乐观锁）。 */
    public record OrderCancelRequest(
            @NotBlank @Size(max = 255) String reason,
            @NotNull Integer version) {
    }

    /** 管理员状态流转请求：目标状态仅允许 PROCESSING/COMPLETED，附带备注与版本。 */
    public record OrderTransitionRequest(
            @NotBlank @Pattern(regexp = "PROCESSING|COMPLETED") String status,
            @Size(max = 255) String note,
            @NotNull Integer version) {
    }

    /** 用户订单列表查询：状态过滤 + 分页。 */
    public static class OrderQuery extends PageQuery {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 管理员订单列表查询：状态 + 订单号模糊 + 分页。 */
    public static class AdminOrderQuery extends PageQuery {
        private String status;
        private String keyword;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }
}
