# Public repository migration — RelayHarbor-Public

This repository is published from **one sanitized source snapshot with a single
new commit**. The old private repository remains private, with all old commits,
PRs and Actions runs unexported.

## Invariants that must never break

| Property | Required |
|---|---|
| Android application ID | `net.automationse.relayharbor` (unchanged) |
| Release signing certificate | **The exact existing certificate** |
| First public app version | `0.5.2` |
| First public app versionCode | `7` (previous v0.5.1 was 6) |
| Android local Room data and settings | Preserved on in-place update |
| First migration | Install signed v0.5.2 APK over existing installation; **do not uninstall** |
| Future app updates | Settings → アプリのアップデート → check / download / Android install confirmation |
| Public release source | `https://github.com/automationseDev/RelayHarbor-Public/releases` |
| Public Docker image | `ghcr.io/automationsedev/relayharbor-public-relay` |
| NAS Compose service/volume | `relay` / `relay_relay_data` (unchanged) |
| NAS secrets and received events | Remain outside Git, stored in existing `.env` and volume |

Do not regenerate the keystore or change the Android package ID. Neither a
GitHub repository rename nor a new keystore is needed to make the source public.

## One-time public repository bootstrap

The **private repository's** manually invoked **Bootstrap Public Repository**
workflow exports its reviewed `feat/public-repo-bootstrap` branch using
`git archive`. The new public repository receives a fresh initial commit from
`github-actions[bot]@users.noreply.github.com`, not the old Git history.

The bootstrap workflow creates `automationseDev/RelayHarbor-Public` using a
**temporary GitHub classic PAT** with `repo` scope in the PRIVATE repository's
`PUBLIC_REPO_BOOTSTRAP_TOKEN` Actions Secret. It never copies that PAT into
the public source or public repository's Secrets. Revoke the PAT after success.

The bootstrap transfers exactly these four encrypted signing Secrets from the
private Actions configuration to the public repository:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

It does **not** transfer Dify/OpenAI credentials, Relay client/webhook tokens,
deployment configuration, GitHub webhook destinations or other personal secrets.

## Signing certificate privacy check

Before making the first public APK downloadable, inspect the subject/owner
metadata of the **existing** production keystore on a trusted machine:

```bash
keytool -list -v -keystore /safe/location/relayharbor-release.jks -alias relayharbor
```

Look at `Owner` / `Issuer` for a real name, personal email, address or
organization details. Do **not** paste the private key, password or complete
keystore into a chat or issue. If the existing certificate includes personally
identifying metadata, it cannot be changed while retaining signature-based
in-place upgrades to the old APK; decide whether to keep the identity or
publish a new app identity that cannot overwrite the old installation.

## First public Android release

1. After the bootstrap succeeds, open this public repository's
   **Actions → Signed APK Release → Run workflow**.
2. Select `main` and tag `v0.5.2`. Verify the workflow and release asset
   `RelayHarbor-v0.5.2.apk` succeed.
3. Install that APK **without uninstalling** the currently installed production
   RelayHarbor. The Android package installer should offer `更新` rather than
   treating it as a different app.
4. Confirm existing chat history, integrations, settings and received
   notifications are still present.
5. For v0.5.3+, use **Settings → アプリのアップデート**. The app retrieves signed
   APK assets from this public repository and performs SHA-256 checks before
   launching the normal Android install confirmation.

If Android reports `App not installed` or a signing conflict, **STOP**.
The new APK was not signed with the same signing certificate. Correct the
public repository's signing Secrets; do not uninstall the existing application.

## First public GHCR image and NAS migration

1. In this public repo, run **Actions → Publish Relay Docker Image (GHCR)
   → Run workflow → main → release** to publish `v0.5.2` and `stable`
   for `ghcr.io/automationsedev/relayharbor-public-relay`.
2. Confirm the GHCR package is readable from your NAS. The package visibility
   is independent of repository visibility; make the package Public if desired
   or retain your read-only `docker login ghcr.io`.
3. Preserve the original on-NAS `relay/.env` and `relay/.deploy/`.
   Make an external backup of SQLite and `.env` before switching source.
4. From your existing PRIVATE git checkout **on NAS** (not the new public URL),
   add and fetch the public remote:

   ```bash
   cd /path/to/RelayHarbor
   git status --short
   git remote add public https://github.com/automationseDev/RelayHarbor-Public.git
   git fetch public main
   git switch -c public-main --track public/main
   cd relay
   bash scripts/nas-deploy.sh stable
   curl -fsS http://127.0.0.1:8088/healthz
   ```

   Review `git status --short` first: resolve local changes before switching.
   This intentionally leaves the on-NAS checkout location untouched, so ignored
   `.env` and `.deploy/` stay in place. If the `public` remote already exists,
   don't run `git remote add` again.

5. Verify the original public Cloudflare hostname still routes to the same
   `http://relay:8000` service; **do not** run `docker compose down -v`.
   Old webhook connections, RSS sources and notifications remain in SQLite.
6. Existing NAS Task Scheduler commands calling `relay/scripts/nas-auto-update.sh
   stable` now poll the **public image**. Updates still require the explicit
   release channel rather than automatic `edge` deployment.

## Common limitations

- The older v0.5.1 app's updater pointed to GitHub Releases in a **private**
  repository and will not automatically discover this new public URL. The
  v0.5.2 manual over-install is the one-time migration.
- Android's normal install approval is required. This is not a silent install.
- Source code Public does **not** make the single-user Relay suitable for
  unrelated accounts. Each user must have their own trusted backend or future
  supported cloud service.
- The private development repository's original Git history is intentionally
  preserved only in the private repository. Do **not** push old branches,
  merge unrelated histories, or run `git push --mirror` to the public repo.
- Publicizing source code without a license does not grant broad open-source
  reuse rights. A separate license decision can be made before community
  contributions are accepted.
