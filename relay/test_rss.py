"""RSS integration tests: no external network calls."""
from fastapi.testclient import TestClient
import httpx
import pytest

import rss
import server

CLIENT = {"Authorization": "Bearer " + "c" * 48}
SAMPLE = b"""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel><title>Automation News</title>
<item><guid>post-5</guid><title>AI New release</title><link>https://news.example.com/5</link><description>New AI announcement</description></item>
<item><guid>post-4</guid><title>AI Tips</title><link>https://news.example.com/4</link><description>Python automation</description></item>
<item><guid>post-3</guid><title>AI Tools</title><link>https://news.example.com/3</link><description>Tech tools</description></item>
<item><guid>post-2</guid><title>AI Older</title><link>https://news.example.com/2</link><description>Older tech</description></item>
<item><guid>post-1</guid><title>AI Oldest</title><link>https://news.example.com/1</link><description>Oldest tech</description></item>
</channel></rss>"""


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "rss.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    with TestClient(server.app) as app:
        yield app


def new_rss(client, **changes):
    data = {
        "provider": "rss",
        "name": "Technology news",
        "channel_name": "Tech",
        "feed_url": "https://news.example.com/feed.xml",
        "poll_interval_minutes": 30,
        **changes,
    }
    response = client.post("/v1/connections", headers=CLIENT, json=data)
    assert response.status_code == 201, response.text
    return response.json()


def test_create_rss_and_private_url_rejection(client):
    assert client.get("/v1/connections").status_code == 401
    created = new_rss(client)
    assert created["provider"] == "rss"
    assert created["feed_url"] == "https://news.example.com/feed.xml"
    assert created["poll_interval_minutes"] == 30
    assert created["path"] == ""
    assert "token" not in created
    listed = client.get("/v1/connections", headers=CLIENT).json()["connections"]
    assert listed[0]["feed_url"] == created["feed_url"]
    for url in (
        "http://news.example.com/rss.xml",
        "https://127.0.0.1/rss",
        "https://localhost/rss",
        "https://nas.local/rss",
        "https://user:pass@news.example.com/rss",
        "https://news.example.com:8080/rss",
        "https://news.example.com/rss#fragment",
        "https://192.168.1.10/rss",
    ):
        response = client.post("/v1/connections", headers=CLIENT, json={
            "provider": "rss", "name": "bad", "channel_name": "bad",
            "feed_url": url,
        })
        assert response.status_code == 422, (url, response.text)
    assert client.post("/v1/connections", headers=CLIENT, json={
        "provider": "rss", "name": "x", "channel_name": "x"
    }).status_code == 422
    assert client.post("/v1/connections", headers=CLIENT, json={
        "provider": "rss", "name": "x", "channel_name": "x",
        "feed_url": "https://news.example.com/rss", "min_score": 50
    }).status_code == 422
    assert client.post("/v1/connections", headers=CLIENT, json={
        "provider": "rss", "name": "x", "channel_name": "x",
        "feed_url": "https://news.example.com/rss", "poll_interval_minutes": 1
    }).status_code == 422


def test_rss_first_sync_next_sync_and_article_link(client, monkeypatch):
    state = {"content": SAMPLE}

    async def fake_download(url, etag="", modified=""):
        assert url.startswith("https://")
        return state["content"], "etag-1", "Wed, 11 Nov 2026 10:00:00 GMT"

    monkeypatch.setattr(rss, "_download", fake_download)
    created = new_rss(client)
    path = f"/v1/connections/{created['id']}/refresh"
    first = client.post(path, headers=CLIENT)
    assert first.status_code == 200, first.text
    assert first.json()["new"] == 3
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 3
    assert events[0]["channel"] == "Tech"
    assert events[0]["sender"] == "Automation News"
    assert events[0]["card"]["url"] == "https://news.example.com/5"
    assert client.post(path, headers=CLIENT).json()["new"] == 0
    assert len(client.get("/v1/events", headers=CLIENT).json()["events"]) == 3

    state["content"] = SAMPLE.replace(
        b"<item><guid>post-5", b'<item><guid>post-6</guid><title>Brand New</title><link>https://news.example.com/6</link><description>Fresh</description></item><item><guid>post-5', 1
    )
    latest = client.post(path, headers=CLIENT)
    assert latest.status_code == 200
    assert latest.json()["new"] == 1
    assert len(client.get("/v1/events", headers=CLIENT).json()["events"]) == 4


def test_rss_keyword_feed_edit_disable_and_delete(client, monkeypatch):
    async def fake_download(url, etag="", modified=""):
        return SAMPLE, "", ""
    monkeypatch.setattr(rss, "_download", fake_download)
    created = new_rss(client, keyword="Python")
    cid = created["id"]
    path = f"/v1/connections/{cid}/refresh"
    assert client.post(path, headers=CLIENT).json()["new"] == 1
    update = client.post(f"/v1/connections/{cid}/update", headers=CLIENT, json={
        "feed_url": "https://feeds.example.org/new", "poll_interval_minutes": 60,
        "keyword": "AI",
    })
    assert update.status_code == 200, update.text
    assert update.json()["feed_url"] == "https://feeds.example.org/new"
    assert update.json()["poll_interval_minutes"] == 60
    assert client.post(path, headers=CLIENT).json()["new"] == 3
    assert client.post(f"/v1/connections/{cid}/rotate", headers=CLIENT).status_code == 400
    assert client.post(f"/v1/inbound/{cid}", headers={"Authorization": "Bearer token"}, json={
        "text": "not valid"
    }).status_code == 404
    assert client.patch(f"/v1/connections/{cid}", headers=CLIENT,
                        json={"enabled": False}).status_code == 200
    assert client.post(path, headers=CLIENT).status_code == 403
    assert client.delete(f"/v1/connections/{cid}", headers=CLIENT).status_code == 204
    assert client.post(path, headers=CLIENT).status_code == 404


def test_rss_error_reported_and_retry(client, monkeypatch):
    async def fail(url, etag="", modified=""):
        raise ValueError("Invalid feed for testing")
    monkeypatch.setattr(rss, "_download", fail)
    created = new_rss(client)
    path = f"/v1/connections/{created['id']}/refresh"
    assert client.post(path, headers=CLIENT).status_code == 502
    listed = client.get("/v1/connections", headers=CLIENT).json()["connections"][0]
    assert "Invalid feed" in listed["last_error"]
    assert listed["last_checked"] > 0

    async def ok(url, etag="", modified=""):
        return SAMPLE, "", ""
    monkeypatch.setattr(rss, "_download", ok)
    assert client.post(path, headers=CLIENT).json()["new"] == 3
    listed = client.get("/v1/connections", headers=CLIENT).json()["connections"][0]
    assert listed["last_error"] == ""
    assert listed["last_success"] > 0


def test_rss_https_redirect_validates_each_hop(client, monkeypatch):
    checked = []

    async def safe_dns(url):
        checked.append(url)

    monkeypatch.setattr(rss, "_check_public_dns", safe_dns)
    original_client = httpx.AsyncClient

    def handler(request):
        if request.url.path == "/old":
            return httpx.Response(302, headers={"Location": "/feed.xml"})
        if request.url.path == "/unsafe":
            return httpx.Response(302, headers={"Location": "https://127.0.0.1/rss"})
        return httpx.Response(200, content=SAMPLE)

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(
        rss.httpx, "AsyncClient",
        lambda **kwargs: original_client(transport=transport, **kwargs)
    )

    created = new_rss(client, feed_url="https://news.example.com/old")
    url = f"/v1/connections/{created['id']}/refresh"
    good = client.post(url, headers=CLIENT)
    assert good.status_code == 200, good.text
    assert good.json()["new"] == 3
    assert checked == [
        "https://news.example.com/old", "https://news.example.com/feed.xml"
    ]

    client.post(f"/v1/connections/{created['id']}/update", headers=CLIENT,
                json={"feed_url": "https://news.example.com/unsafe"})
    bad = client.post(url, headers=CLIENT)
    assert bad.status_code == 502, bad.text
    assert "IP addresses" in bad.json()["detail"]
