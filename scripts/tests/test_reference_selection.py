import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('selector', ROOT / 'scripts/select_static_reference.py')
selector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selector)


class ReferenceSelectionTests(unittest.TestCase):
    def test_disabled_needs_no_policy_or_valid_source(self):
        with tempfile.TemporaryDirectory() as temp:
            result = selector.select(False, 'not-an-option', Path(temp))
            self.assertEqual(result['config'], 'n')
            self.assertEqual(result['policy'], '')

    def test_all_three_sources_are_distinct_and_match_their_manifests(self):
        results = [selector.select(True, source) for source in selector.SOURCES]
        self.assertEqual(len({result['sha256'] for result in results}), 3)
        for result in results:
            self.assertEqual(result['config'], 'y')
            self.assertEqual(hashlib.sha256(Path(result['policy']).read_bytes()).hexdigest(), result['sha256'])

    def test_unknown_source_is_rejected(self):
        with self.assertRaises(ValueError):
            selector.select(True, '../../reference.policy')

    def test_tampered_binary_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            directory = root / 'kernel/feature/references'
            directory.mkdir(parents=True)
            manifest = json.loads((ROOT / 'kernel/feature/references/manifest.json').read_text())
            entry = manifest['policies']['hyperos']
            data = bytearray((ROOT / entry['path']).read_bytes())
            data[-1] ^= 1
            (root / entry['path']).write_bytes(data)
            (directory / 'manifest.json').write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, 'does not match'):
                selector.select(True, 'HyperOS', root)

    def test_cli_exports_disabled_config_and_fails_without_partial_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            environment = os.environ.copy()
            environment['GITHUB_ENV'] = str(root / 'env')
            environment['GITHUB_STEP_SUMMARY'] = str(root / 'summary')
            args = [sys.executable, str(ROOT / 'scripts/select_static_reference.py'),
                    '--enabled', 'false', '--source', 'ignored', '--report', str(root / 'report.json')]
            subprocess.run(args, env=environment, capture_output=True, check=True)
            self.assertEqual((root / 'env').read_text(),
                             'CONFIG_KSU_STATIC_SELINUX_REFERENCE=n\nKSU_REFERENCE_POLICY=\n')
            before = (root / 'env').read_bytes()
            args[args.index('false')] = 'true'
            args[-1] = str(root / 'failed.json')
            result = subprocess.run(args, env=environment, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual((root / 'env').read_bytes(), before)
            self.assertFalse((root / 'failed.json').exists())

    @unittest.skipUnless(shutil.which('make'), 'make is required')
    def test_each_selection_generates_its_own_blob_through_kbuild(self):
        for source in selector.SOURCES:
            with self.subTest(source=source), tempfile.TemporaryDirectory() as temp:
                folder = Path(temp)
                result = selector.select(True, source)
                makefile = folder / 'Makefile'
                makefile.write_text(f'''KSU_KERNEL_DIR := {ROOT / 'kernel'}
obj := {folder}
PYTHON3 := {sys.executable}
.PHONY: FORCE
define if_changed
$(cmd_$(1))
endef
include $(KSU_KERNEL_DIR)/feature/static_reference.mk
FORCE:
''')
                header = folder / 'feature/static_reference_blob.h'
                subprocess.run(['make', '--no-print-directory', '-f', str(makefile),
                                'CONFIG_KSU_STATIC_SELINUX_REFERENCE=y',
                                'KBUILD_EXTMOD=' + str(ROOT / 'kernel'),
                                'KSU_REFERENCE_POLICY=' + result['policy'], str(header)],
                               capture_output=True, check=True)
                self.assertIn(result['sha256'], header.read_text())


if __name__ == '__main__':
    unittest.main()
