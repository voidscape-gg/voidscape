#!/usr/bin/env python3
"""Verify an exact, service-readable regular-file tree against a SHA-256 manifest."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import stat
import sys
from pathlib import Path, PurePosixPath


MANIFEST_ROW = re.compile(r"^([0-9a-f]{64})[ \t]+(?:\*)?(.*)$")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)


def read_manifest(path: Path) -> dict[str, str]:
    expected: dict[str, str] = {}
    try:
        rows = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ValueError(f"cannot read SHA-256 manifest {path}: {exc}") from exc

    for line_number, row in enumerate(rows, 1):
        match = MANIFEST_ROW.fullmatch(row)
        if match is None:
            raise ValueError(f"malformed SHA-256 manifest row {line_number}: {row!r}")
        digest, relative_text = match.groups()
        if relative_text.startswith("./"):
            relative_text = relative_text[2:]
        relative = PurePosixPath(relative_text)
        if (
            not relative_text
            or relative.is_absolute()
            or ".." in relative.parts
            or "." in relative.parts
        ):
            raise ValueError(f"unsafe SHA-256 manifest path on row {line_number}: {relative_text!r}")
        normalized = relative.as_posix()
        if normalized in expected:
            raise ValueError(f"duplicate SHA-256 manifest path: {normalized}")
        expected[normalized] = digest

    return expected


def scan_tree(root: Path) -> dict[str, Path]:
    actual: dict[str, Path] = {}

    def visit(directory: Path, relative_directory: PurePosixPath) -> None:
        directory_mode = stat.S_IMODE(directory.lstat().st_mode)
        if directory_mode & 0o001 == 0:
            raise ValueError(f"directory is not service-traversable: {directory} (mode {directory_mode:04o})")
        try:
            entries = sorted(os.scandir(directory), key=lambda entry: entry.name)
        except OSError as exc:
            raise ValueError(f"cannot scan release tree {directory}: {exc}") from exc
        for entry in entries:
            relative = relative_directory / entry.name
            relative_text = relative.as_posix()
            try:
                entry_stat = entry.stat(follow_symlinks=False)
            except OSError as exc:
                raise ValueError(f"cannot inspect release-tree entry {entry.path}: {exc}") from exc
            if stat.S_ISLNK(entry_stat.st_mode):
                raise ValueError(f"symlink is not allowed in an exact release tree: {entry.path}")
            if stat.S_ISDIR(entry_stat.st_mode):
                visit(Path(entry.path), relative)
                continue
            if not stat.S_ISREG(entry_stat.st_mode):
                raise ValueError(f"special file is not allowed in an exact release tree: {entry.path}")
            mode = stat.S_IMODE(entry_stat.st_mode)
            if mode & 0o004 == 0:
                raise ValueError(f"file is not service-readable: {entry.path} (mode {mode:04o})")
            actual[relative_text] = Path(entry.path)

    visit(root, PurePosixPath())
    return actual


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()

    if not args.directory.is_dir() or args.directory.is_symlink():
        fail(f"exact-tree directory is missing or unsafe: {args.directory}")
        return 1
    if not args.manifest.is_file() or args.manifest.is_symlink():
        fail(f"exact-tree manifest is missing or unsafe: {args.manifest}")
        return 1

    try:
        expected = read_manifest(args.manifest)
        actual = scan_tree(args.directory)
    except (OSError, ValueError) as exc:
        fail(str(exc))
        return 1

    expected_paths = set(expected)
    actual_paths = set(actual)
    if expected_paths != actual_paths:
        for relative in sorted(expected_paths - actual_paths):
            fail(f"release-tree file is missing: {relative}")
        for relative in sorted(actual_paths - expected_paths):
            fail(f"unexpected release-tree file: {relative}")
        return 1

    mismatched = False
    for relative in sorted(expected):
        try:
            actual_digest = sha256(actual[relative])
        except OSError as exc:
            fail(f"cannot hash release-tree file {actual[relative]}: {exc}")
            mismatched = True
            continue
        if actual_digest != expected[relative]:
            fail(f"SHA-256 mismatch for release-tree file: {relative}")
            mismatched = True
    return 1 if mismatched else 0


if __name__ == "__main__":
    raise SystemExit(main())
