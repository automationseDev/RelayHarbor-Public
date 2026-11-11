"""RelayHarbor opt-in webhook and AI relay server.

No provider master API key is shipped in the Android APK.
Requires TLS at a trusted reverse proxy and distinct client/ingress tokens.
"""
from __future__ import annotations

import hmac
import json
import os
import re
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Literal

import httpx
import connections
import rss
from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

app = FastAPI(title="RelayHarbor Relay", version="0.5.2", docs_url=None, redoc_url=None)
app.include_router(connections.router)
app.include_router(rss.router)

DATABASE = Path(os.getenv("RELAY_DATABASE", "/data/relayharbor.sqlite3"))
CLIENT_TOKEN = os.getenv("RELAY_CLIENT_TOKEN", "")
WEBHOOK_TOKEN = os.getenv("RELAY_WEBHOOK_TOKEN", "")
DIFY_API_URL = os.getenv("DIFY_API_URL", "").rstrip("/")
DIFY_API_KEY = os.getenv("DIFY_API_KEY", "")
DIFY_USER = os.getenv("DIFY_USER", "relayharbor")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-6-luna")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")


def init_db() -> None:
    DATABASE.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DATABASE, timeout=15) as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("""
            CREATE TABLE IF NOT EXISTS events (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                channel TEXT NOT NULL,
                channel_name TEXT NOT NULL,
                event_id TEXT NOT NULL,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                card_json TEXT,
                timestamp INTEGER NOT NULL,
                UNIQUE(channel, event_id)
            )
        """)
        db.commit()


@contextmanager
def connection():
    db = sqlite3.connect(DATABASE, timeout=15)
    db.row_factory = sqlite3.Row
    try:
        yield db
    finally:
        db.close()


def authorize(authorization: str | None, expected: str) -> None:
    if not authorization or not authorization.startswith("Bearer ") or not expected:
        raise HTTPException(status_code=401, detail="Unauthorized")
    candidate = authorization[7:]
    if not hmac.compare_digest(candidate, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


class WebhookMessage(BaseModel):
    id: str | None = Field(default=None, max_length=200)
    channel_name: str | None = Field(default=None, max_length=60)
    sender: str = Field(default="Webhook", max_length=80)
    text: str = Field(default="", max_length=10000)
    card: dict | None = None


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=12000)


class ChatRequest(BaseModel):
    provider: Literal["dify", "openai"]
    message: str = Field(min_length=1, max_length=12000)
    conversation_id: str | None = Field(default=None, max_length=160)
    history: list[ChatTurn] = Field(default_factory=list, max_length=20)


@app.on_event("startup")
async def setup() -> None:
    if (len(CLIENT_TOKEN) < 32 or len(WEBHOOK_TOKEN) < 32
            or CLIENT_TOKEN.startswith("replace-with-")
            or WEBHOOK_TOKEN.startswith("replace-with-")
            or hmac.compare_digest(CLIENT_TOKEN, WEBHOOK_TOKEN)):
        raise RuntimeError("Set different random RELAY_CLIENT_TOKEN / RELAY_WEBHOOK_TOKEN (32+ characters).")
    init_db()
    connections.initialize(DATABASE)
    rss.initialize(DATABASE)
    rss.start_worker()


@app.on_event("shutdown")
async def shutdown() -> None:
    await rss.stop_worker()


@app.get("/healthz")
def health():
    return {"ok": True, "service": "relayharbor", "version": "0.5.2"}


@app.post("/v1/webhooks/{channel}", status_code=201)
async def receive_webhook(
    channel: str, request: Request, authorization: str | None = Header(default=None)
):
    authorize(authorization, WEBHOOK_TOKEN)
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", channel):
        raise HTTPException(status_code=400, detail="Invalid channel slug")
    if "application/json" not in request.headers.get("content-type", "").lower():
        raise HTTPException(status_code=415, detail="JSON required")
    raw = await request.body()
    if len(raw) > 65536:
        raise HTTPException(status_code=413, detail="Webhook too large")
    try:
        payload = WebhookMessage.model_validate_json(raw)
    except Exception:
        raise HTTPException(status_code=422, detail="Invalid webhook payload") from None
    if not payload.text.strip() and not payload.card:
        raise HTTPException(status_code=422, detail="Text or card required")
    card_json = json.dumps(payload.card, ensure_ascii=False) if payload.card else None
    if card_json and len(card_json.encode("utf-8")) > 24000:
        raise HTTPException(status_code=413, detail="Card too large")
    incoming_id = payload.id or uuid.uuid4().hex
    channel_name = payload.channel_name or channel.replace("_", " ").replace("-", " ")
    with connection() as db:
        try:
            cur = db.execute(
                """INSERT INTO events (channel, channel_name, event_id, sender, text, card_json, timestamp)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (channel, channel_name, incoming_id, payload.sender.strip() or "Webhook",
                 payload.text, card_json, int(time.time() * 1000))
            )
            db.commit()
            seq = cur.lastrowid
            duplicate = False
        except sqlite3.IntegrityError:
            row = db.execute(
                "SELECT sequence FROM events WHERE channel = ? AND event_id = ?",
                (channel, incoming_id)
            ).fetchone()
            seq = row["sequence"]
            duplicate = True
    return {"sequence": seq, "duplicate": duplicate}


@app.get("/v1/events")
def list_events(
    after: int = 0, limit: int = 100,
    authorization: str | None = Header(default=None)
):
    authorize(authorization, CLIENT_TOKEN)
    if after < 0 or limit < 1 or limit > 100:
        raise HTTPException(status_code=400, detail="Invalid pagination")
    with connection() as db:
        rows = db.execute(
            """SELECT sequence, channel_name, sender, text, card_json, timestamp
               FROM events WHERE sequence > ? ORDER BY sequence ASC LIMIT ?""",
            (after, limit)
        ).fetchall()
    events = [
        {
            "sequence": row["sequence"],
            "channel": row["channel_name"],
            "sender": row["sender"],
            "text": row["text"],
            "card": json.loads(row["card_json"]) if row["card_json"] else None,
            "timestamp": row["timestamp"],
        }
        for row in rows
    ]
    return {"events": events, "next_cursor": events[-1]["sequence"] if events else after}


def openai_text(response: dict) -> str:
    segments = [
        part.get("text", "")
        for item in response.get("output", [])
        if isinstance(item, dict) and item.get("type") == "message"
        for part in item.get("content", [])
        if isinstance(part, dict) and part.get("type") == "output_text"
    ]
    return "\n".join(part for part in segments if part).strip()


@app.post("/v1/chat")
async def chat(
    payload: ChatRequest,
    authorization: str | None = Header(default=None)
):
    authorize(authorization, CLIENT_TOKEN)
    if payload.provider == "dify":
        if not DIFY_API_URL or not DIFY_API_KEY:
            raise HTTPException(status_code=503, detail="Dify is not configured on this relay")
        url = DIFY_API_URL + "/chat-messages"
        body = {
            "inputs": {},
            "query": payload.message,
            "response_mode": "blocking",
            "conversation_id": payload.conversation_id or "",
            "user": DIFY_USER,
        }
        headers = {"Authorization": "Bearer " + DIFY_API_KEY}
    else:
        if not OPENAI_API_KEY:
            raise HTTPException(status_code=503, detail="OpenAI is not configured on this relay")
        url = OPENAI_BASE_URL + "/responses"
        context = [{"role": turn.role, "content": turn.content}
                   for turn in payload.history[-12:]]
        body = {"model": OPENAI_MODEL, "input": context + [
            {"role": "user", "content": payload.message}
        ], "store": False}
        headers = {"Authorization": "Bearer " + OPENAI_API_KEY}
    try:
        async with httpx.AsyncClient(timeout=90, follow_redirects=False) as client:
            result = await client.post(url, headers=headers, json=body)
    except httpx.RequestError:
        raise HTTPException(status_code=502, detail="Provider network error") from None
    if result.status_code >= 400:
        raise HTTPException(status_code=502,
                            detail=f"Provider HTTP {result.status_code} (check relay configuration)")
    try:
        data = result.json()
        answer = (data.get("answer", "") if payload.provider == "dify" else openai_text(data))
        answer = answer.strip()
        if not answer:
            raise ValueError("Empty answer")
    except (ValueError, TypeError, AttributeError):
        raise HTTPException(status_code=502, detail="Provider returned no text answer") from None
    return {
        "answer": answer,
        "provider": payload.provider,
        "conversation_id": (data.get("conversation_id") or "")
            if payload.provider == "dify" else "",
    }
