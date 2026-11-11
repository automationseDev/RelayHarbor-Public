# RelayHarbor UI guide (v0.2.0)

## Design direction: Hybrid Harbor
The UI borrows interaction patterns, not logos or artwork, from popular messengers:
- Slack: channel-first navigation, sender-and-time timeline, structured notification cards.
- Discord: clear workspace identity and channel separation.
- Telegram: unobtrusive composer, readable own-message bubbles and lightweight navigation.
- Modern dashboards: home overview and recent activity.

## Navigation
- Home: current workspace, actual channel/unread counts, recent Bot activity, channels.
- Chat: searchable channels with All / Recent / Unread filters, preview and timestamp.
- Activity: latest received (non-self) messages. Tap to open a channel.
- Settings: retain local/cloud/self-host profile choices. A saved profile does **not** mean a connection is active.
- Chat detail: dated timeline, grouped sender posts, evaluation cards, quick composer, demo-card action in + menu.

## Technical boundaries
- No database schema version change. New channel summaries and the activity feed use read-only Room queries.
- Do not synthesize unread counts or availability; values come from stored messages.
- Current v0.2 data is local-first, including seeded data that is explicitly marked as demonstration data.
- API keys, bots and real push notifications are **not** implemented by the UI change.
- Production applicationId remains net.automationse.relayharbor.
- Use the same release signing key. Increment versionCode for each release.
- Keep light/dark Harbor palette and accessible textual labels.
