# Public integration presets and customizable Home

## Public-ready service selection

From **連携 → ガイド付きで追加**, choose an icon card:

- Generic Webhook (HTTP POST)
- RSS / Atom feed (HTTPS, no sender secrets)
- GitHub Actions (HTTP POST from a workflow)
- Dify workflow (HTTP request node)
- n8n workflow (HTTP Request node)

The private Lancers AI Scout preset has intentionally been **removed** from this public-facing list. Existing saved connections created using it are not modified or deleted. Any personal workflow can still use the generic Webhook option.

All options except RSS are based on the existing generic Webhook API. They are **not** native OAuth integrations; no API keys for Dify or n8n are stored in the wizard. Creating a Webhook returns a one-time-scoped Bearer token that must be copied and configured at the sender.

## Home customization

Tap **ホーム → カスタマイズ**. The Home panel grid now supports:

1. Show/hide individual panel cards (including restoring hidden cards).
2. Reorder by long-pressing a panel header and dragging onto another visible panel.
3. Resize card widths with +/− controls, or drag the bottom-right resize label.
4. Resize card heights using +/− or the corner handle. Card contents can scroll inside a fixed height.
5. Revert the current layout to defaults with an explicit confirmation.
6. Automatically save on-device; choices survive app restarts.
7. Maintain **two separate layouts** based on available window width: compact (<600 dp, 2 columns) and expanded (>=600 dp, 4 columns). On foldable devices this normally follows closed/open states; landscape, multi-window and display scaling can also change the selected profile.

Cards are built-in trusted panels: Workspace status, favorite apps, app drawer, sync status & foreground interval, activity, channels. They contain real existing actions, not screenshots.

Notes: the Home customizer is **not** an Android AppWidget host yet; third-party system widgets and arbitrary remote card plugins are outside this initial release. For now, sizing uses grid-column/height steps rather than pixel-perfect free placement. Existing display settings remain global gates for Home activity/channels. Settings and Android launcher favorites are preserved.

## Regression and release safety

- Preserve application ID `net.automationse.relayharbor`, original release signer and in-place APK upgrades.
- Do not change Relay API, NAS SQLite data, Room schema or existing credentials.
- Check Android CI, Relay API Tests, Public Privacy Preflight before release.
- Test on an actual Fold in cover/open modes, switching screens, tapping cards, resizing near grid edges, drag/drop with scrolling, restoring hidden cards, app favorite picker, and returning to original HOME role.
- Manual test the wizard with real RSS/Webhook; test notification is a server-side test event, not verification of external sender configuration.


## v0.10.3 — All apps moved to favorites header

In the Home dashboard, the **お気に入りアプリ** card's persistent header now has two actions on the right:
- **＋**: open the existing favorites app selection/editing UI.
- **アプリ一覧 icon (すべてのアプリ)**: open the unchanged searchable all-apps drawer.

The old independent **アプリ一覧** dashboard card is retired. It no longer appears in default layouts, saved layouts or layout presets. Existing on-device layouts with the old `drawer` panel ID are normalized on load: only that deprecated card is dropped; the other panel positions, sizes, visibility and Android native widgets are retained. Existing favorite app entries are not changed. Cover and expanded layouts both receive the new header action, which stays visible even when the favorites body is scrolled.

This change only affects the Android UI. The Relay/NAS server needs no update. Confirm tap targets and title truncation on narrow Fold cover layouts after installing.
