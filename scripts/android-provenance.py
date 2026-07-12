#!/usr/bin/env python3
"""Compute and embed reproducible provenance for Voidscape Android artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
import subprocess
import sys
from pathlib import Path


RELEVANT_PATHS = (
    "Android_Client",
    "Client_Base/src",
    "Client_Base/Cache",
    "scripts/build-android.sh",
    "scripts/android-provenance.py",
)


def git(root: Path, *args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), *args],
            text=True,
            stderr=subprocess.PIPE,
        ).strip()
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.strip() or str(exc)
        raise SystemExit(f"ERROR: git provenance query failed: {detail}") from exc


def relevant_files(root: Path) -> list[str]:
    output = git(
        root,
        "ls-files",
        "-co",
        "--exclude-standard",
        "--",
        *RELEVANT_PATHS,
    )
    return sorted(set(output.splitlines())) if output else []


def update_digest_record(
    digest: "hashlib._Hash", relative: str, mode: str, marker: bytes, payload: bytes
) -> None:
    encoded_path = relative.encode("utf-8")
    digest.update(len(encoded_path).to_bytes(4, "big"))
    digest.update(encoded_path)
    digest.update(mode.encode("ascii") + b"\0")
    digest.update(marker + b"\0")
    digest.update(len(payload).to_bytes(8, "big"))
    digest.update(payload)


def working_digest(root: Path) -> str:
    digest = hashlib.sha256()
    for relative in relevant_files(root):
        path = root / relative
        if not os.path.lexists(path):
            update_digest_record(digest, relative, "missing", b"MISSING", b"")
            continue
        if path.is_symlink():
            mode = "120000"
            payload = os.readlink(path).encode("utf-8")
            marker = b"SYMLINK"
        else:
            mode = "100755" if stat.S_IMODE(path.stat().st_mode) & 0o111 else "100644"
            payload = path.read_bytes()
            marker = b"FILE"
        update_digest_record(digest, relative, mode, marker, payload)
    return digest.hexdigest()


def commit_entries(root: Path, commit: str) -> list[tuple[str, str, str, str]]:
    try:
        output = subprocess.check_output(
            [
                "git",
                "-C",
                str(root),
                "ls-tree",
                "-r",
                "-z",
                "--full-tree",
                commit,
                "--",
                *RELEVANT_PATHS,
            ],
            stderr=subprocess.PIPE,
        )
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.decode("utf-8", errors="replace").strip() or str(exc)
        raise SystemExit(f"ERROR: could not read claimed commit tree: {detail}") from exc

    entries: list[tuple[str, str, str, str]] = []
    for record in output.split(b"\0"):
        if not record:
            continue
        metadata, raw_path = record.split(b"\t", 1)
        mode, object_type, object_id = metadata.decode("ascii").split()
        relative = raw_path.decode("utf-8")
        entries.append((relative, mode, object_type, object_id))
    return sorted(entries)


def read_blobs(root: Path, object_ids: list[str]) -> dict[str, bytes]:
    unique_ids = sorted(set(object_ids))
    if not unique_ids:
        return {}
    process = subprocess.Popen(
        ["git", "-C", str(root), "cat-file", "--batch"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert process.stdin is not None
    assert process.stdout is not None
    for object_id in unique_ids:
        process.stdin.write(object_id.encode("ascii") + b"\n")
    process.stdin.close()

    blobs: dict[str, bytes] = {}
    for expected_id in unique_ids:
        header = process.stdout.readline().decode("ascii", errors="replace").strip()
        parts = header.split()
        if len(parts) != 3 or parts[1] != "blob" or not parts[2].isdigit():
            process.kill()
            raise SystemExit(f"ERROR: could not read commit blob {expected_id}: {header}")
        size = int(parts[2])
        payload = process.stdout.read(size)
        if len(payload) != size or process.stdout.read(1) != b"\n":
            process.kill()
            raise SystemExit(f"ERROR: truncated commit blob {expected_id}")
        blobs[expected_id] = payload
    stderr = process.stderr.read().decode("utf-8", errors="replace").strip() if process.stderr else ""
    if process.wait() != 0:
        raise SystemExit(f"ERROR: git cat-file failed: {stderr}")
    return blobs


def commit_digest(root: Path, commit: str) -> str:
    entries = commit_entries(root, commit)
    blobs = read_blobs(
        root,
        [object_id for _, _, object_type, object_id in entries if object_type == "blob"],
    )
    digest = hashlib.sha256()
    for relative, mode, object_type, object_id in entries:
        if object_type == "blob":
            marker = b"SYMLINK" if mode == "120000" else b"FILE"
            payload = blobs[object_id]
        else:
            marker = b"GITLINK"
            payload = object_id.encode("ascii")
        update_digest_record(digest, relative, mode, marker, payload)
    return digest.hexdigest()


def status_reports_dirty(root: Path) -> bool:
    return bool(
        git(
            root,
            "status",
            "--porcelain",
            "--untracked-files=all",
            "--",
            *RELEVANT_PATHS,
        )
    )


def state(root: Path, dirty_release_override: bool = False) -> dict[str, object]:
    commit = git(root, "rev-parse", "HEAD").lower()
    if len(commit) != 40 or any(char not in "0123456789abcdef" for char in commit):
        raise SystemExit(f"ERROR: expected a full Git commit id, got {commit!r}")
    actual_digest = working_digest(root)
    claimed_commit_digest = commit_digest(root, commit)
    return {
        "metadataSchemaVersion": 3,
        "gitCommit": commit,
        "relevantInputDigest": actual_digest,
        "commitInputDigest": claimed_commit_digest,
        "relevantInputDirty": (
            status_reports_dirty(root) or actual_digest != claimed_commit_digest
        ),
        "dirtyReleaseOverride": dirty_release_override,
    }


def write_json_atomic(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    try:
        temporary.write_text(
            json.dumps(value, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    state_parser = subparsers.add_parser("state")
    state_parser.add_argument("--repo-root", required=True, type=Path)
    state_parser.add_argument("--format", choices=("json", "tsv"), default="json")

    write_parser = subparsers.add_parser("write")
    write_parser.add_argument("--repo-root", required=True, type=Path)
    write_parser.add_argument("--output", required=True, type=Path)
    write_parser.add_argument(
        "--dirty-release-override",
        choices=("true", "false"),
        default="false",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repo_root.resolve()
    if args.command == "state":
        value = state(root)
        if args.format == "tsv":
            print(
                value["gitCommit"],
                value["relevantInputDigest"],
                str(value["relevantInputDirty"]).lower(),
                value["commitInputDigest"],
                sep="\t",
            )
        else:
            print(json.dumps(value, sort_keys=True))
        return 0

    value = state(root, args.dirty_release_override == "true")
    write_json_atomic(args.output, value)
    print(json.dumps(value, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
