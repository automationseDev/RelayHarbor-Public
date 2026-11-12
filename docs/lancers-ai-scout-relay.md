# Lancers AI Scout → RelayHarbor migration

The existing Scout pipeline stays intact: Gmail ingestion → SQLite deduplication →
Python prefilter → Dify evaluation → job review. Only final notification delivery changes.

**Important:** RelayHarbor currently polls roughly every 30 seconds **while the
Android app is open**. It does **not** provide background push notification.
Keep Slack as a parallel destination when immediate alerts matter.

## 1. Android: create a receiver

Open **連携 → 連携先を追加 → Webhook** and set:

- Name: `Lancers AI Scout`
- Destination channel: `AI案件スカウト`
- Minimum score: `0` initially (Scout already filters jobs)
- Keyword: empty

Copy the returned unique Webhook URL and Bearer Token. The token is displayed
only once and must not be published. This is a **per-connection** token, not
`RELAY_CLIENT_TOKEN` or a Slack webhook URL.

## 2. NAS: add Scout environment variables

Edit the local Scout `.env` (or actual Compose environment file)
under your Scout Docker working directory:

```dotenv
RELAYHARBOR_INBOUND_URL=https://YOUR-RELAY-DOMAIN/v1/inbound/YOUR-CONNECTION-ID
RELAYHARBOR_INBOUND_TOKEN=YOUR-ONE-TIME-BEARER-TOKEN
```

Make sure Docker Compose forwards both environment variables to the Scout
container. Never commit the secrets. Keep the original Slack notification path
until the new route is tested.

## 3. Replace only the outgoing Slack POST

Find Scout's existing Slack sender on the NAS:

```bash
cd /path/to/your/scout-working-directory
grep -R -n --include='*.py' -E 'SLACK_WEBHOOK_URL|hooks.slack.com|requests.post|httpx.post|ALERT_MODE' . --exclude-dir=.git --exclude-dir=__pycache__
```

Scout's source code is not in the public RelayHarbor repository, so locate the
actual notifier before editing it. Preserve Gmail, Dify, SQLite and review UI.

The existing Slack sender already builds a dictionary `payload` with
`blocks`. Replace its HTTP request, or add a parallel one, using:

```python
import json
import os
from urllib.request import Request, urlopen

def send_relay_notification(payload: dict, job_id=None):
    url = os.environ["RELAYHARBOR_INBOUND_URL"]
    token = os.environ["RELAYHARBOR_INBOUND_TOKEN"]
    message = dict(payload)
    if job_id is not None:
        message["id"] = "lancers-" + str(job_id)[:150]
    request = Request(
        url,
        data=json.dumps(message, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + token,
        },
        method="POST",
    )
    with urlopen(request, timeout=15) as response:
        if response.status != 201:
            raise RuntimeError("RelayHarbor returned HTTP " + str(response.status))
```

Call `send_relay_notification(payload, job_id)` at the existing notifier.
An idempotent `job_id` avoids duplicate events on retries.
RelayHarbor converts the Slack Block Kit header, sections, fields, context and
button links into a notification card without changing Scout's evaluation logic.

If Scout only has `ALERT_MODE=slack`, temporarily retain that
branch for Block Kit formatting, and redirect its HTTP POST to this function.
If it already supports multiple destinations, use a separate Relay destination.

Restart the Scout service only (replace `scout` if Compose shows
another service name):

```bash
cd /path/to/your/scout-working-directory
docker compose config --services
docker compose up -d --no-deps --force-recreate scout
```

## 4. Test safely

On Android, test the new connection using **テスト通知** and **同期**.
Then trigger a single harmless Scout notification and verify the card appears
under `AI案件スカウト`. Disable Slack only when this works.

If Cloudflare Access protects the Relay URL, configure a narrowly scoped
service-token policy or appropriate ingress exception for that one inbound
path. Do not bypass Access for the entire application or remove the Bearer check.

## 5. Integration export/import

**設定をエクスポート** saves non-secret fields to portable JSON: name,
provider, destination, enabled, score/keyword filters, RSS feed URL and interval.
It never includes access tokens, credentials, webhook IDs, saved events or
historical RSS state.

**設定をインポート** validates the whole file and creates missing connections
atomically, skipping matching ones. Webhook tokens and IDs are regenerated and
displayed once; update each source service with its new values.
RSS does not need an inbound token.

The Android app and Relay API must both be updated. NAS deployments can pick
up the tested public GHCR `stable` image with
`bash scripts/nas-auto-update.sh stable`, preserving the original
`relay_relay_data` volume. Never use `down -v`.
