# Calendar & Weather tab (v0.8.1)

## Navigation

Bottom navigation has six items in this fixed order:

1. **ホーム**
2. **予定・天気** (new screen title: カレンダー・天気)
3. **チャット**
4. **通知**
5. **連携**
6. **設定**

The new page is a registered screen ID `calendar_weather`, including save/restore of the last visited page. Existing HOME/Chat/Notification/Integration/Settings storage and deep-link behavior remain unchanged.

## Calendar

- Uses the Android Calendar Provider's `CalendarContract.Instances.query` to read real device appointments, including recurring occurrences, for the selected month.
- Requests only `android.permission.READ_CALENDAR` at runtime when the user taps the permission button on this new page. No `WRITE_CALENDAR` and no permission prompt on application launch.
- Month navigation, Sunday-first calendar grid, event markers, selected date agenda, all-day/timed event display and an item tap to open the device calendar.
- Reads at most 500 occurrences per month and displays up to 20 items per selected day.
- No server upload, AI processing, background poll or weather transmission of calendar event details. No cached copies of event contents are stored.
- If permission is refused or no provider/account exists, an informative empty/error state is displayed.

## Weather

- User manually searches and chooses a city/region by name. **No ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION** required. A location is not preset from IP/GPS; the forecast area is explicit.
- Open-Meteo Geocoding API retrieves up to six matching places. Selected place name/administrative area/country and coordinates are saved in device-private preferences.
- Open-Meteo Forecast API supplies current temperature, apparent temperature, humidity, condition; next 12 hourly forecasts (temperature, weather condition, rain probability) and 7 daily forecasts (high, low, condition, rain probability).
- Fixed HTTPS hosts, URL-encoded city term, 10/12s timeouts, no automatic redirects, bounded JSON responses and 30-minute on-device cache. A manual Refresh skips the fresh cache; a 24-hour cache may be shown with explicit warning if offline.
- User must select a city before a weather request. The city search and selected coordinates are transmitted to Open-Meteo, **not** the device GPS location or any calendar content.
- In-app credit links to Open-Meteo and identifies the forecast source.

**Open-Meteo licensing:** the free API is available for *non-commercial* use under Open-Meteo's CC BY 4.0/usage conditions. Before monetizing RelayHarbor or offering it as a commercial product, replace with a licensed provider, a compatible self-hosted source, or secure appropriate commercial terms. Do not treat this public-client API as an unrestricted commercial entitlement.

Official documentation: https://open-meteo.com/en/docs and https://open-meteo.com/en/docs/geocoding-api

## Foldable and accessibility

- On compact screens, the calendar card precedes the weather card in one vertical scroll.
- When the available window is >=600dp, both are shown side-by-side in independently scrollable panes.
- The six-item bottom navigation uses a short label and calendar icon, and existing navigation-state restore is retained.

## Upgrade and tests

- Signed APK update retains the package `net.automationse.relayharbor` and original release signing key. VersionCode must increase; no uninstall.
- No Room schema migration, NAS/Relay API changes, launcher permissions, or server deployment needed.
- JVM tests cover calendar dates and all-day exclusive end, weather code/coordinate policy and new screen restore.
- Manual device QA required: grant/deny calendar permission, monthly/recurring events, change timezone/locale, select a city in Japan, verify current/hourly/weekly cards, disable network and read cached forecast, Fold cover/open navigation, 6 tab labels on a narrow cover screen, and Play-store permission disclosure if publicly distributed.

## v0.9.5 — Weekends, Japan public holidays and personal leave

- Sunday and Japanese public holidays (including substitute/citizen holidays) are **soft red**; Saturday is **soft blue**; weekdays use the current theme's neutral background. Selected dates have outlines so their weekday/holiday tint remains visible.
- The light palette uses red #FFE9EC and blue #E6F2FF. Dark mode uses muted dark-red and dark-blue surfaces to keep text readable.
- Published official Japan public holidays in **2026 and 2027** are covered by deterministic policy and unit tests, including 2026-05-06 and 2026-09-22 and 2027-03-22. Other 2000–2099 dates follow current statutory rules; vernal/autumnal equinoxes beyond announced years use approximate astronomical formulas until official publication, and future law changes may require app updates. Sources: Cabinet Office https://www8.cao.go.jp/chosei/shukujitsu/gaiyou.html and National Astronomical Observatory annual announcements.
- Tap a date then **休日を設定** to mark **有休**, **休日**, or **勤務日** and optionally attach a short label such as 代休 or 会社休業日. **標準表示に戻す** removes the override, restoring default weekend and holiday coloring.
- Saved markers live only on the Android device in private SharedPreferences (calendar_personal_holidays_v1), persist across restart/upgrade, and are separate from Android CalendarContract events. No calendar write permission or uploads of personal leave to Relay, Open-Meteo, or AI services. Reinstalling the app may remove locally stored markers.
- The calendar day grid and personal holiday actions work without READ_CALENDAR permission; accessing device appointments still requires explicit read permission.
- No server/NAS update, database migration, background worker, or added permissions.
- QA: Saturday/Sunday/holiday tints, selected-day outline, override of weekend and national holiday, restoring default, denied read permission, restart persistence, dark/light themes, Z Fold cover and expanded screen.
