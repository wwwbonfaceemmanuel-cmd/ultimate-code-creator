"""Per-user limits on AI requests, so one account cannot run up the AI bill."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import httpx

from ..config import Settings
from .auth import User
from .store import StoreError, owner_filter


class LimitExceeded(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class MemoryUsage:
    """Counts in process memory. Resets on restart; used when Supabase is not configured."""

    kind = "memory"

    def __init__(self):
        self._events: dict[str, list[datetime]] = {}

    async def recent(self, user: User) -> list[datetime]:
        cutoff = _utcnow() - timedelta(days=1)
        events = [t for t in self._events.get(user.key, []) if t > cutoff]
        self._events[user.key] = events
        return events

    async def record(self, user: User, kind: str) -> None:
        self._events.setdefault(user.key, []).append(_utcnow())


class SupabaseUsage:
    """Stores one row per AI request in the ``ai_usage`` table."""

    kind = "supabase"

    def __init__(self, url: str, service_key: str, transport: httpx.AsyncBaseTransport | None = None):
        self._base = url.rstrip("/") + "/rest/v1/ai_usage"
        self._headers = {"apikey": service_key, "Authorization": f"Bearer {service_key}"}
        self._transport = transport

    async def _request(self, method: str, **kwargs):
        try:
            async with httpx.AsyncClient(timeout=15, transport=self._transport) as client:
                resp = await client.request(method, self._base, headers=self._headers, **kwargs)
        except httpx.HTTPError as exc:
            raise StoreError(f"Could not reach Supabase ({type(exc).__name__}).")
        if resp.status_code >= 400:
            hint = ""
            if resp.status_code == 404:
                hint = " Has the latest supabase/schema.sql been run?"
            elif resp.status_code == 403:
                hint = " The server key has no access to the table: run the latest supabase/schema.sql, and check SUPABASE_SERVICE_ROLE_KEY is the secret key."
            raise StoreError(f"Supabase returned HTTP {resp.status_code} for usage tracking.{hint}")
        return resp

    async def recent(self, user: User) -> list[datetime]:
        cutoff = (_utcnow() - timedelta(days=1)).isoformat()
        resp = await self._request(
            "GET",
            params={
                "select": "created_at",
                "user_id": owner_filter(user.id),
                "created_at": f"gte.{cutoff}",
                "order": "created_at.desc",
                "limit": "2000",
            },
        )
        return [datetime.fromisoformat(row["created_at"].replace("Z", "+00:00")) for row in resp.json()]

    async def record(self, user: User, kind: str) -> None:
        await self._request("POST", json={"user_id": user.id, "kind": kind})


def summarize(events: list[datetime], settings: Settings) -> dict:
    hour_ago = _utcnow() - timedelta(hours=1)
    return {
        "hour": {"used": sum(1 for t in events if t > hour_ago), "limit": settings.ai_limit_per_hour},
        "day": {"used": len(events), "limit": settings.ai_limit_per_day},
    }


def _wait_text(oldest: datetime, window: timedelta) -> str:
    minutes = max(1, int(((oldest + window) - _utcnow()).total_seconds() // 60) + 1)
    if minutes >= 90:
        return f"about {round(minutes / 60)} hours"
    return f"about {minutes} minute{'s' if minutes != 1 else ''}"


async def enforce(tracker, settings: Settings, user: User, kind: str) -> None:
    """Raise LimitExceeded if the user is over a limit, otherwise record the request."""
    if settings.ai_limit_per_hour <= 0 and settings.ai_limit_per_day <= 0:
        return
    events = sorted(await tracker.recent(user))
    hour_ago = _utcnow() - timedelta(hours=1)
    in_hour = [t for t in events if t > hour_ago]
    if 0 < settings.ai_limit_per_day <= len(events):
        raise LimitExceeded(
            f"You have used all {settings.ai_limit_per_day} AI requests for today. "
            f"Try again in {_wait_text(events[0], timedelta(days=1))}."
        )
    if 0 < settings.ai_limit_per_hour <= len(in_hour):
        raise LimitExceeded(
            f"You have used all {settings.ai_limit_per_hour} AI requests for this hour. "
            f"Try again in {_wait_text(in_hour[0], timedelta(hours=1))}."
        )
    await tracker.record(user, kind)
