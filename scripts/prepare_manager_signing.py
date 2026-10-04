#!/usr/bin/env python3
"""Configure release signing only after checking the pinned public certificate."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SECRET_NAMES = (
    "FORK_MANAGER_KEYSTORE", "FORK_MANAGER_STORE_PASSWORD",
    "FORK_MANAGER_KEY_ALIAS", "FORK_MANAGER_KEY_PASSWORD",
)


def prepare(manager, env=None, keytool="keytool"):
    env = dict(os.environ if env is None else env)
    if any(not env.get(name) for name in SECRET_NAMES):
        raise ValueError("Missing fork signing secrets; refusing debug-key fallback")
    manifest = json.loads((ROOT / "manager/signing-certificate.json").read_text())
    if env["FORK_MANAGER_KEY_ALIAS"] != manifest["key_alias"]:
        raise ValueError("Signing key alias does not match the pinned alias")
    for name in ("FORK_MANAGER_STORE_PASSWORD", "FORK_MANAGER_KEY_PASSWORD"):
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,}", env[name]):
            raise ValueError("Signing passwords must use safe property characters")
    key = base64.b64decode(env["FORK_MANAGER_KEYSTORE"], validate=True)
    if not key:
        raise ValueError("Empty keystore")
    manager = Path(manager)
    fd, temp_name = tempfile.mkstemp(prefix=".fork-manager-", suffix=".jks", dir=manager)
    temporary_key = Path(temp_name)
    try:
        with os.fdopen(fd, "wb") as target:
            target.write(key)
        result = subprocess.run([
            keytool, "-exportcert", "-keystore", str(temporary_key),
            "-alias", env["FORK_MANAGER_KEY_ALIAS"],
            "-storepass:env", "FORK_MANAGER_STORE_PASSWORD",
        ], env=env, capture_output=True)
        if result.returncode:
            raise ValueError("Could not export the signing certificate")
        certificate = result.stdout
        if (len(certificate) != manifest["certificate_size"] or
                hashlib.sha256(certificate).hexdigest() != manifest["sha256"]):
            raise ValueError("Keystore certificate is not trusted by this kernel source")
        properties = manager / "gradle.properties"
        original = properties.read_text(encoding="utf-8") if properties.exists() else ""
        names = {"KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"}
        lines = [line for line in original.splitlines()
                 if re.split(r"\s*[=:]\s*", line.strip(), maxsplit=1)[0] not in names]
        lines.extend([
            "KEYSTORE_FILE=key.jks",
            "KEYSTORE_PASSWORD=" + env["FORK_MANAGER_STORE_PASSWORD"],
            "KEY_ALIAS=" + env["FORK_MANAGER_KEY_ALIAS"],
            "KEY_PASSWORD=" + env["FORK_MANAGER_KEY_PASSWORD"],
        ])
        temporary_key.replace(manager / "key.jks")
        properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
        properties.chmod(0o600)
        print("Release signing configured: pinned fork certificate (no debug fallback)")
    finally:
        temporary_key.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        prepare(Path.cwd())
    except (ValueError, OSError) as error:
        # Never print command arguments, environment values or keytool output.
        print("Signing preparation failed: " + str(error), file=sys.stderr)
        sys.exit(1)
