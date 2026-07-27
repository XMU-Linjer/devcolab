# DevCollab Agent Service

This service builds a deterministic, read-only code/document context bundle. It is
not a code-generation Agent and does not call DeepSeek in this phase.

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
- `GET /api/v1/agent-runs/{runId}`

The context endpoint accepts `workspaceId`, `repositoryId`, `selectedPaths`, and
an optional `userInstruction`. The user's Bearer token is forwarded only during
the five read-only MCP calls and is never persisted or logged.

The fixed LangGraph workflow always reads explicitly selected code. It then uses
formal bindings first, searches candidates only for unbound paths, deduplicates
documents, and reads a bounded number of document structures.

Current exclusions: no DeepSeek call, no document-change submission, no long-term
chat memory, and no write tool.
