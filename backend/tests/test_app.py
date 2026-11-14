import io
import json
import zipfile

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.deps import ai_dep, settings_dep, usage_dep, verifier_dep
from app.main import create_app
from app.schemas import ProjectFile, sanitize_path
from app.services import ai as ai_service
from app.services.ai import AIError, OpenAICompatibleClient
from app.services.auth import AuthError as SignInError
from app.services.auth import SupabaseVerifier, User
from app.services.store import SupabaseStore
from app.services.usage import MemoryUsage, SupabaseUsage
from app.services.validator import validate_files


def make_client(tmp_path, ai_text=None, **overrides):
    if ai_text is not None:
        overrides = {"ai_api_key": "k", "ai_model": "m", **overrides}
    settings = Settings(_env_file=None, local_data_dir=str(tmp_path), frontend_dist=str(tmp_path / "none"), **overrides)
    app = create_app()
    app.dependency_overrides[settings_dep] = lambda: settings
    tracker = MemoryUsage()
    app.dependency_overrides[usage_dep] = lambda: tracker
    if ai_text is not None:

        class Fake:
            async def complete(self, system, user):
                return ai_text

        app.dependency_overrides[ai_dep] = lambda: Fake()
    return TestClient(app)


def test_status_reports_ai_not_configured(tmp_path):
    body = make_client(tmp_path).get("/api/status").json()
    assert body["ai"]["configured"] is False
    assert "AI_API_KEY" in body["ai"]["missing"]
    assert body["storage"] == {"kind": "local", "ok": True, "error": None}
    assert len(body["languages"]) == 11


def test_generate_without_ai_is_503_not_fake_output(tmp_path):
    c = make_client(tmp_path)
    r = c.post("/api/ai/generate", json={"prompt": "a todo list web app", "language": "html"})
    assert r.status_code == 503
    assert "No AI model is connected" in r.json()["detail"]
    assert c.get("/api/projects").json() == []


def test_generate_saves_project(tmp_path):
    reply = "```json\n" + json.dumps({"name": "Todo", "summary": "s", "files": [{"path": "./index.html", "content": "<p>hi</p>"}]}) + "\n```"
    c = make_client(tmp_path, ai_text=reply)
    r = c.post("/api/ai/generate", json={"prompt": "a todo list web app", "language": "html"})
    assert r.status_code == 201
    project = r.json()
    assert project["name"] == "Todo" and project["files"][0]["path"] == "index.html"
    assert c.get("/api/projects").json()[0]["file_count"] == 1


def test_generate_rejects_garbage_and_path_traversal(tmp_path):
    assert make_client(tmp_path, ai_text="sorry, no").post("/api/ai/generate", json={"prompt": "a todo list web app", "language": "html"}).status_code == 502
    evil = json.dumps({"files": [{"path": "../../etc/passwd", "content": "x"}]})
    assert make_client(tmp_path, ai_text=evil).post("/api/ai/generate", json={"prompt": "a todo list web app", "language": "html"}).status_code == 502


def test_edit_merges_changes(tmp_path):
    reply = json.dumps({"summary": "done", "files": [{"path": "b.js", "content": "new"}], "deleted": ["a.js"]})
    c = make_client(tmp_path, ai_text=reply)
    r = c.post("/api/ai/edit", json={"instruction": "do it", "language": "javascript", "files": [{"path": "a.js", "content": "1"}, {"path": "keep.js", "content": "2"}]})
    assert r.status_code == 200
    body = r.json()
    assert sorted(f["path"] for f in body["files"]) == ["b.js", "keep.js"]
    assert body["changed"] == ["b.js"] and body["deleted"] == ["a.js"]


def test_project_crud(tmp_path):
    c = make_client(tmp_path)
    created = c.post("/api/projects", json={"name": "P", "language": "python", "files": [{"path": "main.py", "content": "print(1)"}]}).json()
    pid = created["id"]
    updated = c.put(f"/api/projects/{pid}", json={"name": "Q", "files": []}).json()
    assert updated["name"] == "Q" and updated["file_count"] == 0
    assert c.get(f"/api/projects/{pid}").json()["name"] == "Q"
    assert c.delete(f"/api/projects/{pid}").status_code == 204
    assert c.get(f"/api/projects/{pid}").status_code == 404
    assert c.get("/api/projects/..%2F..%2Fsecret").status_code == 404
    assert c.post("/api/projects", json={"name": "P", "language": "cobol"}).status_code == 422


def test_export_zip(tmp_path):
    r = make_client(tmp_path).post("/api/export", json={"name": "My App!", "files": [{"path": "src/a.txt", "content": "héllo"}]})
    assert r.status_code == 200 and "my-app.zip" in r.headers["content-disposition"]
    z = zipfile.ZipFile(io.BytesIO(r.content))
    assert z.read("my-app/src/a.txt").decode() == "héllo"


def test_sanitize_path():
    assert sanitize_path("./a\\b/c.txt") == "a/b/c.txt"
    assert sanitize_path("/.gitignore") == ".gitignore"
    for bad in ("", "../x", "a/../../x", "C:/x"):
        with pytest.raises(ValueError):
            sanitize_path(bad)


def F(path, content):
    return ProjectFile(path=path, content=content)


def test_validator():
    assert validate_files([F("ok.py", "x = 1\n"), F("ok.json", "{}"), F("ok.js", "const s = '}'; // )\nfunction f() { return [1]; }")]) == []
    py = validate_files([F("bad.py", "def f(:\n  pass")])
    assert py[0].severity == "error" and py[0].line == 1
    assert validate_files([F("bad.json", "{,}")])[0].severity == "error"
    assert "never closed" in validate_files([F("a.java", "class A { void f() { }")])[0].message
    html = validate_files([F("index.html", "<div><p>hi<br><span></div><script src='app.js'></script>")])
    msgs = [i.message for i in html]
    assert any("<span>" in m for m in msgs) and any("app.js" in m for m in msgs)
    assert validate_files([F("index.html", "<ul><li>a<li>b</ul><script src='js/app.js'></script>"), F("js/app.js", "let a;")]) == []


def _client(handler, **kw):
    s = Settings(_env_file=None, ai_api_key="sk-secret", ai_model="m", **kw)
    return OpenAICompatibleClient(s, transport=httpx.MockTransport(handler))


async def test_ai_client_sends_key_server_side_and_parses():
    seen = {}

    def handler(request):
        seen["auth"] = request.headers["authorization"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"choices": [{"message": {"content": "{\"a\":1}"}, "finish_reason": "stop"}]})

    assert await _client(handler).complete("sys", "usr") == '{"a":1}'
    assert seen["auth"] == "Bearer sk-secret" and seen["body"]["model"] == "m"


async def test_ai_client_error_mapping_hides_key():
    with pytest.raises(AIError) as e:
        await _client(lambda r: httpx.Response(401, json={"error": {"message": "bad key"}})).complete("s", "u")
    assert e.value.status_code == 502 and "sk-secret" not in e.value.message
    with pytest.raises(AIError) as e:
        await _client(lambda r: httpx.Response(200, json={"choices": [{"message": {"content": "{"}, "finish_reason": "length"}]})).complete("s", "u")
    assert "cut off" in e.value.message
    with pytest.raises(AIError) as e:
        await OpenAICompatibleClient(Settings(_env_file=None)).complete("s", "u")
    assert e.value.status_code == 503


def test_extract_json_tolerates_prose():
    assert ai_service.extract_json('Here you go: {"files": []} thanks') == {"files": []}


# ---------------------------------------------------------------------------
# Sign-in, per-user projects and AI limits
# ---------------------------------------------------------------------------
UID_A = "11111111-1111-1111-1111-111111111111"
UID_B = "22222222-2222-2222-2222-222222222222"
GEN_REPLY = json.dumps({"name": "T", "summary": "s", "files": [{"path": "a.txt", "content": "x"}]})
GEN_BODY = {"prompt": "a todo list web app", "language": "html"}


class FakeVerifier:
    async def verify(self, token):
        users = {"token-a": User(UID_A, "a@example.com"), "token-b": User(UID_B, "b@example.com")}
        if token not in users:
            raise SignInError(401, "Your session has expired. Please sign in again.")
        return users[token]


def auth_client(tmp_path, **kw):
    c = make_client(tmp_path, supabase_url="https://x.supabase.co", supabase_anon_key="anon", **kw)
    c.app.dependency_overrides[verifier_dep] = lambda: FakeVerifier()
    return c


def H(token):
    return {"Authorization": f"Bearer {token}"}


def test_status_exposes_anon_key_but_never_service_key(tmp_path):
    c = make_client(tmp_path, supabase_url="https://x.supabase.co", supabase_anon_key="anon-pub", supabase_service_role_key="svc-secret", ai_api_key="sk-secret", ai_model="m")
    text = c.get("/api/status").text
    assert "anon-pub" in text and "svc-secret" not in text and "sk-secret" not in text


def test_every_data_endpoint_requires_sign_in(tmp_path):
    c = auth_client(tmp_path)
    files = {"files": [{"path": "a.py", "content": "x=1"}]}
    calls = [
        ("get", "/api/projects", None), ("post", "/api/projects", {"name": "P", "language": "python"}),
        ("get", f"/api/projects/{UID_A}", None), ("put", f"/api/projects/{UID_A}", {"name": "Q"}), ("delete", f"/api/projects/{UID_A}", None),
        ("post", "/api/ai/generate", GEN_BODY), ("post", "/api/ai/edit", {"instruction": "do it", "language": "python", **files}),
        ("get", "/api/ai/usage", None), ("post", "/api/validate", files), ("post", "/api/export", files),
    ]
    for method, path, body in calls:
        assert c.request(method, path, json=body).status_code == 401, path
        assert c.request(method, path, json=body, headers=H("stolen-or-expired")).status_code == 401, path
    assert c.get("/api/status").status_code == 200


def test_users_only_see_their_own_projects(tmp_path):
    c = auth_client(tmp_path)
    pid = c.post("/api/projects", json={"name": "A's", "language": "python"}, headers=H("token-a")).json()["id"]
    assert [p["id"] for p in c.get("/api/projects", headers=H("token-a")).json()] == [pid]
    assert c.get("/api/projects", headers=H("token-b")).json() == []
    assert c.get(f"/api/projects/{pid}", headers=H("token-b")).status_code == 404
    assert c.put(f"/api/projects/{pid}", json={"name": "hacked"}, headers=H("token-b")).status_code == 404
    assert c.delete(f"/api/projects/{pid}", headers=H("token-b")).status_code == 404
    assert c.get(f"/api/projects/{pid}", headers=H("token-a")).json()["name"] == "A's"
    assert "owner_id" not in c.get(f"/api/projects/{pid}", headers=H("token-a")).json()


def test_require_auth_blocks_an_unconfigured_server(tmp_path):
    c = make_client(tmp_path, require_auth=True)
    r = c.get("/api/projects")
    assert r.status_code == 503 and "not set up" in r.json()["detail"]
    assert c.get("/api/status").json()["auth"] == {"enabled": False, "required": True, "supabase_url": None, "supabase_anon_key": None}


def test_ai_limits_are_per_user(tmp_path):
    c = auth_client(tmp_path, ai_text=GEN_REPLY, ai_api_key="k", ai_model="m", ai_limit_per_hour=2, ai_limit_per_day=10)
    for _ in range(2):
        assert c.post("/api/ai/generate", json=GEN_BODY, headers=H("token-a")).status_code == 201
    r = c.post("/api/ai/generate", json=GEN_BODY, headers=H("token-a"))
    assert r.status_code == 429 and "this hour" in r.json()["detail"] and "minute" in r.json()["detail"]
    edit = {"instruction": "do it", "language": "html", "files": []}
    assert c.post("/api/ai/edit", json=edit, headers=H("token-a")).status_code == 429
    assert c.get("/api/ai/usage", headers=H("token-a")).json()["hour"] == {"used": 2, "limit": 2}
    assert c.post("/api/ai/generate", json=GEN_BODY, headers=H("token-b")).status_code == 201
    assert len(c.get("/api/projects", headers=H("token-a")).json()) == 2


def test_daily_limit_and_unlimited(tmp_path):
    c = make_client(tmp_path, ai_text=GEN_REPLY, ai_api_key="k", ai_model="m", ai_limit_per_hour=0, ai_limit_per_day=1)
    assert c.post("/api/ai/generate", json=GEN_BODY).status_code == 201
    r = c.post("/api/ai/generate", json=GEN_BODY)
    assert r.status_code == 429 and "today" in r.json()["detail"]
    c = make_client(tmp_path, ai_text=GEN_REPLY, ai_api_key="k", ai_model="m", ai_limit_per_hour=0, ai_limit_per_day=0)
    assert all(c.post("/api/ai/generate", json=GEN_BODY).status_code == 201 for _ in range(3))


def test_unconnected_ai_does_not_use_allowance(tmp_path):
    c = make_client(tmp_path, ai_limit_per_hour=1)
    for _ in range(3):
        assert c.post("/api/ai/generate", json=GEN_BODY).status_code == 503
    assert c.get("/api/ai/usage").json()["hour"]["used"] == 0


async def test_supabase_verifier_checks_token_and_caches():
    calls = []

    def handler(request):
        calls.append((request.headers["apikey"], request.headers["authorization"], request.url.path))
        if request.headers["authorization"] == "Bearer good":
            return httpx.Response(200, json={"id": UID_A, "email": "a@example.com"})
        return httpx.Response(401, json={"msg": "invalid JWT"})

    v = SupabaseVerifier("https://x.supabase.co/", "anon", transport=httpx.MockTransport(handler))
    assert await v.verify("good") == User(UID_A, "a@example.com")
    assert await v.verify("good") == User(UID_A, "a@example.com")
    assert calls == [("anon", "Bearer good", "/auth/v1/user")]
    with pytest.raises(SignInError) as e:
        await v.verify("bad")
    assert e.value.status_code == 401
    down = SupabaseVerifier("https://x.supabase.co", "anon", transport=httpx.MockTransport(lambda r: httpx.Response(500)))
    with pytest.raises(SignInError) as e:
        await down.verify("good")
    assert e.value.status_code == 503


async def test_supabase_store_scopes_every_query_to_the_owner():
    seen = []

    def handler(request):
        seen.append((request.method, dict(request.url.params), json.loads(request.content) if request.content else None))
        return httpx.Response(200, json=[{"id": UID_B}])

    store = SupabaseStore("https://x.supabase.co", "svc", transport=httpx.MockTransport(handler))
    await store.list(UID_A)
    await store.get(UID_B, UID_A)
    await store.update(UID_B, {"name": "n"}, UID_A)
    await store.delete(UID_B, UID_A)
    assert all(params["owner_id"] == f"eq.{UID_A}" for _, params, _ in seen)
    await store.create({"name": "n", "files": []}, UID_A)
    assert seen[-1][2]["owner_id"] == UID_A
    await store.list(None)
    assert seen[-1][1]["owner_id"] == "is.null"
    with pytest.raises(Exception):
        await store.list("x,owner_id.neq.1")


async def test_supabase_usage_reads_and_records():
    seen = []

    def handler(request):
        seen.append((request.method, dict(request.url.params), json.loads(request.content) if request.content else None))
        return httpx.Response(200, json=[{"created_at": "2026-11-14T15:37:12.123456+00:00"}, {"created_at": "2026-11-14T15:00:00Z"}])

    usage = SupabaseUsage("https://x.supabase.co", "svc", transport=httpx.MockTransport(handler))
    events = await usage.recent(User(UID_A))
    assert len(events) == 2 and events[0].tzinfo is not None
    assert seen[0][1]["user_id"] == f"eq.{UID_A}" and seen[0][1]["created_at"].startswith("gte.")
    await usage.record(User(UID_A), "generate")
    assert seen[1][0] == "POST" and seen[1][2] == {"user_id": UID_A, "kind": "generate"}
