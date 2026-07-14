---
name: devcollab-engineering-learning-loop
description: Use for DevCollab implementation work when a change adds a feature, expands architecture, introduces a technical stack such as Outbox, Kafka, Elasticsearch, Redis, WebSocket, RAG, Agent review, or observability, or creates a commit-worthy vertical slice. Enforce the loop of implementation, local learning notes, interview-ready chain explanation, baseline validation before major upgrades, post-upgrade validation, and clean Git commits that exclude local learning documents.
---

# DevCollab Engineering Learning Loop

## Purpose

Keep DevCollab construction tied to explainable engineering outcomes. Every meaningful implementation step should leave behind working code, verification evidence, a local learning/interview note, a clear commit boundary, and measurable before/after evidence for large architecture upgrades.

This skill supplements, but does not replace, `devcollab-document-governance`. When editing any Markdown file under `docs/`, also use the document governance skill and follow its numbering, index, and `.gitignore` rules.

## Default workflow

### 1. Classify the current change

Before coding, classify the task as one of:

- `feature slice`: user-visible or API-visible capability, such as search, member management, document tree, block editing.
- `architecture upgrade`: infrastructure or pattern change, such as Outbox, Kafka, Elasticsearch, Redis cache, WebSocket gateway, Worker, MCP, Agent RAG, observability.
- `refactor hardening`: internal cleanup, validation, test strengthening, security hardening.
- `learning/documentation only`: no production code change.

For a feature slice, implement the smallest closed loop. For an architecture upgrade, do not start by wiring every downstream system; first identify the before/after behavior to measure.

### 2. Define the explainable chain

For each feature or upgrade, write down the chain in plain language before or during implementation:

```text
trigger
  -> frontend / API entry
  -> application service
  -> permission or consistency guard
  -> storage / event / external system
  -> response / projection / UI
```

The final answer should summarize this chain in 3-6 lines. The local learning note should include the more complete version.

### 3. Validate in proportion to risk

Minimum validation:

- Backend code: run relevant Maven tests.
- Frontend code: run `npm.cmd run typecheck`; run `npm.cmd run build` when UI or bundling changes.
- Database or migration changes: run Flyway-backed tests; if PostgreSQL/Testcontainers cannot run, state the exact skip reason.
- Security or permissions: include at least one positive case and one unauthorized/forbidden case.

For major upgrades, run a baseline before the change when practical. Examples:

- Search upgrade to Elasticsearch: measure PostgreSQL search response before; measure ES search after with similar seed data and query set.
- Cache upgrade: measure uncached behavior before; measure cached behavior and invalidation after.
- Outbox/Worker upgrade: measure direct synchronous behavior before; measure event creation, relay/consumer behavior, retry, and idempotency after.
- WebSocket upgrade: measure REST-only edit path before; measure connection, room join, broadcast, reconnect, and permission rejection after.

Never invent performance numbers. If measurement is blocked, record what was attempted and why it was blocked.

### 4. Update local learning material

After each meaningful implementation step, update a local ignored document under `docs/90-99`.

Rules:

- Always prefer creating or updating a **topic-specific** local learning document. Do NOT append to a single catch-all document.
- Pre-assigned topic slots:
  - `90` — Search, Outbox, Elasticsearch projection, and PG vs ES benchmarking
  - `91` — Interview knowledge map (total index of all learning documents)
  - `92` — Frontend technology ADR
  - `93` — Authentication (JWT, Refresh Cookie, CSRF, BCrypt)
  - `94` — Workspace, permission, document tree, and member management
  - `95` — Document Block editing, optimistic locking, and Tiptap
  - `96` — Frontend-backend integration, token refresh, and CORS
  - `97` — Project overview and next-steps plan (summary only; specifics go in topic documents)
  - `98` — Document lifecycle, version snapshots, operation timeline, and Review Issue
  - `99` — Available for future topic
- Keep local learning documents ignored and out of Git commits.
- Every topic document MUST include these four dimensions:
  1. What is the technology stack? (technical definition)
  2. What problem does it solve? What improvement does it bring? (motivation)
  3. What business loop was completed? What's the end-to-end chain? (implementation flow)
  4. Why is the architecture designed this way? Why not alternatives? (design reasoning, interview-ready)
- Additionally include: current implementation conclusion, core technical terms, future upgrade path, interview Q&A, and truthful verification results.

When a new topic clearly belongs to an existing numbered slot, update that document and bump its minor version. When the topic is genuinely new and all 90–99 slots are full, create a new `9N-local-...md` file following `devcollab-document-governance`.

### 5. Keep commit boundaries clean

Before committing:

1. Run `git status --short --ignored`.
2. Confirm local learning documents, `.env`, build outputs, and IDE files are ignored.
3. Stage only engineering files intended for Git.
4. Use a concise Chinese commit message.

Good commit boundaries:

- `feat: 增加工作区搜索能力`
- `feat: 增加 outbox 事件记录`
- `feat: 接入文档搜索索引投影`
- `test: 增加搜索性能基线验证`

Avoid mixing unrelated architecture upgrades in one commit.

## Outbox / Elasticsearch planning guardrail

For Outbox and Elasticsearch work, use the staged path:

```text
PostgreSQL source of truth
  -> same-transaction Outbox event
  -> relay / worker consumes event
  -> Elasticsearch search projection
  -> frontend search behavior remains stable
  -> Agent/RAG can reuse indexed evidence later
```

Recommended order:

1. Add Outbox table and write events in the same transaction as document/block changes.
2. Add tests proving business data and event data commit together.
3. Add a local learning note explaining transactional Outbox, event idempotency, and failure recovery.
4. Create a repeatable seed/search baseline before replacing PostgreSQL search.
5. Add Worker/relay and Elasticsearch projection.
6. Re-run the same search checks and compare behavior truthfully.

Do not claim Elasticsearch improves performance until a measured comparison exists.

## Final response checklist

When this skill affects the work, report:

- what changed,
- what chain was completed,
- what learning note was updated,
- what validation ran and what did not run,
- what commit was created,
- what the next staged module should be.
