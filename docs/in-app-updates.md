# RelayHarbor in-app updates (public v0.5.2+)

## Why RSS registration may return HTTP 422

The RSS provider was added in RelayHarbor Relay **v0.5.0**. If the Android application is v0.5.0 but the NAS is still using the `stable` image from v0.4.0, the server rejects `provider="rss"` with HTTP 422. This is not a wrong token.

Verify the deployed backend on Synology:

```bash
curl -fsS http://127.0.0.1:8088/healthz
```

If `version` is `0.4.0`, open **Actions → Publish Relay Docker Image (GHCR) → Run workflow → main → release**. The GitHub Actions `stable` tag is only updated on an explicit release build. After it succeeds:

```bash
cd /path/to/RelayHarbor/relay
bash scripts/nas-deploy.sh stable
curl -fsS http://127.0.0.1:8088/healthz
```

Target backend version: **0.5.0**. The database, GitHub Actions webhook, `.env`, Docker volume and Cloudflare tunnel remain unchanged.

In Android v0.5.1, RSS registration queries `/healthz` before registration and displays a clear instruction if the server is too old. HTTP 422 responses now include the actual FastAPI validation `detail`, instead of a generic "check tokens" message. If the server is already at v0.5.0, inspect the displayed detail for RSS URL format (public HTTPS only, port 443), field validation and other rejection reasons.

## Android version checks (v0.5.3 and newer)

From v0.5.3, RelayHarbor no longer downloads APKs or requests Android package
installation permissions. Settings → アプリのアップデート → 最新バージョンを確認
checks the official GitHub Releases API for a newer signed release.
When a new release exists, the button **公式リリースページを開く** opens the
official GitHub Releases tag page in a browser. The user may inspect the APK
and Android/Play Protect warnings independently. **Do not install if Play
Protect flags an APK as harmful.**

No background or silent installation is possible. Existing Room databases and
settings are preserved on normal in-place upgrades if the package ID is
unchanged, the APK signing certificate is the same, and versionCode increases.
The browser/Android installer still performs its usual checks independently.

The previous v0.5.1/v0.5.2 built-in APK download+installation feature and
REQUEST_INSTALL_PACKAGES permission were intentionally removed to reduce
privileges. This change does not guarantee a Play Protect classification will
be overturned. Consult Google's official Play Protect developer appeal process
for a flagged APK.

## First public release

After the new public repository is created from a single, clean initial commit
and contains the four original release-signing Secrets, run **Signed APK
Release** on main with tag `v0.5.2`. Verify the resulting
`RelayHarbor-v0.5.2.apk` installs **over** the existing APK. From v0.5.2
onward, the Settings updater checks this public repository without a PAT.

This APK update is separate from the NAS/Relay server image update. RSS
subscriptions require backend v0.5.0 or newer.
