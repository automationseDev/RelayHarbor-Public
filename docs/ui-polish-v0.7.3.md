# RelayHarbor UI polish / v0.7.3 candidate

## Home header visibility

The screenshot's top **RelayHarbor / あなたのワークスペース** bar can be turned off independently of all other screens.

- Open **ホーム → カスタマイズ → RelayHarborの上部ヘッダー** and turn the switch off.
- Or open **設定 → 表示設定 → ホーム画面のRelayHarborヘッダーを表示**.
- On/off takes effect immediately and persists via `WorkspaceUiStore` / Android private preferences.
- Off removes only the Home `WorkspaceBar`. The **マイホーム / カスタマイズ** controls, bottom navigation, chat and Settings remain accessible for recovery. Other screens keep their own headers.
- Existing users retain their current look by default (on), until explicitly changed.

## Design system

- Calmer navy/indigo + teal Material3 palette with consistent elevated surface tones for light and dark theme.
- Shared rounded corner radii (8/12/18/24/28dp) applied from the application's MaterialTheme.
- Notification overflow menu uses a minimum 48dp touch area.

## Real height-aware Home masonry

- Replace the legacy row-based grid with a bounded `Layout` for only the six built-in Home cards.
- Each card is measured at its saved column span and actual height, then placed in the earliest available column using a deterministic skyline algorithm.
- This eliminates avoidable vertical gaps under short cards next to tall cards; unlike the old horizontal-only packing, it supports tiles of uneven heights.
- One global dashboard vertical scroll and independently capped card content preserve natural height and prevent unbounded content.
- Compact editing panel, one overflow menu per card (reorder, resize width and height, hide), long-press drag and bottom-right resizing grip.
- Existing compact/expanded profile settings and serialized `HomePanel` IDs/spans/heights are unchanged. No migration, credentials or history modifications.

**Limitations:** built-in card grid, not freely positioned third-party Android AppWidgets; one column-spanning tile may still need to start below the tallest occupied column for correctness.

## Fold continuity

- When available width crosses the 600dp split-pane threshold, the selected conversation remains open: in expanded width it is the right-hand pane; in compact width it is the chat screen.
- No new permissions or network APIs.

## QA and regression

- Android CI + unit tests, Relay API tests, Public Release Privacy Preflight before merge.
- Unit tests validate vertical gap filling, multi-column placement, no overlap, empty tiles, and backward compatibility of saved layout JSON.
- Real-device QA still required: hide Home header and restore via Settings, rotate/open/close Fold, edit a narrow one-column card, use resize grip and card menu, preserve layouts after process restart, use large text and TalkBack.
- Production signer and application ID remain unchanged; signed release must pass version increment and signature checks before publication.
