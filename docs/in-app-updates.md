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

## Android self-update (v0.5.2 and newer)

1. Install the **signed** RelayHarbor v0.5.2 APK from **this public repository's** GitHub Releases once, directly over the previously installed app. Do not uninstall v0.5.0/v0.5.1; the signing key and app ID must be the same.
2. Open **設定 → アプリのアップデート → 最新バージョンを確認**.
3. When a newer published release is available, select **ダウンロードしてインストール**.
4. The app requests the official latest GitHub release using HTTPS, finds the exact `RelayHarbor-vX.Y.Z.apk` asset and validates its published SHA-256 digest, expected package name and higher version code.
5. Android's package installer opens. On the first use, Android may ask to allow installing apps from **RelayHarbor**. Grant that permission if you trust your GitHub Releases, return to the app and tap **インストール** again.
6. Android verifies that the APK uses the same signing identity before replacing the installed app. User confirmation is always required. Local Room data and settings persist for properly signed in-place upgrades.

The app has no unattended/background silent installation, no unknown APK sources and no GitHub PAT embedded in Android. The in-app updater is designed for a GitHub-distributed APK. Distribution through Google Play may impose separate policies on `REQUEST_INSTALL_PACKAGES`; if publishing there, use the Play in-app updates API instead.

The release asset must have `digest=sha256:<64 hex>` in GitHub's Releases API. The download is limited to 150 MiB and restricted to official GitHub/release-asset domains.

## First public release

After the new public repository is created from a single, clean initial commit
and contains the four original release-signing Secrets, run **Signed APK
Release** on main with tag `v0.5.2`. Verify the resulting
`RelayHarbor-v0.5.2.apk` installs **over** the existing APK. From v0.5.2
onward, the Settings updater checks this public repository without a PAT.

This APK update is separate from the NAS/Relay server image update. RSS
subscriptions require backend v0.5.0 or newer.
