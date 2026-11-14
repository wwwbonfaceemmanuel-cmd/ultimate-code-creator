# AI Code Studio

Describe the software you want in ordinary language; a connected AI model writes a multi-file project that you can browse, edit, preview, check, save and download.

- **Frontend:** React + TypeScript (Vite), Monaco Editor — `frontend/`
- **Backend:** Python FastAPI — `backend/`
- **Database:** Supabase (Postgres) — `supabase/schema.sql`

## What works today

| Area | Status |
| --- | --- |
| Dashboard, navigation, project list with search and filter | Working |
| Prompt screen with language selector (HTML, CSS, JavaScript, TypeScript, Python, Java, Kotlin, PHP, C++, C#, Dart) | Working |
| AI project generation and follow-up AI edits (with undo) | Working **once you connect a model** — see below. Without one, the app says so and the buttons are disabled; nothing is faked. |
| Workspace: file explorer, Monaco editor with syntax highlighting, new/rename/delete file | Working |
| Save and reopen projects | Working (local files by default, Supabase when configured) |
| Live preview | Working for projects with an HTML page (sandboxed iframe; local CSS/JS files are inlined) |
| Download as ZIP | Working |
| Code checks | Python and JSON syntax, HTML tag nesting and missing local files, bracket balance for the other languages. Code is not compiled or run. |
| Mobile layout | Working |

### Not built yet

- **User accounts.** There is no login. Anyone who can reach the server can see every project and spend your AI credit, so do not put it on the public internet without adding authentication (Supabase Auth is the intended route; the `owner_id` column is reserved for it) or putting it behind a private network / access proxy.
- Running non-web projects (Python, Java, …) in the browser. Download the ZIP and run them locally.
- Streaming AI output, project version history, rate limiting.

## Run it locally

Requirements: Python 3.11+, Node.js 20+.

```bash
# 1. Backend
cd backend
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt     # Windows: .venv\Scripts\pip
cp .env.example .env                            # then edit .env (see below)
.venv/bin/uvicorn app.main:app --port 8000 --reload

# 2. Frontend (second terminal)
cd frontend
npm install
npm run dev                                     # http://localhost:5173
```

The dev server proxies `/api` to the backend, so the browser never talks to the AI provider or Supabase directly.

## Connect an AI model

The backend calls any **OpenAI-compatible** `/chat/completions` API. Set these in `backend/.env` and restart the backend:

```ini
AI_API_KEY=your-secret-key
AI_BASE_URL=https://api.openai.com/v1     # or https://openrouter.ai/api/v1, https://api.groq.com/openai/v1, ...
AI_MODEL=the-model-id-from-your-provider
```

Optional: `AI_MAX_OUTPUT_TOKENS` (default 8000 — raise it for bigger projects if your model allows), `AI_TIMEOUT_SECONDS` (default 180), `AI_JSON_MODE=false` if your provider rejects `response_format`.

Open **Settings** in the app and press **Check again** to confirm. The key stays on the server: it is never sent to the browser or returned by any endpoint.

To use a provider with a different API shape, add a client class in `backend/app/services/ai.py` with the same `complete(system, user)` coroutine and return it from `get_ai_client`.

## Connect Supabase

Without Supabase, projects are stored as JSON files in `backend/data/` — fine for local use, but most hosts wipe the disk on redeploy.

1. Create a Supabase project.
2. Run `supabase/schema.sql` in the SQL editor.
3. Set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` (Project Settings → API) in `backend/.env` and restart.

The service-role key is used only by the backend. Row Level Security is enabled with no policies, so the public anon key can read nothing.

## Deploy

**One service (simplest).** The backend serves the built frontend when `frontend/dist` exists. The included `Dockerfile` does this:

```bash
docker build -t ai-code-studio .
docker run -p 8000:8000 \
  -e AI_API_KEY=... -e AI_MODEL=... -e AI_BASE_URL=... \
  -e SUPABASE_URL=... -e SUPABASE_SERVICE_ROLE_KEY=... \
  ai-code-studio
```

This suits any container host (Render, Railway, Fly.io, Cloud Run…). Set the variables in the host's secret settings, not in the image. The server listens on `$PORT` if the host sets it.

**Two services.** Host `frontend/dist` on a static host and the backend separately. Configure the static host to forward `/api/*` to the backend (recommended), or serve the API on another origin and list the frontend origin in `CORS_ORIGINS`.

Always use Supabase in production, and read the note about user accounts above first.

## Tests and checks

```bash
cd backend && .venv/bin/pip install -r requirements-dev.txt && .venv/bin/pytest
cd frontend && npm run build        # type-checks, then builds
```

## Layout

```
backend/app/
  main.py            app factory, error handlers, static frontend
  config.py          environment settings
  schemas.py         request/response models, limits, path safety
  routers/           status, projects, ai (generate/edit), tools (validate/export)
  services/ai.py     AI client, prompts, response parsing
  services/store.py  Supabase and local-file project stores
  services/validator.py
frontend/src/
  pages/             Dashboard, NewProject, Projects, Workspace, Settings
  components/        Layout, FileExplorer, Modal, ProjectCard, AiNotice
  lib/               Monaco setup, preview builder, helpers
  api.ts             typed API client
supabase/schema.sql
```

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/status` | AI and storage connection state (no secrets) |
| GET/POST | `/api/projects` | List / create |
| GET/PUT/DELETE | `/api/projects/{id}` | Read / update / delete |
| POST | `/api/ai/generate` | Generate a project from a prompt and save it |
| POST | `/api/ai/edit` | Apply an instruction to a set of files |
| POST | `/api/validate` | Check files |
| POST | `/api/export` | Build a ZIP |

Interactive docs: `http://localhost:8000/docs`.
