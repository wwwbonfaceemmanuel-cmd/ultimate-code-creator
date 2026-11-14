# AI Code Studio

Describe the software you want in ordinary language; a connected AI model writes a multi-file project that you can browse, edit, preview, check, save and download.

- **Frontend:** React + TypeScript (Vite), Monaco Editor — `frontend/`
- **Backend:** Python FastAPI — `backend/`
- **Accounts and database:** Supabase (Auth + Postgres) — `supabase/schema.sql`

## What works today

| Area | Status |
| --- | --- |
| Dashboard, navigation, project list with search and filter | Working |
| Prompt screen with language selector (HTML, CSS, JavaScript, TypeScript, Python, Java, Kotlin, PHP, C++, C#, Dart) | Working |
| AI project generation and follow-up AI edits (with undo) | Working **once you connect a model** — see below. Without one, the app says so and the buttons are disabled; nothing is faked. |
| Workspace: file explorer, Monaco editor with syntax highlighting, new/rename/delete file | Working |
| User accounts: sign up, sign in, sign out, password reset; each account sees only its own projects | Working once Supabase is connected (see below). Without it the app runs in single-user local mode with no login. |
| AI usage limits per account (default 20 an hour, 100 a day) | Working |
| Save and reopen projects | Working (local files by default, Supabase when configured) |
| Live preview | Working for projects with an HTML page (sandboxed iframe; local CSS/JS files are inlined) |
| Download as ZIP | Working |
| Code checks | Python and JSON syntax, HTML tag nesting and missing local files, bracket balance for the other languages. Code is not compiled or run. |
| Mobile layout | Working |

### Not built yet

- Running non-web projects (Python, Java, …) in the browser. Download the ZIP and run them locally.
- Streaming AI output, project version history, sharing a project between accounts.
- Sign-in with Google/GitHub (only email and password).

### How it has been tested

Automated backend tests, plus a browser run of the whole flow (sign up, sign in, wrong password, private projects, limits, password reset, generate, edit, preview, save, ZIP) against **stand-ins** for Supabase and the AI provider. It has not yet been run against a real Supabase project or a real AI provider, so do one full pass yourself after connecting them.

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

## Connect Supabase (sign-in, storage, usage limits)

Without Supabase the app runs in **single-user local mode**: no login, projects stored as JSON files in `backend/data/`. That is fine on your own computer and unsafe on a public server.

1. Create a project at supabase.com (the free plan is enough).
2. Open **SQL Editor**, paste the contents of `supabase/schema.sql`, and run it. It creates the `projects` and `ai_usage` tables and is safe to run again after updates.
3. Open **Project Settings → API** and copy three values into `backend/.env` (or your host's environment settings):

   | Variable | Value in Supabase | Secret? |
   | --- | --- | --- |
   | `SUPABASE_URL` | Project URL | No |
   | `SUPABASE_ANON_KEY` | `anon` / publishable key | No — it is sent to the browser for sign-in |
   | `SUPABASE_SERVICE_ROLE_KEY` | `service_role` / secret key | **Yes** — server only |

4. Open **Authentication → URL Configuration** and set **Site URL** to the address people use to open the app (for example `https://your-app.onrender.com`, or `http://localhost:5173` while developing). The links in confirmation and password-reset emails point there.
5. Restart the backend. The app now shows a sign-in screen.

Notes:

- By default Supabase emails each new user a confirmation link before they can sign in, and its built-in email sender is limited to a few messages an hour. For real use, set up your own SMTP sender under **Authentication → Emails**, or turn confirmation off under **Authentication → Sign In / Providers → Email**.
- To stop strangers registering, turn off **Allow new users to sign up** in the same place and invite users from the Supabase dashboard.
- How it is secured: the browser signs in with Supabase and sends its access token with every request; the backend asks Supabase who the token belongs to and filters every database query by that user. Row Level Security is on with no policies, so the public key cannot read either table directly.
- Projects created in local mode have no owner and are not shown to signed-in users.

### AI usage limits

Each account may make `AI_LIMIT_PER_HOUR` (default 20) and `AI_LIMIT_PER_DAY` (default 100) AI requests; generating and editing both count. Set either to `0` to remove that limit. With Supabase connected the counts are stored in `ai_usage`; in local mode they are kept in memory and reset when the server restarts.

### `REQUIRE_AUTH`

Set `REQUIRE_AUTH=true` on any server other people can reach (the Docker image does this already). If sign-in is not configured, the server then stays locked and shows setup steps instead of running open to everyone.

## Deploy

The backend serves the built frontend, so the whole app is **one service**. The `Dockerfile` builds it, and GitHub Actions builds and starts that image on every change to check it works.

### Render (step by step)

1. Do the Supabase steps above and have your AI key ready.
2. On render.com choose **New → Blueprint** and pick this repository. Render reads `render.yaml`.
3. Enter the values it asks for: `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`. For `AI_BASE_URL` enter your provider's address, for example `https://api.openai.com/v1` or `https://api.groq.com/openai/v1`.
4. Deploy. When it is live, copy the service address into Supabase **Authentication → URL Configuration → Site URL**.
5. Open the address, create your account, and generate a small project to confirm everything is connected.

The free Render plan sleeps after a period without visitors, so the first visit afterwards is slow. `render.yaml` has not been run on Render from this repository yet; if Render rejects a field, create a **Web Service** from the repository instead (runtime: Docker) and add the same variables by hand.

### Any other container host (Railway, Fly.io, Cloud Run, a VPS)

```bash
docker build -t ai-code-studio .
docker run -p 8000:8000 \
  -e AI_API_KEY=... -e AI_MODEL=... -e AI_BASE_URL=... \
  -e SUPABASE_URL=... -e SUPABASE_ANON_KEY=... -e SUPABASE_SERVICE_ROLE_KEY=... \
  ai-code-studio
```

Set the variables in the host's secret settings, not in the image. The server listens on `$PORT` if the host sets it, and `/api/health` is the health check.

### Frontend and backend on separate hosts

Host `frontend/dist` on a static host and the backend elsewhere. Configure the static host to forward `/api/*` to the backend (recommended), or list the frontend's origin in `CORS_ORIGINS`.

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
  services/auth.py   sign-in token verification
  services/usage.py  per-user AI limits
  services/validator.py
frontend/src/
  pages/             Dashboard, NewProject, Projects, Workspace, Settings, Login
  auth.tsx           sign-in state (Supabase Auth)
  components/        Layout, FileExplorer, Modal, ProjectCard, AiNotice
  lib/               Monaco setup, preview builder, helpers
  api.ts             typed API client
supabase/schema.sql  database tables
render.yaml          Render deployment blueprint
```

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/status` | Connection state and public sign-in settings (no secrets). The only endpoint that works without signing in. |
| GET/POST | `/api/projects` | List / create |
| GET/PUT/DELETE | `/api/projects/{id}` | Read / update / delete |
| POST | `/api/ai/generate` | Generate a project from a prompt and save it |
| POST | `/api/ai/edit` | Apply an instruction to a set of files |
| GET | `/api/ai/usage` | AI requests used against the hourly and daily limits |
| POST | `/api/validate` | Check files |
| POST | `/api/export` | Build a ZIP |

Interactive docs: `http://localhost:8000/docs`.

## Android app

`android/` is a small Android app that opens the hosted site full-screen. It has no logic or secrets of its own: sign-in, projects and AI generation all run on the website, so the app needs internet. It adds a loading screen, an offline screen with a retry button, Back-button navigation, and saving ZIP downloads to the phone's Downloads folder.

The **Android app** workflow (`.github/workflows/android.yml`) builds the APK on every push, then installs and opens it on an emulator to check it starts and reaches the site.

To get the APK: GitHub → **Actions** → **Android app** → open the latest run with a green tick → **Artifacts** → **AI-Code-Studio-Android**. The download is a ZIP; the `ai-code-studio.apk` file is inside it.

- The site address is `appUrl` in `android/gradle.properties`. To build for another address without editing files, use **Run workflow** on the Actions page and fill in the address.
- The APK is signed with a key stored in this repository (`android/keystore/app.p12`) so newer builds install over older ones. Because that key is public, use these builds for direct sharing only; a Play Store release needs a private key.

