import pytest
from fastapi.testclient import TestClient

import server


CLIENT = {"Authorization": "Bearer " + "c" * 48}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(server, "DATABASE", tmp_path / "slack.db")
    monkeypatch.setattr(server, "CLIENT_TOKEN", "c" * 48)
    monkeypatch.setattr(server, "WEBHOOK_TOKEN", "w" * 48)
    with TestClient(server.app) as app:
        yield app


def test_slack_blocks_to_card_with_bearer_and_idempotency(client):
    connection = client.post("/v1/connections", headers=CLIENT, json={
        "name": "Lancers AI Scout", "provider": "webhook",
        "channel_name": "AI案件スカウト"
    }).json()
    payload = {
        "id": "lancers-job123",
        "text": "Python自動化案件が見つかりました",
        "blocks": [
            {"type": "header", "text": {
                "type": "plain_text", "text": "Python自動化"}},
            {"type": "section", "fields": [
                {"type": "mrkdwn", "text": "*予算*: 50,000円"},
                {"type": "mrkdwn", "text": "*Dify*: 高評価"}
            ]},
            {"type": "actions", "elements": [
                {"type": "button", "text": {"text": "案件を開く"},
                 "url": "https://www.lancers.jp/work/detail/123"}
            ]}
        ],
    }
    path = connection["path"]
    assert client.post(path, json=payload).status_code == 401
    ingress = {"Authorization": "Bearer " + connection["token"]}
    response = client.post(path, headers=ingress, json=payload)
    assert response.status_code == 201
    assert response.json()["duplicate"] is False
    assert client.post(path, headers=ingress, json=payload).json()["duplicate"] is True
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 1
    assert events[0]["channel"] == "AI案件スカウト"
    assert events[0]["card"]["title"] == "Python自動化"
    assert "50,000" in events[0]["card"]["description"]
    assert events[0]["card"]["url"] == "https://www.lancers.jp/work/detail/123"


def test_blocks_without_text_and_oversized_payload_rejected(client):
    connection = client.post("/v1/connections", headers=CLIENT, json={
        "name": "Scout", "channel_name": "AI"
    }).json()
    auth = {"Authorization": "Bearer " + connection["token"]}
    assert client.post(connection["path"], headers=auth,
                       json={"blocks": [{"type": "section", "text":
                             {"text": "新規案件"}}]}).status_code == 201
    assert client.post(connection["path"], headers=auth,
                       json={"blocks": []}).status_code == 422
    assert client.post(connection["path"], headers=auth,
                       json={"blocks": [{"type": "section", "text": {
                           "text": "X" * 70000}}]}).status_code == 413
