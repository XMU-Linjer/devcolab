from __future__ import annotations

from pydantic import BaseModel, Field

from app.domain import (
    BlockType,
    DocumentBlock,
    DocumentReviewContext,
    DocumentType,
    ReviewIssueSeverity,
    ReviewIssueSuggestion,
    ReviewIssueType,
)


class ReviewBlockRequest(BaseModel):
    id: str
    type: BlockType = BlockType.PARAGRAPH
    text: str = ""
    sortOrder: int = 0

    def to_domain(self) -> DocumentBlock:
        return DocumentBlock(
            id=self.id,
            type=self.type,
            text=self.text,
            sort_order=self.sortOrder,
        )


class ReviewDocumentRequest(BaseModel):
    documentId: str
    documentVersionId: str | None = None
    title: str = ""
    documentType: DocumentType = DocumentType.REQUIREMENT
    versionNo: int | None = None
    blocks: list[ReviewBlockRequest] = Field(default_factory=list)

    def to_domain(self) -> DocumentReviewContext:
        return DocumentReviewContext.from_blocks(
            document_id=self.documentId,
            document_version_id=self.documentVersionId,
            title=self.title,
            document_type=self.documentType,
            version_no=self.versionNo,
            blocks=(block.to_domain() for block in self.blocks),
        )


class ReviewIssueSuggestionResponse(BaseModel):
    ruleId: str
    type: ReviewIssueType
    severity: ReviewIssueSeverity
    title: str
    description: str
    evidence: str

    @classmethod
    def from_domain(cls, suggestion: ReviewIssueSuggestion) -> ReviewIssueSuggestionResponse:
        return cls(
            ruleId=suggestion.rule_id,
            type=suggestion.type,
            severity=suggestion.severity,
            title=suggestion.title,
            description=suggestion.description,
            evidence=suggestion.evidence,
        )


class ReviewDocumentResponse(BaseModel):
    documentId: str
    documentVersionId: str | None
    issueCount: int
    suggestions: list[ReviewIssueSuggestionResponse]
