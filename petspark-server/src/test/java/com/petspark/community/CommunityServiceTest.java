package com.petspark.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.petspark.audit.AuditService;
import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.notification.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository repository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private CommunityService service;

    @BeforeEach
    void setUp() {
        service = new CommunityService(repository, notificationService, auditService);
    }

    @Test
    void createCommentRejectsHiddenPost() {
        CommunityRecords.PostRow hidden = post("post-1", "author-1", "HIDDEN", 0);
        when(repository.findPost("post-1")).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.createComment("post-1", new CommunityDtos.CommentCreateRequest(null, "评论"), "user-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND_001);

        verify(repository, never()).insertComment(any(), any(), any(), any(), any());
    }

    @Test
    void createCommentNotifiesPostAuthorWhenAnotherUserComments() {
        CommunityRecords.PostRow post = post("post-1", "author-1", "PUBLISHED", 0);
        CommunityDtos.CommentView view = new CommunityDtos.CommentView("comment-1", "post-1", null,
                "user-2", "用户", "评论", "PUBLISHED", null, 0, Instant.now(), Instant.now());
        when(repository.findPost("post-1")).thenReturn(Optional.of(post));
        when(repository.loadCommentView(any(), any(Boolean.class))).thenReturn(view);

        CommunityDtos.CommentView created = service.createComment("post-1",
                new CommunityDtos.CommentCreateRequest(null, "评论"), "user-2");

        assertThat(created.id()).isEqualTo("comment-1");
        verify(repository).incrementCommentCount("post-1");
        verify(notificationService).send("author-1", "COMMUNITY_COMMENT_CREATED", "社区帖子收到新评论",
                "您的帖子《晒猫》收到新评论", "COMMUNITY", "post-1");
    }

    @Test
    void likeHiddenPostIsRejected() {
        when(repository.findPost("post-1")).thenReturn(Optional.of(post("post-1", "author-1", "HIDDEN", 0)));

        assertThatThrownBy(() -> service.setInteraction("post-1", "user-1", "LIKE", true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND_001);
    }

    @Test
    void moderationVersionConflictReturnsVersionError() {
        when(repository.findPost("post-1"))
                .thenReturn(Optional.of(post("post-1", "author-1", "PUBLISHED", 2)))
                .thenReturn(Optional.of(post("post-1", "author-1", "PUBLISHED", 3)));
        when(repository.updatePostStatus("post-1", "HIDDEN", "违规", "op-1", 2)).thenReturn(0);

        assertThatThrownBy(() -> service.moderatePost("post-1",
                new CommunityDtos.ModerationRequest("HIDDEN", "违规", 2), "op-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VERSION_CONFLICT_001);
    }

    @Test
    void listPublishedDelegatesToRepository() {
        CommunityDtos.PostQuery query = new CommunityDtos.PostQuery();
        PageResult<CommunityDtos.PostView> page = new PageResult<>(List.of(), 1, 20, 0);
        when(repository.findPublishedPosts(query, "user-1")).thenReturn(page);

        assertThat(service.listPublished(query, "user-1")).isSameAs(page);
    }

    private CommunityRecords.PostRow post(String id, String authorId, String status, int version) {
        return new CommunityRecords.PostRow(id, authorId, "晒猫", "内容", status, null,
                0, 0, 0, version, Instant.now(), Instant.now());
    }
}
