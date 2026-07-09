package com.petspark.service;

import java.time.Instant;

/**
 * 服务模块领域 snapshot / row 记录载体。
 *
 * <p>纯不可变值对象，由 {@link ServiceBookingRepository} 在 JdbcTemplate 回填时构造，
 * 在 {@link ServiceBookingService} 中映射为对外 DTO。手机号以 ciphertext 落库，
 * 明文仅在归属用户或管理员视图下解密回填。
 */
final class ServiceRecords {

    private ServiceRecords() {
    }

    record ServiceItemRow(
            String id,
            String kind,
            String code,
            String name,
            String description,
            String qualification,
            String availabilityNote,
            String exceptionRule,
            java.math.BigDecimal basePrice,
            String status) {
    }

    record ServiceSpecificationRow(
            String id,
            String serviceItemId,
            String name,
            java.math.BigDecimal priceDelta,
            int sortOrder,
            String status) {
    }

    record ServiceResourceRow(
            String id,
            String serviceItemId,
            String name,
            String qualification,
            String availabilityNote,
            String exceptionRule,
            String status,
            int capacity) {
    }

    record ServiceSlotRow(
            String id,
            String resourceId,
            java.time.LocalDate slotDate,
            Instant startAt,
            Instant endAt,
            int capacity,
            int bookedCount,
            String status,
            int version) {
    }

    /**
     * 预约原始行（ciphertext 未解密）。version 用于乐观锁；status 为状态机当前态。
     */
    record ServiceBookingRow(
            String id,
            String bookingNo,
            String userId,
            String petId,
            String serviceItemId,
            String specificationId,
            String resourceId,
            String slotId,
            String kind,
            String status,
            Instant startAt,
            Instant endAt,
            java.math.BigDecimal unitPrice,
            String customerName,
            String customerPhoneCiphertext,
            String remark,
            String cancelReason,
            Instant cancelledAt,
            Instant fulfilledAt,
            String exceptionNote,
            int version,
            Instant createdAt) {
    }
}
