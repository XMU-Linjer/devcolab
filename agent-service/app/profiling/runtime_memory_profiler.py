from __future__ import annotations

import atexit
import contextvars
import json
import logging
import os
import queue
import re
import threading
import time
import tracemalloc
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

import psutil  # type: ignore[import-untyped]

LOGGER = logging.getLogger("devcollab.memory_profile")
SCHEMA_VERSION = 1
SAFE_RUN_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
SAFE_STAGE = re.compile(r"^[A-Z][A-Z0-9_]{0,63}$")
SAFE_ATTRIBUTE = re.compile(r"^[A-Za-z][A-Za-z0-9_]{0,63}$")
MAX_ATTRIBUTES = 24
MAX_ATTRIBUTE_STRING = 256
_PARENT_STAGE: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "memory_profile_parent_stage", default=None
)


@dataclass(frozen=True)
class MemoryProfileConfig:
    enabled: bool = False
    run_id: str = ""
    output_dir: Path = Path(".data/memory-profile")
    interval_ms: int = 1000
    queue_capacity: int = 1024

    def validated(self) -> MemoryProfileConfig:
        if not self.enabled:
            return self
        run_id = self.run_id.strip() or datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
        if not SAFE_RUN_ID.fullmatch(run_id):
            raise ValueError("Invalid memory profile runId")
        interval = self.interval_ms if self.interval_ms >= 500 else 1000
        capacity = min(max(self.queue_capacity, 1), 65_536)
        return MemoryProfileConfig(True, run_id, self.output_dir, interval, capacity)


class RuntimeMemoryProfiler:
    def __init__(self, config: MemoryProfileConfig, service: str) -> None:
        self._config = config.validated()
        self._service = _safe_service(service)
        self._pid = os.getpid()
        self._instance_id = f"{self._service}-{self._pid}-{uuid4().hex[:8]}"
        self._queue: queue.Queue[tuple[str, dict[str, Any]] | None] = queue.Queue(
            maxsize=self._config.queue_capacity
        )
        self._active = threading.Event()
        self._stop = threading.Event()
        self._writer_failed = threading.Event()
        self._samples_written = 0
        self._events_written = 0
        self._dropped_records = 0
        self._write_errors = 0
        self._lock = threading.Lock()
        self._sampler: threading.Thread | None = None
        self._writer: threading.Thread | None = None
        self._process: psutil.Process | None = None
        self._sample_file: Path | None = None
        self._event_file: Path | None = None
        if self._config.enabled:
            self._start()
        atexit.register(self.close)

    @property
    def enabled(self) -> bool:
        return self._active.is_set()

    @property
    def stats(self) -> dict[str, int]:
        with self._lock:
            return {
                "samplesWritten": self._samples_written,
                "eventsWritten": self._events_written,
                "droppedRecords": self._dropped_records,
                "writeErrors": self._write_errors,
            }

    @contextmanager
    def stage(
        self,
        name: str,
        *,
        job_id: str | None = None,
        repository_id: str | None = None,
        revision: str | None = None,
        unit_id: str | None = None,
    ) -> Iterator[ProfileStage]:
        if not self.enabled:
            yield ProfileStage.noop()
            return
        if not SAFE_STAGE.fullmatch(name):
            raise ValueError("Invalid memory profile stage")
        stage = ProfileStage(
            profiler=self,
            name=name,
            execution_id=str(uuid4()),
            parent_execution_id=_PARENT_STAGE.get(),
            job_id=job_id,
            repository_id=repository_id,
            revision=revision,
            unit_id=unit_id,
        )
        token = _PARENT_STAGE.set(stage.execution_id)
        self._stage_event(stage, "STARTED")
        try:
            yield stage
        except BaseException as exc:
            self._stage_event(stage, "FAILED", exc)
            raise
        else:
            self._stage_event(stage, "COMPLETED")
        finally:
            _PARENT_STAGE.reset(token)

    def sample(self) -> dict[str, Any]:
        record = self._common("sample")
        rss: int | None = None
        vms: int | None = None
        cpu: float | None = None
        threads: int | None = None
        open_files: int | None = None
        uptime: int | None = None
        process = self._process
        if process is not None:
            try:
                memory = process.memory_info()
                rss, vms = memory.rss, memory.vms
                cpu = process.cpu_percent(interval=None)
                threads = process.num_threads()
                open_files = len(process.open_files())
                uptime = max(0, int((time.time() - process.create_time()) * 1000))
            except (psutil.Error, OSError):
                pass
        allocated: int | None = None
        peak: int | None = None
        if tracemalloc.is_tracing():
            allocated, peak = tracemalloc.get_traced_memory()
        record.update(
            rssBytes=rss,
            virtualMemoryBytes=vms,
            cpuPercent=cpu,
            threadCount=threads,
            openFileDescriptorCount=open_files,
            heapUsedBytes=None,
            heapCommittedBytes=None,
            heapMaxBytes=None,
            nonHeapUsedBytes=None,
            metaspaceUsedBytes=None,
            directBufferUsedBytes=None,
            directBufferCapacityBytes=None,
            directBufferCount=None,
            gcCount=None,
            gcTimeMs=None,
            pythonAllocatedBytes=allocated,
            pythonPeakAllocatedBytes=peak,
            processUptimeMs=uptime,
            **self.stats,
            profilingOverheadWarning=self._dropped_records > 0,
        )
        return record

    def close(self) -> None:
        if not self._active.is_set() and self._sampler is None:
            return
        self._active.clear()
        self._stop.set()
        if self._sampler is not None:
            self._sampler.join(timeout=2)
        try:
            self._queue.put_nowait(None)
        except queue.Full:
            with self._lock:
                self._dropped_records += 1
        if self._writer is not None:
            self._writer.join(timeout=2)
        self._sampler = None
        self._writer = None
        if tracemalloc.is_tracing():
            tracemalloc.stop()

    def _start(self) -> None:
        run_root = self._config.output_dir.expanduser().resolve()
        run_dir = (run_root / self._config.run_id).resolve()
        if run_root not in run_dir.parents:
            raise ValueError("Memory profile run directory escapes output root")
        try:
            run_dir.mkdir(parents=True, exist_ok=True)
            self._sample_file = run_dir / f"{self._service}-{self._pid}-samples.jsonl"
            self._event_file = run_dir / f"{self._service}-{self._pid}-events.jsonl"
            self._process = psutil.Process(self._pid)
            self._process.cpu_percent(interval=None)
            tracemalloc.start(1)
            self._active.set()
            self._writer = threading.Thread(
                target=self._writer_loop,
                name=f"{self._service}-memory-profile-writer",
                daemon=True,
            )
            self._sampler = threading.Thread(
                target=self._sampler_loop,
                name=f"{self._service}-memory-profile-sampler",
                daemon=True,
            )
            self._writer.start()
            self._sampler.start()
        except (OSError, psutil.Error) as exc:
            self._active.clear()
            LOGGER.warning(
                "Runtime memory profiling disabled because output initialization failed: %s",
                type(exc).__name__,
            )

    def _sampler_loop(self) -> None:
        while self._active.is_set() and not self._stop.is_set():
            try:
                self._enqueue("sample", self.sample())
            except Exception as exc:
                LOGGER.warning("Runtime memory sampling disabled: %s", type(exc).__name__)
                self._active.clear()
                return
            self._stop.wait(self._config.interval_ms / 1000)

    def _writer_loop(self) -> None:
        assert self._sample_file is not None and self._event_file is not None
        try:
            with (
                self._sample_file.open("a", encoding="utf-8", buffering=64 * 1024) as samples,
                self._event_file.open("a", encoding="utf-8", buffering=64 * 1024) as events,
            ):
                while self._active.is_set() or not self._queue.empty():
                    try:
                        item = self._queue.get(timeout=0.25)
                    except queue.Empty:
                        continue
                    if item is None:
                        break
                    kind, record = item
                    target = events if kind == "stage_event" else samples
                    target.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
                    target.write("\n")
                    with self._lock:
                        if kind == "stage_event":
                            self._events_written += 1
                        else:
                            self._samples_written += 1
                samples.flush()
                events.flush()
        except (OSError, TypeError, ValueError) as exc:
            with self._lock:
                self._write_errors += 1
            self._writer_failed.set()
            self._active.clear()
            _drain(self._queue)
            LOGGER.warning(
                "Runtime memory profiling writer disabled: %s", type(exc).__name__
            )

    def _enqueue(self, kind: str, record: dict[str, Any]) -> None:
        if not self.enabled or self._writer_failed.is_set():
            return
        try:
            self._queue.put_nowait((kind, record))
        except queue.Full:
            with self._lock:
                self._dropped_records += 1

    def _common(self, record_type: str) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "recordType": record_type,
            "timestampUtc": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "monotonicNanos": time.monotonic_ns(),
            "runId": self._config.run_id,
            "service": self._service,
            "instanceId": self._instance_id,
            "pid": self._pid,
        }

    def _stage_event(
        self,
        stage: ProfileStage,
        event_type: str,
        error: BaseException | None = None,
    ) -> None:
        record = self._common("stage_event")
        duration = (
            None
            if event_type == "STARTED"
            else max(0, (time.monotonic_ns() - stage.started_nanos) // 1_000_000)
        )
        record.update(
            eventType=event_type,
            stage=stage.name,
            stageExecutionId=stage.execution_id,
            parentStageExecutionId=stage.parent_execution_id,
            jobId=stage.job_id,
            repositoryId=stage.repository_id,
            revision=stage.revision,
            unitId=stage.unit_id,
            durationMs=duration,
            status=event_type,
            errorType=type(error).__name__ if error is not None else None,
            attributes=dict(stage.attributes),
        )
        self._enqueue("stage_event", record)


@dataclass
class ProfileStage:
    profiler: RuntimeMemoryProfiler | None
    name: str
    execution_id: str
    parent_execution_id: str | None
    job_id: str | None
    repository_id: str | None
    revision: str | None
    unit_id: str | None
    started_nanos: int = field(default_factory=time.monotonic_ns)
    attributes: dict[str, str | int | float | bool | None] = field(default_factory=dict)

    @classmethod
    def noop(cls) -> ProfileStage:
        return cls(None, "", "", None, None, None, None, None)

    def attribute(self, key: str, value: object) -> ProfileStage:
        if self.profiler is None:
            return self
        if not SAFE_ATTRIBUTE.fullmatch(key):
            raise ValueError("Invalid memory profile attribute key")
        if len(self.attributes) >= MAX_ATTRIBUTES and key not in self.attributes:
            raise ValueError("Memory profile attribute limit exceeded")
        if value is not None and not isinstance(value, str | int | float | bool):
            raise ValueError("Memory profile attributes must be scalar")
        if isinstance(value, str) and len(value) > MAX_ATTRIBUTE_STRING:
            raise ValueError("Memory profile attribute is too large")
        self.attributes[key] = value
        return self


def _safe_service(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "-", value.strip())[:64]
    return cleaned or "service"


def _drain(items: queue.Queue[Any]) -> None:
    while True:
        try:
            items.get_nowait()
        except queue.Empty:
            return
