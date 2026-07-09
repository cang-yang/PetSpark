package com.petspark.service;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
 * 服务模块接口 DTO。覆盖服务项目浏览/详情、服务资源浏览、可用窗口查询/预约、
 * 我的预约、取消、履约/状态以及后台管理（API-SVC-001~013）。
 *
 * <p>预约视图中的 {@code customerPhone} 为服务端解密后的明文，仅在预约归属用户或
 * 具备 {@code service:manage} 权限的管理员可见；其他调用方在服务层就被归属校验拦截。
 */
public final class ServiceDtos {

    private ServiceDtos() {
    }

    /** 服务项目视图（含透明度字段）。 */
    public record ServiceItemView(
            String id,
            String kind,
            String code,
            String name,
            String description,
            String qualification,
            String availabilityNote,
            String exceptionRule,
            BigDecimal basePrice,
            String status,
            List<ServiceSpecificationView> specifications,
            BeautyProfileView beautyProfile,
            MedicalProfileView medicalProfile) {
    }

    /** 美容服务规则视图，仅 kind=BEAUTY 的服务项目返回。 */
    public record BeautyProfileView(
            String id,
            String serviceItemId,
            String supportedPetTypes,
            String coatTypes,
            String sizeRanges,
            String carePreferences,
            String cautionNotes) {
    }

    /** 医疗服务规则视图，仅 kind=MEDICAL 的服务项目返回。 */
    public record MedicalProfileView(
            String id,
            String serviceItemId,
            String clinicLicense,
            String veterinarianTeam,
            String supportedPetTypes,
            String careScope,
            String appointmentNotice,
            String emergencyRule) {
    }

    /** 服务规格视图。 */
    public record ServiceSpecificationView(
            String id,
            String name,
            BigDecimal priceDelta,
            int sortOrder,
            String status) {
    }

    /** 服务资源视图（含透明度字段）。 */
    public record ServiceResourceView(
            String id,
            String serviceItemId,
            String name,
            String qualification,
            String availabilityNote,
            String exceptionRule,
            String status,
            int capacity) {
    }

    /** 可用窗口视图（含已预约计数）。 */
    public record ServiceSlotView(
            String id,
            String resourceId,
            String slotDate,
            Instant startAt,
            Instant endAt,
            int capacity,
            int bookedCount,
            String status) {
    }

    /** 预约视图。customerPhone 已解密，仅在归属用户或管理员可见。 */
    @JsonPropertyOrder({"id","bookingNo","userId","petId","serviceItemId","specificationId",
            "resourceId","slotId","kind","status","startAt","endAt","unitPrice",
            "customerName","customerPhone","remark","cancelReason","cancelledAt",
            "fulfilledAt","exceptionNote","version","createdAt","serviceItemName",
            "resourceName","specificationName"})
    public record ServiceBookingView(
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
            BigDecimal unitPrice,
            String customerName,
            String customerPhone,
            String remark,
            String cancelReason,
            Instant cancelledAt,
            Instant fulfilledAt,
            String exceptionNote,
            int version,
            Instant createdAt,
            String serviceItemName,
            String resourceName,
            String specificationName) {
    }

    /** 创建预约请求：选择资源/窗口/规格，关联宠物，提供联系方式。 */
    public record ServiceBookingCreateRequest(
            @NotBlank String serviceItemId,
            String specificationId,
            @NotBlank String resourceId,
            @NotBlank String slotId,
            String petId,
            @NotBlank @Size(max = 64) String customerName,
            @NotBlank @Size(max = 32) String customerPhone,
            @Size(max = 500) String remark) {
    }

    /** 取消预约请求：原因 + 当前版本（乐观锁）。 */
    public record ServiceBookingCancelRequest(
            @NotBlank @Size(max = 255) String reason,
            @NotNull Integer version) {
    }

    /** 异常终止请求：异常说明 + 当前版本（乐观锁）。 */
    public record ServiceBookingExceptionRequest(
            @NotBlank @Size(max = 255) String note,
            @NotNull Integer version) {
    }

    /** 履约状态流转请求：目标状态允许 IN_PROGRESS/COMPLETED，附带备注与版本。 */
    public record ServiceBookingTransitionRequest(
            @NotBlank @Pattern(regexp = "IN_PROGRESS|COMPLETED") String status,
            @Size(max = 255) String note,
            @NotNull Integer version) {
    }

    /** 后台服务项目创建/更新请求。 */
    public record ServiceItemUpsertRequest(
            @NotBlank @Pattern(regexp = "GENERIC|TRAINING|BEAUTY|MEDICAL") String kind,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 500) String qualification,
            @Size(max = 500) String availabilityNote,
            @Size(max = 500) String exceptionRule,
            BigDecimal basePrice,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    /** 后台服务资源创建/更新请求。 */
    public record ServiceResourceUpsertRequest(
            @NotBlank String serviceItemId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String qualification,
            @Size(max = 500) String availabilityNote,
            @Size(max = 500) String exceptionRule,
            @Min(1) @Max(100) Integer capacity,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    /** 后台窗口创建请求（批量）。slotDate/startAt/endAt/capacity 为单窗口快捷入参，
     *  与 {@code slots} 数组二选一：提供非空 slots 时按批量生成，忽略单窗口字段。
     *  resourceId 仍必填，由服务层校验资源存在性。 */
    public record ServiceSlotCreateRequest(
            @NotBlank String resourceId,
            String slotDate,
            String startAt,
            String endAt,
            @Min(1) @Max(100) Integer capacity,
            @Valid List<ServiceSlotRange> slots) {
    }

    /** 窗口时间段范围（用于批量生成窗口）。 */
    public record ServiceSlotRange(
            @NotBlank String startAt,
            @NotBlank String endAt,
            @Min(1) @Max(100) Integer capacity) {
    }

    /** 服务项目列表查询：kind + 状态 + 关键字 + 分页。 */
    public static class ServiceItemQuery extends PageQuery {
        private String kind;
        private String status;
        private String keyword;

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
    }

    /** 服务资源列表查询。 */
    public static class ServiceResourceQuery extends PageQuery {
        private String serviceItemId;
        private String status;

        public String getServiceItemId() { return serviceItemId; }
        public void setServiceItemId(String serviceItemId) { this.serviceItemId = serviceItemId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 可用窗口查询：资源 + 日期。 */
    public static class ServiceSlotQuery extends PageQuery {
        private String resourceId;
        private String slotDate;

        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getSlotDate() { return slotDate; }
        public void setSlotDate(String slotDate) { this.slotDate = slotDate; }
    }

    /** 我的预约查询：状态/kind 过滤 + 分页。 */
    public static class MyBookingQuery extends PageQuery {
        private String status;
        private String kind;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
    }

    /** 管理员预约查询：状态 + kind + 订单号模糊 + 分页。 */
    public static class AdminBookingQuery extends PageQuery {
        private String status;
        private String kind;
        private String keyword;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
    }
}
