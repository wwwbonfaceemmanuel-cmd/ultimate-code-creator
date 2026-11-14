"""Request/response models and shared limits."""
from typing import Literal

from pydantic import BaseModel, Field, field_validator

from .languages import LANGUAGES

MAX_FILES = 80
MAX_FILE_CHARS = 300_000
MAX_TOTAL_CHARS = 2_000_000


def sanitize_path(raw: str) -> str:
    """Return a safe, relative, forward-slash project path or raise ValueError."""
    path = raw.strip().replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    path = path.lstrip("/")
    parts = [p for p in path.split("/") if p not in ("", ".")]
    if not parts:
        raise ValueError("File path is empty")
    if any(p == ".." for p in parts):
        raise ValueError(f"File path may not contain '..': {raw!r}")
    path = "/".join(parts)
    if len(path) > 240 or any(ord(c) < 32 for c in path) or ":" in parts[0]:
        raise ValueError(f"Invalid file path: {raw!r}")
    return path


class ProjectFile(BaseModel):
    path: str
    content: str = Field(default="", max_length=MAX_FILE_CHARS)

    @field_validator("path")
    @classmethod
    def _path(cls, v: str) -> str:
        return sanitize_path(v)


def check_files(files: list[ProjectFile]) -> list[ProjectFile]:
    if len(files) > MAX_FILES:
        raise ValueError(f"A project can contain at most {MAX_FILES} files")
    seen: set[str] = set()
    for f in files:
        if f.path in seen:
            raise ValueError(f"Duplicate file path: {f.path}")
        seen.add(f.path)
    if sum(len(f.content) for f in files) > MAX_TOTAL_CHARS:
        raise ValueError("Project is too large")
    return files


class _WithFiles(BaseModel):
    files: list[ProjectFile] = []

    @field_validator("files")
    @classmethod
    def _files(cls, v: list[ProjectFile]) -> list[ProjectFile]:
        return check_files(v)


def _language(v: str) -> str:
    if v not in LANGUAGES:
        raise ValueError(f"Unsupported language '{v}'")
    return v


class ProjectCreate(_WithFiles):
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(default="", max_length=2000)
    language: str
    prompt: str = Field(default="", max_length=8000)

    _lang = field_validator("language")(_language)


class ProjectUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    description: str | None = Field(default=None, max_length=2000)
    files: list[ProjectFile] | None = None

    @field_validator("files")
    @classmethod
    def _files(cls, v):
        return None if v is None else check_files(v)


class ProjectSummary(BaseModel):
    id: str
    name: str
    description: str = ""
    language: str
    file_count: int = 0
    created_at: str
    updated_at: str


class Project(ProjectSummary):
    prompt: str = ""
    files: list[ProjectFile] = []


class GenerateRequest(BaseModel):
    prompt: str = Field(min_length=10, max_length=8000)
    language: str
    name: str = Field(default="", max_length=120)

    _lang = field_validator("language")(_language)


class EditRequest(_WithFiles):
    instruction: str = Field(min_length=3, max_length=8000)
    language: str

    _lang = field_validator("language")(_language)


class EditResponse(BaseModel):
    summary: str
    files: list[ProjectFile]
    changed: list[str]
    deleted: list[str]


class ValidateRequest(_WithFiles):
    pass


class ExportRequest(_WithFiles):
    name: str = Field(default="project", max_length=120)


class Issue(BaseModel):
    path: str
    line: int = 1
    column: int = 1
    severity: Literal["error", "warning"]
    message: str


class ValidateResponse(BaseModel):
    issues: list[Issue]
    files_checked: int
    note: str
