# Kafka 端到端验证指南

这份文档用于手动验证：

```text
Knowledge Core -> outbox_events -> Kafka -> devcollab-worker -> consumer_inbox -> Elasticsearch
```

默认的 `mvn test` 不依赖真实 Kafka / PostgreSQL / Elasticsearch；这份验证单独面向真实中间件环境。

## 1. 启动基础设施

```powershell
docker compose up -d kafka postgres elasticsearch
docker compose ps
```

确认 Kafka 可用：

```powershell
docker exec devcollab-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## 2. 创建 Kafka Topic

项目关闭了 Kafka 自动建 Topic，所以需要显式创建：

```powershell
docker exec devcollab-kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 `
  --create `
  --if-not-exists `
  --topic devcollab.domain-events `
  --partitions 1 `
  --replication-factor 1
```

## 3. 启动 Knowledge Core

建议使用干净数据库，避免历史 `outbox_events` 积压影响观察。

```powershell
$env:DEVCOLLAB_OUTBOX_WORKER_ENABLED="true"
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_CACHE_ENABLED="false"
$env:DEVCOLLAB_LOCAL_CACHE_ENABLED="false"

.\mvnw.cmd -pl knowledge-core spring-boot:run
```

预期日志：

```text
Outbox worker tick completed: scanned=..., published=..., failed=0
```

## 4. 启动 devcollab-worker

新开一个 PowerShell：

```powershell
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_ELASTICSEARCH_ENABLED="true"
$env:DEVCOLLAB_ELASTICSEARCH_URL="http://localhost:9200"

.\mvnw.cmd -pl devcollab-worker spring-boot:run
```

## 5. 触发业务事件

可以通过前端操作，也可以用接口创建文档。最小验证路径是：

1. 注册用户；
2. 创建工作区；
3. 创建文档；
4. 创建或更新 Block。

这些操作会写入 `outbox_events`，再由 Core Outbox Relay 发布到 Kafka。

## 6. 验证 outbox_events

```powershell
docker exec devcollab-postgres psql -U devcollab -d devcollab -c "
select id, aggregate_type, aggregate_id, event_type, status, retry_count, published_at, left(last_error, 120) as last_error
from outbox_events
order by occurred_at desc
limit 10;
"
```

预期：

- 新事件最终变为 `PUBLISHED`；
- `published_at` 不为空；
- `last_error` 为空。

如果一直是 `PENDING`，先看是否有大量历史积压事件；Outbox Relay 会按旧事件顺序补投递。

## 7. 验证 Kafka 消息

```powershell
docker exec devcollab-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic devcollab.domain-events `
  --from-beginning `
  --max-messages 5 `
  --timeout-ms 10000
```

预期看到包含 `eventId`、`aggregateType`、`aggregateId`、`eventType`、`payload`、`occurredAt` 的 JSON。

## 8. 验证 consumer_inbox

```powershell
docker exec devcollab-postgres psql -U devcollab -d devcollab -c "
select consumer_name, event_id, consumed_at
from consumer_inbox
order by consumed_at desc
limit 10;
"
```

预期：

- `consumer_name = search-projection`；
- `event_id` 能对应 Kafka 消息里的 `eventId`。

当前 Worker 的策略是投影成功后才写 `consumer_inbox`。如果 ES 投影失败，异常会抛出，消息不会被登记为成功消费。

## 9. 验证 Elasticsearch

```powershell
curl "http://localhost:9200/devcollab-search/_search?pretty"
```

预期能看到文档标题或 Block 内容进入搜索索引。

## 10. 清理环境

保留数据：

```powershell
docker compose down
```

删除所有本地数据卷，从干净环境重新验证：

```powershell
docker compose down -v
```

## 11. 验证结论口径

默认单元测试不依赖 Kafka，因为单元测试验证业务规则和状态流转；真实 Kafka 链路通过独立端到端验证执行。生产策略没有降级成本地 Handler，Kafka 不可用时事件留在 PostgreSQL `outbox_events` 中，等待恢复后重试投递。
