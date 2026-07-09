package com.petspark.boarding;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 寄养接口（API-BOARD-001~007、API-ROOM-001~003）。
 *
 * <ul>
 *   <li>GET  /boarding/availability — 查询房间可用性（登录）；</li>
 *   <li>POST /boarding-bookings — 创建预约，支持 Idempotency-Key 幂等重放；</li>
 *   <li>GET  /boarding-bookings/mine — 本人预约列表；</li>
 *   <li>POST /boarding-bookings/{id}/cancel — 取消预约（本人或 boarding:manage）；</li>
 *   <li>GET  /admin/boarding-rooms — 房间列表（room:read）；</li>
 *   <li>POST /admin/boarding-rooms — 新建房间（room:manage）；</li>
 *   <li>PUT  /admin/boarding-rooms/{id} — 更新房间（room:manage）；</li>
 *   <li>GET  /admin/boarding-bookings — 后台预约列表（boarding:manage）；</li>
 *   <li>POST /admin/boarding-bookings/{id}/assign — 分配房间（boarding:manage）；</li>
 *   <li>POST /admin/boarding-bookings/{id}/transition — 状态流转（boarding:manage）。</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class BoardingController {

    private static final String PERM_BOARDING_MANAGE = "boarding:manage";
    private static final String PERM_ROOM_READ = "room:read";
    private static final String PERM_ROOM_MANAGE = "room:manage";

    private final BoardingService service;

    public BoardingController(BoardingService service) {
        this.service = service;
    }

    // ---------- 用户端 ----------

    @GetMapping("/boarding/availability")
    public ApiResponse<java.util.List<BoardingDtos.RoomAvailabilityView>> availability(
            @Valid @ModelAttribute BoardingDtos.AvailabilityRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.availability(request, user.getId()));
    }

    @PostMapping("/boarding-bookings")
    public ApiResponse<BoardingDtos.BookingView> create(
            @Valid @RequestBody BoardingDtos.BookingCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.getId(), idempotencyKey));
    }

    @GetMapping("/boarding-bookings/mine")
    public ApiResponse<PageResult<BoardingDtos.BookingView>> listMine(
            @Valid @ModelAttribute BoardingDtos.MyBookingQuery query,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.okWithPage(service.listMine(user.getId(), query));
    }

    @PostMapping("/boarding-bookings/{id}/cancel")
    public ApiResponse<BoardingDtos.BookingView> cancel(
            @PathVariable String id,
            @Valid @RequestBody BoardingDtos.BookingCancelRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        boolean canManage = BoardingService.hasAuthority(user, PERM_BOARDING_MANAGE);
        return ApiResponse.ok(service.cancel(id, request, user.getId(), canManage));
    }

    // ---------- 后台：房间资源 ----------

    @GetMapping("/admin/boarding-rooms")
    @RequirePermission({PERM_ROOM_READ, PERM_ROOM_MANAGE})
    public ApiResponse<PageResult<BoardingDtos.RoomView>> listRooms(
            @Valid @ModelAttribute BoardingDtos.RoomQuery query) {
        return ApiResponse.okWithPage(service.listRooms(query));
    }

    @PostMapping("/admin/boarding-rooms")
    @RequirePermission(PERM_ROOM_MANAGE)
    public ApiResponse<BoardingDtos.RoomView> createRoom(
            @Valid @RequestBody BoardingDtos.RoomSaveRequest request) {
        return ApiResponse.ok(service.createRoom(request));
    }

    @PutMapping("/admin/boarding-rooms/{id}")
    @RequirePermission(PERM_ROOM_MANAGE)
    public ApiResponse<BoardingDtos.RoomView> updateRoom(
            @PathVariable String id,
            @Valid @RequestBody BoardingDtos.RoomSaveRequest request) {
        return ApiResponse.ok(service.updateRoom(id, request));
    }

    // ---------- 后台：预约履约 ----------

    @GetMapping("/admin/boarding-bookings")
    @RequirePermission(PERM_BOARDING_MANAGE)
    public ApiResponse<PageResult<BoardingDtos.BookingView>> adminList(
            @Valid @ModelAttribute BoardingDtos.AdminBookingQuery query) {
        return ApiResponse.okWithPage(service.listAdmin(query));
    }

    @PostMapping("/admin/boarding-bookings/{id}/assign")
    @RequirePermission(PERM_BOARDING_MANAGE)
    public ApiResponse<BoardingDtos.BookingView> assign(
            @PathVariable String id,
            @Valid @RequestBody BoardingDtos.AssignRoomRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.assignRoom(id, request, user.getId()));
    }

    @PostMapping("/admin/boarding-bookings/{id}/transition")
    @RequirePermission(PERM_BOARDING_MANAGE)
    public ApiResponse<BoardingDtos.BookingView> transition(
            @PathVariable String id,
            @Valid @RequestBody BoardingDtos.BookingTransitionRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.transition(id, request, user.getId()));
    }
}
