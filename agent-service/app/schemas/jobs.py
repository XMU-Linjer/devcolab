from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

JobStatus = Literal[
    "QUEUED",
    "DISCOVERING_FILES",
    "CLASSIFYING_FILES",
    "LOADING_BINDINGS",
    "BUILDING_UNITS",
    "READY_FOR_ANALYSIS",
    "FAILED",
    "CANCELLED",
]


class CurrentFileScope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["CURRENT_FILE"]
    filePath: str = Field(min_length=1, max_length=2_048)


class DirectoryScope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["DIRECTORY"]
    pathPrefix: str = Field(min_length=1, max_length=2_048)
    recursive: bool = True


class GitChangesScope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["GIT_CHANGES"]


class ProjectInitializationScope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["PROJECT_INITIALIZATION"]


AgentJobScope = Annotated[
    CurrentFileScope | DirectoryScope | GitChangesScope | ProjectInitializationScope,
    Field(discriminator="type"),
]


class CreateAgentJobRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workspaceId: UUID
    repositoryId: UUID
    scope: AgentJobScope
    userInstruction: str | None = Field(default=None, max_length=2_000)

    @field_validator("userInstruction")
    @classmethod
    def normalize_instruction(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class QueuedAgentJobResponse(BaseModel):
    jobId: UUID
    status: Literal["QUEUED"]


class AnalysisUnit(BaseModel):
    model_config = ConfigDict(extra="forbid")
    unitId: UUID
    sourceType: str
    filePaths: list[str]
    deletedPaths: list[str]
    boundDocumentIds: list[UUID]
    bindingIds: list[UUID]
    primaryDirectory: str
    languageSet: list[str]
    estimatedSizeBytes: int
    status: Literal["PENDING"] = "PENDING"
    groupingReasons: list[str]


class AgentJobRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")
    jobId: UUID
    status: JobStatus
    workspaceId: UUID
    repositoryId: UUID
    scope: dict[str, object]
    discoveredFileCount: int = 0
    eligibleCodeCount: int = 0
    skippedFileCount: int = 0
    skippedReasonCounts: dict[str, int] = Field(default_factory=dict)
    unitCount: int = 0
    completedUnitCount: int = 0
    reviewRequestIds: list[UUID] = Field(default_factory=list)
    units: list[AnalysisUnit] = Field(default_factory=list)
    errorCode: str | None = None
    errorMessage: str | None = None
    createdAt: datetime
    updatedAt: datetime


class AgentJobSummary(BaseModel):
    jobId: UUID
    status: JobStatus
    workspaceId: UUID
    repositoryId: UUID
    scope: dict[str, object]
    discoveredFileCount: int
    eligibleCodeCount: int
    skippedFileCount: int
    skippedReasonCounts: dict[str, int]
    unitCount: int
    completedUnitCount: int
    reviewRequestIds: list[UUID]
    errorCode: str | None
    errorMessage: str | None
    createdAt: datetime
    updatedAt: datetime


class AgentJobUnitsResponse(BaseModel):
    jobId: UUID
    offset: int
    limit: int
    total: int
    units: list[AnalysisUnit]
