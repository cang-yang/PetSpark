# PetSpark 生产部署手册

适用环境：`PetSpark.canggo.com`、VPS `23.238.7.190:22222`、1Panel、部署目录 `/opt/petspark`。

## 1. 初始化 VPS

先在 1Panel 确认已安装 Docker 和 Docker Compose v2。使用 root 终端执行：

```bash
curl -fsSL https://raw.githubusercontent.com/cang-yang/PetSpark/main/deploy/bootstrap-vps.sh -o /tmp/petspark-bootstrap.sh
sudo bash /tmp/petspark-bootstrap.sh deploy
sudo -iu deploy docker compose version
```

## 2. 创建部署 SSH 密钥

在本地 Windows PowerShell 执行：

```powershell
ssh-keygen -t ed25519 -C "petspark-github-actions" -f "$env:USERPROFILE\.ssh\petspark_deploy"
```

将 `petspark_deploy.pub` 整行内容追加到 VPS：

```bash
sudo tee -a /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
```

粘贴公钥后按 `Ctrl+D`。私钥 `petspark_deploy` 只放 GitHub Secret，不放 VPS、Git 仓库或聊天。

## 3. 配置 GHCR

创建仅含 `read:packages` 权限的 GitHub classic PAT，在 VPS 执行：

```bash
sudo -iu deploy bash
read -rsp "GHCR token: " GHCR_TOKEN; echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u cang-yang --password-stdin
unset GHCR_TOKEN
exit
```

PAT 不写入 `/opt/petspark/.env`。

## 4. 创建生产环境文件

```bash
sudo -iu deploy bash
cd /opt/petspark
curl -fsSL https://raw.githubusercontent.com/cang-yang/PetSpark/main/deploy/.env.production.example -o .env
chmod 600 .env
```

多次执行 `openssl rand -base64 36`，分别生成数据库密码、root 密码、JWT 密钥、AI 消息密钥、健康详情密钥、手机号密钥和两组演示账号密码，替换全部 `CHANGE_ME`。这些加密密钥一旦产生生产数据便不得随意更换，否则旧密文无法解密。

网易 SMTP 必须使用新生成且未曾出现在聊天或日志里的授权码：

```dotenv
MAIL_USERNAME=your-account@163.com
MAIL_PASSWORD=your-new-client-password
REGISTRATION_MAIL_ENABLED=true
REGISTRATION_MAIL_FROM=your-account@163.com
PASSWORD_RESET_MAIL_ENABLED=true
PASSWORD_RESET_MAIL_FROM=your-account@163.com
```

如启用星火，再填写 `SPARK_API_PASSWORD` 并设置 `SPARK_ENABLED=true`。

## 5. 配置 GitHub Secrets

仓库 Settings → Secrets and variables → Actions：

| Secret | 内容 |
|---|---|
| `DEPLOY_HOST` | `23.238.7.190` |
| `DEPLOY_PORT` | `22222` |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | 本地 `petspark_deploy` 私钥完整内容 |
| `DEPLOY_HOST_KEY` | SSH known_hosts 整行 |

同时在 Settings → Secrets and variables → Actions → Variables 创建仓库变量 `CD_ENABLED=true`。在 VPS 与 Secrets 尚未准备好之前不要启用；工作流默认保持跳过状态。

在 VPS 核对真实主机密钥并生成 known_hosts 行：

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
sudo awk '{print "[23.238.7.190]:22222 "$1" "$2}' /etc/ssh/ssh_host_ed25519_key.pub
```

将第二条命令输出整行保存为 `DEPLOY_HOST_KEY`。不要关闭主机密钥检查。建议创建 GitHub `production` Environment。

## 6. 首次发布

打开 GitHub Actions → CD → Run workflow。工作流会构建两个镜像、推送 GHCR、上传 Compose 文件并部署。

```bash
cd /opt/petspark
docker compose --env-file .env -f docker-compose.prod.yml ps
```

首次空库启动会由 Flyway 建表，演示初始化器生成展示数据。不会上传本地数据库或本地上传目录。

## 7. 配置 1Panel HTTPS

1. 1Panel → 网站 → 创建反向代理网站。
2. 域名填写 `PetSpark.canggo.com`。
3. 代理地址填写 `http://127.0.0.1:18081`。
4. 申请 Let's Encrypt 证书并开启 HTTP 自动跳转 HTTPS。
5. 不在防火墙公开 18081、8080、3306。

验证：

```bash
curl -I https://PetSpark.canggo.com
curl -fsS https://PetSpark.canggo.com/actuator/health
```

## 8. 回滚和备份

上一成功 SHA 保存在 `/opt/petspark/.last-successful-tag`。部署失败会自动恢复。手动回滚：

```bash
cd /opt/petspark
PREVIOUS_SHA=替换为40位提交SHA
IMAGE_TAG="$PREVIOUS_SHA" docker compose --env-file .env -f docker-compose.prod.yml pull
IMAGE_TAG="$PREVIOUS_SHA" docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
printf '%s\n' "$PREVIOUS_SHA" > .last-successful-tag
```

发布前数据库备份位于 `/opt/petspark/backups`，默认保留最近 7 份。MySQL 卷和上传卷仍需由 1Panel 定期备份。
