from fastapi import APIRouter, Depends

from ..config import Settings
from ..deps import ai_dep, current_user, settings_dep, store_dep, usage_dep
from ..schemas import EditRequest, EditResponse, GenerateRequest, Project
from ..services import ai as ai_service
from ..services import usage as usage_service
from ..services.auth import User

router = APIRouter(prefix="/api/ai", tags=["ai"])


async def _spend(kind: str, settings: Settings, tracker, user: User) -> None:
    # Checked first so a missing AI connection does not use up the user's allowance.
    if not settings.ai_configured:
        raise ai_service.AIError(503, ai_service.NOT_CONFIGURED)
    await usage_service.enforce(tracker, settings, user, kind)


@router.get("/usage")
async def usage(settings: Settings = Depends(settings_dep), tracker=Depends(usage_dep), user: User = Depends(current_user)):
    """How many AI requests the user has made against their limits."""
    return usage_service.summarize(await tracker.recent(user), settings)


@router.post("/generate", response_model=Project, status_code=201)
async def generate(
    body: GenerateRequest,
    client=Depends(ai_dep),
    store=Depends(store_dep),
    settings: Settings = Depends(settings_dep),
    tracker=Depends(usage_dep),
    user: User = Depends(current_user),
):
    """Generate a project with the connected AI model and save it."""
    await _spend("generate", settings, tracker, user)
    name, summary, files = await ai_service.complete_and_parse(
        client, ai_service.GENERATE_SYSTEM, ai_service.generate_user_prompt(body.prompt, body.language), ai_service.parse_generation
    )
    return await store.create(
        {
            "name": body.name.strip() or name or "Untitled project",
            "description": summary,
            "language": body.language,
            "prompt": body.prompt,
            "files": [f.model_dump() for f in files],
        },
        user.id,
    )


@router.post("/edit", response_model=EditResponse)
async def edit(
    body: EditRequest,
    client=Depends(ai_dep),
    settings: Settings = Depends(settings_dep),
    tracker=Depends(usage_dep),
    user: User = Depends(current_user),
):
    """Apply a natural-language instruction to the given files. Does not save."""
    await _spend("edit", settings, tracker, user)
    summary, files, changed, deleted = await ai_service.complete_and_parse(
        client,
        ai_service.EDIT_SYSTEM,
        ai_service.edit_user_prompt(body.instruction, body.language, body.files),
        lambda text: ai_service.apply_edit(text, body.files),
    )
    return EditResponse(summary=summary or "Changes applied.", files=files, changed=changed, deleted=deleted)
