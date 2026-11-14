"""RSS-free website checks: no external network or browser required."""
import pytest
from fastapi.testclient import TestClient

import server
import website_monitor

CLIENT = {"Authorization": "Bearer " + "c" * 48}
OLD = b"""<!doctype html><html><head><title>News</title>
<script>Math.random()</script></head><body><nav>Clock 09:00</nav>
<main id="news"><h1>Announcements</h1><p>Version 1 published</p></main>
<footer>Generated daily</footer></body></html>"""
NEW = OLD.replace(b"Version 1 published", b"Version 2 available")
AD_ONLY = OLD.replace(b"Clock 09:00", b"Clock 10:00")


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "website.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    with TestClient(server.app) as c:
        yield c


def add(client, **kwargs):
    response = client.post("/v1/connections", headers=CLIENT, json={
        "name": "ニュース速報", "provider": "website", "channel_name": "Web更新",
        "feed_url": "https://example.org/news", "poll_interval_minutes": 30,
        **kwargs
    })
    assert response.status_code == 201, response.text
    return response.json()


def test_website_create_rejects_private_urls_and_bad_selectors(client):
    assert client.get("/v1/connections").status_code == 401
    created = add(client, css_selector="#news")
    assert created["path"] == ""
    assert created["css_selector"] == "#news"
    assert created["poll_interval_minutes"] == 30
    assert "token" not in created
    assert client.post("/v1/connections/" + created["id"] + "/rotate",
                       headers=CLIENT).status_code == 400
    assert client.post("/v1/inbound/" + created["id"], json={"text": "x"},
                       headers=CLIENT).status_code == 404
    for url in ["http://example.org", "https://localhost/", "https://10.1.1.1/",
                "https://127.0.0.1/", "https://nas.local/", "https://example.org:8080/",
                "https://test:pass@example.org/"]:
        result = client.post("/v1/connections", headers=CLIENT, json={
            "name": "bad", "provider": "website", "channel_name": "bad",
            "feed_url": url
        })
        assert result.status_code == 422, (url, result.text)
    assert client.post("/v1/connections", headers=CLIENT, json={
        "name": "bad", "provider": "website", "channel_name": "bad",
        "feed_url": "https://example.org/", "css_selector": "main["
    }).status_code == 422
    assert client.post("/v1/connections", headers=CLIENT, json={
        "name": "bad", "provider": "website", "channel_name": "bad",
        "feed_url": "https://example.org/", "poll_interval_minutes": 1
    }).status_code == 422


def test_first_fetch_is_baseline_and_second_emits_one_clickable_card(client, monkeypatch):
    page = {"body": OLD}
    async def fake_download(url, etag="", modified=""):
        assert url == "https://example.org/news"
        return page["body"], "", ""
    monkeypatch.setattr(website_monitor, "_download", fake_download)
    created = add(client)
    endpoint = "/v1/connections/" + created["id"] + "/refresh"
    first = client.post(endpoint, headers=CLIENT)
    assert first.status_code == 200, first.text
    assert first.json()["new"] == 0
    assert client.get("/v1/events", headers=CLIENT).json()["events"] == []
    page["body"] = NEW
    second = client.post(endpoint, headers=CLIENT)
    assert second.status_code == 200, second.text
    assert second.json()["new"] == 1
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 1
    assert events[0]["channel"] == "Web更新"
    assert events[0]["card"]["badge"] == "WEB更新"
    assert events[0]["card"]["url"] == "https://example.org/news"
    assert "Version 2" in events[0]["card"]["description"]
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0
    assert len(client.get("/v1/events", headers=CLIENT).json()["events"]) == 1


def test_css_scope_ignores_navigation_changes_and_url_edit_resets_baseline(client, monkeypatch):
    page = {"body": OLD}
    async def fake_download(url, etag="", modified=""):
        return page["body"], "", ""
    monkeypatch.setattr(website_monitor, "_download", fake_download)
    created = add(client, css_selector="#news")
    cid = created["id"]
    endpoint = "/v1/connections/" + cid + "/refresh"
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0
    page["body"] = AD_ONLY
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0
    page["body"] = NEW
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 1
    updated = client.post("/v1/connections/" + cid + "/update", headers=CLIENT,
                          json={"feed_url": "https://example.org/updates",
                                "css_selector": "main",
                                "poll_interval_minutes": 60})
    assert updated.status_code == 200, updated.text
    assert updated.json()["css_selector"] == "main"
    assert updated.json()["last_success"] == 0
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0


def test_keyword_filter_and_failed_css_leave_source_retryable(client, monkeypatch):
    page = {"body": OLD}
    async def fake_download(url, etag="", modified=""):
        return page["body"], "etag-v1", ""
    monkeypatch.setattr(website_monitor, "_download", fake_download)
    created = add(client, css_selector="#news", keyword="Important")
    endpoint = "/v1/connections/" + created["id"] + "/refresh"
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0
    page["body"] = NEW
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0
    page["body"] = NEW.replace(b"Version 2", b"Important Version 3")
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 1
    err = client.post("/v1/connections/" + created["id"] + "/update",
        headers=CLIENT, json={"css_selector": ".missing"})
    assert err.status_code == 200
    assert client.post(endpoint, headers=CLIENT).status_code == 502
    details = client.get("/v1/connections", headers=CLIENT).json()["connections"][0]
    assert "did not match" in details["last_error"]
    client.post("/v1/connections/" + created["id"] + "/update",
        headers=CLIENT, json={"css_selector": "#news"})
    assert client.post(endpoint, headers=CLIENT).json()["new"] == 0


def test_html_parser_excludes_scripts_and_rejects_empty_regions():
    first = website_monitor.extract_text(OLD)
    other = website_monitor.extract_text(AD_ONLY)
    assert first == other
    assert "Math.random" not in first
    assert "Clock" not in first
    assert "Version 1" in first
    assert website_monitor.extract_text(OLD, "#news") == "Announcements\nVersion 1 published"
    with pytest.raises(ValueError, match="did not match"):
        website_monitor.extract_text(OLD, "#missing")
    with pytest.raises(ValueError, match="readable"):
        website_monitor.extract_text(b"<html><script>secret()</script></html>")
    assert website_monitor.validate_selector("main > p") == "main > p"
    with pytest.raises(ValueError):
        website_monitor.validate_selector("div[")


def test_export_and_import_preserve_monitor_settings_without_secrets(client):
    created = add(client, css_selector="main > p", poll_interval_minutes=60)
    exported = client.get("/v1/connections/export", headers=CLIENT)
    assert exported.status_code == 200, exported.text
    backup = exported.json()
    row = backup["connections"][0]
    assert row["provider"] == "website"
    assert row["css_selector"] == "main > p"
    assert row["feed_url"] == "https://example.org/news"
    assert row["poll_interval_minutes"] == 60
    assert created["id"] not in exported.text
    assert "secret_hash" not in exported.text
    repeated = client.post("/v1/connections/import", headers=CLIENT, json=backup)
    assert repeated.status_code == 200, repeated.text
    assert repeated.json()["skipped"] == 1
    assert repeated.json()["created_count"] == 0
    modified = backup.copy()
    modified["connections"] = [{**row, "name": "監視2", "feed_url": "https://example.net/new"}]
    imported = client.post("/v1/connections/import", headers=CLIENT, json=modified)
    assert imported.status_code == 200, imported.text
    assert imported.json()["created_count"] == 1
    assert imported.json()["created"][0]["token"] == ""
