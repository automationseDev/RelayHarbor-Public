# RelayHarbor RSS / Atom subscriptions (v0.5)

RSS is a **pull-based** integration: neither GitHub Actions nor an incoming webhook is required. The Relay server fetches public RSS/Atom feeds over HTTPS while your Android app receives normalized article cards through the existing /v1/events API.

## Android: add a subscription

1. Install a **signed** v0.5.0 APK using the same signing identity as the installed version; preserve applicationId `net.automationse.relayharbor` and versionCode 5 so existing data remains accessible.
2. In **設定**, choose **自前サーバー**, enter `https://relay.example.com`, and provide the existing NAS `RELAY_CLIENT_TOKEN` if not already saved. Do not use GitHub PATs or source webhook tokens here.
3. Open **連携 → ＋ 連携先を追加 → RSS** and enter:
   - 連携名: `GitHubブログ（日本語）`
   - 通知チャンネル: `開発ニュース`
   - RSS / Atom URL: `https://github.blog/jp/feed/`
   - 取得間隔: `30分`
   - キーワード: empty (initially recommended)
4. Tap **作成** → **完了**. A fetch is scheduled by Relay automatically (within roughly 20 seconds on first registration), or tap **今すぐ取得** to fetch immediately.
5. Open **通知** or **チャット**; if needed tap **設定 → Webhook通知を今すぐ同期** to import recent events into local Room.
6. On an article card, use **記事を開く** to open the original web article.

Other example feeds: `https://github.blog/feed/` (GitHub Blog in English), `https://blog.cloudflare.com/rss/` (Cloudflare Blog). Always verify the actual feed URL; a blog's home page is not automatically an RSS feed.

## What happens

- RSS 2.0, RSS 1.0 and Atom are parsed by feedparser.
- First successful polling imports at most the **3 newest feed entries** (not the entire history). All first-poll entries inspected are marked as known, preventing old articles from flooding the next poll.
- Later polling adds up to 20 unseen articles per feed poll. Deduplication persists in SQLite and survives Relay container restarts.
- Each article includes the headline, a summary, a `RSS` badge, and the source web URL as an openable action. The destination channel is the selected connection's channel name.
- Keyword matching is case-insensitive against article title **and** summary. If set, articles not matching the keyword are ignored and considered seen.
- Disable stops polling; **編集** adjusts channel, feed URL, poll interval and keyword; **今すぐ取得** forces a poll. Changing the feed URL resets the source's deduplication baseline, but leaves previously received notifications intact.
- Connection list shows last successful check and last error.
- The Android app fetches events roughly every 30 seconds while active; **background Firebase push is not yet implemented**.
- Published feed entries are retrieved from the public internet; there is no way to guarantee publishers update exactly on the configured interval.

## NAS / GHCR upgrade

The 0.5 release includes a new `rss.py` module and feedparser dependency and is distributed as a new GHCR image. Once the PR is merged and the **Publish Relay Docker Image (GHCR)** workflow has published the **release** channel:

```bash
cd /path/to/RelayHarbor
git switch main && git pull --ff-only origin main
cd relay
bash scripts/nas-deploy.sh stable
curl -fsS http://127.0.0.1:8088/healthz
```

Expected `"version":"0.5.0"`. The NAS script takes a consistent SQLite snapshot and recreates only `relay-relay-1`, preserving `relay_relay_data`, secrets, Cloudflare Tunnel and other Docker stacks.

After a successful initial stable deployment you may opt into Synology Task Scheduler `bash /path/to/RelayHarbor/relay/scripts/nas-auto-update.sh stable`.

**Do not** delete the existing SQLite volume; v0.5 adds `rss_sources` and `rss_seen` tables automatically to the existing database.

## Security and compatibility

- Only public HTTPS DNS hosts on port 443, no embedded credentials, fragments, private IP literals or local domains.
- The Relay checks each hostname's resolved IPs for global routability, permits up to 3 checked HTTPS redirects, ignores environment proxies, imposes 1 MiB limit and short timeouts.
- DNS prechecks have a residual DNS-rebinding window. Register only RSS feeds you trust; use an outbound egress firewall/network policy for stronger isolation in public deployments.
- Limit is 50 active/registered RSS feeds per single-user Relay.
- Fetching a feed with a relative or non-web entry URL will show the notification without a working web link.
- It is not safe to expose this single-user relay as a multi-tenant public SaaS; proper user isolation is a separate milestone.

## API

Authenticated with `Authorization: Bearer $RELAY_CLIENT_TOKEN`:

Create:

```http
POST /v1/connections
Content-Type: application/json

{
  "provider":"rss",
  "name":"GitHub Blog",
  "channel_name":"Development",
  "feed_url":"https://github.blog/feed/",
  "poll_interval_minutes":30,
  "keyword":""
}
```

Update: `POST /v1/connections/{id}/update` with `feed_url`, `poll_interval_minutes`, `keyword`, `enabled`, `name`, `channel_name`. Manual refresh: `POST /v1/connections/{id}/refresh` with `{}`. Lists and error metadata: `GET /v1/connections`.

Unlike a Webhook connection, an RSS connection does **not** have an incoming URL or a generated Bearer ingress token.

Tests: `PYTHONPATH=relay pytest -q relay/test_server.py relay/test_connections.py relay/test_rss.py`.
