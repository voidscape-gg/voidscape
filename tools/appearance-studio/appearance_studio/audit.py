from __future__ import annotations

import hashlib
import json
import re
import zipfile
from pathlib import Path

import yaml

from .manifest import validate_manifest
from .model import Finding, Registry
from .paths import REPO_ROOT


def numeric_members(path: Path) -> set[int]:
    try:
        with zipfile.ZipFile(path) as archive:
            corrupt = archive.testzip()
            if corrupt is not None:
                raise ValueError(f"sprite archive {path} has a CRC failure at {corrupt}")
            return {int(name) for name in archive.namelist() if name.isdigit()}
    except (OSError, zipfile.BadZipFile) as exc:
        raise ValueError(f"could not inventory sprite archive {path}: {exc}") from exc


def _archive_bytes(path: Path, indices: range) -> tuple[dict[int, bytes], list[Finding]]:
    findings: list[Finding] = []
    result: dict[int, bytes] = {}
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            for index in indices:
                if str(index) not in names:
                    findings.append(Finding("error", "archive-frame-missing", f"entry {index} is missing", str(path)))
                else:
                    result[index] = archive.read(str(index))
    except (OSError, zipfile.BadZipFile) as exc:
        findings.append(Finding("error", "archive-read", str(exc), str(path)))
    return result, findings


def _relative(path: Path, repo_root: Path) -> str:
    try:
        return str(path.resolve().relative_to(repo_root.resolve()))
    except ValueError:
        return str(path)


def audit_registry(
    registry: Registry,
    *,
    repo_root: Path = REPO_ROOT,
    client_archive: Path | None = None,
    server_archive: Path | None = None,
    md5_file: Path | None = None,
    entity_handler: Path | None = None,
    avatar_generator: Path | None = None,
) -> tuple[list[Finding], dict[str, object]]:
    client_archive = client_archive or repo_root / "Client_Base/Cache/video/Authentic_Sprites.orsc"
    server_archive = server_archive or repo_root / "server/conf/server/data/Authentic_Sprites.orsc"
    md5_file = md5_file or repo_root / "Client_Base/Cache/MD5.SUM"
    entity_handler = entity_handler or repo_root / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
    avatar_generator = avatar_generator or repo_root / "server/src/com/openrsc/server/avatargenerator/AvatarGenerator.java"
    findings: list[Finding] = []
    for entry in registry.entries:
        findings.extend(validate_manifest(entry, repo_root=repo_root))

    try:
        client_members = numeric_members(client_archive)
        server_members = numeric_members(server_archive)
    except ValueError as exc:
        return [Finding("error", "archive-inventory", str(exc))], {}

    owned_loaded: set[int] = set()
    for entry in registry.entries + registry.external_reservations:
        owned_loaded.update(range(entry.sprite_base, entry.sprite_base + entry.frame_count))
    archive_union = client_members | server_members
    unowned = archive_union - owned_loaded

    try:
        from voidscim.pack_wielded_cmd import parse_animations, simulate
        animations, _ = parse_animations(entity_handler.read_text(), allow_bearded_ladies=False)
        simulate(animations)
        visible = [animation for animation in animations if not animation.gated]
        runtime_entries = tuple(
            entry
            for entry in registry.entries + registry.external_reservations
            if entry.state in {"active", "adopted"}
        )
        for entry in runtime_entries:
            runtime_index = entry.appearance_id - 1
            if runtime_index >= len(visible):
                findings.append(Finding("error", "runtime-layout", f"{entry.key!r} appearance ID {entry.appearance_id} is outside {len(visible)} active AnimationDefs"))
                continue
            animation = visible[runtime_index]
            actual_frame_count = 15 + (3 if animation.has_a else 0) + (9 if animation.has_f else 0)
            actual = (animation.name, animation.number, actual_frame_count)
            expected = (entry.animation_name, entry.sprite_base, entry.frame_count)
            if actual != expected:
                findings.append(Finding("error", "runtime-layout", f"{entry.key!r} source-order layout {actual} != registry {expected}", _relative(entity_handler, repo_root)))
    except (OSError, ImportError, RuntimeError, ValueError) as exc:
        findings.append(Finding("error", "runtime-layout", str(exc), _relative(entity_handler, repo_root)))

    for entry in registry.entries:
        if entry.state != "adopted":
            continue
        indices = range(entry.sprite_base, entry.sprite_base + entry.frame_count)
        client_bytes, issues = _archive_bytes(client_archive, indices)
        findings.extend(issues)
        server_bytes, issues = _archive_bytes(server_archive, indices)
        findings.extend(issues)
        for index in indices:
            if index in client_bytes and index in server_bytes and client_bytes[index] != server_bytes[index]:
                findings.append(Finding("error", "archive-parity", f"client/server entry {index} differs"))

        if entry.manifest is not None and entry.key == "cowboy_hat":
            try:
                payload = yaml.safe_load(entry.manifest.read_text())
                frames_dir = repo_root / payload["legacy"]["frames_dir"]
                from voidscim.sprite_io import encode
                for offset, index in enumerate(indices):
                    png = frames_dir / f"frame_{offset:02d}.png"
                    sidecar = frames_dir / f"frame_{offset:02d}.png.json"
                    if not png.exists() or not sidecar.exists():
                        findings.append(Finding("error", "authoring-frame-missing", f"missing Cowboy frame or sidecar {offset:02d}", _relative(frames_dir, repo_root)))
                        continue
                    from PIL import Image
                    encoded = encode(Image.open(png).convert("RGBA"), json.loads(sidecar.read_text()))
                    if client_bytes.get(index) != encoded or server_bytes.get(index) != encoded:
                        findings.append(Finding("error", "authoring-archive-parity", f"Cowboy authoring frame {offset:02d} does not encode byte-identically to both entry {index}"))
            except (OSError, KeyError, TypeError, ValueError, ImportError, json.JSONDecodeError) as exc:
                findings.append(Finding("error", "cowboy-authoring-audit", str(exc), _relative(entry.manifest, repo_root)))

            try:
                from voidscim.validate_wielded_cmd import resolve_wielded_layout
                layout = resolve_wielded_layout(
                    entry.animation_name,
                    entry.item_id if entry.item_id is not None else -1,
                    entity_handler_path=entity_handler,
                )
                expected = (entry.appearance_id, entry.sprite_base, entry.frame_count)
                actual = (layout.appearance_id, layout.archive_base, layout.frame_count)
                if actual != expected:
                    findings.append(Finding("error", "runtime-layout", f"Cowboy layout {actual} != registry {expected}"))
            except Exception as exc:
                findings.append(Finding("error", "runtime-layout", str(exc), _relative(entity_handler, repo_root)))

            try:
                expected_md5 = hashlib.md5(client_archive.read_bytes()).hexdigest()
                rows = [line for line in md5_file.read_text().splitlines() if line.endswith("*./video/Authentic_Sprites.orsc")]
                if len(rows) != 1 or not rows[0].startswith(expected_md5 + " "):
                    findings.append(Finding("error", "archive-md5", f"MD5.SUM does not contain the current client archive digest {expected_md5}", _relative(md5_file, repo_root)))
            except OSError as exc:
                findings.append(Finding("error", "archive-md5", str(exc), _relative(md5_file, repo_root)))

            try:
                avatar_text = avatar_generator.read_text()
                generated_avatar_registry = repo_root / "server/src/com/openrsc/server/appearance/GeneratedAppearanceRegistry.java"
                generated_text = generated_avatar_registry.read_text() if generated_avatar_registry.exists() else ""
                has_generated_resolution = "GeneratedAppearanceRegistry.findAuthentic" in avatar_text
                has_cowboy_mapping = re.search(r"cowboyhat|Cowboy hat", generated_text, re.IGNORECASE) is not None
                if not has_generated_resolution or not has_cowboy_mapping:
                    findings.append(Finding("warning", "cowboy-avatar-mapping-missing", "AvatarGenerator has no Cowboy mapping; Slice 2 owns this compatibility fix", _relative(avatar_generator, repo_root)))
            except OSError as exc:
                findings.append(Finding("error", "avatar-read", str(exc), _relative(avatar_generator, repo_root)))

    inventory = {
        "client": {"count": len(client_members), "max": max(client_members, default=-1)},
        "server": {"count": len(server_members), "max": max(server_members, default=-1)},
        "unowned_numeric_entries": len(unowned),
    }
    try:
        namespace_payload = yaml.safe_load((repo_root / "content/appearance/namespaces.yaml").read_text())
        managed_indices = set(range(registry.managed_namespace["base"], registry.managed_namespace["end"] + 1))
        managed_archive_hits = sorted(managed_indices & archive_union)
        if managed_archive_hits:
            findings.append(Finding("error", "managed-namespace-occupied", f"managed namespace contains existing archive entries: {managed_archive_hits[:10]}"))
        fixed_hits = [
            consumer["key"]
            for consumer in namespace_payload.get("fixed_consumers", [])
            if registry.managed_namespace["base"] <= consumer["end"]
            and registry.managed_namespace["end"] >= consumer["start"]
        ]
        if fixed_hits:
            findings.append(Finding("error", "managed-namespace-consumer-collision", f"managed namespace overlaps fixed consumers: {sorted(fixed_hits)}"))
        capacities = namespace_payload.get("runtime_capacities", {})
        too_small = sorted(name for name, size in capacities.items() if registry.managed_namespace["capacity_required"] > size)
        if too_small:
            findings.append(Finding("error", "managed-namespace-capacity", f"managed namespace exceeds runtime capacities: {too_small}"))
    except (OSError, TypeError, KeyError, yaml.YAMLError) as exc:
        findings.append(Finding("error", "namespace-inventory", str(exc)))
    return findings, {"inventory": inventory, "occupied": archive_union, "unowned": unowned}
