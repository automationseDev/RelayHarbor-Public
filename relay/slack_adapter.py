"""Convert Slack Incoming Webhook Block Kit JSON to RelayHarbor's rich-card format.

Intended for existing bots such as Lancers AI Scout. The Relay inbound endpoint
still requires its own Bearer token; posting Slack JSON to a Slack webhook URL
is unrelated to the Relay service.
"""
from __future__ import annotations

import re
import image_metadata


def _text(element) -> str:
    if not isinstance(element, dict):
        return ""
    text = element.get("text", "")
    if isinstance(text, str):
        return text.strip()
    if isinstance(text, dict):
        return str(text.get("text", "")).strip()
    return ""


def normalize_slack_payload(value: dict) -> dict:
    if not isinstance(value, dict) or not isinstance(value.get("blocks"), list):
        return value
    title = ""
    parts = []
    url = ""
    thumbnail_url = image_metadata.thumbnail_from_mapping(value)
    for block in value["blocks"][:30]:
        if not isinstance(block, dict):
            continue
        kind = block.get("type")
        if not thumbnail_url:
            thumbnail_url = image_metadata.thumbnail_from_mapping(block)
            accessory = block.get("accessory")
            if not thumbnail_url and isinstance(accessory, dict) and accessory.get("type") == "image":
                thumbnail_url = image_metadata.thumbnail_from_mapping(accessory)
        if kind == "header":
            title = _text(block.get("text"))[:100] or title
        elif kind == "section":
            line = _text(block.get("text"))
            if line:
                parts.append(line)
            for field in block.get("fields", [])[:16]:
                line = _text(field)
                if line:
                    parts.append(line)
        elif kind == "context":
            for element in block.get("elements", [])[:12]:
                line = _text(element)
                if line:
                    parts.append(line)
        elif kind == "actions":
            for element in block.get("elements", [])[:10]:
                if not isinstance(element, dict):
                    continue
                link = element.get("url")
                if not url and isinstance(link, str) and link.startswith("https://"):
                    url = link[:2048]
    fallback = value.get("text", "")
    if not isinstance(fallback, str):
        fallback = ""
    body = "\n".join(parts).strip()[:8500]
    text = fallback.strip() or body or title
    if not text.strip():
        return {"id": value.get("id"), "text": ""}
    card = {
        "schemaVersion": 1,
        "title": title or "Lancers AI Scout",
        "description": body[:3000] or text[:3000],
        "badge": "Scout",
    }
    if url:
        card["url"] = url
    if thumbnail_url:
        card["thumbnail_url"] = thumbnail_url
    explicit_score = value.get("score")
    if type(explicit_score) in (int, float) and 0 <= explicit_score <= 100:
        card["score"] = int(explicit_score)
    return {
        "id": value.get("id"),
        "sender": "Lancers AI Scout",
        "text": text[:10000],
        "card": card,
    }
