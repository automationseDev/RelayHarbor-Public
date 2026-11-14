import httpx
import pytest
from fastapi.testclient import TestClient

import server


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "v04.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    with TestClient(server.app) as app:
        yield app


CLIENT = {"Authorization": "Bearer " + "c" * 48}
LEGACY = {"Authorization": "Bearer " + "w" * 48}


def add(client, **extra):
    payload = {
        "name": "GitHub Actions", "provider": "github_actions",
        "channel_name": "CI通知", **extra,
    }
    res = client.post("/v1/connections", headers=CLIENT, json=payload)
    assert res.status_code == 201, res.text
    return res.json()


def test_connection_auth_create_list_and_secret_not_leaked(client):
    assert client.get("/v1/connections").status_code == 401
    assert client.post("/v1/connections", json={
        "name": "x", "channel_name": "y"
    }).status_code == 401
    created = add(client)
    assert len(created["token"]) >= 32
    assert created["path"] == "/v1/inbound/" + created["id"]
    listed = client.get("/v1/connections", headers=CLIENT)
    assert listed.status_code == 200
    item = listed.json()["connections"][0]
    assert "token" not in item and "secret_hash" not in item
    assert item["name"] == "GitHub Actions"
    assert item["channel_name"] == "CI通知"


def test_webhook_rules_dedup_and_no_global_token_access(client):
    connection = add(client, min_score=85, keyword="build")
    route = connection["path"]
    inbound = {"Authorization": "Bearer " + connection["token"]}
    assert client.post(route, json={"text": "build"}).status_code == 401
    assert client.post(route, headers=LEGACY, json={"text": "build"}).status_code == 401
    filtered = client.post(route, headers=inbound, json={
        "text": "build", "card": {"score": 60}
    })
    assert filtered.status_code == 201
    assert filtered.json()["filtered"] is True
    filtered = client.post(route, headers=inbound, json={
        "text": "deploy", "card": {"score": 99}
    })
    assert filtered.json()["filtered"] is True

    payload = {"id": "event-1", "sender": "Bot", "text": "build succeeded",
               "channel_name": "Untrusted channel name",
               "card": {"score": 97, "title": "CI"}}
    created = client.post(route, headers=inbound, json=payload)
    assert created.status_code == 201
    assert created.json()["duplicate"] is False
    again = client.post(route, headers=inbound, json=payload)
    assert again.status_code == 201
    assert again.json()["duplicate"] is True
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 1
    assert events[0]["channel"] == "CI通知"
    assert events[0]["card"]["score"] == 97


def test_test_notification_edit_disable_rotate_delete(client):
    created = add(client, min_score=100, keyword="release")
    route = created["path"]
    tested = client.post("/v1/connections/" + created["id"] + "/test", headers=CLIENT)
    assert tested.status_code == 201
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 1
    assert "release" in events[0]["text"]

    patched = client.patch("/v1/connections/" + created["id"], headers=CLIENT,
                           json={"enabled": False, "channel_name": "CI重要"})
    assert patched.status_code == 200
    assert patched.json()["enabled"] is False
    assert client.post(route, headers={"Authorization": "Bearer " + created["token"]},
                       json={"text": "release", "card": {"score": 100}}).status_code == 403
    client.patch("/v1/connections/" + created["id"], headers=CLIENT, json={"enabled": True})
    rotated = client.post("/v1/connections/" + created["id"] + "/rotate", headers=CLIENT)
    assert rotated.status_code == 200
    assert rotated.json()["token"] != created["token"]
    assert client.post(route, headers={"Authorization": "Bearer " + created["token"]},
                       json={"text": "release", "card": {"score": 100}}).status_code == 401
    assert client.post(route, headers={"Authorization": "Bearer " + rotated.json()["token"]},
                       json={"text": "release", "card": {"score": 100}}).status_code == 201
    assert client.delete("/v1/connections/" + created["id"], headers=CLIENT).status_code == 204
    assert client.post(route, headers={"Authorization": "Bearer " + rotated.json()["token"]},
                       json={"text": "release"}).status_code == 404
    assert len(client.get("/v1/events", headers=CLIENT).json()["events"]) == 2


def test_validation_and_legacy_compatibility(client):
    assert client.post("/v1/connections", headers=CLIENT, json={
        "name": "", "channel_name": "CI"
    }).status_code == 422
    assert client.post("/v1/connections", headers=CLIENT, json={
        "name": "a", "channel_name": "CI", "min_score": 101
    }).status_code == 422
    created = add(client)
    assert client.patch("/v1/connections/" + created["id"], headers=CLIENT,
                        json={"name": None}).status_code == 422
    assert client.patch("/v1/connections/" + created["id"], headers=CLIENT,
                        json={"enabled": "unsupported"}).status_code == 422
    assert client.patch("/v1/connections/" + created["id"], headers=CLIENT,
                        json={"keyword": "test"}).status_code == 200
    assert client.post("/v1/webhooks/github-actions", headers=LEGACY,
                       json={"text": "Legacy works"}).status_code == 201
    assert client.get("/healthz").json()["version"] == "0.6.0"
