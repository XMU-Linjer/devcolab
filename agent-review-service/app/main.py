from __future__ import annotations

from fastapi import FastAPI

from app.rules import review_document
from app.schemas import ReviewDocumentRequest, ReviewDocumentResponse, ReviewIssueSuggestionResponse

app = FastAPI(
    title="DevCollab Agent Review Service",
    version="0.1.0",
    description="Deterministic review-rule MVP for DevCollab documents.",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/agent/review", response_model=ReviewDocumentResponse)
def review(request: ReviewDocumentRequest) -> ReviewDocumentResponse:
    result = review_document(request.to_domain())
    return ReviewDocumentResponse(
        documentId=result.document_id,
        documentVersionId=result.document_version_id,
        issueCount=result.issue_count,
        suggestions=[
            ReviewIssueSuggestionResponse.from_domain(suggestion)
            for suggestion in result.suggestions
        ],
    )
