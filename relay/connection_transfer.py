"""Portable, secret-free RelayHarbor integration configuration transfer.

GET exports non-secret settings only. POST validates all entries and commits
the import in a single SQLite transaction. New webhook credentials are returned
ONCE and are deliberately never included in the exported JSON.
"""
from __future__ import annotations

import hashlib
import secrets
import time

import connections
import rss
import website_monitor
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from typing import Literal

router = APIRouter()
FORMAT = "relayharbor-integrations"
VERSION = 1
MAX_ITEMS = 200


def _server():
    import server
    return server


class PortableConnection(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=60)
    provider: Literal["webhook", "github_actions", "rss", "website"]
    channel_name: str = Field(min_length=1, max_length=60)
    enabled: bool = True
    min_score: int = Field(default=0, ge=0, le=100)
    keyword: str = Field(default="", max_length=100)
    feed_url: str = Field(default="", max_length=2048)
    css_selector: str = Field(default="", max_length=200)
    poll_interval_minutes: int = Field(default=30, ge=15, le=1440)


class PortableBackup(BaseModel):
    model_config = ConfigDict(extra="forbid")
    format: Literal["relayharbor-integrations"]
    version: Literal[1]
    connections: list[PortableConnection] = Field(max_length=MAX_ITEMS)


def _identity(item):
    return (
        item.provider, item.name.casefold(), item.channel_name.casefold(),
        item.feed_url if item.provider in ("rss", "website") else "",
        item.css_selector if item.provider == "website" else ""
    )


@router.get("/v1/connections/export")
def export_connections(authorization: str | None = Header(default=None)):
    connections._client(authorization)
    with _server().connection() as db:
        rows = db.execute("""
            SELECT c.name, c.provider, c.channel_name, c.enabled,
                   c.min_score, c.keyword,
                   COALESCE(r.url, w.url, '') AS feed_url,
                   COALESCE(r.poll_interval_minutes, w.poll_interval_minutes, 30) AS poll_interval_minutes,
                   COALESCE(w.css_selector, '') AS css_selector
              FROM connections AS c
              LEFT JOIN rss_sources AS r ON r.connection_id = c.id
              LEFT JOIN website_sources AS w ON w.connection_id = c.id
             ORDER BY c.created_at, c.id
        """).fetchall()
    # No identifiers, inbound URLs, hashes, tokens, events, secrets or cursors.
    return {
        "format": FORMAT, "version": VERSION,
        "connections": [
            {
                "name": row["name"], "provider": row["provider"],
                "channel_name": row["channel_name"],
                "enabled": bool(row["enabled"]),
                "min_score": row["min_score"],
                "keyword": row["keyword"],
                "feed_url": row["feed_url"],
                "css_selector": row["css_selector"],
                "poll_interval_minutes": row["poll_interval_minutes"],
            }
            for row in rows
        ],
    }


@router.post("/v1/connections/import")
def import_connections(
    payload: PortableBackup, authorization: str | None = Header(default=None)
):
    connections._client(authorization)
    if not payload.connections:
        raise HTTPException(status_code=422, detail="Import file contains no connections")

    # Validate entire file *before* touching the database.
    items = []
    for src in payload.connections:
        item = src.model_copy(update={
            "name": src.name.strip(),
            "channel_name": src.channel_name.strip(),
            "keyword": src.keyword.strip(),
            "feed_url": src.feed_url.strip(),
            "css_selector": src.css_selector.strip(),
        })
        if not item.name or not item.channel_name:
            raise HTTPException(status_code=422, detail="Empty name or channel")
        if item.provider in ("rss", "website"):
            if item.min_score != 0:
                raise HTTPException(status_code=422, detail="Pull sources cannot set minimum score")
            try:
                if item.provider == "rss":
                    rss.validate_url(item.feed_url)
                    if item.css_selector:
                        raise ValueError("CSS selector applies only to website monitors")
                else:
                    website_monitor.validate_url(item.feed_url)
                    website_monitor.validate_selector(item.css_selector)
            except ValueError as exc:
                raise HTTPException(status_code=422, detail=str(exc)) from None
        elif item.feed_url or item.css_selector:
            raise HTTPException(status_code=422, detail="URL/selector fields require RSS/website")
        items.append(item)

    created = []
    skipped = 0
    with _server().connection() as db:
        try:
            db.execute("BEGIN IMMEDIATE")
            current = db.execute("""
                SELECT c.name, c.provider, c.channel_name,
                       COALESCE(r.url, w.url, '') AS feed_url,
                       COALESCE(w.css_selector, '') AS css_selector
                  FROM connections c
                  LEFT JOIN rss_sources r ON r.connection_id = c.id
                  LEFT JOIN website_sources w ON w.connection_id = c.id
            """).fetchall()
            existing = {
                (row["provider"], row["name"].casefold(),
                 row["channel_name"].casefold(),
                 row["feed_url"] if row["provider"] in ("rss", "website") else "",
                 row["css_selector"] if row["provider"] == "website" else "")
                for row in current
            }
            for item in items:
                key = _identity(item)
                if key in existing:
                    skipped += 1
                    continue
                connection_id = secrets.token_hex(12)
                secret = secrets.token_urlsafe(48) if item.provider not in ("rss", "website") else ""
                db.execute("""
                    INSERT INTO connections
                    (id, name, provider, channel_name, secret_hash, enabled,
                     min_score, keyword, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    connection_id, item.name, item.provider, item.channel_name,
                    hashlib.sha256(secret.encode()).hexdigest() if secret else "",
                    int(item.enabled), item.min_score, item.keyword,
                    int(time.time() * 1000)
                ))
                if item.provider == "rss":
                    rss.create_source(db, connection_id, item.feed_url,
                                      item.poll_interval_minutes)
                elif item.provider == "website":
                    website_monitor.create_source(db, connection_id, item.feed_url,
                                                  item.poll_interval_minutes, item.css_selector)
                created.append({
                    "id": connection_id, "name": item.name,
                    "provider": item.provider,
                    "channel_name": item.channel_name,
                    "enabled": item.enabled,
                    "min_score": item.min_score,
                    "keyword": item.keyword,
                    "feed_url": item.feed_url,
                    "css_selector": item.css_selector,
                    "poll_interval_minutes": item.poll_interval_minutes,
                    "path": "/v1/inbound/" + connection_id if secret else "",
                    "token": secret,
                })
                existing.add(key)
            db.commit()
        except ValueError as exc:
            db.rollback()
            raise HTTPException(status_code=422, detail=str(exc)) from None
        except Exception:
            db.rollback()
            raise
    return {"created": created, "skipped": skipped,
            "created_count": len(created)}
