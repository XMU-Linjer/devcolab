from datetime import datetime
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.schemas.context import ContextBundle

RunStatus = Literal["RUNNING", "CONTEXT_READY", "FAILED"]
FormalRunStatus = Literal[
    "QUEUED",
    "BUILDING_CONTEXT",
    "PLANNING",
    "VALIDATING",
    "REPAIRING_PLAN",
    "SUBMITTING_REVIEW",
    "REVIEW_SUBMITTED",
    "NO_CHANGE",
    "FAILED",
]


class CreateContextRunRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    workspaceId: UUID
    repositoryId: UUID
    selectedPaths: list[str]
    userInstruction: str | None = Field(default=None, max_length=2_000)

    @field_validator("selectedPaths")
    @classmethod
    def paths_are_non_empty(cls, value: list[str]) -> list[str]:
        if not value:
            raise ValueError("selectedPaths must contain at least one path")
        normalized = [path.strip() for path in value]
        if any(not path for path in normalized):
            raise ValueError("selectedPaths cannot contain blank paths")
        return list(dict.fromkeys(normalized))


class CreateAgentRunRequest(CreateContextRunRequest):
    pass


class QueuedAgentRunResponse(BaseModel):
    runId: UUID
    status: Literal["QUEUED"]


class AgentRunRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")

    runId: UUID
    status: FormalRunStatus
    workspaceId: UUID
    repositoryId: UUID
    selectedPaths: list[str]
    currentNode: str
    decision: Literal["NO_CHANGE", "SUBMIT_REVIEW"] | None = None
    summary: str | None = None
    changeRequestId: UUID | None = None
    errorCode: str | None = None
    errorMessage: str | None = None
    createdAt: datetime
    updatedAt: datetime


class TraceSummary(BaseModel):
    toolCallsUsed: int = 0
    durationMs: int = 0
    successfulNodes: list[str] = Field(default_factory=list)
    failedNode: str | None = None


class AgentRunResponse(BaseModel):
    runId: UUID
    status: RunStatus
    contextBundle: ContextBundle | None = None
    traceSummary: TraceSummary
    error: dict[str, Any] | None = None
    createdAt: datetime
    updatedAt: datetime
