#!/usr/bin/env python3
"""Copy only the tracked portal runtime and explicitly public static files."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path, PurePosixPath


RUNTIME_FILES = ("dev-server.mjs", "public-static-contract.json")


def load_contract(portal_root: Path) -> dict:
    contract_path = portal_root / "public-static-contract.json"
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    required = ("routes", "rootFiles", "assetFiles", "numericPngDirectories")
    if not isinstance(contract, dict) or any(key not in contract for key in required):
        raise SystemExit("ERROR: invalid portal public-static contract")
    if not isinstance(contract["routes"], dict):
        raise SystemExit("ERROR: portal public-static routes must be an object")
    for key in ("rootFiles", "assetFiles", "numericPngDirectories"):
        values = contract[key]
        if not isinstance(values, list) or not values or any(
            not isinstance(value, str) or not value for value in values
        ):
            raise SystemExit(f"ERROR: portal public-static {key} must be strings")
        if len(values) != len(set(values)):
            raise SystemExit(f"ERROR: portal public-static {key} contains duplicates")
    return contract


def safe_asset_path(relative: str, contract: dict) -> bool:
    path = PurePosixPath(relative)
    if (
        path.is_absolute()
        or len(path.parts) < 2
        or path.parts[0] != "assets"
        or any(part in ("", ".", "..") or part.startswith(".") for part in path.parts)
    ):
        return False
    if relative in contract["assetFiles"]:
        return True
    return (
        path.parent.as_posix() in contract["numericPngDirectories"]
        and re.fullmatch(r"[0-9]+\.png", path.name) is not None
    )


def tracked_paths(repo_root: Path, pathspec: str) -> list[str]:
    output = subprocess.check_output(
        ["git", "-C", str(repo_root), "ls-files", "-z", "--", pathspec]
    )
    return [value.decode("utf-8") for value in output.split(b"\0") if value]


def copy_regular(source: Path, target: Path) -> None:
    if source.is_symlink() or not source.is_file():
        raise SystemExit(f"ERROR: portal runtime only permits regular files: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def package(repo_root: Path, target: Path) -> None:
    portal_root = (repo_root / "web" / "portal").resolve()
    resolved_target = target.resolve()
    if (
        resolved_target == portal_root
        or resolved_target in portal_root.parents
        or portal_root in resolved_target.parents
    ):
        raise SystemExit(
            f"ERROR: portal runtime target overlaps source tree: {target}"
        )
    contract = load_contract(portal_root)
    root_files = contract["rootFiles"]
    if any(
        PurePosixPath(value).name != value or value.startswith(".")
        for value in root_files
    ):
        raise SystemExit("ERROR: portal public-static rootFiles must be basenames")
    root_set = set(root_files)
    if any(target_name not in root_set for target_name in contract["routes"].values()):
        raise SystemExit("ERROR: portal public-static route target is not a root file")

    for key in ("assetFiles", "numericPngDirectories"):
        for value in contract[key]:
            path = PurePosixPath(value)
            if (
                path.is_absolute()
                or len(path.parts) < 2
                or path.parts[0] != "assets"
                or any(
                    part in ("", ".", "..")
                    or part.startswith(".")
                    or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", part) is None
                    for part in path.parts
                )
                or (key == "numericPngDirectories" and any("." in part for part in path.parts))
            ):
                raise SystemExit(f"ERROR: unsafe portal public-static {key} path: {value}")

    tracked_root = set(tracked_paths(repo_root, "web/portal"))
    selected_root = [*RUNTIME_FILES, *root_files]
    for relative in selected_root:
        repo_relative = f"web/portal/{relative}"
        if repo_relative not in tracked_root:
            raise SystemExit(f"ERROR: portal runtime file is not tracked: {repo_relative}")

    if target.is_symlink():
        raise SystemExit(f"ERROR: portal runtime target must not be a symlink: {target}")
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True)
    for relative in selected_root:
        copy_regular(portal_root / relative, target / relative)

    tracked_assets = tracked_paths(repo_root, "web/portal/assets")
    tracked_asset_relatives = {
        value.removeprefix("web/portal/") for value in tracked_assets
    }
    missing_assets = sorted(set(contract["assetFiles"]) - tracked_asset_relatives)
    if missing_assets:
        raise SystemExit(
            "ERROR: portal public-static asset is not tracked: " + ", ".join(missing_assets)
        )
    for directory in contract["numericPngDirectories"]:
        if not any(
            relative.startswith(f"{directory}/")
            and safe_asset_path(relative, contract)
            for relative in tracked_asset_relatives
        ):
            raise SystemExit(
                f"ERROR: portal public-static numeric PNG directory is empty: {directory}"
            )

    for repo_relative in tracked_assets:
        relative = repo_relative.removeprefix("web/portal/")
        if relative == repo_relative:
            raise SystemExit(f"ERROR: invalid tracked portal asset path: {repo_relative}")
        if not safe_asset_path(relative, contract):
            continue
        copy_regular(repo_root / repo_relative, target / relative)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--target", required=True, type=Path)
    args = parser.parse_args()
    package(args.repo_root.resolve(), args.target)


if __name__ == "__main__":
    main()
