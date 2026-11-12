# RelayHarbor as an optional Android default Home app

This release lets you keep using RelayHarbor for chat/notifications **or**
choose it as the device's default launcher. The HOME role is opt-in and can
only be granted through Android's system UI.

## Enable on Android

1. Update RelayHarbor over the existing installation; do **not** uninstall.
2. Open **RelayHarbor → 設定 → 標準ホームアプリ**.
3. Select **標準ホームに設定する**, then choose RelayHarbor in Android's
   system chooser. On some devices, Android may open default-app settings
   instead; choose **ホームアプリ → RelayHarbor** there.
4. Press the physical/gesture Home action. RelayHarbor opens its Home tab,
   even if you last viewed Chat or Settings.
5. Press **すべてのアプリを開く** to search and launch any visible installed
   app. Favorites still show in the existing custom grid or scrolling strip.
   Customize up to 32 pinned shortcuts using **追加・編集**.

Opening RelayHarbor through its normal app icon still resumes the previously
selected tab if you have enabled that display preference. Notification taps
still deep-link to the matching conversation. The Android HOME action always
takes you to RelayHarbor's Home dashboard.

## Return to your former Home app

1. Open **RelayHarbor → 設定 → 標準ホームアプリ**.
2. Tap **標準ホームを変更する**.
3. Select the previously installed launcher, e.g. **One UIホーム** on Samsung,
   in Android's home app settings. Alternatively use Android
   **設定 → アプリ → 標準アプリを選択 → ホームアプリ**.
4. The existing RelayHarbor chats, shortcuts, connection settings and
   encrypted tokens remain intact. You can continue using RelayHarbor as an
   ordinary app, and it will keep receiving notifications according to your
   notification preferences.

If the Android settings app labels vary, search Android's Settings for
"ホームアプリ" or "Default home app".

## Important limitations and recovery

- This is a **minimal optional launcher**, not a replacement for Samsung's
  complete One UI Home implementation. Third-party system widgets, advanced
  device gestures, Samsung panels, cross-device sync, home screen backups,
  multi-page layout, and per-fold-state layouts are **not yet implemented**.
- The **すべてのアプリを開く** drawer and Favorites grid list only launchable
  exported app activities visible under Android's narrow
  MAIN/LAUNCHER package-visibility query. This is intentional; there is no
  QUERY_ALL_PACKAGES permission or accessibility service.
- Work/personal profile visibility depends on Android's package restrictions
  and device management. Do not treat this launcher as enterprise kiosk
  software or a security boundary.
- Home and launcher can only be selected by you through Android system UI.
  The app never silently replaces your default launcher.
- Before making a third-party launcher your daily Home, confirm your
  Android Settings app and the previously used Home app are reachable from
  the all-apps drawer. If any essential shortcuts are missing, change back
  via Android system settings before proceeding.
- The app does **not** modify existing local database schemas, signing keys,
  installed package name, integration credentials, or your saved Favorites.
- On devices that restrict replacing their system launcher, Android may
  reject the role request. This cannot be bypassed by the app.

## Developer verification

- MainActivity has **two separate intent filters**: the existing
  ACTION_MAIN + CATEGORY_LAUNCHER, and an optional
  ACTION_MAIN + CATEGORY_HOME + CATEGORY_DEFAULT.
- MainActivity is singleTask; new HOME intents are handled through
  onNewIntent without creating a stack of duplicate launchers.
- On a system HOME intent, RelayHarbor navigates to Home. A normal app icon
  launch respects the saved tab; notification intents take precedence for
  deep-linking to a chat.
- Android's RoleManager.ROLE_HOME requests a voluntary default-app chooser.
  Users can return to their previous launcher at any time.
