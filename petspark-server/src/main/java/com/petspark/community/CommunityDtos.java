package com.petspark.community;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.petspark.common.api.PageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Community content DTOs for post list/detail, comments, interactions, and moderation. */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    @JsonPropertyOrder({"id","authorUserId","authorName","title","content","status","moderationReason",
            "likeCount","favoriteCount","commentCount","liked","favorited","version","createdAt","updatedAt"})
    public record PostView(
            String id,
            String authorUserId,
            String authorName,
            String title,
            String content,
            String status,
            String moderationReason,
            int likeCount,
            int favoriteCount,
            int commentCount,
            boolean liked,
            boolean favorited,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    @JsonPropertyOrder({"id","postId","parentId","authorUserId","authorName","content","status",
            "moderationReason","version","createdAt","updatedAt"})
    public record CommentView(
            String id,
            String postId,
            String parentId,
            String authorUserId,
            String authorName,
            String content,
            String status,
            String moderationReason,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PostCreateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 10000) String content) {
    }

    public record CommentCreateRequest(
            @Size(max = 36) String parentId,
            @NotBlank @Size(max = 1000) String content) {
    }

    public record ModerationRequest(
            @NotBlank @Pattern(regexp = "PUBLISHED|HIDDEN") String status,
            @Size(max = 255) String reason,
            @NotNull Integer version) {
    }

    public static class PostQuery extends PageQuery {
        private String keyword;
        private String status;

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class MyPostQuery extends PageQuery {
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class CommentQuery extends PageQuery {
    }
}
