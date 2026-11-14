"""AI Code Studio backend."""
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse

from .config import get_settings
from .routers import ai, projects, status, tools
from .services.ai import AIError
from .services.store import NotFound, StoreError


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="AI Code Studio API", version="0.1.0")

    if settings.cors_origin_list:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origin_list,
            allow_methods=["*"],
            allow_headers=["*"],
        )

    @app.exception_handler(AIError)
    async def _ai_error(_: Request, exc: AIError):
        return JSONResponse({"detail": exc.message}, status_code=exc.status_code)

    @app.exception_handler(StoreError)
    async def _store_error(_: Request, exc: StoreError):
        return JSONResponse({"detail": str(exc)}, status_code=502)

    @app.exception_handler(NotFound)
    async def _not_found(_: Request, exc: NotFound):
        return JSONResponse({"detail": "Project not found"}, status_code=404)

    for module in (status, projects, ai, tools):
        app.include_router(module.router)

    # Serve the built frontend (single-service deployment) when it exists.
    dist = Path(settings.frontend_dist).resolve()
    if (dist / "index.html").is_file():

        @app.get("/{full_path:path}", include_in_schema=False)
        async def spa(full_path: str):
            if full_path.startswith("api/"):
                raise HTTPException(404, "Not found")
            candidate = (dist / full_path).resolve()
            if full_path and candidate.is_file() and dist in candidate.parents:
                return FileResponse(candidate)
            return FileResponse(dist / "index.html")

    return app


app = create_app()
