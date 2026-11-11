#!/usr/bin/env bash
set -euo pipefail
[[ -n "${CM_TAG:-}" && "${CM_TAG}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Not a valid tagged release' >&2; exit 1; }
[[ -n "${GITHUB_TOKEN:-}" ]] || { echo 'Set GITHUB_TOKEN as an encrypted variable in the Codemagic relayharbor_github group' >&2; exit 1; }
export GH_TOKEN="$GITHUB_TOKEN"
if ! command -v gh >/dev/null 2>&1; then
  echo 'GitHub CLI not installed; installing via Homebrew'
  brew install gh
fi
apk="dist/RelayHarbor-${CM_TAG}.apk"
[[ -s "$apk" ]] || { echo 'No verified APK available' >&2; exit 1; }

# Avoid overwriting historical release assets. gh uses GH_TOKEN automatically.
if gh release view "$CM_TAG" --repo automationseDev/RelayHarbor-Public >/dev/null 2>&1; then
  echo "Release $CM_TAG already exists. Refusing to overwrite it." >&2
  exit 1
fi
gh release create "$CM_TAG" \
  "$apk" "${apk}.sha256" \
  --repo automationseDev/RelayHarbor-Public \
  --verify-tag \
  --title "RelayHarbor ${CM_TAG}" \
  --generate-notes
echo "Published https://github.com/automationseDev/RelayHarbor-Public/releases/tag/${CM_TAG}"
