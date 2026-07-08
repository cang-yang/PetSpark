package com.petspark.notification;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageQuery;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心接口（API-NOTIFY-001~003）。
 *
 * <ul>
 *   <li>API-NOTIFY-001 {@code GET /notifications}：本人通知分页，支持 onlyUnread 过滤；</li>
 *   <li>API-NOTIFY-002 {@code PUT /notifications/{id}/read}：幂等标记单条已读，本人隔离；</li>
 *   <li>API-NOTIFY-003 {@code PUT /notifications/read-all}：幂等全部已读。</li>
 * </ul>
 *
 * <p>权限=登录（列表/全部已读）/通知本人（单条已读）。资源归属在
 * {@link NotificationQueryService} 内按 recipientId 校验，不依赖路由权限。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    public NotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<NotificationPageView> list(
            @Valid PageQuery query,
            @RequestParam(name = "onlyUnread", defaultValue = "false") boolean onlyUnread,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        NotificationPageView view = queryService.list(user.getId(), query, onlyUnread);
        // 复用 PageResult 信封出口的同时保留 unreadCount 扩展字段。
        return ApiResponse.ok(view);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @PathVariable String id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        queryService.markRead(id, user.getId());
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        queryService.markAllRead(user.getId());
        return ApiResponse.ok();
    }
}
