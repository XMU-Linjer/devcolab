# DevCollab benchmark scripts

本目录用于做可重复的缓存压测对比，目标不是制造漂亮数字，而是给 Redis / Caffeine 的引入提供可量化证据。

## 对比口径

计划分三组：

```text
A. PostgreSQL 无缓存基线
B. PostgreSQL + Redis
C. PostgreSQL + Redis + Caffeine
```

当前脚本先支持 A/B 两组。Caffeine 接入后复用同一套 seed 和 run 命令即可。

## 前置条件

- Node.js 18+
- Knowledge Core 已启动，例如 `http://localhost:8080`
- PostgreSQL 已启动
- Redis 组需要 Redis 已启动

启动中间件：

```powershell
docker compose up -d postgres redis
```

启动后端：

```powershell
.\mvnw.cmd -pl knowledge-core spring-boot:run
```

## 1. 生成测试数据

```powershell
node tools/benchmark/devcollab-benchmark.mjs seed `
  --base-url http://localhost:8080 `
  --documents 500 `
  --child-per-root 4 `
  --blocks-per-document 2 `
  --output tools/benchmark/.benchmark-seed.json
```

脚本会创建：

- 一个压测用户；
- 一个工作区；
- 多篇文档；
- 每篇文档若干 Block。

seed 文件会保存用户名、密码、workspaceId、documentId 等信息，后续压测复用这份数据。

## 2. 跑无缓存基线

无缓存基线使用显式缓存开关，避免把 Redis 连接失败的异常开销计入 PostgreSQL 基线。

```powershell
$env:DEVCOLLAB_CACHE_ENABLED="false"
.\mvnw.cmd -pl knowledge-core spring-boot:run
```

然后另开一个终端运行：

```powershell
node tools/benchmark/devcollab-benchmark.mjs run `
  --label no-cache `
  --seed tools/benchmark/.benchmark-seed.json `
  --iterations 300 `
  --warmup 30 `
  --concurrency 16 `
  --output tools/benchmark/results/no-cache.json
```

## 3. 跑 Redis 组

确保 Redis 正常启动，并重启后端：

```powershell
$env:DEVCOLLAB_CACHE_ENABLED="true"
$env:DEVCOLLAB_REDIS_HOST="localhost"
$env:DEVCOLLAB_REDIS_PORT="6379"
.\mvnw.cmd -pl knowledge-core spring-boot:run
```

运行压测：

```powershell
node tools/benchmark/devcollab-benchmark.mjs run `
  --label redis `
  --seed tools/benchmark/.benchmark-seed.json `
  --iterations 300 `
  --warmup 30 `
  --concurrency 16 `
  --output tools/benchmark/results/redis.json
```

## 4. 生成对比表

```powershell
node tools/benchmark/devcollab-benchmark.mjs compare `
  tools/benchmark/results/no-cache.json `
  tools/benchmark/results/redis.json `
  --output tools/benchmark/results/cache-comparison.md
```

输出指标：

- `avgMs`
- `p50Ms`
- `p95Ms`
- `p99Ms`
- `qps`

## 当前压测接口

脚本默认测三条读路径：

```text
GET /api/v1/workspaces/{workspaceId}/documents/tree
GET /api/v1/documents/{documentId}
GET /api/v1/documents/{documentId}/blocks
```

其中：

- 文档树用于验证 `workspace:documents:tree:{workspaceId}`；
- 文档详情和 Block 列表用于覆盖高频权限校验链路；
- 当前没有缓存 Block 内容，Block 接口主要体现权限缓存收益。

## 注意事项

- 同一台电脑上跑压测，结果只能作为本地对比，不代表生产性能。
- A/B 两组要尽量使用同一份 seed 数据、同样的 iterations、warmup、concurrency。
- 每次切换 Redis 可用/不可用后，应重启后端，避免连接池状态影响结果。
- 不要把 `tools/benchmark/results/` 的临时结果当成正式结论；需要多跑几轮取稳定趋势。
