# Windows local CI/CD (GitHub Actions minutes = 0)

This repository supports **GitHub Actions-free** builds and conditional merges
on an ordinary Windows 10/11 PC. The PC must stay powered on and the task's
Windows account must remain logged in. The existing Synology NAS is **not**
asked to compile Android apps; the NAS keeps serving Relay/SQLite.

## Security and upgrade guarantees

- Production `applicationId = net.automationse.relayharbor` **must not change**.
- Keep the **same production JKS and key alias/passwords** as v0.5.2.
  Never create a new keystore for an existing app. The release script checks
  the new APK signer against the already-published v0.5.2 APK before upload.
- Increase `versionCode` for every Android release. v0.5.2 is code 7;
  the **next** release must have a higher code and new versionName.
- The public GitHub repository remains the release source for the built-in
  Android updater, without embedded API/PAT tokens.
- Real signing passwords are DPAPI-encrypted under the local Windows user's
  `%LOCALAPPDATA%\RelayHarbor-CI\signing.json`. Never commit JKS files or
  private passwords.
- Pull requests are **not automatically trusted**. Only non-draft PRs from
  repository owner `automationseDev` targeting `main`, with manually
  applied label `ci:ready`, may enter the local builder.
- The builder tests the PR's exact SHA in a temporary worktree and rejects
  changes to its head/approval label before merging. Squash merging never
  bypasses GitHub's branch protections or mandatory reviews.

## 1. Windows dependencies

Open an **Administrator PowerShell** for package installation if prompted:

```powershell
winget install --id Git.Git -e --source winget
winget install --id GitHub.cli -e --source winget
winget install --id EclipseAdoptium.Temurin.17.JDK -e --source winget
winget install --id Python.Python.3.12 -e --source winget
winget install --id Google.AndroidStudio -e --source winget
```

Restart PowerShell to refresh PATH. Launch Android Studio once, open
**SDK Manager**, and install **Android SDK Platform 35** and
**Android SDK Build-Tools 35.0.0**, plus the command-line tools.
The scripts detect the default Windows SDK folder
`%LOCALAPPDATA%\Android\Sdk` or `ANDROID_HOME`.

If multiple Java versions are installed, select **JDK 17** with `JAVA_HOME`.
Use a Windows PC with at least 8 GB RAM; 16 GB+ is recommended for
`org.gradle.jvmargs=-Xmx3g`. Do not build Android on a low-memory NAS.

## 2. Clone, authorize GitHub CLI, bootstrap

Open a **normal (non-admin) PowerShell** as the user who will run the task:

```powershell
mkdir C:\Code -Force
cd C:\Code
git clone https://github.com/automationseDev/RelayHarbor-Public.git
cd RelayHarbor-Public
gh auth login -h github.com -p https -w
gh auth status
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Setup-RelayCi.ps1 -InstallTask
```

The Setup script downloads Gradle **8.11.1** from its official distribution
and checks the official SHA-256 file, creates a Python virtual environment,
installs Relay test dependencies, checks the public package/update contract,
creates the `ci:ready` label, and registers a Windows Task Scheduler job
`RelayHarborLocalCI` that runs every 10 minutes.

The scheduled task runs only while that Windows account is logged in. It
does not require a GitHub hosted runner, and requires an outbound internet
connection. The job does **not** bypass review/branch protection.

## 3. Approve a PR for automated build/test/merge

After opening an ordinary PR in GitHub (the PR must come from
`automationseDev` and target `main`), deliberately mark it ready:

```powershell
gh pr edit 12 -R automationseDev/RelayHarbor-Public --add-label ci:ready
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Run-RelayCi.ps1 -Mode Pr -Merge
```

Replace `12` with the actual PR number. The runner:

1. Reads eligible PRs with `gh pr list`.
2. Fetches the exact head SHA into an isolated worktree and simulates
   merging it with `origin/main`.
3. Validates public upgrade invariants and tracked source privacy.
4. Runs Python Relay unit tests and Android debug APK build/unit tests.
5. Reports `relayharbor/local-ci` as a GitHub commit status.
6. Re-checks the PR's head SHA, author, target and label.
7. Squash-merges **only** if everything succeeded and `-Merge` is supplied.

If there are required GitHub Actions checks on `main`, replace them in
**Settings → Rules → Rulesets** with the new
`relayharbor/local-ci` check (after the first local CI status is posted).
Otherwise GitHub will correctly refuse merging when its original required
checks remain queued. Do not use `--admin` to bypass protection.

To dry-run builds **without merging**, omit `-Merge`.

Local logs are written to
`%LOCALAPPDATA%\RelayHarbor-CI\logs`.
To inspect the scheduled task in Windows use **Task Scheduler** →
**RelayHarborLocalCI**.

## 4. One-time signing key configuration

Only needed on a PC that publishes production APKs. You must have the
**original** release keystore; the four GitHub Actions Secret values
cannot be read back from GitHub, and an APK cannot be used to recover a
private signing key.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Set-RelaySigning.ps1 -KeystorePath "D:\secure\relayharbor-release.jks" -Alias "relayharbor"
```

The script prompts for the **original** keystore and key passwords (hidden).
Replace the JKS path and alias with your real existing values. Windows DPAPI
protects the stored passwords for the same Windows user on the same PC.

**Do not run `keytool -genkeypair`** for a project already installed on
Android. Doing so would break in-place upgrades.

## 5. Publish the next APK without GitHub Actions

First, update `app/build.gradle.kts` via a normal PR (and have the PR
validated/merged):

```kotlin
versionCode = 8
versionName = "0.5.3"
```

Then, on your Windows builder:

```powershell
cd C:\Code\RelayHarbor-Public
git switch main
git pull --ff-only origin main
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Run-RelayCi.ps1 -Mode Release
```

The release runner checks tests, builds the **signed** APK using the original
JKS, downloads the official v0.5.2 baseline APK, compares the **signing
certificate SHA-256**, creates/pushes a version tag, and publishes the APK
with `gh release create` to this public repository. If certificate
digests differ, it refuses to publish. It also refuses to overwrite an
existing release/tag.

The Android app's **設定 → アプリのアップデート** then finds new public
releases and opens Android's normal update confirmation. No uninstall needed.

The first setup/test run may take time while Gradle downloads dependencies.
No work continues while the PC is powered off or the scheduled task's user
session is unavailable.

## 6. Optional: Docker GHCR image build outside Actions

If you have Docker Desktop with Linux containers and buildx enabled on the
same PC, log in with a GitHub CLI token allowed to publish packages
(`write:packages`), then:

```powershell
gh auth refresh -s write:packages
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Publish-RelayImage.ps1
# After validation, publish vX.Y.Z and stable:
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\local-ci\Publish-RelayImage.ps1 -Stable
```

Image: `ghcr.io/automationsedev/relayharbor-public-relay`. The optional
NAS `nas-auto-update.sh stable` continues to pull new stable digests,
with backup and rollback logic. GitHub repository visibility and GHCR
package visibility are independent.

## Troubleshooting

- Missing `ci:ready`: intentionally no PRs are built.
- Build logs show `Required status checks pending`: update your branch
  rules from hosted Actions checks to `relayharbor/local-ci`.
- `gh auth status` fails in the scheduled task: authenticate as the **same
  Windows user** that owns the scheduled task.
- `gradle` fails: confirm JDK 17, SDK 35/Build Tools, disk space and RAM.
- Release reports signing mismatch: **do not uninstall** the existing app.
  Retrieve the original signing JKS and its original passwords.
- Branch protection may require human approvals and can block merging;
  local CI does not bypass those rules.
- The existing GitHub Actions YAML files remain for fallback but are configured
  for **manual dispatch only** to avoid spending more hosted runner minutes.
