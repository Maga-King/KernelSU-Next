#!/usr/bin/env python3
"""Verify APK crypto signatures and the certificate accepted by this kernel."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
V2 = 0x7109871A
PADDING = 0x42726577


def v2_certificate(data):
    """Read a bounded V2 signer; reject layouts the kernel parser rejects."""
    def number(fmt, pos, end):
        size = struct.calcsize(fmt)
        if pos < 0 or pos + size > end:
            raise ValueError("Truncated APK signing structure")
        return struct.unpack_from(fmt, data, pos)[0], pos + size

    def field(pos, end):
        size, start = number("<I", pos, end)
        if size > 0x7FFFFFFF or size > end - start:
            raise ValueError("Invalid signing field length")
        return start, start + size

    eocd = -1
    for comment in range(min(65535, len(data) - 22) + 1):
        candidate = len(data) - 22 - comment
        if (data[candidate:candidate + 4] == b"PK\x05\x06" and
                struct.unpack_from("<H", data, candidate + 20)[0] == comment):
            eocd = candidate
            break
    if eocd < 0:
        raise ValueError("No ZIP EOCD")
    if eocd >= 20 and data[eocd - 20:eocd - 16] == b"PK\x06\x07":
        raise ValueError("ZIP64 APK is not supported by the kernel")
    cd_size, _ = number("<I", eocd + 12, len(data))
    central, _ = number("<I", eocd + 16, len(data))
    if central < 32 or central + cd_size != eocd:
        raise ValueError("Invalid central directory bounds")
    if data[central - 16:central] != b"APK Sig Block 42":
        raise ValueError("No APK V2 signing block")
    block_size, _ = number("<Q", central - 24, central)
    if block_size < 24 or block_size > min(0x7FFFFFF7, central - 8):
        raise ValueError("Invalid APK signing block length")
    start = central - block_size - 8
    first_size, pos = number("<Q", start, central - 24)
    if first_size != block_size:
        raise ValueError("APK signing block lengths differ")
    certificate = None
    while pos < central - 24:
        length, pos = number("<Q", pos, central - 24)
        if length < 4 or length > min(0x7FFFFFFF, central - 24 - pos):
            raise ValueError("Invalid APK signing pair length")
        pair_end = pos + length
        scheme, pos = number("<I", pos, pair_end)
        if scheme == V2:
            if certificate is not None:
                raise ValueError("Duplicate APK V2 block")
            signers, signers_end = field(pos, pair_end)
            signer, signer_end = field(signers, signers_end)
            if signer_end != signers_end:
                raise ValueError("Multiple APK signers are not supported")
            signed, signed_end = field(signer, signer_end)
            _, digests_end = field(signed, signed_end)
            certificates, certificates_end = field(digests_end, signed_end)
            cert, cert_end = field(certificates, certificates_end)
            if cert_end != certificates_end or not 0 < cert_end - cert <= 1024:
                raise ValueError("Unexpected APK certificate sequence")
            certificate = data[cert:cert_end]
        elif scheme != PADDING:
            raise ValueError("Unsupported APK signing block: " + hex(scheme))
        pos = pair_end
    if certificate is None:
        raise ValueError("No APK V2 signer")
    return certificate


def verify_pin(apk):
    manifest = json.loads((ROOT / "manager/signing-certificate.json").read_text())
    certificate = v2_certificate(Path(apk).read_bytes())
    if (len(certificate) != manifest["certificate_size"] or
            hashlib.sha256(certificate).hexdigest() != manifest["sha256"]):
        raise ValueError("APK certificate does not match the fork kernel pin")


def find_apksigner():
    existing = shutil.which("apksigner") or shutil.which("apksigner.bat")
    if existing:
        return existing
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        for folder in sorted((Path(sdk) / "build-tools").glob("*"), reverse=True):
            for name in ("apksigner", "apksigner.bat"):
                if (folder / name).is_file():
                    return str(folder / name)
    raise ValueError("Android SDK apksigner is required for cryptographic verification")


def verify(apk, apksigner=None):
    verify_pin(apk)
    subprocess.run([apksigner or find_apksigner(), "verify", "--verbose",
                    "--print-certs", str(apk)], check=True)
    print("PASS: APK signatures verified; certificate matches the kernel fork pin: " + str(apk))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="+")
    parser.add_argument("--apksigner")
    args = parser.parse_args()
    try:
        for apk in args.apk:
            verify(apk, args.apksigner)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print("APK verification failed: " + str(error), file=sys.stderr)
        sys.exit(1)
