# DevCollab

DevCollab 是一个面向软件项目团队的协作知识平台，用于沉淀需求、接口、架构设计和工程文档。当前仓库实现了一个可运行的最小产品闭环：用户登录注册、工作区管理、文档树、Block 编辑器、JWT 会话续期、PostgreSQL 持久化和前端基础交互。

## 当前能力

- 账号认证：注册、登录、当前用户、退出登录。
- 会话安全：Access Token、HttpOnly Refresh Cookie、CSRF Header 校验、401 自动续期。
- 工作区：创建工作区、查看工作区列表、进入工作区。
- 文档树：创建文档、查看工作区文档树、打开文档。
- Block 编辑器：段落块新增、编辑、删除、排序。
- 并发保护：Block 更新使用 `expectedVersion` 做乐观锁控制，旧版本提交返回 409。
- 前端体验：加载态、空状态、错误重试、冲突提示、基础响应式布局。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios |
| 后端 | Java 21、Spring Boot 3.5、Spring Security、Spring JDBC、Flyway |
| 数据库 | PostgreSQL 17 |
| 本地中间件 | Docker Compose、PostgreSQL、Redis |
| 测试 | JUnit 5、Spring Boot Test、H2 |

## 架构说明

当前阶段采用“前端 + Knowledge Core 单核心服务 + 数据库”的纵向切片实现，先把业务主链路做完整，再逐步扩展协作网关、异步 Worker、搜索和 Agent Review。

```text
Vue Web
  -> HTTPS / Vite Proxy / Nginx
  -> Knowledge Core
  -> PostgreSQL

Knowledge Core
  -> Auth / Workspace / Document / Block
  -> Spring Security JWT
  -> Flyway Migration
```

未来架构规划见 `docs/02-devcollab-system-architecture-v0.3.md`。

## 本地启动

### 1. 准备环境

需要安装：

- Java 21
- Maven Wrapper 可直接使用仓库内 `mvnw.cmd`
- Node.js
- Docker Desktop

复制本地环境变量文件：

```powershell
Copy-Item .env.example .env
```

### 2. 启动中间件

```powershell
docker compose up -d postgres redis
```

默认 PostgreSQL 连接信息：

```text
url: jdbc:postgresql://localhost:5432/devcollab
username: devcollab
password: devcollab
```

### 3. 启动后端

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -pl knowledge-core spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

如果本机 8080 被占用，可以临时指定端口：

```powershell
.\mvnw.cmd -pl knowledge-core -Dspring-boot.run.arguments="--server.port=18081" spring-boot:run
```

### 4. 启动前端

```powershell
cd web
npm.cmd install
npm.cmd run dev
```

前端默认地址：

```text
http://localhost:5173
```

## 验证命令

后端测试：

```powershell
.\mvnw.cmd test
```

前端类型检查：

```powershell
cd web
npm.cmd run typecheck
```

前端生产构建：

```powershell
cd web
npm.cmd run build
```

## 目录结构

```text
devcollab/
├─ docs/                 # 正式项目文档与本地学习材料索引
├─ knowledge-core/       # Spring Boot 核心业务服务
├─ web/                  # Vue 3 前端应用
├─ docker-compose.yml    # 本地 PostgreSQL / Redis
├─ .env.example          # 本地环境变量模板
└─ pom.xml               # Maven 多模块父工程
```

## 文档

- `docs/01-devcollab-product-requirements-v0.1.md`：产品需求
- `docs/02-devcollab-system-architecture-v0.3.md`：系统架构
- `docs/04-devcollab-frontend-design-v0.2.md`：前端设计
- `docs/06-devcollab-authentication-design-v0.4.md`：登录会话设计
- `docs/07-devcollab-agent-rag-architecture-v0.1.md`：Agent Review 后续架构

本地学习和面试材料使用 `90–99` 编号，并由 `.gitignore` 忽略。

## 当前阶段边界

当前 MVP 重点是“核心知识平台纵向链路”，暂未实现：

- 多人实时协作 WebSocket
- 成员邀请和权限管理 UI
- 文档搜索
- Git 版本归档
- Agent Review / RAG
- 生产级部署脚本

这些能力属于后续阶段，避免在 MVP 阶段过早扩大复杂度。

## 下一步路线

建议优先顺序：

1. 成员与权限模块：工作区成员、角色、邀请、访问控制。
2. 文档增强：标题编辑、删除文档、文档层级移动。
3. 搜索模块：文档和 Block 的基础检索。
4. 协作模块：WebSocket 房间、心跳、在线状态。
5. Agent Review：规则评审、证据链、RAG 上下文。
