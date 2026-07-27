from copy import deepcopy
from pathlib import Path
from typing import Any

import pytest
from pydantic import ValidationError

from app.planning.context_serializer import build_model_context
from app.planning.validator import AgentPlanValidator, PlanValidationError
from app.schemas.plans import AgentPlan

WORKSPACE = "11111111-1111-1111-1111-111111111111"
REPOSITORY = "22222222-2222-2222-2222-222222222222"
DOCUMENT = "44444444-4444-4444-4444-444444444444"
BLOCK = "55555555-5555-5555-5555-555555555555"
BINDING = "33333333-3333-3333-3333-333333333333"


def context() -> dict[str, Any]:
    return {
        "runId": "run",
        "workspace": {
            "workspaceId": WORKSPACE,
            "repositoryId": REPOSITORY,
            "repositoryName": "devcollab",
            "defaultBranch": "main",
        },
        "task": {
            "selectedPaths": ["src/App.java"],
            "userInstruction": "Keep docs aligned",
        },
        "codeFiles": [
            {
                "filePath": "src/App.java",
                "language": "Java",
                "content": "line one\nline two\nline three",
                "truncated": False,
            }
        ],
        "existingBindings": [
            {
                "bindingId": BINDING,
                "filePath": "src/App.java",
                "documentId": DOCUMENT,
                "blockId": BLOCK,
                "secretMetadata": "drop",
            }
        ],
        "documents": [
            {
                "source": "CANDIDATE",
                "documentId": "66666666-6666-6666-6666-666666666666",
                "structure": {
                    "documentId": "66666666-6666-6666-6666-666666666666",
                    "title": "Candidate",
                    "blocks": [],
                },
            },
            {
                "source": "BOUND",
                "documentId": DOCUMENT,
                "structure": {
                    "documentId": DOCUMENT,
                    "title": "Design",
                    "documentType": "REQUIREMENT",
                    "reviewStatus": "DRAFT",
                    "version": 3,
                    "blocks": [
                        {
                            "blockId": BLOCK,
                            "type": "PARAGRAPH",
                            "sortOrder": 0,
                            "version": 7,
                            "plainText": "old",
                        }
                    ],
                    "internal": "drop",
                },
            },
        ],
        "budget": {"toolCallsUsed": 5},
        "authorization": "Bearer never",
        "trace": ["drop"],
    }


def update_plan() -> dict[str, Any]:
    return {
        "decision": "SUBMIT_REVIEW",
        "summary": "Update the documented behavior",
        "rationale": "The implementation now exposes behavior missing from the document.",
        "operations": [
            {
                "clientOperationId": "op-1",
                "sequenceNumber": 1,
                "operationType": "UPDATE_BLOCK",
                "documentId": DOCUMENT,
                "blockId": BLOCK,
                "baseBlockVersion": 7,
                "proposedPlainText": "new behavior",
            }
        ],
        "bindingProposals": [],
        "evidence": [
            {
                "clientOperationId": "op-1",
                "repositoryId": REPOSITORY,
                "filePath": "src/App.java",
                "startLine": 1,
                "endLine": 2,
                "description": "Implementation evidence",
            }
        ],
    }


def validate(raw: dict[str, Any], bundle: dict[str, Any] | None = None) -> AgentPlan:
    plan = AgentPlan.model_validate(raw)
    return AgentPlanValidator().validate(plan, build_model_context(bundle or context()))


def test_model_context_keeps_selected_code() -> None:
    assert (
        build_model_context(context())["codeFiles"][0]["content"]
        == "line one\nline two\nline three"
    )


def test_model_context_orders_bound_before_candidate() -> None:
    assert [item["source"] for item in build_model_context(context())["documents"]] == [
        "BOUND",
        "CANDIDATE",
    ]


@pytest.mark.parametrize(
    "forbidden",
    ["authorization", "trace", "budget", "secretMetadata", "httpHeaders", "redisUrl"],
)
def test_model_context_excludes_internal_metadata(forbidden: str) -> None:
    assert forbidden not in str(build_model_context(context()))


def test_prompt_contains_safety_constraints() -> None:
    prompt = Path("app/prompts/document_sync_v1.md").read_text(encoding="utf-8")
    assert "Never reference unread files" in prompt
    assert "Do not generate userId, role, status" in prompt
    assert "NO_CHANGE" in prompt


def test_valid_no_change() -> None:
    raw = {
        "decision": "NO_CHANGE",
        "summary": "No synchronization is needed",
        "rationale": "The code and document describe the same behavior.",
        "operations": [],
        "bindingProposals": [],
        "evidence": [],
    }
    assert validate(raw).decision == "NO_CHANGE"


def test_no_change_with_operation_rejected_by_schema() -> None:
    raw = update_plan()
    raw["decision"] = "NO_CHANGE"
    with pytest.raises(ValidationError):
        AgentPlan.model_validate(raw)


def test_submit_without_changes_rejected_by_schema() -> None:
    raw = {
        "decision": "SUBMIT_REVIEW",
        "summary": "Change",
        "rationale": "Reason",
        "operations": [],
        "bindingProposals": [],
        "evidence": [],
    }
    with pytest.raises(ValidationError):
        AgentPlan.model_validate(raw)


def test_extra_model_field_rejected() -> None:
    raw = update_plan()
    raw["toolName"] = "danger"
    with pytest.raises(ValidationError):
        AgentPlan.model_validate(raw)


def test_valid_update_block() -> None:
    assert validate(update_plan()).operations[0].baseBlockVersion == 7


@pytest.mark.parametrize(
    ("field", "value", "code"),
    [
        ("blockId", "77777777-7777-7777-7777-777777777777", "BLOCK_NOT_READ"),
        ("baseBlockVersion", 8, "BLOCK_VERSION_MISMATCH"),
        ("documentId", "77777777-7777-7777-7777-777777777777", "BLOCK_NOT_READ"),
    ],
)
def test_update_rejects_fabricated_targets(field: str, value: Any, code: str) -> None:
    raw = update_plan()
    raw["operations"][0][field] = value
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert code in {issue.code for issue in caught.value.issues}


def test_evidence_rejects_unread_file() -> None:
    raw = update_plan()
    raw["evidence"][0]["filePath"] = "src/Unknown.java"
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "FILE_NOT_READ" in {issue.code for issue in caught.value.issues}


def test_evidence_rejects_line_overflow() -> None:
    raw = update_plan()
    raw["evidence"][0]["endLine"] = 99
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "LINE_OUT_OF_RANGE" in {issue.code for issue in caught.value.issues}


def test_evidence_rejects_repository_mismatch() -> None:
    raw = update_plan()
    raw["evidence"][0]["repositoryId"] = "77777777-7777-7777-7777-777777777777"
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "REPOSITORY_MISMATCH" in {issue.code for issue in caught.value.issues}


def create_document_plan() -> dict[str, Any]:
    return {
        "decision": "SUBMIT_REVIEW",
        "summary": "Create API documentation",
        "rationale": "The selected implementation has no formal document.",
        "operations": [
            {
                "clientOperationId": "create-1",
                "sequenceNumber": 1,
                "operationType": "CREATE_DOCUMENT",
                "proposedDocumentTitle": "Application API",
                "proposedDocumentType": "API",
            },
            {
                "clientOperationId": "block-1",
                "sequenceNumber": 2,
                "operationType": "ADD_BLOCK",
                "createdDocumentClientOperationId": "create-1",
                "proposedBlockType": "PARAGRAPH",
                "proposedPlainText": "The application exposes an API.",
            },
        ],
        "bindingProposals": [
            {
                "clientBindingProposalId": "binding-1",
                "sequenceNumber": 3,
                "action": "UPSERT_BINDING",
                "repositoryId": REPOSITORY,
                "filePath": "src/App.java",
                "createdDocumentClientOperationId": "create-1",
                "reason": "Keep this API document linked to its implementation.",
            }
        ],
        "evidence": [
            {
                "clientOperationId": "create-1",
                "repositoryId": REPOSITORY,
                "filePath": "src/App.java",
                "startLine": 1,
                "endLine": 2,
                "description": "API implementation",
            },
            {
                "clientOperationId": "block-1",
                "repositoryId": REPOSITORY,
                "filePath": "src/App.java",
                "startLine": 1,
                "endLine": 2,
                "description": "Block content source",
            },
        ],
    }


def test_valid_create_add_and_binding_plan() -> None:
    assert len(validate(create_document_plan()).operations) == 2


@pytest.mark.parametrize(
    ("mutation", "code"),
    [
        (
            ("operations", 1, "createdDocumentClientOperationId", "missing"),
            "CREATE_REFERENCE_UNKNOWN",
        ),
        (("operations", 0, "documentId", DOCUMENT), "CREATE_TARGET_INVALID"),
        (("operations", 1, "blockId", BLOCK), "BLOCK_TARGET_INVALID"),
        (("bindingProposals", 0, "repositoryId", WORKSPACE), "REPOSITORY_MISMATCH"),
        (("bindingProposals", 0, "filePath", "src/Unknown.java"), "FILE_NOT_READ"),
    ],
)
def test_create_plan_invalid_references(mutation: tuple[str, int, str, Any], code: str) -> None:
    raw = create_document_plan()
    collection, index, field, value = mutation
    raw[collection][index][field] = value
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert code in {issue.code for issue in caught.value.issues}


def test_remove_unknown_binding_rejected() -> None:
    raw = create_document_plan()
    raw["operations"] = []
    raw["bindingProposals"] = [
        {
            "clientBindingProposalId": "remove-1",
            "sequenceNumber": 1,
            "action": "REMOVE_BINDING",
            "repositoryId": REPOSITORY,
            "filePath": "src/App.java",
            "documentId": DOCUMENT,
            "bindingId": "77777777-7777-7777-7777-777777777777",
            "reason": "Remove stale link",
        }
    ]
    raw["evidence"] = [
        {
            "repositoryId": REPOSITORY,
            "filePath": "src/App.java",
            "description": "Binding no longer matches",
        }
    ]
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "BINDING_NOT_READ" in {issue.code for issue in caught.value.issues}


def test_duplicate_existing_upsert_rejected() -> None:
    raw = create_document_plan()
    raw["operations"] = []
    raw["bindingProposals"][0].pop("createdDocumentClientOperationId")
    raw["bindingProposals"][0]["documentId"] = DOCUMENT
    raw["bindingProposals"][0]["sequenceNumber"] = 1
    raw["evidence"] = [
        {
            "repositoryId": REPOSITORY,
            "filePath": "src/App.java",
            "description": "Existing relationship",
        }
    ]
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "BINDING_EXISTS" in {issue.code for issue in caught.value.issues}


@pytest.mark.parametrize(
    ("field", "first", "second"),
    [
        ("clientOperationId", "same", "same"),
        ("sequenceNumber", 1, 1),
    ],
)
def test_duplicate_operation_identity_rejected(field: str, first: Any, second: Any) -> None:
    raw = create_document_plan()
    raw["operations"][0][field] = first
    raw["operations"][1][field] = second
    with pytest.raises(PlanValidationError):
        validate(raw)


def test_missing_operation_evidence_rejected() -> None:
    raw = update_plan()
    raw["evidence"][0]["clientOperationId"] = None
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "OPERATION_EVIDENCE_REQUIRED" in {issue.code for issue in caught.value.issues}


def test_delete_with_proposed_text_rejected() -> None:
    raw = update_plan()
    raw["operations"][0]["operationType"] = "DELETE_BLOCK"
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert "DELETE_CONTENT_INVALID" in {issue.code for issue in caught.value.issues}


def test_validation_error_details_are_structured() -> None:
    raw = deepcopy(update_plan())
    raw["operations"][0]["baseBlockVersion"] = 999
    with pytest.raises(PlanValidationError) as caught:
        validate(raw)
    assert set(caught.value.safe_details()[0]) == {"path", "code", "message"}
