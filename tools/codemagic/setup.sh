#!/usr/bin/env bash
set -euo pipefail

# This project does not include a Gradle wrapper. Install pinned Gradle
# using the official SHA-256 checksum, not a floating system Gradle version.
GRADLE_VERSION=8.11.1
GRADLE_INSTALL="$HOME/.relayharbor-ci/gradle-$GRADLE_VERSION"
if [[ ! -x "$GRADLE_INSTALL/bin/gradle" ]]; then
  mkdir -p "$HOME/.relayharbor-ci"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL --retry 3 -o "$tmp/gradle.zip" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  curl -fsSL --retry 3 -o "$tmp/gradle.sha256" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip.sha256"
  expected="$(awk '{print $1}' "$tmp/gradle.sha256" | tr '[:upper:]' '[:lower:]')"
  actual="$(shasum -a 256 "$tmp/gradle.zip" | awk '{print $1}')"
  [[ "$expected" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]] || { echo 'Gradle checksum verification failed' >&2; exit 1; }
  unzip -q "$tmp/gradle.zip" -d "$HOME/.relayharbor-ci"
fi
"$GRADLE_INSTALL/bin/gradle" --version | head -n 15

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK" && -d "$SDK" ]] || { echo 'Android SDK location unavailable (ANDROID_SDK_ROOT / ANDROID_HOME)' >&2; exit 1; }
for folder in 'platforms/android-35' 'build-tools/35.0.0'; do
  [[ -d "$SDK/$folder" ]] || { echo "Missing Android SDK component: $folder" >&2; exit 1; }
done
printf 'sdk.dir=%s\n' "$SDK" > local.properties

python3 -m venv "$HOME/.relayharbor-ci/python-venv"
"$HOME/.relayharbor-ci/python-venv/bin/python" -m pip install --disable-pip-version-check -r relay/requirements.txt 'pytest==8.3.5'
