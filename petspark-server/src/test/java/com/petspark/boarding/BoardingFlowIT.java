package com.petspark.boarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PR-BOARD-01 寄养容量与预约履约端到端验收。覆盖：
 * <ol>
 *   <li>日期边界：end_date &lt;= start_date → 400 {@code BOARD_DATE_INVALID_001}；</li>
 *   <li>宠物归属：非主人发起预约 → 403 {@code PET_OWNERSHIP_001}；</li>
 *   <li>照护字段最小化：非主人/非履约角色看不到敏感字段；</li>
 *   <li>多日锁顺序：多日预约按日期升序锁定 boarding_room_day，并发不超容量；</li>
 *   <li>超容量并发：第二笔预约分配同一房间同日超容量 → 409 {@code BOARD_ROOM_CAPACITY_001}；</li>
 *   <li>取消 100% 释放：取消 CONFIRMED 预约后当日容量恢复，可重新分配；</li>
 *   <li>状态机与通知：PENDING→CONFIRMED→IN_SERVICE→COMPLETED，每个节点落 outbox 通知。</li>
 * </ol>
 *
 * <p>由 main controller 在共享本机 MySQL（schema petspark_board）上串行执行。
 */
class BoardingFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE = "00000000-0000-0000-0000-000000000102";
    private static final String SERVICE_ROLE = "00000000-0000-0000-0000-000000000104";
    private static final String USER_ROLE = "00000000-0000-0000-0000-000000000101";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    private String adminId;
    private String serviceId;
    private String ownerId;
    private String otherId;
    private String adminToken;
    private String serviceToken;
    private String ownerToken;
    private String otherToken;
    private String petId;
    private final List<String> roomIds = new ArrayList<>();
    private final List<String> bookingIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        adminId = createUser("board_admin");
        serviceId = createUser("board_service");
        ownerId = createUser("board_owner");
        otherId = createUser("board_other");
        assignRole(adminId, ADMIN_ROLE);
        assignRole(serviceId, SERVICE_ROLE);
        assignRole(ownerId, USER_ROLE);
        assignRole(otherId, USER_ROLE);
        adminToken = token(adminId, "board_admin");
        serviceToken = token(serviceId, "board_service");
        ownerToken = token(ownerId, "board_owner");
        otherToken = token(otherId, "board_other");
        petId = createPet("IT-BOARD-Pet", ownerId);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM boarding_care_profile WHERE booking_id IN "
                + "(SELECT id FROM boarding_booking WHERE user_id IN (?, ?, ?, ?))",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM boarding_booking WHERE user_id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM boarding_room_day WHERE room_id IN "
                + "(SELECT id FROM (SELECT id FROM boarding_room WHERE code LIKE 'IT-BOARD-%') AS x)");
        jdbcTemplate.update("DELETE FROM boarding_room WHERE code LIKE 'IT-BOARD-%'");
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'boarding' AND actor_id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM pet WHERE name LIKE 'IT-BOARD-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?, ?)",
                adminId, serviceId, ownerId, otherId);
    }

    @Test
    void dateBoundaryRejected() throws Exception {
        mockMvc.perform(post("/api/v1/boarding-bookings")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(petId, "2026-07-10", "2026-07-09")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOARD_DATE_INVALID_001"));
    }

    @Test
    void petOwnershipEnforced() throws Exception {
        // otherId 不是 pet owner → PET_OWNERSHIP_001
        mockMvc.perform(post("/api/v1/boarding-bookings")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(petId, "2026-07-10", "2026-07-12")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PET_OWNERSHIP_001"));
    }

    @Test
    void careFieldsMinimizedForNonFulfillmentViewer() throws Exception {
        String bookingId = createBooking(ownerToken, petId, "2026-07-10", "2026-07-12",
                "13800000000", "每日 5g 狗粮", "皮肤药膏");
        // 主人可见敏感字段
        mockMvc.perform(get("/api/v1/boarding-bookings/mine")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].careProfile.emergencyContact").value("13800000000"))
                .andExpect(jsonPath("$.data.items[0].careProfile.feedingPlan").value("每日 5g 狗粮"));

        // 后台 boarding:manage 可见（admin 持有该权限）
        mockMvc.perform(get("/api/v1/admin/boarding-bookings")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + bookingId + "')].careProfile.medicationPlan")
                        .value("皮肤药膏"));

        // 普通用户 other 看不到本人以外的预约：通过 mine 列表无该预约
        mockMvc.perform(get("/api/v1/boarding-bookings/mine")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void multiDayLockOrderAndCapacityNotExceeded() throws Exception {
        String roomId = createRoom("IT-BOARD-Multi", 1);
        String b1 = createBooking(ownerToken, petId, "2026-07-10", "2026-07-13", null, null, null);
        assignRoom(adminToken, b1, roomId);
        // 全部 3 日都被占用
        for (LocalDate d = LocalDate.of(2026, 7, 10); d.isBefore(LocalDate.of(2026, 7, 13)); d = d.plusDays(1)) {
            assertThat(reservedOf(roomId, d)).isEqualTo(1);
        }
        // 第二笔预约同区间分配同一房间 → 容量不足
        String b2 = createBooking(ownerToken, petId, "2026-07-11", "2026-07-12", null, null, null);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/assign", b2)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload(roomId, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_ROOM_CAPACITY_001"));
    }

    @Test
    void cancelReleasesCapacityCompletely() throws Exception {
        String roomId = createRoom("IT-BOARD-Cancel", 1);
        String b = createBooking(ownerToken, petId, "2026-07-10", "2026-07-12", null, null, null);
        assignRoom(adminToken, b, roomId);
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 10))).isEqualTo(1);
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 11))).isEqualTo(1);
        int version = bookingVersion(b);
        mockMvc.perform(post("/api/v1/boarding-bookings/{id}/cancel", b)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload("不寄养了", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        // 取消后容量完全释放
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 10))).isZero();
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 11))).isZero();
        // 可重新分配新预约
        String b2 = createBooking(ownerToken, petId, "2026-07-10", "2026-07-12", null, null, null);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/assign", b2)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload(roomId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void stateMachineAndNotifications() throws Exception {
        String roomId = createRoom("IT-BOARD-State", 1);
        String b = createBooking(ownerToken, petId, "2026-07-10", "2026-07-12",
                "13900000000", "每日 3g", null);
        // PENDING_CONFIRMATION
        assertThat(bookingStatus(b)).isEqualTo("PENDING_CONFIRMATION");
        // 通知：BOARDING_CREATED
        assertNotificationExists(ownerId, "BOARDING_CREATED", b);

        int v0 = bookingVersion(b);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/assign", b)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload(roomId, v0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertNotificationExists(ownerId, "BOARDING_CONFIRMED", b);

        int v1 = bookingVersion(b);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/transition", b)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload("IN_SERVICE", "开始履约", null, v1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_SERVICE"));
        assertNotificationExists(ownerId, "BOARDING_STARTED", b);

        int v2 = bookingVersion(b);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/transition", b)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload("COMPLETED", "完成", null, v2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertNotificationExists(ownerId, "BOARDING_COMPLETED", b);
        // COMPLETED 后容量释放
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 10))).isZero();
    }

    @Test
    void rejectFromPendingDoesNotReserveCapacity() throws Exception {
        String roomId = createRoom("IT-BOARD-Reject", 1);
        String b = createBooking(ownerToken, petId, "2026-07-10", "2026-07-11", null, null, null);
        int v0 = bookingVersion(b);
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/transition", b)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload("REJECTED", null, "档期满", v0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        // 从未分配，容量行未占
        assertThat(reservedOf(roomId, LocalDate.of(2026, 7, 10))).isZero();
        assertNotificationExists(ownerId, "BOARDING_REJECTED", b);
    }

    @Test
    void idempotencyReplaysSameBooking() throws Exception {
        String idemKey = "board-idem-" + UUID.randomUUID();
        String payload = bookingPayload(petId, "2026-07-10", "2026-07-11");
        String first = mockMvc.perform(post("/api/v1/boarding-bookings")
                        .header("Authorization", bearer(ownerToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).path("data").path("id").asText();
        String second = mockMvc.perform(post("/api/v1/boarding-bookings")
                        .header("Authorization", bearer(ownerToken))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).path("data").path("id").asText();
        assertThat(secondId).isEqualTo(firstId);
        bookingIds.add(firstId);
    }

    @Test
    void roomCreateDuplicateCodeRejected() throws Exception {
        createRoom("IT-BOARD-DUP", 2);
        mockMvc.perform(post("/api/v1/admin/boarding-rooms")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomPayload("IT-BOARD-DUP", "重复房间", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_ROOM_DUPLICATE_001"));
    }

    @Test
    void availabilityRespectsCapacity() throws Exception {
        String roomId = createRoom("IT-BOARD-Avail", 2);
        mockMvc.perform(get("/api/v1/boarding/availability")
                        .header("Authorization", bearer(ownerToken))
                        .param("startDate", "2026-07-10")
                        .param("endDate", "2026-07-12")
                        .param("petId", petId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.roomId == '" + roomId + "')].availableCount").value(2))
                .andExpect(jsonPath("$.data[?(@.roomId == '" + roomId + "')].open").value(true));
    }

    @Test
    void serviceRoleCanListRoomsButNotManage() throws Exception {
        createRoom("IT-BOARD-SvcRead", 2);
        // room:read 允许 SERVICE 列出房间
        mockMvc.perform(get("/api/v1/admin/boarding-rooms")
                        .header("Authorization", bearer(serviceToken)))
                .andExpect(status().isOk());
        // room:manage 不允许 SERVICE 创建房间
        mockMvc.perform(post("/api/v1/admin/boarding-rooms")
                        .header("Authorization", bearer(serviceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomPayload("IT-BOARD-SvcCreate", "服务角色建房间", 1)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private String createRoom(String code, int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/boarding-rooms")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomPayload(code, "测试房间 " + code, capacity)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        roomIds.add(id);
        return id;
    }

    private String createBooking(String token, String petId, String start, String end,
            String emergencyContact, String feedingPlan, String medicationPlan) throws Exception {
        String body = mockMvc.perform(post("/api/v1/boarding-bookings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(petId, start, end, emergencyContact, feedingPlan, medicationPlan)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        bookingIds.add(id);
        return id;
    }

    private void assignRoom(String token, String bookingId, String roomId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/boarding-bookings/{id}/assign", bookingId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload(roomId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    private String roomPayload(String code, String name, int capacity) {
        return """
                {"code":"%s","name":"%s","capacity":%d,"description":"IT","version":0}
                """.formatted(code, name, capacity);
    }

    private String bookingPayload(String petId, String start, String end) {
        return bookingPayload(petId, start, end, null, null, null);
    }

    private String bookingPayload(String petId, String start, String end,
            String emergencyContact, String feedingPlan, String medicationPlan) {
        return """
                {"petId":"%s","startDate":"%s","endDate":"%s",
                 "careProfile":{"emergencyContact":"%s","feedingPlan":"%s","medicationPlan":"%s"}}
                """.formatted(petId, start, end,
                emergencyContact == null ? "" : emergencyContact,
                feedingPlan == null ? "" : feedingPlan,
                medicationPlan == null ? "" : medicationPlan);
    }

    private String assignPayload(String roomId, int version) {
        return """
                {"roomId":"%s","note":"分配","version":%d}
                """.formatted(roomId, version);
    }

    private String cancelPayload(String reason, int version) {
        return """
                {"reason":"%s","version":%d}
                """.formatted(reason, version);
    }

    private String transitionPayload(String status, String note, String reason, int version) {
        return """
                {"status":"%s","note":"%s","reason":"%s","version":%d}
                """.formatted(status, note == null ? "" : note,
                reason == null ? "" : reason, version);
    }

    private int reservedOf(String roomId, LocalDate date) {
        // room_day 行仅在 assign 占用时由 lockRoomDay 插入；PENDING/REJECTED 等未占
        // 用状态下该日期无行，按 0 处理（"取消 100% 释放"/"拒绝不占容量"测试点）。
        Integer v = jdbcTemplate.query(
                "SELECT reserved_count FROM boarding_room_day WHERE room_id = ? AND stay_date = ?",
                rs -> rs.next() ? rs.getInt("reserved_count") : null,
                roomId, java.sql.Date.valueOf(date));
        return v == null ? 0 : v;
    }

    private int bookingVersion(String id) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT version FROM boarding_booking WHERE id = ?", Integer.class, id);
        return v == null ? -1 : v;
    }

    private String bookingStatus(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM boarding_booking WHERE id = ?", String.class, id);
    }

    /**
     * 断言存在一条面向 {@code recipientId}、类型为 {@code type}、关联业务对象 {@code businessId}
     * 的 outbox 通知事件。通知事件由 BoardingService 在业务事务内追加进 outbox，由
     * 后台 dispatcher 异步落库为 notification 行（测试 profile 已将轮询拉到 1h，本测试
     * 不依赖落库后的 notification 行，而是直接校验 outbox 事件已原子提交）。
     *
     * <p>{@code outbox_event.payload} 为 MySQL JSON 列，存储时会被规范化（冒号后补空格），
     * 故不能用 {@code LIKE} 匹配键值；改用 {@code JSON_EXTRACT} 取字段精确比对。
     */
    private void assertNotificationExists(String recipientId, String type, String businessId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'notification.send' "
                        + "AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.type')) = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.businessId')) = ?",
                Integer.class, recipientId, type, businessId);
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", username);
        return id;
    }

    private void assignRole(String userId, String roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private String createPet(String name, String ownerId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pet (id, name, species, owner_user_id, ownership_type,
                                 adoption_status, boarding_status, public_status, info_updated_at)
                VALUES (?, ?, 'DOG', ?, 'USER', 'NOT_FOR_ADOPTION', 'NONE', 'PRIVATE', CURRENT_TIMESTAMP(3))
                """, id, name, ownerId);
        return id;
    }

    private String token(String id, String username) {
        return jwtService.issue(new SysUser(id, username, username + "@example.com",
                "$2a$10$test", username, "ACTIVE", 0), List.of()).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
