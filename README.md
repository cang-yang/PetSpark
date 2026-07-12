# PetSpark（派宠）

![PetSpark 智慧宠物生活平台产品海报](docs/assets/petspark-product-poster.png)

PetSpark 是面向宠物家庭与平台运营人员的智慧宠物生活平台。项目将宠物档案、健康记录、领养救助、寄养与医疗预约、社区、商城和 AI 宠物伙伴整合到一个可实际运行、可持续部署的产品中。

线上体验：[https://petspark.canggo.com](https://petspark.canggo.com)

## 核心能力

- 宠物档案：基础资料、图片、生命周期状态及健康记录。
- 服务预约：寄养、训练、美容、医疗服务与个人预约管理。
- 领养与救助：领养申请、审核、交接以及流浪宠物线索。
- 社区与商城：内容发布、评论互动、商品浏览、购物车和订单。
- AI 宠物伙伴：讯飞星火原生流式对话、护理问答和基于平台真实候选项的智能推荐。
- 平台管理：用户与角色、业务字典、商品、宠物、订单、内容、横幅和统计仪表盘。
- 通知与账号：站内通知、邮箱验证码注册确认与密码找回。

> AI 输出仅提供日常信息与陪伴建议，不替代兽医诊断；紧急或持续异常情况应联系专业机构。

## 技术架构

| 层级 | 技术 |
|---|---|
| 前端 | Vue 2.7、Vue Router、Vuex、Element UI、ECharts |
| 后端 | Java 17、Spring Boot 3.5、Spring Security、MyBatis-Plus |
| 数据 | MySQL 8、Flyway、AES-GCM、BCrypt |
| AI | 讯飞星火 OpenAI 兼容接口、原生 SSE 流式转发 |
| 工程 | Maven Wrapper、Jest、JUnit、WireMock、GitHub Actions |
| 部署 | Docker、Docker Compose、GHCR、1Panel/OpenResty、HTTPS |

```text
Browser → OpenResty/HTTPS → Web Nginx → Spring Boot → MySQL
                                      └→ 讯飞星火（SSE）
```

## 目录结构

```text
petspark-server/   Spring Boot API、业务、迁移与测试
petspark-web/      Vue 用户端与管理端
deploy/            生产部署脚本、环境模板和操作手册
scripts/           本地环境检查与启动脚本
.github/workflows/ CI 与生产 CD
```

## 本地启动

环境基线：JDK 17、Node.js 22+、MySQL 8.0+。

1. 复制本地配置模板并填写自己的数据库、SMTP 和星火配置：

```powershell
Copy-Item .env.example .env.local
```

2. 启动后端：

```powershell
.\scripts\start-local-server.ps1
```

3. 启动前端：

```powershell
Set-Location petspark-web
npm ci
npm run serve
```

默认访问地址为 `http://localhost:8081`，后端为 `http://localhost:8080`。本地脚本只向当前进程注入环境变量，不修改系统 Java、Node 或 MySQL 配置。

## 环境配置

- `.env.local`：本地数据库、邮件和演示数据配置，已被 Git 忽略。
- `.env.spark.local`：可选的本地星火凭据，已被 Git 忽略。
- `.env.example`：可提交的字段示例，不包含真实密钥。
- `deploy/.env.production.example`：生产环境模板。

真实密码、SMTP 授权码、JWT 密钥与星火 API Password 禁止提交仓库。生产配置仅保存在 GitHub Secrets 与 VPS `/opt/petspark/.env`。

## 演示数据

设置以下开关后，应用会幂等创建演示账号和丰富业务数据，不覆盖用户自行创建的内容：

```dotenv
PETSPARK_DEMO_USERS_ENABLED=true
PETSPARK_DEMO_DATA_ENABLED=true
```

账号密码必须通过本地或生产环境变量配置，不在公开文档中提供固定密码。

## 测试

```powershell
.\mvnw.cmd verify
Set-Location petspark-web
npm ci
npm run test:unit -- --runInBand
npm run build
```

测试覆盖核心业务正常流程、参数边界、权限、持久化、AI 安全策略、星火流式契约以及关键前端交互。

## 生产部署

`main` 通过 CI 后，CD 会构建不可变前后端镜像、推送 GHCR，并通过 SSH 发布到 VPS。部署过程包含数据库备份、健康检查和失败回滚。

完整流程见 [生产部署手册](deploy/README.md)。生产服务只向本机回环地址暴露 Web 入口，MySQL 与后端端口不直接开放公网。

## 安全设计

- BCrypt 密码哈希、短期 JWT 与可撤销刷新令牌。
- RBAC 权限控制与前后端双重管理入口保护。
- 文件类型、大小、文件名和路径穿越校验。
- AI 输入脱敏、提示词注入拦截、限流、超时、降级与审计。
- 敏感字段 AES-GCM 加密，日志不记录密码、令牌或完整隐私数据。

## License

本项目用于教学实训与学习展示。第三方服务、字体和素材的使用应遵守各自许可与服务条款。
