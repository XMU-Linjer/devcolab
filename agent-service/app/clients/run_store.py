import json
from typing import Protocol

from redis.asyncio import Redis


class RunStoreError(RuntimeError):
    pass


class RunStore(Protocol):
    async def save(self, run_id: str, payload: dict[str, object], ttl: int) -> None: ...

    async def get(self, run_id: str) -> dict[str, object] | None: ...

    async def save_job(self, job_id: str, payload: dict[str, object], ttl: int) -> None: ...

    async def get_job(self, job_id: str) -> dict[str, object] | None: ...


class RedisRunStore:
    def __init__(self, redis_url: str) -> None:
        self._redis = Redis.from_url(redis_url, decode_responses=True)

    async def save(self, run_id: str, payload: dict[str, object], ttl: int) -> None:
        try:
            await self._redis.set(
                f"agent:run:{run_id}",
                json.dumps(payload, ensure_ascii=False),
                ex=ttl,
            )
        except Exception as exc:
            raise RunStoreError("Redis is unavailable") from exc

    async def get(self, run_id: str) -> dict[str, object] | None:
        try:
            value = await self._redis.get(f"agent:run:{run_id}")
        except Exception as exc:
            raise RunStoreError("Redis is unavailable") from exc
        return json.loads(value) if value else None

    async def save_job(self, job_id: str, payload: dict[str, object], ttl: int) -> None:
        try:
            await self._redis.set(
                f"agent:job:{job_id}",
                json.dumps(payload, ensure_ascii=False),
                ex=ttl,
            )
        except Exception as exc:
            raise RunStoreError("Redis is unavailable") from exc

    async def get_job(self, job_id: str) -> dict[str, object] | None:
        try:
            value = await self._redis.get(f"agent:job:{job_id}")
        except Exception as exc:
            raise RunStoreError("Redis is unavailable") from exc
        return json.loads(value) if value else None

    async def ping(self) -> bool:
        try:
            return bool(await self._redis.ping())
        except Exception:
            return False
