"""Metadata-only thumbnail extraction for RSS, Webhooks and Slack blocks.

Never downloads image bytes on the server. Only passes HTTPS public-host URLs
to the Android client, where the user can opt out of remote image fetching.
"""
from __future__ import annotations

import ipaddress
from html.parser import HTMLParser
from urllib.parse import urlsplit

IMAGE_FIELDS = (
    "thumbnail_url", "thumbnailUrl", "image_url", "imageUrl",
    "thumbnail", "image",
)


def public_image_url(candidate) -> str:
    if isinstance(candidate, dict):
        candidate = candidate.get("url") or candidate.get("href") or candidate.get("src")
    if not isinstance(candidate, str):
        return ""
    url = candidate.strip()
    if not 1 <= len(url) <= 2048 or any(ord(c) < 32 or ord(c) == 127 for c in url):
        return ""
    try:
        parsed = urlsplit(url)
        hostname = (parsed.hostname or "").lower().rstrip(".")
        if (parsed.scheme.lower() != "https" or not hostname or
                parsed.username is not None or parsed.password is not None or
                parsed.fragment or parsed.port not in (None, 443) or
                hostname == "localhost" or hostname.endswith((
                    ".localhost", ".local", ".internal", ".home.arpa",
                    ".test", ".invalid",
                )) or "." not in hostname or hostname.startswith(".") or
                ".." in hostname):
            return ""
        try:
            ipaddress.ip_address(hostname)
        except ValueError:
            pass
        else:
            return ""
        return url
    except ValueError:
        return ""


def thumbnail_from_mapping(mapping) -> str:
    if not isinstance(mapping, dict):
        return ""
    for key in IMAGE_FIELDS:
        value = public_image_url(mapping.get(key))
        if value:
            return value
    images = mapping.get("images")
    if isinstance(images, list):
        for item in images[:5]:
            value = public_image_url(item)
            if value:
                return value
    return ""


def enrich_webhook_thumbnail(payload: dict) -> dict:
    """Also accepts top-level image metadata, without changing existing card fields."""
    if not isinstance(payload, dict):
        return payload
    value = thumbnail_from_mapping(payload)
    if not value:
        return payload
    existing = payload.get("card")
    card = dict(existing) if isinstance(existing, dict) else {}
    if thumbnail_from_mapping(card):
        return payload
    if not card:
        title = payload.get("title") or payload.get("text") or "画像付きメッセージ"
        card = {"schemaVersion": 1, "title": str(title)[:140],
                "description": str(payload.get("text") or "")[:2000]}
    card["thumbnail_url"] = value
    return {**payload, "card": card}


class _FirstImg(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.thumbnail = ""

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "img" or self.thumbnail:
            return
        values = dict(attrs)
        self.thumbnail = public_image_url(values.get("src")) or public_image_url(
            values.get("data-src")
        )


def rss_entry_thumbnail(entry) -> str:
    """Common RSS/Atom media metadata; last resort: image in summary HTML."""
    for key in ("media_thumbnail", "media_content"):
        media = entry.get(key, [])
        if isinstance(media, list):
            for item in media[:5]:
                if not isinstance(item, dict):
                    continue
                if key == "media_content":
                    content_type = str(item.get("type", "")).lower()
                    if content_type and not content_type.startswith("image/"):
                        continue
                value = public_image_url(item.get("url"))
                if value:
                    return value
    for item in entry.get("enclosures", [])[:5]:
        if isinstance(item, dict) and str(item.get("type", "")).lower().startswith("image/"):
            value = public_image_url(item.get("href") or item.get("url"))
            if value:
                return value
    image = public_image_url(entry.get("image"))
    if image:
        return image
    parser = _FirstImg()
    try:
        parser.feed(str(entry.get("summary", ""))[:10000])
        return parser.thumbnail
    except (ValueError, TypeError):
        return ""
