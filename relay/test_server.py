import httpx
import pytest
from fastapi.testclient import TestClient

import server


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "events.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    monkeypatch.setattr(server, "DIFY_API_KEY", "")
    monkeypatch.setattr(server, "OPENAI_API_KEY", "")
    with TestClient(server.app) as app:
        yield app


def test_health_and_authorization(client):
    assert client.get("/healthz").status_code == 200
    assert client.get("/v1/events").status_code == 401
    assert client.post("/v1/webhooks/scout", json={"text": "x"}).status_code == 401
    assert client.get("/v1/events", headers={"Authorization": "Bearer wrong"}).status_code == 401


def test_webhook_deduplication_and_polling(client):
    headers = {"Authorization": "Bearer " + "w" * 48}
    payload = {
        "id": "lancers-job-001",
        "channel_name": "AI案件スカウト",
        "sender": "Lancers Scout",
        "text": "案件を検知しました",
        "card": {
            "schemaVersion": 1,
            "title": "自動化案件",
            "description": "Python",
            "score": 92,
            "badge": "高適合",
            "metrics": [{"label": "技術適合", "percent": 94}],
        },
    }
    created = client.post("/v1/webhooks/ai-scout", headers=headers, json=payload)
    assert created.status_code == 201
    assert not created.json()["duplicate"]
    duplicate = client.post("/v1/webhooks/ai-scout", headers=headers, json=payload)
    assert duplicate.status_code == 201
    assert duplicate.json()["duplicate"]
    assert duplicate.json()["sequence"] == created.json()["sequence"]

    response = client.get("/v1/events?after=0&limit=100",
                          headers={"Authorization": "Bearer " + "c" * 48})
    assert response.status_code == 200
    events = response.json()["events"]
    assert len(events) == 1
    assert events[0]["channel"] == "AI案件スカウト"
    assert events[0]["card"]["score"] == 92
    assert response.json()["next_cursor"] == created.json()["sequence"]
    empty = client.get(f"/v1/events?after={created.json()['sequence']}",
                       headers={"Authorization": "Bearer " + "c" * 48})
    assert empty.json()["events"] == []


def test_bad_webhooks_and_pagination(client):
    headers = {"Authorization": "Bearer " + "w" * 48}
    assert client.post("/v1/webhooks/$$$", headers=headers,
                       json={"text": "hi"}).status_code == 400
    assert client.post("/v1/webhooks/test", headers=headers,
                       json={"text": ""}).status_code == 422
    assert client.post("/v1/webhooks/test",
                       content=b"hello", headers={"Authorization": "Bearer " + "w" * 48,
                                                 "Content-Type": "text/plain"}).status_code == 415
    assert client.get("/v1/events?after=-1", headers={
        "Authorization": "Bearer " + "c" * 48
    }).status_code == 400


def test_ai_unconfigured(client):
    res = client.post("/v1/chat", headers={"Authorization": "Bearer " + "c" * 48},
                      json={"provider": "openai", "message": "こんにちは"})
    assert res.status_code == 503


def test_openai_proxy_response(client, monkeypatch):
    monkeypatch.setattr(server, "OPENAI_API_KEY", "test-provider-key")
    monkeypatch.setattr(server, "OPENAI_MODEL", "test-model")
    original_client = httpx.AsyncClient

    def handler(request):
        assert request.url.path == "/v1/responses"
        assert request.headers["Authorization"] == "Bearer test-provider-key"
        assert request.read()  # text input must be present
        return httpx.Response(200, json={"output": [
            {"type": "message", "content": [{"type": "output_text", "text": "応答"}]}
        ]})

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(server.httpx, "AsyncClient",
                        lambda **kwargs: original_client(transport=transport, **kwargs))
    res = client.post("/v1/chat", headers={"Authorization": "Bearer " + "c" * 48},
                      json={"provider": "openai", "message": "こんにちは",
                            "history": [{"role": "user", "content": "前の質問"}]})
    assert res.status_code == 200
    assert res.json()["answer"] == "応答"
