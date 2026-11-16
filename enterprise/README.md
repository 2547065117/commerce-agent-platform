# Commerce OS — Spring Boot + Vue 运营控制台

所有新增代码和运行数据均放在 `enterprise/`。根目录 `python/` 和 `java/` 为原有示例，未修改。

Agent 服务统一维护为 Python，模块对应和验证说明见 [Python 迁移说明](docs/python-migration.md)。

## 已实现

- Vue 3 响应式运营控制台：概览、推荐工作台、商品快照、调用记录、任务详情、安全审计。
- Java 17 + Spring Boot：服务端登录会话、BCrypt 密码、CSRF 保护、管理员接口访问控制、参数校验。
- 调用已有 Python Agent 服务，服务端保留模型密钥，浏览器不接触密钥。
- H2 文件数据库持久化：管理员、请求参数、完整响应、失败记录和操作审计。
- 区分完整成功 / 部分 Agent 降级 / 服务失败；设置连接与响应超时，最多三个并行推荐请求。
- 商品目录是推荐结果的只读快照，不宣称接入真实商品中心或库存系统。

## 本机运行

1. 启动原项目的 Python 服务，默认 `http://127.0.0.1:8000`，沿用 `python/.env` 中的模型配置。
2. 首次构建：在 `enterprise` 执行 `powershell -File build.ps1`，需要 Java 17+、Node.js 22+ 和 Maven 3.6.3+。当前电脑的脚本也可检测 IDEA 自带 Maven。
3. 双击 `enterprise/start.bat` 启动 Java 控制台，打开 `http://127.0.0.1:8080`。也可执行 `powershell -File start-all.ps1`，在后台启动两项服务；已有服务会复用。
4. 首次生成的管理员凭据保存在 `enterprise/backend/data/initial-admin.txt`。用户名 `admin`，随机密码；不提交到 Git。
5. 按 Ctrl+C 停止前台启动的服务。同一端口只能运行一个服务。

开发前端：`cd frontend`，执行 `npm run dev`。Vite 自动代理 `/api` 到 8080。生产构建将 Vue 输出到 Spring Boot 静态资源目录；同源部署无需开放跨域。

## 目录

```text
enterprise/
  backend/      Java 服务、接口、持久化、安全配置、集成测试
  frontend/     Vue 页面、样式、Vite 构建、锁文件
  docs/         架构与上线边界说明
  build.ps1     Windows 构建与测试
  start.bat     Windows 启动
  Dockerfile    前端 + Java 多阶段构建
  compose.yml   Java 控制台 + Python Agent 服务
```

## 服务端配置

| 环境变量 | 默认值 / 用途 |
|---|---|
| PORTAL_PORT | 8080 |
| PORTAL_BIND | 127.0.0.1；容器内设为 0.0.0.0 |
| AGENT_SERVICE_URL | http://127.0.0.1:8000 |
| PORTAL_ADMIN_PASSWORD | 首次建库时可指定至少 12 位密码；否则随机生成。已有用户不随该变量自动改密 |
| PORTAL_COOKIE_SECURE | false；通过 HTTPS 上线时设 true |
| PORTAL_DB_PASSWORD | 本地 H2 数据库密码；现有数据库改密需按数据库迁移流程进行 |

首次凭据文件用于本机交付，应妥善保存后移除。会话重启后失效，需要重新登录。当前仅管理员角色，未实现多角色账号管理。

## Docker

在 `enterprise` 执行 `docker compose up -d --build`。使用父目录 `python/.env` 配置模型，不复制密钥到前端或镜像。

页面默认只绑定服务器回环地址。远程访问可先用 SSH 端口转发；公网发布应配置 HTTPS 反向代理，不能直接公开 Python 服务和数据库。

查看首次凭据：`docker compose exec portal cat /app/data/initial-admin.txt`。数据保存在命名卷中，删除卷会删除账号和历史记录。

这是企业项目的可运行基础版本，尚未完成生产环境建设；详见 [架构与后续建设](docs/architecture.md)。
