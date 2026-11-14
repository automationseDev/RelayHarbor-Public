# Android App Notification Sources — RelayHarbor v0.8.0

## What this adds

A new **Androidアプリ通知** section in **連携**, available even in local-only mode (no Relay/NAS required). Captures *future notifications displayed by Android* from specifically authorized source apps and stores them in existing RelayHarbor Room channels.

1. **連携 → Androidアプリ通知 → 連携アプリを管理**.
2. Search installed launcher apps, e.g., LINE, the default SMS app, Gmail, or Teams.
3. Choose the destination RelayHarbor channel (or create a channel, then select it), and optionally specify a keyword matching the notification title/body.
4. Save that app's rule, enable the **Androidアプリ通知** master switch, and tap **通知アクセス** to grant Android's system-level notification access to RelayHarbor.
5. On subsequent notifications, corresponding messages appear in **チャット** and **通知**. Use notification triage controls as usual. **元アプリを開く** launches the original app if Android exposes its launcher.

Permissions are **two distinct choices**: Android's Notification Access approval in OS Settings and the in-app master switch plus an individually selected source app/target channel. All default to **off**. Merely granting Android access never automatically enables recording. The user can revoke OS access at any time.

## Security and privacy

- No `READ_SMS`, `RECEIVE_SMS`, Accessibility Service, `QUERY_ALL_PACKAGES`, or sensitive-notification permission is requested. SMS/LINE are monitored *only through the notifications Android delivers*.
- The system `NotificationListenerService` requires OS-granted `BIND_NOTIFICATION_LISTENER_SERVICE`; the service is not exported to third-party callers.
- No automatic replies or actions sent to source apps. `元アプリを開く` uses the locally captured package's launcher intent.
- Disabled unless the **global switch**, **OS permission**, and **specific enabled package→channel route** are all configured.
- Only selected app packages are ingested; never RelayHarbor itself. Existing server events are not affected. Captured messages are **device-local** and are not uploaded to Relay, NAS, cloud, or analytics.
- Listener skips secret, ongoing, grouped-summary and call/service notifications. Verification/OTP phrases and code-only messages are also filtered locally before storage; this is a conservative, best-effort heuristic and cannot guarantee detection of every sensitive string. Android 15+ independently redacts some sensitive content available to listeners.
- No source notification bodies are written to routing preferences or logs. Only the chosen package, channel ID, optional keyword, and enabled state are stored.
- Android persists captured text in the existing private Room database; existing app backup is disabled. Removing/turning off a source route prevents **new** capture, but does **not** delete previously saved messages. Users can remove channels using existing channel management.
- Captured Android notifications do **not** produce another Android OS push notification, avoiding duplicate alerts and loops.

## Deduplication and lifetime

- Deduplication is transactional via the existing `inbound_events` table, using the source package plus a one-way hash of the OS notification key and a SHA-256-derived content revision. Reposts of the same displayed content are ignored; changed content may create a new entry.
- This does not reconstruct full historical SMS/LINE data; it captures only title/body fields Android exposes in new notification callbacks. Notification styles vary, and group/channel metadata may not always be available.
- A deleted or missing destination channel causes capture to be skipped; the user can select a new destination. Rules can be disabled individually or deleted entirely.
- The route picker enumerates **launcher-visible installed applications**, not arbitrary hidden packages. A previously saved route remains visible even if the app no longer has a launcher entry.

## Upgrade and device QA

- Room **v3 → v4** migration adds `sourcePackage` as a NOT NULL text column defaulting to an empty string. The v1→v2 and v2→v3 migrations remain. This preserves existing chat history, read/pinned/completed state, connection tokens, and home layout settings.
- Current public Android package name and original production signer MUST remain identical.
- CI: compile and Android/JVM unit tests, Relay API tests and release privacy preflight.
- Real-device checks after upgrading: enable OS access, import one SMS-like notification from another app, confirm correct source and channel, verify no duplicate notification, route several apps to the same channel, filter keywords, disable a route, revoke access, restore access, restart, and verify previous chats persist.
- Some sideloaded Android builds block notification-listener restricted settings until the user explicitly permits the setting in Android app info. Do not bypass those Android restrictions; follow system UI.
- Native app-specific API sync, remote cross-device sync, interactive replies and complete LINE/SMS histories are out of scope.
