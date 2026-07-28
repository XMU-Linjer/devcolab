from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.schemas.plans import AgentPlan


class MemoryRunStore:
    def __init__(self) -> None:
        self.values: dict[str, dict[str, object]] = {}
        self.ttls: dict[str, int] = {}

    async def save(self, run_id: str, payload: dict[str, object], ttl: int) -> None:
        self.values[run_id] = payload
        self.ttls[run_id] = ttl

    async def get(self, run_id: str) -> dict[str, object] | None:
        return self.values.get(run_id)

    async def save_job(self, job_id: str, payload: dict[str, object], ttl: int) -> None:
        self.values[f"job:{job_id}"] = payload
        self.ttls[f"job:{job_id}"] = ttl

    async def get_job(self, job_id: str) -> dict[str, object] | None:
        return self.values.get(f"job:{job_id}")


class FakeMcpClient:
    def __init__(
        self,
        *,
        bound: bool = True,
        code_content: str = "class Example {}",
    ) -> None:
        self.bound = bound
        self.code_content = code_content
        self.calls: list[tuple[str, dict[str, Any], str]] = []
        self.submissions: list[tuple[AgentPlan, str, str, str]] = []

    async def call_tool(
        self, name: str, arguments: dict[str, Any], authorization: str
    ) -> dict[str, Any]:
        self.calls.append((name, arguments, authorization))
        if name == "devcollab.workspace.get_context":
            return {
                "workspaceId": arguments["workspaceId"],
                "name": "Runtime Workspace",
                "currentUserRole": "ADMIN",
                "repositories": [
                    {
                        "repositoryId": "22222222-2222-2222-2222-222222222222",
                        "name": "devcollab",
                        "provider": "GITHUB",
                        "remoteUrl": "https://example.invalid/devcollab",
                        "defaultBranch": "main",
                        "syncStatus": "SYNCED",
                    }
                ],
            }
        if name == "devcollab.code.read":
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "path": arguments["path"],
                "commitHash": "abc",
                "language": "Java",
                "sizeBytes": len(self.code_content),
                "startLine": 1,
                "endLine": 1,
                "totalLines": 1,
                "content": self.code_content,
                "truncated": False,
                "omittedLineCount": 0,
                "omittedCharacterCount": 0,
                "existingBindings": [],
                "existingBindingsAvailable": True,
                "existingBindingsRequested": False,
            }
        if name == "devcollab.binding.list":
            bindings = (
                [
                    {
                        "bindingId": "33333333-3333-3333-3333-333333333333",
                        "pathPattern": arguments["filePath"],
                        "documentId": "44444444-4444-4444-4444-444444444444",
                        "documentTitle": "Bound Design",
                        "blockId": None,
                    }
                ]
                if self.bound
                else []
            )
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "filePath": arguments["filePath"],
                "fileHasBindings": bool(bindings),
                "bindings": bindings,
                "truncated": False,
                "omittedBindingCount": 0,
            }
        if name == "devcollab.document.find_candidates":
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "filePath": arguments["filePath"],
                "query": None,
                "candidates": [
                    {
                        "documentId": "55555555-5555-5555-5555-555555555555",
                        "title": "Candidate Design",
                        "score": 10,
                        "matchReasons": [],
                        "matchedBlockIds": [],
                        "existingBindingCount": 0,
                    }
                ],
                "truncated": False,
                "omittedCandidateCount": 0,
            }
        if name == "devcollab.document.get_structure":
            return {
                "documentId": arguments["documentId"],
                "workspaceId": arguments["workspaceId"],
                "title": "Design",
                "documentType": "REQUIREMENT",
                "reviewStatus": "DRAFT",
                "updatedAt": "2026-07-27T00:00:00Z",
                "blocks": [],
                "version": 1,
                "truncated": False,
                "omittedBlockCount": 0,
                "omittedCharacterCount": 0,
            }
        if name == "devcollab.repository.list_files":
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "pathPrefix": arguments.get("pathPrefix", ""),
                "recursive": arguments.get("recursive", True),
                "files": [
                    {
                        "filePath": "src/Example.java",
                        "fileName": "Example.java",
                        "extension": "java",
                        "sizeBytes": 16,
                        "language": "Java",
                        "readable": True,
                        "isDirectory": False,
                    }
                ],
                "nextCursor": None,
                "hasMore": False,
            }
        if name == "devcollab.repository.list_changes":
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "changeId": "77777777-7777-7777-7777-777777777777",
                "changeType": "COMMIT",
                "commitSha": "abc",
                "files": [
                    {
                        "status": "MODIFIED",
                        "filePath": "src/Example.java",
                        "oldPath": None,
                        "binaryFile": False,
                    }
                ],
                "nextCursor": None,
                "hasMore": False,
            }
        if name == "devcollab.binding.list_batch":
            return {
                "workspaceId": arguments["workspaceId"],
                "repositoryId": arguments["repositoryId"],
                "files": [
                    {
                        "filePath": path,
                        "bindings": [
                            {
                                "bindingId": "33333333-3333-3333-3333-333333333333",
                                "repositoryId": arguments["repositoryId"],
                                "documentId": "44444444-4444-4444-4444-444444444444",
                                "blockId": None,
                                "pathPattern": path,
                            }
                        ]
                        if self.bound
                        else [],
                    }
                    for path in arguments["filePaths"]
                ],
            }
        raise AssertionError(f"Unexpected tool: {name}")

    async def submit_document_change(
        self,
        plan: AgentPlan,
        *,
        workspace_id: str,
        run_id: str,
        authorization: str,
    ) -> dict[str, Any]:
        self.submissions.append((plan, workspace_id, run_id, authorization))
        return {
            "changeRequestId": "99999999-9999-9999-9999-999999999999",
            "status": "PENDING",
            "createdAt": "2026-07-27T00:00:00Z",
            "idempotentReplay": False,
        }


class FakeModelProvider:
    def __init__(self, plans: list[AgentPlan | Exception] | None = None) -> None:
        self.plans = plans or [
            AgentPlan.model_validate(
                {
                    "decision": "NO_CHANGE",
                    "summary": "No synchronization is needed",
                    "rationale": "The implementation and documentation agree.",
                    "operations": [],
                    "bindingProposals": [],
                    "evidence": [],
                }
            )
        ]
        self.calls: list[dict[str, Any]] = []

    async def plan_document_sync(
        self,
        context_bundle: dict[str, Any],
        *,
        previous_plan: dict[str, Any] | None = None,
        validation_errors: list[dict[str, str]] | None = None,
    ) -> AgentPlan:
        self.calls.append(
            {
                "context": context_bundle,
                "previousPlan": previous_plan,
                "validationErrors": validation_errors,
            }
        )
        item = self.plans[min(len(self.calls) - 1, len(self.plans) - 1)]
        if isinstance(item, Exception):
            raise item
        return item


@pytest.fixture
def settings() -> Settings:
    return Settings(
        agent_max_selected_files=2,
        agent_max_code_chars=40,
        agent_max_bound_documents=5,
        agent_max_candidate_documents=5,
        agent_max_document_structures=3,
        agent_max_tool_calls=12,
        agent_run_ttl_seconds=86400,
    )


@pytest.fixture
def fake_mcp() -> FakeMcpClient:
    return FakeMcpClient()


@pytest.fixture
def run_store() -> MemoryRunStore:
    return MemoryRunStore()


@pytest.fixture
def client(
    settings: Settings,
    fake_mcp: FakeMcpClient,
    run_store: MemoryRunStore,
) -> TestClient:
    with TestClient(
        create_app(
            settings=settings,
            mcp_client=fake_mcp,
            run_store=run_store,
            model_provider=FakeModelProvider(),
        )
    ) as test_client:
        yield test_client


def request_payload(paths: list[str] | None = None) -> dict[str, Any]:
    return {
        "workspaceId": "11111111-1111-1111-1111-111111111111",
        "repositoryId": "22222222-2222-2222-2222-222222222222",
        "selectedPaths": paths or ["src/Example.java"],
        "userInstruction": "Check documentation alignment",
    }
