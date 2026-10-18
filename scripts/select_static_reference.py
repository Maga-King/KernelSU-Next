#!/usr/bin/env python3
"""Select a bundled query-only policy without changing the default policy file."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {'OriginOS': 'originos', 'ColorOS17': 'coloros17', 'HyperOS': 'hyperos'}


def select(enabled, source, root=ROOT):
    if not enabled:
        return {'enabled': False, 'source': None, 'config': 'n', 'policy': '',
                'scope': 'upstream dynamic backup'}
    if source not in SOURCES:
        raise ValueError('Unknown reference source: ' + source)
    catalog = json.loads((root / 'kernel/feature/references/manifest.json').read_text(encoding='utf-8'))
    entry = catalog['policies'][SOURCES[source]]
    path = (root / entry['path']).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Reference path is outside the repository')
    data = path.read_bytes()
    if not 24 <= len(data) <= 16 * 1024 * 1024:
        raise ValueError('Reference policy size is invalid')
    magic, tag_length = struct.unpack_from('<II', data)
    version = struct.unpack_from('<I', data, 16)[0]
    if magic != 0xF97CFF8C or tag_length != 8 or data[8:16] != b'SE Linux' or not 30 <= version <= 33:
        raise ValueError('Invalid serialized SELinux reference')
    digest = hashlib.sha256(data).hexdigest()
    if digest != entry['sha256'] or len(data) != entry['bytes'] or version != entry['policy_version']:
        raise ValueError('Reference does not match its manifest: ' + source)
    return {'enabled': True, 'source': source, 'config': 'y', 'policy': str(path),
            'repository_path': entry['path'], 'sha256': digest, 'bytes': len(data),
            'policy_version': version, 'scope': 'query reference only; enforcement unchanged'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--enabled', choices=('true', 'false', 'y', 'n'), required=True)
    parser.add_argument('--source', default='OriginOS')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    result = select(args.enabled in ('true', 'y'), args.source)
    # Resolve and validate everything before writing environment or output files.
    environment = {'CONFIG_KSU_STATIC_SELINUX_REFERENCE': result['config'],
                   'KSU_REFERENCE_POLICY': result['policy']}
    for value in environment.values():
        if '\n' in value or '\r' in value:
            raise ValueError('Newline in build environment')
    if os.environ.get('GITHUB_ENV'):
        with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as stream:
            for key, value in environment.items():
                stream.write(key + '=' + value + '\n')
    args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as stream:
            stream.write('### SELinux reference\n\n')
            stream.write('Enabled: `' + result['config'] + '`\n\n')
            if result['enabled']:
                stream.write('Source: `' + result['source'] + '`\n\n')
                stream.write('SHA-256: `' + result['sha256'] + '`\n\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
