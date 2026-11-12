# Image thumbnails in RelayHarbor cards

Starting with v0.6.2, RelayHarbor displays thumbnails in **chat rich cards**
when an incoming event includes a valid public HTTPS image URL.

## Accepted Webhook JSON

Existing per-connection credentials and URL remain unchanged:

`POST https://YOUR-RELAY-HOST/v1/inbound/YOUR-CONNECTION-ID`
with `Authorization: Bearer YOUR-CONNECTION-TOKEN`.

Preferred schema for new senders:

```json
{
  "id": "job-123",
  "sender": "Lancers AI Scout",
  "text": "New project found",
  "card": {
    "schemaVersion": 1,
    "title": "Python automation project",
    "description": "AI match score 92",
    "score": 92,
    "url": "https://www.lancers.jp/work/detail/123",
    "thumbnail_url": "https://images.example.org/projects/123.jpg"
  }
}
```

The Android card decoder also recognizes `image_url`, `thumbnailUrl`, `imageUrl`,
`thumbnail`, `image`, nested `image: {"url": "..."}`, and an `images` list.
For incoming Webhooks the updated Relay server additionally recognizes a
**top-level** `thumbnail_url` / `image_url` and promotes it into the rich card,
even when the sender provides only a text message.

Existing Slack Block Kit senders may use an `image` block with `image_url`,
or an image accessory inside a section. The Slack-to-Relay adapter preserves
that image as a thumbnail alongside title and score.

## RSS / Atom

The updated Relay server attaches public HTTPS image URLs from the feed's
`media:thumbnail`, `media:content` with image media type, an image enclosure,
or the first image in the RSS entry's HTML summary. It **does not download**
those images on the server.

New articles pulled after the new server is deployed receive image metadata.
Previously stored RSS events without image metadata are not retroactively
enriched.

## Android privacy and limits

- **設定 → 表示設定 → チャットカードのサムネイルを表示**: ON by default.
  Turn it OFF to prevent remote image requests, save data, or protect privacy.
- Images are downloaded **only for visible chat cards**, not while background
  synchronization runs or for status-bar notifications.
- Only public HTTPS hostnames are allowed. Plain HTTP, localhost/LAN URL
  literals, IP-address hosts, credentials, non-default ports, data/file URLs,
  and redirects are rejected. The client also checks DNS answers for private
  network addresses before connecting. DNS rebinding remains a theoretical
  limitation of hostname-based image requests.
- The downloader accepts PNG, JPEG, WebP and GIF file types with a size cap of
  1.5 MB, bounded decode dimensions, short network timeouts and an 8 MB
  in-memory thumbnail cache. Unreachable, invalid or unsupported images
  are ignored; the original card text is still displayed.
- Image URLs are metadata; the HTTP request goes **from the Android device**
  to the remote image host, which can see normal access information such as
  client IP. The Relay server never proxies or uploads your image bytes.
- The app does not add photo-library, external storage, or unsafe package
  permissions, and no migration or reset of the Room database is needed.

## Foreground sync interval

The 30-second, 1-minute, 2-minute and 5-minute selector was already added in
v0.6.1 under **設定 → 新着メッセージ通知**. Starting with v0.6.2, the same
selector also appears directly on **ホーム → 表示中の同期間隔**, making **2分**
easy to choose when using RelayHarbor as the default Android Home.

The choice persists across launches. It controls **only foreground sync**,
while the app Activity is visible. Background periodic WorkManager checks
remain independent and best-effort (15+ minutes), not real-time push.

## Deployment

Android version v0.6.2 displays thumbnails already present in saved cards
without any backend change. To **extract** images automatically from RSS feeds,
Slack image blocks, and top-level Webhook fields, deploy the matching
v0.6.2 Relay Docker image (GHCR stable tag) with the existing NAS update
script, preserving its current SQLite volume and credentials.

No need to re-import integrations or rotate Webhook tokens.
