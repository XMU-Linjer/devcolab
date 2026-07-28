from typing import Any
from uuid import UUID

from app.clients.delegation_client import DelegationClient
from app.clients.mcp_client import McpClientError, ReviewMcpClient
from app.schemas.plans import AgentPlan


class DelegatedMcpClient:
    def __init__(
        self,
        client: ReviewMcpClient,
        delegation_client: DelegationClient,
        *,
        delegation_id: UUID,
        job_id: UUID,
        revision: str,
    ) -> None:
        self._client = client
        self._delegation_client = delegation_client
        self._delegation_id = delegation_id
        self._job_id = job_id
        self._revision = revision

    async def call_tool(
        self, name: str, arguments: dict[str, Any], _authorization: str
    ) -> dict[str, Any]:
        authorization = await self._delegation_client.exchange(
            delegation_id=self._delegation_id,
            job_id=self._job_id,
        )
        result = await self._client.call_tool(name, arguments, authorization)
        if name == "devcollab.code.read":
            actual = str(result.get("commitHash") or "")
            if actual.lower() != self._revision.lower():
                raise McpClientError(
                    "REVISION_CHANGED",
                    "Repository revision changed after the Agent job was created",
                )
        return result

    async def submit_document_change(
        self,
        plan: AgentPlan,
        *,
        workspace_id: str,
        run_id: str,
        authorization: str,
    ) -> dict[str, Any]:
        del authorization
        delegated = await self._delegation_client.exchange(
            delegation_id=self._delegation_id,
            job_id=self._job_id,
        )
        return await self._client.submit_document_change(
            plan,
            workspace_id=workspace_id,
            run_id=run_id,
            authorization=delegated,
        )
