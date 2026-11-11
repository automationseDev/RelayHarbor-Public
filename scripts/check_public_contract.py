#!/usr/bin/env python3
"""Guard the Android upgrade contract during public repository bootstrap.

This checks project invariants, not the cryptographic equality of signing
certificates. The public release must reuse the original four signing secrets.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_PACKAGE = "net.automationse.relayharbor"
PUBLIC_REPO = "automationseDev/RelayHarbor-Public"
PUBLIC_GHCR = "ghcr.io/automationsedev/relayharbor-public-relay"


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit("PUBLIC UPGRADE CONTRACT FAILED: " + message)


gradle = (ROOT / "app/build.gradle.kts").read_text()
release = (ROOT / ".github/workflows/release.yml").read_text()
updater = (ROOT / "app/src/main/java/net/automationse/relayharbor/integration/AppUpdater.kt").read_text()
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
registry = (ROOT / ".github/workflows/relay-image.yml").read_text()
deploy = (ROOT / "relay/scripts/nas-deploy.sh").read_text()
poll = (ROOT / "relay/scripts/nas-auto-update.sh").read_text()

pkg = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
namespace = re.search(r'namespace\s*=\s*"([^"]+)"', gradle)
code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
require(pkg is not None and pkg.group(1) == EXPECTED_PACKAGE,
        "Android applicationId must never change; existing installs must upgrade in-place")
require(namespace is not None and namespace.group(1) == EXPECTED_PACKAGE,
        "The Kotlin Android namespace must remain unchanged")
require(code is not None and int(code.group(1)) >= 7,
        "The first public upgrade must use versionCode >= 7 (v0.5.1 was code 6)")
require(name is not None and tuple(int(x) for x in name.group(1).split(".")) >= (0, 5, 2),
        "The first public upgrade must be v0.5.2 or newer")
require('applicationIdSuffix = ".debug"' in gradle,
        "Debug builds must keep a separate applicationId")
require('signingConfig = signingConfigs.getByName("production")' in gradle,
        "Release builds must be signed with the existing production keystore")
for key in ("RELAYHARBOR_KEYSTORE_PATH", "RELAYHARBOR_STORE_PASSWORD",
            "RELAYHARBOR_KEY_ALIAS", "RELAYHARBOR_KEY_PASSWORD"):
    require(key in gradle, f"The signing configuration must preserve {key}")
for secret in ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
    require("secrets." + secret in release, f"Release workflow needs {secret} from GitHub Secrets")
require("https://api.github.com/repos/" + PUBLIC_REPO + "/releases/latest" in updater,
        "In-app updater must use public GitHub Releases without a GitHub token")
require('requestPackageInstalls' not in updater or "canRequestPackageInstalls" in updater,
        "Android install permission check must remain present")
require("PackageInfoCompat.getLongVersionCode(archive)" in updater,
        "Updater must reject APKs that are not newer")
require("release.sha256" in updater and "MessageDigest.getInstance(\"SHA-256\")" in updater,
        "Updater must verify APK SHA-256")
require("context.packageName" in updater and "FileProvider.getUriForFile" in updater,
        "Updater must validate the package and launch the safe installer")
require("android.permission.REQUEST_INSTALL_PACKAGES" in manifest,
        "The updater needs Android's install permission request")
require("relayharbor-public-relay" in registry, "Public GHCR workflow must use its own image")
require(PUBLIC_GHCR in deploy and PUBLIC_GHCR in poll,
        "NAS deployment and auto-updater must use the public GHCR image")
require("relay_relay_data" in deploy, "Preserve the existing NAS SQLite volume")
require("relay" in deploy, "Preserve the existing Compose project identity")

print(f"Upgrade contract passed: {EXPECTED_PACKAGE}, "
      f"v{name.group(1)} (code {code.group(1)}), public releases + GHCR")
