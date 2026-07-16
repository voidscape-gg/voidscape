from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw

from .paths import CLIENT_ARCHIVE, REPO_ROOT, SERVER_ARCHIVE


WALK = (
    ("north", 0, 0, False),
    ("north-west", 1, 1, False),
    ("west", 2, 2, False),
    ("south-west", 3, 3, False),
    ("south", 4, 4, False),
    ("south-east", 5, 3, True),
    ("east", 6, 2, True),
    ("north-east", 7, 1, True),
)
RASTER_SIZE = (88, 112)
DRAW_CONTRACT = {"x": 12, "y": 2, "width": 64, "height": 102, "topPixelSkew": 0, "overlayMovement": 0}


def expected_states() -> list[dict[str, Any]]:
    states: list[dict[str, Any]] = []
    for direction, wanted, actual, mirror in WALK:
        for frame in range(3):
            states.append({
                "actualAnimDir": actual,
                "direction": direction,
                "frame": frame,
                "kind": "walk",
                "mirrorX": mirror,
                "spriteOffset": actual * 3 + frame,
                "wantedAnimDir": wanted,
            })
    for direction, mirror in (("combat-a", False), ("combat-b", True)):
        for frame in range(3):
            states.append({
                "actualAnimDir": 5,
                "direction": direction,
                "frame": frame,
                "kind": "combat",
                "mirrorX": mirror,
                "spriteOffset": 15 + frame,
                "wantedAnimDir": 2,
            })
    return states


def validate_report(report: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if report.get("ok") is not True:
        errors.append("report is not successful")
    if report.get("scenario") != "appearance-frames":
        errors.append("report scenario must be appearance-frames")
    if report.get("restored") is not True:
        errors.append("preview state was not restored")
    archive = report.get("spriteArchive")
    if not isinstance(archive, dict):
        errors.append("spriteArchive contract is missing")
    else:
        archive_path = Path(str(archive.get("path", "")))
        if not archive_path.is_file():
            errors.append(f"spriteArchive is missing: {archive_path}")
        elif archive.get("sha256") != _digest(archive_path):
            errors.append("spriteArchive digest does not match")
    captures = report.get("captures")
    if not isinstance(captures, list):
        return errors + ["captures must be an array"]
    if report.get("frameCount") != 30 or len(captures) != 30:
        errors.append(f"expected 30 captures, got frameCount={report.get('frameCount')} len={len(captures)}")
    for index, expected in enumerate(expected_states()):
        if index >= len(captures):
            break
        capture = captures[index]
        for key, value in expected.items():
            if capture.get(key) != value:
                errors.append(f"capture {index} {key}={capture.get(key)!r}, expected {value!r}")
        for field in ("pngPath", "statePath"):
            path = Path(str(capture.get(field, "")))
            if not path.is_file() or path.stat().st_size <= 0:
                errors.append(f"capture {index} has missing/empty {field}: {path}")
        raster = capture.get("isolatedRaster")
        if not isinstance(raster, dict):
            errors.append(f"capture {index} has no authoritative isolatedRaster")
        else:
            raster_path = Path(str(raster.get("pngPath", "")))
            if not raster_path.is_file():
                errors.append(f"capture {index} isolated raster is missing: {raster_path}")
            else:
                try:
                    image = Image.open(raster_path).convert("RGB")
                    if image.size != RASTER_SIZE or (raster.get("width"), raster.get("height")) != RASTER_SIZE:
                        errors.append(f"capture {index} isolated raster must be 88x112")
                    raw = b"".join(bytes(pixel) for pixel in image.getdata())
                    if raster.get("rawRgbSha256") != hashlib.sha256(raw).hexdigest():
                        errors.append(f"capture {index} raw RGB digest does not match")
                except OSError as exc:
                    errors.append(f"capture {index} isolated raster is unreadable: {exc}")
                if raster.get("pngSha256") != _digest(raster_path):
                    errors.append(f"capture {index} isolated PNG digest does not match")
            crop = raster.get("crop")
            if not isinstance(crop, dict) or any(not isinstance(crop.get(key), int) for key in ("x", "y", "width", "height")):
                errors.append(f"capture {index} isolated crop is invalid")
            elif (crop["width"] <= 0 or crop["height"] <= 0 or crop["x"] < 0 or crop["y"] < 0
                  or crop["x"] + crop["width"] > RASTER_SIZE[0]
                  or crop["y"] + crop["height"] > RASTER_SIZE[1]):
                errors.append(f"capture {index} isolated crop is out of bounds")
        inputs = capture.get("renderInputs")
        if not isinstance(inputs, dict):
            errors.append(f"capture {index} has no renderInputs")
        else:
            for key in ("wantedAnimDir", "actualAnimDir", "mirrorX", "spriteOffset"):
                if inputs.get(key) != expected[key]:
                    errors.append(f"capture {index} renderInputs.{key} does not match state")
            expected_step = expected["frame"] * 6 if expected["kind"] == "walk" else 0
            if inputs.get("stepFrame") != expected_step:
                errors.append(f"capture {index} renderInputs.stepFrame={inputs.get('stepFrame')!r}, expected {expected_step}")
            for key, value in DRAW_CONTRACT.items():
                if inputs.get(key) != value:
                    errors.append(f"capture {index} renderInputs.{key}={inputs.get(key)!r}, expected {value}")
            if inputs.get("hairStyle") != 0 or inputs.get("invisible") is not False or inputs.get("invulnerable") is not False:
                errors.append(f"capture {index} uses unsupported hairStyle/invisible/invulnerable inputs")
            layer_animation = inputs.get("layerAnimation")
            layer_order = inputs.get("layerOrder")
            layers = inputs.get("layers")
            if not isinstance(layer_animation, list) or len(layer_animation) != 12:
                errors.append(f"capture {index} layerAnimation must contain 12 entries")
            if not isinstance(layer_order, list) or len(layer_order) != 12:
                errors.append(f"capture {index} layerOrder must contain 12 entries")
            if not isinstance(layers, list) or len(layers) != 12:
                errors.append(f"capture {index} layers must contain 12 entries")
            elif isinstance(layer_animation, list) and len(layer_animation) == 12:
                for slot, layer in enumerate(layers):
                    if not isinstance(layer, dict) or layer.get("slot") != slot or layer.get("appearanceId") != layer_animation[slot]:
                        errors.append(f"capture {index} layer {slot} does not resolve layerAnimation")
            for palette_key in ("paletteIndices", "resolvedRgb"):
                palette = inputs.get(palette_key)
                if not isinstance(palette, dict) or any(not isinstance(palette.get(key), int) for key in ("hair", "top", "bottom", "skin")):
                    errors.append(f"capture {index} {palette_key} is incomplete")
    keys = {
        (capture.get("kind"), capture.get("direction"), capture.get("frame"))
        for capture in captures if isinstance(capture, dict)
    }
    if len(keys) != len(captures):
        errors.append("capture state keys are not unique")
    return errors


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cowboy_asset_snapshot() -> dict[str, Any]:
    frame_root = REPO_ROOT / "content/custom/cowboy_hat/art/final/worn"
    frames = {
        str(path.relative_to(REPO_ROOT)): _digest(path)
        for path in sorted(frame_root.glob("frame_*")) if path.is_file()
    }
    return {
        "schema": "voidscape-cowboy-asset-snapshot/v1",
        "frames": frames,
        "archives": {
            str(CLIENT_ARCHIVE.relative_to(REPO_ROOT)): _digest(CLIENT_ARCHIVE),
            str(SERVER_ARCHIVE.relative_to(REPO_ROOT)): _digest(SERVER_ARCHIVE),
        },
    }


def verify_cowboy_immutability(before_path: Path, after_path: Path, output_path: Path) -> dict[str, Any]:
    before = json.loads(before_path.read_text())
    after = json.loads(after_path.read_text())
    unchanged = before == after and before.get("schema") == "voidscape-cowboy-asset-snapshot/v1"
    report = {
        "schema": "voidscape-cowboy-immutability/v1",
        "unchanged": unchanged,
        "before": str(before_path.resolve()),
        "beforeSha256": _digest(before_path),
        "after": str(after_path.resolve()),
        "afterSha256": _digest(after_path),
        "frameCount": len(before.get("frames", {})),
        "archiveCount": len(before.get("archives", {})),
        "assets": after,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return report


def write_evidence(report_path: Path, output_dir: Path) -> tuple[Path, Path]:
    report = json.loads(report_path.read_text())
    errors = validate_report(report)
    if errors:
        raise ValueError("; ".join(errors))
    output_dir.mkdir(parents=True, exist_ok=True)
    captures = report["captures"]
    from .client_preview import extract_composite_fixture
    fixture = output_dir / "composite-fixture.json"
    extract_composite_fixture(report_path, fixture)
    manifest = {
        "appearanceId": report["appearanceId"],
        "appearanceKey": report["appearanceKey"],
        "frameCount": 30,
        "report": str(report_path.resolve()),
        "reportSha256": _digest(report_path),
        "restored": True,
        "schema": "voidscape-appearance-workbench-evidence/v2",
        "authoritative": "isolatedRaster",
        "compositeFixture": str(fixture),
        "compositeFixtureSha256": _digest(fixture),
        "spriteArchive": report["spriteArchive"],
        "captures": [
            {
                "direction": capture["direction"],
                "frame": capture["frame"],
                "kind": capture["kind"],
                "mirrorX": capture["mirrorX"],
                "isolatedRaster": capture["isolatedRaster"],
                "renderInputs": capture["renderInputs"],
                "pngPath": capture["pngPath"],
                "pngSha256": _digest(Path(capture["pngPath"])),
                "spriteOffset": capture["spriteOffset"],
                "statePath": capture["statePath"],
                "stateSha256": _digest(Path(capture["statePath"])),
            }
            for capture in captures
        ],
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")

    cell_width, cell_height, label_height = 160, 120, 16
    sheet = Image.new("RGB", (8 * cell_width, 4 * cell_height), (24, 20, 28))
    draw = ImageDraw.Draw(sheet)
    for index, capture in enumerate(captures):
        if capture["kind"] == "walk":
            column = next(i for i, value in enumerate(WALK) if value[0] == capture["direction"])
            row = capture["frame"]
        else:
            column = (0 if capture["direction"] == "combat-a" else 3) + capture["frame"]
            row = 3
        image = Image.open(capture["pngPath"]).convert("RGB")
        image.thumbnail((cell_width, cell_height - label_height), Image.Resampling.NEAREST)
        x = column * cell_width + (cell_width - image.width) // 2
        y = row * cell_height + label_height
        sheet.paste(image, (x, y))
        draw.text((column * cell_width + 3, row * cell_height + 2),
                  f"{capture['direction']} {capture['frame']}", fill=(240, 230, 205))
    world_contact_sheet = output_dir / "world-contact-sheet.png"
    sheet.save(world_contact_sheet)

    raster_scale, raster_label = 2, 16
    raster_cell_width = RASTER_SIZE[0] * raster_scale
    raster_cell_height = RASTER_SIZE[1] * raster_scale + raster_label
    raster_sheet = Image.new("RGB", (8 * raster_cell_width, 4 * raster_cell_height), (24, 20, 28))
    raster_draw = ImageDraw.Draw(raster_sheet)
    for capture in captures:
        if capture["kind"] == "walk":
            column = next(i for i, value in enumerate(WALK) if value[0] == capture["direction"])
            row = capture["frame"]
        else:
            column = (0 if capture["direction"] == "combat-a" else 3) + capture["frame"]
            row = 3
        image = Image.open(capture["isolatedRaster"]["pngPath"]).convert("RGB")
        image = image.resize((RASTER_SIZE[0] * raster_scale, RASTER_SIZE[1] * raster_scale), Image.Resampling.NEAREST)
        x, y = column * raster_cell_width, row * raster_cell_height + raster_label
        raster_sheet.paste(image, (x, y))
        raster_draw.text((x + 3, row * raster_cell_height + 2),
                         f"{capture['direction']} {capture['frame']}", fill=(240, 230, 205))
    contact_sheet = output_dir / "isolated-contact-sheet.png"
    raster_sheet.save(contact_sheet)
    manifest["isolatedContactSheet"] = str(contact_sheet)
    manifest["isolatedContactSheetSha256"] = _digest(contact_sheet)
    manifest["worldContactSheet"] = str(world_contact_sheet)
    manifest["worldContactSheetSha256"] = _digest(world_contact_sheet)
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    return manifest_path, contact_sheet
