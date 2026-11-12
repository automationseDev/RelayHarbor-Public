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

Bump `versionCode` monotonically for every new production release and update
`versionName` through a PR. Wait for **Android CI, Relay API Tests and Privacy
Preflight** to pass, then merge the PR into `main`. You no longer need to make
a Git tag locally. When the merged PR changes `app/build.gradle.kts`, the
**Signed APK Release** workflow starts automatically on the main push. This
workflow re-runs production tests and signing checks before tagging or publishing.
Ordinary code-only main pushes do not trigger a release.

For a manual retry or an intentionally manual release, open
**Actions → Signed APK Release → Run workflow**, select `main` and
leave the optional `tag` field blank. The workflow derives the tag from
`app/build.gradle.kts` (for example `versionName = "0.5.4"` becomes
`v0.5.4`). Alternatively, from PowerShell:

```powershell
gh workflow run release.yml -R automationseDev/RelayHarbor-Public --ref main
```

The workflow builds and tests the production APK **before creating a tag**.
It validates the compiled application ID and version, verifies that
`REQUEST_INSTALL_PACKAGES` is absent, compares the signing certificate with
v0.5.2 and requires `versionCode` to exceed the latest published APK.
Only after these checks does it create an annotated tag with the GitHub Actions
bot's noreply identity and publish the signed APK + SHA-256 checksum.
Existing tags are never moved, and existing GitHub Releases are never overwritten.

If tag creation succeeded but publication failed, rerun the same workflow on
`main` with the **existing tag** in the optional input (even if main's
`versionName` has advanced). It can resume from the tagged commit only if
the tag belongs to merged `main` history, the release does not already exist,
and all release checks still pass. A previously published release cannot be
replaced by retrying this workflow.

The four original signing secrets are required and must not be deleted.
Never distribute Debug APK as production, regenerate the production keystore
or remove the original signing credentials merely to ship an upgrade.

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
