# DevCollab Agent Service

This service runs a deterministic, single-Agent code/document synchronization
review workflow. DeepSeek produces a strictly validated plan; DevCollab either
records `NO_CHANGE` or submits a `PENDING` change request through the dedicated
MCP review tool. It never applies or approves the request.

## Local startup

```powershell
cd agent-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8092
```

Copy `.env.example` to `.env` for local values. No model key is required for
`GET /health` or context construction.

## API

- `GET /health`
- `POST /api/v1/agent-runs/context`
- `POST /api/v1/agent-runs` (returns `202 QUEUED`)
- `GET /api/v1/agent-runs/{runId}`

The context endpoint accepts `workspaceId`, `repositoryId`, `selectedPaths`, and
an optional `userInstruction`. The user's Bearer token is forwarded only during
the five read-only MCP calls and is never persisted or logged.

The fixed LangGraph workflow always reads explicitly selected code. It then uses
formal bindings first, searches candidates only for unbound paths, deduplicates
documents, and reads a bounded number of document structures.

The formal workflow keeps credentials out of model input and Redis. It permits
one initial model plan and at most one repair, then either finishes with
`NO_CHANGE`, submits a human review, or fails safely.

Current exclusions: no frontend integration, auto-approval, multi-Agent flow,
vector database, long-term chat memory, or direct Core/database access.
