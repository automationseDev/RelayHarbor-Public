# Home header-off spacing refinement (v0.7.4)

When users disable the RelayHarbor title bar from **ホーム → カスタマイズ** or **設定 → 表示設定**, Home content now starts closer to the Android status bar:

- The root app Scaffold already applies safe Android system insets. The nested Home Scaffold explicitly sets its own `contentWindowInsets` to zero **only when its title bar is hidden**, preventing an extra status-bar-sized blank band.
- Dashboard top padding is reduced from **10dp to 2dp**, and the vertical spacing between the Home heading and subsequent cards from **12dp to 8dp**, only while the header is hidden.
- The header-visible layout retains its original inset and padding behavior.
- The Android status bar remains visible and its safe area is provided by the outer Scaffold; content is not moved into unsafe system UI.
- The same preference continues to persist after restart. No changes to saved dashboard layouts, connection secrets, message history, or Relay/NAS database.

## Manual checks

1. With title bar on, confirm previous Home vertical positions are unchanged.
2. Toggle it off and confirm the top of `マイホーム` and first cards moves upward but not under the status bar.
3. Rotate device and open/close the Fold with the title bar hidden.
4. Open notification center/settings and switch back to Home; confirm the title bar stays hidden until toggled back on.
5. Confirm Home cards remain scrollable and navigation bar stays reachable.

This is a spacing fix; no new system permissions or server deploy are required.
