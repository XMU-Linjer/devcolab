from enum import StrEnum
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class Decision(StrEnum):
    NO_CHANGE = "NO_CHANGE"
    SUBMIT_REVIEW = "SUBMIT_REVIEW"


class OperationType(StrEnum):
    CREATE_DOCUMENT = "CREATE_DOCUMENT"
    ADD_BLOCK = "ADD_BLOCK"
    UPDATE_BLOCK = "UPDATE_BLOCK"
    DELETE_BLOCK = "DELETE_BLOCK"


class BindingAction(StrEnum):
    UPSERT_BINDING = "UPSERT_BINDING"
    REMOVE_BINDING = "REMOVE_BINDING"


DocumentType = Literal[
    "REQUIREMENT",
    "API",
    "ARCHITECTURE",
    "DATABASE",
    "FRONTEND",
    "BACKEND",
    "TEST",
    "DEPLOYMENT",
    "ADR",
]
BlockType = Literal["PARAGRAPH", "HEADING", "CODE", "TODO"]


class BlockContent(StrictModel):
    schemaVersion: int | None = Field(default=None, ge=1)
    document: dict[str, Any] | None = None


class DocumentOperation(StrictModel):
    clientOperationId: str = Field(min_length=1, max_length=100)
    sequenceNumber: int = Field(ge=1)
    operationType: OperationType
    documentId: UUID | None = None
    createdDocumentClientOperationId: str | None = Field(default=None, max_length=100)
    blockId: UUID | None = None
    baseBlockVersion: int | None = Field(default=None, ge=0)
    proposedDocumentTitle: str | None = Field(default=None, max_length=200)
    proposedDocumentType: DocumentType | None = None
    proposedParentDocumentId: UUID | None = None
    proposedBlockType: BlockType | None = None
    proposedPlainText: str | None = Field(default=None, max_length=20_000)
    proposedContent: BlockContent | None = None


class BindingProposal(StrictModel):
    clientBindingProposalId: str = Field(min_length=1, max_length=100)
    sequenceNumber: int = Field(ge=1)
    action: BindingAction
    repositoryId: UUID
    filePath: str = Field(min_length=1, max_length=1_000)
    documentId: UUID | None = None
    createdDocumentClientOperationId: str | None = Field(default=None, max_length=100)
    bindingId: UUID | None = None
    reason: str = Field(min_length=1, max_length=1_000)


class PlanEvidence(StrictModel):
    clientOperationId: str | None = Field(default=None, max_length=100)
    repositoryId: UUID
    filePath: str = Field(min_length=1, max_length=1_000)
    startLine: int | None = Field(default=None, ge=1)
    endLine: int | None = Field(default=None, ge=1)
    description: str = Field(min_length=1, max_length=1_000)

    @model_validator(mode="after")
    def complete_line_range(self) -> "PlanEvidence":
        if (self.startLine is None) != (self.endLine is None):
            raise ValueError("startLine and endLine must appear together")
        if self.startLine is not None and self.endLine is not None:
            if self.endLine < self.startLine:
                raise ValueError("endLine must not be before startLine")
        return self


class AgentPlan(StrictModel):
    decision: Decision
    summary: str = Field(min_length=1, max_length=300)
    rationale: str = Field(min_length=1, max_length=10_000)
    operations: list[DocumentOperation] = Field(default_factory=list, max_length=50)
    bindingProposals: list[BindingProposal] = Field(default_factory=list, max_length=50)
    evidence: list[PlanEvidence] = Field(default_factory=list, max_length=50)

    @model_validator(mode="after")
    def decision_matches_changes(self) -> "AgentPlan":
        has_changes = bool(self.operations or self.bindingProposals)
        if self.decision == Decision.NO_CHANGE and has_changes:
            raise ValueError("NO_CHANGE cannot contain operations or binding proposals")
        if self.decision == Decision.SUBMIT_REVIEW and not has_changes:
            raise ValueError("SUBMIT_REVIEW requires an operation or binding proposal")
        return self

    def mcp_payload(self, workspace_id: str, client_request_id: str) -> dict[str, Any]:
        payload = self.model_dump(mode="json", exclude={"decision"}, exclude_none=True)
        return {
            "workspaceId": workspace_id,
            "clientRequestId": client_request_id,
            **payload,
        }


class PlanValidationIssue(StrictModel):
    path: str
    code: str
    message: str
