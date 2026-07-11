package com.petspark.demo;

import com.petspark.auth.DemoUserProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.Transactional;

/**
 * 可选的本地展示数据填充器。
 *
 * <p>所有记录使用固定命名空间生成 ID，并且只在对应自然键或 ID 不存在时插入；
 * 不覆盖、不删除用户自行创建的数据。日期型资源每次启动补齐未来窗口。
 */
public class DemoDataInitializer implements ApplicationRunner, Ordered {

    static final UUID NAMESPACE = UUID.fromString("ebdf31d0-3e26-4f17-bd39-dbd878d4a21f");

    private final DemoDataProperties properties;
    private final DemoUserProperties demoUsers;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    DemoDataInitializer(
            DemoDataProperties properties,
            DemoUserProperties demoUsers,
            JdbcTemplate jdbc,
            Clock clock) {
        this.properties = properties;
        this.demoUsers = demoUsers;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize();
    }

    @Override
    public int getOrder() {
        return 200;
    }

    void initialize() {
        String adminId = exactUserId(demoUsers.getAdminUsername(), demoUsers.getAdminEmail(), "管理员");
        String memberId = exactUserId(demoUsers.getMemberUsername(), demoUsers.getMemberEmail(), "演示用户");

        seedBreedsAndPets(memberId);
        seedCatalog();
        seedRooms();
        seedBeautyAvailability();
        seedBanners();
        seedCommunityAndNotifications(adminId, memberId);
    }

    private void seedBreedsAndPets(String memberId) {
        String golden = ensureBreed("DOG", "金毛寻回犬", "温和亲人，适合家庭陪伴与户外活动。");
        String corgi = ensureBreed("DOG", "威尔士柯基犬", "聪明活泼，需要规律运动与体重管理。");
        String british = ensureBreed("CAT", "英国短毛猫", "性格稳定，适合室内陪伴。");
        String domestic = ensureBreed("CAT", "中华田园猫", "适应力强，活泼亲人。");

        insertPet("pet:member:doubao", "豆包", "DOG", corgi, "MALE", LocalDate.now(clock).minusYears(3),
                "演示用户的家庭成员，喜欢散步和嗅闻游戏。", "USER", memberId,
                "NOT_FOR_ADOPTION", "PRIVATE");
        insertPet("pet:public:orange", "橘子", "CAT", domestic, "FEMALE", LocalDate.now(clock).minusYears(2),
                "亲人爱撒娇，已完成基础体检，正在等待稳定的新家。", "PLATFORM", null,
                "AVAILABLE", "PUBLISHED");
        insertPet("pet:public:cola", "可乐", "DOG", golden, "MALE", LocalDate.now(clock).minusYears(4),
                "性格开朗，喜欢和人互动，适合有规律陪伴时间的家庭。", "PLATFORM", null,
                "AVAILABLE", "PUBLISHED");
        insertPet("pet:public:milky", "奶糖", "CAT", british, "FEMALE", LocalDate.now(clock).minusYears(1),
                "安静好奇，已完成驱虫和疫苗基础评估。", "PLATFORM", null,
                "AVAILABLE", "PUBLISHED");
    }

    private void seedCatalog() {
        String food = ensureCategory("DEMO-FOOD", "主粮与零食", 10);
        String daily = ensureCategory("DEMO-DAILY", "日常用品", 20);
        String care = ensureCategory("DEMO-CARE", "健康护理", 30);

        insertGoods("goods:dog-food", food, "DEMO-DOG-FOOD-01", "鲜肉低敏成犬粮",
                "鸡肉与鱼肉双蛋白配方，适合日常体重管理。", new BigDecimal("168.00"), 46);
        insertGoods("goods:cat-food", food, "DEMO-CAT-FOOD-01", "室内猫营养主粮",
                "兼顾毛球管理与肠胃舒适的日常配方。", new BigDecimal("139.00"), 38);
        insertGoods("goods:treat", food, "DEMO-TREAT-01", "冻干鸡胸训练奖励",
                "单一肉源、小块易喂，适合训练奖励。", new BigDecimal("39.90"), 82);
        insertGoods("goods:bed", daily, "DEMO-BED-01", "云朵可拆洗宠物窝",
                "柔软支撑，外套可拆洗，适合猫咪与中小型犬。", new BigDecimal("129.00"), 24);
        insertGoods("goods:leash", daily, "DEMO-LEASH-01", "夜行反光牵引套装",
                "胸背受力均匀，反光织带提升夜间可见性。", new BigDecimal("79.00"), 55);
        insertGoods("goods:toy", daily, "DEMO-TOY-01", "嗅闻益智玩具",
                "通过藏食与嗅闻互动丰富宠物日常活动。", new BigDecimal("49.00"), 61);
        insertGoods("goods:tooth", care, "DEMO-CARE-01", "宠物口腔护理套装",
                "软毛牙刷搭配宠物专用洁齿凝胶。", new BigDecimal("59.00"), 31);
        insertGoods("goods:comb", care, "DEMO-CARE-02", "圆头去浮毛梳",
                "圆润梳齿减少拉扯，适合日常梳理。", new BigDecimal("45.00"), 44);
    }

    private void seedRooms() {
        insertRoom("room:sunshine", "DEMO-SUNSHINE", "阳光陪伴房", 4,
                "采光充足，配备独立休息区与每日活动记录。");
        insertRoom("room:quiet-cat", "DEMO-QUIET-CAT", "静谧猫咪房", 3,
                "猫犬分区，设有躲藏空间与立体活动设施。");
        insertRoom("room:garden", "DEMO-GARDEN", "庭院活力房", 5,
                "适合中大型犬，含分时段户外活动安排。");
    }

    private void seedBeautyAvailability() {
        String itemId = ensureServiceItem();
        String resourceId = id("service-resource:groomer-lin");
        if (!exists("service_resource", resourceId)) {
            jdbc.update("""
                    INSERT INTO service_resource
                        (id, service_item_id, name, qualification, availability_note, exception_rule, status, capacity)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 2)
                    """, resourceId, itemId, "林老师 · 温和洗护",
                    "持证宠物美容师，五年犬猫洗护经验", "每日 10:00-17:00，需提前预约",
                    "严重皮肤异常或明显应激时建议先行就医评估");
        }

        int days = Math.max(1, Math.min(properties.getFutureDays(), 30));
        LocalDate firstDay = LocalDate.now(clock).plusDays(1);
        for (int day = 0; day < days; day++) {
            LocalDate date = firstDay.plusDays(day);
            insertSlot(resourceId, date, LocalTime.of(10, 0), LocalTime.of(11, 30));
            insertSlot(resourceId, date, LocalTime.of(14, 0), LocalTime.of(15, 30));
        }
    }

    private void seedBanners() {
        insertBanner("banner:boarding", "安心寄养计划", "熟悉的照护节奏，让短暂分别也安心",
                "/demo/banners/banner-boarding-comfort.webp", "SERVICE", "/boarding/new", 10);
        insertBanner("banner:grooming", "温柔焕新护理", "专业洗护与造型，让舒适从每个细节开始",
                "/demo/banners/banner-grooming-care.webp", "SERVICE", "/beauty", 20);
        insertBanner("banner:wellness", "健康守护日", "体检、护理问答与就医建议一站了解",
                "/demo/banners/banner-vet-wellness.webp", "SERVICE", "/medical", 30);
        insertBanner("banner:adoption", "遇见命中注定的它", "认真了解、彼此选择，给陪伴一个温暖起点",
                "/demo/banners/banner-adoption-day.webp", "ADOPTION", "/adoptions", 40);
    }

    private void seedCommunityAndNotifications(String adminId, String memberId) {
        insertPost("post:welcome", adminId, "欢迎来到 PetSpark 派宠社区",
                "在这里分享陪伴日常、护理心得和领养故事。请尊重每一位用户，也尊重每一个生命。", 6, 3, 0);
        insertPost("post:member-walk", memberId, "豆包今天完成了第一次嗅闻散步",
                "把散步速度交给它以后，回家明显更放松了。原来慢慢闻也是很重要的运动。", 12, 5, 0);
        insertNotification("notification:member:welcome", memberId, "SYSTEM", "欢迎加入派宠",
                "豆包的档案已经准备好，去看看今天适合为它做些什么吧。", "PROFILE", memberId);
    }

    private String exactUserId(String username, String email, String label) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE username = ? AND email = ?", String.class, username, email);
        if (ids.size() != 1) {
            throw new IllegalStateException(label + "账号未唯一匹配，请先启用并正确配置 petspark.demo-users");
        }
        return ids.get(0);
    }

    private String ensureBreed(String species, String name, String description) {
        List<String> ids = jdbc.queryForList("""
                SELECT id FROM pet_breed
                WHERE species = ? AND name = ? AND deleted_at IS NULL
                """, String.class, species, name);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        String id = id("breed:" + species + ":" + name);
        jdbc.update("""
                INSERT INTO pet_breed (id, species, name, description, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, id, species, name, description);
        return id;
    }

    private void insertPet(String key, String name, String species, String breedId, String sex,
            LocalDate birthDate, String description, String ownershipType, String ownerId,
            String adoptionStatus, String publicStatus) {
        String id = id(key);
        if (exists("pet", id)) {
            return;
        }
        jdbc.update("""
                INSERT INTO pet
                    (id, name, species, breed_id, sex, birth_date, description, ownership_type,
                     owner_user_id, adoption_status, boarding_status, public_status, info_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NONE', ?, CURRENT_TIMESTAMP(3))
                """, id, name, species, breedId, sex, Date.valueOf(birthDate), description,
                ownershipType, ownerId, adoptionStatus, publicStatus);
    }

    private String ensureCategory(String code, String name, int sortOrder) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM goods_category WHERE code = ? AND deleted_at IS NULL", String.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        String id = id("goods-category:" + code);
        jdbc.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                """, id, code, name, sortOrder);
        return id;
    }

    private void insertGoods(String key, String categoryId, String sku, String name,
            String description, BigDecimal price, int stock) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goods WHERE sku = ? AND deleted_at IS NULL", Integer.class, sku);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO goods
                    (id, category_id, sku, name, description, price, stock, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, id(key), categoryId, sku, name, description, price, stock);
    }

    private void insertRoom(String key, String code, String name, int capacity, String description) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM boarding_room WHERE code = ? AND deleted_at IS NULL", Integer.class, code);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO boarding_room (id, code, name, capacity, status, description)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """, id(key), code, name, capacity, description);
    }

    private String ensureServiceItem() {
        String code = "DEMO-BEAUTY-GENTLE";
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM service_item WHERE code = ? AND deleted_at IS NULL", String.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        String id = id("service-item:gentle-groom");
        jdbc.update("""
                INSERT INTO service_item
                    (id, kind, code, name, description, qualification, availability_note,
                     exception_rule, base_price, status)
                VALUES (?, 'BEAUTY', ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, id, code, "温和洗护护理", "包含基础清洁、低噪吹干、梳毛与耳爪护理。",
                "持证宠物美容师；工具一宠一消毒", "未来七天开放预约",
                "出现皮肤异常或明显应激时暂停服务", new BigDecimal("128.00"));
        return id;
    }

    private void insertSlot(String resourceId, LocalDate date, LocalTime start, LocalTime end) {
        String key = "slot:" + resourceId + ":" + date + ":" + start;
        String id = id(key);
        if (exists("service_slot", id)) {
            return;
        }
        LocalDateTime startAt = date.atTime(start);
        LocalDateTime endAt = date.atTime(end);
        jdbc.update("""
                INSERT INTO service_slot
                    (id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status)
                VALUES (?, ?, ?, ?, ?, 2, 0, 'OPEN')
                """, id, resourceId, Date.valueOf(date), Timestamp.valueOf(startAt), Timestamp.valueOf(endAt));
    }

    private void insertBanner(String key, String title, String subtitle, String imageUrl,
            String targetType, String targetUrl, int sortOrder) {
        String id = id(key);
        if (exists("operation_banner", id)) {
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO operation_banner
                    (id, title, subtitle, image_url, target_type, target_url, status, sort_order,
                     starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, id, title, subtitle, imageUrl, targetType, targetUrl, sortOrder,
                Timestamp.valueOf(now.minusDays(1)), Timestamp.valueOf(now.plusYears(2)));
    }

    private void insertPost(String key, String authorId, String title, String content,
            int likes, int favorites, int comments) {
        String id = id(key);
        if (!exists("community_post", id)) {
            jdbc.update("""
                    INSERT INTO community_post
                        (id, author_user_id, title, content, status, like_count, favorite_count, comment_count)
                    VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)
                    """, id, authorId, title, content, likes, favorites, comments);
        }
    }

    private void insertNotification(String key, String recipientId, String type, String title,
            String content, String businessType, String businessId) {
        String id = id(key);
        if (!exists("notification", id)) {
            jdbc.update("""
                    INSERT INTO notification
                        (id, recipient_id, type, title, content, business_type, business_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, recipientId, type, title, content, businessType, businessId);
        }
    }

    private boolean exists(String table, String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    static String id(String key) {
        return UUID.nameUUIDFromBytes((NAMESPACE + ":" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
