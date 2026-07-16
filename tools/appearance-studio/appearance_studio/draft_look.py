from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

from .art_qa import validate_compiled_layer
from .client_preview import SpriteFrame, contact_sheet, render_canonical_stored_players
from .compiler import expand_rigid_layer
from .evidence_contract import REVOKED_DRAFT_REPORT_SCHEMAS, REVOCATION_REASON
from .geometry import runtime_mirror
from .look import compile_look, load_locked_base_frames, load_look_manifest
from .paths import REPO_ROOT
from .template import load_template


MASTER_NAMES = ("north", "north-west", "west", "south-west", "south", "combat-west")
DIRECTIONS = (
    ("north", 0, False), ("north-west", 3, False), ("west", 6, False),
    ("south-west", 9, False), ("south", 12, False), ("south-east", 9, True),
    ("east", 6, True), ("north-east", 3, True), ("combat-a", 15, False),
    ("combat-b", 15, True),
)
SHADES = (103, 132, 160)


def render_full_players(template, compiled_frames) -> list[Image.Image]:
    heads = [SpriteFrame(frame.sprite.image, frame.sprite.sidecar) for frame in compiled_frames]
    return render_canonical_stored_players(template, heads)


def _put(image: Image.Image, x: int, y: int, shade: int) -> None:
    if 0 <= x < image.width and 0 <= y < image.height:
        image.putpixel((x, y), (shade, shade, shade, 255))


def _mullet(size: tuple[int, int], crown: tuple[int, int], master: str) -> Image.Image:
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    cx, cy = crown
    rear = master in {"south-west", "south"}
    # Uneven crown and broken hairline avoid a smooth helmet silhouette.
    rows = ((-2, -3, 3), (-1, -6, 5), (0, -8, 7), (1, -7, 8), (2, -8, 6), (3, -6, 7))
    for dy, left, right in rows:
        for dx in range(left, right + 1):
            if (dx + dy) % 7 == 0 and abs(dx) < 5:
                continue
            edge = dx in {left, right}
            _put(image, cx + dx, cy + dy, 103 if edge else SHADES[(dx - left + dy) % 3])
    if rear:
        # The mullet's defining mass belongs on rear masters, tapering toward the neck.
        for dy in range(4, 15):
            half = 9 if dy < 9 else 8 if dy < 12 else 6
            lean = -1 if master == "south-west" else 0
            for dx in range(-half + lean, half + lean + 1):
                if dy >= 12 and (dx + dy) % 3 == 0:
                    continue
                _put(image, cx + dx, cy + dy, SHADES[(dx + dy) % 3])
    else:
        # Front and side views keep the face open; only short side locks and a modest rear tail remain.
        side_pairs = ((-8, -7, 6, 7) if master in {"north", "north-west"} else (-9, -8, -7, 6))
        depth = 8 if master in {"north", "north-west"} else 11
        for dy in range(4, depth + 1):
            for dx in side_pairs:
                _put(image, cx + dx, cy + dy, SHADES[(dy + dx) % 3])
    return image


def _mustache(size: tuple[int, int], crown: tuple[int, int], master: str) -> Image.Image:
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    cx, cy = crown
    # Start at mouth-anchor height +11, below the protected nose rectangle.
    if master in {"west", "combat-west"}:
        points = ((-5, 11), (-4, 11), (-3, 11), (-6, 12), (-5, 12), (-4, 12), (-3, 12), (-5, 13), (-4, 13))
    elif master in {"north-west", "south-west"}:
        points = ((-5, 11), (-4, 11), (-3, 11), (-2, 11), (1, 11), (2, 11),
                  (-6, 12), (-5, 12), (-4, 12), (-3, 12), (1, 12), (2, 12), (3, 12),
                  (-5, 13), (-4, 13), (2, 13))
    else:
        points = tuple((dx, 11) for dx in (-5, -4, -3, -2, 2, 3, 4, 5)) + \
                 tuple((dx, 12) for dx in (-6, -5, -4, -3, -2, 2, 3, 4, 5, 6)) + \
                 tuple((dx, 13) for dx in (-5, -4, 4, 5))
    for index, (dx, dy) in enumerate(points):
        _put(image, cx + dx, cy + dy, SHADES[index % len(SHADES)])
    return image


def draft_masters(template) -> dict[str, dict[str, Image.Image]]:
    source = {frame.master: frame for frame in template.frames if frame.offset in {0, 3, 6, 9, 12, 15}}
    return {
        "future_mullet": {name: _mullet(source[name].size, source[name].crown, name) for name in MASTER_NAMES},
        "future_mustache": {name: _mustache(source[name].size, source[name].crown, name) for name in MASTER_NAMES},
    }


def _save(image: Image.Image, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _matrix(frames: list[Image.Image], labels: list[str], *, scale: int = 4, columns: int = 6) -> Image.Image:
    return contact_sheet(frames, labels, scale=scale, columns=columns)


def build_draft_look(look_path: Path, template_path: Path, out: Path, *, repo_root: Path = REPO_ROOT) -> dict:
    out = out.resolve()
    try:
        out.relative_to((repo_root / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("draft output must remain under repository tmp/") from exc
    schema = "voidscape-draft-look/v1"
    if schema in REVOKED_DRAFT_REPORT_SCHEMAS:
        raise ValueError(f"draft-look generation for schema {schema!r} is disabled because {REVOCATION_REASON}")
    look = load_look_manifest(look_path, repo_root=repo_root)
    if look.publishable or look.art is not None:
        raise ValueError("draft generator accepts only an unapproved art-null Look")
    template = load_template(template_path, repo_root=repo_root)
    masters = draft_masters(template)
    compiled_layers = {
        layer.key: expand_rigid_layer(masters[layer.key], layer.kind, layer.manifest.palette_policy, template,
                                      hidden_masters=layer.manifest.hidden_masters)
        for layer in look.layers
    }
    findings = [f"{key}: {finding}" for key, result in compiled_layers.items() for finding in result.findings]
    if findings:
        raise ValueError("; ".join(findings))
    compiled = compile_look(look, template, compiled_layers)
    base = load_locked_base_frames(template)
    art_qa = {
        layer.key: validate_compiled_layer(
            compiled_layers[layer.key], layer.kind, template, base,
            hidden_masters=layer.manifest.hidden_masters,
        )
        for layer in look.layers
    }
    qa_findings = [
        f"{key}: {finding}"
        for key, report in art_qa.items()
        for finding in report["findings"]
    ]
    if qa_findings:
        raise ValueError("; ".join(qa_findings))
    players = render_full_players(template, compiled.frames)
    hashes: dict[str, str] = {}
    for layer_key, images in masters.items():
        for name, image in images.items():
            relative = Path("masters") / layer_key / f"{name}.png"
            hashes[str(relative)] = _save(image, out / relative)
    for offset, image in enumerate(base):
        relative = Path("compiled/base") / f"frame_{offset:02d}.png"
        hashes[str(relative)] = _save(image, out / relative)
    for layer_key, result in compiled_layers.items():
        for frame in result.frames:
            relative = Path("compiled/layers") / layer_key / f"frame_{frame.offset:02d}.png"
            hashes[str(relative)] = _save(frame.canvas, out / relative)
    for frame in compiled.frames:
        relative = Path("compiled/composed") / f"frame_{frame.offset:02d}.png"
        hashes[str(relative)] = _save(frame.canvas, out / relative)
    for offset, image in enumerate(players):
        relative = Path("compiled/player") / f"frame_{offset:02d}.png"
        hashes[str(relative)] = _save(image, out / relative)

    labels18 = [f"stored {offset:02d}" for offset in range(18)]
    preview_paths = {}
    for key, frames in (
        ("mullet", [frame.canvas for frame in compiled_layers["future_mullet"].frames]),
        ("mustache", [frame.canvas for frame in compiled_layers["future_mustache"].frames]),
        ("composed", [frame.canvas for frame in compiled.frames]),
        ("full-player", players),
    ):
        relative = Path("previews") / f"{key}-stored-18.png"
        hashes[str(relative)] = _save(_matrix(frames, labels18), out / relative); preview_paths[key] = str(relative)

    runtime_frames, runtime_labels, runtime_states = [], [], []
    player_runtime_frames = []
    for direction, base_offset, mirrored in DIRECTIONS:
        for phase in range(3):
            stored = base_offset + phase
            image = compiled.frames[stored].canvas
            runtime_frames.append(runtime_mirror(image) if mirrored else image.copy())
            player = players[stored]
            player_runtime_frames.append(runtime_mirror(player) if mirrored else player.copy())
            runtime_labels.append(f"{direction} {phase}")
            runtime_states.append({"direction": direction, "phase": phase, "storedOffset": stored, "mirrorX": mirrored})
    runtime_relative = Path("previews/runtime-30.png")
    hashes[str(runtime_relative)] = _save(_matrix(runtime_frames, runtime_labels, columns=5), out / runtime_relative)
    player_runtime_relative = Path("previews/full-player-runtime-30.png")
    hashes[str(player_runtime_relative)] = _save(
        _matrix(player_runtime_frames, runtime_labels, scale=4, columns=5), out / player_runtime_relative
    )
    report = {
        "schema": schema,
        "geometryContract": "voidscape-paperdoll-geometry/v1",
        "shipping": False, "generatedByAI": False,
        "look": look.key, "frameCount": 18, "runtimeStateCount": 30, "findings": [], "artQa": art_qa,
        "layers": [layer.key for layer in look.layers], "runtimeStates": runtime_states,
        "previews": {**preview_paths, "runtime": str(runtime_relative),
                     "fullPlayerRuntime": str(player_runtime_relative)}, "sha256": dict(sorted(hashes.items())),
    }
    (out / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return report
