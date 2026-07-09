package com.petspark.stray;

import com.petspark.common.api.PageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 流浪动物救助线索 DTO。MVP 覆盖：会员提交/查看本人线索，后台列表/详情/指派/状态流转/备注。
 */
public final class StrayDtos {

    private StrayDtos() {
    }

    public record ImageRef(String fileId, int sortOrder, String previewUrl) {
    }

    public record ClueView(
            String id,
            String clueNo,
            String reporterUserId,
            String reporterName,
            String animalType,
            String location,
            String description,
            String contactPhone,
            String status,
            String statusLabel,
            String statusClass,
            String nextStep,
            String assignedUserId,
            String assignedUserName,
            String adminNote,
            String handoffPetId,
            String handoffNote,
            List<ImageRef> images,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt,
            int version) {
    }

    public record ClueCreateRequest(
            @NotBlank @Pattern(regexp = "DOG|CAT|OTHER") String animalType,
            @NotBlank @Size(max = 255) String location,
            @NotBlank @Size(max = 1000) String description,
            @Size(max = 32) String contactPhone,
            @Size(max = 6) List<@Size(max = 36) String> imageFileIds) {
    }

    public record AssignRequest(
            @NotBlank @Size(max = 36) String assignedUserId,
            @Size(max = 500) String note,
            @NotNull Integer version) {
    }

    public record TransitionRequest(
            @NotBlank @Pattern(regexp = "IN_RESCUE|RESOLVED|CLOSED") String status,
            @Size(max = 500) String note,
            @Size(max = 36) String handoffPetId,
            @Size(max = 500) String handoffNote,
            @NotNull Integer version) {
    }

    public static class MyClueQuery extends PageQuery {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class AdminClueQuery extends PageQuery {
        private String status;
        private String keyword;
        private String assignedUserId;

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

        public String getAssignedUserId() {
            return assignedUserId;
        }

        public void setAssignedUserId(String assignedUserId) {
            this.assignedUserId = assignedUserId;
        }
    }
}
