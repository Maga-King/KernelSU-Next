#!/usr/bin/env python3
"""Check fork-owned C files without reformatting inherited upstream sources."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = (
    "kernel/feature/static_reference.c",
    "kernel/feature/static_reference.h",
    "kernel/manager/fork_manager_certificate.h",
)

if __name__ == "__main__":
    subprocess.run(
        ["clang-format", "--dry-run", "--Werror", *SOURCES],
        cwd=ROOT, check=True,
    )
