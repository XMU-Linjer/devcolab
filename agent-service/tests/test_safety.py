import httpx
import pytest
from conftest import FakeMcpClient, MemoryRunStore, request_payload
from fastapi.testclient import TestClient
from test_workflow import initial_state

from app.clients.mcp_client import McpClientError, OfficialMcpClient
from app.clients.run_store import RunStoreError
from app.config import Settings
from app.context.budget import ToolCallLimitExceededError
from app.graph.workflow import ContextWorkflow
from app.main import create_app


async def test_official_client_rejects_write_tool_before_network() -> None:
    client = OfficialMcpClient("http://localhost:1/mcp", 0.1)
    with pytest.raises(McpClientError, match="not allowed"):
        await client.call_tool(
            "devcollab.review.submit_document_change",
            {},
            "Bearer transient",
        )


async def test_official_client_maps_mcp_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class TimedOutClient:
        def __init__(self, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> None:
            raise httpx.ReadTimeout("timed out")

        async def __aexit__(self, *_args: object) -> None:
            return None

    monkeypatch.setattr("app.clients.mcp_client.httpx.AsyncClient", TimedOutClient)
    client = OfficialMcpClient("http://localhost:8091/mcp", 0.1)
    with pytest.raises(McpClientError) as error:
        await client.call_tool(
            "devcollab.workspace.get_context",
            {"workspaceId": "11111111-1111-1111-1111-111111111111"},
            "Bearer transient",
        )
    assert error.value.code == "MCP_UNAVAILABLE"
    assert str(error.value) == "MCP request timed out"


def test_official_client_whitelist_is_exactly_five_read_tools() -> None:
    assert OfficialMcpClient.ALLOWED_TOOLS == {
        "devcollab.workspace.get_context",
        "devcollab.code.read",
        "devcollab.binding.list",
        "devcollab.document.find_candidates",
        "devcollab.document.get_structure",
    }


async def test_tool_call_budget_is_enforced(settings: Settings) -> None:
    limited = settings.model_copy(update={"agent_max_tool_calls": 3})
    with pytest.raises(ToolCallLimitExceededError):
        await ContextWorkflow(FakeMcpClient(), limited).graph.ainvoke(
            initial_state(["src/Example.java"])
        )


def test_redis_failure_is_service_unavailable(settings: Settings) -> None:
    class FailingStore:
        async def save(self, run_id: str, payload: dict[str, object], ttl: int) -> None:
            raise RunStoreError("Redis is unavailable")

        async def get(self, run_id: str) -> None:
            raise RunStoreError("Redis is unavailable")

    with TestClient(
        create_app(
            settings=settings,
            mcp_client=FakeMcpClient(),
            run_store=FailingStore(),
        )
    ) as client:
        response = client.post(
            "/api/v1/agent-runs/context",
            json=request_payload(),
            headers={"Authorization": "Bearer transient"},
        )
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "REDIS_UNAVAILABLE"


def test_mcp_permission_failure_marks_run_failed(settings: Settings) -> None:
    class DeniedMcp(FakeMcpClient):
        async def call_tool(
            self, name: str, arguments: dict[str, object], authorization: str
        ) -> dict[str, object]:
            raise McpClientError("MCP_PERMISSION_DENIED", "Workspace access denied")

    store = MemoryRunStore()
    with TestClient(
        create_app(settings=settings, mcp_client=DeniedMcp(), run_store=store)
    ) as client:
        response = client.post(
            "/api/v1/agent-runs/context",
            json=request_payload(),
            headers={"Authorization": "Bearer transient"},
        )
    assert response.status_code == 200
    assert response.json()["status"] == "FAILED"
    assert response.json()["error"]["code"] == "MCP_PERMISSION_DENIED"
    assert "transient" not in str(store.values)
