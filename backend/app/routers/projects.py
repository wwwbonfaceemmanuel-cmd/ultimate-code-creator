from fastapi import APIRouter, Depends, HTTPException, Response

from ..deps import current_user, store_dep
from ..schemas import Project, ProjectCreate, ProjectSummary, ProjectUpdate
from ..services.auth import User

router = APIRouter(prefix="/api/projects", tags=["projects"])


@router.get("", response_model=list[ProjectSummary])
async def list_projects(store=Depends(store_dep), user: User = Depends(current_user)):
    return await store.list(user.id)


@router.post("", response_model=Project, status_code=201)
async def create_project(body: ProjectCreate, store=Depends(store_dep), user: User = Depends(current_user)):
    return await store.create(body.model_dump(), user.id)


@router.get("/{project_id}", response_model=Project)
async def get_project(project_id: str, store=Depends(store_dep), user: User = Depends(current_user)):
    return await store.get(project_id, user.id)


@router.put("/{project_id}", response_model=Project)
async def update_project(project_id: str, body: ProjectUpdate, store=Depends(store_dep), user: User = Depends(current_user)):
    changes = body.model_dump(exclude_none=True)
    if not changes:
        raise HTTPException(422, "Nothing to update")
    return await store.update(project_id, changes, user.id)


@router.delete("/{project_id}", status_code=204)
async def delete_project(project_id: str, store=Depends(store_dep), user: User = Depends(current_user)):
    await store.delete(project_id, user.id)
    return Response(status_code=204)
