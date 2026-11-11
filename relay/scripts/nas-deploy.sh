#!/usr/bin/env bash
# Deploy one tested GHCR image without rebuilding on the NAS.
# Existing Compose project name, network, runtime .env and named volume are preserved.
set -Eeuo pipefail

tag="${1:-}"
if [[ ! "$tag" =~ ^(edge|stable|v[0-9]+\.[0-9]+\.[0-9]+|sha-[0-9a-f]{12})$ ]]; then
  echo "Usage: bash scripts/nas-deploy.sh {stable|edge|vX.Y.Z|sha-<12 lowercase hex>}" >&2
  exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
[[ -f .env ]] || { echo "Missing relay/.env (secrets must remain on NAS)" >&2; exit 1; }

image_repo="ghcr.io/automationsedev/relayharbor-public-relay"
requested="$image_repo:$tag"
compose_file="$root/compose.registry.yaml"
state="$root/.deploy"
backups="$state/backups"
mkdir -p "$backups"
chmod 700 "$state" "$backups"
umask 077

lock="$state/deploy.lock"
if ! mkdir "$lock" 2>/dev/null; then
  echo "Another deployment is running (or stale lock at $lock)" >&2
  exit 1
fi
trap 'rmdir "$lock" 2>/dev/null || true' EXIT

container="relay-relay-1"
docker inspect "$container" >/dev/null 2>&1 || {
  echo "Expected existing $container. This updater is for the current Compose project." >&2
  exit 1
}
project="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
service="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.service" }}' "$container")"
volume="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' "$container")"
if [[ "$project" != "relay" || "$service" != "relay" || "$volume" != "relay_relay_data" ]]; then
  echo "Unexpected Compose project/service/volume; refusing to alter other data ($project/$service/$volume)" >&2
  exit 1
fi

echo "Pulling $requested (only RelayHarbor; other services are untouched)"
docker pull "$requested"
digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$requested" |
  grep -m1 "^$image_repo@sha256:" || true)"
[[ "$digest" =~ ^ghcr\.io/automationsedev/relayharbor-public-relay@sha256:[a-f0-9]{64}$ ]] || {
  echo "Could not resolve a trusted immutable GHCR digest for $requested" >&2
  exit 1
}
if [[ -f "$state/active.env" ]] &&
   [[ "$(sed -n 's/^RELAY_IMAGE=//p' "$state/active.env")" == "$digest" ]]; then
  if curl --silent --show-error --fail --max-time 5 http://127.0.0.1:8088/healthz |
     grep -Eq '"ok":[[:space:]]*true'; then
    echo "Already running $digest and healthy"
    exit 0
  fi
fi

old_image_id="$(docker inspect -f '{{.Image}}' "$container")"
stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
rollback_image="relayharbor/rollback:$stamp"
docker tag "$old_image_id" "$rollback_image"

backup="relay-before-$stamp.sqlite3"
echo "Taking a consistent SQLite snapshot before replacement: $backup"
docker exec "$container" python -c '
import os, sqlite3, sys
source = os.getenv("RELAY_DATABASE", "/data/relayharbor.sqlite3")
if not os.path.isfile(source):
    raise SystemExit("Existing Relay SQLite database not found: " + source)
destination = "/data/" + sys.argv[1]
with sqlite3.connect(source) as src, sqlite3.connect(destination) as dst:
    src.backup(dst)
    if dst.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
        raise SystemExit("SQLite backup integrity check failed")
' "$backup"
docker cp "$container:/data/$backup" "$backups/$backup"
docker exec "$container" rm "/data/$backup"
chmod 600 "$backups/$backup"

run_image() {
  local image="$1"
  echo "Recreating ONLY relay from $image"
  RELAY_IMAGE="$image" docker compose --project-name relay \
    -f "$compose_file" up -d --no-deps --no-build --pull never \
    --force-recreate relay
}

healthy() {
  local expected="${1:-}"
  local response=""
  for attempt in {1..20}; do
    response="$(curl -fsS --max-time 3 http://127.0.0.1:8088/healthz 2>/dev/null)" || true
    if [[ "$response" =~ \"ok\"[[:space:]]*:[[:space:]]*true ]] &&
       [[ "$response" =~ \"service\"[[:space:]]*:[[:space:]]*\"relayharbor\" ]]; then
      if [[ -z "$expected" || "$response" == *"\"version\":\"$expected\""* ]]; then
        return 0
      fi
    fi
    sleep 3
  done
  return 1
}

expected_version=""
if [[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  expected_version="${BASH_REMATCH[1]}"
fi

if ! run_image "$digest" || ! healthy "$expected_version"; then
  echo "Deployment failed its health check; attempting rollback to previous cached image" >&2
  if run_image "$rollback_image" && healthy ""; then
    echo "Previous application image restored. SQLite backup: $backups/$backup" >&2
  else
    echo "Automatic rollback also failed; review 'docker logs relay-relay-1'." >&2
    echo "SQLite backup: $backups/$backup" >&2
  fi
  exit 1
fi

printf 'RELAY_IMAGE=%s\n' "$rollback_image" > "$state/previous.env"
printf 'RELAY_IMAGE=%s\n' "$digest" > "$state/active.env"
printf '%s image=%s source=%s backup=%s\n' \
  "$(date -u +%FT%TZ)" "$digest" "$tag" "$backup" >> "$state/history.log"
echo "SUCCESS: RelayHarbor deployed from immutable digest $digest"
echo "Previous image available via: bash scripts/nas-rollback.sh"
echo "SQLite backup: $backups/$backup (retained; not automatically restored)"
