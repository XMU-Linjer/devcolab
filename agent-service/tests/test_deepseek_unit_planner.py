from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

import pytest
from conftest import FakeMcpClient, MemoryAgentJobRepository

from app.planning.deepseek_unit_planner import DeepSeekUnitPlanner
from app.planning.unit_plan_validator import UnitPlanValidationError, UnitPlanValidator
from app.runtime.project_unit_context import ProjectUnitContextBuilder
from app.runtime.semantic_planner import ProjectFile, materialize_deepseek_units
from app.schemas.unit_plans import UnitPlan


def _index() -> dict[str, Any]:
    return {
        "repositoryId": "22222222-2222-2222-2222-222222222222",
        "revision": "abc",
        "files": [
            {"filePath": "src/AuthController.java", "eligible": True},
            {"filePath": "src/AuthService.java", "eligible": True},
            {"filePath": "src/AuthRepository.java", "eligible": True},
        ],
        "documents": [
            {
                "documentId": "44444444-4444-4444-4444-444444444444",
                "title": "认证设计",
            }
        ],
    }


def _plan(**updates: Any) -> UnitPlan:
    item: dict[str, Any] = {
        "name": "认证服务",
        "kind": "BUSINESS_SERVICE",
        "summary": "描述认证应用服务的业务边界。",
        "primaryFiles": ["src/AuthService.java"],
        "supportingFiles": ["src/AuthRepository.java"],
        "relatedDocumentIds": ["44444444-4444-4444-4444-444444444444"],
        "groupingEvidence": ["AuthService imports AuthRepository"],
    }
    item.update(updates)
    return UnitPlan.model_validate({"units": [item]})


def test_validator_accepts_file_overlap_between_units() -> None:
    first = _plan().units[0].model_dump(mode="json")
    second = {
        **first,
        "name": "认证接口",
        "kind": "BACKEND_REST_API",
        "primaryFiles": ["src/AuthController.java"],
        "supportingFiles": ["src/AuthService.java"],
    }
    result = UnitPlanValidator(max_files_per_unit=4, max_units=10).validate(
        UnitPlan.model_validate({"units": [first, second]}),
        _index(),
    )
    assert result.units[0].primaryFiles[0] in result.units[1].supportingFiles


def test_validator_rejects_unknown_file() -> None:
    with pytest.raises(UnitPlanValidationError):
        UnitPlanValidator(max_files_per_unit=4, max_units=10).validate(
            _plan(primaryFiles=["src/Missing.java"]),
            _index(),
        )


def test_schema_rejects_empty_unit() -> None:
    with pytest.raises(ValueError):
        _plan(primaryFiles=[], supportingFiles=[])


@pytest.mark.asyncio
async def test_planner_repairs_at_most_once_without_rule_fallback() -> None:
    class Provider:
        def __init__(self) -> None:
            self.calls = 0

        async def plan_project_units(
            self,
            project_index: dict[str, Any],
            *,
            previous_plan: dict[str, Any] | None = None,
            validation_errors: list[dict[str, str]] | None = None,
        ) -> UnitPlan:
            self.calls += 1
            return (
                _plan(primaryFiles=["src/Missing.java"])
                if self.calls == 1
                else _plan()
            )

        async def plan_document_sync(self, *_args: Any, **_kwargs: Any) -> Any:
            raise AssertionError("document planner must not run")

    provider = Provider()
    planner = DeepSeekUnitPlanner(
        provider,  # type: ignore[arg-type]
        max_files_per_unit=4,
        max_units=10,
    )
    result = await planner.plan(_index())
    assert result.units[0].name == "认证服务"
    assert provider.calls == 2


@pytest.mark.asyncio
async def test_batched_planner_uses_deepseek_for_candidates_and_final_plan() -> None:
    first = {
        **_index(),
        "files": _index()["files"][:2],
        "batch": {"index": 0, "count": 2},
    }
    second = {
        **_index(),
        "files": [_index()["files"][2]],
        "batch": {"index": 1, "count": 2},
    }

    class Provider:
        def __init__(self) -> None:
            self.inputs: list[dict[str, Any]] = []

        async def plan_project_units(
            self,
            project_index: dict[str, Any],
            **_kwargs: Any,
        ) -> UnitPlan:
            self.inputs.append(project_index)
            if project_index.get("planningMode") == "CONSOLIDATE_BATCH_PLANS":
                return _plan()
            path = project_index["files"][0]["filePath"]
            return _plan(primaryFiles=[path], supportingFiles=[])

        async def plan_document_sync(self, *_args: Any, **_kwargs: Any) -> Any:
            raise AssertionError("document planner must not run")

    provider = Provider()
    result = await DeepSeekUnitPlanner(
        provider,  # type: ignore[arg-type]
        max_files_per_unit=4,
        max_units=10,
    ).plan([first, second], validation_index=_index())

    assert result == _plan()
    assert len(provider.inputs) == 3
    assert provider.inputs[-1]["planningMode"] == "CONSOLIDATE_BATCH_PLANS"
    assert len(provider.inputs[-1]["candidateBatchPlans"]) == 2


@pytest.mark.asyncio
async def test_batched_planner_reserves_single_repair_for_final_plan() -> None:
    first = {
        **_index(),
        "files": _index()["files"][:2],
        "batch": {"index": 0, "count": 2},
    }
    second = {
        **_index(),
        "files": [_index()["files"][2]],
        "batch": {"index": 1, "count": 2},
    }

    class Provider:
        def __init__(self) -> None:
            self.calls = 0
            self.final_repair_errors: list[dict[str, str]] = []

        async def plan_project_units(
            self,
            project_index: dict[str, Any],
            *,
            previous_plan: dict[str, Any] | None = None,
            validation_errors: list[dict[str, str]] | None = None,
        ) -> UnitPlan:
            self.calls += 1
            if project_index.get("planningMode") != "CONSOLIDATE_BATCH_PLANS":
                return _plan(primaryFiles=["src/Missing.java"])
            if previous_plan is None:
                assert all(
                    candidate["validationIssues"]
                    for candidate in project_index["candidateBatchPlans"]
                )
                return _plan(primaryFiles=["src/Missing.java"])
            self.final_repair_errors = validation_errors or []
            return _plan()

        async def plan_document_sync(self, *_args: Any, **_kwargs: Any) -> Any:
            raise AssertionError("document planner must not run")

    provider = Provider()
    result = await DeepSeekUnitPlanner(
        provider,  # type: ignore[arg-type]
        max_files_per_unit=4,
        max_units=10,
    ).plan([first, second], validation_index=_index())

    assert result == _plan()
    assert provider.calls == 4
    assert provider.final_repair_errors[0]["code"] == "UNKNOWN_FILE"


@pytest.mark.asyncio
async def test_project_unit_context_uses_batch_bindings_and_fixed_revision(
    settings: Any,
) -> None:
    client = FakeMcpClient(bound=False)
    state = await ProjectUnitContextBuilder(client, settings).build(
        run_id="unit-1",
        workspace_id="11111111-1111-1111-1111-111111111111",
        repository_id="22222222-2222-2222-2222-222222222222",
        revision="abc",
        selected_paths=["src/AuthService.java", "src/AuthRepository.java"],
        preferred_document_ids=[],
        user_instruction="生成正式认证设计文档",
    )
    assert [item["filePath"] for item in state["context_bundle"]["codeFiles"]] == [
        "src/AuthService.java",
        "src/AuthRepository.java",
    ]
    assert sum(
        name == "devcollab.binding.list_batch"
        for name, _arguments, _authorization in client.calls
    ) == 1
    assert not any(
        name == "devcollab.binding.list"
        for name, _arguments, _authorization in client.calls
    )


@pytest.mark.asyncio
async def test_same_document_units_are_serialized_and_partial_result_is_isolated() -> None:
    repository = MemoryAgentJobRepository()
    job_id = uuid4()
    discovery_id = uuid4()
    now = datetime.now(UTC)
    await repository.create_job(
        {
            "id": job_id,
            "delegation_id": uuid4(),
            "created_by_user_id": uuid4(),
            "workspace_id": UUID("11111111-1111-1111-1111-111111111111"),
            "repository_id": UUID("22222222-2222-2222-2222-222222222222"),
            "revision": "abc",
            "scope_type": "PROJECT_INITIALIZATION",
            "scope_payload": {"type": "PROJECT_INITIALIZATION"},
            "user_instruction": None,
            "created_at": now,
        },
        {
            "id": discovery_id,
            "max_attempts": 3,
            "unit_kind": "PROJECT_DISCOVERY",
        },
    )
    repository.units[discovery_id].update(
        status="RUNNING", worker_id="planner", attempt=1
    )
    files = [
        ProjectFile(uuid4(), "src/AuthService.java", "Java", 100),
        ProjectFile(uuid4(), "src/AuthRepository.java", "Java", 100),
        ProjectFile(uuid4(), "src/AuthController.java", "Java", 100),
    ]
    plan = UnitPlan.model_validate(
        {
            "units": [
                _plan().units[0].model_dump(mode="json"),
                {
                    **_plan().units[0].model_dump(mode="json"),
                    "name": "认证接口",
                    "kind": "BACKEND_REST_API",
                    "primaryFiles": ["src/AuthController.java"],
                    "supportingFiles": [],
                },
            ]
        }
    )
    units = materialize_deepseek_units(
        plan, files, job_id=job_id, revision="abc"
    )
    rows = [
        {
            "id": item.id,
            "repository_id": UUID("22222222-2222-2222-2222-222222222222"),
            "revision": "abc",
            "file_path": item.file_path,
        }
        for item in files
    ]
    await repository.complete_project_discovery(
        discovery_id,
        "planner",
        rows,
        units,
        {
            "discovered_file_count": 3,
            "supported_code_count": 3,
            "skipped_file_count": 0,
            "skipped_reason_counts": {},
            "metadata_parsed_count": 3,
            "metadata_failed_count": 0,
            "bound_file_count": 3,
            "unbound_file_count": 0,
            "analysis_unit_count": 2,
            "overlapping_file_count": 0,
        },
        1,
        0,
    )
    first = await repository.claim_next_unit("worker-1", 60)
    assert first is not None
    assert await repository.claim_next_unit("worker-2", 60) is None
    await repository.complete_unit(
        UUID(str(first["id"])), "worker-1", "NO_CHANGE", None
    )
    second = await repository.claim_next_unit("worker-2", 60)
    assert second is not None
    await repository.fail_unit(
        UUID(str(second["id"])),
        "worker-2",
        "MODEL_INVALID_RESPONSE",
        "invalid",
        None,
    )
    assert repository.jobs[job_id]["status"] == "PARTIALLY_COMPLETED"
