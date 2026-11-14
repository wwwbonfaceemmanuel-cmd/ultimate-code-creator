"""Project persistence: Supabase (PostgREST) when configured, local JSON files otherwise."""
from __future__ import annotations

import json
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx

from ..config import Settings


class StoreError(Exception):
    pass


class NotFound(Exception):
    pass


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


SUMMARY_FIELDS = ("id", "name", "description", "language", "file_count", "created_at", "updated_at")
_ID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")


def owner_filter(owner: str | None) -> str:
    """PostgREST filter value for an owner column (None = rows with no owner)."""
    if owner is None:
        return "is.null"
    return f"eq.{_check_id(owner)}"


def _check_id(project_id: str) -> str:
    if not _ID_RE.match(project_id):
        raise NotFound()
    return project_id


class LocalStore:
    """One JSON file per project. Intended for local development."""

    kind = "local"

    def __init__(self, directory: str):
        self._dir = Path(directory) / "projects"

    def _path(self, project_id: str) -> Path:
        return self._dir / f"{_check_id(project_id)}.json"

    def _write(self, record: dict) -> None:
        self._dir.mkdir(parents=True, exist_ok=True)
        tmp = self._path(record["id"]).with_suffix(".tmp")
        tmp.write_text(json.dumps(record, ensure_ascii=False), encoding="utf-8")
        tmp.replace(self._path(record["id"]))

    async def list(self, owner: str | None) -> list[dict]:
        items = []
        if self._dir.exists():
            for p in self._dir.glob("*.json"):
                try:
                    rec = json.loads(p.read_text(encoding="utf-8"))
                except (ValueError, OSError):
                    continue
                if rec.get("owner_id") == owner:
                    items.append({k: rec.get(k) for k in SUMMARY_FIELDS})
        return sorted(items, key=lambda r: r["updated_at"] or "", reverse=True)

    async def get(self, project_id: str, owner: str | None) -> dict:
        p = self._path(project_id)
        if not p.exists():
            raise NotFound()
        record = json.loads(p.read_text(encoding="utf-8"))
        if record.get("owner_id") != owner:
            raise NotFound()
        return record

    async def create(self, data: dict, owner: str | None) -> dict:
        now = _now()
        record = {
            **data,
            "id": str(uuid.uuid4()),
            "owner_id": owner,
            "file_count": len(data["files"]),
            "created_at": now,
            "updated_at": now,
        }
        self._write(record)
        return record

    async def update(self, project_id: str, changes: dict, owner: str | None) -> dict:
        record = await self.get(project_id, owner)
        record.update(changes)
        record["file_count"] = len(record["files"])
        record["updated_at"] = _now()
        self._write(record)
        return record

    async def delete(self, project_id: str, owner: str | None) -> None:
        await self.get(project_id, owner)
        self._path(project_id).unlink()

    async def check(self) -> None:
        self._dir.mkdir(parents=True, exist_ok=True)


class SupabaseStore:
    """Uses the Supabase REST API with the service-role key (backend only)."""

    kind = "supabase"
    table = "projects"

    def __init__(self, url: str, service_key: str, transport: httpx.AsyncBaseTransport | None = None):
        self._base = url.rstrip("/") + f"/rest/v1/{self.table}"
        self._headers = {"apikey": service_key, "Authorization": f"Bearer {service_key}"}
        self._transport = transport

    async def _request(self, method: str, params: dict | None = None, body: dict | None = None, representation: bool = False):
        headers = dict(self._headers)
        if representation:
            headers["Prefer"] = "return=representation"
        try:
            async with httpx.AsyncClient(timeout=20, transport=self._transport) as client:
                resp = await client.request(method, self._base, params=params, json=body, headers=headers)
        except httpx.HTTPError as exc:
            raise StoreError(f"Could not reach Supabase ({type(exc).__name__}).")
        if resp.status_code >= 400:
            hint = ""
            if resp.status_code == 401:
                hint = " Check SUPABASE_SERVICE_ROLE_KEY."
            elif resp.status_code == 403:
                hint = " The server key has no access to the table: run the latest supabase/schema.sql, and check SUPABASE_SERVICE_ROLE_KEY is the secret key."
            elif resp.status_code == 404:
                hint = " Has supabase/schema.sql been run?"
            raise StoreError(f"Supabase returned HTTP {resp.status_code}.{hint}")
        return resp.json() if resp.content else None

    # Every query is scoped to the owner: the service-role key bypasses Row
    # Level Security, so this filter is what keeps users' projects separate.
    @staticmethod
    def _scope(owner: str | None, project_id: str | None = None) -> dict:
        params = {"owner_id": owner_filter(owner)}
        if project_id is not None:
            params["id"] = f"eq.{_check_id(project_id)}"
        return params

    async def list(self, owner: str | None) -> list[dict]:
        return await self._request("GET", {**self._scope(owner), "select": ",".join(SUMMARY_FIELDS), "order": "updated_at.desc"})

    async def get(self, project_id: str, owner: str | None) -> dict:
        rows = await self._request("GET", {**self._scope(owner, project_id), "select": "*"})
        if not rows:
            raise NotFound()
        return rows[0]

    async def create(self, data: dict, owner: str | None) -> dict:
        body = {**data, "owner_id": owner, "file_count": len(data["files"])}
        rows = await self._request("POST", body=body, representation=True)
        return rows[0]

    async def update(self, project_id: str, changes: dict, owner: str | None) -> dict:
        body = {**changes, "updated_at": _now()}
        if "files" in changes:
            body["file_count"] = len(changes["files"])
        rows = await self._request("PATCH", self._scope(owner, project_id), body, representation=True)
        if not rows:
            raise NotFound()
        return rows[0]

    async def delete(self, project_id: str, owner: str | None) -> None:
        rows = await self._request("DELETE", self._scope(owner, project_id), representation=True)
        if not rows:
            raise NotFound()

    async def check(self) -> None:
        await self._request("GET", {"select": "id", "limit": "1"})


def get_store(settings: Settings):
    if settings.supabase_configured:
        return SupabaseStore(settings.supabase_url, settings.supabase_service_role_key)
    return LocalStore(settings.local_data_dir)
