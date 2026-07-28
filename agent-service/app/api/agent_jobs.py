from datetime import UTC, datetime
from typing import cast
from uuid import UUID, uuid4

from fastapi import APIRouter, Header, HTTPException, Query, Request, status

from app.clients.run_store import RunStoreError
from app.schemas.jobs import (
    AgentJobRecord,
    AgentJobSummary,
    AgentJobUnitsResponse,
    CreateAgentJobRequest,
    QueuedAgentJobResponse,
)

router = APIRouter(prefix="/api/v1/agent-jobs", tags=["agent-jobs"])


@router.post("", response_model=QueuedAgentJobResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_agent_job(
    payload: CreateAgentJobRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> QueuedAgentJobResponse:
    token = _require_bearer(authorization)
    job_id = uuid4()
    now = datetime.now(UTC)
    scope = payload.scope.model_dump(mode="json")
    queued = AgentJobRecord(
        jobId=job_id,
        status="QUEUED",
        workspaceId=payload.workspaceId,
        repositoryId=payload.repositoryId,
        scope=scope,
        createdAt=now,
        updatedAt=now,
    )
    try:
        await request.app.state.run_store.save_job(
            str(job_id),
            queued.model_dump(mode="json"),
            request.app.state.settings.agent_run_ttl_seconds,
        )
    except RunStoreError as exc:
        raise _redis_unavailable(exc) from exc
    request.app.state.job_executor.start(
        job_id=str(job_id),
        workspace_id=str(payload.workspaceId),
        repository_id=str(payload.repositoryId),
        scope=scope,
        authorization=token,
        created_at=now.isoformat(),
    )
    return QueuedAgentJobResponse(jobId=job_id, status="QUEUED")


@router.get("/{job_id}", response_model=AgentJobSummary)
async def get_agent_job(
    job_id: UUID,
    request: Request,
    authorization: str | None = Header(default=None),
) -> AgentJobSummary:
    _require_bearer(authorization)
    record = await _load(job_id, request)
    return AgentJobSummary.model_validate(record.model_dump(exclude={"units"}))


@router.get("/{job_id}/units", response_model=AgentJobUnitsResponse)
async def get_agent_job_units(
    job_id: UUID,
    request: Request,
    authorization: str | None = Header(default=None),
    offset: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=100),
) -> AgentJobUnitsResponse:
    _require_bearer(authorization)
    record = await _load(job_id, request)
    return AgentJobUnitsResponse(
        jobId=job_id,
        offset=offset,
        limit=limit,
        total=len(record.units),
        units=record.units[offset : offset + limit],
    )


async def _load(job_id: UUID, request: Request) -> AgentJobRecord:
    try:
        payload = await request.app.state.run_store.get_job(str(job_id))
    except RunStoreError as exc:
        raise _redis_unavailable(exc) from exc
    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "INVALID_REQUEST", "message": "Agent job not found"},
        )
    return AgentJobRecord.model_validate(cast(dict[str, object], payload))


def _require_bearer(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "AUTHENTICATION_REQUIRED", "message": "Bearer token required"},
        )
    return authorization


def _redis_unavailable(exc: RunStoreError) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail={"code": "REDIS_UNAVAILABLE", "message": str(exc)},
    )
