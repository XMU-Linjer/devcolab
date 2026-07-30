from __future__ import annotations

import importlib.util
import json
from pathlib import Path
from typing import Any

import pytest

MODULE_PATH = Path(__file__).with_name("profile_run.py")
SPEC = importlib.util.spec_from_file_location("profile_run", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
profile_run = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(profile_run)


def test_host_sample_has_memory_and_cpu_fields() -> None:
    sample = profile_run.host_sample("run-1")
    assert sample["totalMemoryBytes"] > 0
    assert sample["availableMemoryBytes"] > 0
    assert sample["recordType"] == "sample"


def test_docker_json_is_parsed() -> None:
    raw = json.dumps(
        {
            "ID": "abc",
            "Name": "core",
            "MemUsage": "12.5MiB / 1GiB",
            "MemPerc": "1.22%",
            "CPUPerc": "0.10%",
            "PIDs": "14",
            "BlockIO": "0B / 0B",
        }
    )
    sample = profile_run.parse_docker_stats_line(raw, "run-1")
    assert sample is not None
    assert sample["memoryUsageBytes"] == int(12.5 * 1024 * 1024)
    assert sample["memoryLimitBytes"] == 1024**3
    assert sample["pids"] == 14


def test_docker_unavailable_is_nonfatal(monkeypatch: pytest.MonkeyPatch) -> None:
    def fail(*_args: object, **_kwargs: object) -> Any:
        raise FileNotFoundError

    monkeypatch.setattr(profile_run.subprocess, "run", fail)
    samples, error = profile_run.docker_samples("run-1")
    assert samples == []
    assert error == "FileNotFoundError"


def test_collect_stops_at_duration_and_writes_host_samples(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(profile_run, "docker_samples", lambda _run: ([], "offline"))
    profile_run.collect("short-run", tmp_path, 500, 0.05)
    assert (tmp_path / "short-run" / "host-samples.jsonl").read_text()


def _write(path: Path, records: list[dict[str, object]], corrupt: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [json.dumps(record) for record in records]
    if corrupt:
        lines.append("{invalid")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def sample(at: str, rss: int, monotonic: int) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "recordType": "sample",
        "timestampUtc": at,
        "monotonicNanos": monotonic,
        "runId": "summary-run",
        "service": "agent-worker",
        "instanceId": "worker-1",
        "pid": 1,
        "rssBytes": rss,
        "heapUsedBytes": None,
        "directBufferUsedBytes": None,
        "pythonAllocatedBytes": rss // 2,
        "gcCount": None,
        "threadCount": 3,
        "droppedRecords": 2,
    }


def event(kind: str, at: str, duration: int | None = None) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "recordType": "stage_event",
        "timestampUtc": at,
        "monotonicNanos": 1,
        "runId": "summary-run",
        "service": "agent-worker",
        "instanceId": "worker-1",
        "pid": 1,
        "eventType": kind,
        "stage": "UNIT_EXECUTION",
        "stageExecutionId": "stage-1",
        "parentStageExecutionId": None,
        "jobId": "job-1",
        "repositoryId": "repo-1",
        "revision": "sha",
        "unitId": "unit-1",
        "durationMs": duration,
        "attributes": {"fileCount": 2},
    }


def test_streaming_summary_calculates_series_stage_and_quality(tmp_path: Path) -> None:
    run = tmp_path / "summary-run"
    _write(
        run / "agent-worker-1-samples.jsonl",
        [
            sample("2026-01-01T00:00:00Z", 100, 1_000_000_000),
            sample("2026-01-01T00:00:01Z", 180, 2_000_000_000),
            sample("2026-01-01T00:00:02Z", 130, 3_000_000_000),
        ],
        corrupt=True,
    )
    _write(
        run / "agent-worker-1-events.jsonl",
        [
            event("STARTED", "2026-01-01T00:00:00Z"),
            event("COMPLETED", "2026-01-01T00:00:02Z", 2000),
            {**event("STARTED", "2026-01-01T00:00:03Z"), "stageExecutionId": "lost"},
        ],
    )

    result = profile_run.summarize("summary-run", tmp_path)

    service = result["services"]["worker-1"]
    assert (service["baselineBytes"], service["peakBytes"], service["endBytes"]) == (
        100,
        180,
        130,
    )
    assert result["stages"][0]["peakDeltaBytes"] == 80
    assert result["stages"][0]["shortTermDropBytes"] == 50
    assert result["dataQuality"]["corruptLines"] == 1
    assert result["dataQuality"]["unmatchedStarted"] == 1
    assert result["run"]["droppedRecordCount"] == 2
    assert result["jobTrend"][0]["jobId"] == "job-1"
    assert (run / "summary.json").exists()
    assert (run / "report.md").exists()


def test_invalid_run_id_cannot_escape_output(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        profile_run.summarize("../escape", tmp_path)
