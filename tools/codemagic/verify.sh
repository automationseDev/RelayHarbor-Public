#!/usr/bin/env bash
set -euo pipefail
mode="${1:?Usage: verify.sh debug|release}"
[[ "$mode" == debug || "$mode" == release ]] || { echo 'Invalid mode' >&2; exit 2; }

python3 scripts/privacy_preflight.py
python3 scripts/check_public_contract.py
PYTHONPATH=relay "$HOME/.relayharbor-ci/python-venv/bin/python" -m pytest -q \
  relay/test_server.py relay/test_connections.py relay/test_rss.py

GRADLE="$HOME/.relayharbor-ci/gradle-8.11.1/bin/gradle"
# Inspect the merged Android manifest of the APK actually built.
check_apk_permissions() {
  local apk="$1"
  local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  local aapt="$sdk/build-tools/35.0.0/aapt"
  [[ -x "$aapt" ]] || { echo "aapt is missing: $aapt" >&2; exit 1; }
  local permissions
  permissions="$("$aapt" dump permissions "$apk")" || {
    echo "Failed to inspect compiled APK manifest: $apk" >&2
    exit 1
  }
  if grep -Fq 'android.permission.REQUEST_INSTALL_PACKAGES' <<< "$permissions"; then
    echo "Forbidden APK self-install permission found: $apk" >&2
    exit 1
  fi
}
if [[ "$mode" == debug ]]; then
  "$GRADLE" --no-daemon :app:assembleDebug :app:testDebugUnitTest
  test -s app/build/outputs/apk/debug/app-debug.apk
  check_apk_permissions app/build/outputs/apk/debug/app-debug.apk
  exit 0
fi

# Only signed release builds may access Codemagic's keystore/secret group.
[[ -n "${CM_TAG:-}" ]] || { echo 'Release must be triggered by a Git tag (not manual PR build)' >&2; exit 1; }
# On a Codemagic Personal/Free account, reconstitute the ORIGINAL
# keystore from app-level Secret environment variables. Never log the data.
for variable in RELAYHARBOR_KEYSTORE_BASE64 RELAYHARBOR_STORE_PASSWORD RELAYHARBOR_KEY_ALIAS RELAYHARBOR_KEY_PASSWORD; do
  [[ -n "${!variable:-}" ]] || { echo "Missing app-level signing secret: $variable" >&2; exit 1; }
done
SIGNING_TMP="$(mktemp -d "${TMPDIR:-/tmp}/relayharbor-sign.XXXXXXXX")"
chmod 700 "$SIGNING_TMP"
trap 'rm -rf "$SIGNING_TMP"' EXIT
export CM_KEYSTORE_PATH="$SIGNING_TMP/release.jks"
python3 - <<'PY_SIGN'
import base64
import os
from pathlib import Path
payload = os.environ['RELAYHARBOR_KEYSTORE_BASE64'].strip()
try:
    data = base64.b64decode(payload, validate=True)
except (ValueError, base64.binascii.Error):
    raise SystemExit('The encoded signing file is invalid')
if not (100 <= len(data) <= 10_000_000):
    raise SystemExit('Unexpected signing file size')
p = Path(os.environ['CM_KEYSTORE_PATH'])
p.write_bytes(data)
p.chmod(0o600)
PY_SIGN
export CM_KEYSTORE_PASSWORD="$RELAYHARBOR_STORE_PASSWORD"
export CM_KEY_ALIAS="$RELAYHARBOR_KEY_ALIAS"
export CM_KEY_PASSWORD="$RELAYHARBOR_KEY_PASSWORD"

version="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9]*\)/\1/p' app/build.gradle.kts | head -n 1)"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$CM_TAG" == "v$version" ]] || { echo "Tag $CM_TAG must equal Android version v$version" >&2; exit 1; }
[[ "$version_code" =~ ^[0-9]+$ && "$version_code" -gt 7 ]] || { echo 'For releases after v0.5.2, versionCode must be > 7' >&2; exit 1; }

# Verify the tag has been pushed and points into main; never release unmerged code.
if [[ "$(git rev-parse --is-shallow-repository)" == true ]]; then
  git fetch --unshallow origin
fi
git fetch --no-tags origin main
if ! git merge-base --is-ancestor HEAD refs/remotes/origin/main; then
  echo 'Refusing to release a tag that is not part of main' >&2
  exit 1
fi

export RELAYHARBOR_KEYSTORE_PATH="$CM_KEYSTORE_PATH"
export RELAYHARBOR_STORE_PASSWORD="$CM_KEYSTORE_PASSWORD"
export RELAYHARBOR_KEY_ALIAS="$CM_KEY_ALIAS"
export RELAYHARBOR_KEY_PASSWORD="$CM_KEY_PASSWORD"
"$GRADLE" --no-daemon :app:assembleRelease :app:testReleaseUnitTest
unset RELAYHARBOR_KEYSTORE_PATH RELAYHARBOR_STORE_PASSWORD RELAYHARBOR_KEY_ALIAS RELAYHARBOR_KEY_PASSWORD

apk='app/build/outputs/apk/release/app-release.apk'
[[ -s "$apk" ]] || { echo 'Signed release APK not generated' >&2; exit 1; }
check_apk_permissions "$apk"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
apksigner="$SDK/build-tools/35.0.0/apksigner"
[[ -x "$apksigner" ]] || { echo "apksigner unavailable: $apksigner" >&2; exit 1; }

# Compare cryptographic signing certificate to the ALREADY-PUBLISHED v0.5.2.
# This is what permits installing a new APK without uninstalling the old app.
base_apk="$(mktemp -t relayharbor-baseline)"
trap 'rm -f "$base_apk"; rm -rf "$SIGNING_TMP"' EXIT
curl -fsSL --retry 3 -o "$base_apk" \
  'https://github.com/automationseDev/RelayHarbor-Public/releases/download/v0.5.2/RelayHarbor-v0.5.2.apk'
old_digest="$($apksigner verify --print-certs "$base_apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
new_digest="$($apksigner verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
[[ "$old_digest" =~ ^[a-f0-9]{64}$ && "$new_digest" == "$old_digest" ]] || {
  echo 'Signing certificate mismatch! Do not distribute or uninstall the existing app.' >&2
  exit 1
}

mkdir -p dist
cp "$apk" "dist/RelayHarbor-${CM_TAG}.apk"
(cd dist && shasum -a 256 "RelayHarbor-${CM_TAG}.apk" > "RelayHarbor-${CM_TAG}.apk.sha256")
echo "Signed Android $version (versionCode $version_code); existing installation may update in place."
