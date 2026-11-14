import io
import re
import zipfile

from fastapi import APIRouter, HTTPException, Response

from ..schemas import ExportRequest, ValidateRequest, ValidateResponse
from ..services.validator import NOTE, validate_files

router = APIRouter(prefix="/api", tags=["tools"])


@router.post("/validate", response_model=ValidateResponse)
async def validate(body: ValidateRequest):
    return ValidateResponse(issues=validate_files(body.files), files_checked=len(body.files), note=NOTE)


@router.post("/export")
async def export_zip(body: ExportRequest):
    if not body.files:
        raise HTTPException(422, "The project has no files to export")
    slug = re.sub(r"[^a-zA-Z0-9_-]+", "-", body.name).strip("-").lower() or "project"
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for f in body.files:
            archive.writestr(f"{slug}/{f.path}", f.content)
    return Response(
        buffer.getvalue(),
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="{slug}.zip"'},
    )
