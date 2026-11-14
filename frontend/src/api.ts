import type { EditResult, Project, ProjectFile, ProjectSummary, SystemStatus, ValidateResult } from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function errorFrom(res: Response): Promise<ApiError> {
  let message = `Request failed (HTTP ${res.status})`;
  try {
    const body = await res.json();
    if (typeof body.detail === 'string') message = body.detail;
    else if (Array.isArray(body.detail) && body.detail.length) {
      message = body.detail.map((d: { msg?: string }) => d.msg ?? '').filter(Boolean).join('; ') || message;
    }
  } catch {
    if (res.status >= 500) message = 'The backend is not reachable or failed. Is the API server running?';
  }
  return new ApiError(res.status, message);
}

async function send(path: string, method = 'GET', body?: unknown): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Could not reach the server. Check your connection and that the backend is running.');
  }
  if (!res.ok) throw await errorFrom(res);
  return res;
}

async function json<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  return (await send(path, method, body)).json();
}

export const api = {
  status: () => json<SystemStatus>('/status'),
  listProjects: () => json<ProjectSummary[]>('/projects'),
  getProject: (id: string) => json<Project>(`/projects/${id}`),
  createProject: (data: { name: string; language: string; description?: string; files: ProjectFile[] }) =>
    json<Project>('/projects', 'POST', data),
  updateProject: (id: string, data: { name?: string; description?: string; files?: ProjectFile[] }) =>
    json<Project>(`/projects/${id}`, 'PUT', data),
  deleteProject: async (id: string) => {
    await send(`/projects/${id}`, 'DELETE');
  },
  generate: (data: { prompt: string; language: string; name: string }) => json<Project>('/ai/generate', 'POST', data),
  edit: (data: { instruction: string; language: string; files: ProjectFile[] }) =>
    json<EditResult>('/ai/edit', 'POST', data),
  validate: (files: ProjectFile[]) => json<ValidateResult>('/validate', 'POST', { files }),
  exportZip: async (name: string, files: ProjectFile[]) => (await send('/export', 'POST', { name, files })).blob(),
};

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Something went wrong.';
}
