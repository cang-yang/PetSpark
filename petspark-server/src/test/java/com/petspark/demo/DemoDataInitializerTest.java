package com.petspark.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petspark.auth.DemoUserProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DemoDataInitializerTest {

    private JdbcTemplate jdbc;
    private DemoDataProperties properties;
    private DemoUserProperties users;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:demo_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        jdbc.update("INSERT INTO sys_user (id, username, email) VALUES ('admin-id', 'admin', 'admin@petspark.local')");
        jdbc.update("INSERT INTO sys_user (id, username, email) VALUES ('member-id', 'demo', 'demo@petspark.local')");

        properties = new DemoDataProperties();
        properties.setFutureDays(2);
        users = new DemoUserProperties();
    }

    @Test
    void firstRunSeedsUsefulDataSecondRunDoesNotGrowAndCustomDataSurvives() {
        jdbc.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order)
                VALUES ('custom-category', 'CUSTOM', '我的自建分类', 'ACTIVE', 99)
                """);
        DemoDataInitializer initializer = initializer();

        initializer.initialize();
        Map<String, Integer> first = counts();
        initializer.initialize();

        assertThat(counts()).isEqualTo(first);
        assertThat(first).containsEntry("pet_breed", 4)
                .containsEntry("pet", 4)
                .containsEntry("goods_category", 4)
                .containsEntry("goods", 8)
                .containsEntry("boarding_room", 3)
                .containsEntry("service_slot", 4)
                .containsEntry("operation_banner", 4)
                .containsEntry("community_post", 2)
                .containsEntry("notification", 1);
        assertThat(jdbc.queryForObject(
                "SELECT name FROM goods_category WHERE id = 'custom-category'", String.class))
                .isEqualTo("我的自建分类");
    }

    @Test
    void failsClosedWhenConfiguredAccountsDoNotMatchExactly() {
        users.setMemberEmail("wrong@petspark.local");

        assertThatThrownBy(() -> initializer().initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("演示用户账号未唯一匹配");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet", Integer.class)).isZero();
    }

    @Test
    void generatedIdsAreStable() {
        assertThat(DemoDataInitializer.id("pet:member:doubao"))
                .isEqualTo(DemoDataInitializer.id("pet:member:doubao"))
                .isNotEqualTo(DemoDataInitializer.id("pet:public:orange"));
    }

    private DemoDataInitializer initializer() {
        return new DemoDataInitializer(properties, users, jdbc,
                Clock.fixed(Instant.parse("2026-07-11T08:00:00Z"), ZoneOffset.UTC));
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String table : new String[]{"pet_breed", "pet", "goods_category", "goods",
                "boarding_room", "service_item", "service_resource", "service_slot",
                "operation_banner", "community_post", "notification"}) {
            values.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return values;
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(36) PRIMARY KEY, username VARCHAR(64), email VARCHAR(128))");
        jdbc.execute("""
                CREATE TABLE pet_breed (
                    id VARCHAR(36) PRIMARY KEY, species VARCHAR(32), name VARCHAR(64), description VARCHAR(500),
                    status VARCHAR(16), deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE pet (
                    id VARCHAR(36) PRIMARY KEY, name VARCHAR(64), species VARCHAR(32), breed_id VARCHAR(36),
                    sex VARCHAR(16), birth_date DATE, description VARCHAR(1000), ownership_type VARCHAR(16),
                    owner_user_id VARCHAR(36), adoption_status VARCHAR(32), boarding_status VARCHAR(32),
                    public_status VARCHAR(16), info_updated_at TIMESTAMP, deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE goods_category (
                    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) UNIQUE, name VARCHAR(80), status VARCHAR(16),
                    sort_order INT, deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE goods (
                    id VARCHAR(36) PRIMARY KEY, category_id VARCHAR(36), sku VARCHAR(64) UNIQUE, name VARCHAR(120),
                    description VARCHAR(1000), price DECIMAL(12,2), stock INT, status VARCHAR(16),
                    deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE boarding_room (
                    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) UNIQUE, name VARCHAR(120), capacity INT,
                    status VARCHAR(16), description VARCHAR(500), deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE service_item (
                    id VARCHAR(36) PRIMARY KEY, kind VARCHAR(16), code VARCHAR(64) UNIQUE, name VARCHAR(120),
                    description VARCHAR(1000), qualification VARCHAR(500), availability_note VARCHAR(500),
                    exception_rule VARCHAR(500), base_price DECIMAL(12,2), status VARCHAR(16),
                    deleted_at TIMESTAMP NULL)
                """);
        jdbc.execute("""
                CREATE TABLE service_resource (
                    id VARCHAR(36) PRIMARY KEY, service_item_id VARCHAR(36), name VARCHAR(120),
                    qualification VARCHAR(500), availability_note VARCHAR(500), exception_rule VARCHAR(500),
                    status VARCHAR(16), capacity INT)
                """);
        jdbc.execute("""
                CREATE TABLE service_slot (
                    id VARCHAR(36) PRIMARY KEY, resource_id VARCHAR(36), slot_date DATE, start_at TIMESTAMP,
                    end_at TIMESTAMP, capacity INT, booked_count INT, status VARCHAR(16))
                """);
        jdbc.execute("""
                CREATE TABLE operation_banner (
                    id VARCHAR(36) PRIMARY KEY, title VARCHAR(120), subtitle VARCHAR(255), image_url VARCHAR(500),
                    target_type VARCHAR(32), target_url VARCHAR(500), status VARCHAR(16), sort_order INT,
                    starts_at TIMESTAMP, ends_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE community_post (
                    id VARCHAR(36) PRIMARY KEY, author_user_id VARCHAR(36), title VARCHAR(120), content VARCHAR(2000),
                    status VARCHAR(16), like_count INT, favorite_count INT, comment_count INT)
                """);
        jdbc.execute("""
                CREATE TABLE notification (
                    id VARCHAR(36) PRIMARY KEY, recipient_id VARCHAR(36), type VARCHAR(32), title VARCHAR(128),
                    content VARCHAR(512), business_type VARCHAR(64), business_id VARCHAR(64))
                """);
    }
}
