# Home fixed section headers & compact six-tab navigation (v0.8.2)

## Home panel behavior

All six existing dashboard cards now have a **non-scrolling header**, separated from their individually scrollable content by a subtle divider:

| Panel | Fixed heading | Fixed quick action |
| --- | --- | --- |
| Overview | マイワークスペース | — |
| Favorite apps | お気に入りアプリ | + (追加・編集) |
| All apps | すべてのアプリ | — |
| Synchronization | 同期・通知 | — |
| Notifications | 最近のアクティビティ | → (すべて見る) |
| Channels | チャンネル | → (一覧へ) |

As a card's **inner content** is scrolled, its heading remains visible at the top of that card. The full dashboard can still scroll as one page, in which case cards (including their headers) naturally move offscreen. We are not pinning all six cards to the Android status bar. A fixed heading is also shown during customization, with the existing drag handle and overflow menu.

The Favorites picker remains available through the header's + button, even while its grid/list content is scrolled. The picker state is hoisted without changing the persisted favorite shortcuts, display mode or layout. Notification and channel actions similarly remain at the top of their card.

The new header uses a 48dp action target and a single-line, ellipsized title when a user resizes a panel to one column. Scrollable bodies have independent clipping and a height limit based on the saved `height` value. Existing panel IDs, hidden/visible state, compact/Fold layout, Masonry placement and header-visibility preference are unchanged. No data migration.

## Six-tab navigation

Tab order is unchanged: **ホーム / 予定・天気 / チャット / 通知 / 連携 / 設定**.

- Cover screens or large system font scale: all six icons remain visible, while the label is displayed only under the selected icon. Every icon includes an accessibility content description.
- Wider screens (at least 540dp, normal font scale): all tab labels remain visible.
- The notification tab shows a capped `99+` unread indicator attached to the bell, rather than widening its tab label with an unread number.
- Navigation state, last-page restoration and all screen destinations are unchanged.

## Safety and QA

- No new Android permissions, Relay/NAS API changes, database migrations or account connections.
- Preserve application ID `net.automationse.relayharbor`, release signature and monotonic versionCode.
- Automated checks: Android compile/unit tests, Relay API tests, privacy preflight, signed APK release.
- Unit tests: fixed-header labels/action affordances, compact/wide label policy and unread badge boundaries, old panel layout serialization.
- Manual device checks still needed: open a content-heavy Home card and scroll its body, ensure the header does not move; scroll the overall Home and ensure nothing obstructs navigation; try editing favorites from its fixed header, toggle Home's global RelayHarbor header off/on, resize to a narrow panel, Fold cover/open and large accessibility fonts. CI is not a substitute for these UI gesture tests.
