package com.petspark.adoption;

import com.petspark.common.api.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 领养申请接口 DTO。涵盖申请提交、本人查询/撤回、管理员审核与交接闭环
 * （API-ADOPT-001~008）。
 *
 * <p>视图 {@link ApplicationView} 携带 {@code statusLabel}、{@code nextStep} 等
 * 状态面板信息（NFR-UX-001），由服务层根据当前状态与操作者角色组装。
 */
public final class AdoptionDtos {

    private AdoptionDtos() {
    }

    /** 可领养宠物视图：地址/启事 + 责任主体 + 更新时间。 */
    public record AdoptablePetView(
            String id,
            String name,
            String species,
            String breedId,
            String breedName,
            String sex,
            String description,
            String ownershipType,
            String ownerUserId,
            String publicStatus,
            String adoptionStatus,
            Instant infoUpdatedAt,
            List<PetImageRef> images) {
    }

    public record PetImageRef(String fileId, int sortOrder, boolean coverFlag, String previewUrl) {
    }

    /** 申请视图。 */
    public record ApplicationView(
            String id,
            String applicationNo,
            String petId,
            PetSummary pet,
            String applicantUserId,
            String applicantName,
            String statement,
            String profileSnapshot,
            String status,
            String statusLabel,
            String statusClass,
            String role,
            String nextStep,
            String allowedActions,
            String reviewerUserId,
            String reviewerName,
            String reviewNote,
            Instant decidedAt,
            Instant withdrawnAt,
            String withdrawReason,
            String handoverNote,
            Instant handoverAt,
            Instant createdAt,
            int version) {
    }

    public record PetSummary(String id, String name, String species, String breedName) {
    }

    /** 提交申请请求（API-ADOPT-002）。 */
    public record ApplicationCreateRequest(
            @NotBlank @Size(max = 36) String petId,
            @NotBlank @Size(max = 1000) String statement,
            @Size(max = 500) String profileSnapshot) {
    }

    /** 撤回请求（API-ADOPT-004）。 */
    public record WithdrawRequest(
            @NotBlank @Size(max = 255) String reason,
            @NotNull Integer version) {
    }

    /** 审核请求（API-ADOPT-006）：通过/拒绝 + 理由 + 版本。 */
    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @Size(max = 500) String reason,
            @NotNull Integer version) {
    }

    /** 交接请求（API-ADOPT-007）：成功/失败 + 备注 + 版本。 */
    public record HandoverRequest(
            @NotBlank @Pattern(regexp = "SUCCESS|FAILURE") String outcome,
            @Size(max = 500) String note,
            @NotNull Integer version) {
    }

    /** 可领养宠物分页查询（API-ADOPT-001）：物种/品种关键词 + 分页。 */
    public static class AdoptablePetQuery extends PageQuery {
        private String keyword;
        private String species;
        private String breedId;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getSpecies() {
            return species;
        }

        public void setSpecies(String species) {
            this.species = species;
        }

        public String getBreedId() {
            return breedId;
        }

        public void setBreedId(String breedId) {
            this.breedId = breedId;
        }
    }

    /** 本人申请分页查询（API-ADOPT-003）：状态过滤 + 分页。 */
    public static class MyApplicationQuery extends PageQuery {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 管理员审核分页查询（API-ADOPT-005）：状态 + 关键词 + 分页。 */
    public static class AdminApplicationQuery extends PageQuery {
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
