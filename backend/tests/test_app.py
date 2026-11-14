import io
import json
import zipfile

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.deps import ai_dep, settings_dep
from app.main import create_app
from app.schemas import ProjectFile, sanitize_path
from app.services import ai as ai_service
from app.services.ai import AIError, OpenAICompatibleClient
from app.services.validator import validate_files


def make_client(tmp_path, ai_text=None, **overrides):
    settings = Settings(_env_file=None, local_data_dir=str(tmp_path), frontend_dist=str(tmp_path / "none"), **overrides)
    app = create_app()
    app.dependency_overrides[settings_dep] = lambda: settings
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
