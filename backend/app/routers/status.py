from fastapi import APIRouter, Depends

from ..config import Settings
from ..deps import settings_dep, store_dep
from ..languages import LANGUAGES
from ..services.store import StoreError

router = APIRouter(prefix="/api", tags=["status"])


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.get("/status")
async def status(settings: Settings = Depends(settings_dep), store=Depends(store_dep)):
    """Connection state for the UI. Never returns secrets."""
    storage_error = None
    try:
        await store.check()
    except (StoreError, OSError) as exc:
        storage_error = str(exc)
    return {
        "ai": {
            "configured": settings.ai_configured,
            "model": settings.ai_model if settings.ai_configured else None,
            "missing": [
                name
                for name, value in (("AI_API_KEY", settings.ai_api_key), ("AI_MODEL", settings.ai_model), ("AI_BASE_URL", settings.ai_base_url))
                if not value.strip()
            ],
        },
        # The anon key is designed to be public: it only lets the browser reach
        # Supabase sign-in. The service-role key is never returned.
        "auth": {
            "enabled": settings.auth_enabled,
            "required": settings.require_auth,
            "supabase_url": settings.supabase_url.strip() if settings.auth_enabled else None,
            "supabase_anon_key": settings.supabase_anon_key.strip() if settings.auth_enabled else None,
        },
        "limits": {"per_hour": settings.ai_limit_per_hour, "per_day": settings.ai_limit_per_day},
        "storage": {"kind": store.kind, "ok": storage_error is None, "error": storage_error},
        "languages": [{"id": k, "label": v} for k, v in LANGUAGES.items()],
    }
