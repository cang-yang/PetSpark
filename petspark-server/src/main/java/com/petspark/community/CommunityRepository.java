package com.petspark.community;

import com.petspark.common.api.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JdbcTemplate repository for community posts, comments, and post interactions. */
@Repository
public class CommunityRepository {

    private final JdbcTemplate jdbcTemplate;

    public CommunityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<CommunityDtos.PostView> findPublishedPosts(CommunityDtos.PostQuery q, String viewerId) {
        q.setStatus("PUBLISHED");
        return findPosts(q, viewerId, false, null);
    }

    public PageResult<CommunityDtos.PostView> findMyPosts(String authorId, CommunityDtos.MyPostQuery q) {
        CommunityDtos.PostQuery query = new CommunityDtos.PostQuery();
        query.setPage(q.getPage());
        query.setSize(q.getSize());
        query.setStatus(q.getStatus());
        return findPosts(query, authorId, true, authorId);
    }

    public PageResult<CommunityDtos.PostView> findAdminPosts(CommunityDtos.PostQuery q) {
        return findPosts(q, null, true, null);
    }

    private PageResult<CommunityDtos.PostView> findPosts(CommunityDtos.PostQuery q, String viewerId,
            boolean includeHidden, String authorId) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (!includeHidden) {
            where.append(" AND p.status = 'PUBLISHED' ");
        }
        if (StringUtils.hasText(authorId)) {
            where.append(" AND p.author_user_id = ? ");
            args.add(authorId);
        }
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND p.status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND (p.title LIKE ? OR p.content LIKE ?) ");
            String kw = "%" + q.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_post p" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<CommunityDtos.PostView> items = jdbcTemplate.query(
                "SELECT p.id FROM community_post p %s ORDER BY p.created_at DESC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadPostView(rs.getString("id"), viewerId, includeHidden), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    public Optional<CommunityRecords.PostRow> findPost(String id) {
        return jdbcTemplate.query("""
                SELECT id, author_user_id, title, content, status, moderation_reason,
                       like_count, favorite_count, comment_count, version, created_at, updated_at
                FROM community_post
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapPost(rs)) : Optional.empty(), id);
    }

    public CommunityDtos.PostView loadPostView(String id, String viewerId, boolean includeHidden) {
        Optional<CommunityRecords.PostRow> found = findPost(id);
        if (found.isEmpty()) {
            return null;
        }
        CommunityRecords.PostRow row = found.get();
        if (!includeHidden && !"PUBLISHED".equals(row.status())) {
            return null;
        }
        String authorName = authorName(row.authorUserId());
        boolean liked = hasInteraction(id, viewerId, "LIKE");
        boolean favorited = hasInteraction(id, viewerId, "FAVORITE");
        return new CommunityDtos.PostView(row.id(), row.authorUserId(), authorName, row.title(), row.content(),
                row.status(), row.moderationReason(), row.likeCount(), row.favoriteCount(), row.commentCount(),
                liked, favorited, row.version(), row.createdAt(), row.updatedAt());
    }

    public void insertPost(String id, String authorId, CommunityDtos.PostCreateRequest req) {
        jdbcTemplate.update("""
                INSERT INTO community_post (id, author_user_id, title, content, status, version)
                VALUES (?, ?, ?, ?, 'PUBLISHED', 0)
                """, id, authorId, req.title(), req.content());
    }

    public int updatePostStatus(String id, String status, String reason, String operatorId, int version) {
        return jdbcTemplate.update("""
                UPDATE community_post
                SET status = ?, moderation_reason = ?, moderated_by = ?, moderated_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, status, reason, operatorId, id, version);
    }

    public PageResult<CommunityDtos.CommentView> findComments(String postId, CommunityDtos.CommentQuery q,
            boolean includeHidden) {
        StringBuilder where = new StringBuilder(" WHERE c.post_id = ? AND c.deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        args.add(postId);
        if (!includeHidden) {
            where.append(" AND c.status = 'PUBLISHED' ");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_comment c" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<CommunityDtos.CommentView> items = jdbcTemplate.query(
                "SELECT c.id FROM community_comment c %s ORDER BY c.created_at ASC LIMIT ? OFFSET ?".formatted(where),
                (rs, rowNum) -> loadCommentView(rs.getString("id"), includeHidden), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    public Optional<CommunityRecords.CommentRow> findComment(String id) {
        return jdbcTemplate.query("""
                SELECT id, post_id, parent_id, author_user_id, content, status, moderation_reason,
                       version, created_at, updated_at
                FROM community_comment
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapComment(rs)) : Optional.empty(), id);
    }

    public CommunityDtos.CommentView loadCommentView(String id, boolean includeHidden) {
        Optional<CommunityRecords.CommentRow> found = findComment(id);
        if (found.isEmpty()) {
            return null;
        }
        CommunityRecords.CommentRow row = found.get();
        if (!includeHidden && !"PUBLISHED".equals(row.status())) {
            return null;
        }
        return new CommunityDtos.CommentView(row.id(), row.postId(), row.parentId(), row.authorUserId(),
                authorName(row.authorUserId()), row.content(), row.status(), row.moderationReason(),
                row.version(), row.createdAt(), row.updatedAt());
    }

    public void insertComment(String id, String postId, String parentId, String authorId,
            CommunityDtos.CommentCreateRequest req) {
        jdbcTemplate.update("""
                INSERT INTO community_comment (id, post_id, parent_id, author_user_id, content, status, version)
                VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 0)
                """, id, postId, parentId, authorId, req.content());
    }

    public int incrementCommentCount(String postId) {
        return jdbcTemplate.update("UPDATE community_post SET comment_count = comment_count + 1 WHERE id = ?", postId);
    }

    public int updateCommentStatus(String id, String status, String reason, String operatorId, int version) {
        return jdbcTemplate.update("""
                UPDATE community_comment
                SET status = ?, moderation_reason = ?, moderated_by = ?, moderated_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, status, reason, operatorId, id, version);
    }

    public boolean insertInteraction(String id, String postId, String userId, String type) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO community_interaction (id, post_id, user_id, type)
                VALUES (?, ?, ?, ?)
                """, id, postId, userId, type) > 0;
    }

    public boolean deleteInteraction(String postId, String userId, String type) {
        return jdbcTemplate.update("DELETE FROM community_interaction WHERE post_id = ? AND user_id = ? AND type = ?",
                postId, userId, type) > 0;
    }

    public void incrementInteractionCount(String postId, String type) {
        String column = "LIKE".equals(type) ? "like_count" : "favorite_count";
        jdbcTemplate.update("UPDATE community_post SET " + column + " = " + column + " + 1 WHERE id = ?", postId);
    }

    public void decrementInteractionCount(String postId, String type) {
        String column = "LIKE".equals(type) ? "like_count" : "favorite_count";
        jdbcTemplate.update("UPDATE community_post SET " + column + " = GREATEST(" + column + " - 1, 0) WHERE id = ?", postId);
    }

    private boolean hasInteraction(String postId, String viewerId, String type) {
        if (!StringUtils.hasText(viewerId)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM community_interaction
                WHERE post_id = ? AND user_id = ? AND type = ?
                """, Long.class, postId, viewerId, type);
        return count != null && count > 0;
    }

    private String authorName(String userId) {
        return jdbcTemplate.query("SELECT nickname FROM sys_user WHERE id = ?",
                rs -> rs.next() ? rs.getString("nickname") : null, userId);
    }

    private CommunityRecords.PostRow mapPost(ResultSet rs) throws SQLException {
        return new CommunityRecords.PostRow(
                rs.getString("id"),
                rs.getString("author_user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("moderation_reason"),
                rs.getInt("like_count"),
                rs.getInt("favorite_count"),
                rs.getInt("comment_count"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private CommunityRecords.CommentRow mapComment(ResultSet rs) throws SQLException {
        return new CommunityRecords.CommentRow(
                rs.getString("id"),
                rs.getString("post_id"),
                rs.getString("parent_id"),
                rs.getString("author_user_id"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("moderation_reason"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
