package com.petspark.community;

import java.time.Instant;

/** Internal community persistence records. */
public final class CommunityRecords {

    private CommunityRecords() {
    }

    public record PostRow(
            String id,
            String authorUserId,
            String title,
            String content,
            String status,
            String moderationReason,
            int likeCount,
            int favoriteCount,
            int commentCount,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CommentRow(
            String id,
            String postId,
            String parentId,
            String authorUserId,
            String content,
            String status,
            String moderationReason,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
