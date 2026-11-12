# RelayHarbor

**Android-first local notification and AI/Bot chat hub.**

RelayHarbor is a Kotlin + Jetpack Compose Android app combining Slack-style channels with
rich, JSON-driven score cards and persistent chat history. Future versions will add optional
cloud and self-hosted (NAS/VPS) relay gateways, Dify, Webhooks, and AI providers.

## v0.5 RSS/Atom subscriptions
- **連携 → RSS** adds a feed by HTTPS URL (no webhook secret / external workflow).
- Background server polling at selectable 15/30/60/180/360/720-minute intervals, plus **今すぐ取得** for immediate refresh.
- First successful fetch imports up to 3 current articles; subsequent checks only import unseen items (up to 20 per cycle).
- Keyword filtering across article title/summary, duplicate prevention across restarts, article cards with an **記事を開く** action.
- RSS network requests require public HTTPS hosts on port 443, limited size and only validated HTTPS redirects and no proxy inheritance.
- Existing GitHub Actions / generic webhooks and v0.4 connection settings remain compatible.
- See [RSS feed subscription guide](docs/rss-integration.md).

## Public repository and continuity of updates

- This is a **clean public source snapshot**. The old private development Git
  history, branches, internal workflow logs, and original tags are not imported.
- First public release: **RelayHarbor v0.5.2**, `versionCode 7`, with unchanged
  application ID `net.automationse.relayharbor` and the **original signing key**.
- **Do not uninstall** an existing production APK: installing the signed v0.5.2
  release directly over v0.5.0 or v0.5.1 preserves local Android data.
- Because the earlier updater points to a private release repository, the first
  v0.5.2 public migration is a one-time manual APK install. Future releases can
  be checked in **設定 → アプリのアップデート**; v0.5.3+ opens the official
  release page in a browser rather than downloading/installing APKs inside the app.
- The public GHCR relay uses an independent package name; NAS credentials,
  configuration, the existing SQLite volume, and the network identity are unchanged.
- Read [public migration](docs/public-upgrade.md) and
  [release signing continuity](docs/releasing.md).
- The relay remains **single-user**: public source is not a public SaaS backend.

## GHCR container delivery for Relay (available via GitHub Actions)
- GitHub Actions builds and tests a multi-platform Relay image and publishes to `ghcr.io/automationsedev/relayharbor-public-relay`.
- Synology NAS can pull an immutable digest, back up SQLite, update only the Relay container and revert the application image if health checks fail.
- Optional scheduled polling of the `stable` channel requires **no inbound SSH access or self-hosted runner**.
- See [GHCR delivery and NAS update guide](docs/container-delivery.md).

## v0.4.0 integration manager (pending signed APK release)
- New **連携** tab: create, list, edit, test, disable, delete and rotate individual webhook connections without editing relay .env.
- Dedicated token and inbound URL per integration; token is only displayed during creation/rotation, not returned in lists.
- Optional minimum-score and keyword filters at the relay; per-connection target channels.
- Existing v0.3 webhook endpoints, chats and local Room data are preserved. versionCode increments from 3 to 4; production application ID and signing remain unchanged.
- GitHub Actions template is a **generic JSON webhook**, not native GitHub OAuth/GitHub App setup. The sender still needs to configure the webhook URL and token.
- Cloud OAuth, one-click provider installs, automatic arbitrary payload mapping and multi-user cloud accounts are **not implemented** in v0.4.
- Keep the relay private/single-user until a dedicated multi-tenant security design is deployed.

## v0.3.0 webhook + AI gateway
- Optional Docker Relay can be deployed in cloud, VPS or NAS.
- HTTPS bearer-authenticated webhook notifications, deduplicated and saved to local Room.
- Proxy chat requests to Dify or OpenAI without embedding provider master keys in the APK.
- Android Keystore-encrypted relay token, manual sync and periodic polling while app is active.
- See [relay deployment](relay/README.md). Real background push and direct native Slack event verification remain future work.

## v0.2.0 hybrid UI
- Slack-style channel navigation plus a Discord-inspired workspace overview.
- Telegram-inspired message composer and clearly differentiated own messages.
- Four tabs: Home, Chat, Activity, Settings.
- Recent-message preview, search, recent/unread filters, and sender-grouped timeline.
- Refined scoring cards and branded light/dark palette.
- Local-first data stays intact: no Room schema migration, same applicationId and signing identity.
- See [hybrid UI design](docs/hybrid-ui.md).

## v0.1.0 foundation
- Channel list with local search, create, confirm-delete.
- Per-channel messages, local composition and Room persistence.
- Rich cards with score, progress metrics, expandable description and copy action.
- Seeded sample notifications **clearly marked as demos**, plus "Add demo notification".
- Settings profiles for local / cloud / self-hosted and Dify / OpenAI-compatible provider.
  These are **saved only**; no real remote connection is made in v0.1.0.
- Dark theme + Material 3 dynamic color.
- CI builds debug APK on every PR and main push; tagged releases require persistent signing secrets.

## Prerequisites
- Android Studio with Android SDK 35
- JDK 17
- Gradle 8.11.1 (installed by GitHub Actions; local invocation with `gradle`)

## Development
```bash
gradle :app:assembleDebug :app:testDebugUnitTest
```
Debug application ID: `net.automationse.relayharbor.debug`.
Production application ID: `net.automationse.relayharbor`.

To install the debug artifact, download the APK from the successful GitHub Actions
**Android CI** run, or use Android Studio.

## Upgrades without uninstalling
1. Keep production `applicationId` **unchanged**.
2. Preserve the original release signing keystore and credentials permanently.
3. Increase `versionCode` on every production release.
4. Avoid destructive Room migrations; add explicit schema migrations when DB schema changes.

See [signing and release instructions](docs/releasing.md) before the **first** production APK.
Without configured signing secrets, the production release pipeline intentionally fails rather
than publishing APKs signed with a disposable debug key.

## Architecture
- `app/src/main/java/net/automationse/relayharbor/data`: Room entities and DAO; JSON card codec; settings DataStore
- `app/src/main/java/net/automationse/relayharbor/ui`: channel, chat, rich card, settings screens
- `app/src/main/java/net/automationse/relayharbor/integration`: future gateway/AI provider contracts

See [architecture notes](docs/architecture.md).

## Roadmap
- v0.2: user-configured Dify / OpenAI-compatible provider adapters and secure auth
- v0.3: optional webhook relay server (Docker, cloud, NAS/VPS), push and sync
- v1.0: production hardening, onboarding, interoperability, Play Store readiness

No NAS or cloud subscription is required for the local-first build.

## Privacy
The initial app works locally. No analytics SDK, Firebase SDK, or push services are enabled.
Android backups are disabled by default to avoid exporting personal notification histories.
