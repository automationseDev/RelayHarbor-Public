"""Encrypted, single-user AI provider profiles stored on the Relay volume.

API keys are never returned to Android or written to logs. The separate Fernet
key must stay in the Relay environment and be backed up independently of SQLite.
"""
from __future__ import annotations

import ipaddress
import os
import socket
import sqlite3
import time
import uuid
from pathlib import Path
from urllib.parse import urlsplit

from cryptography.fernet import Fernet, InvalidToken
from fastapi import HTTPException

MAX_PROFILES = 30
SUPPORTED_KINDS = {"openai", "compatible", "dify"}


def initialize(database: Path) -> None:
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS ai_profiles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                base_url TEXT NOT NULL,
                model TEXT NOT NULL,
                secret_ciphertext BLOB NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)
        db.commit()


def cipher() -> Fernet:
    value = os.getenv("RELAY_AI_ENCRYPTION_KEY", "")
    if not value:
        raise HTTPException(status_code=503, detail="Set RELAY_AI_ENCRYPTION_KEY on Relay to manage AI providers")
    try:
        return Fernet(value.encode("ascii"))
    except (ValueError, TypeError):
        raise HTTPException(status_code=503, detail="Invalid RELAY_AI_ENCRYPTION_KEY configuration") from None


def public_https_url(value: str) -> str:
    """Validate provider base URL; reject credentials, private targets and non-HTTPS."""
    value = value.strip().rstrip("/")
    try:
        parsed = urlsplit(value)
        hostname = parsed.hostname
        if (parsed.scheme != "https" or not hostname or parsed.port not in (None, 443)
                or parsed.username or parsed.password or parsed.query or parsed.fragment
                or "\\" in value or any(c.isspace() for c in value)
                or parsed.path.startswith("//")):
            raise ValueError()
        host = hostname.encode("idna").decode("ascii").lower().rstrip(".")
        if (host in {"localhost", "metadata.google.internal"} or
                host.endswith((".local", ".internal", ".localhost", ".test", ".invalid"))
                or "." not in host):
            raise ValueError()
        try:
            address = ipaddress.ip_address(host)
            if not address.is_global:
                raise ValueError()
        except ValueError:
            # Distinguish rejected numeric IPs (no DNS-like hostname) from normal domains.
            if hostname.replace(".", "").isdigit() or ":" in hostname:
                raise
    except (ValueError, UnicodeError):
        raise HTTPException(status_code=422, detail="A public HTTPS provider URL on port 443 is required") from None
    return value


def require_public_dns(url: str) -> None:
    """Fail closed on DNS errors or non-public IPs before provider requests.

    This is not a full DNS rebinding defense. Keep the Relay behind an
    authenticated HTTPS proxy and restrict its egress at network level.
    """
    host = urlsplit(url).hostname
    try:
        addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
        if not addresses:
            raise ValueError()
        for record in addresses:
            if not ipaddress.ip_address(record[4][0]).is_global:
                raise ValueError()
    except (OSError, ValueError):
        raise HTTPException(status_code=422, detail="Provider DNS must resolve only to public addresses") from None


def validate_fields(name: str, kind: str, base_url: str, model: str) -> tuple[str, str, str, str]:
    name = name.strip()
    model = model.strip()
    if not name or len(name) > 60 or kind not in SUPPORTED_KINDS:
        raise HTTPException(status_code=422, detail="Invalid AI provider name or kind")
    if kind != "dify" and (not model or len(model) > 120):
        raise HTTPException(status_code=422, detail="Model ID is required")
    if len(model) > 120:
        raise HTTPException(status_code=422, detail="Model ID is too long")
    return name, kind, public_https_url(base_url), model


def list_profiles(database: Path) -> list[dict]:
    with sqlite3.connect(database, timeout=15) as db:
        db.row_factory = sqlite3.Row
        rows = db.execute(
            "SELECT id, name, kind, base_url, model FROM ai_profiles ORDER BY created_at, id"
        ).fetchall()
    return [dict(row, managed=True) for row in rows]


def read_profile(database: Path, profile_id: str) -> dict:
    with sqlite3.connect(database, timeout=15) as db:
        db.row_factory = sqlite3.Row
        row = db.execute("SELECT * FROM ai_profiles WHERE id = ?", (profile_id,)).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="AI profile not found")
    try:
        secret = cipher().decrypt(row["secret_ciphertext"]).decode("utf-8")
    except InvalidToken:
        raise HTTPException(status_code=503, detail="AI encryption key does not match stored profiles") from None
    return {
        "id": row["id"], "name": row["name"], "kind": row["kind"],
        "base_url": row["base_url"], "model": row["model"], "api_key": secret
    }


def create_profile(
    database: Path, name: str, kind: str, base_url: str, model: str, api_key: str
) -> dict:
    name, kind, base_url, model = validate_fields(name, kind, base_url, model)
    if not api_key.strip() or len(api_key) > 512:
        raise HTTPException(status_code=422, detail="An API key is required")
    encrypted = cipher().encrypt(api_key.strip().encode("utf-8"))
    profile_id = uuid.uuid4().hex
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("BEGIN IMMEDIATE")
        count = db.execute("SELECT COUNT(*) FROM ai_profiles").fetchone()[0]
        if count >= MAX_PROFILES:
            raise HTTPException(status_code=409, detail="AI provider limit reached")
        db.execute(
            "INSERT INTO ai_profiles (id, name, kind, base_url, model, secret_ciphertext, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (profile_id, name, kind, base_url, model, encrypted, int(time.time()))
        )
        db.commit()
    return {"id": profile_id, "name": name, "kind": kind,
            "base_url": base_url, "model": model, "managed": True}


def update_profile(
    database: Path, profile_id: str, name: str, kind: str, base_url: str,
    model: str, api_key: str = ""
) -> dict:
    name, kind, base_url, model = validate_fields(name, kind, base_url, model)
    cipher()  # Do not allow management with a missing or rotated server key.
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute("SELECT secret_ciphertext FROM ai_profiles WHERE id = ?",
                         (profile_id,)).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="AI profile not found")
        if api_key:
            if len(api_key) > 512:
                raise HTTPException(status_code=422, detail="API key is too long")
            ciphertext = cipher().encrypt(api_key.strip().encode("utf-8"))
        else:
            ciphertext = row[0]
        db.execute("UPDATE ai_profiles SET name=?,kind=?,base_url=?,model=?,secret_ciphertext=? "
                   "WHERE id=?", (name, kind, base_url, model, ciphertext, profile_id))
        db.commit()
    return {"id": profile_id, "name": name, "kind": kind,
            "base_url": base_url, "model": model, "managed": True}


def delete_profile(database: Path, profile_id: str) -> None:
    with sqlite3.connect(database, timeout=15) as db:
        cur = db.execute("DELETE FROM ai_profiles WHERE id = ?", (profile_id,))
        db.commit()
    if cur.rowcount == 0:
        raise HTTPException(status_code=404, detail="AI profile not found")
