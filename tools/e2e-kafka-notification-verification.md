# Kafka 通知中心端到端验证

这份文档用于验证 Kafka 第二消费者链路：

```text
提交/通过文档评审
  -> Core 写 outbox_events
  -> Core Outbox Relay 发布 Kafka
  -> NotificationProjectionConsumer 消费事件
  -> Worker 写 consumer_inbox 和 notifications
  -> 用户通过 Core 通知 API 读取通知
```

## 1. 前置条件

启动基础设施：

```powershell
docker compose up -d postgres kafka elasticsearch
```

启动 Knowledge Core：

```powershell
$env:DEVCOLLAB_OUTBOX_WORKER_ENABLED="true"
$env:DEVCOLLAB_OUTBOX_WORKER_BATCH_SIZE="1000"
$env:DEVCOLLAB_OUTBOX_WORKER_INITIAL_DELAY_MS="1000"
$env:DEVCOLLAB_OUTBOX_WORKER_FIXED_DELAY_MS="3000"
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
.\mvnw.cmd -pl knowledge-core spring-boot:run
```

启动 Worker：

```powershell
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_WORKER_NOTIFICATION_ENABLED="true"
.\mvnw.cmd -pl devcollab-worker spring-boot:run
```

## 2. 自动验收命令

```powershell
node tools\e2e-kafka-notification-check.mjs
```

脚本会自动执行：

```text
注册作者
  -> 注册管理员
  -> 作者创建工作区
  -> 作者邀请管理员为 ADMIN
  -> 作者创建文档和 Block
  -> 作者提交评审
  -> 等待 outbox_events 变成 PUBLISHED
  -> 等待 notification-projection 写 consumer_inbox
  -> 管理员通过 Core API 查到“文档待评审”通知
  -> 管理员标记通知已读
  -> 管理员通过评审
  -> 等待 outbox_events 变成 PUBLISHED
  -> 等待 notification-projection 写 consumer_inbox
  -> 作者通过 Core API 查到“文档已发布”通知
```

通过时会看到：

```text
[kafka-notification-e2e] submitted outbox status=PUBLISHED
[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_SUBMITTED
[kafka-notification-e2e] admin notification=...
[kafka-notification-e2e] approved outbox status=PUBLISHED
[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_APPROVED
[kafka-notification-e2e] author notification=...
[kafka-notification-e2e] PASS ...
```

## 3. 验收意义

这条链路证明：

- Kafka 已经不只是服务 ES 搜索投影；
- 同一条领域事件可以被不同 consumer group 独立消费；
- 通知消费者具备幂等消费记录；
- 通知最终可以通过 Core API 被真实用户读取；
- Worker 不对浏览器暴露 API，职责边界清晰。

## 4. 常见问题

### 4.1 超时等待 outbox_events

如果脚本卡在：

```text
Timed out waiting for DOCUMENT_REVIEW_SUBMITTED outbox published
```

先看脚本自动打印的诊断：

```text
DOCUMENT_REVIEW_SUBMITTED|PENDING|0||
DOCUMENT_CREATED|PENDING|0||
```

含义：

- Core 业务事务已经写入 `outbox_events`；
- 事件还没有被 Outbox Relay 尝试发布；
- 不是 Kafka 发布失败，因为 `retry_count` 没增加；
- 不是 Worker 消费失败，因为消息还没从 Core 发布到 Kafka。

最常见原因：

```text
Knowledge Core 启动时没有设置：
DEVCOLLAB_OUTBOX_WORKER_ENABLED=true
```

处理方式：

1. 停掉 Knowledge Core；
2. 在同一个 PowerShell 窗口设置环境变量；
3. 重新启动 Knowledge Core。

启动后 Core 日志应看到：

```text
Outbox worker tick completed: scanned=..., published=..., failed=...
```

### 4.2 Worker 日志里 SearchProjectionConsumer 进 DLQ

如果同时看到：

```text
SearchProjectionConsumer.onEvent threw exception
Kafka consumer sending record to DLQ
```

不要立刻判断通知消费者失败。

这通常表示：

- Worker 已经连上 Kafka；
- SearchProjectionConsumer 正在处理历史消息；
- 历史坏消息被送入 DLQ；
- 但通知脚本如果卡在 `outbox published`，根因仍然在 Core Outbox Relay 阶段。

判断顺序应该是：

```text
outbox_events.status
  PENDING -> Core Relay 没跑或没扫到
  FAILED -> Kafka 发布失败，看 last_error
  PUBLISHED -> 再看 Worker / consumer_inbox / notifications
```

## 5. 真实验收记录

### 第一次失败

现象：

```text
[kafka-notification-e2e] created workspace=... document=...
[kafka-notification-e2e] FAIL Timed out waiting for DOCUMENT_REVIEW_SUBMITTED outbox published
```

诊断：

```text
DOCUMENT_REVIEW_SUBMITTED|PENDING|0||
DOCUMENT_CREATED|PENDING|0||
consumer_inbox rows=0
notifications rows=0
```

结论：

Core 已经写出 Outbox，但 Relay 没有运行。因为 `retry_count=0` 且 `last_error` 为空，说明不是 Kafka 发布失败，而是根本没有尝试发布。

修复：

重新启动 Knowledge Core，并设置：

```powershell
$env:DEVCOLLAB_OUTBOX_WORKER_ENABLED="true"
```

### 第二次成功

重新启动 Core 后，脚本输出：

```text
[kafka-notification-e2e] created workspace=98149909-539b-4650-aca0-11fe028ef133 document=d39f2c1d-ab36-4560-84f1-5c1be3b4e4e9
[kafka-notification-e2e] submitted outbox status=PUBLISHED
[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_SUBMITTED
[kafka-notification-e2e] admin notification=e7032c7d-e854-4f80-a458-6b0fc14a9f7b title=文档待评审：Kafka Notification E2E Document 20260716172547
[kafka-notification-e2e] approved outbox status=PUBLISHED
[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_APPROVED
[kafka-notification-e2e] author notification=00d2c348-fb03-45ca-a098-65bfea8a1a53 title=文档已发布：Kafka Notification E2E Document 20260716172547
[kafka-notification-e2e] PASS workspace=98149909-539b-4650-aca0-11fe028ef133 document=d39f2c1d-ab36-4560-84f1-5c1be3b4e4e9
```

结论：

第二消费者链路已完成真实端到端验收。
