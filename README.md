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

运行后端（推荐统一入口，会加载 `.env.local`，并在存在时合并被 Git 忽略的 `.env.spark.local`）：

```powershell
.\scripts\start-local-server.ps1
```

需要避开已占用端口时可使用 `-Port 8082`。脚本只向当前进程注入环境变量，不修改用户或系统环境。

运行前端：

```powershell
Set-Location petspark-web
npm ci
npm run serve
```

默认情况下 AI 功能关闭；启动基础应用不需要星火密钥。真实密钥只能放入被 Git 忽略的 `.env.spark.local` 或部署环境变量，不得写入源码、示例配置或日志。本地文件只需提供 `SPARK_API_PASSWORD`、`SPARK_BASE_URL` 和 `SPARK_MODEL`；密码非空时统一加载脚本会在当前进程启用网关。若需要强制关闭，可在 `.env.spark.local` 中显式设置 `SPARK_ENABLED=false`。

演示账号和演示业务数据同样默认关闭。需要本地展示时，在被 Git 忽略的 `.env.local` 中同时设置 `PETSPARK_DEMO_USERS_ENABLED=true`、两组演示账号密码以及 `PETSPARK_DEMO_DATA_ENABLED=true`；可用 `PETSPARK_DEMO_DATA_FUTURE_DAYS` 控制补齐的未来预约天数。演示填充使用稳定标识并重复执行安全，不会覆盖或删除自行创建的数据；不得把真实密码写入仓库。

用户注册继续保留用户名与邮箱，登录支持用户名或邮箱。注册确认和找回密码验证码可通过真实 SMTP 邮件发送；需要启用时，在 `.env.local` 中配置 `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD` 和对应发件地址，并设置 `REGISTRATION_MAIL_ENABLED=true`、`PASSWORD_RESET_MAIL_ENABLED=true`。网易 163 等使用 465 端口的服务应启用 `MAIL_SSL_ENABLED=true` 并关闭 STARTTLS。未配置或投递失败时不会在响应或日志中输出验证码。

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
