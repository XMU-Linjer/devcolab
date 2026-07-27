from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_prefix="", extra="ignore", case_sensitive=False
    )

    deepseek_api_key: str = ""
    deepseek_base_url: str = ""
    deepseek_model: str = ""
    mcp_base_url: str = "http://localhost:8091/mcp"
    redis_url: str = "redis://localhost:6379/0"
    agent_max_selected_files: int = Field(6, ge=1, le=20)
    agent_max_code_chars: int = Field(40_000, ge=1)
    agent_max_bound_documents: int = Field(5, ge=1, le=20)
    agent_max_candidate_documents: int = Field(5, ge=1, le=20)
    agent_max_document_structures: int = Field(3, ge=1, le=20)
    agent_max_tool_calls: int = Field(12, ge=5, le=100)
    agent_run_ttl_seconds: int = Field(86_400, ge=60)
    agent_request_timeout_seconds: float = Field(30, gt=0, le=120)
    agent_model_connect_timeout_seconds: float = Field(10, gt=0, le=60)
    agent_model_total_timeout_seconds: float = Field(60, gt=0, le=180)
    agent_model_max_input_characters: int = Field(120_000, ge=1)
    agent_review_max_operations: int = Field(50, ge=1, le=50)
    agent_review_max_evidence: int = Field(50, ge=1, le=50)


@lru_cache
def get_settings() -> Settings:
    return Settings()
