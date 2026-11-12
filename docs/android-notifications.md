# Android message notifications (v0.5.9)

RelayHarbor now shows native Android notifications for newly synchronized Relay
Webhook/Bot messages and AI chat replies while the target chat is not visible.

## Enable

1. Upgrade the installed app without uninstalling.
2. Allow **Notifications** when Android asks. If denied, open **接続と設定 →
   新着メッセージ通知 → 通知の許可をリクエスト**, or Android's notification
   settings via **Androidの通知音・振動などを設定**.
3. In Settings, turn on **Androidの通知バーに表示** and optionally
   **アプリを閉じている間も定期的に確認**.
4. For each channel, open **チャット → ︙ → 通知をオン／オフ**.
   Select **通知音・振動を設定** to open Android's native system notification
   channel settings for that chat. These sound/vibration choices belong to the
   system and are not overwritten when the app updates.

## Delivery behavior

- **App open:** Relay sync is attempted approximately every 30 seconds.
- **App not visible:** Android WorkManager checks the configured HTTPS Relay
  every **15 minutes or later** when the user enables background sync. This
  is **not guaranteed real-time push**: Doze, data saver, battery restrictions,
  force-stop and the manufacturer's power management may delay/stop delivery.
- Notifications are issued only for **newly committed** event sequences in
  the existing Room database, not on every UI refresh. Only events less than
  24 hours old are alerted, up to five per sync page.
- Messages remain in the local chat history even if the Android notification
  permission is denied or a channel's alerts have been muted.
- Tapping a notification opens its channel directly in RelayHarbor.
- If that channel is already open and the app is visible, a duplicate
  status-bar notification is suppressed.
- Settings and channel mute states are device-private, with no credentials
  or webhook tokens written to notification preferences.
- A self-hosted Relay still requires a working HTTPS endpoint and a configured
  Relay client token. The background worker uses the same encrypted credentials.

## Immediate push (future provider integration)

Firebase Cloud Messaging (FCM) cannot be enabled generically from a public,
unconfigured APK. It requires a project-specific Firebase app ID/sender key,
device registration, and a trusted server-side message sender. These credentials
and delivery choices should be configured per installation before enabling
real-time pushes. This release deliberately does not embed a third-party FCM
project or upload device push tokens to a central service.

For latency-sensitive Lancers AI Scout jobs, continue parallel Slack notifications
until a verified real-time push provider is available.

## Test

1. In **連携**, use **テスト通知** on a Webhook (or send one from Scout).
2. Go to a different tab; let Relay sync, and verify notification sound/banner.
3. Tap the notification; confirm the correct channel opens.
4. Toggle that channel OFF, send another message and verify no banner.
5. Restore it, close the app normally, and wait for the next WorkManager run.
   Note: swiping away, force-stopping and aggressive device battery policies
   may produce different results. Do not use force-stop to evaluate background
   scheduling guarantees.
