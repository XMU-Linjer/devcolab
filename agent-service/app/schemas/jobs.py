from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

JobStatus = Literal[
    "QUEUED",
    "RUNNING",
    "COMPLETED",
    "FAILED",
    "CANCELLED",
    "READY_FOR_ANALYSIS",
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


AgentJobScope = CurrentFileScope | ProjectInitializationScope


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
    createdAt: datetime


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
    status: Literal["PENDING", "READY_FOR_ANALYSIS"] = "PENDING"
    groupingReasons: list[str]
    semanticKey: str | None = None
    displayName: str | None = None
    semanticKind: str | None = None
    primaryFiles: list[str] = Field(default_factory=list)
    supportingFiles: list[str] = Field(default_factory=list)
    unitFingerprint: str | None = None


class AgentJobRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")
    jobId: UUID
    status: JobStatus
    workspaceId: UUID
    repositoryId: UUID
    scope: dict[str, object]
    revision: str
    result: Literal["NO_CHANGE", "REVIEW_SUBMITTED", "PARTIALLY_COMPLETED"] | None = None
    phase: Literal[
        "LOADING_CONTEXT",
        "MODEL_RUNNING",
        "VALIDATING",
        "REPAIRING",
        "SUBMITTING_REVIEW",
        "DISCOVERING_FILES",
        "CLASSIFYING_FILES",
        "LOADING_CODE_METADATA",
        "LOADING_BINDINGS",
        "BUILDING_SEMANTIC_GRAPH",
        "BUILDING_ANALYSIS_UNITS",
        "READY_FOR_ANALYSIS",
    ] | None = None
    totalUnits: int = 1
    completedUnits: int = 0
    failedUnits: int = 0
    reviewRequestIds: list[UUID] = Field(default_factory=list)
    errorCode: str | None = None
    errorMessage: str | None = None
    createdAt: datetime
    startedAt: datetime | None = None
    completedAt: datetime | None = None
    updatedAt: datetime


class AgentJobSummary(BaseModel):
    jobId: UUID
    status: JobStatus
    workspaceId: UUID
    repositoryId: UUID
    scopeType: Literal["CURRENT_FILE", "PROJECT_INITIALIZATION"]
    scopePayload: dict[str, object]
    revision: str
    result: Literal["NO_CHANGE", "REVIEW_SUBMITTED", "PARTIALLY_COMPLETED"] | None
    phase: Literal[
        "LOADING_CONTEXT",
        "MODEL_RUNNING",
        "VALIDATING",
        "REPAIRING",
        "SUBMITTING_REVIEW",
        "DISCOVERING_FILES",
        "CLASSIFYING_FILES",
        "LOADING_CODE_METADATA",
        "LOADING_BINDINGS",
        "BUILDING_SEMANTIC_GRAPH",
        "BUILDING_ANALYSIS_UNITS",
        "READY_FOR_ANALYSIS",
    ] | None
    totalUnits: int
    completedUnits: int
    failedUnits: int
    reviewRequestIds: list[UUID]
    errorCode: str | None
    errorMessage: str | None
    createdAt: datetime
    startedAt: datetime | None
    completedAt: datetime | None
    updatedAt: datetime
    discoveredFileCount: int = 0
    supportedCodeCount: int = 0
    skippedFileCount: int = 0
    skippedReasonCounts: dict[str, int] = Field(default_factory=dict)
    metadataParsedCount: int = 0
    metadataFailedCount: int = 0
    boundFileCount: int = 0
    unboundFileCount: int = 0
    analysisUnitCount: int = 0
    overlappingFileCount: int = 0


class AgentJobUnitsResponse(BaseModel):
    jobId: UUID
    offset: int
    limit: int
    total: int
    units: list["SemanticAnalysisUnit"]


class SemanticUnitFile(BaseModel):
    filePath: str
    role: str
    relevanceReason: str
    ordinal: int


class SemanticUnitDocument(BaseModel):
    documentId: UUID
    relationship: str
    source: str
    ordinal: int


class SemanticAnalysisUnit(BaseModel):
    unitId: UUID
    semanticKey: str
    displayName: str
    semanticKind: str
    status: Literal["READY_FOR_ANALYSIS"]
    primaryDirectory: str
    files: list[SemanticUnitFile]
    primaryFiles: list[str]
    supportingFiles: list[str]
    boundDocumentIds: list[UUID]
    boundDocuments: list[SemanticUnitDocument]
    languageSet: list[str]
    estimatedSizeBytes: int
    groupingReasons: list[str]
    unitFingerprint: str
