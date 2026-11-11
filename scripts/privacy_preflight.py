#!/usr/bin/env python3
"""Check tracked files for secrets and environment-specific identifiers.

Run before changing repository visibility:
    python3 scripts/privacy_preflight.py
    python3 scripts/privacy_preflight.py --history

The history mode also checks commit email metadata and historical patch content.
Neither mode guarantees that secrets have never appeared in releases, Actions logs,
archived PR refs, GitHub caches, or external mirrors.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SELF = "scripts/privacy_preflight.py"

# Project/repository identifiers such as automationseDev and the fixed Android
# applicationId are intentionally excluded: changing them would break releases.
RULES = {
    "non-example Relay endpoint": re.compile(
        r"(?i)https://relay[.](?!example[.](?:com|net|org)\b)[a-z0-9.-]+"
    ),
    "host-specific NAS path": re.compile(r"/volume\d+/(?:docker\d*|homes|home)/"),
    "personal address or host name": re.compile(
        r"(?i)\b(?:ponta@|SynologyNas|yokkaichi|四日市)\b"
    ),
    "email address": re.compile(
        r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"
    ),
    "embedded GitHub access token": re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{18,}\b"),
    "embedded provider access token": re.compile(
        r"\b(?:sk-proj-|sk-live-|xoxb-|xoxp-)[A-Za-z0-9_-]{18,}"
    ),
    "Slack incoming webhook": re.compile(
        r"https://hooks[.]slack[.]com/services/[A-Za-z0-9/_-]+"
    ),
    "private key PEM header": re.compile(
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
    ),
    "AWS key ID": re.compile(r"\bAKIA[A-Z0-9]{16}\b"),
}
SAFE_EMAIL_DOMAINS = {"example.com", "example.org", "example.net",
                      "users.noreply.github.com"}
RUNTIME_FILES = re.compile(
    r"(?i)(?:^|/)(?:\.env(?:\..+)?|credentials[.]json|service-account[.]json|"
    r"google-services[.]json|[^/]+[.](?:jks|keystore|p12|pem|key|sqlite3?|db))$"
)
IGNORE_FILES = {SELF}
MAX_EXAMPLES = 25


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def suspicious_email(value: str) -> bool:
    domain = value.rsplit("@", 1)[-1].lower()
    return not any(domain == safe or domain.endswith("." + safe) for safe in SAFE_EMAIL_DOMAINS)


def scan_line(line: str) -> list[str]:
    issues = []
    for name, pattern in RULES.items():
        for match in pattern.finditer(line):
            if name == "email address" and not suspicious_email(match.group()):
                continue
            issues.append(name)
            break
    return issues


def scan_current() -> int:
    files = git("ls-files", "-z").decode().split("\0")
    problems: list[str] = []
    for name in files:
        if not name or name in IGNORE_FILES:
            continue
        if RUNTIME_FILES.search(name) and not name.endswith(".env.example"):
            problems.append(f"{name}: tracked secret/runtime file")
            continue
        file = ROOT / name
        if not file.is_file():
            continue
        try:
            data = file.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for line_no, line in enumerate(data.splitlines(), 1):
            for issue in scan_line(line):
                problems.append(f"{name}:{line_no}: {issue}")
    if problems:
        print("CURRENT TREE: review needed")
        for item in problems[:MAX_EXAMPLES]:
            print("  " + item)
        if len(problems) > MAX_EXAMPLES:
            print(f"  ... and {len(problems)-MAX_EXAMPLES} other findings")
    else:
        print("CURRENT TREE: no matches for the configured high-risk rules")
    return len(problems)


def scan_history() -> int:
    entries = git("log", "--all", "--format=%H %ae %ce").decode().splitlines()
    bad_commits = []
    for entry in entries:
        parts = entry.split(" ", 2)
        if len(parts) == 3 and (
            suspicious_email(parts[1]) or suspicious_email(parts[2])
        ):
            bad_commits.append(parts[0][:12])
    # Check diffs for the known private host and NAS-directory family.
    # Scan only historical patches, not this audit script's own source code.
    raw = git("log", "--all", "-p", "--format=commit:%H", "--no-ext-diff")
    text = raw.decode("utf-8", errors="replace")
    history_hits = []
    current_commit = "unknown"
    current_file = ""
    for line in text.splitlines():
        if line.startswith("commit:"):
            current_commit = line[7:19]
        elif line.startswith("diff --git "):
            current_file = line.split(" b/", 1)[-1]
        elif line.startswith(("+", "-")) and not line.startswith(("+++", "---")):
            if current_file == SELF:
                continue
            if (RULES["non-example Relay endpoint"].search(line) or
                RULES["host-specific NAS path"].search(line) or
                RULES["private key PEM header"].search(line) or
                RULES["embedded GitHub access token"].search(line) or
                RULES["Slack incoming webhook"].search(line)):
                history_hits.append(f"{current_commit} {current_file}")
    if bad_commits:
        print(f"HISTORY: {len(bad_commits)} commits contain non-noreply author/committer email metadata")
        print("  Example commit IDs: " + ", ".join(bad_commits[:5]))
    if history_hits:
        print(f"HISTORY: {len(history_hits)} patch lines contain environment-specific or secret patterns")
        for item in list(dict.fromkeys(history_hits))[:MAX_EXAMPLES]:
            print("  " + item)
    if not bad_commits and not history_hits:
        print("HISTORY: no matches for the configured rules")
    return len(bad_commits) + len(history_hits)


def main() -> int:
    problems = scan_current()
    if "--history" in sys.argv:
        problems += scan_history()
    if problems:
        print("NOT READY FOR PUBLIC VISIBILITY: address findings before changing visibility")
        return 1
    print("Pattern checks passed. Manual review is still required.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
