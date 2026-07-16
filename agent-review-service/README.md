# DevCollab Agent Review Service

Agent Review Service is the first independent Agent-side service in DevCollab. The MVP intentionally starts with deterministic review rules instead of a real LLM workflow, so the review contract can be validated before introducing model latency, cost and non-determinism.

## Current scope

- Exposes a FastAPI endpoint for reviewing a document version snapshot.
- Produces structured review issue suggestions aligned with Knowledge Core's `ReviewIssueType` and `ReviewIssueSeverity`.
- Does not write issues back to Knowledge Core yet.
- Does not call an LLM yet.

## Run locally

```powershell
cd agent-review-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8090
```

## Test rule engine

```powershell
python -m unittest discover -s agent-review-service/tests
```

## Review endpoint

```http
POST /api/v1/agent/review
```

The endpoint accepts a document version context and returns issue suggestions. A later stage will add an authenticated Core integration path to persist accepted suggestions as formal Review Issues.
