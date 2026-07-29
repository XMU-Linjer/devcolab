from __future__ import annotations

import ast
import hashlib
import secrets
from dataclasses import dataclass
from typing import Any
from uuid import UUID

from app.schemas.binding_plans import (
    BindingPlan,
    CodeAnchorKind,
    CodeCandidate,
    DocumentAnchorCandidate,
)
from app.schemas.plans import (
    AgentPlan,
    BindingAction,
    BindingProposal,
    Decision,
    OperationType,
    PlanEvidence,
)

MAX_CODE_CANDIDATES = 40
MAX_DOCUMENT_CANDIDATES = 40
MAX_PREVIEW_CHARACTERS = 600


class BindingPlanValidationError(ValueError):
    def __init__(self, issues: list[dict[str, str]]) -> None:
        super().__init__("Binding plan failed validation")
        self.issues = issues


@dataclass(frozen=True)
class BindingCandidateSet:
    code: tuple[CodeCandidate, ...]
    documents: tuple[DocumentAnchorCandidate, ...]

    def model_payload(self) -> dict[str, Any]:
        return {
            "codeCandidates": [
                item.model_dump(mode="json", exclude_none=True) for item in self.code
            ],
            "documentAnchorCandidates": [
                item.model_dump(mode="json", exclude_none=True)
                for item in self.documents
            ],
        }


class BindingCandidateBuilder:
    def __init__(
        self,
        *,
        max_code_candidates: int = MAX_CODE_CANDIDATES,
        max_document_candidates: int = MAX_DOCUMENT_CANDIDATES,
        max_preview_characters: int = MAX_PREVIEW_CHARACTERS,
    ) -> None:
        self._max_code_candidates = min(max_code_candidates, MAX_CODE_CANDIDATES)
        self._max_document_candidates = min(
            max_document_candidates, MAX_DOCUMENT_CANDIDATES
        )
        self._max_preview_characters = min(
            max_preview_characters, MAX_PREVIEW_CHARACTERS
        )

    def build(
        self,
        context: dict[str, Any],
        plan: AgentPlan,
    ) -> BindingCandidateSet:
        workspace = context.get("workspace", {})
        repository_id = UUID(str(workspace["repositoryId"]))
        revision = str(workspace.get("revision") or "").strip()
        if not revision:
            raise BindingPlanValidationError(
                [
                    {
                        "path": "workspace.revision",
                        "code": "REVISION_REQUIRED",
                        "message": "A fixed repository revision is required",
                    }
                ]
            )
        code_candidates: list[CodeCandidate] = []
        code_files = sorted(
            context.get("codeFiles", []),
            key=lambda item: str(item.get("filePath") or ""),
        )
        for item in code_files:
            if len(code_candidates) >= self._max_code_candidates:
                break
            code_candidates.extend(
                self._code_candidates(
                    repository_id,
                    revision,
                    item,
                    self._max_code_candidates - len(code_candidates),
                )
            )
        document_candidates = self._document_candidates(context, plan)
        ordered_code = sorted(code_candidates, key=_candidate_source_key)
        return BindingCandidateSet(
            tuple(ordered_code[: self._max_code_candidates]),
            tuple(document_candidates[: self._max_document_candidates]),
        )

    def _code_candidates(
        self,
        repository_id: UUID,
        revision: str,
        item: dict[str, Any],
        remaining: int,
    ) -> list[CodeCandidate]:
        file_path = str(item.get("filePath") or "")
        content = str(item.get("content") or "")
        language = str(item.get("language") or "")
        result = [
            self._code_candidate(
                repository_id,
                revision,
                file_path,
                CodeAnchorKind.FILE,
                language,
                file_path,
                content,
            )
        ]
        normalized = language.lower()
        if normalized == "python" or file_path.lower().endswith(".py"):
            result.extend(
                self._python_candidates(
                    repository_id, revision, file_path, language, content
                )
            )
        elif normalized == "java" or file_path.lower().endswith(".java"):
            for symbol in item.get("symbols", []):
                symbol_key = str(symbol.get("symbolKey") or "").strip()
                if not symbol_key:
                    continue
                start = _positive_int(symbol.get("startLine"))
                end = _positive_int(symbol.get("endLine"))
                if (start is None) != (end is None) or (
                    start is not None and end is not None and end < start
                ):
                    start = end = None
                result.append(
                    self._code_candidate(
                        repository_id,
                        revision,
                        file_path,
                        CodeAnchorKind.SYMBOL,
                        language,
                        str(
                            symbol.get("qualifiedName")
                            or symbol.get("simpleName")
                            or symbol_key
                        ),
                        _line_preview(content, start, end),
                        symbol_key=symbol_key,
                        start_line=start,
                        end_line=end,
                    )
                )
        file_candidate, *symbol_candidates = result
        return [
            file_candidate,
            *sorted(symbol_candidates, key=_candidate_source_key),
        ][:remaining]

    def _python_candidates(
        self,
        repository_id: UUID,
        revision: str,
        file_path: str,
        language: str,
        content: str,
    ) -> list[CodeCandidate]:
        try:
            tree = ast.parse(content)
        except (SyntaxError, ValueError):
            return []
        result: list[CodeCandidate] = []
        for node in tree.body:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                result.append(
                    self._python_symbol(
                        repository_id,
                        revision,
                        file_path,
                        language,
                        content,
                        node,
                        node.name,
                        type(node).__name__,
                    )
                )
            elif isinstance(node, ast.ClassDef):
                result.append(
                    self._python_symbol(
                        repository_id,
                        revision,
                        file_path,
                        language,
                        content,
                        node,
                        node.name,
                        "ClassDef",
                    )
                )
                for child in node.body:
                    if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        result.append(
                            self._python_symbol(
                                repository_id,
                                revision,
                                file_path,
                                language,
                                content,
                                child,
                                f"{node.name}.{child.name}",
                                type(child).__name__,
                            )
                        )
        return result

    def _python_symbol(
        self,
        repository_id: UUID,
        revision: str,
        file_path: str,
        language: str,
        content: str,
        node: ast.FunctionDef | ast.AsyncFunctionDef | ast.ClassDef,
        qualified_name: str,
        kind: str,
    ) -> CodeCandidate:
        start = int(node.lineno)
        end = int(getattr(node, "end_lineno", None) or start)
        symbol_key = (
            f"PYTHON:{file_path}:{qualified_name}:{kind.upper()}"
        )
        return self._code_candidate(
            repository_id,
            revision,
            file_path,
            CodeAnchorKind.SYMBOL,
            language,
            qualified_name,
            _line_preview(content, start, end),
            symbol_key=symbol_key,
            start_line=start,
            end_line=end,
        )

    def _code_candidate(
        self,
        repository_id: UUID,
        revision: str,
        file_path: str,
        anchor_kind: CodeAnchorKind,
        language: str,
        display_name: str,
        preview: str,
        *,
        symbol_key: str | None = None,
        start_line: int | None = None,
        end_line: int | None = None,
    ) -> CodeCandidate:
        bounded = preview[: self._max_preview_characters]
        return CodeCandidate(
            candidateId=_opaque_id("code"),
            repositoryId=repository_id,
            revision=revision,
            filePath=file_path,
            anchorKind=anchor_kind,
            symbolKey=symbol_key,
            startLine=start_line,
            endLine=end_line,
            language=language or None,
            displayName=display_name,
            contentPreview=bounded,
            contentHash=hashlib.sha256(bounded.encode("utf-8")).hexdigest(),
        )

    def _document_candidates(
        self,
        context: dict[str, Any],
        plan: AgentPlan,
    ) -> list[DocumentAnchorCandidate]:
        result: list[DocumentAnchorCandidate] = []
        for document in context.get("documents", []):
            document_id = UUID(str(document["documentId"]))
            title = str(document.get("title") or document_id)
            result.append(
                DocumentAnchorCandidate(
                    candidateId=_opaque_id("doc"),
                    documentId=document_id,
                    documentTitle=title,
                    blockLabel="整篇文档",
                )
            )
            for block in document.get("blocks", []):
                if len(result) >= self._max_document_candidates:
                    return result
                result.append(
                    DocumentAnchorCandidate(
                        candidateId=_opaque_id("doc"),
                        documentId=document_id,
                        blockId=UUID(str(block["blockId"])),
                        documentTitle=title,
                        blockLabel=_block_label(block),
                        contentPreview=_block_preview(block)[
                            : self._max_preview_characters
                        ],
                        contentSchemaVersion=_positive_int(
                            block.get("content", {}).get("schemaVersion")
                            if isinstance(block.get("content"), dict)
                            else None
                        ),
                        sortOrder=_non_negative_int(block.get("sortOrder")),
                    )
                )

        create_titles = {
            item.clientOperationId: item.proposedDocumentTitle or "新建文档"
            for item in plan.operations
            if item.operationType == OperationType.CREATE_DOCUMENT
        }
        for operation in plan.operations:
            if operation.operationType != OperationType.CREATE_DOCUMENT:
                continue
            result.append(
                DocumentAnchorCandidate(
                    candidateId=_opaque_id("doc"),
                    createdDocumentClientOperationId=operation.clientOperationId,
                    documentTitle=operation.proposedDocumentTitle or "新建文档",
                    blockLabel="整篇文档",
                )
            )
        for operation in plan.operations:
            if operation.operationType != OperationType.ADD_BLOCK:
                continue
            add_block_document_id = operation.documentId
            created_document = operation.createdDocumentClientOperationId
            title = (
                _existing_title(context, str(add_block_document_id))
                if add_block_document_id is not None
                else create_titles.get(created_document or "", "新建文档")
            )
            result.append(
                DocumentAnchorCandidate(
                    candidateId=_opaque_id("doc"),
                    documentId=add_block_document_id,
                    createdDocumentClientOperationId=created_document,
                    createdBlockClientOperationId=operation.clientOperationId,
                    documentTitle=title,
                    blockLabel=operation.proposedBlockType or "新建 Block",
                    contentPreview=_operation_preview(operation)[
                        : self._max_preview_characters
                    ],
                    contentSchemaVersion=(
                        operation.proposedContent.schemaVersion
                        if operation.proposedContent is not None
                        else None
                    ),
                    sortOrder=operation.sequenceNumber,
                )
            )
        return result


class BindingPlanExpander:
    def expand(
        self,
        plan: AgentPlan,
        binding_plan: BindingPlan,
        candidates: BindingCandidateSet,
    ) -> AgentPlan:
        code_by_id = {item.candidateId: item for item in candidates.code}
        document_by_id = {
            item.candidateId: item for item in candidates.documents
        }
        issues: list[dict[str, str]] = []
        for index, selection in enumerate(binding_plan.selections):
            if selection.codeCandidateId not in code_by_id:
                issues.append(
                    _issue(
                        f"selections[{index}].codeCandidateId",
                        "UNKNOWN_CODE_CANDIDATE",
                        "Select only a supplied codeCandidateId",
                    )
                )
            if selection.documentAnchorCandidateId not in document_by_id:
                issues.append(
                    _issue(
                        f"selections[{index}].documentAnchorCandidateId",
                        "UNKNOWN_DOCUMENT_CANDIDATE",
                        "Select only a supplied documentAnchorCandidateId",
                    )
                )
        if issues:
            raise BindingPlanValidationError(issues)

        proposals: list[BindingProposal] = []
        evidence = list(plan.evidence)
        evidence_keys = {
            (str(item.repositoryId), item.filePath, item.startLine, item.endLine)
            for item in evidence
        }
        sequence = len(plan.operations)
        ordered_selections = sorted(
            binding_plan.selections,
            key=lambda selection: _selection_source_key(
                code_by_id[selection.codeCandidateId],
                document_by_id[selection.documentAnchorCandidateId],
            ),
        )
        for index, selection in enumerate(ordered_selections, start=1):
            code = code_by_id[selection.codeCandidateId]
            document = document_by_id[selection.documentAnchorCandidateId]
            sequence += 1
            proposals.append(
                BindingProposal(
                    clientBindingProposalId=f"binding-{index}-{code.candidateId[-8:]}",
                    sequenceNumber=sequence,
                    action=BindingAction.UPSERT_BINDING,
                    repositoryId=code.repositoryId,
                    revision=code.revision,
                    filePath=code.filePath,
                    anchorKind=code.anchorKind.value,
                    symbolKey=code.symbolKey,
                    startLine=code.startLine,
                    endLine=code.endLine,
                    documentId=document.documentId,
                    createdDocumentClientOperationId=(
                        document.createdDocumentClientOperationId
                    ),
                    blockId=document.blockId,
                    createdBlockClientOperationId=(
                        document.createdBlockClientOperationId
                    ),
                    candidateId=code.candidateId,
                    documentAnchorCandidateId=document.candidateId,
                    reason=selection.reason,
                    confidence=selection.confidence,
                )
            )
            evidence_key = (
                str(code.repositoryId),
                code.filePath,
                code.startLine,
                code.endLine,
            )
            if evidence_key not in evidence_keys:
                evidence.append(
                    PlanEvidence(
                        repositoryId=code.repositoryId,
                        filePath=code.filePath,
                        startLine=code.startLine,
                        endLine=code.endLine,
                        description=selection.reason,
                    )
                )
                evidence_keys.add(evidence_key)
        decision = (
            Decision.SUBMIT_REVIEW
            if plan.operations or proposals
            else Decision.NO_CHANGE
        )
        return plan.model_copy(
            update={
                "decision": decision,
                "bindingProposals": proposals,
                "evidence": evidence,
            }
        )


def _opaque_id(prefix: str) -> str:
    return f"{prefix}_{secrets.token_urlsafe(12)}"


def _candidate_source_key(candidate: CodeCandidate) -> tuple[Any, ...]:
    precise_rank = 0 if candidate.startLine is not None else 1
    return (
        candidate.filePath,
        precise_rank,
        candidate.startLine if candidate.startLine is not None else 2**31 - 1,
        candidate.endLine if candidate.endLine is not None else 2**31 - 1,
        candidate.symbolKey or "",
        candidate.candidateId,
    )


def _selection_source_key(
    code: CodeCandidate,
    document: DocumentAnchorCandidate,
) -> tuple[Any, ...]:
    precise_rank = 0 if code.startLine is not None else 1
    return (
        code.filePath,
        precise_rank,
        code.startLine if code.startLine is not None else 2**31 - 1,
        code.endLine if code.endLine is not None else 2**31 - 1,
        document.sortOrder if document.sortOrder is not None else 2**31 - 1,
        code.candidateId,
        document.candidateId,
    )


def _positive_int(value: Any) -> int | None:
    return int(value) if isinstance(value, int) and value >= 1 else None


def _non_negative_int(value: Any) -> int | None:
    return int(value) if isinstance(value, int) and value >= 0 else None


def _line_preview(content: str, start: int | None, end: int | None) -> str:
    if start is None or end is None:
        return content
    return "\n".join(content.splitlines()[start - 1 : end])


def _block_preview(block: dict[str, Any]) -> str:
    if block.get("plainText") is not None:
        return str(block["plainText"])
    return str(block.get("content") or "")


def _block_label(block: dict[str, Any]) -> str:
    preview = _block_preview(block).strip().splitlines()
    return preview[0][:80] if preview else str(block.get("type") or "Block")


def _operation_preview(operation: Any) -> str:
    if operation.proposedPlainText:
        return str(operation.proposedPlainText)
    if operation.proposedContent is not None:
        return str(operation.proposedContent.document)
    return ""


def _existing_title(context: dict[str, Any], document_id: str) -> str:
    for item in context.get("documents", []):
        if str(item.get("documentId")) == document_id:
            return str(item.get("title") or document_id)
    return document_id


def _issue(path: str, code: str, message: str) -> dict[str, str]:
    return {"path": path, "code": code, "message": message}
