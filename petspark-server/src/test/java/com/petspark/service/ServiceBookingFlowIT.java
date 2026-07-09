package com.petspark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PR-SERVICE-01 服务资源、可用窗口与通用预约端到端验收。覆盖 API-SVC-001~013 关键路径：
 *
 * <ul>
 *   <li>服务透明度字段（资质/时段/异常规则）回显；</li>
 *   <li>宠物归属校验（非本人宠物拒绝）；</li>
 *   <li>窗口校验与重叠并发：同窗口容量上限内不超卖，满员返回 SERVICE_SLOT_UNAVAILABLE_001；</li>
 *   <li>取消/异常终止释放窗口容量；</li>
 *   <li>状态机权限：履约流转仅 service:fulfill 角色；</li>
 *   <li>通知触发（scene + event 键）。</li>
 * </ul>
 */
class ServiceBookingFlowIT extends AbstractControllerTest {

    private static final String ADMIN_ROLE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE_ID = "00000000-0000-0000-0000-000000000101";
    private static final String SERVICE_ROLE_ID = "00000000-0000-0000-0000-000000000104";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private String adminId;
    private String userId;
    private String otherId;
    private String serviceStaffId;
    private String adminToken;
    private String userToken;
    private String otherToken;
    private String serviceStaffToken;

    @BeforeEach
    void setUp() {
        adminId = createUser("svc_admin");
        userId = createUser("svc_user");
        otherId = createUser("svc_other");
        serviceStaffId = createUser("svc_staff");
        assignRole(adminId, ADMIN_ROLE_ID);
        assignRole(userId, USER_ROLE_ID);
        assignRole(otherId, USER_ROLE_ID);
        assignRole(serviceStaffId, SERVICE_ROLE_ID);
        adminToken = token(adminId, "svc_admin", List.of("service:manage", "service:fulfill"));
        userToken = token(userId, "svc_user", List.of());
        otherToken = token(otherId, "svc_other", List.of());
        serviceStaffToken = token(serviceStaffId, "svc_staff", List.of("service:fulfill"));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM service_cancellation WHERE operator_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM service_booking WHERE user_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM service_slot WHERE resource_id IN "
                + "(SELECT id FROM service_resource WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-SVC-%'))");
        jdbcTemplate.update("DELETE FROM service_resource WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-SVC-%')");
        jdbcTemplate.update("DELETE FROM service_specification WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-SVC-%')");
        jdbcTemplate.update("DELETE FROM service_beauty_profile WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-SVC-%')");
        jdbcTemplate.update("DELETE FROM service_medical_profile WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code LIKE 'IT-SVC-%')");
        jdbcTemplate.update("DELETE FROM service_item WHERE code LIKE 'IT-SVC-%'");
        jdbcTemplate.update("DELETE FROM pet WHERE name LIKE 'IT-SVC-%'");
        jdbcTemplate.update("DELETE FROM notification WHERE recipient_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE module = 'service' AND actor_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?, ?)",
                adminId, userId, otherId, serviceStaffId);
    }

    @Test
    void serviceItemTransparencyFieldsDisplayed() throws Exception {
        String itemId = createServiceItem("IT-SVC-TRANSPARENCY", "GENERIC", "测试通用服务",
                "AAA 资质", "工作日 09-18", "迟到 15 分钟视为放弃");
        String body = mockMvc.perform(get("/api/v1/services/items/{id}", itemId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("qualification").asText()).isEqualTo("AAA 资质");
        assertThat(data.path("availabilityNote").asText()).isEqualTo("工作日 09-18");
        assertThat(data.path("exceptionRule").asText()).isEqualTo("迟到 15 分钟视为放弃");
        assertThat(data.path("kind").asText()).isEqualTo("GENERIC");
    }

    @Test
    void petOwnershipEnforcedOnBooking() throws Exception {
        String itemId = createServiceItem("IT-SVC-PETOWN", "GENERIC", "测试宠物归属", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-归属", 1);
        String slotId = createSlot(resourceId, 1);
        // 宠物归属 other 用户，user 试图绑定 → 拒绝
        String petId = createPet("IT-SVC-OtherPet", otherId);

        mockMvc.perform(post("/api/v1/services/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(itemId, resourceId, slotId, petId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_PET_OWNERSHIP_001"));
    }

    @Test
    void slotOverlapConcurrencyNotOversold() throws Exception {
        String itemId = createServiceItem("IT-SVC-OVERLAP", "GENERIC", "测试重叠并发", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-并发", 1);
        String slotId = createSlot(resourceId, 1);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CompletableFuture<Void>[] futures = new CompletableFuture[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            futures[idx] = CompletableFuture.runAsync(() -> {
                try {
                    int status = mockMvc.perform(post("/api/v1/services/bookings")
                                    .header("Authorization", bearer(userToken))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bookingPayloadWithPhone(itemId, resourceId, slotId,
                                            null, "1380000000" + idx)))
                            .andReturn().getResponse().getStatus();
                    // 期望 200 或 409（满员），但不能是 500
                    assertThat(status == 200 || status == 409).isTrue();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pool);
        }
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 容量 1：只能成功一次
        Integer confirmed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_booking WHERE slot_id = ? AND status IN ('CONFIRMED','IN_PROGRESS','COMPLETED')",
                Integer.class, slotId);
        assertThat(confirmed).isEqualTo(1);
        Integer bookedCount = jdbcTemplate.queryForObject(
                "SELECT booked_count FROM service_slot WHERE id = ?", Integer.class, slotId);
        assertThat(bookedCount).isEqualTo(1);
    }

    @Test
    void cancelReleasesSlot() throws Exception {
        String itemId = createServiceItem("IT-SVC-CANCEL", "GENERIC", "测试取消", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-取消", 1);
        String slotId = createSlot(resourceId, 1);
        String bookingId = createBooking(itemId, resourceId, slotId, null);

        assertThat(slotBookedCount(slotId)).isEqualTo(1);

        int version = bookingVersion(bookingId);
        mockMvc.perform(post("/api/v1/services/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"临时有事\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(slotBookedCount(slotId)).isEqualTo(0);

        // 取消后窗口可被重新预约
        String booking2 = createBooking(itemId, resourceId, slotId, null);
        assertThat(booking2).isNotBlank();
        assertThat(slotBookedCount(slotId)).isEqualTo(1);
    }

    @Test
    void exceptionTerminatesAndReleasesSlot() throws Exception {
        String itemId = createServiceItem("IT-SVC-EXCEPT", "GENERIC", "测试异常终止", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-异常", 1);
        String slotId = createSlot(resourceId, 1);
        String bookingId = createBooking(itemId, resourceId, slotId, null);

        int version = bookingVersion(bookingId);
        mockMvc.perform(post("/api/v1/services/bookings/{id}/exception", bookingId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"资源故障\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXCEPTION"));

        assertThat(slotBookedCount(slotId)).isEqualTo(0);
        // 取消/异常轨迹行已写
        Integer trailCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_cancellation WHERE booking_id = ?",
                Integer.class, bookingId);
        assertThat(trailCount).isEqualTo(1);
    }

    @Test
    void fulfillmentTransitionGuardsPermission() throws Exception {
        String itemId = createServiceItem("IT-SVC-FULFILL", "GENERIC", "测试履约", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-履约", 1);
        String slotId = createSlot(resourceId, 1);
        String bookingId = createBooking(itemId, resourceId, slotId, null);
        int version = bookingVersion(bookingId);

        // 普通用户无 service:fulfill → 403
        mockMvc.perform(post("/api/v1/admin/services/bookings/{id}/transition", bookingId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"开始\",\"version\":" + version + "}"))
                .andExpect(status().isForbidden());

        // SERVICE 角色 → 200
        mockMvc.perform(post("/api/v1/admin/services/bookings/{id}/transition", bookingId)
                        .header("Authorization", bearer(serviceStaffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"开始\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // ADMIN 完成
        int v1 = bookingVersion(bookingId);
        mockMvc.perform(post("/api/v1/admin/services/bookings/{id}/transition", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"note\":\"完成\",\"version\":" + v1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 回退非法（COMPLETED → IN_PROGRESS）
        int v2 = bookingVersion(bookingId);
        mockMvc.perform(post("/api/v1/admin/services/bookings/{id}/transition", bookingId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"note\":\"回退\",\"version\":" + v2 + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_BOOKING_STATE_001"));
    }

    @Test
    void bookingOwnershipIsolation() throws Exception {
        String itemId = createServiceItem("IT-SVC-OWNER", "GENERIC", "测试归属隔离", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-隔离", 1);
        String slotId = createSlot(resourceId, 1);
        String bookingId = createBooking(itemId, resourceId, slotId, null);

        // other 用户访问 user 的预约 → 403
        mockMvc.perform(get("/api/v1/services/bookings/{id}", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void notificationSceneEventKeyWrittenToOutbox() throws Exception {
        String itemId = createServiceItem("IT-SVC-NOTIFY", "GENERIC", "测试通知", null, null, null);
        String resourceId = createServiceResource(itemId, "IT-SVC 资源-通知", 1);
        String slotId = createSlot(resourceId, 1);
        createBooking(itemId, resourceId, slotId, null);

        // 通知走 outbox，type = SERVICE_BOOKING_CONFIRMED
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'notification.send'",
                Integer.class, userId);
        assertThat(count).isGreaterThan(0);
        // payload 里 type 字段应为 SERVICE_BOOKING_CONFIRMED（scene + event 组合键）
        String payloadType = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.type')) FROM outbox_event "
                        + "WHERE aggregate_id = ? AND event_type = 'notification.send' ORDER BY id DESC LIMIT 1",
                String.class, userId);
        assertThat(payloadType).isEqualTo("SERVICE_BOOKING_CONFIRMED");
    }

    @Test
    void adminManageServiceItemAndSlot() throws Exception {
        // 管理员新增服务项目
        String body = mockMvc.perform(post("/api/v1/admin/services/items")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GENERIC\",\"code\":\"IT-SVC-ADMIN-ITEM\",\"name\":\"后台新增服务\","
                                + "\"basePrice\":88.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("IT-SVC-ADMIN-ITEM"))
                .andReturn().getResponse().getContentAsString();
        String itemId = objectMapper.readTree(body).path("data").path("id").asText();

        // 更新
        mockMvc.perform(put("/api/v1/admin/services/items/{id}", itemId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GENERIC\",\"code\":\"IT-SVC-ADMIN-ITEM\",\"name\":\"改名服务\","
                                + "\"basePrice\":99.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名服务"));

        // 新增资源
        String resBody = mockMvc.perform(post("/api/v1/admin/services/resources")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceItemId\":\"" + itemId + "\",\"name\":\"IT-SVC 资源-后台\","
                                + "\"capacity\":2,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String resourceId = objectMapper.readTree(resBody).path("data").path("id").asText();

        // 批量创建窗口
        String startAt = Instant.now().plusSeconds(3600).toString();
        String endAt = Instant.now().plusSeconds(7200).toString();
        mockMvc.perform(post("/api/v1/admin/services/slots")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"" + resourceId + "\",\"slots\":["
                                + "{\"startAt\":\"" + startAt + "\",\"endAt\":\"" + endAt + "\",\"capacity\":2}"
                                + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capacity").value(2));

        // 删除服务项目
        mockMvc.perform(delete("/api/v1/admin/services/items/{id}", itemId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void beautyKindFilteredViewsAndProfileReuseServiceBookingFlow() throws Exception {
        String beautyItemId = createServiceItem("IT-SVC-BEAUTY", "BEAUTY", "测试美容服务",
                "美容师 B 级", "10-18 点", "严重应激暂停");
        createBeautyProfile(beautyItemId, "DOG,CAT", "LONG,CURLY", "SMALL,MEDIUM",
                "低噪吹干", "说明过敏史");
        String genericItemId = createServiceItem("IT-SVC-NON-BEAUTY", "GENERIC", "测试非美容服务", null, null, null);
        String resourceId = createServiceResource(beautyItemId, "IT-SVC 美容师", 1);
        String slotId = createSlot(resourceId, 1);

        String beautyListBody = mockMvc.perform(get("/api/v1/services/items")
                        .header("Authorization", bearer(userToken))
                        .param("kind", "BEAUTY")
                        .param("keyword", "IT-SVC-BEAUTY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode beautyItem = objectMapper.readTree(beautyListBody).path("data").path("items").get(0);
        assertThat(beautyItem.path("kind").asText()).isEqualTo("BEAUTY");
        assertThat(beautyItem.path("beautyProfile").path("carePreferences").asText()).isEqualTo("低噪吹干");

        String bookingId = createBooking(beautyItemId, resourceId, slotId, null);
        String bookingNo = jdbcTemplate.queryForObject("SELECT booking_no FROM service_booking WHERE id = ?",
                String.class, bookingId);
        mockMvc.perform(get("/api/v1/services/bookings")
                        .header("Authorization", bearer(userToken))
                        .param("kind", "BEAUTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.items[0].kind").value("BEAUTY"));

        mockMvc.perform(get("/api/v1/admin/services/bookings")
                        .header("Authorization", bearer(adminToken))
                        .param("kind", "BEAUTY")
                        .param("keyword", bookingNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.items[0].kind").value("BEAUTY"));

        Integer genericCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_item WHERE id = ? AND kind = 'GENERIC'", Integer.class, genericItemId);
        assertThat(genericCount).isEqualTo(1);
    }

    @Test
    void medicalKindFilteredViewsAndProfileReuseServiceBookingFlow() throws Exception {
        String medicalItemId = createServiceItem("IT-SVC-MEDICAL", "MEDICAL", "测试医疗服务",
                "执业兽医师", "09-18 点", "急症请转急诊");
        createMedicalProfile(medicalItemId, "动物诊疗许可证 IT-MED", "全科兽医团队", "DOG,CAT",
                "健康体检与疫苗咨询", "携带免疫记录", "呼吸困难等急症优先急诊");
        String genericItemId = createServiceItem("IT-SVC-NON-MEDICAL", "GENERIC", "测试非医疗服务", null, null, null);
        String resourceId = createServiceResource(medicalItemId, "IT-SVC 医疗诊室", 1);
        String slotId = createSlot(resourceId, 1);

        String medicalListBody = mockMvc.perform(get("/api/v1/services/items")
                        .header("Authorization", bearer(userToken))
                        .param("kind", "MEDICAL")
                        .param("keyword", "IT-SVC-MEDICAL"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode medicalItem = objectMapper.readTree(medicalListBody).path("data").path("items").get(0);
        assertThat(medicalItem.path("kind").asText()).isEqualTo("MEDICAL");
        assertThat(medicalItem.path("medicalProfile").path("careScope").asText()).isEqualTo("健康体检与疫苗咨询");
        assertThat(medicalItem.path("medicalProfile").path("clinicLicense").asText()).isEqualTo("动物诊疗许可证 IT-MED");

        String bookingId = createBooking(medicalItemId, resourceId, slotId, null);
        String bookingNo = jdbcTemplate.queryForObject("SELECT booking_no FROM service_booking WHERE id = ?",
                String.class, bookingId);
        mockMvc.perform(get("/api/v1/services/bookings")
                        .header("Authorization", bearer(userToken))
                        .param("kind", "MEDICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.items[0].kind").value("MEDICAL"));

        mockMvc.perform(get("/api/v1/admin/services/bookings")
                        .header("Authorization", bearer(adminToken))
                        .param("kind", "MEDICAL")
                        .param("keyword", bookingNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.items[0].kind").value("MEDICAL"));

        Integer genericCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_item WHERE id = ? AND kind = 'GENERIC'", Integer.class, genericItemId);
        assertThat(genericCount).isEqualTo(1);
    }

    // ----- helpers -----

    private String createServiceItem(String code, String kind, String name,
            String qualification, String availabilityNote, String exceptionRule) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO service_item (id, kind, code, name, qualification, availability_note,
                                          exception_rule, base_price, status, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 50.00, 'ACTIVE', 0)
                """, id, kind, code, name, qualification, availabilityNote, exceptionRule);
        return id;
    }

    private void createBeautyProfile(String itemId, String supportedPetTypes, String coatTypes,
            String sizeRanges, String carePreferences, String cautionNotes) {
        jdbcTemplate.update("""
                INSERT INTO service_beauty_profile
                    (id, service_item_id, supported_pet_types, coat_types, size_ranges,
                     care_preferences, caution_notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), itemId, supportedPetTypes, coatTypes,
                sizeRanges, carePreferences, cautionNotes);
    }

    private void createMedicalProfile(String itemId, String clinicLicense, String veterinarianTeam,
            String supportedPetTypes, String careScope, String appointmentNotice, String emergencyRule) {
        jdbcTemplate.update("""
                INSERT INTO service_medical_profile
                    (id, service_item_id, clinic_license, veterinarian_team, supported_pet_types,
                     care_scope, appointment_notice, emergency_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), itemId, clinicLicense, veterinarianTeam,
                supportedPetTypes, careScope, appointmentNotice, emergencyRule);
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
        Instant start = Instant.now().plusSeconds(600);
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

    private String createPet(String name, String ownerId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pet (id, name, species, owner_user_id, ownership_type, adoption_status,
                                 boarding_status, public_status, info_updated_at)
                VALUES (?, ?, 'DOG', ?, 'USER', 'NOT_FOR_ADOPTION', 'NONE', 'PRIVATE', CURRENT_TIMESTAMP(3))
                """, id, name, ownerId);
        return id;
    }

    private String createBooking(String itemId, String resourceId, String slotId, String petId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/services/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(itemId, resourceId, slotId, petId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String bookingPayload(String itemId, String resourceId, String slotId, String petId) {
        String petClause = petId == null ? "null" : "\"" + petId + "\"";
        return """
                {"serviceItemId":"%s","resourceId":"%s","slotId":"%s","petId":%s,
                 "customerName":"张三","customerPhone":"13800000000","remark":"测试预约"}
                """.formatted(itemId, resourceId, slotId, petClause);
    }

    private String bookingPayloadWithPhone(String itemId, String resourceId, String slotId, String petId, String phone) {
        String petClause = petId == null ? "null" : "\"" + petId + "\"";
        return """
                {"serviceItemId":"%s","resourceId":"%s","slotId":"%s","petId":%s,
                 "customerName":"测试用户","customerPhone":"%s","remark":"并发测试"}
                """.formatted(itemId, resourceId, slotId, petClause, phone);
    }

    private int slotBookedCount(String slotId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT booked_count FROM service_slot WHERE id = ?", Integer.class, slotId);
        return c == null ? -1 : c;
    }

    private int bookingVersion(String bookingId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT version FROM service_booking WHERE id = ?", Integer.class, bookingId);
        return v == null ? -1 : v;
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
