# RelayHarbor v0.13.0 — Chat productivity & notification workflow

## User-facing improvements
1. **Search within an individual chat channel** (magnifying glass in the chat toolbar). Search messages, sender names, rich-card data and thread replies locally. When a reply matches, the parent message appears, with an indicator to open the thread. Filter results by all / unread / pinned / saved-for-later. Query and filter state survive ordinary recompositions and rotation. Searching never sends message text to a server.
2. **AI quick prompts** from the chat composer (+ menu): summarize, extract action items, draft a reply and translate to English. Long-press a message to draft a summary request containing that message. The prompt is inserted into the local draft only — **nothing is sent until the user presses Send**. Drafts remain in the existing per-channel persistent store; thread replies remain local-only.
3. **Bulk mark read** in Notification Center. Operates only on unread, received notifications that match the *current search and filter*, with confirmation and an Undo snackbar. Other notifications, channels excluded by the current view, and self-sent messages are not modified. A single SQL statement per small batch prevents excessive coroutine/database work.
4. **Safer AI send**: acquire a per-channel busy flag immediately before sending, preventing rapid duplicate sends. Persist the user's outgoing text before checking for a missing AI profile or sending to a gateway. On failure, show a readable error; the message remains accessible for copying or retrying manually. Different channels can send concurrently without clobbering one another's loading indicators.
5. **Better search and status feedback**: show result count, reply-match hints, clearer empty-result text and progress state.

## Compatibility and privacy
- Application ID remains \`net.automationse.relayharbor\`; keep original production signing key.
- VersionName 0.13.0, versionCode 44, strictly greater than v0.12.0 (43).
- **No Room schema migration**; existing v6 DB, history, reactions, channels and provider choices are preserved. No new permissions or Relay API changes.
- Local-only search and prompts do not leak data to external AI providers. The user makes the send decision.
- Notification bulk actions are scoped to explicit visible message IDs (not SQL-wide bulk operations).

## Verification
Android CI: compile debug APK, execute JVM tests, verify public upgrade contract and absence of self-installer permission. JVM tests cover thread-aware search, filters, prompt bounds, and safe bulk target selection. Signed release workflow validates the Android package, increase in versionCode and continuity of the production signing certificate against the published baseline before generating a tag and release.

Manual checks still needed for a production device: upgrade from v0.12.0 without uninstalling, search including replies, cover/expanded Z Fold layout, prompt text persistence after restart, notification read/undo, rapid Send taps, offline AI failure, and Android app notification bridge.
