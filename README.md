# DevCollab

DevCollab 是面向软件项目团队的工程知识协作平台。它把需求、架构、接口和代码变更放进同一套权限、版本、评审与证据链中，而不是只提供通用 Markdown 编辑器。

当前仓库已经形成可运行的核心闭环：登录 → 工作区与成员权限 → 文档树 → Tiptap Block 编辑 → WebSocket 实时协作 → gRPC 写入 Core → 评审发布 → Kafka 异步投影 → 搜索、通知与 MinIO 快照。Git 工程知识模块已提供供应商无关的仓库、Commit/PR、Diff 和代码路径关联契约。

## 当前能力

- 认证安全：Spring Security、BCrypt、JWT Access Token、HttpOnly Refresh Cookie、CSRF、Origin 校验、登录限流。
- 工作区权限：ADMIN/MEMBER 两种可扩展角色、成员邀请、角色调整和移除。
- 文档治理：九种文档类型、文档树、状态机、评审、不可变发布版本、历史快照、Review Issue。
- 结构化编辑：Vue 3 + Tiptap，支持段落、标题、代码和待办 Block；稳定 `blockId + version` 乐观锁。
- 实时协作：独立 Collaboration Gateway、WebSocket 房间、Presence、操作 ACK、幂等、冲突和断线增量恢复。
- 内部 RPC：独立 Protobuf 契约、Gateway gRPC Client、Core gRPC Server；HTTP 兼容通道暂时保留。
- 异步平台：Transactional Outbox、Kafka 多 Topic、独立 Worker、Consumer Inbox、重试与 DLQ。
- 搜索与对象：PostgreSQL 搜索基线、Elasticsearch 可重建投影、MinIO 发布快照。
- 缓存与协调：Redis 登录/操作限流、去重、Presence；Caffeine + Redis 多级缓存和 Kafka 跨节点失效。
- 通知：评审提交与发布通知、未读状态和前端通知中心。
- Git 工程知识：工作区仓库登记、标准化 Commit/PR Diff 接入、文档/Block 代码路径绑定、变更影响查询。
- 可观测性：OpenTelemetry、Prometheus、Loki、Tempo、Grafana 本地编排和验收工具。

## 架构

```text
Vue 3 Web
  ├─ REST ───────────────> Nginx ──> Knowledge Core ──> PostgreSQL
  └─ WebSocket ──────────> Nginx ──> Collaboration Gateway
                                      └─ gRPC ────────> Knowledge Core

Knowledge Core
  ├─ Redis / Caffeine
  └─ Transactional Outbox ──> Kafka ──> DevCollab Worker
                                        ├─ Elasticsearch
                                        ├─ Notification projection
                                        └─ MinIO snapshot
```

| 模块 | 职责 |
|---|---|
| `knowledge-core` | 权限、工作区、文档、版本、Git 元数据、事务和 Outbox |
| `collaboration-gateway` | WebSocket 连接、房间、Presence、背压和操作路由 |
| `devcollab-grpc-contract` | Gateway 与 Core 的 Protobuf/gRPC 契约 |
| `devcollab-worker` | Kafka 消费、幂等、搜索/通知/对象投影 |
| `web` | Vue 3 文档工作台、成员、搜索、通知、Git 与协作界面 |
| `agent-review-service` | 后置 Agent 阶段的 FastAPI 确定性规则雏形，尚未接入 LLM/RAG |

正式架构边界见 `docs/02-devcollab-system-architecture-v0.3.md`。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Tiptap、Axios |
| Core | Java 21、Spring Boot 3.5、Spring Security、Spring JDBC、Flyway |
| 协作/RPC | Spring WebFlux、WebSocket、gRPC、Protobuf |
| 数据与中间件 | PostgreSQL 17、Redis 8、Caffeine、Kafka 4、Elasticsearch 8、MinIO |
| 可观测性 | OpenTelemetry、Prometheus、Loki、Tempo、Grafana、Alloy |
| 测试 | JUnit 5、Spring Boot Test、MockMvc、H2、Node E2E/基准工具 |

## 本地启动

### 1. 环境

- Java 21
- Node.js
- Docker Desktop
- 仓库内 Maven Wrapper

```powershell
Copy-Item .env.example .env
docker compose up -d
```

### 2. Core

```powershell
.\mvnw.cmd -pl knowledge-core -am spring-boot:run
```

默认 HTTP `8080`，gRPC 端口由 `knowledge-core/src/main/resources/application.yml` 配置。

### 3. Worker

```powershell
.\mvnw.cmd -pl devcollab-worker -am spring-boot:run
```

### 4. Collaboration Gateway

```powershell
.\mvnw.cmd -pl collaboration-gateway -am spring-boot:run
```

### 5. Web

```powershell
cd web
npm.cmd install
npm.cmd run dev
```

Vite 本地端口、Core 代理和 Gateway 代理可通过 `web/.env.example` 中的变量覆盖。

## 验证

```powershell
# 全量 Java Reactor
.\mvnw.cmd test

# 前端类型检查和生产构建
cd web
npm.cmd run typecheck
npm.cmd run build
```

`tools/` 包含 Kafka、Worker、Gateway、Nginx、可观测性和性能基准的可重复验收工具。正式验收口径见 `docs/03-devcollab-architecture-verification-v0.1.md` 与 `docs/11-devcollab-editor-verification-v0.1.md`。

## Git 工程知识边界

当前 Git 模块保存供应商无关的仓库元数据和标准化变更，不保存 Git Token，也不会在 Core 请求线程执行 `git clone`：

```text
管理员登记仓库
→ Webhook / Worker / 调试入口提交标准化 Commit 或 Pull Request Diff
→ Core 以 repository + type + externalId 幂等落库
→ 成员将文档或 Block 关联到精确路径、目录/** 或 **/*.扩展名
→ 变更查询返回受影响文档与命中路径
```

下一阶段由 Worker 增加 GitHub/GitLab Adapter、Webhook 签名校验和漂移 Issue 自动生成；供应商凭证必须通过环境或秘密管理系统提供。

## 尚未完成

- GitHub/GitLab Webhook 与增量同步 Adapter；
- 自动生成和处理代码—文档漂移 Issue；
- MCP Context Server；
- Agent Review 的 RAG、LLM、Evidence Verifier 和评测闭环；
- 生产 HTTPS、秘密管理、CI/CD 和多节点部署验证。

## 文档治理

项目文档统一位于 `docs/`，入口为 `docs/00-devcollab-document-index.md`。`00–89` 是应提交的团队基线；`90–99` 是由 `.gitignore` 精确忽略的本地学习和面试材料。
