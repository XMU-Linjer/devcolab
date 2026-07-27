from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.agent_runs import router
from app.clients.mcp_client import OfficialMcpClient
from app.clients.run_store import RedisRunStore
from app.config import Settings, get_settings
from app.providers.base import ModelProvider
from app.providers.deepseek import DeepSeekProvider
from app.runtime.executor import AgentRunExecutor


def create_app(
    settings: Settings | None = None,
    mcp_client: object | None = None,
    run_store: object | None = None,
    model_provider: ModelProvider | None = None,
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
        app.state.model_provider = model_provider or DeepSeekProvider(
            api_key=configured.deepseek_api_key,
            base_url=configured.deepseek_base_url,
            model=configured.deepseek_model,
            connect_timeout_seconds=configured.agent_model_connect_timeout_seconds,
            total_timeout_seconds=configured.agent_model_total_timeout_seconds,
        )
        app.state.run_executor = AgentRunExecutor(
            app.state.mcp_client,
            app.state.model_provider,
            app.state.run_store,
            configured,
        )
        yield
        await app.state.run_executor.close()

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
