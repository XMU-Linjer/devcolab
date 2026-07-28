from typing import Any, Protocol

from app.schemas.plans import AgentPlan
from app.schemas.unit_plans import UnitPlan


class ModelProviderError(RuntimeError):
    def __init__(
        self,
        code: str,
        message: str,
        *,
        raw_plan: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.raw_plan = raw_plan


class ModelProvider(Protocol):
    async def plan_document_sync(
        self,
        context_bundle: dict[str, Any],
        *,
        previous_plan: dict[str, Any] | None = None,
        validation_errors: list[dict[str, str]] | None = None,
    ) -> AgentPlan: ...

    async def plan_project_units(
        self,
        project_index: dict[str, Any],
        *,
        previous_plan: dict[str, Any] | None = None,
        validation_errors: list[dict[str, str]] | None = None,
    ) -> UnitPlan: ...
