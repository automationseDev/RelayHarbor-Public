# RelayHarbor — GitHub Actions CI/CD

This public repository uses standard GitHub-hosted Ubuntu runners for Android
and Relay tests. The release is manual and uses four existing repository
Actions secrets. Codemagic is no longer part of the build/release pipeline.

## Continuous integration

- `Android CI`: PRs targeting `main`, pushes to `main`, or manual runs.
  Builds the debug APK and runs Android unit tests. Checks the built APK for
  `REQUEST_INSTALL_PACKAGES`, which was removed from v0.5.3.
- `Relay API Tests`: PRs targeting `main`, pushes to `main`, or manual runs.
  Tests Python Relay, deployment shell scripts, and Docker image build.
- `Public Release Privacy Preflight`: PRs targeting `main`, pushes to `main`,
  or manual runs. Prevents accidental disclosure of sensitive local paths.
- `Publish Relay Docker Image (GHCR)`: manual release/edge publishing only;
  it does not connect to or mutate the NAS.

All PR checks use `contents: read`; no signing secrets are shared with PRs.
Require `Android CI / android`, `Relay API Tests / relay`, and
`Public Release Privacy Preflight / current-tree` as branch protection checks
after their first successful registration. Remove obsolete Codemagic required
checks from the main branch protection rule to avoid merge deadlocks.

## Signed APK release (manual only)

1. Verify the version has been merged to main, tests succeeded, and the
   matching annotated Git tag already exists. v0.5.3 tag was created earlier.
2. Run the updated workflow from `main` with the tag as input (the checkout uses the tag commit):

   ```powershell
   gh workflow run release.yml -R automationseDev/RelayHarbor-Public --ref main -f tag=v0.5.3
   ```

3. Check Actions → Signed APK Release. The workflow verifies the tag, original
   JKS credential presence, APK permission manifest, and signing certificate
   against published v0.5.2. It creates SHA-256 and publishes GitHub Releases.
4. If the APK remains blocked by Play Protect, do **not** bypass Play Protect.
   Investigate and request Google review; a successful CI build does not imply
   the Play Protect classification is cleared.

Signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Do not disclose or print values. Use the existing certificate.

## One-time signing key recovery

`.github/workflows/recover-signing-local.yml` is **temporary**, manual only,
and intentionally kept pending confirmation that the encrypted recovery artifact
has been decrypted and the JKS backed up locally. Once confirmed, remove the
workflow, the temporary `RECOVERY_PASSPHRASE` secret, and encrypted artifacts.
Do not accidentally delete the four production signing secrets.

## Removing Codemagic

The Codemagic YAML, helper scripts and documentation are deleted from the Git
repository. Once GitHub Actions is verified, disconnect or delete the external
Codemagic application in the Codemagic web UI. This needs separate user action.
