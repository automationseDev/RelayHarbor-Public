#!/usr/bin/env bash
# Restore the previously cached application image only. Database is NOT reverted.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
state="$root/.deploy"
previous="$state/previous.env"
[[ -f "$previous" ]] || {
  echo "No previous image recorded. Deploy once with nas-deploy.sh first." >&2
  exit 1
}
image="$(sed -n 's/^RELAY_IMAGE=//p' "$previous")"
[[ "$image" =~ ^relayharbor/rollback:[A-Za-z0-9._-]+$ ]] || {
  echo "Invalid local rollback image reference" >&2
  exit 1
}
docker image inspect "$image" >/dev/null || {
  echo "Previous image is no longer cached (possibly removed by docker image prune)" >&2
  exit 1
}
[[ -f .env ]] || { echo "Missing runtime .env" >&2; exit 1; }
echo "Rolling back ONLY relay to cached image $image"
RELAY_IMAGE="$image" docker compose --project-name relay \
  -f compose.registry.yaml up -d --no-deps --no-build --pull never \
  --force-recreate relay
healthy=0
for i in {1..20}; do
  if curl -fsS --max-time 3 http://127.0.0.1:8088/healthz 2>/dev/null |
     grep -Eq '"ok":[[:space:]]*true'; then
    healthy=1
    break
  fi
  sleep 3
done
[[ "$healthy" -eq 1 ]] || {
  echo "Rollback container did not pass the health check; inspect docker logs" >&2
  exit 1
}
printf 'RELAY_IMAGE=%s\n' "$image" > "$state/active.env"
echo "Application rollback successful. Database was NOT reverted."
echo "Previous SQLite backups remain under $state/backups/."
