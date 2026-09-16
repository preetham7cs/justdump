from functools import lru_cache
from pathlib import Path

from pydantic import AnyHttpUrl, UUID4
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Non-privileged settings used by the local FastAPI process."""

    model_config = SettingsConfigDict(
        env_file=Path(__file__).parents[1] / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    justdump_supabase_url: AnyHttpUrl
    justdump_supabase_publishable_key: str
    justdump_owner_id: UUID4

    @property
    def auth_issuer(self) -> str:
        return f"{str(self.justdump_supabase_url).rstrip('/')}/auth/v1"

    @property
    def jwks_url(self) -> str:
        return f"{self.auth_issuer}/.well-known/jwks.json"

    @property
    def rest_url(self) -> str:
        return f"{str(self.justdump_supabase_url).rstrip('/')}/rest/v1"


@lru_cache
def get_settings() -> Settings:
    return Settings()
