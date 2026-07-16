from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import yaml

from .java_bridge import rendered_sources
from .look_presets import rendered_preset_sources
from .model import AppearanceEntry, Registry
from .paths import REPO_ROOT


CANDIDATE_SCHEMA = "voidscape-appearance-candidate/v1"
MD5_ARCHIVE_SUFFIX = "*./video/Authentic_Sprites.orsc"

CLIENT_ARCHIVE = Path("Client_Base/Cache/video/Authentic_Sprites.orsc")
SERVER_ARCHIVE = Path("server/conf/server/data/Authentic_Sprites.orsc")
MD5_SUM = Path("Client_Base/Cache/MD5.SUM")
CLIENT_ITEMS = Path("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")
SERVER_ITEMS = Path("server/conf/server/defs/ItemDefsCustom.json")
ITEM_IDS = Path("server/src/com/openrsc/server/constants/ItemId.java")
APPEARANCE_IDS = Path("server/src/com/openrsc/server/constants/AppearanceId.java")
CLIENT_REGISTRY = Path("Client_Base/src/com/openrsc/client/entityhandling/GeneratedAppearanceRegistry.java")
SERVER_REGISTRY = Path("server/src/com/openrsc/server/appearance/GeneratedAppearanceRegistry.java")
CLIENT_LOOK_PRESETS = Path("Client_Base/src/com/openrsc/client/entityhandling/GeneratedLookPresets.java")
SERVER_LOOK_PRESETS = Path("server/src/com/openrsc/server/appearance/GeneratedLookPresets.java")


class CandidateError(ValueError):
    pass


@dataclass(frozen=True)
class CandidateFile:
    path: Path
    data: bytes
    role: str
    mode: str


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _relative(path: Path, root: Path) -> Path:
    try:
        return path.resolve().relative_to(root.resolve())
    except ValueError as exc:
        raise CandidateError(f"candidate input escapes repository root: {path}") from exc


def _manifest(entry: AppearanceEntry) -> dict:
    if entry.manifest is None:
        raise CandidateError(f"{entry.key!r} has no appearance manifest")
    try:
        payload = yaml.safe_load(entry.manifest.read_text())
    except (OSError, yaml.YAMLError) as exc:
        raise CandidateError(f"could not read {entry.key!r} manifest: {exc}") from exc
    if not isinstance(payload, dict):
        raise CandidateError(f"{entry.key!r} appearance manifest must be a mapping")
    if payload.get("source_mode") != "legacy-import":
        raise CandidateError(
            f"Slice 4 no-op candidate supports only legacy-import adoption, got {payload.get('source_mode')!r}"
        )
    return payload


def _verify_archive_adoption(entry: AppearanceEntry, payload: dict, repo_root: Path) -> None:
    legacy = payload.get("legacy")
    if not isinstance(legacy, dict):
        raise CandidateError(f"{entry.key!r} manifest.legacy must be a mapping")
    frames_raw = legacy.get("frames_dir")
    if not isinstance(frames_raw, str):
        raise CandidateError(f"{entry.key!r} manifest.legacy.frames_dir must be a path")
    frames_dir = (repo_root / frames_raw).resolve()
    _relative(frames_dir, repo_root)
    try:
        from PIL import Image
        from voidscim.sprite_io import encode

        with zipfile.ZipFile(repo_root / CLIENT_ARCHIVE) as client, zipfile.ZipFile(repo_root / SERVER_ARCHIVE) as server:
            for offset in range(entry.frame_count):
                index = entry.sprite_base + offset
                png = frames_dir / f"frame_{offset:02d}.png"
                sidecar = frames_dir / f"frame_{offset:02d}.png.json"
                encoded = encode(Image.open(png).convert("RGBA"), json.loads(sidecar.read_text()))
                client_bytes = client.read(str(index))
                server_bytes = server.read(str(index))
                if encoded != client_bytes or encoded != server_bytes:
                    raise CandidateError(
                        f"{entry.key!r} frame {offset:02d} is not byte-identical to both archive entry {index}"
                    )
    except CandidateError:
        raise
    except (OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        raise CandidateError(f"could not verify {entry.key!r} legacy archive adoption: {exc}") from exc


def _verify_item_contract(entry: AppearanceEntry, repo_root: Path) -> None:
    if entry.item_id is None:
        return
    try:
        item_payload = json.loads((repo_root / SERVER_ITEMS).read_text())
        matches = [item for item in item_payload["items"] if item.get("id") == entry.item_id]
    except (OSError, KeyError, json.JSONDecodeError, TypeError) as exc:
        raise CandidateError(f"could not read server item definitions: {exc}") from exc
    if len(matches) != 1:
        raise CandidateError(f"item {entry.item_id} must occur exactly once in ItemDefsCustom.json")
    item = matches[0]
    expected = {
        "appearanceID": entry.appearance_id,
        "isWearable": 1,
        "wearSlot": entry.paperdoll_slot,
    }
    for field, value in expected.items():
        if item.get(field) != value:
            raise CandidateError(
                f"item {entry.item_id} {field} {item.get(field)!r} does not match registry {value!r}"
            )

    constant = re.sub(r"[^A-Za-z0-9]", "_", entry.key).upper()
    checks = (
        (ITEM_IDS, rf"\b{constant}\s*\(\s*{entry.item_id}\s*\)"),
        (APPEARANCE_IDS, rf"\b{constant}\s*\(\s*{entry.appearance_id}\s*,"),
    )
    for relative, pattern in checks:
        try:
            text = (repo_root / relative).read_text()
        except OSError as exc:
            raise CandidateError(f"could not read {relative}: {exc}") from exc
        if re.search(pattern, text) is None:
            raise CandidateError(f"{relative} does not map {constant} to the registry value")


def _replace_unique(text: str, pattern: str, replacement: str, *, label: str, flags: int = 0) -> str:
    rendered, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise CandidateError(f"expected exactly one generated region for {label}")
    return rendered


def _java_bool(value: bool) -> str:
    return "true" if value else "false"


def _item_contract(entry: AppearanceEntry, manifest: dict) -> dict:
    integration = manifest.get("integration")
    item = integration.get("item") if isinstance(integration, dict) else None
    if not isinstance(item, dict):
        raise CandidateError(f"{entry.key!r} manifest.integration.item must be a mapping")
    if item.get("id") != entry.item_id:
        raise CandidateError(f"{entry.key!r} integration.item.id does not match registry item_id")
    if item.get("wear_slot") != entry.paperdoll_slot:
        raise CandidateError(f"{entry.key!r} integration.item.wear_slot does not match paperdoll_slot")
    expected_icon = 2150 + item.get("inventory_sprite_id", -2151)
    if item.get("inventory_archive_index") != expected_icon:
        raise CandidateError(
            f"{entry.key!r} inventory_archive_index must equal 2150 + inventory_sprite_id ({expected_icon})"
        )
    if item.get("inventory_sprite_location") != f"items:{item.get('inventory_sprite_id')}":
        raise CandidateError(f"{entry.key!r} inventory_sprite_location does not match inventory_sprite_id")
    icon_file = item.get("inventory_icon_file")
    if not isinstance(icon_file, str) or not icon_file:
        raise CandidateError(f"{entry.key!r} inventory_icon_file must be a repository-relative path")
    return item


def _render_entity_handler(source: bytes, entries: Iterable[AppearanceEntry], manifests: dict[str, dict]) -> bytes:
    text = source.decode("utf-8")
    for entry in entries:
        suffix_pattern = rf'(?m)^(\s*)animations\.add\(new AnimationDef\("{re.escape(entry.animation_name)}"[^\n]*?(\s*//[^\n]*)?$'
        match = re.search(suffix_pattern, text)
        if match is None:
            raise CandidateError(f"EntityHandler.java has no animation region for {entry.animation_name!r}")
        suffix = match.group(2) or ""
        replacement = (
            f'{match.group(1)}animations.add(new AnimationDef("{entry.animation_name}", "{entry.category}", '
            f'{entry.char_colour}, {entry.blue_mask}, {_java_bool(entry.has_a)}, {_java_bool(entry.has_f)}, '
            f'{entry.gender_model}));{suffix}'
        )
        text = text[:match.start()] + replacement + text[match.end():]

        if entry.item_id is None:
            continue
        item_pattern = rf'(?m)^(\s*)items\.add\(new ItemDef\(([^\n]*),\s*{entry.item_id}\)\);$'
        item_match = re.search(item_pattern, text)
        if item_match is None:
            raise CandidateError(f"EntityHandler.java has no item region for {entry.item_id}")
        manifest_name = manifests[entry.key].get("name")
        if not isinstance(manifest_name, str) or not manifest_name:
            raise CandidateError(f"{entry.key!r} manifest.name must be a non-empty string")
        item = _item_contract(entry, manifests[entry.key])
        arguments_list = [
            json.dumps(manifest_name), json.dumps(item["description"]), json.dumps(item["command"]),
            str(item["base_price"]), str(item["inventory_sprite_id"]),
            json.dumps(item["inventory_sprite_location"]), _java_bool(item["stackable"]),
            _java_bool(item["wearable"]), str(item["wearable_id"]), str(item["picture_mask"]),
        ]
        if item["blue_mask"]:
            arguments_list.append(str(item["blue_mask"]))
        arguments_list.extend(
            (_java_bool(item["members"]), _java_bool(item["untradeable"]), _java_bool(item["noteable"]))
        )
        arguments = ", ".join(arguments_list)
        item_replacement = f"{item_match.group(1)}items.add(new ItemDef({arguments}, {entry.item_id}));"
        text = text[:item_match.start()] + item_replacement + text[item_match.end():]
    return text.encode("utf-8")


def _render_server_items(source: bytes, entries: Iterable[AppearanceEntry], manifests: dict[str, dict]) -> bytes:
    text = source.decode("utf-8")
    for entry in entries:
        if entry.item_id is None:
            continue
        pattern = rf'(?ms)(\{{\s*"id"\s*:\s*{entry.item_id}\s*,.*?\n\s*\}})(?=\s*[,\]])'
        match = re.search(pattern, text)
        if match is None:
            raise CandidateError(f"ItemDefsCustom.json has no item region for {entry.item_id}")
        item = _item_contract(entry, manifests[entry.key])
        bonuses = item["bonuses"]
        fields = (
            ("id", entry.item_id), ("name", manifests[entry.key]["name"]),
            ("description", item["description"]), ("command", item["command"]),
            ("isFemaleOnly", int(item["female_only"])), ("isMembersOnly", int(item["members"])),
            ("isStackable", int(item["stackable"])), ("isUntradable", int(item["untradeable"])),
            ("isWearable", int(item["wearable"])), ("appearanceID", entry.appearance_id),
            ("wearableID", item["wearable_id"]), ("wearSlot", entry.paperdoll_slot),
            ("requiredLevel", item["required_level"]), ("requiredSkillID", item["required_skill_id"]),
            ("armourBonus", bonuses["armour"]), ("weaponAimBonus", bonuses["weapon_aim"]),
            ("weaponPowerBonus", bonuses["weapon_power"]), ("magicBonus", bonuses["magic"]),
            ("prayerBonus", bonuses["prayer"]), ("basePrice", item["base_price"]),
            ("isNoteable", int(item["noteable"])),
        )
        rows = ["{"]
        for index, (field, value) in enumerate(fields):
            comma = "," if index < len(fields) - 1 else ""
            rows.append(f'            "{field}": {json.dumps(value)}{comma}')
        rows.append("        }")
        block = "\n".join(rows)
        text = text[:match.start()] + block + text[match.end():]
    # Parsing the rendered document is part of generation, not deferred validation.
    json.loads(text)
    return text.encode("utf-8")


def _constant(entry: AppearanceEntry) -> str:
    return re.sub(r"[^A-Za-z0-9]", "_", entry.key).upper()


def _render_item_ids(source: bytes, entries: Iterable[AppearanceEntry]) -> bytes:
    text = source.decode("utf-8")
    for entry in entries:
        if entry.item_id is None:
            continue
        name = _constant(entry)
        pattern = rf'(?m)^(\s*){name}\s*\(\s*\d+\s*\)([;,])$'
        match = re.search(pattern, text)
        if match is None:
            raise CandidateError(f"ItemId.java has no generated region for {name}")
        replacement = f"{match.group(1)}{name}({entry.item_id}){match.group(2)}"
        text = text[:match.start()] + replacement + text[match.end():]
    return text.encode("utf-8")


def _render_appearance_ids(source: bytes, entries: Iterable[AppearanceEntry]) -> bytes:
    kind_category = {"hat": "HAT", "hair": "HAT", "facial-hair": "HAT", "clothing": "BODY", "equipment": "WEAPON"}
    text = source.decode("utf-8")
    for entry in entries:
        name = _constant(entry)
        category = kind_category.get(entry.kind)
        if category is None:
            raise CandidateError(f"no AppearanceId category renderer for kind {entry.kind!r}")
        pattern = rf'(?m)^(\s*){name}\s*\(\s*\d+\s*,\s*[A-Z_]+\s*\)(,)(\s*//[^\n]*)?$'
        match = re.search(pattern, text)
        if match is None:
            raise CandidateError(f"AppearanceId.java has no generated region for {name}")
        replacement = f"{match.group(1)}{name}({entry.appearance_id}, {category}){match.group(2)}{match.group(3) or ''}"
        text = text[:match.start()] + replacement + text[match.end():]
    return text.encode("utf-8")


def _render_content_metadata(source: bytes) -> bytes:
    text = source.decode("utf-8")
    additions = (
        ("server_defs", str(SERVER_REGISTRY)),
        ("server_defs", str(SERVER_LOOK_PRESETS)),
        ("client_defs", str(CLIENT_REGISTRY)),
        ("client_defs", str(CLIENT_LOOK_PRESETS)),
        ("cache_files", str(MD5_SUM)),
    )
    for section, path in additions:
        quoted = f'    - "{path}"'
        if quoted in text:
            continue
        pattern = rf'(?m)^(  {section}:\n(?:    - [^\n]+\n)*)'
        match = re.search(pattern, text)
        if match is None:
            raise CandidateError(f"content.yaml has no integration.{section} list")
        replacement = match.group(1) + quoted + "\n"
        text = text[:match.start()] + replacement + text[match.end():]
    payload = yaml.safe_load(text)
    if not isinstance(payload, dict) or not isinstance(payload.get("integration"), dict):
        raise CandidateError("rendered content metadata is invalid")
    return text.encode("utf-8")


def _rewrite_md5(source: bytes, archive: bytes) -> bytes:
    try:
        text = source.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise CandidateError("MD5.SUM must be UTF-8 text") from exc
    lines = text.splitlines(keepends=True)
    matches = [index for index, line in enumerate(lines) if line.rstrip("\r\n").endswith(MD5_ARCHIVE_SUFFIX)]
    if len(matches) != 1:
        raise CandidateError("MD5.SUM must contain exactly one Authentic_Sprites.orsc row")
    newline = "\r\n" if lines[matches[0]].endswith("\r\n") else "\n"
    lines[matches[0]] = f"{hashlib.md5(archive).hexdigest()} {MD5_ARCHIVE_SUFFIX}{newline}"
    return "".join(lines).encode("utf-8")


def _copy_targets(entries: Iterable[AppearanceEntry]) -> tuple[Path, ...]:
    targets: set[Path] = {CLIENT_ARCHIVE, SERVER_ARCHIVE, MD5_SUM}
    if any(entry.item_id is not None for entry in entries):
        targets.update({CLIENT_ITEMS, SERVER_ITEMS, ITEM_IDS, APPEARANCE_IDS})
    return tuple(sorted(targets, key=str))


def _archive_contracts(entries: Iterable[AppearanceEntry], manifests: dict[str, dict], repo_root: Path) -> list[dict]:
    try:
        from PIL import Image
        from voidscim.sprite_io import encode

        with zipfile.ZipFile(repo_root / CLIENT_ARCHIVE) as client, zipfile.ZipFile(repo_root / SERVER_ARCHIVE) as server:
            client_names = set(client.namelist())
            server_names = set(server.namelist())
            contracts = []
            for entry in entries:
                worn = list(range(entry.sprite_base, entry.sprite_base + entry.frame_count))
                parity = all(
                    str(index) in client_names
                    and str(index) in server_names
                    and client.read(str(index)) == server.read(str(index))
                    for index in worn
                )
                if not parity:
                    raise CandidateError(f"{entry.key!r} worn range is missing or differs between archives")
                contract = {
                    "key": entry.key,
                    "worn": {"archives": [str(CLIENT_ARCHIVE), str(SERVER_ARCHIVE)], "end": worn[-1],
                              "parity": True, "start": worn[0]},
                }
                if entry.item_id is not None:
                    item = _item_contract(entry, manifests[entry.key])
                    icon_relative = Path(item["inventory_icon_file"])
                    if icon_relative.is_absolute() or ".." in icon_relative.parts:
                        raise CandidateError(f"{entry.key!r} inventory_icon_file escapes repository root")
                    icon_path = (repo_root / icon_relative).resolve()
                    _relative(icon_path, repo_root)
                    sidecar_path = Path(str(icon_path) + ".json")
                    encoded = encode(Image.open(icon_path).convert("RGBA"), json.loads(sidecar_path.read_text()))
                    index = item["inventory_archive_index"]
                    if str(index) not in client_names or client.read(str(index)) != encoded:
                        raise CandidateError(f"{entry.key!r} inventory icon does not encode to client entry {index}")
                    if str(index) in server_names:
                        raise CandidateError(f"{entry.key!r} client-only inventory icon unexpectedly exists in server archive")
                    contract["inventoryIcon"] = {
                        "archive": str(CLIENT_ARCHIVE), "archiveIndex": index, "clientOnly": True,
                        "file": str(icon_relative), "serverEntryPresent": False,
                        "spriteId": item["inventory_sprite_id"],
                    }
                contracts.append(contract)
            return contracts
    except CandidateError:
        raise
    except (OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        raise CandidateError(f"could not build archive contracts: {exc}") from exc


def build_candidate_files(registry: Registry, *, repo_root: Path = REPO_ROOT) -> list[CandidateFile]:
    entries = tuple(
        sorted(
            (entry for entry in registry.entries if entry.state in {"active", "adopted"}),
            key=lambda value: (value.appearance_id, value.key),
        )
    )
    if not entries:
        raise CandidateError("registry has no active or adopted appearance entries")

    metadata: set[Path] = set()
    manifests: dict[str, dict] = {}
    for entry in entries:
        payload = _manifest(entry)
        manifests[entry.key] = payload
        _item_contract(entry, payload) if entry.item_id is not None else None
        _verify_archive_adoption(entry, payload, repo_root)
        _verify_item_contract(entry, repo_root)
        assert entry.manifest is not None
        manifest_relative = _relative(entry.manifest, repo_root)
        metadata.add(manifest_relative)
        content_manifest = manifest_relative.parent / "content.yaml"
        if (repo_root / content_manifest).exists():
            metadata.add(content_manifest)

    files: dict[Path, CandidateFile] = {}
    for relative in _copy_targets(entries):
        try:
            data = (repo_root / relative).read_bytes()
        except OSError as exc:
            raise CandidateError(f"could not read candidate input {relative}: {exc}") from exc
        role = "sprite-archive" if relative in {CLIENT_ARCHIVE, SERVER_ARCHIVE} else "integration-copy"
        files[relative] = CandidateFile(relative, data, role, "copy")

    client_archive = files[CLIENT_ARCHIVE].data
    md5_source = files[MD5_SUM].data
    files[MD5_SUM] = CandidateFile(MD5_SUM, _rewrite_md5(md5_source, client_archive), "client-md5", "generated")

    for relative, source in rendered_sources(registry).items():
        files[relative] = CandidateFile(relative, source.encode("utf-8"), "appearance-mapping", "generated")
    for relative, source in rendered_preset_sources(registry).items():
        files[relative] = CandidateFile(relative, source.encode("utf-8"), "look-presets", "generated")

    for relative in metadata:
        source = (repo_root / relative).read_bytes()
        if relative.name == "content.yaml":
            files[relative] = CandidateFile(relative, _render_content_metadata(source), "content-metadata", "generated")
        else:
            files[relative] = CandidateFile(relative, source, "content-metadata", "copy")

    files[CLIENT_ITEMS] = CandidateFile(
        CLIENT_ITEMS, _render_entity_handler(files[CLIENT_ITEMS].data, entries, manifests),
        "client-item-animation-regions", "generated-region",
    )
    files[SERVER_ITEMS] = CandidateFile(
        SERVER_ITEMS, _render_server_items(files[SERVER_ITEMS].data, entries, manifests),
        "server-item-regions", "generated-region",
    )
    files[ITEM_IDS] = CandidateFile(
        ITEM_IDS, _render_item_ids(files[ITEM_IDS].data, entries), "item-id-regions", "generated-region",
    )
    files[APPEARANCE_IDS] = CandidateFile(
        APPEARANCE_IDS, _render_appearance_ids(files[APPEARANCE_IDS].data, entries),
        "appearance-id-regions", "generated-region",
    )

    return [files[path] for path in sorted(files, key=str)]


def build_candidate_outputs(registry: Registry, *, repo_root: Path = REPO_ROOT) -> dict[Path, bytes]:
    """Return publisher-ready target bytes keyed by repository-relative path."""
    return {item.path: item.data for item in build_candidate_files(registry, repo_root=repo_root)}


def _write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle, raw = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temp = Path(raw)
    try:
        with os.fdopen(handle, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def stage_candidate(
    registry: Registry,
    staging_root: Path,
    *,
    repo_root: Path = REPO_ROOT,
) -> dict:
    staging_root = staging_root.resolve()
    repo_root = repo_root.resolve()
    if staging_root == repo_root:
        raise CandidateError("staging root must not be the repository root")
    if staging_root.exists() and any(staging_root.iterdir()):
        raise CandidateError(f"staging root must be empty: {staging_root}")

    candidate_files = build_candidate_files(registry, repo_root=repo_root)
    for item in candidate_files:
        production = repo_root / item.path
        preimage = production.read_bytes() if production.exists() else None
        if preimage != item.data:
            raise CandidateError(
                f"Cowboy adoption is not a no-op for {item.path}; Slice 4 requires explicit review before staging changes"
            )
    outputs = []
    for item in candidate_files:
        production = repo_root / item.path
        preimage = production.read_bytes() if production.exists() else None
        _write_atomic(staging_root / item.path, item.data)
        digest = _sha256(item.data)
        outputs.append(
            {
                "candidateSha256": digest,
                "mode": item.mode,
                "path": str(item.path),
                "preimageSha256": _sha256(preimage) if preimage is not None else None,
                "role": item.role,
                "size": len(item.data),
            }
        )

    manifest = {
        "archiveContracts": _archive_contracts(
            tuple(entry for entry in registry.entries if entry.state in {"active", "adopted"}),
            {entry.key: _manifest(entry) for entry in registry.entries if entry.state in {"active", "adopted"}},
            repo_root,
        ),
        "artAndArchiveBytePreserving": True,
        "bytePreserving": True,
        "entries": [
            entry.key
            for entry in sorted(registry.entries, key=lambda value: value.key)
            if entry.state in {"active", "adopted"}
        ],
        "outputs": outputs,
        "profile": registry.profile,
        "schema": CANDIDATE_SCHEMA,
    }
    _write_atomic(
        staging_root / "appearance-candidate.json",
        (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode("utf-8"),
    )
    return manifest


def build_candidate(registry: Registry, staging_root: Path, *, repo_root: Path = REPO_ROOT) -> dict:
    """Publisher-facing name for deterministic candidate staging."""
    return stage_candidate(registry, staging_root, repo_root=repo_root)


def validate_candidate(manifest_path: Path, *, repo_root: Path = REPO_ROOT) -> list[str]:
    """Validate staged target hashes and production preimages without writes."""
    try:
        payload = json.loads(manifest_path.read_text())
    except (OSError, json.JSONDecodeError) as exc:
        return [f"candidate manifest unreadable: {exc}"]
    errors: list[str] = []
    if payload.get("schema") != CANDIDATE_SCHEMA:
        errors.append(f"unsupported candidate schema {payload.get('schema')!r}")
    outputs = payload.get("outputs")
    if not isinstance(outputs, list):
        return errors + ["candidate outputs must be a list"]
    staging_root = manifest_path.parent
    for item in outputs:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            errors.append("candidate output is malformed")
            continue
        relative = Path(item["path"])
        if relative.is_absolute() or ".." in relative.parts:
            errors.append(f"candidate output path escapes staging root: {relative}")
            continue
        try:
            candidate = (staging_root / relative).read_bytes()
        except OSError as exc:
            errors.append(f"candidate output unreadable {relative}: {exc}")
            continue
        if _sha256(candidate) != item.get("candidateSha256"):
            errors.append(f"candidate hash mismatch: {relative}")
        production = repo_root / relative
        if not production.exists() or _sha256(production.read_bytes()) != item.get("preimageSha256"):
            errors.append(f"stale production preimage: {relative}")
    return errors
