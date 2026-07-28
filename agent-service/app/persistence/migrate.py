import asyncio
from pathlib import Path

import asyncpg  # type: ignore[import-untyped]

from app.config import get_settings


async def migrate() -> None:
    settings = get_settings()
    connection = await asyncpg.connect(settings.agent_database_url)
    try:
        await connection.execute("CREATE SCHEMA IF NOT EXISTS agent_service")
        await connection.execute(
            """
            CREATE TABLE IF NOT EXISTS agent_service.schema_migrations (
                version VARCHAR(100) PRIMARY KEY,
                applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """
        )
        for migration in migration_files():
            applied = await connection.fetchval(
                "SELECT 1 FROM agent_service.schema_migrations WHERE version = $1",
                migration.name,
            )
            if applied:
                continue
            async with connection.transaction():
                await connection.execute(migration.read_text(encoding="utf-8"))
                await connection.execute(
                    """
                    INSERT INTO agent_service.schema_migrations(version)
                    VALUES ($1)
                    """,
                    migration.name,
                )
    finally:
        await connection.close()


def migration_files() -> list[Path]:
    root = Path(__file__).resolve().parents[2] / "migrations"
    return sorted(root.glob("V*.sql"))


if __name__ == "__main__":
    asyncio.run(migrate())
