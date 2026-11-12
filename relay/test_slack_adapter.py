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


def test_slack_image_block_and_webhook_top_level_image_are_displayable(client):
    connection = client.post("/v1/connections", headers=CLIENT, json={
        "name": "Scout", "provider": "webhook", "channel_name": "Jobs"
    }).json()
    auth = {"Authorization": "Bearer " + connection["token"]}
    payload = {
        "id": "img-block-1", "text": "写真あり",
        "blocks": [
            {"type": "header", "text": {"text": "案件画像"}},
            {"type": "image", "image_url": "https://cdn.example.com/job-cover.jpg",
             "alt_text": "Job cover"}
        ]
    }
    assert client.post(connection["path"], headers=auth, json=payload).status_code == 201
    payload2 = {
        "id": "img-flat-2", "sender": "Scout",
        "text": "2件目", "thumbnail_url": "https://cdn.example.com/two.webp"
    }
    assert client.post(connection["path"], headers=auth, json=payload2).status_code == 201
    payload3 = {
        "id": "img-nested-3", "sender": "Scout",
        "text": "3件目", "card": {
            "title": "既存カード", "image_url": "https://cdn.example.com/three.png"
        }
    }
    assert client.post(connection["path"], headers=auth, json=payload3).status_code == 201

    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert len(events) == 3
    assert events[0]["card"]["thumbnail_url"] == "https://cdn.example.com/job-cover.jpg"
    assert events[1]["card"]["thumbnail_url"] == "https://cdn.example.com/two.webp"
    assert events[2]["card"]["image_url"] == "https://cdn.example.com/three.png"


def test_image_metadata_rejects_private_targets_before_sending_to_android(client):
    connection = client.post("/v1/connections", headers=CLIENT, json={
        "name": "Scout", "channel_name": "Images"
    }).json()
    auth = {"Authorization": "Bearer " + connection["token"]}
    assert client.post(connection["path"], headers=auth, json={
        "text": "Plain text", "image_url": "http://127.0.0.1/secret"
    }).status_code == 201
    events = client.get("/v1/events", headers=CLIENT).json()["events"]
    assert events[0]["card"] is None
