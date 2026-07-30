from __future__ import annotations

import json
import time
import tracemalloc
from pathlib import Path

import pytest

from app.profiling import MemoryProfileConfig, RuntimeMemoryProfiler


def config(tmp_path: Path, *, enabled: bool = True, capacity: int = 16) -> MemoryProfileConfig:
    return MemoryProfileConfig(
        enabled=enabled,
        run_id="python-test",
        output_dir=tmp_path,
        interval_ms=500,
        queue_capacity=capacity,
    )


def records(tmp_path: Path, suffix: str) -> list[dict[str, object]]:
    path = next((tmp_path / "python-test").glob(f"*{suffix}"))
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def test_disabled_profiler_does_not_start_tracemalloc_or_create_files(
    tmp_path: Path,
) -> None:
    if tracemalloc.is_tracing():
        tracemalloc.stop()
    profiler = RuntimeMemoryProfiler(config(tmp_path, enabled=False), "agent-worker")
    assert not profiler.enabled
    assert not tracemalloc.is_tracing()
    profiler.close()
    assert list(tmp_path.iterdir()) == []


def test_enabled_profiler_writes_rss_and_python_allocation_sample(
    tmp_path: Path,
) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    time.sleep(0.05)
    sample = profiler.sample()
    profiler.close()
    assert isinstance(sample["rssBytes"], int) and sample["rssBytes"] > 0
    assert isinstance(sample["pythonAllocatedBytes"], int)
    assert isinstance(sample["pythonPeakAllocatedBytes"], int)
    assert sample["schemaVersion"] == 1


def test_stage_records_completed_and_nested_parent(tmp_path: Path) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    with profiler.stage("UNIT_EXECUTION", job_id="job") as outer:
        outer.attribute("fileCount", 2)
        with profiler.stage("DOCUMENT_PROPOSAL", job_id="job"):
            pass
    profiler.close()
    events = records(tmp_path, "-events.jsonl")
    child = next(item for item in events if item["stage"] == "DOCUMENT_PROPOSAL")
    assert child["parentStageExecutionId"] == outer.execution_id
    assert [item["eventType"] for item in events].count("COMPLETED") == 2


def test_stage_records_failed_and_reraises_original(tmp_path: Path) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    with pytest.raises(RuntimeError, match="original"):
        with profiler.stage("PLANNER"):
            raise RuntimeError("original")
    profiler.close()
    event = records(tmp_path, "-events.jsonl")[-1]
    assert event["eventType"] == "FAILED"
    assert event["errorType"] == "RuntimeError"
    assert "original" not in json.dumps(event)


def test_attributes_are_scalar_size_and_count_bounded(tmp_path: Path) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    with profiler.stage("JOB") as stage:
        with pytest.raises(ValueError):
            stage.attribute("payload", {"large": "object"})
        with pytest.raises(ValueError):
            stage.attribute("prompt", "x" * 257)
        for index in range(24):
            stage.attribute(f"value{index}", index)
        with pytest.raises(ValueError):
            stage.attribute("overflow", 1)
    profiler.close()


@pytest.mark.parametrize("run_id", ["../escape", "a/b", r"a\b", ""])
def test_unsafe_or_missing_explicit_run_ids_are_handled(
    tmp_path: Path, run_id: str
) -> None:
    candidate = MemoryProfileConfig(True, run_id, tmp_path, 500, 8)
    if run_id:
        with pytest.raises(ValueError):
            RuntimeMemoryProfiler(candidate, "agent-worker")
    else:
        profiler = RuntimeMemoryProfiler(candidate, "agent-worker")
        assert profiler.enabled
        profiler.close()


def test_output_failure_disables_without_raising(tmp_path: Path) -> None:
    output_file = tmp_path / "file"
    output_file.write_text("occupied", encoding="utf-8")
    profiler = RuntimeMemoryProfiler(
        MemoryProfileConfig(True, "safe", output_file, 500, 8), "agent-worker"
    )
    assert not profiler.enabled
    profiler.close()


def test_bounded_queue_drops_without_blocking(tmp_path: Path) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path, capacity=1), "agent-worker")
    started = time.monotonic()
    for _ in range(10_000):
        with profiler.stage("JOB"):
            pass
    elapsed = time.monotonic() - started
    profiler.close()
    assert elapsed < 5
    assert profiler.stats["droppedRecords"] > 0


def test_writer_failure_disables_profiler(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    original_open = Path.open

    def broken_open(path: Path, *args: object, **kwargs: object) -> object:
        if path.suffix == ".jsonl":
            raise OSError("simulated")
        return original_open(path, *args, **kwargs)

    monkeypatch.setattr(Path, "open", broken_open)
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    deadline = time.monotonic() + 2
    while profiler.enabled and time.monotonic() < deadline:
        time.sleep(0.01)
    assert not profiler.enabled
    assert profiler.stats["writeErrors"] == 1
    profiler.close()


def test_shutdown_flushes_events(tmp_path: Path) -> None:
    profiler = RuntimeMemoryProfiler(config(tmp_path), "agent-worker")
    with profiler.stage("REVIEW_BUILD"):
        pass
    profiler.close()
    assert [item["eventType"] for item in records(tmp_path, "-events.jsonl")] == [
        "STARTED",
        "COMPLETED",
    ]
