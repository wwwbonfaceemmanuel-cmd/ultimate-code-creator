"""Application settings, loaded from environment variables / backend/.env."""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    ai_api_key: str = ""
    ai_base_url: str = "https://api.openai.com/v1"
    ai_model: str = ""
    ai_timeout_seconds: float = 180
    ai_max_output_tokens: int = 8000
    ai_json_mode: bool = True

    supabase_url: str = ""
    supabase_service_role_key: str = ""
    supabase_anon_key: str = ""

    # When true, the API refuses to serve data unless sign-in is configured.
    require_auth: bool = False

    # AI requests allowed per user. 0 turns a limit off.
    ai_limit_per_hour: int = 20
    ai_limit_per_day: int = 100

    local_data_dir: str = "./data"
    cors_origins: str = "http://localhost:5173"
    frontend_dist: str = "../frontend/dist"

    @property
    def ai_configured(self) -> bool:
        return bool(self.ai_api_key.strip() and self.ai_model.strip() and self.ai_base_url.strip())

    @property
    def supabase_configured(self) -> bool:
        return bool(self.supabase_url.strip() and self.supabase_service_role_key.strip())

    @property
    def auth_enabled(self) -> bool:
        return bool(self.supabase_url.strip() and self.supabase_anon_key.strip())

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
