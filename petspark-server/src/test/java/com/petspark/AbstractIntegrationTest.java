package com.petspark;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：连接本机 MySQL 8.0.40，复用同一可运行构建。
 *
 * <p>数据库连接走 {@code application-test.yml} / 进程环境变量：
 * <ul>
 *   <li>{@code DB_URL}、{@code DB_USERNAME}、{@code DB_PASSWORD} 由
 *       {@code scripts/load-local-env.ps1} 从 {@code .env.local} 注入当前进程，
 *       不进入源码或仓库；</li>
 *   <li>Flyway 在容器/应用启动时自动执行 V001 及后续迁移，验证空库与升级路径。</li>
 * </ul>
 *
 * <p>本类不拉取 Docker 镜像、不引入 Testcontainers：本机已具备 MySQL 8.0.40
 * 服务，CI/本地验证均复用同一实例（NFR-MNT-001 可重复构建）。
 *
 * <p>所有子类共享同一 Spring 上下文与同一数据库 schema。Flyway 已在 V001
 * 建立公共空表；测试间数据隔离由各测试自行清理或使用独立 schema 命名。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
}