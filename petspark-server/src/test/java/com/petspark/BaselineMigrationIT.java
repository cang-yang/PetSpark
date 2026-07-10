package com.petspark;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 验证 V001 基线迁移在本机 MySQL 8.0.40 上正确执行：audit_log 与 outbox_event
 * 两张表存在且列结构符合 05-数据库设计说明书 §2/§9。
 *
 * <p>继承 {@link AbstractIntegrationTest}，连接信息由
 * {@code scripts/load-local-env.ps1} 从 {@code .env.local} 注入进程环境变量。
 * 替换原 {@code EmptyDatabaseFlywayIT}：旧测试只做最小 Flyway 启动校验，本测试
 * 显式断言公共表结构与索引，锁定 V001 契约。
 */
@SpringBootTest
class BaselineMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void auditLogTableCreatedWithExpectedColumns() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'audit_log'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'audit_log'",
                Integer.class);
        // id, request_id, actor_id, actor_role, module, action, object_type,
        // object_id, result, reason_code, ip_hash, created_at
        assertThat(columnCount).isEqualTo(12);
    }

    @Test
    void outboxEventTableCreatedWithExpectedColumns() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'outbox_event'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'outbox_event'",
                Integer.class);
        // id, event_type, aggregate_type, aggregate_id, payload, status,
        // attempt_count, next_attempt_at, created_at, processed_at
        assertThat(columnCount).isEqualTo(10);
    }

    @Test
    void outboxPendingIndexExists() {
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'outbox_event' "
                        + "AND index_name = 'idx_outbox_pending'",
                Integer.class);
        assertThat(indexCount).isGreaterThan(0);
    }

    @Test
    void auditActorTimeIndexExists() {
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'audit_log' "
                        + "AND index_name = 'idx_audit_actor_time'",
                Integer.class);
        assertThat(indexCount).isGreaterThan(0);
    }

    @Test
    void defaultBannerUsesARepositoryOwnedImage() {
        String imageUrl = jdbcTemplate.queryForObject(
                "SELECT image_url FROM operation_banner WHERE id = '00000000-0000-0000-0000-000000000281'",
                String.class);
        assertThat(imageUrl).startsWith("/").doesNotContain("placehold.co");
    }
}
