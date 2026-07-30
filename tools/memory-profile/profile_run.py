#!/usr/bin/env python3
"""Collect and summarize lightweight DevCollab runtime memory profiles."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from collections import defaultdict
from collections.abc import Iterable, Iterator
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import psutil  # type: ignore[import-untyped]

SCHEMA_VERSION = 1
SAFE_RUN_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
SIZE_UNITS = {
    "B": 1,
    "KB": 1000,
    "KIB": 1024,
    "MB": 1000**2,
    "MIB": 1024**2,
    "GB": 1000**3,
    "GIB": 1024**3,
    "TB": 1000**4,
    "TIB": 1024**4,
}


def utc_now() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def validate_run_id(value: str) -> str:
    if not SAFE_RUN_ID.fullmatch(value):
        raise ValueError("runId must contain only letters, digits, dot, dash, underscore")
    return value


def parse_size(value: str | None) -> int | None:
    if not value:
        return None
    match = re.fullmatch(r"\s*([\d.]+)\s*([A-Za-z]+)\s*", value)
    if not match:
        return None
    factor = SIZE_UNITS.get(match.group(2).upper())
    return None if factor is None else int(float(match.group(1)) * factor)


def parse_percent(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return float(value.strip().removesuffix("%"))
    except ValueError:
        return None


def host_sample(run_id: str) -> dict[str, Any]:
    memory = psutil.virtual_memory()
    swap = psutil.swap_memory()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "recordType": "sample",
        "timestampUtc": utc_now(),
        "monotonicNanos": time.monotonic_ns(),
        "runId": run_id,
        "service": "host",
        "instanceId": "host",
        "pid": None,
        "totalMemoryBytes": memory.total,
        "availableMemoryBytes": memory.available,
        "usedMemoryBytes": memory.used,
        "swapTotalBytes": swap.total,
        "swapUsedBytes": swap.used,
        "cpuPercent": psutil.cpu_percent(interval=None),
    }


def parse_docker_stats_line(line: str, run_id: str) -> dict[str, Any] | None:
    try:
        raw = json.loads(line)
    except json.JSONDecodeError:
        return None
    usage, separator, limit = str(raw.get("MemUsage", "")).partition("/")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "recordType": "sample",
        "timestampUtc": utc_now(),
        "monotonicNanos": time.monotonic_ns(),
        "runId": run_id,
        "service": "docker",
        "instanceId": str(raw.get("Container") or raw.get("ID") or "unknown"),
        "pid": None,
        "containerId": raw.get("ID") or raw.get("Container"),
        "containerName": raw.get("Name"),
        "memoryUsageBytes": parse_size(usage),
        "memoryLimitBytes": parse_size(limit) if separator else None,
        "memoryPercent": parse_percent(raw.get("MemPerc")),
        "cpuPercent": parse_percent(raw.get("CPUPerc")),
        "pids": _optional_int(raw.get("PIDs")),
        "blockIo": raw.get("BlockIO"),
    }


def docker_samples(run_id: str, timeout_seconds: float = 10) -> tuple[list[dict[str, Any]], str | None]:
    try:
        result = subprocess.run(
            ["docker", "stats", "--no-stream", "--format", "{{json .}}"],
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return [], type(exc).__name__
    if result.returncode != 0:
        return [], f"docker_exit_{result.returncode}"
    parsed = [
        sample
        for line in result.stdout.splitlines()
        if (sample := parse_docker_stats_line(line, run_id)) is not None
    ]
    return parsed, None


def collect(
    run_id: str,
    output: Path,
    interval_ms: int,
    duration_seconds: float | None,
) -> None:
    run_id = validate_run_id(run_id)
    if interval_ms < 500:
        raise ValueError("interval-ms must be at least 500")
    run_dir = _safe_run_dir(output, run_id)
    run_dir.mkdir(parents=True, exist_ok=True)
    host_path = run_dir / "host-samples.jsonl"
    docker_path = run_dir / "docker-samples.jsonl"
    errors_path = run_dir / "collector-errors.jsonl"
    deadline = None if duration_seconds is None else time.monotonic() + duration_seconds
    error_counts: dict[str, int] = defaultdict(int)
    try:
        with (
            host_path.open("a", encoding="utf-8", buffering=64 * 1024) as host_file,
            docker_path.open("a", encoding="utf-8", buffering=64 * 1024) as docker_file,
            errors_path.open("a", encoding="utf-8", buffering=16 * 1024) as errors_file,
        ):
            while deadline is None or time.monotonic() < deadline:
                started = time.monotonic()
                _write_json(host_file, host_sample(run_id))
                samples, error = docker_samples(run_id)
                for sample in samples:
                    _write_json(docker_file, sample)
                if error and error_counts[error] < 3:
                    error_counts[error] += 1
                    _write_json(
                        errors_file,
                        {
                            "schemaVersion": SCHEMA_VERSION,
                            "recordType": "collector_error",
                            "timestampUtc": utc_now(),
                            "runId": run_id,
                            "source": "docker",
                            "errorType": error[:128],
                        },
                    )
                wait = interval_ms / 1000 - (time.monotonic() - started)
                if wait > 0:
                    time.sleep(wait)
    except KeyboardInterrupt:
        print("[memory-profile] collection stopped", file=sys.stderr)


def iter_jsonl(path: Path, quality: dict[str, Any]) -> Iterator[dict[str, Any]]:
    try:
        with path.open(encoding="utf-8") as source:
            for line_number, line in enumerate(source, 1):
                try:
                    value = json.loads(line)
                    if isinstance(value, dict):
                        yield value
                    else:
                        quality["corruptLines"] += 1
                except json.JSONDecodeError:
                    quality["corruptLines"] += 1
                    quality["corruptLineLocations"].append(f"{path.name}:{line_number}")
    except OSError:
        quality["missingSources"].append(path.name)


def summarize(run_id: str, output: Path) -> dict[str, Any]:
    run_dir = _safe_run_dir(output, validate_run_id(run_id))
    quality: dict[str, Any] = {
        "corruptLines": 0,
        "corruptLineLocations": [],
        "missingSources": [],
        "unmatchedStarted": 0,
        "unmatchedFinished": 0,
        "samplingGaps": [],
    }
    files = sorted(
        run_dir.glob("*.jsonl"),
        key=lambda path: (1 if path.name.endswith("-events.jsonl") else 0, path.name),
    )
    if not files:
        raise FileNotFoundError(f"No JSONL profile files found in {run_dir}")
    host = _Series()
    host_details: dict[str, int | None] = {
        "minimumAvailableMemoryBytes": None,
        "maximumUsedMemoryBytes": None,
        "maximumSwapUsedBytes": None,
        "endAvailableMemoryBytes": None,
    }
    containers: dict[str, _Series] = defaultdict(_Series)
    services: dict[str, _Series] = defaultdict(_Series)
    service_names: dict[str, str] = {}
    stages: dict[str, dict[str, Any]] = {}
    finished: list[dict[str, Any]] = []
    timestamps: list[str] = []
    sample_count = 0
    dropped = 0
    interval_values: list[int] = []
    previous_monotonic: dict[str, int] = {}

    for path in files:
        for record in iter_jsonl(path, quality):
            timestamp = record.get("timestampUtc")
            if isinstance(timestamp, str):
                timestamps.append(timestamp)
            record_type = record.get("recordType")
            if record_type == "sample":
                sample_count += 1
                dropped = max(dropped, _optional_int(record.get("droppedRecords")) or 0)
                service = str(record.get("service") or "unknown")
                monotonic = _optional_int(record.get("monotonicNanos"))
                if monotonic is not None and service in previous_monotonic:
                    interval_values.append(
                        max(0, (monotonic - previous_monotonic[service]) // 1_000_000)
                    )
                if monotonic is not None:
                    previous_monotonic[service] = monotonic
                if service == "host":
                    host.add(record, "usedMemoryBytes")
                    available = _optional_int(record.get("availableMemoryBytes"))
                    used = _optional_int(record.get("usedMemoryBytes"))
                    swap = _optional_int(record.get("swapUsedBytes"))
                    current_min = host_details["minimumAvailableMemoryBytes"]
                    host_details["minimumAvailableMemoryBytes"] = (
                        available
                        if current_min is None
                        else (
                            current_min
                            if available is None
                            else min(current_min, available)
                        )
                    )
                    host_details["maximumUsedMemoryBytes"] = _max_optional(
                        host_details["maximumUsedMemoryBytes"], used
                    )
                    host_details["maximumSwapUsedBytes"] = _max_optional(
                        host_details["maximumSwapUsedBytes"], swap
                    )
                    host_details["endAvailableMemoryBytes"] = available
                elif service == "docker":
                    key = str(record.get("containerName") or record.get("containerId"))
                    containers[key].add(record, "memoryUsageBytes")
                else:
                    instance = str(
                        record.get("instanceId") or f"{service}-{record.get('pid')}"
                    )
                    services[instance].add(record, "rssBytes")
                    service_names[instance] = service
            elif record_type == "stage_event":
                execution = str(record.get("stageExecutionId") or "")
                if record.get("eventType") == "STARTED":
                    stages[execution] = record
                elif record.get("eventType") in {"COMPLETED", "FAILED"}:
                    started = stages.pop(execution, None)
                    if started is None:
                        quality["unmatchedFinished"] += 1
                    finished.append(_stage_summary(started, record))
    stages_by_service: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for stage in finished:
        stages_by_service[str(stage["instanceId"])].append(stage)
    second_pass_quality = {
        "corruptLines": 0,
        "corruptLineLocations": [],
        "missingSources": [],
    }
    for path in files:
        if path.name.endswith("-events.jsonl"):
            continue
        for record in iter_jsonl(path, second_pass_quality):
            if record.get("recordType") != "sample":
                continue
            instance = str(
                record.get("instanceId")
                or f"{record.get('service')}-{record.get('pid')}"
            )
            for stage in stages_by_service.get(instance, []):
                _add_stage_sample(stage, record)
    for stage in finished:
        stage["peakDeltaBytes"] = _difference(
            stage["peakRssBytes"], stage["startRssBytes"]
        )
        stage["shortTermDropBytes"] = _difference(
            stage["peakRssBytes"], stage["endRssBytes"]
        )
    quality["unmatchedStarted"] = len(stages)
    present_services = {"host" if host.count else "", *service_names.values()}
    if containers:
        present_services.add("docker")
    for expected in (
        "host",
        "docker",
        "knowledge-core",
        "devcollab-worker",
        "agent-service",
        "agent-worker",
    ):
        if expected not in present_services:
            quality["missingSources"].append(expected)
    if interval_values:
        median = sorted(interval_values)[len(interval_values) // 2]
        quality["samplingGaps"] = [value for value in interval_values if value > median * 3]

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "run": {
            "runId": run_id,
            "startedAt": min(timestamps) if timestamps else None,
            "endedAt": max(timestamps) if timestamps else None,
            "intervalMs": (
                sorted(interval_values)[len(interval_values) // 2]
                if interval_values
                else None
            ),
            "sampleCount": sample_count,
            "droppedRecordCount": dropped,
            "missingDataSources": quality["missingSources"],
        },
        "host": {**host.summary(), **host_details},
        "containers": {key: value.summary() for key, value in containers.items()},
        "services": {
            key: {"service": service_names[key], **value.summary()}
            for key, value in services.items()
        },
        "stages": finished,
        "jobTrend": _job_trend(finished),
        "dataQuality": quality,
    }
    (run_dir / "summary.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (run_dir / "report.md").write_text(_report(result), encoding="utf-8")
    return result


class _Series:
    def __init__(self) -> None:
        self.count = 0
        self.baseline: int | None = None
        self.peak: int | None = None
        self.end: int | None = None
        self.peak_timestamp: str | None = None
        self.heap_peak: int | None = None
        self.direct_peak: int | None = None
        self.python_peak: int | None = None
        self.gc_baseline: int | None = None
        self.gc_end: int | None = None
        self.thread_peak: int | None = None

    def add(self, record: dict[str, Any], primary: str) -> None:
        self.count += 1
        value = _optional_int(record.get(primary))
        if self.baseline is None:
            self.baseline = value
        self.end = value
        if value is not None and (self.peak is None or value > self.peak):
            self.peak = value
            self.peak_timestamp = record.get("timestampUtc")
        self.heap_peak = _max_optional(self.heap_peak, record.get("heapUsedBytes"))
        self.direct_peak = _max_optional(
            self.direct_peak, record.get("directBufferUsedBytes")
        )
        self.python_peak = _max_optional(
            self.python_peak, record.get("pythonAllocatedBytes")
        )
        self.thread_peak = _max_optional(self.thread_peak, record.get("threadCount"))
        gc = _optional_int(record.get("gcCount"))
        if self.gc_baseline is None:
            self.gc_baseline = gc
        self.gc_end = gc

    def summary(self) -> dict[str, Any]:
        return {
            "sampleCount": self.count,
            "baselineBytes": self.baseline,
            "peakBytes": self.peak,
            "endBytes": self.end,
            "deltaBytes": _difference(self.end, self.baseline),
            "peakTimestamp": self.peak_timestamp,
            "heapPeakBytes": self.heap_peak,
            "directMemoryPeakBytes": self.direct_peak,
            "pythonAllocatedPeakBytes": self.python_peak,
            "gcCountDelta": _difference(self.gc_end, self.gc_baseline),
            "threadPeak": self.thread_peak,
        }


def _stage_summary(
    started: dict[str, Any] | None,
    ended: dict[str, Any],
) -> dict[str, Any]:
    service = str(ended.get("service") or "unknown")
    start_at = None if started is None else started.get("timestampUtc")
    end_at = ended.get("timestampUtc")
    return {
        "service": service,
        "instanceId": ended.get("instanceId"),
        "stage": ended.get("stage"),
        "stageExecutionId": ended.get("stageExecutionId"),
        "parentStageExecutionId": ended.get("parentStageExecutionId"),
        "jobId": ended.get("jobId"),
        "repositoryId": ended.get("repositoryId"),
        "revision": ended.get("revision"),
        "unitId": ended.get("unitId"),
        "status": ended.get("eventType"),
        "startedAt": start_at,
        "endedAt": end_at,
        "durationMs": ended.get("durationMs"),
        "startRssBytes": None,
        "peakRssBytes": None,
        "endRssBytes": None,
        "peakDeltaBytes": None,
        "shortTermDropBytes": None,
        "attributes": ended.get("attributes") or {},
    }


def _add_stage_sample(stage: dict[str, Any], record: dict[str, Any]) -> None:
    timestamp = record.get("timestampUtc")
    if not isinstance(timestamp, str):
        return
    if stage["startedAt"] and timestamp < stage["startedAt"]:
        return
    if stage["endedAt"] and timestamp > stage["endedAt"]:
        return
    rss = _optional_int(record.get("rssBytes"))
    if rss is None:
        return
    if stage["startRssBytes"] is None:
        stage["startRssBytes"] = rss
    stage["peakRssBytes"] = _max_optional(stage["peakRssBytes"], rss)
    stage["endRssBytes"] = rss


def _job_trend(stages: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "jobId": item.get("jobId"),
            "startedAt": item.get("startedAt"),
            "endedAt": item.get("endedAt"),
            "baselineBytes": item.get("startRssBytes"),
            "endBytes": item.get("endRssBytes"),
        }
        for item in stages
        if item.get("stage") in {"JOB", "UNIT_EXECUTION"}
    ]


def _report(result: dict[str, Any]) -> str:
    run = result["run"]
    lines = [
        f"# Runtime memory profile: {run['runId']}",
        "",
        "This report describes measurements, not a memory-leak diagnosis.",
        "",
        "## Run",
        "",
        f"- Start: {run['startedAt']}",
        f"- End: {run['endedAt']}",
        f"- Samples: {run['sampleCount']}",
        f"- Dropped records: {run['droppedRecordCount']}",
        "",
        "## Services",
        "",
        "| Service | Baseline | Peak | End | Delta |",
        "|---|---:|---:|---:|---:|",
    ]
    for service, summary in result["services"].items():
        lines.append(
            f"| {service} | {_bytes(summary['baselineBytes'])} | "
            f"{_bytes(summary['peakBytes'])} | {_bytes(summary['endBytes'])} | "
            f"{_bytes(summary['deltaBytes'])} |"
        )
    lines.extend(
        [
            "",
            "## Stage observations",
            "",
            "| Service | Stage | Status | Duration ms | Peak delta | End drop |",
            "|---|---|---|---:|---:|---:|",
        ]
    )
    for stage in result["stages"]:
        lines.append(
            f"| {stage['service']} | {stage['stage']} | {stage['status']} | "
            f"{stage['durationMs']} | {_bytes(stage['peakDeltaBytes'])} | "
            f"{_bytes(stage['shortTermDropBytes'])} |"
        )
    lines.extend(
        [
            "",
            (
                "A single task that does not return to its starting RSS is not proof "
                "of a leak. Repeat comparable jobs and use JFR/heap dumps only when "
                "the trend warrants it."
            ),
            "",
        ]
    )
    return "\n".join(lines)


def _safe_run_dir(output: Path, run_id: str) -> Path:
    root = output.expanduser().resolve()
    target = (root / run_id).resolve()
    if root not in target.parents:
        raise ValueError("run directory escapes output root")
    return target


def _write_json(target: Any, record: dict[str, Any]) -> None:
    target.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
    target.flush()


def _optional_int(value: Any) -> int | None:
    try:
        return None if value is None else int(value)
    except (TypeError, ValueError):
        return None


def _max_optional(current: int | None, value: Any) -> int | None:
    parsed = _optional_int(value)
    if parsed is None:
        return current
    return parsed if current is None else max(current, parsed)


def _difference(left: int | None, right: int | None) -> int | None:
    return None if left is None or right is None else left - right


def _bytes(value: int | None) -> str:
    if value is None:
        return "n/a"
    return f"{value / (1024 * 1024):.1f} MiB"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    collect_parser = subcommands.add_parser("collect")
    collect_parser.add_argument("--run-id", required=True)
    collect_parser.add_argument("--output", type=Path, default=Path(".data/memory-profile"))
    collect_parser.add_argument("--interval-ms", type=int, default=1000)
    collect_parser.add_argument("--duration-seconds", type=float)
    summarize_parser = subcommands.add_parser("summarize")
    summarize_parser.add_argument("--run-id", required=True)
    summarize_parser.add_argument(
        "--output", type=Path, default=Path(".data/memory-profile")
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "collect":
            collect(args.run_id, args.output, args.interval_ms, args.duration_seconds)
        else:
            summarize(args.run_id, args.output)
        return 0
    except (OSError, ValueError) as exc:
        print(f"[memory-profile] {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
