# RelayHarbor — Codemagic CI/CD (GitHub Actions-free)

## What is automated

- `relayharbor-pr`: when a PR targets `main`, Codemagic runs privacy checks, Python Relay API tests, Android debug build and Android unit tests. No signing key, GitHub PAT, or other secrets are exposed to PR builds.
- GitHub's protected-branch **auto-merge** may merge a reviewed PR **after** Codemagic's required check succeeds. Codemagic does **not** directly merge PRs in this configuration.
- `relayharbor-release`: when a **new version tag** `vX.Y.Z` is pushed, Codemagic builds and tests the release APK, checks the signing certificate against the published v0.5.2 APK, and uploads to GitHub Releases. No new version is released until the tag is created deliberately.
- Existing GitHub Actions workflows remain available for manual emergency execution, but auto triggers are disabled when this pack is installed using the provided script.

## One-time Codemagic configuration

1. In Codemagic, select your connected `automationseDev/RelayHarbor-Public` application and switch its build configuration to **codemagic.yaml**.
2. Go to **Team settings → Code signing identities → Android keystores**, upload the **original** release JKS used for v0.5.2, and enter its existing keystore password, key alias and key password. Set the keystore reference name to **`relayharbor_release`**. Do not generate a new signing key.
3. Under app or team **Environment variables**, create the group **`relayharbor_github`** containing the encrypted variable **`GITHUB_TOKEN`**, a narrowly scoped GitHub token for `automationseDev/RelayHarbor-Public` with *Contents: Read and write*. Keep this token out of the source code. This group is attached **only** to the tag-release workflow, never PR checks.
4. Confirm **Webhooks** are connected for **Pull requests** and **Branch or tag creation**. If Codemagic did not install them automatically, add a GitHub repository webhook with payload `https://api.codemagic.io/hooks/<YOUR_CODEMAGIC_APP_ID>` (find the app ID in the Codemagic app URL). See the Codemagic Webhooks page for the event choices.
5. After the first `RelayHarbor PR Verification` build reports a check to GitHub, update **GitHub Settings → Rules / Branch protection** for `main`: require that Codemagic check (and desired human reviews), not old queued Actions checks. Enable **Allow auto-merge**. On approved PRs choose **Enable auto-merge**. No administrator override is required or recommended.

The `mac_mini_m2` runner is selected intentionally for entry-level Codemagic plans. Check your current plan's build minutes and billing before running repeated builds.

## Install the configuration into GitHub with a privacy-preserving commit

Download/unzip the Codemagic pack on Windows, open PowerShell in the extracted folder, then:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Codemagic.ps1
```

The script uses the existing Git and GitHub CLI. It creates a fresh feature branch from main, copies `codemagic.yaml`, scripts, and documentation, changes the old Actions workflows to manual-only, commits under a GitHub `users.noreply.github.com` author, pushes and opens a PR.

**Do not merge your older `feat/local-windows-ci-no-actions` branch**; it is not needed for Codemagic and retains undesirable old commit metadata.

## Test flow

1. Open the new Codemagic PR in GitHub. If the Codemagic webhook is configured, `RelayHarbor PR Verification` starts automatically.
2. If no build starts, in Codemagic click **Start new build** and select the PR branch and **RelayHarbor PR Verification** workflow. Check the Webhooks / Recent deliveries panel.
3. After a green build, manually enable GitHub auto-merge for that PR (following configured branch protections) or merge with the GitHub UI. Later approved PRs can follow the same workflow automatically.
4. Release APKs require a **new** version on `main`. The current public release is `v0.5.2` / versionCode `7`. For the next release, update `app/build.gradle.kts` to something like `versionName = "0.5.3"` and `versionCode = 8` via a tested PR. Merge to `main` first.
5. Create a **new tag** and push it:

```powershell
git switch main
git pull --ff-only origin main
git tag -a v0.5.3 -m "RelayHarbor v0.5.3"
git push origin v0.5.3
```

6. Codemagic tag workflow runs and publishes `RelayHarbor-v0.5.3.apk` to https://github.com/automationseDev/RelayHarbor-Public/releases . In Settings → アプリのアップデート, existing Android installs can download and apply it **without uninstalling**.

## Critical integrity checks

- Android `applicationId` remains `net.automationse.relayharbor`.
- Release APK must use the **same signing certificate** as `v0.5.2`; release script compares the SHA-256 of signer certificates before publishing.
- `versionCode` must increase beyond `7`; the release tag must match `versionName`.
- Release tag commit must be reachable from main.
- Published release tags/assets are never overwritten.
- Never place a GitHub PAT in a PR workflow; a malicious PR can read its own CI environment.
- This migration only covers **Android APK** and Relay API test builds. Existing GHCR Docker image publishing is **not** migrated; do not assume the NAS stable image automatically updates.

## Troubleshooting

- **No automatic Codemagic build:** check `codemagic.yaml` is committed to the PR branch and inspect Codemagic Webhooks > Recent deliveries.
- **No GitHub check:** use the GitHub App integration rather than a generic SSH/HTTP repository connection, then run one build to register the check.
- **PR won't merge:** GitHub branch protection may still require old Actions checks; replace them with the Codemagic check.
- **Signing fails:** verify that `relayharbor_release` references the SAME old production keystore and credentials.
- **GitHub Releases upload fails:** verify `GITHUB_TOKEN` is in the `relayharbor_github` variable group with repository `Contents: Read and write`, and the tag was created on main.
- **Missing Gradle wrapper:** this project downloads and verifies Gradle 8.11.1 in `setup.sh`.

Codemagic references: https://docs.codemagic.io/yaml-quick-start/building-a-native-android-app/ , https://docs.codemagic.io/yaml-notification/github-checks/ , https://docs.codemagic.io/yaml-publishing/github-releases/ .
