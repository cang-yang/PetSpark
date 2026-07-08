# PetSpark（派宠）

PetSpark 是前后端分离的智慧宠物管理平台。本分支仅提供可重复构建的基础脚手架，不包含业务功能。

## 技术基线

- JDK 17（当前本地验证版本：Oracle JDK 17.0.12+8）
- Maven 3.9.16（Maven Wrapper）
- Spring Boot 3.5.15、Spring AI 1.1.8、MyBatis-Plus 3.5.16
- Node.js 22 或更高（当前本地验证版本：24.14.1）、Vue 2.7.16、Element UI 2.15.14
- MySQL 8.0 或更高（当前本地验证版本：MySQL 8.0.40）

## 本地运行

PowerShell 中可先检查本机环境：

```powershell
. .\scripts\petspark-env.ps1
```

该脚本只校验本机已有的 Java、Node 和 MySQL，不修改系统环境变量。默认复用正在运行的本机 `MySQL80` 服务。

运行后端：

```powershell
.\mvnw.cmd -pl petspark-server spring-boot:run
```

运行前端：

```powershell
Set-Location petspark-web
npm ci
npm run serve
```

默认情况下 AI 功能关闭；启动基础应用不需要星火密钥。真实密钥只能放入被 Git 忽略的 `.env.spark.local` 或部署环境变量，不得写入源码、示例配置或日志。

## 验证命令

```powershell
. .\scripts\petspark-env.ps1
.\mvnw.cmd verify
Set-Location petspark-web
npm ci
npm run test:unit -- --runInBand
npm run build
```

执行后端 `verify` 前需要确认本机 MySQL 可连接。环境变量示例见 `.env.example`。`docker-compose.yml` 仅作为其他机器缺少 MySQL 时的备用方案。
