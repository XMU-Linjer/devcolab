from __future__ import annotations

import json
from collections.abc import Mapping
from datetime import datetime
from typing import Any, Protocol
from uuid import UUID

import asyncpg  # type: ignore[import-untyped]


class AgentJobRepository(Protocol):
    async def create_job(self, job: Mapping[str, Any], unit: Mapping[str, Any]) -> None: ...

    async def get_job(self, job_id: UUID) -> dict[str, Any] | None: ...

    async def claim_next_unit(
        self, worker_id: str, lease_seconds: int
    ) -> dict[str, Any] | None: ...

    async def heartbeat(self, unit_id: UUID, worker_id: str, lease_seconds: int) -> bool: ...

    async def update_phase(self, unit_id: UUID, worker_id: str, phase: str) -> bool: ...

    async def complete_unit(
        self,
        unit_id: UUID,
        worker_id: str,
        result: str,
        review_request_id: UUID | None,
    ) -> None: ...

    async def fail_unit(
        self,
        unit_id: UUID,
        worker_id: str,
        error_code: str,
        error_message: str,
        retry_at: datetime | None,
    ) -> None: ...

    async def record_worker_heartbeat(self, worker_id: str) -> None: ...

    async def close(self) -> None: ...


class PostgresAgentJobRepository:
    def __init__(
        self,
        database_url: str,
        pool: asyncpg.Pool | None = None,
    ) -> None:
        self._database_url = database_url
        self._pool = pool

    @classmethod
    async def connect(cls, database_url: str) -> PostgresAgentJobRepository:
        return cls(
            database_url,
            await asyncpg.create_pool(database_url, min_size=1, max_size=10),
        )

    async def close(self) -> None:
        if self._pool is not None:
            await self._pool.close()

    async def _database(self) -> asyncpg.Pool:
        if self._pool is None:
            self._pool = await asyncpg.create_pool(
                self._database_url, min_size=1, max_size=10
            )
        return self._pool

    async def create_job(self, job: Mapping[str, Any], unit: Mapping[str, Any]) -> None:
        pool = await self._database()
        async with pool.acquire() as connection, connection.transaction():
            await connection.execute(
                """
                INSERT INTO agent_service.agent_jobs (
                    id, delegation_id, created_by_user_id, workspace_id, repository_id,
                    revision, scope_type, scope_payload, user_instruction, status,
                    total_units, created_at, updated_at
                ) VALUES (
                    $1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, 'QUEUED',
                    1, $10, $10
                )
                """,
                job["id"],
                job["delegation_id"],
                job["created_by_user_id"],
                job["workspace_id"],
                job["repository_id"],
                job["revision"],
                job["scope_type"],
                json.dumps(job["scope_payload"]),
                job.get("user_instruction"),
                job["created_at"],
            )
            await connection.execute(
                """
                INSERT INTO agent_service.agent_units (
                    id, job_id, ordinal, status, max_attempts, created_at, updated_at
                ) VALUES ($1, $2, 1, 'PENDING', $3, $4, $4)
                """,
                unit["id"],
                job["id"],
                unit["max_attempts"],
                job["created_at"],
            )

    async def get_job(self, job_id: UUID) -> dict[str, Any] | None:
        pool = await self._database()
        row = await pool.fetchrow(
            """
            SELECT j.*,
                   u.phase AS current_phase
            FROM agent_service.agent_jobs j
            LEFT JOIN agent_service.agent_units u
              ON u.job_id = j.id AND u.ordinal = 1
            WHERE j.id = $1
            """,
            job_id,
        )
        return self._decode_job(row) if row else None

    async def claim_next_unit(
        self, worker_id: str, lease_seconds: int
    ) -> dict[str, Any] | None:
        pool = await self._database()
        async with pool.acquire() as connection, connection.transaction():
            exhausted = await connection.fetch(
                """
                UPDATE agent_service.agent_units
                SET status = 'FAILED',
                    lease_expires_at = NULL,
                    error_code = 'WORKER_LEASE_EXPIRED',
                    error_message = 'Worker lease expired after maximum attempts',
                    completed_at = now(),
                    updated_at = now()
                WHERE status IN ('CLAIMED', 'RUNNING')
                  AND lease_expires_at <= now()
                  AND attempt >= max_attempts
                RETURNING job_id
                """
            )
            exhausted_job_ids = list({row["job_id"] for row in exhausted})
            if exhausted_job_ids:
                await connection.execute(
                    """
                    UPDATE agent_service.agent_jobs
                    SET status = 'FAILED',
                        failed_units = 1,
                        error_code = 'WORKER_LEASE_EXPIRED',
                        error_message = 'Worker lease expired after maximum attempts',
                        completed_at = now(),
                        updated_at = now(),
                        version = version + 1
                    WHERE id = ANY($1::uuid[])
                      AND status <> 'CANCELLED'
                    """,
                    exhausted_job_ids,
                )
            row = await connection.fetchrow(
                """
                WITH candidate AS (
                    SELECT u.id
                    FROM agent_service.agent_units u
                    JOIN agent_service.agent_jobs j ON j.id = u.job_id
                    WHERE j.status <> 'CANCELLED'
                      AND u.attempt < u.max_attempts
                      AND (
                        (u.status IN ('PENDING', 'RETRY_WAITING')
                          AND (u.next_attempt_at IS NULL OR u.next_attempt_at <= now()))
                        OR
                        (u.status IN ('CLAIMED', 'RUNNING')
                          AND u.lease_expires_at <= now())
                      )
                    ORDER BY COALESCE(u.next_attempt_at, u.created_at), u.created_at
                    FOR UPDATE OF u SKIP LOCKED
                    LIMIT 1
                )
                UPDATE agent_service.agent_units u
                SET status = 'CLAIMED',
                    phase = 'LOADING_CONTEXT',
                    worker_id = $1,
                    attempt = u.attempt + 1,
                    lease_expires_at = now() + ($2 * interval '1 second'),
                    heartbeat_at = now(),
                    next_attempt_at = NULL,
                    started_at = COALESCE(u.started_at, now()),
                    updated_at = now(),
                    error_code = NULL,
                    error_message = NULL
                FROM candidate
                WHERE u.id = candidate.id
                RETURNING u.*
                """,
                worker_id,
                lease_seconds,
            )
            if row is None:
                return None
            await connection.execute(
                """
                UPDATE agent_service.agent_jobs
                SET status = 'RUNNING',
                    started_at = COALESCE(started_at, now()),
                    updated_at = now(),
                    version = version + 1
                WHERE id = $1 AND status <> 'CANCELLED'
                """,
                row["job_id"],
            )
            job = await connection.fetchrow(
                "SELECT * FROM agent_service.agent_jobs WHERE id = $1",
                row["job_id"],
            )
            return {**dict(row), "job": self._decode_job(job)}

    @staticmethod
    def _decode_job(row: asyncpg.Record) -> dict[str, Any]:
        job = dict(row)
        for field, fallback in (
            ("scope_payload", {}),
            ("review_request_ids", []),
        ):
            value = job.get(field)
            if isinstance(value, str):
                job[field] = json.loads(value)
            elif value is None:
                job[field] = fallback
        return job

    async def heartbeat(self, unit_id: UUID, worker_id: str, lease_seconds: int) -> bool:
        pool = await self._database()
        result = await pool.execute(
            """
            UPDATE agent_service.agent_units
            SET heartbeat_at = now(),
                lease_expires_at = now() + ($3 * interval '1 second'),
                updated_at = now(),
                status = CASE WHEN status = 'CLAIMED' THEN 'RUNNING' ELSE status END
            WHERE id = $1 AND worker_id = $2 AND status IN ('CLAIMED', 'RUNNING')
            """,
            unit_id,
            worker_id,
            lease_seconds,
        )
        return bool(result.endswith(" 1"))

    async def update_phase(self, unit_id: UUID, worker_id: str, phase: str) -> bool:
        pool = await self._database()
        result = await pool.execute(
            """
            UPDATE agent_service.agent_units
            SET phase = $3, status = 'RUNNING', updated_at = now()
            WHERE id = $1 AND worker_id = $2 AND status IN ('CLAIMED', 'RUNNING')
            """,
            unit_id,
            worker_id,
            phase,
        )
        return bool(result.endswith(" 1"))

    async def complete_unit(
        self,
        unit_id: UUID,
        worker_id: str,
        result: str,
        review_request_id: UUID | None,
    ) -> None:
        pool = await self._database()
        async with pool.acquire() as connection, connection.transaction():
            row = await connection.fetchrow(
                """
                UPDATE agent_service.agent_units
                SET status = 'COMPLETED', result = $3, review_request_id = $4,
                    lease_expires_at = NULL, completed_at = now(), updated_at = now()
                WHERE id = $1 AND worker_id = $2 AND status IN ('CLAIMED', 'RUNNING')
                RETURNING job_id
                """,
                unit_id,
                worker_id,
                result,
                review_request_id,
            )
            if row is None:
                raise RuntimeError("Agent unit lease was lost")
            review_ids = [] if review_request_id is None else [str(review_request_id)]
            await connection.execute(
                """
                UPDATE agent_service.agent_jobs
                SET status = 'COMPLETED', result = $2, completed_units = 1,
                    failed_units = 0, review_request_ids = $3::jsonb,
                    completed_at = now(), updated_at = now(), version = version + 1
                WHERE id = $1
                """,
                row["job_id"],
                result,
                json.dumps(review_ids),
            )

    async def fail_unit(
        self,
        unit_id: UUID,
        worker_id: str,
        error_code: str,
        error_message: str,
        retry_at: datetime | None,
    ) -> None:
        pool = await self._database()
        async with pool.acquire() as connection, connection.transaction():
            status = "RETRY_WAITING" if retry_at else "FAILED"
            row = await connection.fetchrow(
                """
                UPDATE agent_service.agent_units
                SET status = $3::varchar, next_attempt_at = $4, lease_expires_at = NULL,
                    error_code = $5, error_message = $6, updated_at = now(),
                    completed_at = CASE
                        WHEN $3::varchar = 'FAILED' THEN now()
                        ELSE NULL
                    END
                WHERE id = $1 AND worker_id = $2
                RETURNING job_id
                """,
                unit_id,
                worker_id,
                status,
                retry_at,
                error_code,
                error_message[:500],
            )
            if row is None:
                return
            if retry_at is None:
                await connection.execute(
                    """
                    UPDATE agent_service.agent_jobs
                    SET status = 'FAILED', failed_units = 1, error_code = $2,
                        error_message = $3, completed_at = now(), updated_at = now(),
                        version = version + 1
                    WHERE id = $1
                    """,
                    row["job_id"],
                    error_code,
                    error_message[:500],
                )
            else:
                await connection.execute(
                    """
                    UPDATE agent_service.agent_jobs
                    SET status = 'QUEUED', error_code = $2, error_message = $3,
                        updated_at = now(), version = version + 1
                    WHERE id = $1
                    """,
                    row["job_id"],
                    error_code,
                    error_message[:500],
                )

    async def record_worker_heartbeat(self, worker_id: str) -> None:
        pool = await self._database()
        await pool.execute(
            """
            INSERT INTO agent_service.worker_heartbeats(worker_id, heartbeat_at, started_at)
            VALUES ($1, now(), now())
            ON CONFLICT (worker_id) DO UPDATE SET heartbeat_at = excluded.heartbeat_at
            """,
            worker_id,
        )
