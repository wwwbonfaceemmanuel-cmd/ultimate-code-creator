"""Server-side AI integration.

Talks to any OpenAI-compatible ``/chat/completions`` endpoint. The API key is
read from the environment and never leaves the backend. To plug in a provider
with a different wire format, implement another class with the same
``complete`` coroutine and return it from ``get_ai_client``.
"""
from __future__ import annotations

import json
import re

import httpx

from ..config import Settings
from ..languages import LANGUAGES
from ..schemas import MAX_FILES, ProjectFile, check_files, sanitize_path


class AIError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


NOT_CONFIGURED = (
    "No AI model is connected. Set AI_API_KEY, AI_BASE_URL and AI_MODEL in the "
    "backend environment (see backend/.env.example) and restart the server."
)


class OpenAICompatibleClient:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self._s = settings
        self._transport = transport

    async def complete(self, system: str, user: str) -> str:
        s = self._s
        if not s.ai_configured:
            raise AIError(503, NOT_CONFIGURED)
        payload: dict = {
            "model": s.ai_model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "max_tokens": s.ai_max_output_tokens,
        }
        if s.ai_json_mode:
            payload["response_format"] = {"type": "json_object"}
        url = s.ai_base_url.rstrip("/") + "/chat/completions"
        try:
            async with httpx.AsyncClient(timeout=s.ai_timeout_seconds, transport=self._transport) as client:
                resp = await client.post(
                    url, json=payload, headers={"Authorization": f"Bearer {s.ai_api_key}"}
                )
        except httpx.TimeoutException:
            raise AIError(504, "The AI provider took too long to respond. Try again or simplify the request.")
        except httpx.HTTPError as exc:
            raise AIError(502, f"Could not reach the AI provider ({type(exc).__name__}).")

        if resp.status_code >= 400:
            raise AIError(502, _provider_error(resp))
        try:
            data = resp.json()
            choice = data["choices"][0]
            content = choice["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError):
            raise AIError(502, "The AI provider returned an unexpected response.")
        if not isinstance(content, str) or not content.strip():
            raise AIError(502, "The AI provider returned an empty response.")
        if choice.get("finish_reason") == "length":
            raise AIError(
                502,
                "The AI response was cut off before it finished (output token limit). "
                "Raise AI_MAX_OUTPUT_TOKENS or ask for a smaller project.",
            )
        return content


def _provider_error(resp: httpx.Response) -> str:
    detail = ""
    try:
        err = resp.json().get("error")
        detail = err.get("message", "") if isinstance(err, dict) else str(err or "")
    except Exception:
        pass
    detail = detail[:300]
    if resp.status_code in (401, 403):
        base = "The AI provider rejected the server's API key."
    elif resp.status_code == 404:
        base = "The AI provider did not recognise the endpoint or model (check AI_BASE_URL and AI_MODEL)."
    elif resp.status_code == 429:
        base = "The AI provider is rate-limiting or out of quota."
    else:
        base = f"The AI provider returned an error (HTTP {resp.status_code})."
    return f"{base} {detail}".strip()


def get_ai_client(settings: Settings) -> OpenAICompatibleClient:
    return OpenAICompatibleClient(settings)


# --------------------------------------------------------------------------
# Prompts and response parsing
# --------------------------------------------------------------------------

_FORMAT_RULES = f"""Respond with ONE JSON object and nothing else (no markdown fences, no commentary).
File paths are relative, use forward slashes, and never contain "..".
Every file's "content" is the complete file text - never abbreviate with placeholders such as "...".
Use at most {MAX_FILES} files."""

GENERATE_SYSTEM = f"""You are a senior software engineer who writes complete, working, maintainable projects.
The user describes software in plain language. Produce the full multi-file project.
Organise the code sensibly into files, include a README.md with run instructions, and any
manifest the stack needs (package.json, requirements.txt, build files...).
For browser projects put the entry page at index.html and reference other files by relative path
so the project runs without a build step where practical.

{_FORMAT_RULES}
Schema: {{"name": "short project name", "summary": "one or two sentences on what was built",
"files": [{{"path": "index.html", "content": "..."}}]}}"""

EDIT_SYSTEM = f"""You are a senior software engineer modifying an existing project according to the user's instruction.
Change only what the instruction requires and keep the rest of the project intact.

{_FORMAT_RULES}
Return only files you created or changed, each with its full new content.
Schema: {{"summary": "what you changed", "files": [{{"path": "...", "content": "..."}}],
"deleted": ["paths/to/remove"]}}"""


def generate_user_prompt(prompt: str, language: str) -> str:
    return f"Primary language: {LANGUAGES[language]}\n\nSoftware to build:\n{prompt.strip()}"


def edit_user_prompt(instruction: str, language: str, files: list[ProjectFile]) -> str:
    current = json.dumps([f.model_dump() for f in files], ensure_ascii=False)
    return (
        f"Primary language: {LANGUAGES[language]}\n\nInstruction:\n{instruction.strip()}\n\n"
        f"Current project files (JSON):\n{current}"
    )


def extract_json(text: str) -> dict:
    """Parse the model output, tolerating code fences or stray prose around the object."""
    cleaned = text.strip()
    fence = re.match(r"^```[a-zA-Z]*\s*\n(.*)\n```\s*$", cleaned, re.S)
    if fence:
        cleaned = fence.group(1).strip()
    candidates = [cleaned]
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start != -1 and end > start:
        candidates.append(cleaned[start : end + 1])
    for candidate in candidates:
        try:
            data = json.loads(candidate)
        except ValueError:
            continue
        if isinstance(data, dict):
            return data
    raise AIError(502, "The AI response was not valid JSON, so no project could be built from it. Try again.")


def parse_files(raw) -> list[ProjectFile]:
    if not isinstance(raw, list):
        raise AIError(502, "The AI response did not contain a file list. Try again.")
    files: dict[str, ProjectFile] = {}
    try:
        for item in raw:
            f = ProjectFile(path=item["path"], content=item.get("content") or "")
            files[f.path] = f
        return check_files(list(files.values()))
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise AIError(502, f"The AI returned an unusable file list ({str(exc)[:200]}). Try again.")


def parse_generation(text: str) -> tuple[str, str, list[ProjectFile]]:
    data = extract_json(text)
    files = parse_files(data.get("files"))
    if not files:
        raise AIError(502, "The AI did not return any files. Try rephrasing the request.")
    return str(data.get("name") or "").strip()[:120], str(data.get("summary") or "").strip()[:2000], files


def apply_edit(text: str, current: list[ProjectFile]) -> tuple[str, list[ProjectFile], list[str], list[str]]:
    data = extract_json(text)
    changed_files = parse_files(data.get("files") or [])
    merged = {f.path: f for f in current}
    changed = [f.path for f in changed_files if f.path not in merged or merged[f.path].content != f.content]
    for f in changed_files:
        merged[f.path] = f
    deleted: list[str] = []
    for raw in data.get("deleted") or []:
        try:
            path = sanitize_path(str(raw))
        except ValueError:
            continue
        if path in merged:
            del merged[path]
            deleted.append(path)
    if not changed and not deleted:
        raise AIError(502, "The AI did not change any files. Try a more specific instruction.")
    try:
        result = check_files(list(merged.values()))
    except ValueError as exc:
        raise AIError(502, f"The edited project is not usable ({exc}).")
    return str(data.get("summary") or "").strip()[:2000], result, changed, deleted
