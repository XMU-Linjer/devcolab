from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class CodeFile(BaseModel):
    filePath: str
    language: str | None = None
    content: str
    truncated: bool


class ContextDocument(BaseModel):
    source: Literal["BOUND", "CANDIDATE"]
    documentId: str
    structure: dict[str, Any]


class BudgetSummary(BaseModel):
    toolCallsUsed: int
    codeCharsUsed: int
    truncatedFiles: list[str] = Field(default_factory=list)
    skippedDocumentIds: list[str] = Field(default_factory=list)


class ContextBundle(BaseModel):
    model_config = ConfigDict(extra="forbid")

    runId: str
    workspace: dict[str, Any]
    task: dict[str, Any]
    codeFiles: list[CodeFile]
    existingBindings: list[dict[str, Any]]
    documents: list[ContextDocument]
    budget: BudgetSummary
