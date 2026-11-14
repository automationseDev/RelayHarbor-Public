# RelayHarbor v0.9.6: Android local website monitoring

## Two selectable monitoring modes

In the **連携** tab, the Webサイト更新監視 section offers:

- **📱 端末内監視（サーバー不要）** — Android WorkManager fetches an HTML page over HTTPS and compares the selected text against a local snapshot. A Relay endpoint and access token are **not** needed.
- **🖥️ Relay監視** — The existing server-backed website provider continues monitoring on NAS/VPS. Requires a connected Relay v0.6.0+.

These modes are separate. Switching modes doesn't silently migrate monitoring state or send local snapshots to Relay.

## Android local setup

1. Open 連携 and tap **＋ 端末内監視を追加**.
2. Set display name and **public HTTPS** URL (no login credentials).
3. Choose a local chat channel as the destination; you may create one first.
4. Choose polling interval from 15m, 30m, 1h, 3h, 6h, 12h or 24h.
5. Optional CSS selector, e.g. #news, main, .updates; optional notification keyword.
6. Save, then tap **今すぐ確認**. First successful check saves the baseline and creates **no** alert.
7. On later changes, a rich card with the changed excerpt and source URL appears in the selected channel, accompanied by an Android notification if notifications are allowed.

Source edits to URL or CSS selector intentionally reset the baseline. Disable/enable, edit, delete and manual refresh are available from the same list. The Android notification settings and existing per-channel notification preferences still apply.

## Limitations and privacy

- Android WorkManager periodic work has a **15-minute minimum**. Doze, battery settings, network restrictions and force-stopping the app can delay or prevent scheduled work. This mode is **not** a reliable real-time alert. Do not advertise guaranteed five-minute checks.
- Monitoring works while the app isn't visible, subject to Android scheduling. Jobs are scheduled in WorkManager, not tied to an activity or a permanently running foreground service.
- Uses **only public HTTPS** sites on port 443. URLs containing credentials, fragments, loopback/private hosts or custom ports are rejected. DNS is checked for public addresses before a fetch and at each redirect; max 3 redirects; no unsafe downgrade to HTTP. DNS preflight does not fully eliminate DNS-rebinding risks: monitor only trusted sites.
- HTTP connection/read timeouts: 6/12 seconds. HTML is read to a maximum of 1 MiB, selected text is truncated to 16,000 characters, and snapshots are stored in private SharedPreferences. At most 25 website watches per device.
- CSS extraction uses jsoup. Excludes scripts/styles/iframes, and navigation/footer/header for whole-body mode. Login-dependent pages, JavaScript-rendered content, anti-bot protections, image-only changes and content beyond the text limit are not supported.
- Conditional requests use ETag and Last-Modified when supplied.
- Baselines, configuration, errors, timestamps, and last texts **stay on-device**. No Relay API, AI API, GPS or weather API calls are made for local website monitoring. Only the chosen public website receives HTTP GET requests; user agent identifies RelayHarbor.
- Channel messages are saved in the existing local Room database, with deduplication and normal channel-notification preferences.
- Monitoring continues recording events if notification permission is denied; Android OS notifications require permission.
- Avoid aggressive polling, respect website terms and rate limits. Deleting a monitoring configuration does not delete previously received messages.
- Local watch configuration is **not** included in Relay's portable integration export/import format. Uninstalling or clearing application data removes local website watches. The OS backups for this app are disabled.

## Validation

Automated JVM tests cover safe URL/interval validation, CSS extraction and rejection, text hashing, and changes. Android CI should compile the UI and WorkManager integration. Manual tests still required on the user's device: background Doze, interrupted connectivity, app force-stop, notification permission denial, Fold cover/unfolded UI, and restart persistence.

No NAS or Relay server update is required for **端末内監視**. The prior Relay server watcher remains unchanged.

## v0.9.7 — unified integration cards

After registration, Android-local watches are **not nested in the Web monitoring setup card**. All local and Relay integrations now appear together in the **same integration-card list**, using the same regular/compact card layout setting, search field, service filter chips and standard action controls.

- Filter **すべて**: Webhook/RSS/Relay websites and local website watches in one list.
- Filter **端末内**: show only device-local watches. **Web監視**: show both local and Relay website watches.
- Each card identifies its monitoring mode: **Web監視・端末内** or **Web監視・Relay**.
- Shared regular/compact layouts, enabled switch, menu/details, URL copy, manual check, edit and delete.
- Local actions never issue Relay network calls; the cards use distinct device identifiers and retain their existing local snapshots.
- The top Web監視 section is now only a small **registration selector** with 端末内 and Relay options. The local watch list was removed from that section.
- Existing watches remain intact; no migration, NAS update or re-registration needed.
- The JSON export/import controls remain Relay-only. Local watch settings are device-private and **not** included in those exports.

