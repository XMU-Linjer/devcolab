from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.agent_runs import router
from app.clients.mcp_client import OfficialMcpClient
from app.clients.run_store import RedisRunStore
from app.config import Settings, get_settings


def create_app(
    settings: Settings | None = None,
    mcp_client: object | None = None,
    run_store: object | None = None,
) -> FastAPI:
    configured = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.settings = configured
        app.state.mcp_client = mcp_client or OfficialMcpClient(
            configured.mcp_base_url,
            configured.agent_request_timeout_seconds,
        )
        app.state.run_store = run_store or RedisRunStore(configured.redis_url)
        yield

    app = FastAPI(
        title="DevCollab Agent Service",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(router)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP", "mode": "context-only"}

    return app


app = create_app()
