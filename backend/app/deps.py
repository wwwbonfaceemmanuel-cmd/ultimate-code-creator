"""FastAPI dependencies (overridable in tests)."""
from fastapi import Depends

from .config import Settings, get_settings
from .services.ai import get_ai_client
from .services.store import get_store


def settings_dep() -> Settings:
    return get_settings()


def store_dep(settings: Settings = Depends(settings_dep)):
    return get_store(settings)


def ai_dep(settings: Settings = Depends(settings_dep)):
    return get_ai_client(settings)
