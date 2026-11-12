# Codemagic Personal/Free: RelayHarbor v0.5.3 signed releases

A paid Team is NOT needed. Store all secrets under **RelayHarbor-Public → App settings
→ Environment variables**, mark **Secret**, and use these group names:

| Group | Name | Value |
| --- | --- | --- |
| relayharbor_signing | RELAYHARBOR_KEYSTORE_BASE64 | Base64 of the ORIGINAL production .jks |
| relayharbor_signing | RELAYHARBOR_STORE_PASSWORD | Original keystore password |
| relayharbor_signing | RELAYHARBOR_KEY_ALIAS | Original alias |
| relayharbor_signing | RELAYHARBOR_KEY_PASSWORD | Original key password |
| relayharbor_github | GITHUB_TOKEN | GitHub fine-grained PAT, Contents Read and write on RelayHarbor-Public |

To copy the keystore Base64 on Windows without printing it to the terminal:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\original-release.jks')) | Set-Clipboard
```

Paste the clipboard into Codemagic's Secret value field, then clear the clipboard.
NEVER paste the JKS, Base64, token, or passwords into chat, GitHub, scripts,
or repository files.

**No new JKS:** v0.5.3 MUST use the same signer certificate as the public
v0.5.2; the release script downloads v0.5.2 and compares the SHA-256 of the
signing certificate. A missing original keystore cannot be recovered from a
public APK or read back from GitHub Actions Secrets.

## Release safety

1. Merge this PR only after the **RelayHarbor PR Verification** check is green.
2. Confirm `app/build.gradle.kts` is v0.5.3, versionCode 8, on main.
3. Add app-level secrets, then create and push a tag `v0.5.3` on main.
4. `RelayHarbor Signed APK Release` on Codemagic reconstructs the JKS in
   a temporary folder, signs the APK, checks v0.5.2 signer continuity, then
   publishes GitHub Releases. It removes the temporary keystore after the job.
5. DO NOT bypass Google Play Protect if it reports a harmful app. Inspect
   the APK and request Play Protect re-review if necessary.

The PR workflow does NOT import these secrets. No team or paid billing is needed.
