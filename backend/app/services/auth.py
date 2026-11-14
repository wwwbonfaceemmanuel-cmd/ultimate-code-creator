"""Sign-in verification.

The browser signs in with Supabase Auth and sends the resulting access token
as ``Authorization: Bearer <token>``. The backend asks Supabase who the token
belongs to (``GET /auth/v1/user``) and caches the answer briefly.
"""
from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass

import httpx


@dataclass(frozen=True)
class User:
    # None when sign-in is switched off (single-user local mode).
    id: str | None
    email: str | None = None

    @property
    def key(self) -> str:
        return self.id or "local"


LOCAL_USER = User(id=None)


class AuthError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


class SupabaseVerifier:
    def __init__(self, url: str, anon_key: str, transport: httpx.AsyncBaseTransport | None = None, ttl: float = 60):
        self._url = url.rstrip("/") + "/auth/v1/user"
        self._anon_key = anon_key
        self._transport = transport
        self._ttl = ttl
        self._cache: dict[str, tuple[User, float]] = {}

    async def verify(self, token: str) -> User:
        digest = hashlib.sha256(token.encode()).hexdigest()
        now = time.monotonic()
        hit = self._cache.get(digest)
        if hit and hit[1] > now:
            return hit[0]
        try:
            async with httpx.AsyncClient(timeout=10, transport=self._transport) as client:
                resp = await client.get(self._url, headers={"apikey": self._anon_key, "Authorization": f"Bearer {token}"})
        except httpx.HTTPError:
            raise AuthError(503, "Could not reach the sign-in service. Try again in a moment.")
        if resp.status_code in (401, 403):
            raise AuthError(401, "Your session has expired. Please sign in again.")
        if resp.status_code >= 400:
            raise AuthError(503, f"The sign-in service returned an error (HTTP {resp.status_code}).")
        try:
            data = resp.json()
            user = User(id=str(data["id"]), email=data.get("email"))
        except (ValueError, KeyError, TypeError):
            raise AuthError(503, "The sign-in service returned an unexpected response.")
        if len(self._cache) > 5000:
            self._cache = {k: v for k, v in self._cache.items() if v[1] > now}
        self._cache[digest] = (user, now + self._ttl)
        return user
