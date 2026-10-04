#!/usr/bin/env python3
"""Generate an immutable kernel query-reference blob. Never load a live policy."""
import hashlib
import struct
import sys
from pathlib import Path


def embed(source, target):
    data = Path(source).read_bytes()
    if len(data) < 24 or len(data) > 16 * 1024 * 1024:
        raise ValueError('Reference policy size is invalid')
    magic, tag_len = struct.unpack_from('<II', data)
    if magic != 0xF97CFF8C or tag_len != 8 or data[8:16] != b'SE Linux':
        raise ValueError('Expected a serialized SELinux policy, not CIL or a memory dump')
    version = struct.unpack_from('<I', data, 16)[0]
    if not 30 <= version <= 33:
        raise ValueError('Unsupported reference policy format version')
    digest = hashlib.sha256(data).hexdigest()
    output = Path(target)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', encoding='ascii', newline='\n') as stream:
        stream.write('/* Generated query-only data. Never use as enforcement policy. */\n')
        stream.write('#define KSU_STATIC_REFERENCE_SHA256 "' + digest + '"\n')
        stream.write('static const unsigned char ksu_static_reference_blob[] __aligned(8) = {\n')
        for offset in range(0, len(data), 16):
            stream.write('    ' + ','.join(f'0x{v:02x}' for v in data[offset:offset + 16]) + ',\n')
        stream.write('};\n')
    return version, len(data), digest


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: embed_static_reference.py REFERENCE.policy OUTPUT.h')
    version, size, digest = embed(*sys.argv[1:])
    print(f'query-reference version={version} bytes={size} sha256={digest}')
