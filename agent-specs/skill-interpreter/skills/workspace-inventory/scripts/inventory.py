#!/usr/bin/env python3
# SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Inventory the files staged for this run.

Deterministic by construction: the files are visited in name order, every figure is
computed from the bytes, nothing here reads a clock, and the digest of a set depends only
on the set. Two runs over the same files print the same lines.

Input files are the ones the caller passed to the script tool. The engine stages them in
the directory named by INPUT_DIR; the working directory is the skill's own folder, so this
script never walks a path it was handed - it reads what was staged. An argument, if any,
is an extension to restrict the run to (".md", ".yml"); a path is never taken from the
command line.
"""

import hashlib
import os
import sys
from pathlib import Path


def staged_files(extensions: tuple[str, ...]) -> list[tuple[str, bytes]]:
    """The staged files, as (name, bytes), sorted by name."""
    folder = os.environ.get("INPUT_DIR", "")
    if not folder:
        return []
    root = Path(folder)
    if not root.is_dir():
        return []
    found: list[tuple[str, bytes]] = []
    for path in sorted(root.iterdir(), key=lambda p: p.name):
        if not path.is_file():
            continue
        if extensions and not path.name.lower().endswith(extensions):
            continue
        found.append((path.name, path.read_bytes()))
    return found


def line_count(data: bytes) -> int:
    """Lines of text, counting an unterminated final line."""
    if not data:
        return 0
    return data.count(b"\n") if data.endswith(b"\n") else data.count(b"\n") + 1


def digest(data: bytes) -> str:
    """The short content digest quoted for a file and for the whole set."""
    return hashlib.sha256(data).hexdigest()[:12]


def main() -> int:
    extensions = tuple(a.lower() if a.startswith(".") else f".{a.lower()}" for a in sys.argv[1:])
    files = staged_files(extensions)

    if not files:
        print("no input files staged")
        print("pass the files to examine as the script's input files; nothing was counted")
        return 0

    print(f"files: {len(files)}")
    by_digest: dict[tuple[int, str], list[str]] = {}
    by_extension: dict[str, list[int]] = {}
    total_bytes = 0
    total_lines = 0
    largest: tuple[str, int] = ("", 0)

    for name, data in files:
        short = digest(data)
        print(f"{name}  {len(data)} bytes  {line_count(data)} lines  {short}")
        by_digest.setdefault((len(data), short), []).append(name)
        extension = Path(name).suffix.lower() or "(none)"
        stats = by_extension.setdefault(extension, [0, 0, 0])
        stats[0] += 1
        stats[1] += len(data)
        stats[2] += line_count(data)
        total_bytes += len(data)
        total_lines += line_count(data)
        if len(data) > largest[1]:
            largest = (name, len(data))

    print("by extension:")
    for extension in sorted(by_extension):
        count, size, lines = by_extension[extension]
        print(f"  {extension}  {count} files  {size} bytes  {lines} lines")

    print(f"total: {total_bytes} bytes, {total_lines} lines")
    print(f"largest: {largest[0]} ({largest[1]} bytes)")

    duplicates = {key: names for key, names in by_digest.items() if len(names) > 1}
    if duplicates:
        print("identical files:")
        for key in sorted(duplicates, key=lambda k: k[1]):
            print(f"  {key[1]}  {', '.join(sorted(duplicates[key]))}")
    else:
        print("identical files: none")

    over = "|".join(f"{name}={digest(data)}" for name, data in files)
    print(f"set digest: {hashlib.sha256(over.encode('utf-8')).hexdigest()[:16]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
