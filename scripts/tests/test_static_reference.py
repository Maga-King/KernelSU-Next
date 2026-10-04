import importlib.util
import hashlib
import struct
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
        self.assertIn('backup_sepolicy = ksu_static_reference_create(old_pol);', rules)
        self.assertIn('pol = ksu_dup_sepolicy(old_pol);', rules)
        self.assertIn('rcu_assign_pointer(selinux_state.policy, pol);', rules)


if __name__ == '__main__':
    unittest.main()
