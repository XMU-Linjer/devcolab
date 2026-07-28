import re
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
        blocks: dict[str, tuple[str, int, str | None]] = {}
        for document_id, document in documents.items():
            for block in document.get("blocks", []):
                block_id = block.get("blockId")
                version = block.get("version")
                if block_id is not None and isinstance(version, int):
                    blocks[str(block_id)] = (document_id, version, block.get("type"))
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
        self._validate_binding_completeness(plan, context, bindings, issues)

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
                if not self._operation_body(operation):
                    self._issue(
                        issues,
                        path,
                        "BLOCK_CONTENT_REQUIRED",
                        "ADD_BLOCK requires final document content",
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
                if (
                    actual is not None
                    and operation.proposedBlockType is not None
                    and operation.proposedBlockType != actual[2]
                ):
                    self._issue(
                        issues,
                        path,
                        "BLOCK_TYPE_MISMATCH",
                        "UPDATE_BLOCK cannot change the observed Block type",
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
                elif not self._operation_body(operation):
                    self._issue(
                        issues,
                        path,
                        "BLOCK_CONTENT_REQUIRED",
                        "UPDATE_BLOCK requires complete replacement content",
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

        self._validate_document_authoring(plan, context, documents, code_by_path, issues)

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
                if created_target:
                    creator = next(
                        (
                            item
                            for item in plan.operations
                            if item.clientOperationId == created_target
                        ),
                        None,
                    )
                    if creator and creator.sequenceNumber >= proposal.sequenceNumber:
                        self._issue(
                            issues,
                            path,
                            "CREATE_REFERENCE_ORDER",
                            "created document must appear before its binding",
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

    def _validate_binding_completeness(
        self,
        plan: AgentPlan,
        context: dict[str, Any],
        bindings: list[dict[str, Any]],
        issues: list[PlanValidationIssue],
    ) -> None:
        if plan.decision != Decision.NO_CHANGE:
            return
        selected_paths = {
            str(path) for path in context.get("task", {}).get("selectedPaths", []) if path
        }
        bound_paths = {
            str(item.get("filePath") or item.get("pathPattern"))
            for item in bindings
            if item.get("filePath") or item.get("pathPattern")
        }
        missing = sorted(selected_paths - bound_paths)
        if missing:
            self._issue(
                issues,
                "decision",
                "BINDING_CHANGE_REQUIRED",
                "NO_CHANGE is invalid while selected files lack a formal Binding",
            )

    def _validate_document_authoring(
        self,
        plan: AgentPlan,
        context: dict[str, Any],
        documents: dict[str, dict[str, Any]],
        code_by_path: dict[str, str],
        issues: list[PlanValidationIssue],
    ) -> None:
        create_operations = {
            item.clientOperationId: item
            for item in plan.operations
            if item.operationType == OperationType.CREATE_DOCUMENT
        }
        add_by_create: dict[str, list[tuple[int, Any]]] = {
            operation_id: [] for operation_id in create_operations
        }
        updated_blocks: Counter[str] = Counter()
        seen_bodies: dict[tuple[str, str, str], int] = {}
        role = self._context_role(code_by_path)
        source_text = "\n".join(code_by_path.values())
        chinese_required = self._requires_chinese(context)

        for index, operation in enumerate(plan.operations):
            path = f"operations[{index}]"
            body = self._operation_body(operation)
            if operation.operationType == OperationType.UPDATE_BLOCK and operation.blockId:
                updated_blocks[str(operation.blockId)] += 1
            if operation.operationType == OperationType.ADD_BLOCK:
                created_id = operation.createdDocumentClientOperationId
                if created_id in add_by_create:
                    add_by_create[created_id].append((index, operation))
            if body:
                normalized = self._normalize_body(body)
                target = self._operation_document_key(operation)
                duplicate_key = (target, operation.proposedBlockType or "", normalized)
                if normalized and duplicate_key in seen_bodies:
                    self._issue(
                        issues,
                        path,
                        "DUPLICATE_BLOCK_CONTENT",
                        "the same document cannot contain duplicate Block content",
                    )
                else:
                    seen_bodies[duplicate_key] = index
                if self._is_instructional_body(body):
                    self._issue(
                        issues,
                        path,
                        "INSTRUCTIONAL_DOCUMENT_CONTENT",
                        "document content must be final prose, not writing advice",
                    )
                if role == "FRONTEND_API_CLIENT":
                    self._validate_frontend_client_content(
                        body,
                        source_text,
                        path,
                        issues,
                    )

            title = self._operation_target_title(operation, create_operations, documents)
            if title and not self._role_matches_document(role, title):
                self._issue(
                    issues,
                    path,
                    "DOCUMENT_RESPONSIBILITY_MISMATCH",
                    "the selected code responsibility does not match the target document",
                )

        for block_id, count in updated_blocks.items():
            if count > 1:
                self._issue(
                    issues,
                    "operations",
                    "DUPLICATE_BLOCK_UPDATE",
                    f"Block {block_id} is updated more than once",
                )

        for create_id, create_operation in create_operations.items():
            create_index = plan.operations.index(create_operation)
            title = (create_operation.proposedDocumentTitle or "").strip()
            content_operations = add_by_create.get(create_id, [])
            substantive = [
                operation
                for _, operation in content_operations
                if operation.proposedBlockType != "HEADING"
                and len(self._operation_body(operation).strip()) >= 12
            ]
            if not substantive:
                self._issue(
                    issues,
                    f"operations[{create_index}]",
                    "CREATE_DOCUMENT_BODY_REQUIRED",
                    "CREATE_DOCUMENT requires substantive final content in the same plan",
                )
            if content_operations and all(
                operation.proposedBlockType == "HEADING" for _, operation in content_operations
            ):
                self._issue(
                    issues,
                    f"operations[{create_index}]",
                    "HEADING_ONLY_DOCUMENT",
                    "a document cannot consist only of headings",
                )
            if chinese_required and title and not self._contains_chinese(title):
                self._issue(
                    issues,
                    f"operations[{create_index}].proposedDocumentTitle",
                    "CHINESE_TITLE_REQUIRED",
                    "new document titles must use Simplified Chinese",
                )
            for block_index, operation in content_operations:
                body = self._operation_body(operation)
                if chinese_required and body and not self._contains_chinese(body):
                    self._issue(
                        issues,
                        f"operations[{block_index}]",
                        "CHINESE_CONTENT_REQUIRED",
                        "new document content must use Simplified Chinese",
                    )
                elif chinese_required and body and self._is_obviously_mixed_language(body):
                    self._issue(
                        issues,
                        f"operations[{block_index}]",
                        "MIXED_DOCUMENT_LANGUAGE",
                        "new document content mixes Chinese and English prose excessively",
                    )
            short_count = sum(
                1
                for _, operation in content_operations
                if len(self._operation_body(operation).strip()) < 40
            )
            if (
                len(content_operations) >= 10
                and short_count / len(content_operations) >= 0.7
            ):
                self._issue(
                    issues,
                    f"operations[{create_index}]",
                    "FRAGMENTED_DOCUMENT",
                    "too many short Blocks form a fragmented document",
                )

    def _validate_frontend_client_content(
        self,
        body: str,
        source_text: str,
        path: str,
        issues: list[PlanValidationIssue],
    ) -> None:
        backend_identifiers = (
            "AuthController",
            "AuthService",
            "SecurityFilterChain",
            "JwtAuthenticationFilter",
        )
        for identifier in backend_identifiers:
            if identifier in body and identifier not in source_text:
                self._issue(
                    issues,
                    path,
                    "FRONTEND_BACKEND_SCOPE_POLLUTION",
                    (
                        "frontend API Client documentation contains "
                        "backend-only implementation details"
                    ),
                )
                break
        unsupported_paths = {
            item
            for item in re.findall(r"/[A-Za-z0-9_{}./:-]+", body)
            if item not in source_text
        }
        if unsupported_paths:
            self._issue(
                issues,
                path,
                "UNSUPPORTED_FRONTEND_ENDPOINT",
                "frontend API Client documentation contains an endpoint absent from selected code",
            )
        backend_patterns = (
            r"(?:服务端|后端).{0,20}(?:创建|清理|写入|设置).{0,12}Cookie",
            r"刷新令牌.{0,12}Cookie",
            r"(?:HTTP\s*)?(?:201|204)(?:\s+Created|\s+No\s+Content)?",
        )
        if any(
            re.search(pattern, body, flags=re.IGNORECASE) and not re.search(pattern, source_text)
            for pattern in backend_patterns
        ):
            self._issue(
                issues,
                path,
                "FRONTEND_BACKEND_SCOPE_POLLUTION",
                "frontend API Client documentation invents server-side behavior",
            )

    @staticmethod
    def _context_role(code_by_path: dict[str, str]) -> str:
        paths = [path.replace("\\", "/").lower() for path in code_by_path]
        if paths and all(
            re.search(r"(?:^|/)web/src/api/[^/]+\.tsx?$", path) for path in paths
        ):
            return "FRONTEND_API_CLIENT"
        if paths and all(path.endswith("controller.java") for path in paths):
            return "BACKEND_CONTROLLER"
        return "GENERAL"

    @staticmethod
    def _role_matches_document(role: str, title: str) -> bool:
        lowered = title.lower()
        if role == "FRONTEND_API_CLIENT":
            return not any(
                marker in lowered
                for marker in ("controller", "后端接口", "服务端接口", "后端认证")
            )
        if role == "BACKEND_CONTROLLER":
            return not any(
                marker in lowered
                for marker in ("前端", "客户端", "api client")
            )
        return True

    @staticmethod
    def _operation_target_title(
        operation: Any,
        create_operations: dict[str, Any],
        documents: dict[str, dict[str, Any]],
    ) -> str:
        if operation.operationType == OperationType.CREATE_DOCUMENT:
            return operation.proposedDocumentTitle or ""
        if operation.createdDocumentClientOperationId:
            creator = create_operations.get(operation.createdDocumentClientOperationId)
            return creator.proposedDocumentTitle if creator else ""
        if operation.documentId:
            return str(documents.get(str(operation.documentId), {}).get("title") or "")
        return ""

    @staticmethod
    def _operation_document_key(operation: Any) -> str:
        if operation.documentId:
            return f"existing:{operation.documentId}"
        if operation.createdDocumentClientOperationId:
            return f"created:{operation.createdDocumentClientOperationId}"
        return f"operation:{operation.clientOperationId}"

    @staticmethod
    def _operation_body(operation: Any) -> str:
        if operation.proposedPlainText and operation.proposedPlainText.strip():
            return str(operation.proposedPlainText).strip()
        if operation.proposedContent is None:
            return ""

        values: list[str] = []

        def collect(node: Any) -> None:
            if isinstance(node, dict):
                text = node.get("text")
                if isinstance(text, str):
                    values.append(text)
                for value in node.values():
                    collect(value)
            elif isinstance(node, list):
                for value in node:
                    collect(value)

        collect(operation.proposedContent.document)
        return "\n".join(item for item in values if item.strip()).strip()

    @staticmethod
    def _normalize_body(value: str) -> str:
        return re.sub(r"\s+", " ", value).strip().casefold()

    @staticmethod
    def _is_instructional_body(value: str) -> bool:
        normalized = re.sub(r"^[#>*\-\d.\s]+", "", value.strip())
        patterns = (
            r"^(?:建议新增|建议补充|建议增加|应补充|可以增加|可以描述)",
            r"^(?:add\s+(?:a\s+)?section|consider\s+documenting|this\s+section\s+should)\b",
        )
        return any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in patterns)

    @staticmethod
    def _contains_chinese(value: str) -> bool:
        return bool(re.search(r"[\u4e00-\u9fff]", value))

    @staticmethod
    def _is_obviously_mixed_language(value: str) -> bool:
        without_code = re.sub(r"`[^`]*`", "", value)
        without_code = re.sub(r"https?://\S+|(?:[A-Za-z]:)?[/\\][^\s，。；：]+", "", without_code)
        chinese_count = len(re.findall(r"[\u4e00-\u9fff]", without_code))
        latin_words = re.findall(r"\b[A-Za-z]{3,}\b", without_code)
        return chinese_count > 0 and len(latin_words) > max(12, chinese_count // 2)

    @staticmethod
    def _requires_chinese(context: dict[str, Any]) -> bool:
        instruction = str(context.get("task", {}).get("userInstruction") or "")
        return not bool(re.search(r"(?:英文|英语|\bEnglish\b)", instruction, re.IGNORECASE))

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
                if (
                    evidence.startLine is not None
                    and evidence.endLine - evidence.startLine + 1 > 200
                ):
                    self._issue(
                        issues,
                        path,
                        "LINE_RANGE_TOO_LARGE",
                        "evidence cannot exceed 200 lines",
                    )

    @staticmethod
    def _issue(issues: list[PlanValidationIssue], path: str, code: str, message: str) -> None:
        issues.append(PlanValidationIssue(path=path, code=code, message=message))
