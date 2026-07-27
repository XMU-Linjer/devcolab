from collections import Counter
from typing import Any

from app.schemas.plans import (
    AgentPlan,
    BindingAction,
    Decision,
    OperationType,
    PlanValidationIssue,
)


class PlanValidationError(ValueError):
    def __init__(self, issues: list[PlanValidationIssue]) -> None:
        super().__init__("Agent plan failed validation")
        self.issues = issues

    def safe_details(self) -> list[dict[str, str]]:
        return [item.model_dump() for item in self.issues]


class AgentPlanValidator:
    def __init__(self, max_operations: int = 50, max_evidence: int = 50) -> None:
        self._max_operations = max_operations
        self._max_evidence = max_evidence

    def validate(self, plan: AgentPlan, context: dict[str, Any]) -> AgentPlan:
        issues: list[PlanValidationIssue] = []
        code_by_path = {
            str(item.get("filePath")): str(item.get("content", ""))
            for item in context.get("codeFiles", [])
        }
        repository_id = str(context.get("workspace", {}).get("repositoryId"))
        documents = {str(item.get("documentId")): item for item in context.get("documents", [])}
        blocks: dict[str, tuple[str, int]] = {}
        for document_id, document in documents.items():
            for block in document.get("blocks", []):
                block_id = block.get("blockId")
                version = block.get("version")
                if block_id is not None and isinstance(version, int):
                    blocks[str(block_id)] = (document_id, version)
        bindings = context.get("existingBindings", [])
        bindings_by_id = {
            str(item.get("bindingId")): item for item in bindings if item.get("bindingId")
        }
        create_ids = {
            item.clientOperationId
            for item in plan.operations
            if item.operationType == OperationType.CREATE_DOCUMENT
        }

        self._validate_counts_and_sequences(plan, issues)
        self._validate_evidence(plan, code_by_path, repository_id, issues)

        for index, operation in enumerate(plan.operations):
            path = f"operations[{index}]"
            if operation.operationType == OperationType.CREATE_DOCUMENT:
                if any(
                    value is not None
                    for value in (
                        operation.documentId,
                        operation.createdDocumentClientOperationId,
                        operation.blockId,
                        operation.baseBlockVersion,
                    )
                ):
                    self._issue(
                        issues,
                        path,
                        "CREATE_TARGET_INVALID",
                        "CREATE_DOCUMENT cannot target existing objects",
                    )
                if (
                    not operation.proposedDocumentTitle
                    or not operation.proposedDocumentTitle.strip()
                ):
                    self._issue(issues, path, "TITLE_REQUIRED", "CREATE_DOCUMENT requires a title")
            elif operation.operationType == OperationType.ADD_BLOCK:
                existing = str(operation.documentId) in documents if operation.documentId else False
                created = operation.createdDocumentClientOperationId in create_ids
                if existing == created:
                    self._issue(
                        issues,
                        path,
                        "DOCUMENT_TARGET_INVALID",
                        "ADD_BLOCK must target exactly one known or newly created document",
                    )
                if operation.blockId is not None or operation.baseBlockVersion is not None:
                    self._issue(
                        issues,
                        path,
                        "BLOCK_TARGET_INVALID",
                        "ADD_BLOCK cannot target an existing block",
                    )
                if operation.proposedBlockType is None:
                    self._issue(
                        issues, path, "BLOCK_TYPE_REQUIRED", "ADD_BLOCK requires proposedBlockType"
                    )
            elif operation.operationType in {
                OperationType.UPDATE_BLOCK,
                OperationType.DELETE_BLOCK,
            }:
                document_id = str(operation.documentId) if operation.documentId else ""
                block_id = str(operation.blockId) if operation.blockId else ""
                actual = blocks.get(block_id)
                if document_id not in documents or actual is None or actual[0] != document_id:
                    self._issue(
                        issues,
                        path,
                        "BLOCK_NOT_READ",
                        "operation must reference a block read in this run",
                    )
                elif operation.baseBlockVersion != actual[1]:
                    self._issue(
                        issues,
                        path,
                        "BLOCK_VERSION_MISMATCH",
                        "baseBlockVersion must equal the observed block version",
                    )
                if operation.operationType == OperationType.DELETE_BLOCK:
                    if (
                        operation.proposedPlainText is not None
                        or operation.proposedContent is not None
                    ):
                        self._issue(
                            issues,
                            path,
                            "DELETE_CONTENT_INVALID",
                            "DELETE_BLOCK cannot contain proposed content",
                        )
            if operation.createdDocumentClientOperationId:
                if operation.createdDocumentClientOperationId not in create_ids:
                    self._issue(
                        issues,
                        path,
                        "CREATE_REFERENCE_UNKNOWN",
                        "created document reference is unknown",
                    )
                referenced = next(
                    (
                        item
                        for item in plan.operations
                        if item.clientOperationId == operation.createdDocumentClientOperationId
                    ),
                    None,
                )
                if referenced and referenced.sequenceNumber >= operation.sequenceNumber:
                    self._issue(
                        issues,
                        path,
                        "CREATE_REFERENCE_ORDER",
                        "created document must appear earlier",
                    )

        seen_upserts: set[tuple[str, str, str]] = set()
        for index, proposal in enumerate(plan.bindingProposals):
            path = f"bindingProposals[{index}]"
            if str(proposal.repositoryId) != repository_id:
                self._issue(
                    issues, path, "REPOSITORY_MISMATCH", "repositoryId must match the current run"
                )
            if proposal.filePath not in code_by_path:
                self._issue(
                    issues, path, "FILE_NOT_READ", "binding filePath was not read in this run"
                )
            target = str(proposal.documentId) if proposal.documentId else ""
            created_target = proposal.createdDocumentClientOperationId
            if (target in documents) == (created_target in create_ids):
                self._issue(
                    issues,
                    path,
                    "DOCUMENT_TARGET_INVALID",
                    "binding must target exactly one known or newly created document",
                )
            if proposal.action == BindingAction.UPSERT_BINDING:
                key = (proposal.filePath, target, created_target or "")
                if key in seen_upserts:
                    self._issue(issues, path, "DUPLICATE_BINDING", "duplicate binding proposal")
                seen_upserts.add(key)
                for binding in bindings:
                    known_path = binding.get("filePath") or binding.get("pathPattern")
                    if known_path == proposal.filePath and str(binding.get("documentId")) == target:
                        self._issue(
                            issues, path, "BINDING_EXISTS", "the same binding already exists"
                        )
            else:
                existing_binding = bindings_by_id.get(str(proposal.bindingId))
                if existing_binding is None:
                    self._issue(
                        issues,
                        path,
                        "BINDING_NOT_READ",
                        "REMOVE_BINDING must reference a binding read in this run",
                    )
                elif (
                    existing_binding.get("filePath") or existing_binding.get("pathPattern")
                ) != proposal.filePath or str(existing_binding.get("documentId")) != target:
                    self._issue(
                        issues,
                        path,
                        "BINDING_MISMATCH",
                        "binding target does not match observed context",
                    )

        if plan.decision == Decision.SUBMIT_REVIEW:
            if not plan.evidence:
                self._issue(
                    issues, "evidence", "EVIDENCE_REQUIRED", "SUBMIT_REVIEW requires evidence"
                )
            evidence_operations = {
                item.clientOperationId for item in plan.evidence if item.clientOperationId
            }
            for operation in plan.operations:
                if operation.clientOperationId not in evidence_operations:
                    self._issue(
                        issues,
                        f"operations[{operation.sequenceNumber}]",
                        "OPERATION_EVIDENCE_REQUIRED",
                        "each document operation requires evidence",
                    )

        if issues:
            raise PlanValidationError(issues)
        return plan

    def _validate_counts_and_sequences(
        self, plan: AgentPlan, issues: list[PlanValidationIssue]
    ) -> None:
        if len(plan.operations) > self._max_operations:
            self._issue(issues, "operations", "TOO_MANY_OPERATIONS", "operation limit exceeded")
        if len(plan.bindingProposals) > self._max_operations:
            self._issue(
                issues, "bindingProposals", "TOO_MANY_BINDINGS", "binding proposal limit exceeded"
            )
        if len(plan.evidence) > self._max_evidence:
            self._issue(issues, "evidence", "TOO_MUCH_EVIDENCE", "evidence limit exceeded")
        operation_ids = [item.clientOperationId for item in plan.operations]
        binding_ids = [item.clientBindingProposalId for item in plan.bindingProposals]
        for field, values in (
            ("clientOperationId", operation_ids),
            ("clientBindingProposalId", binding_ids),
        ):
            for value, count in Counter(values).items():
                if count > 1:
                    self._issue(
                        issues, field, "DUPLICATE_CLIENT_ID", f"duplicate client id: {value}"
                    )
        sequences = [item.sequenceNumber for item in plan.operations]
        sequences += [item.sequenceNumber for item in plan.bindingProposals]
        if sorted(sequences) != list(range(1, len(sequences) + 1)):
            self._issue(
                issues,
                "sequenceNumber",
                "SEQUENCE_INVALID",
                "sequences must be unique, ordered and contiguous from 1",
            )

    def _validate_evidence(
        self,
        plan: AgentPlan,
        code_by_path: dict[str, str],
        repository_id: str,
        issues: list[PlanValidationIssue],
    ) -> None:
        operation_ids = {item.clientOperationId for item in plan.operations}
        for index, evidence in enumerate(plan.evidence):
            path = f"evidence[{index}]"
            content = code_by_path.get(evidence.filePath)
            if content is None:
                self._issue(issues, path, "FILE_NOT_READ", "evidence filePath was not read")
            if str(evidence.repositoryId) != repository_id:
                self._issue(
                    issues,
                    path,
                    "REPOSITORY_MISMATCH",
                    "evidence repositoryId must match the current run",
                )
            if evidence.clientOperationId and evidence.clientOperationId not in operation_ids:
                self._issue(
                    issues, path, "OPERATION_NOT_FOUND", "evidence references an unknown operation"
                )
            if content is not None and evidence.endLine is not None:
                total_lines = len(content.splitlines()) or 1
                if evidence.endLine > total_lines:
                    self._issue(
                        issues,
                        path,
                        "LINE_OUT_OF_RANGE",
                        "evidence line range exceeds the read file",
                    )

    @staticmethod
    def _issue(issues: list[PlanValidationIssue], path: str, code: str, message: str) -> None:
        issues.append(PlanValidationIssue(path=path, code=code, message=message))
