# RelayHarbor v0.13.4 — app drawer actions & calendar today focus

## App drawer
- Tap a tile in **すべてのアプリ**: launch the chosen app (unchanged).
- Long-press a tile: open a menu with **お気に入りに追加** (or **お気に入りから外す** if already selected) and **アプリ情報・設定を開く**.
- Settings opens Android's official app details screen via \`ACTION_APPLICATION_DETAILS_SETTINGS\` with a \`package:\` URI. No privileged permissions are used.
- Favorite changes use existing \`favorite_app_shortcuts\` preferences and the existing 32-shortcut limit. A process-local revision signal updates the visible Home favorites panel immediately.
- No remote data transfer or extra Android permissions.

## Calendar
- Opening the calendar tab anew starts on the current month with today's date selected (rather than restoring an old saved date).
- Today's date has a **thick primary-color border** even if the user temporarily chooses a different date. The manually selected day has a distinct thin tertiary-colored border.
- Browsing months and tapping another date still work.
- At midnight, today's frame moves automatically while the view stays open. Selection follows today's date only when previously following the old today, and otherwise retains the manually chosen date.
- The existing event and personal holiday interfaces remain unchanged.

## Compatibility
- VersionName 0.13.4, versionCode 48 (from 47).
- Application ID, signing-key continuity, Room DB v6, message histories, Relay APIs and user favorites are preserved.
- New JVM tests for today/selected frame, day rollover and existing favorites policy.
- GitHub CI and signed APK verification cover the build; physical Z Fold and installed APK upgrade testing remain manual checks.
