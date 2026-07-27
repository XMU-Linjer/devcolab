import json
from typing import Any

import httpx
import pytest

from app.providers.base import ModelProviderError
from app.providers.deepseek import DeepSeekProvider


def no_change() -> dict[str, Any]:
    return {
        "decision": "NO_CHANGE",
        "summary": "No synchronization is needed",
        "rationale": "The implementation and document agree.",
        "operations": [],
        "bindingProposals": [],
        "evidence": [],
    }


def provider(handler: Any, key: str = "secret-key") -> DeepSeekProvider:
    return DeepSeekProvider(
        api_key=key,
        base_url="https://model.invalid",
        model="deepseek-chat",
        connect_timeout_seconds=1,
        total_timeout_seconds=2,
        transport=httpx.MockTransport(handler),
    )


def response(plan: dict[str, Any]) -> httpx.Response:
    return httpx.Response(
        200,
        json={"choices": [{"message": {"content": json.dumps(plan)}}]},
    )


@pytest.mark.asyncio
async def test_deepseek_parses_valid_json_and_uses_authorization_header() -> None:
    seen: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["authorization"] = request.headers["Authorization"]
        return response(no_change())

    result = await provider(handler).plan_document_sync({"codeFiles": []})
    assert result.decision == "NO_CHANGE"
    assert seen["authorization"] == "Bearer secret-key"


@pytest.mark.asyncio
async def test_deepseek_timeout() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    with pytest.raises(ModelProviderError) as caught:
        await provider(handler).plan_document_sync({})
    assert caught.value.code == "MODEL_TIMEOUT"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "code"),
    [
        (429, "MODEL_RATE_LIMITED"),
        (500, "MODEL_UNAVAILABLE"),
        (503, "MODEL_UNAVAILABLE"),
        (400, "MODEL_CONFIGURATION_ERROR"),
    ],
)
async def test_deepseek_http_error_mapping(status: int, code: str) -> None:
    with pytest.raises(ModelProviderError) as caught:
        await provider(lambda request: httpx.Response(status)).plan_document_sync({})
    assert caught.value.code == code


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "payload",
    [
        b"not-json",
        json.dumps({"choices": []}).encode(),
        json.dumps({"choices": [{"message": {"content": "not-json"}}]}).encode(),
        json.dumps(
            {"choices": [{"message": {"content": json.dumps({"decision": "NO_CHANGE"})}}]}
        ).encode(),
    ],
)
async def test_deepseek_invalid_response(payload: bytes) -> None:
    with pytest.raises(ModelProviderError) as caught:
        await provider(
            lambda request: httpx.Response(
                200, content=payload, headers={"content-type": "application/json"}
            )
        ).plan_document_sync({})
    assert caught.value.code == "MODEL_INVALID_RESPONSE"


@pytest.mark.asyncio
async def test_deepseek_error_never_contains_key() -> None:
    with pytest.raises(ModelProviderError) as caught:
        await provider(lambda request: httpx.Response(500), "top-secret").plan_document_sync({})
    assert "top-secret" not in str(caught.value)


@pytest.mark.asyncio
async def test_deepseek_repair_payload_contains_safe_validation_errors() -> None:
    seen: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(json.loads(request.content))
        return response(no_change())

    await provider(handler).plan_document_sync(
        {"codeFiles": []},
        previous_plan=no_change(),
        validation_errors=[{"path": "operations", "code": "BAD", "message": "fix"}],
    )
    user = json.loads(seen["messages"][1]["content"])
    assert user["agentPlanSchema"]["title"] == "AgentPlan"
    assert user["repair"]["validationErrors"][0]["code"] == "BAD"


@pytest.mark.asyncio
async def test_deepseek_requires_configuration() -> None:
    empty = DeepSeekProvider(
        api_key="",
        base_url="",
        model="",
        connect_timeout_seconds=1,
        total_timeout_seconds=2,
    )
    with pytest.raises(ModelProviderError) as caught:
        await empty.plan_document_sync({})
    assert caught.value.code == "MODEL_CONFIGURATION_ERROR"
