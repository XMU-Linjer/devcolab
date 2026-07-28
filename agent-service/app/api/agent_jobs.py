from datetime import UTC, datetime
from typing import cast
from uuid import UUID, uuid4

from fastapi import APIRouter, Header, HTTPException, Request, status

from app.clients.delegation_client import DelegationClientError
from app.schemas.jobs import (
    AgentJobSummary,
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
    unit_id = uuid4()
    created_at = datetime.now(UTC)
    try:
        delegation = await request.app.state.delegation_client.create(
            job_id=job_id,
            workspace_id=payload.workspaceId,
            repository_id=payload.repositoryId,
            authorization=token,
        )
        await request.app.state.job_repository.create_job(
            {
                "id": job_id,
                "delegation_id": UUID(str(delegation["delegationId"])),
                "created_by_user_id": UUID(str(delegation["createdByUserId"])),
                "workspace_id": payload.workspaceId,
                "repository_id": payload.repositoryId,
                "revision": str(delegation["revision"]),
                "scope_type": "CURRENT_FILE",
                "scope_payload": payload.scope.model_dump(mode="json"),
                "user_instruction": payload.userInstruction,
                "created_at": created_at,
            },
            {
                "id": unit_id,
                "max_attempts": request.app.state.settings.agent_unit_max_attempts,
            },
        )
    except DelegationClientError as exc:
        raise _delegation_error(exc) from exc
    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "MCP_UNAVAILABLE", "message": "Delegation response is invalid"},
        ) from exc
    return QueuedAgentJobResponse(jobId=job_id, status="QUEUED", createdAt=created_at)


@router.get("/{job_id}", response_model=AgentJobSummary)
async def get_agent_job(
    job_id: UUID,
    request: Request,
    authorization: str | None = Header(default=None),
) -> AgentJobSummary:
    token = _require_bearer(authorization)
    record = await request.app.state.job_repository.get_job(job_id)
    if record is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "INVALID_REQUEST", "message": "Agent job not found"},
        )
    try:
        await request.app.state.delegation_client.authorize(
            delegation_id=record["delegation_id"],
            job_id=job_id,
            authorization=token,
        )
    except DelegationClientError as exc:
        raise _delegation_error(exc) from exc
    return _summary(record)


def _summary(record: dict[str, object]) -> AgentJobSummary:
    scope = record["scope_payload"]
    review_ids = cast(list[object], record["review_request_ids"])
    return AgentJobSummary(
        jobId=record["id"],
        status=record["status"],
        workspaceId=record["workspace_id"],
        repositoryId=record["repository_id"],
        scopeType="CURRENT_FILE",
        scopePayload=scope if isinstance(scope, dict) else {},
        revision=str(record["revision"]),
        result=record["result"],
        phase=record["current_phase"],
        totalUnits=cast(int, record["total_units"]),
        completedUnits=cast(int, record["completed_units"]),
        failedUnits=cast(int, record["failed_units"]),
        reviewRequestIds=[UUID(str(value)) for value in review_ids or []],
        errorCode=record["error_code"],
        errorMessage=record["error_message"],
        createdAt=record["created_at"],
        startedAt=record["started_at"],
        completedAt=record["completed_at"],
        updatedAt=record["updated_at"],
    )


def _require_bearer(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "AUTHENTICATION_REQUIRED", "message": "Bearer token required"},
        )
    return authorization


def _delegation_error(exc: DelegationClientError) -> HTTPException:
    if exc.code == "MCP_PERMISSION_DENIED":
        code = status.HTTP_403_FORBIDDEN
    elif exc.code in {"INVALID_SCOPE", "FILE_NOT_FOUND"}:
        code = status.HTTP_404_NOT_FOUND
    elif exc.retryable:
        code = status.HTTP_503_SERVICE_UNAVAILABLE
    else:
        code = status.HTTP_400_BAD_REQUEST
    return HTTPException(
        status_code=code,
        detail={"code": exc.code, "message": str(exc)[:300]},
    )
