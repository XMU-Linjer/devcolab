import asyncio
import sys

import asyncpg  # type: ignore[import-untyped]

from app.config import get_settings


async def check() -> int:
    settings = get_settings()
    try:
        connection = await asyncpg.connect(settings.agent_database_url, timeout=5)
        try:
            healthy = await connection.fetchval(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM agent_service.worker_heartbeats
                    WHERE heartbeat_at >= now() - interval '90 seconds'
                )
                """
            )
            return 0 if healthy else 1
        finally:
            await connection.close()
    except Exception:
        return 1


if __name__ == "__main__":
    sys.exit(asyncio.run(check()))
