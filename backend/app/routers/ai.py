from fastapi import APIRouter, Depends

from ..deps import ai_dep, store_dep
from ..schemas import EditRequest, EditResponse, GenerateRequest, Project
from ..services import ai as ai_service

router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.post("/generate", response_model=Project, status_code=201)
async def generate(body: GenerateRequest, client=Depends(ai_dep), store=Depends(store_dep)):
    """Generate a project with the connected AI model and save it."""
    text = await client.complete(ai_service.GENERATE_SYSTEM, ai_service.generate_user_prompt(body.prompt, body.language))
    name, summary, files = ai_service.parse_generation(text)
    return await store.create(
        {
            "name": body.name.strip() or name or "Untitled project",
            "description": summary,
            "language": body.language,
            "prompt": body.prompt,
            "files": [f.model_dump() for f in files],
        }
    )


@router.post("/edit", response_model=EditResponse)
async def edit(body: EditRequest, client=Depends(ai_dep)):
    """Apply a natural-language instruction to the given files. Does not save."""
    text = await client.complete(ai_service.EDIT_SYSTEM, ai_service.edit_user_prompt(body.instruction, body.language, body.files))
    summary, files, changed, deleted = ai_service.apply_edit(text, body.files)
    return EditResponse(summary=summary or "Changes applied.", files=files, changed=changed, deleted=deleted)
