from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter

from .paths import REPO_ROOT, TOOL_OUT_DIR, UI_TOPBAR_SPEC, VOIDSCAPE_SKIN_DIR
from .ui_concepts import add_ui_concept_subcommands


DEFAULT_PREVIEW = TOOL_OUT_DIR / "voidscape-topbar-preview.png"
DEFAULT_INGEST_DIR = TOOL_OUT_DIR / "ui-ingest"


def _repo_path(value: str | Path) -> Path:
    path = Path(value)
    return path if path.is_absolute() else REPO_ROOT / path


def _repo_rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()
    except ValueError:
        return str(path)


def load_spec(path: Path = UI_TOPBAR_SPEC) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def skin_dir_from_spec(spec: dict[str, Any], override: Path | None = None) -> Path:
    if override is not None:
        return override
    return _repo_path(spec["skin_dir"])


def _image_size(path: Path) -> tuple[int, int] | None:
    if not path.exists():
        return None
    with Image.open(path) as image:
        return image.size


def _stem(asset: str) -> str:
    return Path(asset).stem


def _asset_path(skin_dir: Path, asset: str, fallback_dir: Path | None = None) -> Path:
    path = skin_dir / asset
    if path.exists() or fallback_dir is None:
        return path
    fallback = fallback_dir / asset
    return fallback if fallback.exists() else path


def _load_rgba(skin_dir: Path, asset: str, fallback_dir: Path | None = None) -> Image.Image:
    path = _asset_path(skin_dir, asset, fallback_dir)
    if not path.exists():
        raise FileNotFoundError(path)
    return Image.open(path).convert("RGBA")


def _variant_asset(asset: str, size: int) -> str:
    path = Path(asset)
    return f"{path.stem}-{size}{path.suffix}"


def _variant_or_master(skin_dir: Path, asset: str, size: int, fallback_dir: Path | None = None) -> str:
    variant = _variant_asset(asset, size)
    if _asset_path(skin_dir, variant, fallback_dir).exists():
        return variant
    return asset


def _parse_size(value: str) -> tuple[int, int]:
    value = value.lower().strip()
    if "x" in value:
        left, right = value.split("x", 1)
        width = int(left)
        height = int(right)
    else:
        width = height = int(value)
    if width <= 0 or height <= 0:
        raise argparse.ArgumentTypeError("size must be positive")
    return width, height


def _parse_hex_color(value: str) -> tuple[int, int, int]:
    raw = value.strip().lower()
    if raw.startswith("#"):
        raw = raw[1:]
    if raw.startswith("0x"):
        raw = raw[2:]
    if len(raw) != 6:
        raise argparse.ArgumentTypeError("colour must be #RRGGBB")
    try:
        color = int(raw, 16)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("colour must be #RRGGBB") from exc
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


def _remove_chroma(image: Image.Image, key: tuple[int, int, int], tolerance: int) -> Image.Image:
    out = image.convert("RGBA")
    pixels = []
    kr, kg, kb = key
    for r, g, b, a in out.getdata():
        if abs(r - kr) <= tolerance and abs(g - kg) <= tolerance and abs(b - kb) <= tolerance:
            pixels.append((r, g, b, 0))
        else:
            pixels.append((r, g, b, a))
    out.putdata(pixels)
    return out


def _trim_alpha(image: Image.Image) -> Image.Image:
    bbox = image.getchannel("A").getbbox()
    return image.crop(bbox) if bbox else image


def _fit_centered(image: Image.Image, size: int) -> Image.Image:
    image = image.convert("RGBA")
    if image.width <= 0 or image.height <= 0:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = min(size / image.width, size / image.height)
    width = max(1, int(round(image.width * scale)))
    height = max(1, int(round(image.height * scale)))
    resized = image.resize((width, height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((size - width) // 2, (size - height) // 2))
    return canvas


def bake_icon_variant(master: Image.Image, size: int) -> Image.Image:
    icon = master.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    icon = ImageEnhance.Contrast(icon).enhance(1.12)
    icon = ImageEnhance.Sharpness(icon).enhance(2.05)
    icon = icon.filter(ImageFilter.UnsharpMask(radius=0.45, percent=120, threshold=2))

    alpha = icon.getchannel("A")
    stroke_alpha = alpha.filter(ImageFilter.MaxFilter(3))
    stroke_alpha = ImageChops.subtract(stroke_alpha, alpha)
    stroke_alpha = stroke_alpha.point(lambda px: int(px * 0.33))

    stroke = Image.new("RGBA", (size, size), (3, 3, 5, 0))
    stroke.putalpha(stroke_alpha)
    stroke.alpha_composite(icon)
    return stroke


def _slot_lookup(spec: dict[str, Any]) -> dict[str, dict[str, Any]]:
    lookup: dict[str, dict[str, Any]] = {}
    for slot in spec["slots"]:
        lookup[slot["key"]] = slot
        lookup[_stem(slot["asset"])] = slot
        lookup[slot["asset"]] = slot
    return lookup


def _parse_slot_indices(value: str, spec: dict[str, Any]) -> dict[str, int]:
    lookup = _slot_lookup(spec)
    indices: dict[str, int] = {}
    if not value:
        return indices
    for part in value.split(","):
        if not part.strip():
            continue
        if "=" not in part:
            raise ValueError("slot mapping must look like options=0,inventory=5")
        key, raw_index = part.split("=", 1)
        key = key.strip()
        if key not in lookup:
            raise ValueError(f"unknown slot: {key}")
        indices[lookup[key]["key"]] = int(raw_index.strip())
    return indices


def _spec_slot_order(spec: dict[str, Any]) -> list[dict[str, Any]]:
    by_key = {slot["key"]: slot for slot in spec["slots"]}
    order = spec.get("sheet_ingest", {}).get("slot_order") or list(by_key)
    return [by_key[key] for key in order]


def _print_validation_line(kind: str, message: str) -> None:
    print(f"{kind}: {message}")


def cmd_spec(args: argparse.Namespace) -> int:
    spec = load_spec(args.spec)
    encoded = json.dumps(spec, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(encoded, encoding="utf-8")
        print(f"wrote spec: {_repo_rel(args.out)}")
        return 0
    if args.json:
        print(encoded, end="")
        return 0

    runtime = spec["runtime"]
    print(f"spec: {_repo_rel(args.spec)}")
    print(f"skin: {spec['skin_dir']}")
    print(f"slots: {len(spec['slots'])}")
    print(f"masters: {spec['master_size']}x{spec['master_size']}")
    print(f"variants: {', '.join(str(size) for size in spec['variant_sizes'])}")
    print(f"runtime: normal {runtime['normal']['icon_size']}px, compact {runtime['compact']['icon_size']}px")
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    spec = load_spec(args.spec)
    skin_dir = skin_dir_from_spec(spec, args.skin_dir)
    errors: list[str] = []
    warnings: list[str] = []

    if not skin_dir.exists():
        errors.append(f"skin directory missing: {_repo_rel(skin_dir)}")
    for frame in spec["frames"]:
        path = skin_dir / frame["asset"]
        size = _image_size(path)
        expected = (int(frame["width"]), int(frame["height"]))
        if size is None:
            errors.append(f"missing frame {frame['asset']}")
        elif size != expected:
            errors.append(f"{frame['asset']} is {size[0]}x{size[1]}, expected {expected[0]}x{expected[1]}")

    master_size = int(spec["master_size"])
    variant_sizes = [int(size) for size in spec["variant_sizes"]]
    runtime_sizes = {int(mode["icon_size"]) for mode in spec["runtime"].values()}
    sizes_to_check = sorted(runtime_sizes if args.runtime_only else variant_sizes)

    for slot in spec["slots"]:
        asset = slot["asset"]
        master = skin_dir / asset
        master_dimensions = _image_size(master)
        if master_dimensions is None:
            errors.append(f"missing master {asset}")
            continue
        if master_dimensions != (master_size, master_size):
            errors.append(
                f"{asset} is {master_dimensions[0]}x{master_dimensions[1]}, "
                f"expected {master_size}x{master_size}"
            )
        for size in sizes_to_check:
            variant = _variant_asset(asset, size)
            dimensions = _image_size(skin_dir / variant)
            if dimensions is None:
                message = f"missing variant {variant}"
                if size in runtime_sizes:
                    errors.append(message)
                else:
                    warnings.append(message)
            elif dimensions != (size, size):
                errors.append(f"{variant} is {dimensions[0]}x{dimensions[1]}, expected {size}x{size}")

    for warning in warnings:
        _print_validation_line("warn", warning)
    if errors:
        for error in errors:
            _print_validation_line("error", error)
        print(f"failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(
        f"ok: {len(spec['slots'])} topbar icon slots, "
        f"{len(sizes_to_check)} size variant(s) each in {_repo_rel(skin_dir)}"
    )
    return 0


def cmd_preview(args: argparse.Namespace) -> int:
    spec = load_spec(args.spec)
    skin_dir = skin_dir_from_spec(spec, args.skin_dir)
    fallback_dir = skin_dir_from_spec(spec, args.fallback_skin_dir) if args.fallback_skin_dir else None

    rows = [
        ("normal", spec["runtime"]["normal"]),
        ("compact", spec["runtime"]["compact"]),
    ]
    tab_gap = -4
    padding = 18
    label_h = 18
    row_gap = 24
    spans = []
    for _, runtime in rows:
        tab_size = int(runtime["tab_size"])
        spans.append(tab_size * len(spec["slots"]) + tab_gap * (len(spec["slots"]) - 1))
    width = max(spans) + padding * 2
    height = padding + sum(label_h + int(row[1]["tab_size"]) for row in rows) + row_gap * (len(rows) - 1) + padding
    canvas = Image.new("RGBA", (width, height), (16, 17, 18, 255))
    draw = ImageDraw.Draw(canvas)

    y = padding
    active_key = args.active
    for row_name, runtime in rows:
        tab_size = int(runtime["tab_size"])
        icon_size = int(runtime["icon_size"])
        x = padding
        draw.text((x, y), f"{row_name} topbar ({tab_size}px tabs / {icon_size}px icons)", fill=(226, 214, 181, 255))
        y += label_h
        for slot in spec["slots"]:
            frame_asset = "top-tab-active.png" if slot["key"] == active_key else "top-tab-normal.png"
            frame = _load_rgba(skin_dir, frame_asset, fallback_dir)
            if frame.size != (tab_size, tab_size):
                frame = frame.resize((tab_size, tab_size), Image.Resampling.LANCZOS)
            canvas.alpha_composite(frame, (x, y))

            icon_asset = _variant_or_master(skin_dir, slot["asset"], icon_size, fallback_dir)
            icon = _load_rgba(skin_dir, icon_asset, fallback_dir)
            if icon.size != (icon_size, icon_size):
                icon = icon.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
            pad = (tab_size - icon_size) // 2
            canvas.alpha_composite(icon, (x + pad, y + pad))
            x += tab_size + tab_gap
        y += tab_size + row_gap

    args.out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(args.out)
    print(f"wrote preview: {_repo_rel(args.out)}")
    return 0


def cmd_ingest_sheet(args: argparse.Namespace) -> int:
    spec = load_spec(args.spec)
    sheet_path = args.sheet
    if not sheet_path.exists():
        print(f"error: sheet does not exist: {_repo_rel(sheet_path)}", file=sys.stderr)
        return 2

    cell_w, cell_h = args.cell_size
    if args.columns <= 0:
        print("error: --columns must be positive", file=sys.stderr)
        return 2

    try:
        custom_indices = _parse_slot_indices(args.slots or "", spec)
    except (TypeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    default_start = int(spec.get("sheet_ingest", {}).get("default_start_index", 0))
    start_index = default_start if args.start_index is None else args.start_index
    ordered_slots = _spec_slot_order(spec)
    slot_indices: dict[str, int] = {}
    for offset, slot in enumerate(ordered_slots):
        slot_indices[slot["key"]] = start_index + offset
    slot_indices.update(custom_indices)

    output_dir = args.output_dir
    if output_dir is None:
        output_dir = VOIDSCAPE_SKIN_DIR if args.commit else DEFAULT_INGEST_DIR

    bg_key = _parse_hex_color(args.bg_key) if args.bg_key else None
    variant_sizes = [int(size) for size in spec["variant_sizes"]]
    master_size = int(spec["master_size"])
    sheet = Image.open(sheet_path).convert("RGBA")
    writes: list[Path] = []

    for slot in ordered_slots:
        index = slot_indices[slot["key"]]
        col = index % args.columns
        row = index // args.columns
        left = args.margin + col * (cell_w + args.gap)
        top = args.margin + row * (cell_h + args.gap)
        box = (left, top, left + cell_w, top + cell_h)
        if box[2] > sheet.width or box[3] > sheet.height:
            print(f"error: cell {index} for {slot['key']} exceeds sheet bounds", file=sys.stderr)
            return 2

        crop = sheet.crop(box)
        if bg_key is not None:
            crop = _remove_chroma(crop, bg_key, args.bg_tolerance)
        if args.trim:
            crop = _trim_alpha(crop)
        master = _fit_centered(crop, master_size)

        master_path = output_dir / slot["asset"]
        writes.append(master_path)
        for size in variant_sizes:
            writes.append(output_dir / _variant_asset(slot["asset"], size))

        if args.dry_run:
            continue

        output_dir.mkdir(parents=True, exist_ok=True)
        master.save(master_path)
        for size in variant_sizes:
            bake_icon_variant(master, size).save(output_dir / _variant_asset(slot["asset"], size))

    action = "would write" if args.dry_run else "wrote"
    print(f"{action}: {len(writes)} file(s) under {_repo_rel(output_dir)}")
    if args.commit:
        print("committed to live Voidscape skin directory")
    else:
        print("staged only; pass --commit to write the live skin directory")
    return 0


def add_ui_subcommands(parser: argparse.ArgumentParser) -> None:
    sub = parser.add_subparsers(dest="ui_command", required=True)

    p_spec = sub.add_parser("spec", help="show the Voidscape topbar UI asset spec")
    p_spec.add_argument("--spec", type=Path, default=UI_TOPBAR_SPEC)
    p_spec.add_argument("--json", action="store_true", help="print the full JSON spec")
    p_spec.add_argument("--out", type=Path, help="write the JSON spec to a path")
    p_spec.set_defaults(func=cmd_spec)

    p_validate = sub.add_parser("validate", help="validate topbar icon masters and variants")
    p_validate.add_argument("--spec", type=Path, default=UI_TOPBAR_SPEC)
    p_validate.add_argument("--skin-dir", type=Path, default=None)
    p_validate.add_argument("--runtime-only", action="store_true", help="check only icon sizes used by the client")
    p_validate.set_defaults(func=cmd_validate)

    p_preview = sub.add_parser("preview", help="render a topbar icon preview PNG")
    p_preview.add_argument("--spec", type=Path, default=UI_TOPBAR_SPEC)
    p_preview.add_argument("--skin-dir", type=Path, default=None)
    p_preview.add_argument("--fallback-skin-dir", type=Path, default=None)
    p_preview.add_argument("--out", type=Path, default=DEFAULT_PREVIEW)
    p_preview.add_argument("--active", default="stats", help="slot key to draw active")
    p_preview.set_defaults(func=cmd_preview)

    p_ingest = sub.add_parser("ingest-sheet", help="crop a concept sheet into topbar icons and baked variants")
    p_ingest.add_argument("sheet", type=Path)
    p_ingest.add_argument("--spec", type=Path, default=UI_TOPBAR_SPEC)
    p_ingest.add_argument("--cell-size", type=_parse_size, required=True, help="cell size, e.g. 1024 or 512x512")
    p_ingest.add_argument("--columns", type=int, required=True, help="number of sheet columns")
    p_ingest.add_argument("--start-index", type=int, default=None, help="first grid cell index for spec slot order")
    p_ingest.add_argument("--slots", default="", help="override slot cells, e.g. options=0,inventory=5")
    p_ingest.add_argument("--margin", type=int, default=0, help="outer sheet margin in pixels")
    p_ingest.add_argument("--gap", type=int, default=0, help="gap between cells in pixels")
    p_ingest.add_argument("--bg-key", default=None, help="transparent chroma key, e.g. #ff00ff")
    p_ingest.add_argument("--bg-tolerance", type=int, default=8, help="per-channel chroma key tolerance")
    p_ingest.add_argument("--trim", action=argparse.BooleanOptionalAction, default=True)
    p_ingest.add_argument("--output-dir", type=Path, default=None, help="write somewhere other than the default staging/live dir")
    p_ingest.add_argument("--commit", action="store_true", help="write directly to the live skin directory")
    p_ingest.add_argument("--dry-run", action="store_true", help="show output count without writing files")
    p_ingest.set_defaults(func=cmd_ingest_sheet)

    add_ui_concept_subcommands(sub)
