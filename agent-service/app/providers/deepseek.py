import json
from importlib.resources import files
from typing import Any

import httpx
from pydantic import ValidationError

from app.providers.base import ModelProviderError
from app.schemas.plans import AgentPlan


class DeepSeekProvider:
    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model: str,
        connect_timeout_seconds: float,
        total_timeout_seconds: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = httpx.Timeout(
            total_timeout_seconds,
            connect=connect_timeout_seconds,
        )
        self._transport = transport
        self._system_prompt = (
            files("app.prompts").joinpath("document_sync_v1.md").read_text(encoding="utf-8")
        )

    async def plan_document_sync(
        self,
        context_bundle: dict[str, Any],
        *,
        previous_plan: dict[str, Any] | None = None,
        validation_errors: list[dict[str, str]] | None = None,
    ) -> AgentPlan:
        self._require_configuration()
        user_payload: dict[str, Any] = {
            "agentPlanSchema": AgentPlan.model_json_schema(),
            "context": context_bundle,
        }
        if previous_plan is not None:
            user_payload["repair"] = {
                "previousPlan": previous_plan,
                "validationErrors": validation_errors or [],
                "instruction": (
                    "直接返回修正后的完整 AgentPlan JSON。正式标题和正文必须是可发布的"
                    "简体中文最终内容；不要解释错误原因，不要输出建议、计划或占位文字。"
                ),
            }
        body = {
            "model": self._model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": self._system_prompt},
                {
                    "role": "user",
                    "content": json.dumps(user_payload, ensure_ascii=False, separators=(",", ":")),
                },
            ],
        }
        try:
            async with httpx.AsyncClient(
                timeout=self._timeout,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"{self._base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self._api_key}"},
                    json=body,
                )
        except httpx.TimeoutException as exc:
            raise ModelProviderError("MODEL_TIMEOUT", "Model request timed out") from exc
        except httpx.HTTPError as exc:
            raise ModelProviderError("MODEL_UNAVAILABLE", "Model request failed") from exc

        if response.status_code == 429:
            raise ModelProviderError("MODEL_RATE_LIMITED", "Model rate limit exceeded")
        if response.status_code >= 500:
            raise ModelProviderError("MODEL_UNAVAILABLE", "Model service is unavailable")
        if response.status_code >= 400:
            raise ModelProviderError("MODEL_CONFIGURATION_ERROR", "Model request was rejected")
        raw_plan: dict[str, Any] | None = None
        try:
            response_json = response.json()
            content = response_json["choices"][0]["message"]["content"]
            decoded = json.loads(content)
            if not isinstance(decoded, dict):
                raise TypeError("AgentPlan must be an object")
            raw_plan = decoded
            return AgentPlan.model_validate(raw_plan)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError, ValidationError) as exc:
            raise ModelProviderError(
                "MODEL_INVALID_RESPONSE",
                "Model returned an invalid AgentPlan",
                raw_plan=raw_plan,
            ) from exc

    def _require_configuration(self) -> None:
        if not self._api_key or not self._base_url or not self._model:
            raise ModelProviderError(
                "MODEL_CONFIGURATION_ERROR",
                "DeepSeek provider is not configured",
            )
