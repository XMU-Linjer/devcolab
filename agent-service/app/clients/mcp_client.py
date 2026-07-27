from typing import Any, Protocol

import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

from app.schemas.plans import AgentPlan


class McpClientError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


def _contains_timeout(exception: BaseException) -> bool:
    if isinstance(exception, httpx.TimeoutException):
        return True
    if isinstance(exception, BaseExceptionGroup):
        return any(_contains_timeout(item) for item in exception.exceptions)
    if exception.__cause__ is not None and _contains_timeout(exception.__cause__):
        return True
    if exception.__context__ is not None and _contains_timeout(exception.__context__):
        return True
    return False


class ReadOnlyMcpClient(Protocol):
    async def call_tool(
        self, name: str, arguments: dict[str, Any], authorization: str
    ) -> dict[str, Any]: ...


class ReviewMcpClient(ReadOnlyMcpClient, Protocol):
    async def submit_document_change(
        self,
        plan: AgentPlan,
        *,
        workspace_id: str,
        run_id: str,
        authorization: str,
    ) -> dict[str, Any]: ...


class OfficialMcpClient:
    ALLOWED_TOOLS = frozenset(
        {
            "devcollab.workspace.get_context",
            "devcollab.code.read",
            "devcollab.binding.list",
            "devcollab.document.find_candidates",
            "devcollab.document.get_structure",
        }
    )

    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        self._base_url = base_url
        self._timeout_seconds = timeout_seconds

    async def call_tool(
        self, name: str, arguments: dict[str, Any], authorization: str
    ) -> dict[str, Any]:
        if name not in self.ALLOWED_TOOLS:
            raise McpClientError("INVALID_REQUEST", f"Tool is not allowed: {name}")
        return await self._invoke_tool(name, arguments, authorization)

    async def submit_document_change(
        self,
        plan: AgentPlan,
        *,
        workspace_id: str,
        run_id: str,
        authorization: str,
    ) -> dict[str, Any]:
        return await self._invoke_tool(
            "devcollab.review.submit_document_change",
            plan.mcp_payload(workspace_id, f"agent-{run_id}"),
            authorization,
        )

    async def _invoke_tool(
        self,
        name: str,
        arguments: dict[str, Any],
        authorization: str,
    ) -> dict[str, Any]:
        try:
            async with httpx.AsyncClient(
                headers={"Authorization": authorization},
                timeout=self._timeout_seconds,
            ) as http_client:
                async with streamable_http_client(
                    self._base_url,
                    http_client=http_client,
                ) as (read_stream, write_stream, _):
                    async with ClientSession(read_stream, write_stream) as session:
                        await session.initialize()
                        result = await session.call_tool(name, arguments)
        except httpx.TimeoutException as exc:
            raise McpClientError("MCP_UNAVAILABLE", "MCP request timed out") from exc
        except McpClientError:
            raise
        except Exception as exc:
            if _contains_timeout(exc):
                raise McpClientError("MCP_UNAVAILABLE", "MCP request timed out") from exc
            raise McpClientError("MCP_UNAVAILABLE", "MCP request failed") from exc

        content = result.structuredContent
        if not isinstance(content, dict):
            raise McpClientError("MCP_UNAVAILABLE", "MCP returned no structured content")
        error = content.get("error")
        if isinstance(error, dict):
            code = str(error.get("code", "MCP_UNAVAILABLE"))
            if code in {"PERMISSION_DENIED", "AUTHENTICATION_REQUIRED"}:
                raise McpClientError(f"MCP_{code}", str(error.get("message", code)))
            raise McpClientError(code, str(error.get("message", code)))
        return content
