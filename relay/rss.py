"""Periodic RSS/Atom ingestion for a single-user RelayHarbor deployment.

Feeds must be HTTPS public hostnames (port 443), without credentials. All DNS
answers are checked before each request; redirects and environment proxies are
disabled. Feed URLs should still be trusted: preflight DNS checks are not an
absolute defense against DNS-rebinding between resolution and connection.
"""
from __future__ import annotations

import asyncio
import hashlib
import html
import ipaddress
import logging
import os
import re
import socket
import sqlite3
import time
from urllib.parse import urljoin, urlsplit

import feedparser
import image_metadata
import httpx
from fastapi import APIRouter, Header, HTTPException

router = APIRouter()
logger = logging.getLogger("relayharbor.rss")
_poller: asyncio.Task | None = None
_lock = asyncio.Lock()
MAX_SOURCES = 50
MAX_BYTES = 1024 * 1024
MAX_ENTRIES = 50
FIRST_IMPORT = 3
NEXT_IMPORT = 20


def _server():
    import server
    return server


def _connections():
    import connections
    return connections


def validate_url(value: str) -> str:
    url = value.strip()
    if not url or len(url) > 2048:
        raise ValueError("RSS URL must be 1–2048 characters")
    if any(ord(c) < 32 or ord(c) == 127 for c in url):
        raise ValueError("Control characters are not allowed in RSS URL")
    try:
        parts = urlsplit(url)
        host = (parts.hostname or "").rstrip(".").lower()
        port = parts.port
    except ValueError as exc:
        raise ValueError("Invalid RSS URL") from exc
    if (parts.scheme.lower() != "https" or not host or
            parts.username is not None or parts.password is not None or
            parts.fragment or port not in (None, 443)):
        raise ValueError("Only public HTTPS feeds on port 443 without credentials are allowed")
    if host == "localhost" or host.endswith((
        ".localhost", ".local", ".internal", ".home.arpa", ".test", ".invalid"
    )):
        raise ValueError("Local hostnames cannot be used as RSS feeds")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise ValueError("IP addresses are not accepted; use a public DNS hostname")
    if "." not in host or host.startswith(".") or ".." in host:
        raise ValueError("RSS hostname must be a public DNS name")
    return url


def initialize(database):
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS rss_sources (
                connection_id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                poll_interval_minutes INTEGER NOT NULL DEFAULT 30,
                last_checked INTEGER NOT NULL DEFAULT 0,
                last_success INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                etag TEXT NOT NULL DEFAULT '',
                modified TEXT NOT NULL DEFAULT '',
                initialized INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execute("""
            CREATE TABLE IF NOT EXISTS rss_seen (
                connection_id TEXT NOT NULL,
                entry_key TEXT NOT NULL,
                first_seen INTEGER NOT NULL,
                PRIMARY KEY(connection_id, entry_key)
            )
        """)
        db.commit()


def create_source(db, connection_id: str, url: str, interval: int):
    if not isinstance(interval, int) or not 15 <= interval <= 1440:
        raise ValueError("Polling interval must be 15–1440 minutes")
    if db.execute("SELECT COUNT(*) FROM rss_sources").fetchone()[0] >= MAX_SOURCES:
        raise ValueError("RSS feed limit reached (50)")
    db.execute(
        "INSERT INTO rss_sources (connection_id, url, poll_interval_minutes) VALUES (?, ?, ?)",
        (connection_id, validate_url(url), interval)
    )


def update_source(db, connection_id: str, url=None, interval=None):
    row = db.execute(
        "SELECT url, poll_interval_minutes FROM rss_sources WHERE connection_id = ?",
        (connection_id,)
    ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="RSS source not found")
    next_url = validate_url(url) if url is not None else row["url"]
    next_interval = interval if interval is not None else row["poll_interval_minutes"]
    if not isinstance(next_interval, int) or not 15 <= next_interval <= 1440:
        raise ValueError("Polling interval must be 15–1440 minutes")
    if next_url != row["url"]:
        # Keep old notifications, but reset source identity and baseline.
        db.execute("DELETE FROM rss_seen WHERE connection_id = ?", (connection_id,))
        db.execute(
            """UPDATE rss_sources SET url=?, poll_interval_minutes=?, last_checked=0,
               last_success=0, last_error='', etag='', modified='', initialized=0
               WHERE connection_id=?""",
            (next_url, next_interval, connection_id)
        )
    else:
        db.execute(
            "UPDATE rss_sources SET poll_interval_minutes=? WHERE connection_id=?",
            (next_interval, connection_id)
        )


def public_source(connection_id: str):
    with _server().connection() as db:
        row = db.execute(
            """SELECT url, poll_interval_minutes, last_checked, last_success, last_error
               FROM rss_sources WHERE connection_id=?""", (connection_id,)
        ).fetchone()
        if row is None:
            return {}
        return {
            "feed_url": row["url"],
            "poll_interval_minutes": row["poll_interval_minutes"],
            "last_checked": row["last_checked"],
            "last_success": row["last_success"],
            "last_error": row["last_error"],
        }


async def _check_public_dns(url: str):
    host = urlsplit(validate_url(url)).hostname
    try:
        infos = await asyncio.wait_for(
            asyncio.get_running_loop().getaddrinfo(
                host, 443, type=socket.SOCK_STREAM
            ), timeout=5
        )
    except (OSError, asyncio.TimeoutError) as exc:
        raise ValueError("RSS hostname could not be resolved") from exc
    if not infos:
        raise ValueError("RSS hostname has no DNS records")
    addresses = [entry[4][0] for entry in infos]
    if any(not ipaddress.ip_address(ip).is_global for ip in addresses):
        raise ValueError("RSS hostname resolves to a non-public IP address")


async def _download(url, etag="", modified="", *, accept=None, user_agent=None):
    headers = {
        "User-Agent": user_agent or "RelayHarbor-RSS/0.5 (+feed reader)",
        "Accept": accept or "application/atom+xml, application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.1",
    }
    if etag:
        headers["If-None-Match"] = etag[:500]
    if modified:
        headers["If-Modified-Since"] = modified[:150]
    current_url = url
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(12.0, connect=6.0), follow_redirects=False,
        trust_env=False
    ) as client:
        for hop in range(4):
            await _check_public_dns(current_url)
            async with client.stream("GET", current_url, headers=headers) as response:
                if response.status_code in (301, 302, 303, 307, 308):
                    if hop >= 3:
                        raise ValueError("Too many RSS redirects")
                    target = response.headers.get("Location", "")
                    if not target:
                        raise ValueError("RSS redirect has no Location header")
                    current_url = validate_url(urljoin(current_url, target))
                    # Cache validators only belong to the original resource.
                    headers.pop("If-None-Match", None)
                    headers.pop("If-Modified-Since", None)
                    continue
                if response.status_code == 304:
                    return None, etag, modified
                response.raise_for_status()
                data = bytearray()
                async for chunk in response.aiter_bytes():
                    data.extend(chunk)
                    if len(data) > MAX_BYTES:
                        raise ValueError("RSS feed exceeds the 1 MiB size limit")
                return (
                    bytes(data),
                    response.headers.get("ETag", "")[:500],
                    response.headers.get("Last-Modified", "")[:150],
                )
    raise ValueError("RSS fetch exceeded redirect limit")


def _plain(value, limit):
    value = re.sub(r"(?s)<[^>]*>", " ", str(value or ""))
    value = html.unescape(value)
    return re.sub(r"\s+", " ", value).strip()[:limit]


def _article_url(value):
    try:
        parsed = urlsplit(str(value or ""))
        if parsed.scheme.lower() in ("https", "http") and parsed.hostname:
            return str(value)[:2048]
    except ValueError:
        pass
    return ""


def _entry_key(entry):
    identity = str(entry.get("id") or entry.get("link") or (
        str(entry.get("title", "")) + "|" + str(entry.get("published", ""))
    ))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()


async def refresh(connection_id: str, force: bool = False):
    server = _server()
    async with _lock:
        with server.connection() as db:
            row = db.execute(
                """SELECT c.*, s.url, s.poll_interval_minutes, s.last_checked,
                          s.etag, s.modified, s.initialized
                   FROM connections c JOIN rss_sources s ON c.id=s.connection_id
                   WHERE c.id=? AND c.provider='rss'""",
                (connection_id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail="RSS connection not found")
            if not row["enabled"]:
                raise HTTPException(status_code=403, detail="RSS connection is disabled")
            now = int(time.time() * 1000)
            if not force and now - row["last_checked"] < row["poll_interval_minutes"] * 60000:
                return {"new": 0, "status": "not_due"}
            source = dict(row)

        try:
            payload, etag, modified = await _download(
                source["url"], source["etag"], source["modified"]
            )
            parsed = feedparser.parse(payload) if payload is not None else None
            if parsed is not None and not parsed.version:
                raise ValueError("URL did not return a valid RSS or Atom feed")
        except (httpx.HTTPError, ValueError, OSError, TimeoutError) as exc:
            message = _plain(str(exc), 180) or "RSS fetch failed"
            with server.connection() as db:
                db.execute(
                    "UPDATE rss_sources SET last_checked=?, last_error=? WHERE connection_id=?",
                    (int(time.time() * 1000), message, connection_id)
                )
                db.commit()
            raise HTTPException(status_code=502, detail="RSS acquisition failed: " + message) from None

        imported = 0
        scanned = 0
        with server.connection() as db:
            # Check again after network I/O: the user may have disabled/deleted it.
            existing = db.execute(
                "SELECT enabled, channel_name, keyword FROM connections WHERE id=? AND provider='rss'",
                (connection_id,)
            ).fetchone()
            state = db.execute(
                "SELECT url, initialized FROM rss_sources WHERE connection_id=?",
                (connection_id,)
            ).fetchone()
            if existing is None or state is None or not existing["enabled"] or state["url"] != source["url"]:
                return {"new": 0, "status": "changed"}
            if parsed is not None:
                title = _plain(parsed.feed.get("title", ""), 80) or source["name"]
                first_sync = state["initialized"] == 0
                allowed = FIRST_IMPORT if first_sync else NEXT_IMPORT
                for entry in parsed.entries[:MAX_ENTRIES]:
                    key = _entry_key(entry)
                    cur = db.execute(
                        """INSERT OR IGNORE INTO rss_seen(connection_id, entry_key, first_seen)
                           VALUES (?, ?, ?)""",
                        (connection_id, key, int(time.time() * 1000))
                    )
                    scanned += 1
                    if cur.rowcount == 0:
                        continue
                    article_title = _plain(entry.get("title", ""), 140) or "記事タイトルなし"
                    article_summary = _plain(entry.get("summary", ""), 850)
                    link = _article_url(entry.get("link", ""))
                    searchable = article_title + " " + article_summary
                    if existing["keyword"].casefold() not in searchable.casefold():
                        continue
                    if imported >= allowed:
                        continue
                    card = {
                        "schemaVersion": 1,
                        "title": article_title,
                        "description": article_summary or article_title,
                        "badge": "RSS",
                    }
                    if link:
                        card["url"] = link
                    thumbnail_url = image_metadata.rss_entry_thumbnail(entry)
                    if thumbnail_url:
                        card["thumbnail_url"] = thumbnail_url
                    result = _connections()._insert_event(
                        db, source, "rss-" + key, title,
                        article_title, card
                    )
                    if not result["duplicate"]:
                        imported += 1
            db.execute(
                """UPDATE rss_sources SET last_checked=?, last_success=?,
                   last_error='', etag=?, modified=?, initialized=1
                   WHERE connection_id=?""",
                (int(time.time() * 1000), int(time.time() * 1000),
                 etag, modified, connection_id)
            )
            db.commit()
        return {"new": imported, "scanned": scanned, "status": "ok"}


@router.post("/v1/connections/{connection_id}/refresh")
async def refresh_endpoint(connection_id: str, authorization: str | None = Header(default=None)):
    _connections()._client(authorization)
    return await refresh(connection_id, force=True)


async def _run_worker():
    while True:
        await asyncio.sleep(20)
        try:
            with _server().connection() as db:
                now = int(time.time() * 1000)
                ids = [
                    row["id"] for row in db.execute(
                        """SELECT c.id FROM connections c
                           JOIN rss_sources s ON s.connection_id=c.id
                           WHERE c.provider='rss' AND c.enabled=1
                           AND (? - s.last_checked >= s.poll_interval_minutes * 60000)
                           ORDER BY s.last_checked ASC LIMIT 50""",
                        (now,)
                    ).fetchall()
                ]
            for connection_id in ids:
                try:
                    await refresh(connection_id)
                except HTTPException as exc:
                    logger.warning("RSS polling failed for %s (HTTP %d)",
                                   connection_id, exc.status_code)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("RSS polling loop iteration failed")


def start_worker():
    global _poller
    if os.getenv("RELAY_RSS_ENABLED", "1") == "1":
        _poller = asyncio.create_task(_run_worker(), name="relayharbor-rss-poll")


async def stop_worker():
    global _poller
    if _poller is not None:
        _poller.cancel()
        try:
            await _poller
        except asyncio.CancelledError:
            pass
        _poller = None
