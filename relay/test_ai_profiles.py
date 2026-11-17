import httpx
import pytest
from cryptography.fernet import Fernet
from fastapi.testclient import TestClient

import ai_profiles
import server


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "ai-test.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    monkeypatch.setattr(server, "OPENAI_API_KEY", "")
    monkeypatch.setattr(server, "DIFY_API_KEY", "")
    monkeypatch.setenv("RELAY_AI_ENCRYPTION_KEY", Fernet.generate_key().decode())
    monkeypatch.setattr(ai_profiles, "require_public_dns", lambda url: None)
    with TestClient(server.app) as app:
        yield app


AUTH = {"Authorization": "Bearer " + "c" * 48}
NEW = {
    "name": "OpenAI Work", "kind": "openai",
    "base_url": "https://api.openai.com/v1",
    "model": "gpt-4.1-mini", "api_key": "secret-openai-key"
}


def test_multiple_profiles_crud_and_encryption(client):
    assert client.get("/v1/ai-profiles").status_code == 401
    assert client.post("/v1/ai-profiles", json=NEW).status_code == 401
    a = client.post("/v1/ai-profiles", headers=AUTH, json=NEW)
    assert a.status_code == 201
    first_id = a.json()["id"]
    b = client.post("/v1/ai-profiles", headers=AUTH, json=dict(NEW,
        name="Gemini-compatible", kind="compatible",
        base_url="https://generativelanguage.googleapis.com/v1beta/openai",
        model="gemini-2.5-flash", api_key="secret-gemini-key"))
    assert b.status_code == 201
    listed = client.get("/v1/ai-profiles", headers=AUTH).json()["profiles"]
    assert len(listed) == 2
    assert all("api_key" not in entry for entry in listed)
    with server.connection() as db:
        rows = db.execute("SELECT secret_ciphertext FROM ai_profiles").fetchall()
        assert b"secret-openai-key" not in rows[0]["secret_ciphertext"]
        assert b"secret-gemini-key" not in rows[1]["secret_ciphertext"]
    updated = client.patch("/v1/ai-profiles/" + first_id,
        headers=AUTH, json=dict(NEW, name="Renamed", api_key=""))
    assert updated.status_code == 200
    assert ai_profiles.read_profile(server.DATABASE, first_id)["api_key"] == "secret-openai-key"
    assert client.delete("/v1/ai-profiles/" + first_id, headers=AUTH).status_code == 200
    assert len(client.get("/v1/ai-profiles", headers=AUTH).json()["profiles"]) == 1


def test_provider_url_block_and_missing_encryption_config(client, monkeypatch):
    for url in ["http://api.openai.com/v1", "https://localhost/v1",
                "https://127.0.0.1/v1", "https://10.0.0.1/v1",
                "https://user:pass" + chr(64) + "api.openai.com/v1", "https://api.openai.com:8080/v1",
                "https://metadata.google.internal/v1"]:
        assert client.post("/v1/ai-profiles", headers=AUTH,
                           json=dict(NEW, base_url=url)).status_code == 422, url
    monkeypatch.delenv("RELAY_AI_ENCRYPTION_KEY")
    response = client.post("/v1/ai-profiles", headers=AUTH, json=NEW)
    assert response.status_code == 503


def test_dispatch_distinct_models_and_legacy_route(client, monkeypatch):
    one = client.post("/v1/ai-profiles", headers=AUTH, json=NEW).json()["id"]
    two = client.post("/v1/ai-profiles", headers=AUTH, json=dict(NEW,
        name="Compatible", kind="compatible", model="test-compatible",
        api_key="other-key")).json()["id"]
    original = httpx.AsyncClient
    def handler(request):
        import json
        payload = json.loads(request.content)
        if request.url.path.endswith("/responses"):
            assert request.headers["Authorization"] == "Bearer secret-openai-key"
            assert payload["model"] == "gpt-4.1-mini"
            return httpx.Response(200, json={"output": [{"type": "message", "content": [
                {"type": "output_text", "text": "A"}]}]})
        assert request.url.path.endswith("/chat/completions")
        assert request.headers["Authorization"] == "Bearer other-key"
        assert payload["model"] == "test-compatible"
        return httpx.Response(200, json={"choices": [{"message": {"content": "B"}}]})
    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(server.httpx, "AsyncClient",
                        lambda **kw: original(transport=transport, **kw))
    for profile, expected in [(one, "A"), (two, "B")]:
        out = client.post("/v1/chat", headers=AUTH,
            json={"provider": "openai", "profile_id": profile, "message": "hello"})
        assert out.status_code == 200, out.text
        assert out.json()["answer"] == expected
    assert client.post("/v1/chat", headers=AUTH, json={
        "provider": "openai", "profile_id": "bad", "message": "hi"
    }).status_code == 404


def test_no_provider_secret_leak_and_mismatched_key(client, monkeypatch):
    pid = client.post("/v1/ai-profiles", headers=AUTH, json=NEW).json()["id"]
    monkeypatch.setenv("RELAY_AI_ENCRYPTION_KEY", Fernet.generate_key().decode())
    res = client.post("/v1/chat", headers=AUTH,
        json={"provider": "openai", "profile_id": pid, "message": "hello"})
    assert res.status_code == 503
    assert "secret-openai-key" not in res.text
