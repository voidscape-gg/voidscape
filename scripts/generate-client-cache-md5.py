#!/usr/bin/env python3
"""Generate or verify the complete legacy client-cache MD5 table."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT_ROOT = ROOT / "Client_Base"
CACHE_ROOT = CLIENT_ROOT / "Cache"
MANIFEST = CACHE_ROOT / "MD5.SUM"
CLIENT_JAR = CLIENT_ROOT / "Open_RSC_Client.jar"

# These files are created or overwritten per installation and must never be
# distributed from a developer/release checkout.
RUNTIME_BASENAMES = {
    "accounts.txt",
    "android_version.txt",
    "android_version_pk.txt",
    "client.properties",
    "config.txt",
    "credentials.txt",
    "discord_inuse.txt",
    "hideip.txt",
    "ip.txt",
    "launchersettings.conf",
    "openpk.apk",
    "openrsc.apk",
    "openrsc.jar",
    "open_rsc_client_dev.jar",
    "port.txt",
    "uid.dat",
    "voidscapelauncher.properties",
}


def md5(path: Path) -> str:
    digest = hashlib.md5()  # noqa: S324 - required by the legacy updater format
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_manifest() -> str:
    if not CLIENT_JAR.is_file():
        raise SystemExit(f"client jar is missing; run scripts/build.sh first: {CLIENT_JAR}")

    rows = [("Open_RSC_Client.jar", CLIENT_JAR)]
    for path in CACHE_ROOT.rglob("*"):
        if not path.is_file() or path == MANIFEST:
            continue
        if path.name.lower() in RUNTIME_BASENAMES:
            continue
        relative = path.relative_to(CACHE_ROOT).as_posix()
        rows.append((relative, path))

    rows.sort(key=lambda row: row[0])
    return "".join(f"{md5(path)} *./{relative}\n" for relative, path in rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if Client_Base/Cache/MD5.SUM is not canonical",
    )
    args = parser.parse_args()

    expected = canonical_manifest()
    if args.check:
        actual = MANIFEST.read_text(encoding="utf-8") if MANIFEST.exists() else ""
        if actual != expected:
            raise SystemExit(
                "Client_Base/Cache/MD5.SUM is stale or incomplete; "
                "run scripts/generate-client-cache-md5.py after scripts/build.sh"
            )
        print(f"client cache MD5 manifest is canonical ({expected.count(chr(10))} files)")
        return 0

    MANIFEST.write_text(expected, encoding="utf-8")
    print(f"wrote {MANIFEST} ({expected.count(chr(10))} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
