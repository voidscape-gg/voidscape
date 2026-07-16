from __future__ import annotations

import hashlib
import json
import re
import shutil
import struct
from pathlib import Path
from typing import Any, Mapping, Sequence
import zipfile

from PIL import Image

from .client_preview import (
    ANIM_DIR_LAYER_TO_CHAR_LAYER,
    BASE_DRAW_SIZE,
    CANVAS_SIZE,
    DRAW_ORIGIN,
    SpriteFrame,
    decode_sprite,
    draw_sprite_clipping,
    load_reference_frames,
    state_contact_sheet,
)
from .geometry import derive_sprite, expand_crop, hard_alpha, translate_integer
from .paths import CLIENT_ARCHIVE, REPO_ROOT, SERVER_ARCHIVE
from .template import PaperdollTemplate, load_template
from .workbench_report import expected_states


SCHEMA = "voidscape-manual-headwear-workspace/v1"
REPORT_SCHEMA = "voidscape-manual-headwear-build/v1"
REFERENCE_BASES = {"mediumhelm": 540, "wizardshat": 594, "partyhat": 1377}
MASTER_OFFSETS = {
    "north": 0,
    "north-west": 3,
    "west": 6,
    "south-west": 9,
    "south": 12,
    "combat-west": 15,
}
DEFAULT_TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"
DEFAULT_PREVIEW_MASK = 0x805030
BACKGROUND = 0x121820


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def _png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _relative(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(REPO_ROOT.resolve()))
    except ValueError as exc:
        raise ValueError(f"path must remain inside the repository: {path}") from exc


def _tmp_root(path: Path) -> Path:
    resolved = path.resolve()
    tmp = (REPO_ROOT / "tmp").resolve()
    try:
        relative = resolved.relative_to(tmp)
    except ValueError as exc:
        raise ValueError(f"manual headwear workspaces must remain under {tmp}") from exc
    if not relative.parts:
        raise ValueError("refusing to use the repository tmp root as a workspace")
    return resolved


def _reference_frames(reference: str) -> tuple[tuple[SpriteFrame, ...], dict[str, Any]]:
    if reference not in REFERENCE_BASES:
        raise ValueError(f"reference must be one of {sorted(REFERENCE_BASES)}")
    base = REFERENCE_BASES[reference]
    frames: list[SpriteFrame] = []
    entry_hashes: list[str] = []
    with zipfile.ZipFile(CLIENT_ARCHIVE) as client, zipfile.ZipFile(SERVER_ARCHIVE) as server:
        for offset in range(18):
            entry = str(base + offset)
            try:
                client_bytes = client.read(entry)
                server_bytes = server.read(entry)
            except KeyError as exc:
                raise ValueError(f"authentic {reference} entry {entry} is missing") from exc
            if client_bytes != server_bytes:
                raise ValueError(f"authentic {reference} entry {entry} differs between client and server")
            frames.append(decode_sprite(client_bytes))
            entry_hashes.append(hashlib.sha256(client_bytes).hexdigest())
    return tuple(frames), {
        "name": reference,
        "spriteBase": base,
        "clientArchiveSha256": _sha256(CLIENT_ARCHIVE),
        "serverArchiveSha256": _sha256(SERVER_ARCHIVE),
        "entrySha256": entry_hashes,
    }


def _normalization(image: Image.Image) -> tuple[Image.Image, dict[str, int]]:
    source = image.convert("RGBA")
    normalized = hard_alpha(source)
    changed = soft = safe_black = 0
    for before, after in zip(source.getdata(), normalized.getdata()):
        if before != after:
            changed += 1
        if before[3] not in {0, 255}:
            soft += 1
        if before[:3] == (0, 0, 0) and before[3] >= 128:
            safe_black += 1
    return normalized, {
        "changedPixels": changed,
        "softAlphaPixels": soft,
        "safeBlackPixels": safe_black,
    }


def _encode_legacy(frame: SpriteFrame) -> bytes:
    image = frame.image.convert("RGBA")
    sidecar = frame.sidecar
    header = struct.pack(
        ">iiBiiii", image.width, image.height,
        1 if sidecar.get("requiresShift") else 0,
        int(sidecar.get("xShift", 0)), int(sidecar.get("yShift", 0)),
        int(sidecar.get("something1", 0)), int(sidecar.get("something2", 0)),
    )
    pixels = bytearray()
    for red, green, blue, alpha in image.getdata():
        if alpha < 128:
            pixels.extend((0, 0, 0, 0))
        else:
            if (red, green, blue) == (0, 0, 0):
                red = 1
            pixels.extend((0, red, green, blue))
    return header + bytes(pixels)


def _draw_layers(
    template: PaperdollTemplate,
    offset: int,
    wanted_direction: int,
    mirror_x: bool,
    hats: Sequence[SpriteFrame] | None,
    *,
    canvas_size: tuple[int, int],
    draw_origin: tuple[int, int],
    base_draw_size: tuple[int, int],
    preview_mask: int,
) -> Image.Image:
    heads = load_reference_frames(template.reference_dirs["head4"])
    bodies = load_reference_frames(template.reference_dirs["body"])
    legs = load_reference_frames(template.reference_dirs["legs"])
    layers: dict[int, tuple[Sequence[SpriteFrame], int]] = {
        0: (heads, 0x805030),
        1: (bodies, 0x345C94),
        2: (legs, 0x5C442C),
    }
    if hats is not None:
        layers[5] = (hats, preview_mask)
    canvas = Image.new("RGB", canvas_size, ((BACKGROUND >> 16) & 255, (BACKGROUND >> 8) & 255, BACKGROUND & 255))
    for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[wanted_direction]:
        item = layers.get(slot)
        if item is None:
            continue
        frames, mask1 = item
        frame, frame0 = frames[offset], frames[0]
        logical_width = int(frame.sidecar["something1"])
        frame0_width = int(frame0.sidecar["something1"])
        draw_width = logical_width * base_draw_size[0] // frame0_width
        x = draw_origin[0] - (draw_width - base_draw_size[0]) // 2
        draw_sprite_clipping(
            canvas, frame, x, draw_origin[1], draw_width, base_draw_size[1],
            mask1, 0xECDED0, mirror_x=mirror_x,
        )
    return canvas


def _guide(template: PaperdollTemplate, master: str) -> Image.Image:
    offset = MASTER_OFFSETS[master]
    frame = template.frames[offset]
    wanted = 2 if offset >= 15 else offset // 3
    # The live renderer centers an 84px combat layer in a 64px base draw box.
    # On the 84px authoring canvas the equivalent origin is +10, which cancels
    # that centering subtraction and keeps logical x=0 at guide x=0.
    logical_origin = ((frame.size[0] - 64) // 2, 0)
    return _draw_layers(
        template, offset, wanted, False, None,
        canvas_size=frame.size, draw_origin=logical_origin, base_draw_size=(64, 102),
        preview_mask=DEFAULT_PREVIEW_MASK,
    )


def init_headwear_workspace(
    name: str,
    reference: str,
    out: Path,
    *,
    template_path: Path = DEFAULT_TEMPLATE,
    force: bool = False,
) -> dict[str, Any]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", name):
        raise ValueError("name must use lowercase letters, numbers, hyphens, or underscores")
    root = _tmp_root(out)
    if root.exists():
        if not force:
            raise FileExistsError(f"workspace already exists: {root}; pass --force to replace it")
        shutil.rmtree(root)
    root.mkdir(parents=True)
    template = load_template(template_path)
    reference_frames, reference_info = _reference_frames(reference)
    for master, offset in MASTER_OFFSETS.items():
        frame = template.frames[offset]
        canvas = expand_crop(reference_frames[offset].image, dict(reference_frames[offset].sidecar))
        if canvas.size != frame.size:
            raise ValueError(f"reference {reference} {master} canvas {canvas.size} != template {frame.size}")
        _png(root / "masters" / f"{master}.png", canvas)
        _png(root / "guides" / f"{master}.png", _guide(template, master))
    manifest = {
        "schema": SCHEMA,
        "shipping": False,
        "name": name,
        "slot": 5,
        "template": {"path": _relative(template.path), "sha256": template.digest},
        "reference": reference_info,
        "preview": {"mask1": DEFAULT_PREVIEW_MASK, "backgroundRgb": BACKGROUND},
        "masters": {master: f"masters/{master}.png" for master in MASTER_OFFSETS},
        "instructions": "Edit only the six masters; run headwear-build to regenerate build/.",
    }
    _json(root / "workspace.json", manifest)
    return manifest


def load_headwear_workspace(root: Path) -> tuple[Path, dict[str, Any], PaperdollTemplate]:
    workspace_root = _tmp_root(root)
    manifest_path = workspace_root / "workspace.json"
    payload = json.loads(manifest_path.read_text())
    if payload.get("schema") != SCHEMA or payload.get("shipping") is not False or payload.get("slot") != 5:
        raise ValueError("unsupported manual headwear workspace")
    if set(payload.get("masters", {})) != set(MASTER_OFFSETS):
        raise ValueError("workspace must declare exactly the six locked headwear masters")
    template_path = REPO_ROOT / payload["template"]["path"]
    template = load_template(template_path)
    if template.digest != payload["template"]["sha256"]:
        raise ValueError("workspace template digest changed")
    for archive_key, archive in (("clientArchiveSha256", CLIENT_ARCHIVE), ("serverArchiveSha256", SERVER_ARCHIVE)):
        if _sha256(archive) != payload["reference"][archive_key]:
            raise ValueError(f"workspace authentic reference drifted: {archive.name}")
    return workspace_root, payload, template


def compile_headwear_masters(
    root: Path, manifest: Mapping[str, Any], template: PaperdollTemplate,
) -> tuple[tuple[SpriteFrame, ...], list[Image.Image], dict[str, dict[str, int]]]:
    masters: dict[str, Image.Image] = {}
    normalization: dict[str, dict[str, int]] = {}
    source_frames = {frame.master: frame for frame in template.frames if frame.offset in MASTER_OFFSETS.values()}
    for master in MASTER_OFFSETS:
        declared = Path(str(manifest["masters"][master]))
        if declared.is_absolute() or ".." in declared.parts:
            raise ValueError(f"unsafe master path for {master}")
        path = root / declared
        with Image.open(path) as source:
            image = source.convert("RGBA")
        if image.size != source_frames[master].size:
            raise ValueError(f"master {master} size {image.size} != {source_frames[master].size}")
        normalized, counts = _normalization(image)
        if normalized.getbbox() is None:
            raise ValueError(f"master {master} is empty")
        masters[master] = normalized
        normalization[master] = counts

    frames: list[SpriteFrame] = []
    canvases: list[Image.Image] = []
    for frame in template.frames:
        source = source_frames[frame.master]
        dx, dy = frame.crown[0] - source.crown[0], frame.crown[1] - source.crown[1]
        canvas = translate_integer(masters[frame.master], dx, dy, frame.size)
        derived = derive_sprite(canvas)
        if expand_crop(derived.image, derived.sidecar).tobytes() != canvas.tobytes():
            raise ValueError(f"frame {frame.offset} crop/sidecar round-trip failed")
        frames.append(SpriteFrame(derived.image, derived.sidecar))
        canvases.append(canvas)
    return tuple(frames), canvases, normalization


def _render_preview(template: PaperdollTemplate, hats: Sequence[SpriteFrame], state: Mapping[str, Any], mask: int) -> Image.Image:
    return _draw_layers(
        template, int(state["spriteOffset"]), int(state["wantedAnimDir"]), bool(state["mirrorX"]), hats,
        canvas_size=CANVAS_SIZE, draw_origin=DRAW_ORIGIN, base_draw_size=BASE_DRAW_SIZE,
        preview_mask=mask,
    )


def _artifact_hashes(root: Path, build: Path) -> list[dict[str, Any]]:
    artifacts = []
    for path in sorted(item for item in build.rglob("*") if item.is_file() and item.name != "report.json"):
        artifacts.append({"path": str(path.relative_to(root)), "sha256": _sha256(path), "bytes": path.stat().st_size})
    return artifacts


def build_headwear_workspace(root: Path) -> dict[str, Any]:
    workspace_root, manifest, template = load_headwear_workspace(root)
    frames, canvases, normalization = compile_headwear_masters(workspace_root, manifest, template)
    build = workspace_root / "build"
    if build.exists():
        shutil.rmtree(build)
    (build / "frames").mkdir(parents=True)
    for offset, (frame, canvas) in enumerate(zip(frames, canvases)):
        path = build / "frames" / f"frame_{offset:02d}.png"
        _png(path, frame.image)
        _json(path.with_suffix(".png.json"), dict(frame.sidecar))

    states = expected_states()
    previews = []
    preview_entries = []
    preview_mask = int(manifest["preview"]["mask1"])
    for index, state in enumerate(states):
        image = _render_preview(template, frames, state, preview_mask)
        if image.size != CANVAS_SIZE:
            raise ValueError(f"preview {index} has unexpected size {image.size}")
        filename = f"{index:02d}-{state['direction']}-{state['frame']}.png"
        path = build / "previews" / filename
        _png(path, image)
        previews.append(image)
        preview_entries.append({**state, "path": str(path.relative_to(workspace_root))})
    _png(build / "contact-sheet.png", state_contact_sheet(previews, states, scale=1))

    reference_base = int(manifest["reference"]["spriteBase"])
    with zipfile.ZipFile(CLIENT_ARCHIVE) as archive:
        seed_round_trip = all(
            _encode_legacy(frame) == archive.read(str(reference_base + offset))
            for offset, frame in enumerate(frames)
        )

    report = {
        "schema": REPORT_SCHEMA,
        "valid": True,
        "shipping": False,
        "humanVisualApproval": False,
        "workspace": manifest["name"],
        "templateSha256": template.digest,
        "reference": manifest["reference"],
        "frameCount": len(frames),
        "previewCount": len(previews),
        "seedRoundTrip": seed_round_trip,
        "normalization": normalization,
        "masterSha256": {
            master: _sha256(workspace_root / manifest["masters"][master]) for master in MASTER_OFFSETS
        },
        "previews": preview_entries,
        "artifacts": _artifact_hashes(workspace_root, build),
    }
    _json(build / "report.json", report)
    return report
