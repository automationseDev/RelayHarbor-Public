# Smart Notification Center and Actionable Rich Cards (v0.7.1)

RelayHarbor now treats each inbound message as a local actionable notification. Existing chats, RSS, Webhook, Dify and GitHub integrations continue to work unchanged.

## Notification Center

Open **通知** in the bottom navigation. The search searches sender, channel, text, card title and card description.

| View | Display rule |
| --- | --- |
| すべて | All not-completed notifications, newest first |
| 重要 | Manually pinned notifications OR rich-card score >= 85, unfinished; manually pinned items appear first |
| 未読 | Unread and unfinished notifications |
| あとで | Explicitly saved-for-later notifications, unfinished |
| 対応済み | Completed notifications; can reopen |
| 既読 | Read and unfinished notifications |

The selected filter and search query persist across app restarts. Notification actions on the expanded card and the chat view update the same Room record:

- **重要に固定 / 重要を解除**
- **あとで確認 / 保留を解除**
- **対応済みにする / 対応を再開**
- **既読にする / 未読に戻す**

Marking a notification complete also marks it read and clears its saved-for-later status. Completing does **not delete** the message; it remains in 対応済み and its chat history. The saved-for-later queue is **not** a timed reminder or an automatic notification resend. A future update could add explicit due times.

Only the most recent 500 inbound/AI messages are loaded into this on-device notification inbox to bound list rendering. Full channel histories are retained in Room; nothing is deleted by the inbox limit.

## Actionable Rich Cards

All existing rich-card content (title, description, score, metrics, image thumbnail, original article URL) remains supported. Webhook senders can optionally provide up to three extra **safe, user-initiated** buttons:

```json
{
  "id": "example-123",
  "sender": "Automation Bot",
  "text": "Build completed",
  "card": {
    "schemaVersion": 1,
    "title": "Build #123",
    "description": "Release candidate is ready for review.",
    "score": 92,
    "thumbnail_url": "https://images.example.org/example.png",
    "url": "https://example.org/build/123",
    "actions": [
      { "type": "open_url", "label": "Open build", "url": "https://example.org/build/123" },
      { "type": "copy_text", "label": "Copy build ID", "text": "123" }
    ]
  }
}
```

Only `open_url` and `copy_text` are recognized. Unknown kinds are ignored. Buttons run **only when the user taps**. External action links must be public HTTPS URLs on port 443 without credentials or fragments; local network/private IP hosts, arbitrary schemes, insecure HTTP and direct request bodies are rejected. Copy values are limited to 512 characters. At most 3 buttons are rendered.

**Not supported or promised:** arbitrary sender-defined POST/DELETE requests, scripts, local commands, embedded HTML/JavaScript, OAuth, background execution, or one-click changes to third-party services. Those would require a separately authenticated and explicitly authorized action framework in a later release.

The existing generic `POST /v1/inbound/{id}` passes the `actions` metadata through its existing JSON card payload; no server or NAS migration is required to display those buttons.

## Data safety / upgrade

- Android Room schema **v2 -> v3**: adds `isPinned`, `savedForLater`, `isDone` Boolean columns to messages, with non-destructive defaults. Historical data remains. `MIGRATION_1_2` is retained.
- Existing Android package ID and original production signing key must be preserved; never uninstall to update.
- New notification statuses are device-local and not synced back to the Relay server or other devices.
- No new Android permissions, secrets, auto-executing webhooks, or NAS database changes.
- Generic demo channels now replace project-specific example content **only for first-time installation**; existing user channels are untouched.

## Validation / limitations

CI: Android build & JVM unit tests, Relay API tests, Public Privacy Preflight, signed release checks. Device checks: upgrading an existing Room v2 install and preserving history; read/done effects on unread counts; Fold cover and expanded layouts; card buttons on Webhook messages; Android back/forward, link chooser, copy/clipboard; case-insensitive search; status persistence after process death. The Room migration should be tested on a real existing installation before daily use.
