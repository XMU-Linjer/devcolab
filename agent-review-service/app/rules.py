from __future__ import annotations

import re

from app.domain import (
    DocumentReviewContext,
    DocumentType,
    ReviewIssueSeverity,
    ReviewIssueSuggestion,
    ReviewIssueType,
    ReviewResult,
)


MIN_MEANINGFUL_TEXT_LENGTH = 80

ACCEPTANCE_CRITERIA_PATTERNS = (
    "验收",
    "验收标准",
    "验收条件",
    "acceptance criteria",
    "acceptance",
    "given",
    "when",
    "then",
)

API_METHOD_PATTERN = re.compile(r"\b(GET|POST|PUT|PATCH|DELETE)\b|/api/", re.IGNORECASE)
API_FIELD_PATTERN = re.compile(
    r"请求字段|响应字段|字段|参数|request|response|schema|body|payload",
    re.IGNORECASE,
)


def review_document(context: DocumentReviewContext) -> ReviewResult:
    text = _combined_text(context)
    suggestions: list[ReviewIssueSuggestion] = []

    if not context.title.strip():
        suggestions.append(
            ReviewIssueSuggestion(
                rule_id="DOC_TITLE_EMPTY",
                type=ReviewIssueType.STYLE,
                severity=ReviewIssueSeverity.LOW,
                title="文档标题为空",
                description="文档缺少可读标题，会影响文档树识别、搜索结果展示和评审沟通。",
                evidence="title 为空或仅包含空白字符",
            )
        )

    if len(_meaningful_text(text)) < MIN_MEANINGFUL_TEXT_LENGTH:
        suggestions.append(
            ReviewIssueSuggestion(
                rule_id="DOC_CONTENT_TOO_SHORT",
                type=ReviewIssueType.REQUIREMENT_GAP,
                severity=ReviewIssueSeverity.MEDIUM,
                title="文档内容过短，评审上下文不足",
                description=(
                    "当前文档正文信息量不足，评审人难以判断范围、约束、边界条件和交付标准。"
                    "建议补充背景、目标、关键流程、异常场景或接口约束。"
                ),
                evidence=f"有效正文长度={len(_meaningful_text(text))}，阈值={MIN_MEANINGFUL_TEXT_LENGTH}",
            )
        )

    if context.document_type == DocumentType.REQUIREMENT and not _contains_any(
        f"{context.title}\n{text}",
        ACCEPTANCE_CRITERIA_PATTERNS,
    ):
        suggestions.append(
            ReviewIssueSuggestion(
                rule_id="REQ_MISSING_ACCEPTANCE_CRITERIA",
                type=ReviewIssueType.REQUIREMENT_GAP,
                severity=ReviewIssueSeverity.HIGH,
                title="需求文档缺少验收标准",
                description=(
                    "需求文档应明确功能完成后的验收口径，例如可操作的步骤、成功条件、异常处理和边界场景。"
                    "没有验收标准时，开发、测试和评审容易各自理解。"
                ),
                evidence="未匹配到“验收/acceptance/Given-When-Then”等验收标准关键词",
            )
        )

    if context.document_type == DocumentType.API and API_METHOD_PATTERN.search(text) and not API_FIELD_PATTERN.search(text):
        suggestions.append(
            ReviewIssueSuggestion(
                rule_id="API_MISSING_FIELD_CONTRACT",
                type=ReviewIssueType.API_CONTRACT,
                severity=ReviewIssueSeverity.HIGH,
                title="API 文档缺少字段契约说明",
                description=(
                    "API 文档出现了接口路径或 HTTP 方法，但缺少请求字段、响应字段或 Schema 说明。"
                    "这会导致前后端无法稳定对齐接口。"
                ),
                evidence="检测到 HTTP 方法或 /api/ 路径，但未检测到字段/参数/request/response/schema 等契约信息",
            )
        )

    ordered = tuple(sorted(suggestions, key=lambda issue: (_severity_order(issue.severity), issue.rule_id), reverse=True))
    return ReviewResult(
        document_id=context.document_id,
        document_version_id=context.document_version_id,
        issue_count=len(ordered),
        suggestions=ordered,
    )


def _combined_text(context: DocumentReviewContext) -> str:
    return "\n".join(block.text for block in context.blocks if block.text)


def _meaningful_text(text: str) -> str:
    return re.sub(r"\s+", "", text)


def _contains_any(text: str, patterns: tuple[str, ...]) -> bool:
    normalized = text.casefold()
    return any(pattern.casefold() in normalized for pattern in patterns)


def _severity_order(severity: ReviewIssueSeverity) -> int:
    return {
        ReviewIssueSeverity.BLOCKER: 4,
        ReviewIssueSeverity.HIGH: 3,
        ReviewIssueSeverity.MEDIUM: 2,
        ReviewIssueSeverity.LOW: 1,
    }[severity]
