import json
import time
from typing import Any

import pytest
from conftest import FakeMcpClient, MemoryRunStore
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.runtime.file_classification import classify_file
from app.runtime.unit_grouping import build_analysis_units

WORKSPACE_ID = "11111111-1111-1111-1111-111111111111"
REPOSITORY_ID = "22222222-2222-2222-2222-222222222222"
AUTH = {"Authorization": "Bearer transient-secret"}


@pytest.mark.parametrize(
    "scope",
    [
        {"type": "CURRENT_FILE", "filePath": "src/Example.java"},
        {"type": "DIRECTORY", "pathPrefix": "src", "recursive": True},
        {"type": "GIT_CHANGES"},
        {"type": "PROJECT_INITIALIZATION"},
    ],
)
def test_all_scopes_reach_ready_without_model_or_submit(
    scope: dict[str, Any],
    settings: Settings,
) -> None:
    mcp = FakeMcpClient()
    store = MemoryRunStore()
    with TestClient(
        create_app(
            settings=settings,
            mcp_client=mcp,
            run_store=store,
        )
    ) as client:
        response = client.post(
            "/api/v1/agent-jobs",
            json={
                "workspaceId": WORKSPACE_ID,
                "repositoryId": REPOSITORY_ID,
                "scope": scope,
                "userInstruction": "not persisted",
            },
            headers=AUTH,
        )
        assert response.status_code == 202
        job = _wait_for_terminal(client, response.json()["jobId"])
        assert job["status"] == "READY_FOR_ANALYSIS"
        assert job["unitCount"] == 1
        assert job["completedUnitCount"] == 0
        assert job["reviewRequestIds"] == []
        units = client.get(
            f"/api/v1/agent-jobs/{response.json()['jobId']}/units",
            headers=AUTH,
        )
        assert units.status_code == 200
        assert units.json()["units"][0]["status"] == "PENDING"
        names = [call[0] for call in mcp.calls]
        assert "devcollab.binding.list_batch" in names
        assert "devcollab.binding.list" not in names
        assert "devcollab.code.read" not in names
        assert mcp.submissions == []
        stored = json.dumps(store.values, ensure_ascii=False)
        assert "transient-secret" not in stored
        assert "not persisted" not in stored
        assert "class Example" not in stored
        assert store.ttls[f"job:{response.json()['jobId']}"] == 86_400


def test_job_request_forbids_identity_and_runtime_fields(
    client: TestClient,
) -> None:
    base = {
        "workspaceId": WORKSPACE_ID,
        "repositoryId": REPOSITORY_ID,
        "scope": {"type": "PROJECT_INITIALIZATION"},
    }
    for field, value in [
        ("userId", WORKSPACE_ID),
        ("status", "QUEUED"),
        ("JWT", "secret"),
        ("clientRequestId", "id"),
    ]:
        response = client.post(
            "/api/v1/agent-jobs",
            json={**base, field: value},
            headers=AUTH,
        )
        assert response.status_code == 422


def test_job_requires_bearer(client: TestClient) -> None:
    response = client.post(
        "/api/v1/agent-jobs",
        json={
            "workspaceId": WORKSPACE_ID,
            "repositoryId": REPOSITORY_ID,
            "scope": {"type": "PROJECT_INITIALIZATION"},
        },
    )
    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "AUTHENTICATION_REQUIRED"


def test_units_api_is_paginated(settings: Settings) -> None:
    mcp = PagedMcpClient(file_count=7)
    store = MemoryRunStore()
    with TestClient(
        create_app(
            settings=settings.model_copy(update={"agent_max_files_per_unit": 2}),
            mcp_client=mcp,
            run_store=store,
        )
    ) as client:
        created = client.post(
            "/api/v1/agent-jobs",
            json={
                "workspaceId": WORKSPACE_ID,
                "repositoryId": REPOSITORY_ID,
                "scope": {"type": "PROJECT_INITIALIZATION"},
            },
            headers=AUTH,
        )
        job = _wait_for_terminal(client, created.json()["jobId"])
        assert job["unitCount"] == 4
        page = client.get(
            f"/api/v1/agent-jobs/{created.json()['jobId']}/units?offset=1&limit=2",
            headers=AUTH,
        ).json()
        assert page["total"] == 4
        assert len(page["units"]) == 2


def test_pagination_deduplicates_files_and_batches_bindings(settings: Settings) -> None:
    mcp = PagedMcpClient(file_count=5, duplicate_pages=True)
    store = MemoryRunStore()
    configured = settings.model_copy(
        update={
            "agent_repository_page_size": 2,
            "agent_binding_batch_size": 2,
        }
    )
    with TestClient(create_app(settings=configured, mcp_client=mcp, run_store=store)) as client:
        created = client.post(
            "/api/v1/agent-jobs",
            json={
                "workspaceId": WORKSPACE_ID,
                "repositoryId": REPOSITORY_ID,
                "scope": {"type": "PROJECT_INITIALIZATION"},
            },
            headers=AUTH,
        )
        job = _wait_for_terminal(client, created.json()["jobId"])
        assert job["discoveredFileCount"] == 5
        batch_calls = [call for call in mcp.calls if call[0] == "devcollab.binding.list_batch"]
        assert [len(call[1]["filePaths"]) for call in batch_calls] == [2, 2, 1]


def test_cursor_loop_and_discovery_limit_become_failed(settings: Settings) -> None:
    for mcp, expected in [
        (LoopingCursorMcpClient(), "REPOSITORY_SCAN_FAILED"),
        (PagedMcpClient(file_count=3), "DISCOVERY_LIMIT_EXCEEDED"),
    ]:
        configured = settings.model_copy(
            update={
                "agent_repository_page_size": 2,
                "agent_max_discovered_files": 2,
            }
        )
        with TestClient(
            create_app(settings=configured, mcp_client=mcp, run_store=MemoryRunStore())
        ) as client:
            created = client.post(
                "/api/v1/agent-jobs",
                json={
                    "workspaceId": WORKSPACE_ID,
                    "repositoryId": REPOSITORY_ID,
                    "scope": {"type": "PROJECT_INITIALIZATION"},
                },
                headers=AUTH,
            )
            job = _wait_for_terminal(client, created.json()["jobId"])
            assert job["status"] == "FAILED"
            assert job["errorCode"] == expected


def test_file_classification_covers_supported_and_skip_reasons() -> None:
    cases = [
        ({"filePath": "src/A.java", "sizeBytes": 10, "readable": True}, "SUPPORTED_CODE"),
        ({"filePath": "README.md", "sizeBytes": 10, "readable": True}, "TEXT_NON_CODE_SKIPPED"),
        ({"filePath": "image.png", "sizeBytes": 10, "readable": False}, "BINARY_SKIPPED"),
        (
            {"filePath": "vendor/A.java", "sizeBytes": 10, "readable": True},
            "VENDOR_GENERATED_SKIPPED",
        ),
        ({"filePath": "src/Huge.java", "sizeBytes": 101, "readable": True}, "OVERSIZED_SKIPPED"),
        ({"filePath": "src/A.java", "binaryFile": False}, "DELETED_CODE_REFERENCE"),
    ]
    for index, (item, expected) in enumerate(cases):
        result = classify_file(item, max_size_bytes=100, deleted=index == 5)
        assert result.classification == expected


def test_grouping_preserves_many_to_many_and_splits_large_components() -> None:
    document_a = "44444444-4444-4444-4444-444444444444"
    document_b = "55555555-5555-5555-5555-555555555555"
    files = [
        classify_file(
            {"filePath": f"src/auth/F{index}.java", "sizeBytes": 10, "readable": True},
            max_size_bytes=100,
        )
        for index in range(7)
    ]
    bindings = {
        item.file_path: [
            {
                "bindingId": f"33333333-3333-3333-3333-{index:012d}",
                "documentId": document_a,
            }
        ]
        for index, item in enumerate(files)
    }
    bindings[files[0].file_path].append(
        {
            "bindingId": "66666666-6666-6666-6666-666666666666",
            "documentId": document_b,
        }
    )
    units = build_analysis_units(
        files,
        bindings,
        source_type="PROJECT_INITIALIZATION",
        max_files=6,
        max_deleted=100,
        max_units=10,
    )
    assert [len(unit.filePaths) for unit in units] == [6, 1]
    assert all(document_a in {str(value) for value in unit.boundDocumentIds} for unit in units)
    assert document_b in {str(value) for value in units[0].boundDocumentIds}
    assert all("SIZE_LIMIT_SPLIT" in unit.groupingReasons for unit in units)


def test_unbound_modules_are_not_merged_and_deleted_paths_are_not_read() -> None:
    files = [
        classify_file(
            {"filePath": "module-a/src/A.java", "sizeBytes": 10, "readable": True},
            max_size_bytes=100,
        ),
        classify_file(
            {"filePath": "module-b/src/B.java", "sizeBytes": 10, "readable": True},
            max_size_bytes=100,
        ),
        classify_file(
            {"filePath": "module-a/src/Gone.java", "binaryFile": False},
            max_size_bytes=100,
            deleted=True,
        ),
    ]
    units = build_analysis_units(
        files,
        {},
        source_type="GIT_CHANGES",
        max_files=6,
        max_deleted=100,
        max_units=10,
    )
    assert len(units) == 2
    assert {unit.primaryDirectory for unit in units} == {"module-a/src", "module-b/src"}
    assert [path for unit in units for path in unit.deletedPaths] == ["module-a/src/Gone.java"]


def test_unit_limit_fails_explicitly() -> None:
    files = [
        classify_file(
            {"filePath": f"module-{index}/A.java", "sizeBytes": 10, "readable": True},
            max_size_bytes=100,
        )
        for index in range(2)
    ]
    with pytest.raises(ValueError, match="UNIT_LIMIT_EXCEEDED"):
        build_analysis_units(
            files,
            {},
            source_type="PROJECT_INITIALIZATION",
            max_files=6,
            max_deleted=100,
            max_units=1,
        )


class PagedMcpClient:
    def __init__(self, file_count: int, duplicate_pages: bool = False) -> None:
        self.files = [
            {
                "filePath": f"src/F{index}.java",
                "fileName": f"F{index}.java",
                "extension": "java",
                "sizeBytes": 10,
                "language": "Java",
                "readable": True,
                "isDirectory": False,
            }
            for index in range(file_count)
        ]
        self.duplicate_pages = duplicate_pages
        self.calls: list[tuple[str, dict[str, Any], str]] = []

    async def call_tool(
        self, name: str, arguments: dict[str, Any], authorization: str
    ) -> dict[str, Any]:
        self.calls.append((name, arguments, authorization))
        if name == "devcollab.repository.list_files":
            offset = int(arguments.get("cursor", "0"))
            limit = int(arguments["limit"])
            items = self.files[offset : offset + limit]
            if self.duplicate_pages and offset:
                items = [self.files[offset - 1], *items]
            next_offset = offset + limit
            return {
                "files": items,
                "hasMore": next_offset < len(self.files),
                "nextCursor": str(next_offset) if next_offset < len(self.files) else None,
            }
        if name == "devcollab.binding.list_batch":
            return {
                "files": [{"filePath": path, "bindings": []} for path in arguments["filePaths"]]
            }
        raise AssertionError(name)

    async def submit_document_change(self, *_args: Any, **_kwargs: Any) -> None:
        raise AssertionError("submit must not be called")


class LoopingCursorMcpClient:
    async def call_tool(
        self, name: str, arguments: dict[str, Any], _authorization: str
    ) -> dict[str, Any]:
        if name == "devcollab.repository.list_files":
            return {
                "files": [
                    {
                        "filePath": "src/A.java",
                        "sizeBytes": 10,
                        "readable": True,
                    }
                ],
                "hasMore": True,
                "nextCursor": "same",
            }
        raise AssertionError(name)


def _wait_for_terminal(client: TestClient, job_id: str) -> dict[str, Any]:
    for _ in range(100):
        response = client.get(f"/api/v1/agent-jobs/{job_id}", headers=AUTH)
        assert response.status_code == 200
        payload = response.json()
        if payload["status"] in {"READY_FOR_ANALYSIS", "FAILED", "CANCELLED"}:
            return payload
        time.sleep(0.01)
    raise AssertionError("Agent job did not reach a terminal state")
