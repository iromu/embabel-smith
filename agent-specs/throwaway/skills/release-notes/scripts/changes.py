#!/usr/bin/env python3
# SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Report the figures behind release-note bullets for the files staged for this run.

Deterministic by construction: the files are visited in name order, every figure is
computed from the bytes, nothing here reads a clock, and the digest of a set depends only
on the set. Two runs over the same files print the same lines.

Input files are the ones the caller passed to the script tool. The engine stages them in
the directory named by INPUT_DIR; the working directory is the skill's own folder, so this
script never walks a path it was handed - it reads what was staged.

It reports facts and stops there. Which severity label a file deserves is decided from the
skill's rules file, not from a file name, so no label is printed here.
"""

import hashlib
import os
import sys
from pathlib import Path

SOURCE_SUFFIXES = {".py", ".java", ".kt", ".kts", ".ts", ".tsx", ".js", ".mjs", ".go", ".rs", ".c", ".h", ".cpp"}
DOC_SUFFIXES = {".md", ".markdown", ".rst", ".adoc", ".txt"}
CONFIG_SUFFIXES = {".yml", ".yaml", ".toml", ".ini", ".cfg", ".json", ".xml", ".properties", ".env"}
TEST_MARKERS = ("test", "spec", "check")


def staged_files() -> list[tuple[str, bytes]]:
    """The staged files, as (name, bytes), sorted by name."""
    folder = os.environ.get("INPUT_DIR", "")
    if not folder:
        return []
    root = Path(folder)
    if not root.is_dir():
        return []
    return [(path.name, path.read_bytes()) for path in sorted(root.iterdir(), key=lambda p: p.name) if path.is_file()]


def line_count(data: bytes) -> int:
    """Lines of text, counting an unterminated final line."""
    if not data:
        return 0
    return data.count(b"\n") if data.endswith(b"\n") else data.count(b"\n") + 1


def classify(name: str, data: bytes) -> str:
    """What kind of file this is, from its name and its bytes alone.

    A classification, not a verdict: it says what to read, not what changed.
    """
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return "unreadable"
    if b"\x00" in data:
        # Valid UTF-8 can still be a binary: a NUL byte is the cheapest honest signal
        # that there is no text here to describe.
        return "unreadable"
    lowered = name.lower()
    suffix = Path(lowered).suffix
    if any(marker in lowered for marker in TEST_MARKERS):
        return "test"
    if suffix in SOURCE_SUFFIXES:
        return "source"
    if suffix in DOC_SUFFIXES:
        return "documentation"
    if suffix in CONFIG_SUFFIXES:
        return "configuration"
    return "other"


def main() -> int:
    files = staged_files()

    if not files:
        print("changed files: 0")
        print("no input files staged; pass the changed files as the script's input files")
        return 0

    print(f"changed files: {len(files)}")
    by_kind: dict[str, list[int]] = {}
    total_bytes = 0
    total_lines = 0

    for name, data in files:
        kind = classify(name, data)
        lines = line_count(data)
        print(f"{name}  {len(data)} bytes  {lines} lines  {kind}")
        stats = by_kind.setdefault(kind, [0, 0, 0])
        stats[0] += 1
        stats[1] += len(data)
        stats[2] += lines
        total_bytes += len(data)
        total_lines += lines

    print("by kind:")
    for kind in sorted(by_kind):
        count, size, lines = by_kind[kind]
        print(f"  {kind}  {count} files  {size} bytes  {lines} lines")

    print(f"totals: {len(files)} files, {total_bytes} bytes, {total_lines} lines")
    unreadable = [name for name, data in files if classify(name, data) == "unreadable"]
    print(f"unreadable: {', '.join(unreadable) if unreadable else 'none'}")

    over = "|".join(f"{name}={hashlib.sha256(data).hexdigest()[:12]}" for name, data in files)
    print(f"set digest: {hashlib.sha256(over.encode('utf-8')).hexdigest()[:16]}")
    print("labels: none assigned here; severity comes from the skill's rules file")
    return 0


if __name__ == "__main__":
    sys.exit(main())
