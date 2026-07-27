from typing import Any

from conftest import FakeMcpClient
from test_workflow import initial_state

from app.config import Settings
from app.graph.workflow import ContextWorkflow


async def test_workflow_arguments_match_the_five_current_mcp_tool_contracts(
    settings: Settings,
) -> None:
    expected_keys = {
        "devcollab.workspace.get_context": {"workspaceId"},
        "devcollab.code.read": {"workspaceId", "repositoryId", "path"},
        "devcollab.binding.list": {"workspaceId", "repositoryId", "filePath"},
        "devcollab.document.find_candidates": {
            "workspaceId",
            "repositoryId",
            "filePath",
            "limit",
        },
        "devcollab.document.get_structure": {
            "workspaceId",
            "documentId",
            "includeBlockContent",
        },
    }

    class ContractCheckingMcp(FakeMcpClient):
        async def call_tool(
            self,
            name: str,
            arguments: dict[str, Any],
            authorization: str,
        ) -> dict[str, Any]:
            assert set(arguments) == expected_keys[name]
            assert authorization == "Bearer transient"
            return await super().call_tool(name, arguments, authorization)

    client = ContractCheckingMcp(bound=False)
    result = await ContextWorkflow(client, settings).graph.ainvoke(
        initial_state(["src/Example.java"])
    )

    assert [name for name, _, _ in client.calls] == list(expected_keys)
    assert result["context_bundle"]["documents"][0]["source"] == "CANDIDATE"
