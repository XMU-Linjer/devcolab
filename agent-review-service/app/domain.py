from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Iterable


class DocumentType(StrEnum):
    REQUIREMENT = "REQUIREMENT"
    API = "API"
    ARCHITECTURE = "ARCHITECTURE"
    DATABASE = "DATABASE"
    FRONTEND = "FRONTEND"
    BACKEND = "BACKEND"
    TEST = "TEST"
    DEPLOYMENT = "DEPLOYMENT"
    ADR = "ADR"


class BlockType(StrEnum):
    PARAGRAPH = "PARAGRAPH"


class ReviewIssueType(StrEnum):
    REQUIREMENT_GAP = "REQUIREMENT_GAP"
    API_CONTRACT = "API_CONTRACT"
    SECURITY = "SECURITY"
    PERFORMANCE = "PERFORMANCE"
    CONSISTENCY = "CONSISTENCY"
    STYLE = "STYLE"
    OTHER = "OTHER"


class ReviewIssueSeverity(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    BLOCKER = "BLOCKER"


@dataclass(frozen=True)
class DocumentBlock:
    id: str
    type: BlockType
    text: str
    sort_order: int = 0


@dataclass(frozen=True)
class DocumentReviewContext:
    document_id: str
    document_version_id: str | None
    title: str
    document_type: DocumentType
    version_no: int | None
    blocks: tuple[DocumentBlock, ...]

    @classmethod
    def from_blocks(
        cls,
        *,
        document_id: str,
        document_version_id: str | None,
        title: str,
        document_type: DocumentType,
        version_no: int | None,
        blocks: Iterable[DocumentBlock],
    ) -> DocumentReviewContext:
        return cls(
            document_id=document_id,
            document_version_id=document_version_id,
            title=title,
            document_type=document_type,
            version_no=version_no,
            blocks=tuple(sorted(blocks, key=lambda block: block.sort_order)),
        )


@dataclass(frozen=True)
class ReviewIssueSuggestion:
    rule_id: str
    type: ReviewIssueType
    severity: ReviewIssueSeverity
    title: str
    description: str
    evidence: str


@dataclass(frozen=True)
class ReviewResult:
    document_id: str
    document_version_id: str | None
    issue_count: int
    suggestions: tuple[ReviewIssueSuggestion, ...]
