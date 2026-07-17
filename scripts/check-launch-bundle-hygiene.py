#!/usr/bin/env python3
"""Reject local-machine residue from a generated launch bundle."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path


PERSONAL_POSIX_PATH = re.compile(
    rb"(?<![A-Za-z0-9])/(?:Users|home)/[^/\\\x00\r\n\t ]+"
    rb"(?=[/\\\x00\r\n\t ]|$)",
)
PERSONAL_WINDOWS_PATH = re.compile(
    rb"(?<![A-Za-z0-9])[A-Z]:[\\/]+Users[\\/]+[^/\\\x00\r\n\t ]+"
    rb"(?=[/\\\x00\r\n\t ]|$)",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--forbidden-root", action="append", default=[])
    args = parser.parse_args()

    bundle = args.bundle
    if not bundle.is_dir() or bundle.is_symlink():
        print(f"ERROR: launch bundle is missing or unsafe: {bundle}", file=sys.stderr)
        return 1

    forbidden_roots = [
        Path(value).resolve().as_posix().encode("utf-8")
        for value in args.forbidden_root
    ]
    failures: list[str] = []
    for entry in sorted(bundle.rglob("*")):
        relative = entry.relative_to(bundle).as_posix()
        if entry.is_symlink():
            failures.append(f"symlink is not allowed: {relative}")
            continue
        if entry.is_dir():
            continue
        if not entry.is_file():
            failures.append(f"special file is not allowed: {relative}")
            continue
        if entry.name == ".gitsave":
            failures.append(f"repository placeholder is not allowed: {relative}")
        try:
            payload = entry.read_bytes()
        except OSError as exc:
            failures.append(f"cannot inspect {relative}: {exc}")
            continue
        if contains_forbidden_root(payload, forbidden_roots):
            failures.append(f"local checkout path is embedded in: {relative}")
        elif not zipfile.is_zipfile(entry) and contains_personal_path(payload):
            failures.append(f"personal absolute path is embedded in: {relative}")
        if zipfile.is_zipfile(entry):
            inspect_archive(entry, relative, forbidden_roots, failures)

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1

    print("Launch bundle hygiene passed.")
    return 0


def contains_forbidden_root(payload: bytes, forbidden_roots: list[bytes]) -> bool:
    return any(root and root in payload for root in forbidden_roots)


def contains_personal_path(payload: bytes) -> bool:
    return bool(
        PERSONAL_POSIX_PATH.search(payload)
        or PERSONAL_WINDOWS_PATH.search(payload)
    )


def inspect_archive(
    path: Path,
    relative: str,
    forbidden_roots: list[bytes],
    failures: list[str],
) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            for member in archive.infolist():
                if member.is_dir():
                    continue
                label = f"{relative}!/{member.filename}"
                if contains_forbidden_root(
                    member.filename.encode("utf-8", "surrogateescape"), forbidden_roots
                ):
                    failures.append(f"local checkout path is embedded in: {label}")
                    continue
                payload = archive.read(member)
                if contains_forbidden_root(payload, forbidden_roots):
                    failures.append(f"local checkout path is embedded in: {label}")
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        failures.append(f"cannot inspect archive {relative}: {exc}")


if __name__ == "__main__":
    raise SystemExit(main())
