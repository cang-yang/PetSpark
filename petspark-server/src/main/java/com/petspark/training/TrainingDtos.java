package com.petspark.training;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.petspark.common.api.PageQuery;
import com.petspark.service.ServiceDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 训练服务薄封装 DTO：预约/履约复用 service_booking，训练申请内容独立落 training_application_detail。 */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    public record TrainingItemView(
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
            List<ServiceDtos.ServiceSpecificationView> specifications) {
        static TrainingItemView from(ServiceDtos.ServiceItemView item) {
            return new TrainingItemView(item.id(), item.kind(), item.code(), item.name(), item.description(),
                    item.qualification(), item.availabilityNote(), item.exceptionRule(), item.basePrice(),
                    item.status(), item.specifications());
        }
    }

    public record TrainingApplicationDetailView(
            String trainingGoal,
            String behaviorProblem,
            String intensity,
            String attentionNote) {
    }

    @JsonPropertyOrder({"id","bookingNo","userId","petId","serviceItemId","specificationId",
            "resourceId","slotId","kind","status","startAt","endAt","unitPrice",
            "customerName","customerPhone","remark","cancelReason","cancelledAt",
            "fulfilledAt","exceptionNote","version","createdAt","serviceItemName",
            "resourceName","specificationName","trainingDetail"})
    public record TrainingBookingView(
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
            String specificationName,
            TrainingApplicationDetailView trainingDetail) {
        static TrainingBookingView from(ServiceDtos.ServiceBookingView booking,
                TrainingApplicationDetailView detail) {
            return new TrainingBookingView(booking.id(), booking.bookingNo(), booking.userId(), booking.petId(),
                    booking.serviceItemId(), booking.specificationId(), booking.resourceId(), booking.slotId(),
                    booking.kind(), booking.status(), booking.startAt(), booking.endAt(), booking.unitPrice(),
                    booking.customerName(), booking.customerPhone(), booking.remark(), booking.cancelReason(),
                    booking.cancelledAt(), booking.fulfilledAt(), booking.exceptionNote(), booking.version(),
                    booking.createdAt(), booking.serviceItemName(), booking.resourceName(), booking.specificationName(),
                    detail);
        }
    }

    public record TrainingApplicationRequest(
            @NotBlank String serviceItemId,
            String specificationId,
            @NotBlank String resourceId,
            @NotBlank String slotId,
            String petId,
            @NotBlank @Size(max = 64) String customerName,
            @NotBlank @Size(max = 32) String customerPhone,
            @Size(max = 500) String remark,
            @NotBlank @Size(max = 500) String trainingGoal,
            @Size(max = 500) String behaviorProblem,
            @Pattern(regexp = "LOW|MEDIUM|HIGH") String intensity,
            @Size(max = 500) String attentionNote) {
        ServiceDtos.ServiceBookingCreateRequest toServiceRequest() {
            return new ServiceDtos.ServiceBookingCreateRequest(serviceItemId, specificationId, resourceId, slotId,
                    petId, customerName, customerPhone, remark);
        }
    }

    public static class TrainingItemQuery extends PageQuery {
        private String status;
        private String keyword;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
    }

    public static class TrainingBookingQuery extends PageQuery {
        private String status;
        private String keyword;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
    }
}
