# DevCollab 演示稳定性联调清单

这份清单用于正式演示前快速确认主链路稳定：

```text
登录/注册 -> 工作区 -> 文档工作台 -> 提交评审 -> Kafka -> Worker -> 通知 -> 前端通知点击跳转
```

## 1. 启动基础设施

```powershell
docker compose up -d postgres redis elasticsearch kafka
docker compose ps
```

确认 Kafka Topic 存在：

```powershell
docker exec devcollab-kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 `
  --create `
  --if-not-exists `
  --topic devcollab.domain-events `
  --partitions 1 `
  --replication-factor 1
```

## 2. 启动 Knowledge Core

```powershell
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_OUTBOX_WORKER_ENABLED="true"
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_CACHE_ENABLED="true"
$env:DEVCOLLAB_LOCAL_CACHE_ENABLED="true"

.\mvnw.cmd -pl knowledge-core spring-boot:run
```

验收点：

- `http://localhost:8080/actuator/health` 可访问；
- 业务操作后 `outbox_events.published_at` 能被写入；
- Kafka 不可用时事件留在 `outbox_events`，不会绕过 Kafka 走本地 Handler。

## 3. 启动 Worker

```powershell
$env:DEVCOLLAB_DB_URL="jdbc:postgresql://localhost:5432/devcollab"
$env:DEVCOLLAB_DB_USERNAME="devcollab"
$env:DEVCOLLAB_DB_PASSWORD="devcollab"
$env:DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:DEVCOLLAB_ELASTICSEARCH_ENABLED="true"
$env:DEVCOLLAB_ELASTICSEARCH_URL="http://localhost:9200"
$env:DEVCOLLAB_WORKER_NOTIFICATION_ENABLED="true"

.\mvnw.cmd -pl devcollab-worker spring-boot:run
```

验收点：

- Worker 正常加入 `devcollab-search-projection` / `devcollab-notification-projection` 消费组；
- 坏 Kafka 消息只打印一行 WARN，不阻塞后续正常消息；
- 投影或数据库失败仍抛出异常，由 Kafka 重试机制处理。

## 4. 启动前端

```powershell
cd web
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 5. 演示主链路

建议准备两个账号：

- 作者账号：创建工作区、创建文档、提交评审；
- 管理员账号：接收通知、进入工作台、通过评审。

操作顺序：

1. 作者登录，创建工作区和文档；
2. 作者在文档工作台编辑 Block，刷新页面确认内容仍在；
3. 作者提交评审；
4. 管理员登录，打开右上角通知中心；
5. 管理员点击“文档待评审”通知，确认进入对应文档工作台；
6. 管理员通过评审；
7. 作者重新登录或刷新通知中心，看到“文档已发布”通知；
8. 作者点击通知，确认跳回已发布文档。

## 6. 快速排障

| 现象 | 优先检查 |
|------|----------|
| 前端 401 | 登录后 Access Token 是否在内存中；刷新是否能通过 Refresh Cookie 恢复 |
| 通知不出现 | Core 是否开启 Outbox Worker；Worker 是否启动；Kafka Topic 是否一致 |
| outbox_events 未发布 | Kafka 是否可用；`last_error` 是否有错误 |
| consumer_inbox 无记录 | Worker 是否连接同一个数据库和同一个 Topic |
| 搜索不更新 | Elasticsearch 是否开启；Worker 是否设置 `DEVCOLLAB_ELASTICSEARCH_ENABLED=true` |
| 页面空白 | 先运行 `npm run build`，再检查浏览器控制台错误 |

## 7. 演示前验证命令

```powershell
.\mvnw.cmd -pl devcollab-worker test

cd web
npm run build
```

这两个验证通过后，再进行浏览器手动演示。
