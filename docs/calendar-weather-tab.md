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
