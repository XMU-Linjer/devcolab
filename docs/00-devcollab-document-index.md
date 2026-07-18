# 00 DevCollab 文档索引

## 1. 正式项目文档

| 顺序 | 文档 | 作用 | 当前版本 | 版本库状态 |
|---:|---|---|---|---|
| 01 | `01-devcollab-product-requirements-v0.1.md` | 定义产品目标、范围、功能需求、优先级和验收条件 | V0.1 | 应提交 |
| 02 | `02-devcollab-system-architecture-v0.3.md` | 定义系统边界、服务职责、数据流、一致性、安全及技术基线 | V0.3 | 应提交 |
| 03 | `03-devcollab-architecture-verification-v0.1.md` | 定义架构测试、压测、故障实验和证据要求 | V0.1 | 应提交 |
| 04 | `04-devcollab-frontend-design-v0.2.md` | 定义信息架构、页面布局、关键流程、状态表达及接口能力 | V0.2 | 应提交 |
| 05 | `05-devcollab-document-governance-v0.1.md` | 统一文档目录、编号、命名、提交属性、正文结构和维护流程 | V0.1 | 应提交 |
| 06 | `06-devcollab-authentication-design-v0.4.md` | 定义注册、登录、Token、Cookie、会话、限流、安全及验收 | V0.4 | 应提交 |
| 07 | `07-devcollab-agent-rag-architecture-v0.1.md` | 定义 LangChain/LangGraph、RAG 入库、混合检索、Reviewer、Evidence、安全和评测 | V0.1 | 应提交 |
| 08 | `08-devcollab-observability-design-v0.2.md` | 定义本地指标、日志、链路追踪、Grafana 展示、故障演练与验收口径 | V0.2 | 应提交 |

正式文档的评审顺序为需求、总体架构、验证、前端设计和专项设计。Agent 专项设计属于 V1.1 后置阶段，只有核心平台、Git、搜索与权限基础通过验证后才进入实施评审。

## 2. 本地学习材料

| 顺序 | 文档 | 主题 | 版本 | 版本库状态 |
|---:|---|---|---|---|
| 90 | `90-local-search-outbox-es-learning-v0.1.md` | PostgreSQL 搜索、Transactional Outbox、ES 搜索投影、PG vs ES 基准对比 | V0.1 | 已由 `.gitignore` 忽略 |
| 91 | `91-local-interview-knowledge-map-v0.3.md` | 全栈学习文档总索引、技术栈速查表、证据边界 | V0.3 | 已由 `.gitignore` 忽略 |
| 92 | `92-local-frontend-technology-adr-v0.1.md` | 前端选型、知识基线和技术风险记录 | V0.1 | 已由 `.gitignore` 忽略 |
| 93 | `93-local-authentication-interview-guide-v0.2.md` | 登录全链路、JWT、Cookie、安全威胁和面试表达 | V0.2 | 已由 `.gitignore` 忽略 |
| 94 | `94-local-workspace-document-permission-v0.2.md` | 工作空间、成员权限、文档树管理、RBAC 隔离 | V0.2 | 已由 `.gitignore` 忽略 |
| 95 | `95-local-document-block-learning-guide-v0.3.md` | Block 编辑、乐观锁、冲突检测、Tiptap 集成 | V0.3 | 已由 `.gitignore` 忽略 |
| 96 | `96-local-frontend-backend-integration-guide-v0.1.md` | 前后端联调、Refresh 自动续期、CSRF Header、401 处理 | V0.1 | 已由 `.gitignore` 忽略 |
| 97 | `97-local-project-explanation-v0.2.md` | 项目总览、MVP 总结、亮点速查、下一步计划 | V0.2 | 已由 `.gitignore` 忽略 |
| 98 | `98-local-document-lifecycle-review-learning-v0.1.md` | 文档状态机、版本快照、操作时间线、Review Issue | V0.1 | 已由 `.gitignore` 忽略 |
| 99 | `99-local-redis-caffeine-architecture-learning-v0.2.md` | Redis 限流与去重、Caffeine 多级缓存、Kafka 跨节点失效及真实验收记录 | V0.2 | 已由 `.gitignore` 精确忽略 |

每份本地学习文档覆盖一个独立技术主题，按面试 4 维度（技术栈是什么 → 解决了什么问题/带来什么提升 → 业务闭环流程 → 架构设计原因）组织。91 为总索引，收录全部技术栈速查表。

## 3. 文档维护规则

所有 Markdown 文档统一存放在 `docs/`，并执行 `05-devcollab-document-governance-v0.1.md`。文件名前两位数字用于固定阅读顺序；本地文档使用 90–99 编号并同步加入 `.gitignore`。

## 当前有效本地学习入口修正

| 编号 | 文档 | 主题 | 版本 | 版本库状态 |
|---:|---|---|---|---|
| 90 | `90-local-outbox-kafka-minio-learning-v0.7.md` | Transactional Outbox、Kafka Worker、MinIO 文档快照及真实排错复盘 | V0.7 | 已由 `.gitignore` 忽略 |
| 90 | `90-local-architecture-integration-troubleshooting-v0.4.md` | Nginx 统一入口、可观测性、故障演练、跨服务联调、演示编排和真实故障排查 | V0.4 | 已由 `.gitignore` 忽略 |
| 90 | `90-local-collaboration-gateway-learning-v0.4.md` | WebSocket 四类可靠操作、documentSequence 游标分页、断线增量补偿与真实排错 | V0.4 | 已由 `.gitignore` 精确忽略 |
| 91 | `91-local-interview-knowledge-map-v0.6.md` | 当前技术栈、Gateway 断线恢复证据边界与面试表达总索引 | V0.6 | 已由 `.gitignore` 精确忽略 |

> 说明：较早的本地学习材料保留为历史版本；上表“当前有效本地学习入口修正”中的对应主题新版本优先于旧快照。
