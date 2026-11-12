# Compact integration cards and battery-aware synchronization

## Connection cards

Go to **連携 → カードの大きさ** and choose **通常** or **小さく**.
The preference is saved privately on the device and survives restarting
RelayHarbor. It applies to the saved connections list only, not to incoming
message cards themselves.

- **通常:** connection name, provider, destination channel, scores or RSS
  details, explicit buttons for testing/copying/editing/rotation/deletion.
- **小さく:** one compact row with name, provider, destination, enable switch,
  and more-actions menu. All connection actions remain accessible, including
  RSS retrieval and Webhook test.
- This choice is purely UI; it never mutates API credentials, webhook routes,
  the server database, RSS polling, or imported connection definitions.

## Battery-aware foreground sync

**設定 → 新着メッセージ通知 → アプリ画面を表示している間の同期間隔**:
choose 30 seconds, 1 minute, 2 minutes, or 5 minutes. The previous
30-second behavior remains the default after upgrading.

In this release the foreground poller is controlled by Android's activity
STARTED/STOPPED lifecycle. A HOME activity can remain composed even after
the user switches to another app, so a Compose coroutine running unconditionally
could otherwise continue requesting Relay every 30 seconds while hidden.
Now the short-interval loop is cancelled when the screen/activity is stopped.
On becoming visible again, Relay checks immediately and then uses the selected
interval.

The existing WorkManager periodic job still checks background messages if the
user enabled background notifications and granted the Android notification
permission. It uses a **minimum 15-minute interval** and may be deferred
by Doze or device power management. It does not become an exact timer.

The request count below is illustrative *if the app remains foreground and
connected for the full duration*, not a measurement of battery percentage:

| Interval | 1 h | 8 h | 24 h |
| --- | ---: | ---: | ---: |
| 30 seconds | 120 | 960 | 2,880 |
| 1 minute | 60 | 480 | 1,440 |
| 2 minutes | 30 | 240 | 720 |
| 5 minutes | 12 | 96 | 288 |

Actual battery impact requires an on-device A/B test with the same signal,
Wi-Fi/cellular connection, screen time, incoming event rate and brightness.
Radio wake-ups, network latency and server response characteristics vary.
Android Settings → Battery → App battery usage can help compare RelayHarbor
across similar usage days. A 60- or 120-second interval is a reasonable
starting point for a launcher; choose 30 seconds when notifications are
time-sensitive and the app is visible.

Nothing in this release changes the Relay NAS server, notification authentication,
Room history, signed package or previously saved favorite apps.
