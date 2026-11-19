"""AI usage ledger: access controls, provider parsing, retention and cost safety."""
import csv
import io
import sqlite3
import httpx
import pytest
from cryptography.fernet import Fernet
from fastapi.testclient import TestClient

import ai_usage
import server

AUTH = {"Authorization": "Bearer " + "c" * 48}
NEW = {
    "name": "Work AI", "kind": "openai",
    "base_url": "https://api.openai.com/v1",
    "model": "test-model", "api_key": "do-not-log-this-secret",
}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "usage.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    monkeypatch.setattr(server, "OPENAI_API_KEY", "")
    monkeypatch.setattr(server, "DIFY_API_KEY", "")
    monkeypatch.setenv("RELAY_AI_ENCRYPTION_KEY", Fernet.generate_key().decode())
    monkeypatch.setattr(server.ai_profiles, "require_public_dns", lambda _: None)
    with TestClient(server.app) as app:
        yield app


def test_requires_client_auth(client):
    assert client.get("/v1/ai-usage").status_code == 401
    assert client.get("/v1/ai-usage/export").status_code == 401
    assert client.post("/v1/ai-usage/rates", json={}).status_code == 401
    assert client.get("/v1/ai-usage?days=999", headers=AUTH).status_code == 422
    assert client.get("/v1/ai-usage?days=30", headers=AUTH).status_code == 200


def test_extract_usage_across_providers():
    assert ai_usage.extract_usage("openai", {"usage": {
        "input_tokens": 8, "output_tokens": 4}}) == (8, 4)
    assert ai_usage.extract_usage("anthropic", {"usage": {
        "input_tokens": 12, "output_tokens": 5}}) == (12, 5)
    assert ai_usage.extract_usage("compatible", {"usage": {
        "prompt_tokens": 9, "completion_tokens": 6}}) == (9, 6)
    assert ai_usage.extract_usage("dify", {"metadata": {"usage": {
        "prompt_tokens": 4, "completion_tokens": 1}}}) == (4, 1)
    assert ai_usage.extract_usage("compatible", {"choices": []}) == (None, None)
    assert ai_usage.positive_tokens(-8) is None
    assert ai_usage.positive_tokens(True) is None


def test_profile_usage_summary_rates_and_csv_no_sensitive_data(client, monkeypatch):
    profile = client.post("/v1/ai-profiles", headers=AUTH, json=NEW).json()
    original = httpx.AsyncClient

    def handler(_):
        return httpx.Response(200, json={"output": [
            {"type": "message", "content": [
                {"type": "output_text", "text": "ANSWER_BODY"}
            ]}
        ], "usage": {"input_tokens": 300, "output_tokens": 120}})

    monkeypatch.setattr(server.httpx, "AsyncClient",
                        lambda **kw: original(transport=httpx.MockTransport(handler), **kw))
    for _ in range(2):
        res = client.post("/v1/chat", headers=AUTH, json={
            "provider": "openai", "profile_id": profile["id"],
            "message": "PROMPT_BODY"})
        assert res.status_code == 200

    result = client.get("/v1/ai-usage?days=30", headers=AUTH).json()
    assert result["timezone"] == "Asia/Tokyo"
    assert result["totals"]["requests"] == 2
    assert result["totals"]["input_tokens"] == 600
    assert result["totals"]["output_tokens"] == 240
    assert result["totals"]["estimated_usd"] is None
    assert result["totals"]["unpriced_models"] == 1
    assert result["models"][0]["unreported_calls"] == 0
    assert result["daily"][-1]["requests"] == 2
    assert "PROMPT_BODY" not in str(result) and "ANSWER_BODY" not in str(result)
    assert "do-not-log-this-secret" not in str(result)

    invalid = client.post("/v1/ai-usage/rates", headers=AUTH, json={
        "profile_id": profile["id"], "model": "test-model",
        "input_usd_per_million": 1.0})
    assert invalid.status_code == 422
    assert client.post("/v1/ai-usage/rates", headers=AUTH, json={
        "profile_id": profile["id"], "model": "test-model",
        "input_usd_per_million": 1.5, "output_usd_per_million": 6.0
    }).status_code == 200
    result = client.get("/v1/ai-usage", headers=AUTH).json()
    assert result["totals"]["estimated_usd"] == pytest.approx(0.00234)
    assert result["totals"]["unpriced_models"] == 0

    export = client.get("/v1/ai-usage/export?days=7", headers=AUTH)
    assert export.status_code == 200
    assert export.headers["content-type"].startswith("text/csv")
    assert export.text.count("test-model") == 1
    assert "PROMPT_BODY" not in export.text
    assert "ANSWER_BODY" not in export.text
    assert "do-not-log-this-secret" not in export.text
    with server.connection() as db:
        raw = db.execute("SELECT * FROM ai_usage").fetchall()
    assert len(raw) == 2
    assert "PROMPT_BODY" not in repr([tuple(x) for x in raw])

    # Deleting a provider does not delete historical usage.
    client.delete("/v1/ai-profiles/" + profile["id"], headers=AUTH)
    after = client.get("/v1/ai-usage", headers=AUTH).json()
    assert after["totals"]["requests"] == 2
    assert after["models"][0]["profile_name"] == "Work AI"


def test_missing_tokens_and_errors_never_show_zero_cost(client, monkeypatch):
    profile = client.post("/v1/ai-profiles", headers=AUTH, json=NEW).json()
    original = httpx.AsyncClient
    state = {"calls": 0}

    def handler(_):
        state["calls"] += 1
        if state["calls"] == 1:
            return httpx.Response(429, json={"error": "RATE_LIMIT"})
        return httpx.Response(200, json={"output": [
            {"type": "message", "content": [
                {"type": "output_text", "text": "hello"}
            ]}
        ]})
    monkeypatch.setattr(server.httpx, "AsyncClient",
                        lambda **kw: original(transport=httpx.MockTransport(handler), **kw))
    for expected in (502, 200):
        res = client.post("/v1/chat", headers=AUTH, json={
            "provider": "openai", "profile_id": profile["id"], "message": "hi"})
        assert res.status_code == expected
    client.post("/v1/ai-usage/rates", headers=AUTH, json={
        "profile_id": profile["id"], "model": "test-model",
        "input_usd_per_million": 0.0, "output_usd_per_million": 0.0})
    data = client.get("/v1/ai-usage", headers=AUTH).json()
    assert data["totals"]["requests"] == 2
    assert data["totals"]["error_count"] == 1
    assert data["totals"]["unreported_calls"] == 2
    assert data["totals"]["estimated_usd"] is None
    assert data["models"][0]["estimated_usd"] is None


def test_ledger_preserves_existing_tables_and_csv_formula_safety(tmp_path):
    db = tmp_path / "old.db"
    with sqlite3.connect(db) as con:
        con.execute("CREATE TABLE events (id INTEGER PRIMARY KEY, note TEXT)")
        con.execute("INSERT INTO events(note) VALUES ('persist')")
    ai_usage.initialize(db)
    ai_usage.initialize(db)
    ai_usage.record(db, profile_id="a" * 32, profile_name="=HYPERLINK(x)",
                    kind="compatible", model="+model", status="ok",
                    input_tokens=3, output_tokens=2, latency_ms=17)
    exported = ai_usage.csv_export(db, 30)
    assert "'=HYPERLINK" in exported
    assert "'+model" in exported
    with sqlite3.connect(db) as con:
        assert con.execute("SELECT note FROM events").fetchone()[0] == "persist"
