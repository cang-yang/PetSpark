package com.petspark.community;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Community post, comment, interaction, and admin moderation APIs. */
@Validated
@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    private static final String COMMUNITY_MANAGE = "community:manage";
    private static final String COMMUNITY_MODERATE = "community:moderate";

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/community/posts")
    public ApiResponse<PageResult<CommunityDtos.PostView>> listPosts(
            @Valid @ModelAttribute CommunityDtos.PostQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listPublished(query, user.getId()));
    }

    @PostMapping("/community/posts")
    public ApiResponse<CommunityDtos.PostView> createPost(
            @Valid @RequestBody CommunityDtos.PostCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.createPost(request, user.getId()));
    }

    @GetMapping("/community/posts/{id}")
    public ApiResponse<CommunityDtos.PostView> getPost(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getPublished(id, user.getId(), hasCommunityAdmin(user)));
    }

    @GetMapping("/community/posts/{id}/comments")
    public ApiResponse<PageResult<CommunityDtos.CommentView>> listComments(
            @PathVariable String id,
            @Valid @ModelAttribute CommunityDtos.CommentQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listComments(id, query, hasCommunityAdmin(user)));
    }

    @PostMapping("/community/posts/{id}/comments")
    public ApiResponse<CommunityDtos.CommentView> createComment(
            @PathVariable String id,
            @Valid @RequestBody CommunityDtos.CommentCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.createComment(id, request, user.getId()));
    }

    @PostMapping("/community/posts/{id}/like")
    public ApiResponse<CommunityDtos.PostView> likePost(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.setInteraction(id, user.getId(), "LIKE", true));
    }

    @PostMapping("/community/posts/{id}/unlike")
    public ApiResponse<CommunityDtos.PostView> unlikePost(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.setInteraction(id, user.getId(), "LIKE", false));
    }

    @PostMapping("/community/posts/{id}/favorite")
    public ApiResponse<CommunityDtos.PostView> favoritePost(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.setInteraction(id, user.getId(), "FAVORITE", true));
    }

    @PostMapping("/community/posts/{id}/unfavorite")
    public ApiResponse<CommunityDtos.PostView> unfavoritePost(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.setInteraction(id, user.getId(), "FAVORITE", false));
    }

    @GetMapping("/community/my/posts")
    public ApiResponse<PageResult<CommunityDtos.PostView>> listMyPosts(
            @Valid @ModelAttribute CommunityDtos.MyPostQuery query,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMy(user.getId(), query));
    }

    @GetMapping("/admin/community/posts")
    @RequirePermission({COMMUNITY_MANAGE, COMMUNITY_MODERATE})
    public ApiResponse<PageResult<CommunityDtos.PostView>> adminListPosts(
            @Valid @ModelAttribute CommunityDtos.PostQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @PostMapping("/admin/community/posts/{id}/moderation")
    @RequirePermission({COMMUNITY_MANAGE, COMMUNITY_MODERATE})
    public ApiResponse<CommunityDtos.PostView> moderatePost(
            @PathVariable String id,
            @Valid @RequestBody CommunityDtos.ModerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.moderatePost(id, request, user.getId()));
    }

    @PostMapping("/admin/community/comments/{id}/moderation")
    @RequirePermission({COMMUNITY_MANAGE, COMMUNITY_MODERATE})
    public ApiResponse<CommunityDtos.CommentView> moderateComment(
            @PathVariable String id,
            @Valid @RequestBody CommunityDtos.ModerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.moderateComment(id, request, user.getId()));
    }

    private boolean hasCommunityAdmin(AuthenticatedUser user) {
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : user.getAuthorities()) {
            if (COMMUNITY_MANAGE.equals(authority.getAuthority()) || COMMUNITY_MODERATE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
