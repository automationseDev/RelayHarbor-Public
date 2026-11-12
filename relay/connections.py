"""Single-user per-source webhook connections for RelayHarbor v0.4.

Secrets are never returned by GET/list; only create/rotate returns the credential.
The legacy /v1/webhooks/{channel} route remains supported for existing clients.
"""
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import time
import uuid

import rss
import image_metadata
import slack_adapter
from typing import Literal

from fastapi import APIRouter, Header, HTTPException, Request
from pydantic import BaseModel, Field

router = APIRouter()


def _server():
    import server
    return server


def initialize(database):
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS connections (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                provider TEXT NOT NULL,
                channel_name TEXT NOT NULL,
                secret_hash TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                min_score INTEGER NOT NULL DEFAULT 0,
                keyword TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
        """)
        db.commit()


def _client(authorization):
    server = _server()
    server.authorize(authorization, server.CLIENT_TOKEN)


def _find(db, connection_id):
    row = db.execute(
        "SELECT * FROM connections WHERE id = ?", (connection_id,)
    ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Connection not found")
    return row


def _public(row):
    result = {
        "id": row["id"],
        "name": row["name"],
        "provider": row["provider"],
        "channel_name": row["channel_name"],
        "enabled": bool(row["enabled"]),
        "min_score": row["min_score"],
        "keyword": row["keyword"],
        "created_at": row["created_at"],
        "path": "/v1/inbound/" + row["id"] if row["provider"] != "rss" else "",
    }
    if row["provider"] == "rss":
        result.update(rss.public_source(row["id"]))
    return result


class ConnectionCreate(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    provider: Literal["webhook", "github_actions", "rss"] = "webhook"
    channel_name: str = Field(min_length=1, max_length=60)
    min_score: int = Field(default=0, ge=0, le=100)
    keyword: str = Field(default="", max_length=100)
    feed_url: str | None = Field(default=None, max_length=2048)
    poll_interval_minutes: int = Field(default=30, ge=15, le=1440)


class ConnectionUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=60)
    channel_name: str | None = Field(default=None, min_length=1, max_length=60)
    enabled: bool | None = None
    min_score: int | None = Field(default=None, ge=0, le=100)
    keyword: str | None = Field(default=None, max_length=100)
    feed_url: str | None = Field(default=None, max_length=2048)
    poll_interval_minutes: int | None = Field(default=None, ge=15, le=1440)


@router.get("/v1/connections")
def list_connections(authorization: str | None = Header(default=None)):
    _client(authorization)
    with _server().connection() as db:
        rows = db.execute("SELECT * FROM connections ORDER BY created_at DESC, id").fetchall()
    return {"connections": [_public(row) for row in rows]}


@router.post("/v1/connections", status_code=201)
def create_connection(
    payload: ConnectionCreate, authorization: str | None = Header(default=None)
):
    _client(authorization)
    name = payload.name.strip()
    channel_name = payload.channel_name.strip()
    if not name or not channel_name:
        raise HTTPException(status_code=422, detail="Name and channel are required")
    if payload.provider == "rss":
        if payload.feed_url is None:
            raise HTTPException(status_code=422, detail="RSS URL is required")
        if payload.min_score:
            raise HTTPException(status_code=422, detail="RSS articles do not have scores")
        try:
            rss_url = rss.validate_url(payload.feed_url)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from None
    elif payload.feed_url is not None:
        raise HTTPException(status_code=422, detail="Feed URL applies only to RSS")
    connection_id = secrets.token_hex(12)
    secret = secrets.token_urlsafe(48) if payload.provider != "rss" else None
    with _server().connection() as db:
        try:
            db.execute(
                """INSERT INTO connections
                   (id, name, provider, channel_name, secret_hash, enabled,
                    min_score, keyword, created_at)
                   VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)""",
                (connection_id, name, payload.provider, channel_name,
                 hashlib.sha256(secret.encode()).hexdigest() if secret else "",
                 payload.min_score, payload.keyword.strip(), int(time.time() * 1000))
            )
            if payload.provider == "rss":
                rss.create_source(db, connection_id, rss_url, payload.poll_interval_minutes)
            db.commit()
        except ValueError as exc:
            db.rollback()
            raise HTTPException(status_code=422, detail=str(exc)) from None
        row = _find(db, connection_id)
    response = _public(row)
    if secret:
        response["token"] = secret
    return response


@router.patch("/v1/connections/{connection_id}")
def update_connection(
    connection_id: str, payload: ConnectionUpdate,
    authorization: str | None = Header(default=None)
):
    _client(authorization)
    changes = payload.model_dump(exclude_unset=True)
    if not changes:
        raise HTTPException(status_code=400, detail="No fields to change")
    if any(value is None for value in changes.values()):
        raise HTTPException(status_code=422, detail="Null fields are not supported")
    for field in ("name", "channel_name"):
        if field in changes:
            changes[field] = changes[field].strip()
            if not changes[field]:
                raise HTTPException(status_code=422, detail="Empty name/channel")
    if "keyword" in changes:
        changes["keyword"] = changes["keyword"].strip()
    if "enabled" in changes:
        changes["enabled"] = int(changes["enabled"])
    feed_url = changes.pop("feed_url", None)
    interval = changes.pop("poll_interval_minutes", None)
    allowed = {"name", "channel_name", "enabled", "min_score", "keyword"}
    if set(changes) - allowed:
        raise HTTPException(status_code=400, detail="Unsupported field")
    with _server().connection() as db:
        row = _find(db, connection_id)
        if row["provider"] != "rss" and (feed_url is not None or interval is not None):
            raise HTTPException(status_code=422, detail="RSS fields only apply to RSS")
        if row["provider"] == "rss" and changes.get("min_score", 0) != 0:
            raise HTTPException(status_code=422, detail="RSS articles do not have scores")
        try:
            if row["provider"] == "rss" and (feed_url is not None or interval is not None):
                rss.update_source(db, connection_id, feed_url, interval)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from None
        if changes:
            assigns = ", ".join(field + " = ?" for field in changes)
            db.execute(
                "UPDATE connections SET " + assigns + " WHERE id = ?",
                (*changes.values(), connection_id)
            )
        db.commit()
        return _public(_find(db, connection_id))


@router.post("/v1/connections/{connection_id}/rotate")
def rotate_connection(
    connection_id: str, authorization: str | None = Header(default=None)
):
    _client(authorization)
    secret = secrets.token_urlsafe(48)
    with _server().connection() as db:
        row = _find(db, connection_id)
        if row["provider"] == "rss":
            raise HTTPException(status_code=400, detail="RSS sources do not use inbound tokens")
        db.execute(
            "UPDATE connections SET secret_hash = ? WHERE id = ?",
            (hashlib.sha256(secret.encode()).hexdigest(), connection_id)
        )
        db.commit()
        return {**_public(_find(db, connection_id)), "token": secret}


@router.delete("/v1/connections/{connection_id}", status_code=204)
def delete_connection(
    connection_id: str, authorization: str | None = Header(default=None)
):
    _client(authorization)
    with _server().connection() as db:
        _find(db, connection_id)
        db.execute("DELETE FROM rss_seen WHERE connection_id = ?", (connection_id,))
        db.execute("DELETE FROM rss_sources WHERE connection_id = ?", (connection_id,))
        db.execute("DELETE FROM connections WHERE id = ?", (connection_id,))
        db.commit()
    return None


def _insert_event(db, row, event_id, sender, text, card):
    """Persist idempotently in the existing v0.3 event store."""
    server = _server()
    card_json = json.dumps(card, ensure_ascii=False) if card is not None else None
    if card_json and len(card_json.encode("utf-8")) > 24000:
        raise HTTPException(status_code=413, detail="Card too large")
    channel = "conn_" + row["id"]
    try:
        cur = db.execute(
            """INSERT INTO events (channel, channel_name, event_id, sender, text, card_json, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (channel, row["channel_name"], event_id,
             sender.strip() or "Webhook", text, card_json, int(time.time() * 1000))
        )
        db.commit()
        return {"sequence": cur.lastrowid, "duplicate": False}
    except sqlite3.IntegrityError:
        existing = db.execute(
            "SELECT sequence FROM events WHERE channel = ? AND event_id = ?",
            (channel, event_id)
        ).fetchone()
        return {"sequence": existing["sequence"], "duplicate": True}


@router.post("/v1/inbound/{connection_id}", status_code=201)
async def inbound(
    connection_id: str, request: Request,
    authorization: str | None = Header(default=None)
):
    server = _server()
    with server.connection() as db:
        row = _find(db, connection_id)
        if row["provider"] == "rss":
            raise HTTPException(status_code=404, detail="RSS sources do not accept webhooks")
        candidate = (authorization or "")
        if not candidate.startswith("Bearer ") or not hmac.compare_digest(
            hashlib.sha256(candidate[7:].encode()).hexdigest(), row["secret_hash"]
        ):
            raise HTTPException(status_code=401, detail="Unauthorized")
        if not row["enabled"]:
            raise HTTPException(status_code=403, detail="Connection disabled")
        if "application/json" not in request.headers.get("content-type", "").lower():
            raise HTTPException(status_code=415, detail="JSON required")
        raw = await request.body()
        if len(raw) > 65536:
            raise HTTPException(status_code=413, detail="Webhook too large")
        try:
            decoded = json.loads(raw)
            if not isinstance(decoded, dict):
                raise ValueError("JSON object required")
            normalized = image_metadata.enrich_webhook_thumbnail(
                slack_adapter.normalize_slack_payload(decoded)
            )
            payload = server.WebhookMessage.model_validate(normalized)
        except Exception:
            raise HTTPException(status_code=422, detail="Invalid webhook payload") from None
        if not payload.text.strip() and not payload.card:
            raise HTTPException(status_code=422, detail="Text or card required")
        if row["min_score"] > 0:
            score = payload.card.get("score") if isinstance(payload.card, dict) else None
            if not isinstance(score, (int, float)) or isinstance(score, bool) or score < row["min_score"]:
                return {"filtered": True, "reason": "score"}
        if row["keyword"]:
            searchable = payload.text + " " + (
                json.dumps(payload.card, ensure_ascii=False) if payload.card else ""
            )
            if row["keyword"].casefold() not in searchable.casefold():
                return {"filtered": True, "reason": "keyword"}
        return _insert_event(
            db, row, payload.id or uuid.uuid4().hex,
            payload.sender, payload.text, payload.card
        )


@router.post("/v1/connections/{connection_id}/test", status_code=201)
def test_connection(
    connection_id: str, authorization: str | None = Header(default=None)
):
    _client(authorization)
    with _server().connection() as db:
        row = _find(db, connection_id)
        if not row["enabled"]:
            raise HTTPException(status_code=403, detail="Connection disabled")
        card = {
            "schemaVersion": 1, "title": "接続テスト成功",
            "description": row["name"] + " のテスト通知です。",
            "score": 100, "badge": "テスト",
            "metrics": [{"label": "Webhook受信", "percent": 100}],
        }
        text = "RelayHarborの連携テスト通知を受信しました"
        if row["keyword"]:
            text += " " + row["keyword"]
        return _insert_event(
            db, row, "test-" + uuid.uuid4().hex, "RelayHarbor", text, card
        )

# POST aliases for Android's standard HttpURLConnection, which does not
# consistently support PATCH/DELETE on every OS version.
@router.post("/v1/connections/{connection_id}/update")
def update_connection_post(
    connection_id: str, payload: ConnectionUpdate,
    authorization: str | None = Header(default=None)
):
    return update_connection(connection_id, payload, authorization)


@router.post("/v1/connections/{connection_id}/delete")
def delete_connection_post(
    connection_id: str, authorization: str | None = Header(default=None)
):
    return delete_connection(connection_id, authorization)
