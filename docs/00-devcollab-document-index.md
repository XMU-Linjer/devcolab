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

正式文档的评审顺序为需求、总体架构、验证、前端设计和专项设计。Agent 专项设计属于 V1.1 后置阶段，只有核心平台、Git、搜索与权限基础通过验证后才进入实施评审。需求变更影响系统行为时应同步评估架构与前端设计；架构约束变化时应同步更新验证方案和对应专项设计。

## 2. 本地执行材料

| 顺序 | 文档 | 作用 | 版本库状态 |
|---:|---|---|---|
| 90 | `90-local-development-plan-v0.2.md` | 个人八周排期、阶段一纵向切片和执行范围 | 已由 `.gitignore` 忽略 |
| 91 | `91-local-interview-knowledge-map-v0.2.md` | 个人学习、服务拆分依据与面试知识映射 | 已由 `.gitignore` 忽略 |
| 92 | `92-local-frontend-technology-adr-v0.1.md` | 本地前端选型、知识基线和技术风险记录 | 已由 `.gitignore` 忽略 |
| 93 | `93-local-authentication-interview-guide-v0.2.md` | 登录全链路、JWT、Cookie、安全威胁和面试表达 | 已由 `.gitignore` 忽略 |
| 94 | `94-local-workspace-document-interview-guide-v0.1.md` | 工作空间、成员隔离、文档树链路、技术栈与场景题 | 已由 `.gitignore` 忽略 |
| 95 | `95-local-document-block-learning-guide-v0.2.md` | 文档 Block 增删改查、拖动排序、权限校验与代码调用链 | 已由 `.gitignore` 忽略 |

本地技术选型和执行材料不得作为产品范围、架构决策或完成状态的唯一依据。

## 3. 文档维护规则

所有 Markdown 文档统一存放在 `docs/`，并执行 `05-devcollab-document-governance-v0.1.md`。文件名前两位数字用于固定阅读顺序；本地文档使用 90–99 编号并同步加入 `.gitignore`。
