import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


signing = load("prepare_manager_signing")
verifier = load("verify_manager_apk")


def lp(data):
    return struct.pack("<I", len(data)) + data


def fake_apk(cert=b"certificate", schemes=None, signers=1):
    signed_data = lp(b"digest") + lp(lp(cert)) + lp(b"")
    signer = lp(signed_data) + lp(b"signature") + lp(b"public-key")
    v2 = lp(lp(signer) * signers)
    if schemes is None:
        schemes = [(verifier.V2, v2), (verifier.PADDING, b"")]
    pairs = b"".join(struct.pack("<QI", len(body) + 4, ident) + body
                     for ident, body in schemes)
    size = len(pairs) + 24
    block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
    prefix = b"PK\x03\x04" + bytes(16)
    central_offset = len(prefix) + len(block)
    central = b"PK\x01\x02" + bytes(42)
    eocd = b"PK\x05\x06" + struct.pack("<HHHHIIH", 0, 0, 1, 1, len(central), central_offset, 0)
    return prefix + block + central + eocd


class ManagerSigningTests(unittest.TestCase):
    def test_public_manifest_matches_kernel_pin(self):
        manifest = json.loads((ROOT / "manager/signing-certificate.json").read_text())
        header = (ROOT / "kernel/manager/fork_manager_certificate.h").read_text()
        self.assertIn(str(manifest["certificate_size"]) + "U", header)
        self.assertIn(manifest["sha256"], header)
        self.assertNotIn("PRIVATE KEY", header)
        source = (ROOT / "kernel/manager/apk_sign.c").read_text()
        self.assertIn("ksu_manager_cert_size_allowed(certificate_size, expected_size)", source)
        self.assertIn("ksu_manager_cert_allowed(certificate_size, hash_str, expected_size", source)
        self.assertIn("if (v2_signing_blocks != 1)", source)
        kbuild = (ROOT / "kernel/Kbuild").read_text()
        self.assertIn("79e590113c4c4c0c222978e413a5faa801666957b1212a328e46c00c69821bf7", kbuild)
        self.assertIn("0x3e6", kbuild)

    def test_actual_kernel_helper_accepts_only_paired_certificates(self):
        compiler = shutil.which("cc") or shutil.which("clang") or shutil.which("gcc")
        if not compiler:
            self.skipTest("C compiler not installed")
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / "linux").mkdir()
            (folder / "linux/types.h").write_text("#include <stdbool.h>\n")
            (folder / "linux/string.h").write_text("#include <string.h>\n")
            source = folder / "test.c"
            source.write_text('''#include <assert.h>
#include "manager/fork_manager_certificate.h"
int main(void) {
    const char *official = "79e590113c4c4c0c222978e413a5faa801666957b1212a328e46c00c69821bf7";
    const char *fork = KSU_FORK_MANAGER_CERT_SHA256;
    const char *old_debug = "327c0b14c297121d9c672c5826fa429fb5664af85ac84f30bff51206fb2566a6";
    assert(ksu_manager_cert_size_allowed(998, 998));
    assert(ksu_manager_cert_size_allowed(805, 998));
    assert(!ksu_manager_cert_size_allowed(744, 998));
    assert(ksu_manager_cert_allowed(998, official, 998, official));
    assert(ksu_manager_cert_allowed(805, fork, 998, official));
    assert(!ksu_manager_cert_allowed(998, fork, 998, official));
    assert(!ksu_manager_cert_allowed(805, official, 998, official));
    assert(!ksu_manager_cert_allowed(744, old_debug, 998, official));
    assert(!ksu_manager_cert_allowed(805, old_debug, 998, official));
    assert(!ksu_manager_cert_allowed(998, "anything", 998, official));
    assert(!ksu_manager_cert_allowed(0, fork, 998, official));
    return 0;
}
''')
            exe = folder / ("test.exe" if os.name == "nt" else "test")
            subprocess.run([compiler, "-Wall", "-Werror", "-I", str(folder),
                            "-I", str(ROOT / "kernel"), str(source), "-o", str(exe)], check=True)
            subprocess.run([str(exe)], check=True)

    def test_signing_missing_secrets_fail_before_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "gradle.properties"
            path.write_text("original=yes\n")
            with self.assertRaises(ValueError):
                signing.prepare(Path(temp), {})
            self.assertEqual(path.read_text(), "original=yes\n")
            self.assertFalse((Path(temp) / "key.jks").exists())

    def test_signing_checks_pin_and_replaces_old_configuration(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manager = root / "manager"
            manager.mkdir()
            cert = b"test-public-certificate"
            manifest = {"key_alias": "test-alias", "certificate_size": len(cert),
                        "sha256": hashlib.sha256(cert).hexdigest()}
            (manager / "signing-certificate.json").write_text(json.dumps(manifest))
            props = manager / "gradle.properties"
            original = "original=yes\nKEYSTORE_PASSWORD=obsolete\n"
            props.write_text(original)
            env = dict(zip(signing.SECRET_NAMES, [base64.b64encode(b"keystore").decode(),
                       "a" * 64, "test-alias", "b" * 64]))
            with mock.patch.object(signing, "ROOT", root):
                with mock.patch.object(signing.subprocess, "run", return_value=mock.Mock(
                        returncode=0, stdout=b"wrong")):
                    with self.assertRaises(ValueError):
                        signing.prepare(manager, env)
                self.assertEqual(props.read_text(), original)
                self.assertFalse((manager / "key.jks").exists())
                with mock.patch.object(signing.subprocess, "run", return_value=mock.Mock(
                        returncode=0, stdout=cert)):
                    signing.prepare(manager, env)
            self.assertEqual((manager / "key.jks").read_bytes(), b"keystore")
            self.assertEqual(props.read_text().count("KEYSTORE_PASSWORD="), 1)
            self.assertIn("original=yes", props.read_text())
            self.assertNotIn("obsolete", props.read_text())
            self.assertFalse(list(manager.glob(".fork-manager-*.jks")))

    def test_v2_parser_accepts_valid_shape(self):
        self.assertEqual(verifier.v2_certificate(fake_apk()), b"certificate")

    def test_v2_parser_rejects_duplicates_unsupported_and_truncation(self):
        signed = lp(lp(lp(lp(b"digest") + lp(lp(b"certificate")) + lp(b""))))
        cases = [b"", fake_apk()[:-1], fake_apk(signers=2),
                 fake_apk(schemes=[(verifier.V2, signed), (verifier.V2, signed)]),
                 fake_apk(schemes=[(0xF05368C0, b"v3")]),
                 fake_apk(schemes=[(verifier.V2, b"\xff\xff\xff\xff")]),
                 fake_apk(cert=b"x" * 1025)]
        for data in cases:
            with self.subTest(size=len(data)):
                with self.assertRaises(ValueError):
                    verifier.v2_certificate(data)

    def test_pin_rejects_debug_and_arbitrary_certificates(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "bad.apk"
            apk.write_bytes(fake_apk())
            with self.assertRaises(ValueError):
                verifier.verify_pin(apk)


if __name__ == "__main__":
    unittest.main()
