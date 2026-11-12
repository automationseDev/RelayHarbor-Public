"""Tests for portable, secret-free RelayHarbor connection backups."""
import pytest
from fastapi.testclient import TestClient

import server

CLIENT = {"Authorization": "Bearer " + "c" * 48}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "connections-transfer.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    with TestClient(server.app) as app:
        yield app


def add(client, name="Scout", provider="webhook", **kwargs):
    response = client.post("/v1/connections", headers=CLIENT, json={
        "name": name, "provider": provider,
        "channel_name": "AI案件スカウト", **kwargs
    })
    assert response.status_code == 201, response.text
    return response.json()


def test_transfer_requires_client_auth(client):
    backup = {"format": "relayharbor-integrations", "version": 1,
              "connections": []}
    assert client.get("/v1/connections/export").status_code == 401
    assert client.post("/v1/connections/import", json=backup).status_code == 401


def test_export_omits_all_credentials_and_restores_settings(client):
    secret = add(client, name="Scout", min_score=75, keyword="python")
    rss = add(client, name="Tech RSS", provider="rss",
              feed_url="https://example.com/feed.xml", poll_interval_minutes=60)
    assert secret["token"]
    exported = client.get("/v1/connections/export", headers=CLIENT)
    assert exported.status_code == 200
    doc = exported.json()
    assert doc["format"] == "relayharbor-integrations"
    assert doc["version"] == 1
    assert len(doc["connections"]) == 2
    content = exported.text
    for sensitive in ("secret_hash", "token", secret["token"], secret["id"],
                      "last_error", "last_success", "path"):
        assert sensitive not in content
    assert doc["connections"][0]["min_score"] == 75
    assert doc["connections"][1]["feed_url"] == "https://example.com/feed.xml"
    assert doc["connections"][1]["poll_interval_minutes"] == 60

    imported = client.post("/v1/connections/import", headers=CLIENT, json=doc)
    assert imported.status_code == 200
    assert imported.json()["skipped"] == 2
    assert imported.json()["created_count"] == 0


def test_import_generates_distinct_one_time_tokens(client):
    backup = {
        "format": "relayharbor-integrations", "version": 1,
        "connections": [
            {"name": "Lancers Scout", "provider": "webhook",
             "channel_name": "AI案件スカウト", "enabled": True,
             "min_score": 85, "keyword": ""},
            {"name": "Watch RSS", "provider": "rss",
             "channel_name": "ニュース", "enabled": False,
             "feed_url": "https://example.org/rss.xml",
             "poll_interval_minutes": 180}
        ]
    }
    response = client.post("/v1/connections/import", headers=CLIENT, json=backup)
    assert response.status_code == 200, response.text
    assert response.json()["created_count"] == 2
    entries = response.json()["created"]
    assert len(entries[0]["token"]) >= 32
    assert entries[1]["token"] == ""
    listed = client.get("/v1/connections", headers=CLIENT).json()["connections"]
    assert all("token" not in row for row in listed)
    assert any(r["name"] == "Watch RSS" and not r["enabled"] for r in listed)
    inbound = client.post(entries[0]["path"],
                          headers={"Authorization": "Bearer " + entries[0]["token"]},
                          json={"id": "scout-1", "text": "python",
                                "card": {"score": 93}})
    assert inbound.status_code == 201, inbound.text


def test_import_rejects_secrets_malformed_and_rolls_back(client):
    row = {"name": "Scout", "provider": "webhook", "channel_name": "AI"}
    bad = {"format": "relayharbor-integrations", "version": 1,
           "connections": [row, {**row, "token": "NEVER_EXPORTED"}]}
    response = client.post("/v1/connections/import", headers=CLIENT, json=bad)
    assert response.status_code == 422
    assert client.get("/v1/connections", headers=CLIENT).json()["connections"] == []

    bad["connections"][1] = {
        "name": "Bad RSS", "provider": "rss",
        "channel_name": "AI", "feed_url": "http://127.0.0.1/private"
    }
    response = client.post("/v1/connections/import", headers=CLIENT, json=bad)
    assert response.status_code == 422
    assert client.get("/v1/connections", headers=CLIENT).json()["connections"] == []


def test_import_is_atomic_and_respects_rss_limit(client, monkeypatch):
    import rss
    monkeypatch.setattr(rss, "MAX_SOURCES", 1)
    payload = {"format": "relayharbor-integrations", "version": 1,
               "connections": [
                   {"name": "A", "provider": "rss", "channel_name": "news",
                    "feed_url": "https://example.com/a"},
                   {"name": "B", "provider": "rss", "channel_name": "news",
                    "feed_url": "https://example.com/b"}
               ]}
    result = client.post("/v1/connections/import", headers=CLIENT, json=payload)
    assert result.status_code == 422
    assert not client.get("/v1/connections", headers=CLIENT).json()["connections"]


def test_reimport_ignores_duplicates_even_within_same_backup(client):
    row = {"name": "Scout", "provider": "webhook", "channel_name": "AI"}
    payload = {"format": "relayharbor-integrations", "version": 1,
               "connections": [row, row]}
    first = client.post("/v1/connections/import", headers=CLIENT, json=payload)
    assert first.status_code == 200
    assert first.json()["created_count"] == 1
    assert first.json()["skipped"] == 1
