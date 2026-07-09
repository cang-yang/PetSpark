package com.petspark.boarding;

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
import java.time.LocalDate;
import java.util.List;

/**
 * 寄养模块接口 DTO。覆盖房间资源 CRUD、日期容量查询、预约创建/取消/履约、照护档案。
 *
 * <p>状态机：{@code PENDING_CONFIRMATION → CONFIRMED → IN_SERVICE → COMPLETED}；
 * 确认前可 {@code REJECTED}；允许阶段可 {@code CANCELLED}；{@code IN_SERVICE} 可
 * {@code TERMINATED}。只有 {@code CONFIRMED/IN_SERVICE} 占用房间容量，占用切换在
 * 服务层同一事务内完成。
 *
 * <p>照护档案 {@link CareProfileView} 的敏感字段（紧急联系人、喂养/用药/行为/疫苗等）
 * 仅在调用方为宠物主人或具备 {@code boarding:fulfill}/{@code boarding:manage} 角色
 * 时返回，否则返回 {@code null}。
 */
public final class BoardingDtos {

    private BoardingDtos() {
    }

    // ---------- 房间资源 ----------

    /** 房间视图（含当日占用上下文，capacity/reserved）。 */
    public record RoomView(
            String id,
            String code,
            String name,
            int capacity,
            String status,
            String description,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 房间管理创建/更新请求。capacity >= 1。 */
    public record RoomSaveRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) @Max(9999) int capacity,
            @Size(max = 500) String description,
            @NotNull Integer version) {
    }

    /** 房间查询：状态过滤 + 分页。 */
    public static class RoomQuery extends PageQuery {
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

    // ---------- 日期容量查询 ----------

    /** 可用性查询：日期范围 + 宠物（验归属）→ 房间可用摘要。 */
    public record AvailabilityRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank String petId) {
    }

    /** 单房间某日期范围的可用摘要。availableCount 为可用余量，open 为是否仍有空位。 */
    public record RoomAvailabilityView(
            String roomId,
            String roomCode,
            String roomName,
            int capacity,
            int availableCount,
            boolean open) {
    }

    // ---------- 预约 ----------

    /** 预约视图。careProfile 视调用方授权裁剪。 */
    public record BookingView(
            String id,
            String bookingNo,
            String userId,
            String petId,
            String petName,
            String roomId,
            String roomName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            BigDecimal quotedAmount,
            String cancelReason,
            String rejectReason,
            String handlerId,
            String handlerNote,
            CareProfileView careProfile,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            String terminatedReason,
            int version) {
    }

    /** 照护档案视图。敏感字段仅在宠物主人或具备 boarding:fulfill/manage 角色时返回。 */
    public record CareProfileView(
            String id,
            String vaccinationSummary,
            String behaviorNotes,
            String feedingPlan,
            String medicationPlan,
            String emergencyContact,
            String emergencyAuthorization,
            String accessScope) {
    }

    /** 创建预约请求：宠物、日期、照护档案。 */
    public record BookingCreateRequest(
            @NotBlank String petId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Valid CareProfileRequest careProfile,
            BigDecimal quotedAmount,
            String idempotencyKey) {
    }

    /** 照护档案子请求。emergencyContact 为紧急联系人电话（加密落库）。 */
    public record CareProfileRequest(
            @Size(max = 500) String vaccinationSummary,
            @Size(max = 500) String behaviorNotes,
            @Size(max = 500) String feedingPlan,
            @Size(max = 500) String medicationPlan,
            @Size(max = 32) String emergencyContact,
            @Size(max = 32) String emergencyAuthorization) {
    }

    /** 取消预约请求：原因 + 当前版本（乐观锁）。 */
    public record BookingCancelRequest(
            @NotBlank @Size(max = 255) String reason,
            @NotNull Integer version) {
    }

    /** 我的预约列表查询：状态过滤 + 分页。 */
    public static class MyBookingQuery extends PageQuery {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 后台预约列表查询：状态 + 房间 + 预约号模糊 + 分页。 */
    public static class AdminBookingQuery extends PageQuery {
        private String status;
        private String roomId;
        private String keyword;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }

    /** 后台分配房间请求：房间 + 备注 + 版本。 */
    public record AssignRoomRequest(
            @NotBlank String roomId,
            @Size(max = 255) String note,
            @NotNull Integer version) {
    }

    /** 后台状态流转请求：目标状态仅允许 CONFIRMED/IN_SERVICE/COMPLETED/REJECTED/TERMINATED。 */
    public record BookingTransitionRequest(
            @NotBlank @Pattern(regexp = "CONFIRMED|IN_SERVICE|COMPLETED|REJECTED|TERMINATED") String status,
            @Size(max = 255) String note,
            @Size(max = 255) String reason,
            @NotNull Integer version) {
    }
}
