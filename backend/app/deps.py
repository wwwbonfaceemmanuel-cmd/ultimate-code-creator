"""FastAPI dependencies (overridable in tests)."""
from fastapi import Depends, Request

from .config import Settings, get_settings
from .services.ai import get_ai_client
from .services.auth import LOCAL_USER, AuthError, SupabaseVerifier, User
from .services.store import get_store
from .services.usage import MemoryUsage, SupabaseUsage

_verifiers: dict[tuple[str, str], SupabaseVerifier] = {}
_memory_usage = MemoryUsage()


def settings_dep() -> Settings:
    return get_settings()


def store_dep(settings: Settings = Depends(settings_dep)):
    return get_store(settings)


def ai_dep(settings: Settings = Depends(settings_dep)):
    return get_ai_client(settings)


def verifier_dep(settings: Settings = Depends(settings_dep)) -> SupabaseVerifier | None:
    if not settings.auth_enabled:
        return None
    key = (settings.supabase_url, settings.supabase_anon_key)
    if key not in _verifiers:
        _verifiers[key] = SupabaseVerifier(*key)
    return _verifiers[key]


def usage_dep(settings: Settings = Depends(settings_dep)):
    if settings.supabase_configured:
        return SupabaseUsage(settings.supabase_url, settings.supabase_service_role_key)
    return _memory_usage


async def current_user(
    request: Request,
    settings: Settings = Depends(settings_dep),
    verifier: SupabaseVerifier | None = Depends(verifier_dep),
) -> User:
    """The signed-in user, or the single local user when sign-in is switched off."""
    if verifier is None:
        if settings.require_auth:
            raise AuthError(
                503,
                "Sign-in is required on this server but is not set up. "
                "Set SUPABASE_URL and SUPABASE_ANON_KEY on the backend.",
            )
        return LOCAL_USER
    scheme, _, token = request.headers.get("authorization", "").partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise AuthError(401, "Please sign in to continue.")
    return await verifier.verify(token.strip())
