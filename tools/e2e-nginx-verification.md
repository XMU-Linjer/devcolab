# Nginx 本地统一入口联调验证

这份清单用于验证本地统一入口：

```text
http://localhost:8088
  /             -> web/dist 前端静态资源
  /api/         -> Knowledge Core
  /ws/          -> Collaboration Gateway
  /nginx-health -> Nginx 健康检查
```

当前阶段 Core 和 Gateway 仍然是本机 Maven 进程，Nginx 运行在 Docker 容器中，通过 `host.docker.internal` 反代到宿主机端口：

```text
Nginx container -> host.docker.internal:8080 -> Knowledge Core
Nginx container -> host.docker.internal:8090 -> Collaboration Gateway
```

## 1. 构建前端静态资源

```powershell
cd web
npm run build
cd ..
```

Nginx 会把 `web/dist` 挂载到 `/usr/share/nginx/html`。

## 2. 启动基础设施与 Nginx

```powershell
docker compose up -d postgres redis nginx
docker compose ps
```

检查 Nginx：

```powershell
Invoke-WebRequest http://localhost:8088/nginx-health
```

预期返回：

```text
ok
```

如果本机 `6379` 已被其他 Redis 占用，可以临时换一个宿主机端口，例如：

```powershell
$env:REDIS_HOST_PORT="16379"
docker compose up -d redis nginx
```

此时后续启动 Core 和 Gateway 时也要使用同一个 Redis 端口：

```powershell
$env:DEVCOLLAB_REDIS_PORT="16379"
```

## 3. 启动 Knowledge Core

通过 Nginx 访问前端时，浏览器 Origin 是 `http://localhost:8088`，所以 Core 的允许来源也要设置成 8088：

```powershell
$env:DEVCOLLAB_WEB_ORIGIN="http://localhost:8088"
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_REDIS_HOST="localhost"
$env:DEVCOLLAB_REDIS_PORT="6379"

.\mvnw.cmd -pl knowledge-core spring-boot:run
```

## 4. 启动 Collaboration Gateway

```powershell
$env:DEVCOLLAB_CORE_BASE_URL="http://localhost:8080"
$env:DEVCOLLAB_REDIS_HOST="localhost"
$env:DEVCOLLAB_REDIS_PORT="6379"

.\mvnw.cmd -pl collaboration-gateway spring-boot:run
```

## 5. 自动验收

复用 Gateway 验收脚本，但把 HTTP 和 WebSocket 地址都切换到 Nginx：

```powershell
$env:DEVCOLLAB_CORE_BASE_URL="http://localhost:8088"
$env:DEVCOLLAB_GATEWAY_WS_URL="ws://localhost:8088"

node tools/e2e-gateway-check.mjs
```

验收内容：

- `/api/v1/auth/register` 经 Nginx 到 Core；
- `/api/v1/workspaces` 经 Nginx 到 Core；
- `/ws/documents/{documentId}` 经 Nginx WebSocket Upgrade 到 Gateway；
- 两个 WebSocket 客户端能看到在线成员；
- Block 编辑开始、停止状态能通过 Nginx 广播。

## 6. 浏览器验收

打开：

```text
http://localhost:8088
```

手动验证：

1. 登录或注册；
2. 进入文档工作台；
3. 打开右侧“协作”页签；
4. 另开一个窗口进入同一文档；
5. 验证在线成员和正在编辑状态。

## 7. 当前阶段不做

- 不做 HTTPS；
- 不做生产证书；
- 不做复杂限流；
- 不做前后端容器化镜像；
- 不把 Core/Gateway 放进 compose；
- 不做 Kubernetes / CI/CD。

当前目标只是让本地架构具备统一入口，并验证 HTTP 与 WebSocket 都能通过 Nginx。
