"""RSS-free website change detection for public HTTPS HTML pages.

Reuses RSS DNS/HTTPS/redirect/body-size restrictions. No JavaScript, logins,
cookies, browser automation or private-network crawling. First fetch is baseline.
"""
from __future__ import annotations

import asyncio
import hashlib
import logging
import os
import re
import sqlite3
import time

from bs4 import BeautifulSoup
from fastapi import APIRouter, Header, HTTPException

import rss

router = APIRouter()
logger = logging.getLogger("relayharbor.website")
_poller: asyncio.Task | None = None
_lock = asyncio.Lock()
MAX_SOURCES = 50
MAX_TEXT = 60_000
MAX_SELECTOR = 200


def _server():
    import server
    return server


def _connections():
    import connections
    return connections


def validate_url(url: str) -> str:
    try:
        return rss.validate_url(url)
    except ValueError as exc:
        raise ValueError(str(exc).replace("RSS URL", "Website URL")
                         .replace("RSS feeds", "websites")
                         .replace("RSS feed", "website")) from None


def validate_selector(selector: str) -> str:
    if not isinstance(selector, str):
        raise ValueError("CSS selector must be a string")
    value = selector.strip()
    if len(value) > MAX_SELECTOR or any(ord(c) < 32 or ord(c) == 127 for c in value):
        raise ValueError("CSS selector is too long or contains control characters")
    if value:
        try:
            BeautifulSoup("<html><body></body></html>", "html.parser").select(value)
        except Exception:
            raise ValueError("Invalid CSS selector") from None
    return value


def initialize(database):
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS website_sources (
                connection_id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                css_selector TEXT NOT NULL DEFAULT '',
                poll_interval_minutes INTEGER NOT NULL DEFAULT 30,
                last_checked INTEGER NOT NULL DEFAULT 0,
                last_success INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                etag TEXT NOT NULL DEFAULT '',
                modified TEXT NOT NULL DEFAULT '',
                initialized INTEGER NOT NULL DEFAULT 0,
                content_hash TEXT NOT NULL DEFAULT '',
                content_text TEXT NOT NULL DEFAULT ''
            )
        """)
        db.commit()


def create_source(db, connection_id: str, url: str, interval: int, selector: str = ""):
    if not isinstance(interval, int) or isinstance(interval, bool) or not 15 <= interval <= 1440:
        raise ValueError("Polling interval must be 15–1440 minutes")
    if db.execute("SELECT COUNT(*) FROM website_sources").fetchone()[0] >= MAX_SOURCES:
        raise ValueError("Website monitor limit reached (50)")
    db.execute(
        """INSERT INTO website_sources
           (connection_id, url, css_selector, poll_interval_minutes) VALUES (?, ?, ?, ?)""",
        (connection_id, validate_url(url), validate_selector(selector), interval)
    )


def update_source(db, connection_id: str, url=None, interval=None, selector=None):
    row = db.execute(
        "SELECT url, css_selector, poll_interval_minutes FROM website_sources WHERE connection_id=?",
        (connection_id,)
    ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Website monitor not found")
    next_url = validate_url(url) if url is not None else row["url"]
    next_selector = validate_selector(selector) if selector is not None else row["css_selector"]
    next_interval = interval if interval is not None else row["poll_interval_minutes"]
    if not isinstance(next_interval, int) or isinstance(next_interval, bool) or not 15 <= next_interval <= 1440:
        raise ValueError("Polling interval must be 15–1440 minutes")
    if next_url != row["url"] or next_selector != row["css_selector"]:
        db.execute(
            """UPDATE website_sources SET url=?, css_selector=?, poll_interval_minutes=?,
               last_checked=0, last_success=0, last_error='', etag='', modified='',
               initialized=0, content_hash='', content_text='' WHERE connection_id=?""",
            (next_url, next_selector, next_interval, connection_id)
        )
    else:
        db.execute(
            "UPDATE website_sources SET poll_interval_minutes=? WHERE connection_id=?",
            (next_interval, connection_id)
        )


def public_source(connection_id: str):
    with _server().connection() as db:
        row = db.execute(
            """SELECT url, css_selector, poll_interval_minutes, last_checked,
                      last_success, last_error FROM website_sources WHERE connection_id=?""",
            (connection_id,)
        ).fetchone()
        if row is None:
            return {}
        # feed_url is the existing Android field for all pull-based sources.
        return {
            "feed_url": row["url"],
            "css_selector": row["css_selector"],
            "poll_interval_minutes": row["poll_interval_minutes"],
            "last_checked": row["last_checked"],
            "last_success": row["last_success"],
            "last_error": row["last_error"],
        }


def extract_text(data: bytes, selector: str = "") -> str:
    if not data or len(data) > rss.MAX_BYTES:
        raise ValueError("HTML body is empty or exceeds 1 MiB")
    soup = BeautifulSoup(data, "html.parser")
    for tag in soup(["script", "style", "noscript", "template", "svg", "iframe"]):
        tag.decompose()
    if selector:
        matches = soup.select(selector)
        if not matches:
            raise ValueError("CSS selector did not match any page content")
        elements = matches[:30]
    else:
        root = soup.body if soup.body else soup
        for tag in root(["nav", "footer", "header", "aside", "form"]):
            tag.decompose()
        elements = [root]
    lines = []
    for element in elements:
        for raw in element.get_text(separator="\n", strip=True).splitlines():
            line = re.sub(r"\s+", " ", raw).strip()
            if line:
                lines.append(line)
    result = "\n".join(lines)[:MAX_TEXT]
    if not result.strip():
        raise ValueError("No readable text in selected page area")
    return result


def changed_excerpt(old: str, new: str, limit: int = 850) -> str:
    seen = set(old.splitlines())
    added = [line for line in new.splitlines() if line not in seen]
    return "\n".join(added[:15]).strip()[:limit] or new[:limit]


async def _download(url: str, etag: str = "", modified: str = ""):
    return await rss._download(
        url, etag, modified,
        accept="text/html, application/xhtml+xml;q=0.9, */*;q=0.1",
        user_agent="RelayHarbor-WebsiteMonitor/0.6 (change detection)"
    )


async def refresh(connection_id: str, force: bool = False):
    server = _server()
    async with _lock:
        with server.connection() as db:
            row = db.execute(
                """SELECT c.*, s.url, s.css_selector, s.poll_interval_minutes,
                          s.last_checked, s.etag, s.modified, s.initialized,
                          s.content_hash, s.content_text
                   FROM connections c
                   JOIN website_sources s ON s.connection_id=c.id
                   WHERE c.id=? AND c.provider='website'""", (connection_id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail="Website monitor not found")
            if not row["enabled"]:
                raise HTTPException(status_code=403, detail="Website monitor disabled")
            now = int(time.time() * 1000)
            if not force and now - row["last_checked"] < row["poll_interval_minutes"] * 60_000:
                return {"new": 0, "status": "not_due"}
            source = dict(row)

        try:
            validate_url(source["url"])
            payload, etag, modified = await _download(
                source["url"], source["etag"], source["modified"]
            )
            if payload is None:
                content = None
                digest = source["content_hash"]
            else:
                content = extract_text(payload, source["css_selector"])
                digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
        except Exception as exc:
            message = re.sub(r"\s+", " ", str(exc))[:180] or "Website fetch failed"
            with server.connection() as db:
                db.execute(
                    "UPDATE website_sources SET last_checked=?, last_error=? WHERE connection_id=?",
                    (int(time.time() * 1000), message, connection_id)
                )
                db.commit()
            raise HTTPException(status_code=502, detail="Website check failed: " + message) from None

        inserted = 0
        with server.connection() as db:
            current = db.execute(
                "SELECT * FROM connections WHERE id=? AND provider='website'", (connection_id,)
            ).fetchone()
            state = db.execute(
                """SELECT url, css_selector, initialized, content_hash, content_text
                   FROM website_sources WHERE connection_id=?""", (connection_id,)
            ).fetchone()
            if (current is None or state is None or not current["enabled"] or
                state["url"] != source["url"] or
                state["css_selector"] != source["css_selector"]):
                return {"new": 0, "status": "changed"}
            if content is not None:
                changed = state["initialized"] == 1 and digest != state["content_hash"]
                if changed:
                    keyword = current["keyword"].strip().casefold()
                    excerpt = changed_excerpt(state["content_text"], content)
                    if not keyword or keyword in content.casefold():
                        card = {
                            "schemaVersion": 1,
                            "title": current["name"] + " に更新がありました",
                            "description": excerpt,
                            "badge": "WEB更新",
                            "url": source["url"]
                        }
                        result = _connections()._insert_event(
                            db, current, "website-" + digest,
                            "Web更新監視", "サイトの更新を検出しました: " + excerpt[:650],
                            card
                        )
                        if not result["duplicate"]:
                            inserted = 1
                db.execute(
                    """UPDATE website_sources SET last_checked=?, last_success=?,
                       last_error='', etag=?, modified=?, initialized=1,
                       content_hash=?, content_text=? WHERE connection_id=?""",
                    (int(time.time() * 1000), int(time.time() * 1000),
                     etag, modified, digest, content[:20_000], connection_id)
                )
            else:
                db.execute(
                    """UPDATE website_sources SET last_checked=?, last_success=?,
                       last_error='' WHERE connection_id=?""",
                    (int(time.time() * 1000), int(time.time() * 1000), connection_id)
                )
            db.commit()
        return {"new": inserted, "status": "ok" if content is not None else "not_modified"}


async def _run_worker():
    while True:
        await asyncio.sleep(20)
        try:
            with _server().connection() as db:
                now = int(time.time() * 1000)
                ids = [row["id"] for row in db.execute(
                    """SELECT c.id FROM connections c
                       JOIN website_sources w ON w.connection_id=c.id
                       WHERE c.provider='website' AND c.enabled=1
                       AND (? - w.last_checked >= w.poll_interval_minutes * 60000)
                       ORDER BY w.last_checked ASC LIMIT 50""",
                    (now,)
                ).fetchall()]
            for connection_id in ids:
                try:
                    await refresh(connection_id)
                except HTTPException as exc:
                    logger.warning("Website check failed for %s (HTTP %d)",
                                   connection_id, exc.status_code)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Website monitor polling failed")


def start_worker():
    global _poller
    if os.getenv("RELAY_WEBSITE_MONITOR_ENABLED", "1") == "1":
        _poller = asyncio.create_task(_run_worker(), name="relayharbor-website-monitor")


async def stop_worker():
    global _poller
    if _poller is not None:
        _poller.cancel()
        try:
            await _poller
        except asyncio.CancelledError:
            pass
        _poller = None
