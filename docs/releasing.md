# Signing and update continuity

Android upgrades preserve app data only when **applicationId remains identical**, the installed
app and replacement APK use the **same signing certificate**, and **versionCode increases**.
Debug builds intentionally use the distinct ID net.automationse.relayharbor.debug, and do NOT
replace production builds.

## New app identities only (not required when upgrading RelayHarbor)
For a completely new application only, create a release signing key:
```bash
keytool -genkeypair -v -keystore relayharbor-release.jks -alias relayharbor \
  -keyalg RSA -keysize 3072 -validity 10000
```
**RelayHarbor's public release MUST NOT generate a new JKS. Reuse the previous production JKS and the exact alias/passwords.** Never commit the JKS or passwords. Store an encrypted offline backup.
Encode the keystore as a single-line base64 string:
```bash
base64 -w 0 relayharbor-release.jks
```
On macOS: `base64 -i relayharbor-release.jks | tr -d '\n'`.

Add these repository Actions secrets in Settings -> Secrets and variables -> Actions:
- KEYSTORE_BASE64 (the complete base64 of the JKS)
- KEYSTORE_PASSWORD
- KEY_ALIAS (relayharbor)
- KEY_PASSWORD

Bump `versionCode` monotonically for every new production release, and update `versionName`.
Create and push an annotated tag matching the version, e.g. `v0.5.2`; the
Signed APK Release workflow builds and publishes the APK automatically.
Alternatively run the workflow manually to test signed artifacts without tagging.

Important: Do not distribute the debug APK as the production app. Do not regenerate
the release key on future updates. If using Google Play App Signing, follow Play's upload
key enrollment and signing certificate lifecycle requirements.

## First public release and data continuity

- Public APK **v0.5.2 (versionCode 7)** replaces private v0.5.0/v0.5.1
  installations in place; keep `applicationId = "net.automationse.relayharbor"`.
- The bootstrap workflow copies only `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS` and `KEY_PASSWORD` from private Actions Secrets into the new
  public repository, using GitHub's encrypted Actions Secrets API.
- Those four values must be the **same** values previously used to sign
  production APKs. Never regenerate a release key merely because the GitHub
  repository is new.
- Verify the signature with Android's `apksigner` or by installing the newly
  signed APK directly over an existing release **without uninstalling it**.
  Android rejects an APK signed with a different certificate.
- The public app's updater targets the new public GitHub Releases. The older
  installed app still points to private Releases, so v0.5.2 is installed once
  manually. From v0.5.3 onward, the app checks GitHub Releases and opens the official release page in a browser; it cannot download or install APKs itself.
- Debug APKs have the distinct `.debug` app ID and cannot update production.

See [public-upgrade.md](public-upgrade.md).
