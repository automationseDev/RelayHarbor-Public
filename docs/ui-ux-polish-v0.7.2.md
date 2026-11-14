# RelayHarbor UI/UX refinement (v0.7.2 candidate)

Four key improvements to the public Android UX, without changing the NAS Relay API, signing identity or database schema:

## 1. Flexible HOME panels
- The HOME panel body measures itself and shrinks when content is short, instead of reserving `130 + 92 * height` dp even for a one-line card.
- Existing width/height preferences remain intact. Height controls the maximum scrollable body viewport, not an artificial minimum filled with empty space.
- Responsive, best-fit horizontal row packing fills unused slots with later panels that fit; it preserves all panel IDs and never executes arbitrary widgets.
- In edit mode, long-press the drag handle, move to another card (highlighted as the drop target), then release to reorder. The layout changes only on release, not each hover event.
- Accessible `前に移動` / `後ろに移動` controls are also provided, and the existing `+ / -` and resize handle remain available.
- Compact and expanded layout preferences continue to be stored separately, and can be reset independently.

Limit: the panel grid is not a pixel-perfect freeform or masonry layout. Vertical gaps can still occur when adjacent cards have different heights. True Android third-party AppWidget hosting remains out of scope.

## 2. Swipe-based notification triage
- Notification inbox: swipe **right** to mark done (or reopen if already done), swipe **left** to save for later (or remove from later).
- Swipe actions immediately return the row to its resting position; no data is deleted. A Snackbar offers **元に戻す** for each swipe.
- Each notification offers an overflow menu; long-press also opens the menu. This avoids the prior always-visible four-chip toolbar on every row.
- Filters and search remain persistent; inbox categories continue to use Room v3 stored state.

## 3. Shared rich-card density
- `COMPACT`: title, badge, score and short description for notification rows and small panels.
- `STANDARD`: card content with metrics, actions, thumbnail and optional expandable description for chat.
- `DETAILED`: complete card for inspectors with description, metrics and approved actions.
- Only explicitly supported `open_url` and `copy_text` actions are available. This update does not introduce remote POST commands or arbitrary script execution.

## 4. Foldable / tablet panes
- Use **available window width >=600dp** to activate split panes, rather than identifying a particular model. Window resizing and fold/unfold transitions can change the presentation.
- **Chat tab**: left channel list (~36%) and right conversation (~64%), with a select-channel placeholder if none selected.
- **Notifications tab**: left filterable, swipeable notification list (~44%) and right persistent detailed preview (~56%).
- Narrow screens: existing full-screen channel chat; notification preview as a modal bottom sheet.
- Notification deep links navigate to the appropriate chat, not a stale detail pane.

## Regression checklist
- CI: Android debug build and unit tests; Relay API; privacy checks.
- Verify an update from v0.7.1 on an actual Android device: preserve app signature/ID, workspace settings, Room v3 message states, favorite apps and connection tokens.
- Compact phone: swipe left/right with undo, tap a notification for bottom-sheet details, open chat, close and back.
- Open Fold/tablet >=600dp: compare channel list + chat, notification list + detail; change selected item and preserve navigation.
- HOME: drag-preview with release, move buttons, width/height step resize, hide/restore, process death and cover/open profile persistence; check no blank 200dp panels with little content.
- Test interaction between horizontal action chip scrolling and swipe gestures; test TalkBack labels and large font scale.
- Do not claim on-device verification until physically tested.

The current release pipeline checks original signing certificate, forbidden self-install permissions and monotonically increasing versionCode before publishing APK. Relay server migration is not required.
