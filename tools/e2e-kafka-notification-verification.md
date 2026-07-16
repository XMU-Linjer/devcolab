# Kafka 通知中心端到端验证

这份文档用于验证第二消费者链路：

```text
提交/通过文档评审
  -> outbox_events
  -> Kafka devcollab.domain-events
  -> NotificationProjectionConsumer
  -> notifications
  -> Core 通知 API
```

## 前置条件

启动基础设施：

```powershell
docker compose up -d postgres kafka elasticsearch
```

启动 Knowledge Core：

```powershell
$env:DEVCOLLAB_OUTBOX_WORKER_ENABLED="true"
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

## 自动验收

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

## 验收意义

这条链路证明：

- Kafka 已经不只是服务 ES 搜索投影；
- 同一条领域事件可以被不同 consumer group 独立消费；
- 通知消费者具备幂等消费记录；
- 通知最终可以通过 Core API 被真实用户读取；
- Worker 仍然不对浏览器暴露 API，职责边界清晰。

## 常见问题

如果超时等待 `outbox_events`：

- 检查 Core 是否开启 `DEVCOLLAB_OUTBOX_WORKER_ENABLED=true`；
- 检查 Kafka 容器是否运行；
- 检查 `outbox_events.last_error`。

如果超时等待 `consumer_inbox`：

- 检查 Worker 是否启动；
- 检查 `DEVCOLLAB_WORKER_NOTIFICATION_ENABLED=true`；
- 检查 Worker 日志是否有数据库或 Kafka 消费错误。

如果通知 API 查不到：

- 检查通知接收人是否正确；
- `DOCUMENT_REVIEW_SUBMITTED` 通知给工作区 ADMIN，排除提交人；
- `DOCUMENT_REVIEW_APPROVED` 通知给文档作者，排除操作者。
