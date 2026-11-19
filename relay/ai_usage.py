"""Private, token-free AI usage ledger. Stores only request metadata.

Amounts are estimates from USER-DEFINED USD per million token rates. Unknown
provider usage or unset prices never silently become free/zero-cost calls.
Dates use JST (+09:00). No prompts, answers, credentials or raw API payloads.
"""
from __future__ import annotations

import csv
import io
import math
import sqlite3
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

JST = timezone(timedelta(hours=9))
PERIODS = frozenset({1, 7, 30, 90, 365})


def initialize(database: Path) -> None:
    with sqlite3.connect(database, timeout=15) as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS ai_usage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at INTEGER NOT NULL,
                profile_id TEXT NOT NULL,
                profile_name TEXT NOT NULL,
                kind TEXT NOT NULL,
                model TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('ok','error')),
                input_tokens INTEGER,
                output_tokens INTEGER,
                latency_ms INTEGER NOT NULL,
                error_type TEXT NOT NULL DEFAULT ''
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS ai_usage_date_idx ON ai_usage(occurred_at)")
        db.execute("""
            CREATE TABLE IF NOT EXISTS ai_usage_rates (
                profile_id TEXT NOT NULL,
                model TEXT NOT NULL,
                input_usd_per_million REAL NOT NULL CHECK(input_usd_per_million >= 0),
                output_usd_per_million REAL NOT NULL CHECK(output_usd_per_million >= 0),
                PRIMARY KEY(profile_id, model)
            )
        """)
        db.commit()


def positive_tokens(value) -> int | None:
    """A provider may omit usage; never estimate from character count."""
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        return None
    return min(value, 1_000_000_000)


def extract_usage(kind: str, data: dict) -> tuple[int | None, int | None]:
    if not isinstance(data, dict):
        return None, None
    usage = data.get("usage")
    if kind == "dify":
        usage = (data.get("metadata") or {}).get("usage") or usage
        if not isinstance(usage, dict):
            return None, None
        return (positive_tokens(usage.get("prompt_tokens")),
                positive_tokens(usage.get("completion_tokens")))
    if not isinstance(usage, dict):
        return None, None
    if kind in ("openai", "anthropic"):
        return (positive_tokens(usage.get("input_tokens")),
                positive_tokens(usage.get("output_tokens")))
    return (positive_tokens(usage.get("prompt_tokens")),
            positive_tokens(usage.get("completion_tokens")))


def record(database: Path, *, profile_id: str, profile_name: str, kind: str,
           model: str, status: str, input_tokens: int | None,
           output_tokens: int | None, latency_ms: int,
           error_type: str = "") -> None:
    with sqlite3.connect(database, timeout=15) as db:
        db.execute(
            """INSERT INTO ai_usage (occurred_at,profile_id,profile_name,kind,model,status,
               input_tokens,output_tokens,latency_ms,error_type)
               VALUES (?,?,?,?,?,?,?,?,?,?)""",
            (int(time.time()), profile_id, profile_name[:60], kind, model[:120],
             status, positive_tokens(input_tokens), positive_tokens(output_tokens),
             max(0, min(int(latency_ms), 600_000)), error_type[:24])
        )
        db.commit()


def _cutoff(days: int) -> int:
    if days not in PERIODS:
        raise ValueError("Unsupported period")
    now = datetime.now(JST)
    start = (now - timedelta(days=days - 1)).replace(
        hour=0, minute=0, second=0, microsecond=0)
    return int(start.timestamp())


def set_rates(database: Path, profile_id: str, model: str,
              input_rate: float | None, output_rate: float | None) -> None:
    if not profile_id or len(profile_id) > 80 or len(model) > 120:
        raise ValueError("Invalid profile/model")
    if (input_rate is None) != (output_rate is None):
        raise ValueError("Set both rates or leave both empty")
    for value in (input_rate, output_rate):
        if value is not None and (not math.isfinite(value) or value < 0 or value > 1000):
            raise ValueError("Invalid USD per million token rate")
    with sqlite3.connect(database, timeout=15) as db:
        if input_rate is None:
            db.execute("DELETE FROM ai_usage_rates WHERE profile_id=? AND model=?",
                       (profile_id, model))
        else:
            db.execute("""
                INSERT INTO ai_usage_rates
                (profile_id,model,input_usd_per_million,output_usd_per_million)
                VALUES (?,?,?,?)
                ON CONFLICT(profile_id,model) DO UPDATE SET
                input_usd_per_million=excluded.input_usd_per_million,
                output_usd_per_million=excluded.output_usd_per_million
            """, (profile_id, model, input_rate, output_rate))
        db.commit()


def summary(database: Path, days: int, profiles: list[dict] | None = None) -> dict:
    cutoff = _cutoff(days)
    current = {p["id"]: p for p in (profiles or [])}
    with sqlite3.connect(database, timeout=15) as db:
        db.row_factory = sqlite3.Row
        rates = {
            (r["profile_id"], r["model"]): (r["input_usd_per_million"],
                                               r["output_usd_per_million"])
            for r in db.execute("SELECT * FROM ai_usage_rates")
        }
        rows = db.execute("""
            SELECT profile_id, MAX(profile_name) AS profile_name, kind, model,
                   COUNT(*) AS requests,
                   SUM(CASE WHEN status='ok' THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN status='error' THEN 1 ELSE 0 END) AS error_count,
                   SUM(COALESCE(input_tokens,0)) AS input_tokens,
                   SUM(COALESCE(output_tokens,0)) AS output_tokens,
                   SUM(CASE WHEN input_tokens IS NULL OR output_tokens IS NULL
                            THEN 1 ELSE 0 END) AS unreported_calls,
                   CAST(ROUND(AVG(latency_ms)) AS INTEGER) AS average_latency_ms
            FROM ai_usage WHERE occurred_at>=?
            GROUP BY profile_id, kind, model
            ORDER BY requests DESC, profile_name, model
        """, (cutoff,)).fetchall()
        days_rows = db.execute("""
            SELECT date(occurred_at,'unixepoch','+9 hours') AS day,
                   COUNT(*) AS requests,
                   SUM(CASE WHEN status='error' THEN 1 ELSE 0 END) AS errors,
                   SUM(COALESCE(input_tokens,0)) AS input_tokens,
                   SUM(COALESCE(output_tokens,0)) AS output_tokens
            FROM ai_usage WHERE occurred_at>=?
            GROUP BY day ORDER BY day
        """, (cutoff,)).fetchall()
        first = db.execute("SELECT MIN(occurred_at) FROM ai_usage").fetchone()[0]

    models = []
    for row in rows:
        item = dict(row)
        profile_id, model = item["profile_id"], item["model"]
        active = current.get(profile_id)
        if active and active.get("model", "") == model:
            item["profile_name"] = active["name"]
        rate = rates.get((profile_id, model))
        item["input_usd_per_million"] = rate[0] if rate else None
        item["output_usd_per_million"] = rate[1] if rate else None
        if rate and item["unreported_calls"] == 0:
            item["estimated_usd"] = round((
                item["input_tokens"] * rate[0] +
                item["output_tokens"] * rate[1]) / 1_000_000, 6)
        else:
            item["estimated_usd"] = None
        models.append(item)

    present = {(m["profile_id"], m["model"]) for m in models}
    for profile in (profiles or []):
        model_id = profile["model"] or "Dify app"
        key = (profile["id"], model_id)
        if key in present:
            continue
        rate = rates.get(key)
        models.append({
            "profile_id": profile["id"], "profile_name": profile["name"],
            "kind": profile["kind"], "model": model_id,
            "requests": 0, "success_count": 0, "error_count": 0,
            "input_tokens": 0, "output_tokens": 0, "unreported_calls": 0,
            "average_latency_ms": 0, "estimated_usd": 0.0 if rate else None,
            "input_usd_per_million": rate[0] if rate else None,
            "output_usd_per_million": rate[1] if rate else None
        })
    models.sort(key=lambda m: (-m["requests"], m["profile_name"], m["model"]))
    totals = {
        "requests": sum(m["requests"] for m in models),
        "success_count": sum(m["success_count"] for m in models),
        "error_count": sum(m["error_count"] for m in models),
        "input_tokens": sum(m["input_tokens"] for m in models),
        "output_tokens": sum(m["output_tokens"] for m in models),
        "unreported_calls": sum(m["unreported_calls"] for m in models),
        "average_latency_ms": (round(sum(m["average_latency_ms"] * m["requests"]
            for m in models) / sum(m["requests"] for m in models))
            if any(m["requests"] for m in models) else 0),
        "estimated_usd": (
            round(sum(m["estimated_usd"] for m in models), 6)
            if all(m["estimated_usd"] is not None for m in models if m["requests"]) and
               any(m["requests"] for m in models) else None),
        "estimated_partial_usd": round(sum(m["estimated_usd"] or 0.0 for m in models), 6),
        "unpriced_models": sum(1 for m in models if m["requests"] and
                               m["estimated_usd"] is None),
    }
    daily_by_day = {row["day"]: dict(row) for row in days_rows}
    start = datetime.fromtimestamp(cutoff, JST).date()
    daily = []
    for offset in range(days):
        label = (start + timedelta(days=offset)).isoformat()
        daily.append(daily_by_day.get(label, {
            "day": label, "requests": 0, "errors": 0, "input_tokens": 0, "output_tokens": 0
        }))
    return {"period_days": days, "timezone": "Asia/Tokyo",
            "tracking_since": first, "totals": totals,
            "models": models, "daily": daily}


def csv_export(database: Path, days: int, profiles: list[dict] | None = None) -> str:
    """Aggregated daily CSV: no prompts, raw replies, keys or customer data."""
    _cutoff(days)
    with sqlite3.connect(database, timeout=15) as db:
        db.row_factory = sqlite3.Row
        rows = db.execute("""
            SELECT date(occurred_at,'unixepoch','+9 hours') AS day,
                   profile_id, MAX(profile_name) AS profile_name, kind, model,
                   COUNT(*) AS requests,
                   SUM(CASE WHEN status='ok' THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN status='error' THEN 1 ELSE 0 END) AS error_count,
                   SUM(COALESCE(input_tokens,0)) AS input_tokens,
                   SUM(COALESCE(output_tokens,0)) AS output_tokens,
                   SUM(CASE WHEN input_tokens IS NULL OR output_tokens IS NULL
                            THEN 1 ELSE 0 END) AS unreported_calls,
                   CAST(ROUND(AVG(latency_ms)) AS INTEGER) AS average_latency_ms
            FROM ai_usage WHERE occurred_at>=?
            GROUP BY day, profile_id, kind, model
            ORDER BY day DESC, profile_name, model
        """, (_cutoff(days),)).fetchall()
    output = io.StringIO()
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(["date_jst", "profile_id", "profile_name", "provider",
                     "model", "requests", "success_count", "error_count",
                     "input_tokens", "output_tokens", "unreported_calls",
                     "average_latency_ms"])
    # Prefix spreadsheet formula indicators in any user-supplied display fields.
    def safe_cell(value):
        text = str(value)
        return "'" + text if text.lstrip().startswith(("=", "+", "-", "@")) else text
    for row in rows:
        writer.writerow([safe_cell(v) for v in tuple(row)])
    return output.getvalue()
