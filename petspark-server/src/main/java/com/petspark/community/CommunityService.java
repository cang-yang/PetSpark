package com.petspark.community;

import com.petspark.audit.AuditContext;
import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.notification.NotificationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Application service for community content publishing, comments, interactions, and moderation. */
@Service
public class CommunityService {

    private static final String MODULE = "community";
    private static final String BIZ_TYPE = "COMMUNITY";

    private final CommunityRepository repository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public CommunityService(CommunityRepository repository,
            NotificationService notificationService,
            AuditService auditService) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public PageResult<CommunityDtos.PostView> listPublished(CommunityDtos.PostQuery query, String viewerId) {
        return repository.findPublishedPosts(query, viewerId);
    }

    public CommunityDtos.PostView getPublished(String id, String viewerId, boolean includeHidden) {
        CommunityDtos.PostView view = repository.loadPostView(id, viewerId, includeHidden);
        if (view == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND_001);
        }
        return view;
    }

    @Transactional
    public CommunityDtos.PostView createPost(CommunityDtos.PostCreateRequest request, String userId) {
        String id = UUID.randomUUID().toString();
        repository.insertPost(id, userId, request);
        auditService.recordSuccess(audit(userId, "user", "create_post", "community_post", id));
        return getPublished(id, userId, true);
    }

    public PageResult<CommunityDtos.PostView> listMy(String userId, CommunityDtos.MyPostQuery query) {
        return repository.findMyPosts(userId, query);
    }

    public PageResult<CommunityDtos.PostView> listAdmin(CommunityDtos.PostQuery query) {
        return repository.findAdminPosts(query);
    }

    @Transactional
    public CommunityDtos.CommentView createComment(String postId, CommunityDtos.CommentCreateRequest request,
            String userId) {
        CommunityRecords.PostRow post = loadVisiblePost(postId);
        String parentId = StringUtils.hasText(request.parentId()) ? request.parentId() : null;
        if (parentId != null) {
            CommunityRecords.CommentRow parent = repository.findComment(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND_001));
            if (!postId.equals(parent.postId()) || !"PUBLISHED".equals(parent.status())) {
                throw new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND_001);
            }
        }
        String id = UUID.randomUUID().toString();
        repository.insertComment(id, postId, parentId, userId, request);
        repository.incrementCommentCount(postId);
        if (!userId.equals(post.authorUserId())) {
            notificationService.send(post.authorUserId(), "COMMUNITY_COMMENT_CREATED", "社区帖子收到新评论",
                    "您的帖子《" + post.title() + "》收到新评论", BIZ_TYPE, postId);
        }
        auditService.recordSuccess(audit(userId, "user", "create_comment", "community_comment", id));
        return repository.loadCommentView(id, false);
    }

    public PageResult<CommunityDtos.CommentView> listComments(String postId, CommunityDtos.CommentQuery query,
            boolean includeHidden) {
        if (includeHidden) {
            loadPost(postId);
        } else {
            loadVisiblePost(postId);
        }
        return repository.findComments(postId, query, includeHidden);
    }

    @Transactional
    public CommunityDtos.PostView setInteraction(String postId, String userId, String type, boolean enabled) {
        loadVisiblePost(postId);
        boolean changed = enabled
                ? repository.insertInteraction(UUID.randomUUID().toString(), postId, userId, type)
                : repository.deleteInteraction(postId, userId, type);
        if (changed) {
            if (enabled) {
                repository.incrementInteractionCount(postId, type);
            } else {
                repository.decrementInteractionCount(postId, type);
            }
        }
        return getPublished(postId, userId, true);
    }

    @Transactional
    public CommunityDtos.PostView moderatePost(String id, CommunityDtos.ModerationRequest request,
            String operatorId) {
        CommunityRecords.PostRow row = loadPost(id);
        int affected = repository.updatePostStatus(id, request.status(), request.reason(), operatorId, request.version());
        if (affected == 0) {
            CommunityRecords.PostRow current = loadPost(id);
            if (current.version() != request.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.COMMUNITY_STATE_001);
        }
        auditService.recordSuccess(audit(operatorId, "operator", "moderate_post", "community_post", id));
        notificationService.send(row.authorUserId(), "COMMUNITY_POST_MODERATED", "社区帖子状态已更新",
                "您的帖子《" + row.title() + "》状态已更新为 " + request.status(), BIZ_TYPE, id);
        return getPublished(id, operatorId, true);
    }

    @Transactional
    public CommunityDtos.CommentView moderateComment(String id, CommunityDtos.ModerationRequest request,
            String operatorId) {
        CommunityRecords.CommentRow row = repository.findComment(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND_001));
        int affected = repository.updateCommentStatus(id, request.status(), request.reason(), operatorId, request.version());
        if (affected == 0) {
            CommunityRecords.CommentRow current = repository.findComment(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND_001));
            if (current.version() != request.version()) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
            }
            throw new BusinessException(ErrorCode.COMMUNITY_STATE_001);
        }
        auditService.recordSuccess(audit(operatorId, "operator", "moderate_comment", "community_comment", id));
        notificationService.send(row.authorUserId(), "COMMUNITY_COMMENT_MODERATED", "社区评论状态已更新",
                "您的评论状态已更新为 " + request.status(), BIZ_TYPE, row.postId());
        return repository.loadCommentView(id, true);
    }

    private CommunityRecords.PostRow loadVisiblePost(String id) {
        CommunityRecords.PostRow row = loadPost(id);
        if (!"PUBLISHED".equals(row.status())) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND_001);
        }
        return row;
    }

    private CommunityRecords.PostRow loadPost(String id) {
        return repository.findPost(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND_001));
    }

    private AuditContext audit(String actorId, String role, String action, String objectType, String objectId) {
        return AuditContext.builder()
                .actorId(actorId)
                .actorRole(role)
                .module(MODULE)
                .action(action)
                .objectType(objectType)
                .objectId(objectId)
                .build();
    }
}
