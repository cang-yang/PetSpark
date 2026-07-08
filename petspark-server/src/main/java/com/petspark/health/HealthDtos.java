package com.petspark.health;

import com.petspark.common.api.PageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 健康记录相关 DTO。{@code HealthRecordView.detail} 仅在调用方被授权时返回解密明文，
 * 否则返回 {@code null}（已清除或不允许查看）。
 */
class HealthQuery extends PageQuery {
    private String recordType;

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }
}

record HealthRecordView(String id, String petId, String recordType, LocalDate occurredOn, String summary,
                        String detail, String attachmentFileId, String attachmentUrl, String sourceRole,
                        String authorId, String authorName, String revisionOfId, String status,
                        Instant createdAt, int version) {}

record HealthRecordRequest(@NotBlank @Size(max = 32) String recordType,
                           @NotNull LocalDate occurredOn,
                           @NotBlank @Size(max = 200) String summary,
                           @Size(max = 4000) String detail,
                           String attachmentFileId,
                           String sourceRole) {}

record HealthRevisionRequest(@NotBlank @Size(max = 32) String recordType,
                             @NotNull LocalDate occurredOn,
                             @NotBlank @Size(max = 200) String summary,
                             @Size(max = 4000) String detail,
                             String attachmentFileId,
                             String sourceRole) {}

record HealthEraseRequest(@NotBlank @Size(max = 200) String reason) {}
