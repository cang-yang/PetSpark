package com.petspark.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** PR-TRAINING-01 训练项目与申请视图后端验收：kind=TRAINING 薄封装复用 service 状态机。 */
class TrainingFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE_ID = "00000000-0000-0000-0000-000000000101";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    private String adminId;
    private String userId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminId = createUser("training_admin");
        userId = createUser("training_user");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userId, USER_ROLE_ID);
        adminToken = token(adminId, "training_admin", List.of("service:manage", "service:fulfill"));
        userToken = token(userId, "training_user", List.of());
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM training_application_detail WHERE booking_id IN "
                + "(SELECT id FROM service_booking WHERE user_id IN (?, ?))", adminId, userId);
        jdbcTemplate.update("DELETE FROM service_cancellation WHERE operator_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM service_booking WHERE user_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM service_slot WHERE resource_id IN "
                + "(SELECT id FROM service_resource WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-TRAIN-%'))");
        jdbcTemplate.update("DELETE FROM service_resource WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-TRAIN-%')");
        jdbcTemplate.update("DELETE FROM service_specification WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-TRAIN-%')");
        jdbcTemplate.update("DELETE FROM service_item WHERE code LIKE 'IT-TRAIN-%'");
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'service' AND actor_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", adminId, userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", adminId, userId);
    }

    @Test
    void trainingItemsOnlyReturnTrainingKind() throws Exception {
        createServiceItem("IT-TRAIN-LIST", "TRAINING", "训练列表项目", "ACTIVE");
        createServiceItem("IT-TRAIN-GENERIC", "GENERIC", "通用服务项目", "ACTIVE");

        mockMvc.perform(get("/api/v1/training/items")
                        .header("Authorization", bearer(userToken))
                        .param("keyword", "IT-TRAIN")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].kind").value("TRAINING"));
    }

    @Test
    void applicationCreatesTrainingBookingAndDetail() throws Exception {
        String itemId = createServiceItem("IT-TRAIN-APPLY", "TRAINING", "训练申请项目", "ACTIVE");
        String resourceId = createServiceResource(itemId, "IT-TRAIN 训练师", 1);
        String slotId = createSlot(resourceId, 1);

        String body = mockMvc.perform(post("/api/v1/training/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingPayload(itemId, resourceId, slotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("TRAINING"))
                .andExpect(jsonPath("$.data.trainingDetail.trainingGoal").value("改善扑人"))
                .andExpect(jsonPath("$.data.trainingDetail.intensity").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        String bookingId = objectMapper.readTree(body).path("data").path("id").asText();

        assertThat(bookingKind(bookingId)).isEqualTo("TRAINING");
        assertThat(detailCount(bookingId)).isEqualTo(1);
        assertThat(slotBookedCount(slotId)).isEqualTo(1);
    }

    @Test
    void stoppedOrNonTrainingItemCannotBeApplied() throws Exception {
        String inactiveTraining = createServiceItem("IT-TRAIN-INACTIVE", "TRAINING", "停用训练", "INACTIVE");
        String inactiveResource = createServiceResource(inactiveTraining, "IT-TRAIN 停用资源", 1);
        String inactiveSlot = createSlot(inactiveResource, 1);

        mockMvc.perform(post("/api/v1/training/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingPayload(inactiveTraining, inactiveResource, inactiveSlot)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_ITEM_NOT_FOUND_001"));

        String generic = createServiceItem("IT-TRAIN-GENERIC-APPLY", "GENERIC", "非训练", "ACTIVE");
        String genericResource = createServiceResource(generic, "IT-TRAIN 非训练资源", 1);
        String genericSlot = createSlot(genericResource, 1);
        mockMvc.perform(post("/api/v1/training/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingPayload(generic, genericResource, genericSlot)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_ITEM_NOT_FOUND_001"));
    }

    @Test
    void myAndAdminTrainingBookingsAreKindFilteredAndTransitionReused() throws Exception {
        String trainingItem = createServiceItem("IT-TRAIN-FILTER", "TRAINING", "训练筛选", "ACTIVE");
        String trainingResource = createServiceResource(trainingItem, "IT-TRAIN 筛选资源", 1);
        String trainingSlot = createSlot(trainingResource, 1);
        String trainingBooking = createTrainingBooking(trainingItem, trainingResource, trainingSlot);

        String genericItem = createServiceItem("IT-TRAIN-GENERIC-FILTER", "GENERIC", "通用筛选", "ACTIVE");
        String genericResource = createServiceResource(genericItem, "IT-TRAIN 通用资源", 1);
        String genericSlot = createSlot(genericResource, 1);
        createGenericBooking(genericItem, genericResource, genericSlot);

        mockMvc.perform(get("/api/v1/training/bookings")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(trainingBooking));

        mockMvc.perform(get("/api/v1/admin/training/bookings")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("TRAINING"));

        int version = bookingVersion(trainingBooking);
        mockMvc.perform(post("/api/v1/admin/training/bookings/{id}/transition", trainingBooking)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"开始训练\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    private String createTrainingBooking(String itemId, String resourceId, String slotId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/training/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingPayload(itemId, resourceId, slotId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void createGenericBooking(String itemId, String resourceId, String slotId) throws Exception {
        mockMvc.perform(post("/api/v1/services/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceItemId":"%s","resourceId":"%s","slotId":"%s",
                                 "customerName":"张三","customerPhone":"13800000000","remark":"通用"}
                                """.formatted(itemId, resourceId, slotId)))
                .andExpect(status().isOk());
    }

    private String trainingPayload(String itemId, String resourceId, String slotId) {
        return """
                {"serviceItemId":"%s","resourceId":"%s","slotId":"%s",
                 "customerName":"张三","customerPhone":"13800000000","remark":"训练备注",
                 "trainingGoal":"改善扑人","behaviorProblem":"出门扑人","intensity":"HIGH",
                 "attentionNote":"膝盖旧伤，避免跳跃"}
                """.formatted(itemId, resourceId, slotId);
    }

    private String createServiceItem(String code, String kind, String name, String status) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO service_item (id, kind, code, name, qualification, availability_note,
                                          exception_rule, base_price, status, version)
                VALUES (?, ?, ?, ?, '训练师资质', '周末可约', '异常可改期', 120.00, ?, 0)
                """, id, kind, code, name, status);
        return id;
    }

    private String createServiceResource(String itemId, String name, int capacity) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO service_resource (id, service_item_id, name, status, capacity, version)
                VALUES (?, ?, ?, 'ACTIVE', ?, 0)
                """, id, itemId, name, capacity);
        return id;
    }

    private String createSlot(String resourceId, int capacity) {
        String id = UUID.randomUUID().toString();
        Instant start = Instant.now().plusSeconds(3600 + Math.abs(id.hashCode() % 600));
        Instant end = start.plusSeconds(1800);
        LocalDate slotDate = start.atZone(ZoneOffset.UTC).toLocalDate();
        jdbcTemplate.update("""
                INSERT INTO service_slot (id, resource_id, slot_date, start_at, end_at,
                                          capacity, booked_count, status, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'OPEN', 0)
                """, id, resourceId, java.sql.Date.valueOf(slotDate),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), capacity);
        return id;
    }

    private int slotBookedCount(String slotId) {
        Integer count = jdbcTemplate.queryForObject("SELECT booked_count FROM service_slot WHERE id = ?",
                Integer.class, slotId);
        return count == null ? -1 : count;
    }

    private int detailCount(String bookingId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_application_detail WHERE booking_id = ?",
                Integer.class, bookingId);
        return count == null ? -1 : count;
    }

    private String bookingKind(String bookingId) {
        return jdbcTemplate.queryForObject("SELECT kind FROM service_booking WHERE id = ?", String.class, bookingId);
    }

    private int bookingVersion(String bookingId) {
        Integer version = jdbcTemplate.queryForObject("SELECT version FROM service_booking WHERE id = ?",
                Integer.class, bookingId);
        return version == null ? -1 : version;
    }

    private String createUser(String username) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version)
                VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)
                """, id, username, username + "@example.com", username);
        return id;
    }

    private void assignRole(String id, String roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
    }

    private String token(String id, String username, List<String> authorities) {
        SysUser user = new SysUser(id, username, username + "@example.com", "$2a$10$test", username, "ACTIVE", 0);
        return jwtService.issue(user, authorities).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
