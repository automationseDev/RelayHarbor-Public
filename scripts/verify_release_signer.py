#!/usr/bin/env python3
"""Compare APK v2 signer certificates without relying on apksigner text formatting.

The release workflow must additionally use `apksigner verify` to cryptographically
verify APK signatures. This tool only reads public X.509 certificates.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import zipfile

MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
# Pinned to the original public production release, not the signing private key.
BASELINE_APK_SHA256 = "3b0f7bdd3e5dd4dd436d70b5f158724699a6df2b85e14fa28d5792422e317b72"
BASELINE_CERT_SHA256 = "efe61f8a76195d2c794bf9749ed2e294949c94c1b5946fee2132c4e1cb213470"


class SigningBlockError(ValueError):
    pass


def uint(data: bytes, offset: int, size: int) -> int:
    if offset < 0 or offset + size > len(data):
        raise SigningBlockError("truncated APK signing block")
    return int.from_bytes(data[offset:offset + size], "little")


def length_prefixed(data: bytes, offset: int) -> tuple[bytes, int]:
    length = uint(data, offset, 4)
    start = offset + 4
    end = start + length
    if end > len(data):
        raise SigningBlockError("invalid length-prefixed signer data")
    return data[start:end], end


def signer_certificates(apk_path: Path) -> set[str]:
    with zipfile.ZipFile(apk_path) as archive:
        central_directory_offset = archive.start_dir
    apk = apk_path.read_bytes()
    if (central_directory_offset < 32 or
            apk[central_directory_offset - 16:central_directory_offset] != MAGIC):
        raise SigningBlockError("APK v2 signing block is missing")
    size = uint(apk, central_directory_offset - 24, 8)
    start = central_directory_offset - size - 8
    if start < 0 or uint(apk, start, 8) != size:
        raise SigningBlockError("APK signing block size mismatch")
    offset = start + 8
    end = central_directory_offset - 24
    certificates = set()
    while offset < end:
        pair_length = uint(apk, offset, 8)
        offset += 8
        if pair_length < 4 or offset + pair_length > end:
            raise SigningBlockError("invalid APK signing block pair")
        pair_id = uint(apk, offset, 4)
        payload = apk[offset + 4:offset + pair_length]
        offset += pair_length
        if pair_id != V2_ID:
            continue
        signers, consumed = length_prefixed(payload, 0)
        if consumed != len(payload):
            raise SigningBlockError("unexpected trailing v2 signer data")
        signer_offset = 0
        while signer_offset < len(signers):
            signer, signer_offset = length_prefixed(signers, signer_offset)
            signed_data, _ = length_prefixed(signer, 0)
            _, position = length_prefixed(signed_data, 0)
            certificate_list, _ = length_prefixed(signed_data, position)
            leaf_certificate, _ = length_prefixed(certificate_list, 0)
            if not leaf_certificate:
                raise SigningBlockError("signer certificate missing")
            certificates.add(hashlib.sha256(leaf_certificate).hexdigest())
    if offset != end or not certificates:
        raise SigningBlockError("no APK v2 signer certificates found")
    return certificates


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: verify_release_signer.py baseline.apk candidate.apk", file=sys.stderr)
        return 2
    baseline, candidate = map(Path, sys.argv[1:])
    try:
        baseline_sha = hashlib.sha256(baseline.read_bytes()).hexdigest()
        if baseline_sha != BASELINE_APK_SHA256:
            raise SigningBlockError("v0.5.2 baseline APK SHA256 does not match trusted release")
        baseline_signers = signer_certificates(baseline)
        candidate_signers = signer_certificates(candidate)
        if baseline_signers != {BASELINE_CERT_SHA256}:
            raise SigningBlockError("v0.5.2 signing certificate changed unexpectedly")
        if baseline_signers != candidate_signers:
            print("::error::APK signer certificate differs from the trusted v0.5.2 release.", file=sys.stderr)
            print("Baseline certificate SHA256: " + ", ".join(sorted(baseline_signers)), file=sys.stderr)
            print("Candidate certificate SHA256: " + ", ".join(sorted(candidate_signers)), file=sys.stderr)
            return 1
        print("Signer verification OK: SHA256=" + BASELINE_CERT_SHA256)
        return 0
    except (OSError, zipfile.BadZipFile, SigningBlockError) as error:
        print(f"::error::Cannot verify APK certificate: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
