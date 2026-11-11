#!/usr/bin/env bash
# Optional Synology Task Scheduler entry: checks only this Relay image.
# The "stable" tag is only advanced by trusted GHCR release publications.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
state="$root/.deploy"
[[ -f "$state/active.env" ]] || {
  echo "Initial installation is manual: run bash scripts/nas-deploy.sh stable" >&2
  exit 1
}
channel="${1:-stable}"
if [[ "$channel" != "stable" && "$channel" != "edge" ]]; then
  echo "Use stable (default) or edge" >&2
  exit 2
fi
repo="ghcr.io/automationsedev/relayharbor-public-relay"
docker pull "$repo:$channel"
digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$repo:$channel" |
  grep -m1 "^$repo@sha256:" || true)"
[[ "$digest" =~ ^ghcr\.io/automationsedev/relayharbor-public-relay@sha256:[a-f0-9]{64}$ ]] || {
  echo "Cannot resolve GHCR digest" >&2; exit 1;
}
active="$(sed -n 's/^RELAY_IMAGE=//p' "$state/active.env")"
if [[ "$active" == "$digest" ]]; then
  echo "No update: $digest"
else
  echo "New $channel image available. Deploying pinned digest with backup/rollback..."
  bash "$root/scripts/nas-deploy.sh" "$channel"
fi
