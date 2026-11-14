# RelayHarbor Relay (v0.6.0)

**Recommended GHCR image deployment:** [Container delivery guide](../docs/container-delivery.md). GHCR image publishing is done by GitHub Actions; NAS deployment is opt-in. The original build-based `compose.yaml` remains supported for development.

Optional portable webhook + Dify/OpenAI gateway. Host on a VPS, cloud container or Docker-capable NAS. Never embed provider master API keys inside the Android APK.

## Start

1. Open the relay/ folder and copy .env.example to .env.
2. Generate TWO DIFFERENT secrets, each using: python -c 'import secrets; print(secrets.token_urlsafe(48))'
3. Set RELAY_CLIENT_TOKEN and RELAY_WEBHOOK_TOKEN in .env. Keep both private.
4. Configure DIFY_API_URL and DIFY_API_KEY and/or OPENAI_API_KEY.
5. Run: docker compose up -d --build
6. Put an HTTPS reverse proxy in front of the localhost-only port 8088. Do not expose port 8088 directly. Back up the relay_data volume.

## Android settings

- Mode: Cloud or Self-hosted
- Relay server URL: https://relay.example.com (a domain you own and configure)
- Relay access token: RELAY_CLIENT_TOKEN (stored encrypted on device)
- AI: Dify, OpenAI or None

The app polls events approximately every 30 seconds while active, and provides manual sync. Background push notifications are NOT yet included. Local Room chat remains available without the relay.

## Generic webhook example

POST https://relay.example.com/v1/webhooks/ai-scout
Authorization: Bearer <RELAY_WEBHOOK_TOKEN>
Content-Type: application/json

{"id":"job-001","channel_name":"AI案件スカウト","sender":"Scout Bot","text":"新しい案件です","card":{"schemaVersion":1,"title":"Python自動化","description":"業務改善案件","score":92,"badge":"高適合","metrics":[{"label":"技術適合","percent":95}]}}

The id is an idempotency key within the webhook channel slug; retries with the same id are ignored. Events are persisted in SQLite. Do not include tokens in URLs.

## Dify

DIFY_API_URL must end at the API root (/v1), such as https://dify.example.com/v1. Set DIFY_API_KEY to your Dify app API key, not the console administrator key. Chat and Chatflow apps supporting blocking responses work. SSE-only Agent apps are not yet supported. Dify conversation IDs are stored per Android chat channel.

## OpenAI

OPENAI_API_KEY is configured only on the server. Change OPENAI_MODEL to a model your API account allows (default gpt-6-luna). The gateway uses the Responses API with store=false and up to twelve local chat turns per request. Set spending limits and rotate credentials.

## Routes

- GET /healthz : readiness
- POST /v1/webhooks/{channel} : bearer RELAY_WEBHOOK_TOKEN
- GET /v1/events?after=N&limit=100 : bearer RELAY_CLIENT_TOKEN
- POST /v1/chat : bearer RELAY_CLIENT_TOKEN

This is a single-user reference server, not a multi-tenant auth/tenant-isolation solution. Use a trusted reverse proxy, HTTPS, rate limits, access restrictions and regular SQLite backups.

Native Slack Events API and GitHub webhook signatures are NOT implemented. Add a source adapter to verify their signatures before sending normalized events to /v1/webhooks/{channel}. Slack Incoming Webhooks are for posting TO Slack, not receiving FROM Slack.

## Tests

From relay/: pip install -r requirements.txt pytest
Then: PYTHONPATH=. pytest -q test_server.py



## Portable connection settings (v0.5.7)

- `GET /v1/connections/export` with the existing `RELAY_CLIENT_TOKEN`
  returns a portable JSON document containing **configuration only** (display name,
  provider, channel, enabled, score and keyword filters, RSS HTTPS URL and interval).
- `POST /v1/connections/import` with the same client authorization accepts
  that document, validates it before updating SQLite, skips matching entries,
  and atomically imports the rest. The response includes **new** one-time tokens
  for newly created webhook sources; no token is exported.
- The Android **連携** tab offers file picker actions **設定をエクスポート** and
  **設定をインポート**. A newly imported Webhook needs to be reconfigured at
  its sending service with its new URL and token.
- Existing events/history, old credentials and RSS state are never overwritten.
  200 integration definitions maximum per import. JSON files must stay under 256 KB
  when imported from Android.
- Inbound Webhooks with a dedicated Bearer token now also accept Slack Incoming
  Webhook `blocks` JSON and turn it into a RelayHarbor rich card.
  See [Lancers AI Scout migration](../docs/lancers-ai-scout-relay.md).

## v0.6 — Webサイト更新監視（RSS不要）

Android「連携」→「＋ ガイド付きで追加」→「Webサイト更新監視」。公開HTTPSページの静的HTML本文（任意のCSSセレクターで範囲指定）を15分〜24時間間隔で比較します。初回は基準保存のみ、以降の変更で元URL付きリッチカードを指定チャンネルへ送信します。認証トークンは不要です。詳しくは [Web更新監視ガイド](../docs/website-change-monitor.md) を参照してください。

利用にはRelayサーバー v0.6.0以上への更新が必要です。GHCRイメージの公開後、NAS/VPS側で更新してください。JavaScript描画・ログイン必須サイトには非対応で、監視対象のサイト規約を守ってください。

## v0.5 — RSS/Atom subscriptions

RSS subscriptions are managed through the Android **連携** tab, with a public HTTPS feed URL, a destination channel, a 15–1440-minute polling interval and an optional keyword. No inbound token is created.

The relay pulls each feed directly (not GitHub Actions), stores up to 3 latest articles on first successful polling and up to 20 unseen entries on subsequent polls, and exposes an authenticated **POST /v1/connections/{id}/refresh** endpoint for **今すぐ取得**. Article cards have an optional HTTPS/HTTP article link the Android client can open. Source state, ETag, Last-Modified, errors and deduplication survive restarts in SQLite.

Network restrictions: HTTPS public DNS host on port 443, no local/private IPs in preflight DNS, up to 3 validated HTTPS redirects, no proxy-environment inheritance, 1 MiB maximum feed size, 12-second timeout. As this is a single-user implementation, only add trusted RSS feeds; DNS preflight is not a full mitigation for all forms of DNS rebinding.

Feed polling runs in the single Relay process. Do not increase Uvicorn workers to more than one without introducing a coordinated job scheduler. For details and an example, see [RSS guide](../docs/rss-integration.md).

## v0.4 — Integration management (single user)

After updating the relay container, configure the Android app with the same Relay URL and client token as before. Open the new **連携** tab.

1. Tap **連携先を追加**, pick **Webhook** or **GitHub Actions (JSON)**, set display name and receiving channel.
2. Optionally set **min score** (0 = no limit) and a required keyword. Events without a numeric score do not pass a positive minimum-score rule. Incoming payloads that fail a rule are acknowledged with HTTP 201 and `{"filtered":true,...}`, not stored.
3. The app displays a unique HTTPS ingress URL and **Bearer Token** for the newly created integration. Copy the token now: it is never retrievable later, only rotated.
4. Configure your sending service to make an HTTP POST with `Authorization: Bearer <integration token>` and `Content-Type: application/json` using the legacy normalized JSON schema below. This is *not* a native GitHub event endpoint.
5. Tap **テスト通知** then synchronize in the app. You can disable, edit, rotate, and delete the connection in the same screen. Disabling/deleting revokes incoming access but does not delete previously stored messages.

Typical integration endpoint: `https://relay.example.com/v1/inbound/<generated-id>`.

Example JSON: `{"id":"external-event-1","sender":"Monitor","text":"Build succeeded","card":{"schemaVersion":1,"title":"Build","score":96}}`.

API (authenticated with `RELAY_CLIENT_TOKEN`):
- GET `/v1/connections` (returns **no secrets**)
- POST `/v1/connections` (creates source with `name`, `provider`, `channel_name`, `min_score`, `keyword`; returns token once)
- PATCH `/v1/connections/{id}` (edit/disable)
- DELETE `/v1/connections/{id}` (revoke)
- POST `/v1/connections/{id}/test` (stores a rich sample notification)
- POST `/v1/connections/{id}/rotate` (revokes old token and displays new one once)
- POST `/v1/inbound/{id}` (authenticated with that **connection's own token**)
- Android-compatible POST aliases: `/v1/connections/{id}/update`, `/v1/connections/{id}/delete`

Legacy `POST /v1/webhooks/{channel}` with the shared `RELAY_WEBHOOK_TOKEN` continues to work so existing external automations do not break. Migrate sources to their own credentials over time.

The relay is **single-user**. It has no identity provider, individual accounts, tenant boundaries, OAuth, background FCM, provider-native payload adapters, abuse-control quotas or multi-user authorization. Do not advertise it as a public multi-tenant API. Protect with HTTPS/WAF/rate limits; keep both client and individual source tokens confidential.

To update an existing Docker deployment: `git pull` from `main` **after merge**, then from `relay/` run `docker compose up -d --no-deps --build relay`. Avoid `--remove-orphans` if using a companion cloudflared container. Existing SQLite events persist in the `relay_data` named volume; back up the volume before upgrade.

Tests: `PYTHONPATH=relay pytest -q relay/test_server.py relay/test_connections.py`.
