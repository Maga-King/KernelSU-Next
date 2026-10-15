import importlib.util
import hashlib
import shutil
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('embed', ROOT / 'scripts/embed_static_reference.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BlobTests(unittest.TestCase):
    def test_real_binary(self):
        source = ROOT / 'kernel/feature/reference.policy'
        with tempfile.TemporaryDirectory() as temp:
            header = Path(temp) / 'generated/ref.h'
            version, size, digest = module.embed(source, header)
            data = source.read_bytes()
            self.assertEqual(version, 30)
            self.assertEqual(size, len(data))
            self.assertEqual(digest, hashlib.sha256(data).hexdigest())
            text = header.read_text()
            body = text.split('{', 1)[1].split('}', 1)[0]
            recovered = bytes(int(word.strip(), 16) for word in body.split(',') if word.strip())
            self.assertEqual(recovered, data)

    def test_invalid_header(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / 'bad.policy'
            target = Path(temp) / 'bad.h'
            for data in [b'', b'(allow a b (file (read)))\n',
                         struct.pack('<II8sII', 0xF97CFF8C, 8, b'SE Linux', 99, 0)]:
                source.write_bytes(data)
                with self.assertRaises(ValueError):
                    module.embed(source, target)
                self.assertFalse(target.exists())

    def test_enforcement_path_not_replaced(self):
        source = (ROOT / 'kernel/feature/static_reference.c').read_text()
        self.assertNotIn('rcu_assign_pointer', source)
        self.assertNotIn('selinux_state.policy =', source)
        rules = (ROOT / 'kernel/selinux/rules.c').read_text()
        self.assertIn('backup_sepolicy = ksu_create_query_reference(old_pol);', rules)
        self.assertIn('pol = ksu_dup_sepolicy(old_pol);', rules)
        self.assertIn('rcu_assign_pointer(selinux_state.policy, pol);', rules)

    @unittest.skipUnless(shutil.which('make'), 'make is required for the build-rule test')
    def test_build_fragment_generates_blob_only_when_enabled(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            makefile = folder / 'Makefile'
            makefile.write_text(f'''KSU_KERNEL_DIR := {ROOT / 'kernel'}
obj := {folder}
PYTHON3 := python3
.PHONY: inspect FORCE
define if_changed
$(cmd_$(1))
endef
include $(KSU_KERNEL_DIR)/feature/static_reference.mk
inspect:
\t@echo objects=$(kernelsu-objs)
\t@echo flags=$(ccflags-y)
FORCE:
''')
            for enabled in ('n', 'y'):
                args = ['make', '--no-print-directory', '-f', str(makefile),
                        f'CONFIG_KSU_STATIC_SELINUX_REFERENCE={enabled}',
                        f'KBUILD_EXTMOD={ROOT / "kernel"}']
                output = subprocess.check_output([*args, 'inspect'], text=True)
                if enabled == 'n':
                    self.assertNotIn('feature/static_reference.o', output)
                    self.assertNotIn('-DCONFIG_KSU_STATIC_SELINUX_REFERENCE', output)
                else:
                    self.assertIn('feature/static_reference.o', output)
                    self.assertIn('-DCONFIG_KSU_STATIC_SELINUX_REFERENCE=1', output)
                    header = folder / 'feature/static_reference_blob.h'
                    subprocess.run([*args, str(header)], check=True, capture_output=True)
                    self.assertIn('ksu_static_reference_blob', header.read_text())

    def test_query_reference_fallback_with_feature_enabled_and_disabled(self):
        compiler = shutil.which('cc') or shutil.which('clang') or shutil.which('gcc')
        if not compiler:
            self.skipTest('C compiler not installed')
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / 'linux').mkdir()
            (folder / 'selinux').mkdir()
            (folder / 'linux/err.h').write_text('''#include <stdint.h>
#define IS_ERR(p) ((uintptr_t)(p) >= (uintptr_t)-4095)
#define PTR_ERR(p) ((long)(p))
''')
            (folder / 'klog.h').write_text('#define pr_warn(...) ((void)0)\n')
            (folder / 'selinux/sepolicy.h').write_text('''struct selinux_policy { int id; };
struct selinux_policy *ksu_dup_sepolicy(struct selinux_policy *actual);
''')
            source = folder / 'test.c'
            source.write_text('''#include <assert.h>
#include "feature/static_reference.h"
static struct selinux_policy active, fixed, backup;
static int duplicate_calls, reference_calls;
static struct selinux_policy *reference_result, *backup_result;
struct selinux_policy *ksu_dup_sepolicy(struct selinux_policy *actual) {
    assert(actual == &active);
    ++duplicate_calls;
    return backup_result;
}
struct selinux_policy *ksu_static_reference_create(struct selinux_policy *actual) {
    assert(actual == &active);
    ++reference_calls;
    return reference_result;
}
int main(void) {
    reference_result = &fixed;
    backup_result = &backup;
#ifdef CONFIG_KSU_STATIC_SELINUX_REFERENCE
    assert(ksu_create_query_reference(&active) == &fixed);
    assert(reference_calls == 1 && duplicate_calls == 0);
    reference_result = (void *)-12;
    assert(ksu_create_query_reference(&active) == &backup);
    assert(reference_calls == 2 && duplicate_calls == 1);
    backup_result = (void *)-22;
    assert(ksu_create_query_reference(&active) == backup_result);
#else
    assert(ksu_create_query_reference(&active) == &backup);
    assert(reference_calls == 0 && duplicate_calls == 1);
    backup_result = (void *)-12;
    assert(ksu_create_query_reference(&active) == backup_result);
#endif
    return 0;
}
''')
            for enabled in (False, True):
                with self.subTest(enabled=enabled):
                    binary = folder / ('test-enabled' if enabled else 'test-disabled')
                    flags = ['-DCONFIG_KSU_STATIC_SELINUX_REFERENCE=1'] if enabled else []
                    subprocess.run([compiler, '-Wall', '-Werror', *flags,
                                    '-I', str(folder), '-I', str(ROOT / 'kernel'),
                                    str(source), '-o', str(binary)], check=True)
                    subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    unittest.main()
