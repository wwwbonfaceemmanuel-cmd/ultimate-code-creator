from fastapi import APIRouter, Depends, HTTPException, Response

from ..deps import store_dep
from ..schemas import Project, ProjectCreate, ProjectSummary, ProjectUpdate

router = APIRouter(prefix="/api/projects", tags=["projects"])


@router.get("", response_model=list[ProjectSummary])
async def list_projects(store=Depends(store_dep)):
    return await store.list()


@router.post("", response_model=Project, status_code=201)
async def create_project(body: ProjectCreate, store=Depends(store_dep)):
    return await store.create(body.model_dump())


@router.get("/{project_id}", response_model=Project)
async def get_project(project_id: str, store=Depends(store_dep)):
    return await store.get(project_id)


@router.put("/{project_id}", response_model=Project)
async def update_project(project_id: str, body: ProjectUpdate, store=Depends(store_dep)):
    changes = body.model_dump(exclude_none=True)
    if not changes:
        raise HTTPException(422, "Nothing to update")
    return await store.update(project_id, changes)


@router.delete("/{project_id}", status_code=204)
async def delete_project(project_id: str, store=Depends(store_dep)):
    await store.delete(project_id)
    return Response(status_code=204)
