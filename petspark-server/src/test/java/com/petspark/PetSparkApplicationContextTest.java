package com.petspark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用在禁用 AI、连接本机 MySQL 的最小配置下应能启动。{@code @ActiveProfiles("test")}
 * 复用 {@code application-test.yml}（禁用 AI、启用 Flyway），配合进程环境变量
 * {@code DB_URL/DB_USERNAME/DB_PASSWORD}（由 {@code scripts/load-local-env.ps1} 从
 * {@code .env.local} 注入）。启动成功即断言通过。
 *
 * <p>原版通过 {@code spring.autoconfigure.exclude} 排除 DataSource/Flyway，仅验证
 * 无数据库上下文启动。本版贴近真实部署：验证带数据库的最小上下文也能启动，
 * 与 BaselineMigrationIT 共同锁定“本机构建可连接本地 MySQL”这一可重复构建约束。
 */
@SpringBootTest
@ActiveProfiles("test")
class PetSparkApplicationContextTest {

    @Test
    void applicationContextLoads() {
        // Starting the Spring context is the assertion.
    }
}