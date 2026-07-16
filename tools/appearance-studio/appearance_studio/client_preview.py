from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import struct
from typing import Any, Mapping, Sequence
import zipfile

from PIL import Image, ImageDraw

from .paths import REPO_ROOT
from .registry import safe_repo_path
from .template import PaperdollTemplate
from .workbench_report import expected_states


ANIM_DIR_LAYER_TO_CHAR_LAYER = (
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4),
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4),
    (11, 3, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4),
    (3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (4, 3, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (11, 4, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3),
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4, 3),
)
WALK_FRAMES = (0, 1, 2, 1)
CANVAS_SIZE = (88, 112)
DRAW_ORIGIN = (12, 2)
BASE_DRAW_SIZE = (64, 102)

HAIR_LUSTER_MASKS = frozenset((
    0x6A0DAD, 0x7EDAE6, 0xB2181E, 0xE66917, 0xDAAD36, 0x5BCC3D, 0xABBEDB, 0x3D3D48,
))
CLOTHING_LUSTER_MASKS = frozenset((
    0x4A2C6F, 0x5F8D94, 0x7E2B31, 0x92512E, 0xA07F3A, 0x4E7F3F, 0x7B869D, 0x34343B,
))
SKIN_LUSTER_MASKS = frozenset((0xF2C8BA, 0xD99A8F, 0xA76A45, 0x6D3F2C, 0x9B9290))


def _jdiv(value: int, divisor: int) -> int:
    """Java integer division, including truncation toward zero for negatives."""
    if divisor == 0:
        raise ZeroDivisionError
    sign = -1 if (value < 0) != (divisor < 0) else 1
    return sign * (abs(value) // abs(divisor))


@dataclass(frozen=True)
class SpriteFrame:
    image: Image.Image
    sidecar: Mapping[str, int | bool]


@dataclass(frozen=True)
class AnimationLayer:
    slot: int
    appearance_id: int
    name: str
    sprite_base: int
    char_colour: int
    blue_mask: int
    has_a: bool
    has_f: bool


@dataclass(frozen=True)
class CompositeFixture:
    path: Path
    digest: str
    oracle_report: Path
    oracle_report_digest: str
    archive: Path
    archive_digest: str
    layer_animation: tuple[int, ...]
    layers: dict[int, AnimationLayer]
    hair_colour: int
    top_colour: int
    bottom_colour: int
    skin_colour: int
    palette_indices: dict[str, int]
    background_rgb: int
    canvas_size: tuple[int, int]
    draw_origin: tuple[int, int]
    draw_size: tuple[int, int]
    top_pixel_skew: int


@dataclass(frozen=True)
class DrawTrace:
    slot: int
    appearance_id: int
    stored_offset: int
    x: int
    y: int
    width: int
    height: int
    mirror_x: bool


@dataclass(frozen=True)
class CompositeResult:
    image: Image.Image
    traces: tuple[DrawTrace, ...]


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_composite_fixture(oracle_report_path: Path, output_path: Path) -> dict[str, Any]:
    """Extract and bind state-invariant live renderer inputs from a Workbench oracle report."""
    report = json.loads(oracle_report_path.read_text())
    captures = report.get("captures")
    if not isinstance(captures, list) or len(captures) != 30:
        raise ValueError("composite fixture extraction requires a 30-capture Workbench oracle")
    first_inputs = captures[0]["renderInputs"]
    first_raster = captures[0]["isolatedRaster"]
    invariant_keys = (
        "x", "y", "width", "height", "topPixelSkew", "hairStyle", "invisible", "invulnerable",
        "paletteIndices", "resolvedRgb", "layerAnimation", "layers",
    )
    for index, capture in enumerate(captures):
        inputs = capture.get("renderInputs", {})
        if any(inputs.get(key) != first_inputs.get(key) for key in invariant_keys):
            raise ValueError(f"Workbench oracle render input {index} changes a fixture invariant")
        raster = capture.get("isolatedRaster", {})
        if (
            raster.get("width") != first_raster.get("width")
            or raster.get("height") != first_raster.get("height")
            or raster.get("backgroundRgb") != first_raster.get("backgroundRgb")
        ):
            raise ValueError(f"Workbench oracle raster {index} changes fixture geometry/background")
    flattened_layers = []
    for layer in first_inputs["layers"]:
        definition = layer.get("definition")
        if definition is None:
            continue
        flattened_layers.append({"slot": layer["slot"], "appearanceId": layer["appearanceId"], **definition})
    archive_path = Path(report["spriteArchive"]["path"]).resolve()
    try:
        archive_relative = archive_path.relative_to(REPO_ROOT.resolve())
        report_relative = oracle_report_path.resolve().relative_to(REPO_ROOT.resolve())
    except ValueError as exc:
        raise ValueError("Workbench oracle report and sprite archive must remain inside the repository") from exc
    fixture = {
        "schema": "voidscape-player-composite-fixture/v1",
        "oracleReport": str(report_relative),
        "oracleReportSha256": _sha256(oracle_report_path),
        "spriteArchive": {"path": str(archive_relative), "sha256": report["spriteArchive"]["sha256"]},
        "backgroundRgb": first_raster["backgroundRgb"],
        "draw": {
            "canvasWidth": first_raster["width"], "canvasHeight": first_raster["height"],
            "x": first_inputs["x"], "y": first_inputs["y"],
            "width": first_inputs["width"], "height": first_inputs["height"],
            "topPixelSkew": first_inputs["topPixelSkew"],
        },
        "hairStyle": first_inputs["hairStyle"],
        "invisible": first_inputs["invisible"],
        "invulnerable": first_inputs["invulnerable"],
        "paletteIndices": first_inputs["paletteIndices"],
        "resolvedPalette": first_inputs["resolvedRgb"],
        "layerAnimation": first_inputs["layerAnimation"],
        "layers": flattened_layers,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(fixture, indent=2, sort_keys=True) + "\n")
    return fixture


def load_composite_fixture(path: Path, *, repo_root: Path = REPO_ROOT) -> CompositeFixture:
    payload = json.loads(path.read_text())
    if payload.get("schema") != "voidscape-player-composite-fixture/v1":
        raise ValueError("unsupported player composite fixture")
    oracle_report = safe_repo_path(payload["oracleReport"], repo_root=repo_root)
    oracle_report_digest = _sha256(oracle_report)
    if oracle_report_digest != payload["oracleReportSha256"]:
        raise ValueError("player composite fixture oracle report digest changed")
    layer_animation = tuple(payload["layerAnimation"])
    if len(layer_animation) != 12 or any(isinstance(value, bool) or not isinstance(value, int) for value in layer_animation):
        raise ValueError("player composite fixture requires exactly 12 integer layerAnimation entries")
    archive = safe_repo_path(payload["spriteArchive"]["path"], repo_root=repo_root)
    archive_digest = _sha256(archive)
    if archive_digest != payload["spriteArchive"]["sha256"]:
        raise ValueError("player composite fixture sprite archive digest changed")
    layers: dict[int, AnimationLayer] = {}
    for item in payload["layers"]:
        slot = item["slot"]
        if slot in layers or not 0 <= slot < 12 or layer_animation[slot] != item["appearanceId"]:
            raise ValueError("player composite fixture layer slot/appearance mapping is inconsistent")
        layers[slot] = AnimationLayer(
            slot, item["appearanceId"], item["name"], item["spriteBase"], item["charColour"],
            item["blueMask"], item["hasA"], item["hasF"],
        )
    palette = payload["resolvedPalette"]
    draw = payload["draw"]
    if payload.get("hairStyle") != 0 or payload.get("invisible") is not False or payload.get("invulnerable") is not False:
        raise ValueError("offline compositor only accepts hairStyle=0, visible, non-invulnerable oracle fixtures")
    if draw.get("topPixelSkew") != 0:
        raise ValueError("offline compositor oracle fixture requires topPixelSkew=0")
    return CompositeFixture(
        path.resolve(), _sha256(path), oracle_report, oracle_report_digest,
        archive, archive_digest, layer_animation, layers,
        palette["hair"], palette["top"], palette["bottom"], palette["skin"],
        dict(payload["paletteIndices"]),
        payload["backgroundRgb"], (draw["canvasWidth"], draw["canvasHeight"]),
        (draw["x"], draw["y"]), (draw["width"], draw["height"]), draw["topPixelSkew"],
    )


def decode_sprite(data: bytes) -> SpriteFrame:
    if len(data) < 25:
        raise ValueError("sprite header is truncated")
    width, height, requires_shift, x_shift, y_shift, logical_width, logical_height = struct.unpack_from(
        ">iiBiiii", data, 0
    )
    count = width * height
    if width <= 0 or height <= 0 or len(data) != 25 + count * 4:
        raise ValueError("sprite payload geometry is invalid")
    source = data[25:]
    rgba = bytearray(count * 4)
    for index in range(0, len(source), 4):
        if source[index:index + 4] == b"\0\0\0\0":
            continue
        rgba[index:index + 4] = bytes((source[index + 1], source[index + 2], source[index + 3], 255))
    return SpriteFrame(Image.frombytes("RGBA", (width, height), bytes(rgba)), {
        "requiresShift": bool(requires_shift), "xShift": x_shift, "yShift": y_shift,
        "something1": logical_width, "something2": logical_height,
    })


class ArchiveSprites:
    def __init__(self, path: Path):
        self.path = path
        self._cache: dict[int, SpriteFrame] = {}

    def get(self, index: int) -> SpriteFrame:
        if index not in self._cache:
            with zipfile.ZipFile(self.path) as archive:
                try:
                    self._cache[index] = decode_sprite(archive.read(str(index)))
                except KeyError as exc:
                    raise ValueError(f"sprite archive entry {index} is missing") from exc
        return self._cache[index]


def _rgb(value: int) -> tuple[int, int, int]:
    return value >> 16 & 255, value >> 8 & 255, value & 255


def _shade_level(shade: int, family: str) -> int:
    if family == "hair":
        if shade in {16, 27, 76, 90, 103, 109, 156, 201} or shade < 120:
            return 0x5C
        if shade in {121, 130, 132, 135, 173, 232} or shade < 150:
            return 0x8A
        return 0xFF
    if family == "skin":
        return 0x76 if shade < 100 else 0xB0 if shade < 180 else 0xFF
    return 0x58 if shade < 80 else 0x72 if shade < 120 else 0x8C if shade < 155 else 0xA6 if shade < 205 else 0xBE


def _masked_colour(pixel: tuple[int, int, int, int], mask1: int, mask2: int, blue_mask: int,
                   *, two_masks: bool) -> tuple[int, int, int] | None:
    red, green, blue, alpha = pixel
    if alpha < 128:
        return None
    mask1 = mask1 or 0xFFFFFF
    mask2 = mask2 or 0xFFFFFF
    blue_mask = blue_mask or 0xFFFFFF
    if red == green == blue:
        if mask1 in HAIR_LUSTER_MASKS:
            shade = _shade_level(red, "hair")
            return tuple(channel * shade // 255 for channel in _rgb(mask1))
        if mask1 in CLOTHING_LUSTER_MASKS:
            shade = _shade_level(red, "clothing")
            return tuple(channel * shade // 255 for channel in _rgb(mask1))
        return tuple(red * channel >> 8 for channel in _rgb(mask1))
    if two_masks and red == 255 and green == blue:
        if mask2 in SKIN_LUSTER_MASKS:
            shade = _shade_level(green, "skin")
            return tuple(channel * shade // 255 for channel in _rgb(mask2))
        return tuple(component * channel >> 8 for component, channel in zip((red, green, blue), _rgb(mask2)))
    if blue_mask != 0xFFFFFF and red == green and blue != green:
        shifter = red * blue
        return tuple(channel * shifter >> 16 for channel in _rgb(blue_mask))
    return red, green, blue


def draw_sprite_clipping(
    canvas: Image.Image, frame: SpriteFrame, x: int, y: int, width: int, height: int,
    mask1: int, mask2: int, blue_mask: int = 0, *, mirror_x: bool = False,
    top_pixel_skew: int = 0, colour_transform: int = 0xFFFFFFFF,
) -> None:
    """Integer port of GraphicsController.drawSpriteClipping onto an opaque RGB target."""
    image = frame.image.convert("RGBA")
    sprite_width, sprite_height = image.size
    if min(sprite_width, sprite_height, width, height) <= 0:
        return
    mask1 = mask1 or 0xFFFFFF
    mask2 = mask2 or 0xFFFFFF
    two_masks = mask2 != 0xFFFFFF
    source_x = source_y = 0
    first_column = top_pixel_skew << 16
    scale_x = _jdiv(sprite_width << 16, width)
    scale_y = _jdiv(sprite_height << 16, height)
    skew_per_row = _jdiv(-(top_pixel_skew << 16), height)
    sidecar = frame.sidecar
    if sidecar.get("requiresShift"):
        logical_width = int(sidecar["something1"])
        logical_height = int(sidecar["something2"])
        if logical_width == 0 or logical_height == 0:
            return
        scale_x = _jdiv(logical_width << 16, width)
        scale_y = _jdiv(logical_height << 16, height)
        x_shift = int(sidecar["xShift"])
        if mirror_x:
            x_shift = logical_width - sprite_width - x_shift
        y_shift = int(sidecar["yShift"])
        x += _jdiv(logical_width + x_shift * width - 1, logical_width)
        y_delta = _jdiv(y_shift * height + logical_height - 1, logical_height)
        if (x_shift * width) % logical_width != 0:
            source_x = _jdiv((logical_width - (x_shift * width) % logical_width) << 16, width)
        y += y_delta
        first_column += y_delta * skew_per_row
        if (y_shift * height) % logical_height != 0:
            source_y = _jdiv((logical_height - (y_shift * height) % logical_height) << 16, height)
        width = _jdiv(scale_x + ((sprite_width << 16) - (source_x + 1)), scale_x)
        height = _jdiv((sprite_height << 16) - source_y - (1 - scale_y), scale_y)
    if width <= 0 or height <= 0:
        return

    source = image.load()
    target = canvas.load()
    transform_alpha = colour_transform >> 24 & 255
    inverse_alpha = (255 if two_masks else 256) - transform_alpha
    transform = _rgb(colour_transform)
    for row in range(height):
        target_y = y + row
        if not 0 <= target_y < canvas.height:
            continue
        target_start_x = (first_column + row * skew_per_row) >> 16
        source_row = (source_y + row * scale_y) >> 16
        if not 0 <= source_row < sprite_height:
            continue
        for column in range(width):
            target_x = x + target_start_x + column
            if not 0 <= target_x < canvas.width:
                continue
            if mirror_x:
                source_column = ((sprite_width << 16) - (source_x + 1) - column * scale_x) >> 16
            else:
                source_column = (source_x + column * scale_x) >> 16
            if not 0 <= source_column < sprite_width:
                continue
            colour = _masked_colour(
                source[source_column, source_row], mask1, mask2, blue_mask, two_masks=two_masks
            )
            if colour is None:
                continue
            previous = target[target_x, target_y]
            target[target_x, target_y] = tuple(
                (((component * tint) >> 8) * transform_alpha + old * inverse_alpha) >> 8
                for component, tint, old in zip(colour, transform, previous)
            )


def _layer_offset(layer: AnimationLayer, state: Mapping[str, Any]) -> tuple[int, int, int] | None:
    actual = state["actualAnimDir"]
    mirror = state["mirrorX"]
    mapped = layer.slot
    offset_x = offset_y = 0
    sprite_offset = state["spriteOffset"]
    if mirror and 1 <= actual <= 3:
        if layer.has_f:
            sprite_offset += 15
        elif mapped in {3, 4}:
            phase = state["frame"]
            sprite_offset = actual * 3 + WALK_FRAMES[(phase + 2) % 4]
            if mapped == 4:
                offset_x, offset_y = {1: (-22, -3), 2: (0, -8), 3: (26, -5)}[actual]
            else:
                offset_x, offset_y = {1: (22, 3), 2: (0, 8), 3: (-26, 5)}[actual]
    if actual == 5 and not layer.has_a:
        return None
    return sprite_offset, offset_x, offset_y


def render_composite(fixture: CompositeFixture, state: Mapping[str, Any]) -> CompositeResult:
    expected = expected_states()
    state_key = (state.get("kind"), state.get("direction"), state.get("frame"))
    canonical = next((item for item in expected if (item["kind"], item["direction"], item["frame"]) == state_key), None)
    if canonical is None or any(state.get(key) != value for key, value in canonical.items()):
        raise ValueError("offline preview state does not match the canonical 30-state contract")
    sprites = ArchiveSprites(fixture.archive)
    canvas = Image.new("RGB", fixture.canvas_size, _rgb(fixture.background_rgb))
    traces: list[DrawTrace] = []
    for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]]:
        layer = fixture.layers.get(slot)
        if layer is None:
            continue
        selection = _layer_offset(layer, state)
        if selection is None:
            continue
        sprite_offset, sprite_offset_x, sprite_offset_y = selection
        frame = sprites.get(layer.sprite_base + sprite_offset)
        frame0 = sprites.get(layer.sprite_base)
        logical_width = int(frame.sidecar["something1"])
        logical_height = int(frame.sidecar["something2"])
        frame0_width = int(frame0.sidecar["something1"])
        if not logical_width or not logical_height or not frame0_width:
            continue
        x_offset = _jdiv(sprite_offset_x * fixture.draw_size[0], logical_width)
        y_offset = _jdiv(sprite_offset_y * fixture.draw_size[1], logical_height)
        draw_width = _jdiv(logical_width * fixture.draw_size[0], frame0_width)
        x_offset -= _jdiv(draw_width - fixture.draw_size[0], 2)
        mask1 = layer.char_colour
        if mask1 == 1:
            mask1 = fixture.hair_colour
        elif mask1 == 2:
            mask1 = fixture.top_colour
        elif mask1 == 3:
            mask1 = fixture.bottom_colour
        draw_x, draw_y = fixture.draw_origin[0] + x_offset, fixture.draw_origin[1] + y_offset
        draw_sprite_clipping(
            canvas, frame, draw_x, draw_y, draw_width, fixture.draw_size[1], mask1,
            fixture.skin_colour, layer.blue_mask, mirror_x=state["mirrorX"],
            top_pixel_skew=fixture.top_pixel_skew,
        )
        traces.append(DrawTrace(slot, layer.appearance_id, sprite_offset, draw_x, draw_y, draw_width,
                                fixture.draw_size[1], state["mirrorX"]))
    return CompositeResult(canvas, tuple(traces))


def load_reference_frames(directory: Path) -> tuple[SpriteFrame, ...]:
    frames = []
    for offset in range(18):
        path = directory / f"frame_{offset:02d}.png"
        sidecar = json.loads(path.with_suffix(".png.json").read_text())
        frames.append(SpriteFrame(Image.open(path).convert("RGBA"), sidecar))
    return tuple(frames)


def render_canonical_stored_players(
    template: PaperdollTemplate, heads: Sequence[SpriteFrame], *,
    hair: int = 0x805030, top: int = 0x345C94, bottom: int = 0x5C442C, skin: int = 0xECDED0,
    background: int = 0,
) -> list[Image.Image]:
    """Render stored legacy frames with canonical head/body/legs and live transforms."""
    if len(heads) != 18:
        raise ValueError("stored player preview requires exactly 18 head frames")
    bodies = load_reference_frames(template.reference_dirs["body"])
    legs = load_reference_frames(template.reference_dirs["legs"])
    by_slot = {0: (tuple(heads), hair), 1: (bodies, top), 2: (legs, bottom)}
    output = []
    for offset in range(18):
        canvas = Image.new("RGB", CANVAS_SIZE, _rgb(background))
        wanted = 2 if offset >= 15 else offset // 3
        for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[wanted]:
            item = by_slot.get(slot)
            if item is None:
                continue
            frames, mask1 = item
            frame, frame0 = frames[offset], frames[0]
            logical_width = int(frame.sidecar["something1"])
            draw_width = _jdiv(logical_width * BASE_DRAW_SIZE[0], int(frame0.sidecar["something1"]))
            x = DRAW_ORIGIN[0] - _jdiv(draw_width - BASE_DRAW_SIZE[0], 2)
            draw_sprite_clipping(canvas, frame, x, DRAW_ORIGIN[1], draw_width, BASE_DRAW_SIZE[1], mask1, skin)
        output.append(canvas)
    return output


def contact_sheet(frames: Sequence[Image.Image], labels: Sequence[str], *, scale: int = 4,
                  columns: int = 6, padding: int = 4, label_height: int = 16) -> Image.Image:
    if len(frames) != len(labels) or not frames or scale <= 0 or columns <= 0:
        raise ValueError("contact sheet requires matching non-empty frames/labels and positive geometry")
    maximum_width = max(frame.width for frame in frames)
    maximum_height = max(frame.height for frame in frames)
    cell_width = maximum_width * scale + padding * 2
    cell_height = maximum_height * scale + padding * 2 + label_height
    rows = math.ceil(len(frames) / columns)
    sheet = Image.new("RGB", (columns * cell_width, rows * cell_height), (22, 25, 30))
    draw = ImageDraw.Draw(sheet)
    for index, (frame, label) in enumerate(zip(frames, labels)):
        cell_x = index % columns * cell_width
        cell_y = index // columns * cell_height
        scaled = frame.convert("RGB").resize((frame.width * scale, frame.height * scale), Image.Resampling.NEAREST)
        x = cell_x + padding + (maximum_width - frame.width) * scale // 2
        y = cell_y + padding + label_height + (maximum_height - frame.height) * scale // 2
        sheet.paste(scaled, (x, y))
        draw.text((cell_x + padding, cell_y + padding), label, fill=(240, 230, 205))
    return sheet


def state_contact_sheet(frames: Sequence[Image.Image], states: Sequence[Mapping[str, Any]], *,
                        scale: int = 4, padding: int = 4, label_height: int = 16) -> Image.Image:
    """Arrange the canonical 30 states as 8 walk columns × 3 rows plus combat row."""
    if len(frames) != 30 or len(states) != 30:
        raise ValueError("state contact sheet requires exactly 30 frames and states")
    walk_directions = [state["direction"] for state in states if state["kind"] == "walk" and state["frame"] == 0]
    if len(walk_directions) != 8:
        raise ValueError("state contact sheet requires eight walk directions")
    maximum_width = max(frame.width for frame in frames)
    maximum_height = max(frame.height for frame in frames)
    cell_width = maximum_width * scale + padding * 2
    cell_height = maximum_height * scale + padding * 2 + label_height
    sheet = Image.new("RGB", (8 * cell_width, 4 * cell_height), (22, 25, 30))
    draw = ImageDraw.Draw(sheet)
    for image, state in zip(frames, states):
        if state["kind"] == "walk":
            column, row = walk_directions.index(state["direction"]), state["frame"]
        else:
            column = (0 if state["direction"] == "combat-a" else 3) + state["frame"]
            row = 3
        cell_x, cell_y = column * cell_width, row * cell_height
        scaled = image.convert("RGB").resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST)
        x = cell_x + padding + (maximum_width - image.width) * scale // 2
        y = cell_y + padding + label_height + (maximum_height - image.height) * scale // 2
        sheet.paste(scaled, (x, y))
        draw.text((cell_x + padding, cell_y + padding), f"{state['direction']} {state['frame']}",
                  fill=(240, 230, 205))
    return sheet


def raw_rgb_sha256(image: Image.Image) -> str:
    return hashlib.sha256(image.convert("RGB").tobytes()).hexdigest()


def pixel_mismatch_count(left: Image.Image, right: Image.Image) -> int:
    left_rgb, right_rgb = left.convert("RGB"), right.convert("RGB")
    if left_rgb.size != right_rgb.size:
        raise ValueError(f"raster size mismatch: offline={left_rgb.size} oracle={right_rgb.size}")
    return sum(a != b for a, b in zip(left_rgb.getdata(), right_rgb.getdata()))


def _background_crop(image: Image.Image, background: int) -> dict[str, int]:
    rgb = image.convert("RGB")
    colour = _rgb(background)
    points = [
        (x, y) for y in range(rgb.height) for x in range(rgb.width)
        if rgb.getpixel((x, y)) != colour
    ]
    if not points:
        return {"x": 0, "y": 0, "width": 0, "height": 0}
    left, top = min(x for x, _ in points), min(y for _, y in points)
    right, bottom = max(x for x, _ in points) + 1, max(y for _, y in points) + 1
    return {"x": left, "y": top, "width": right - left, "height": bottom - top}


def write_oracle_comparison(
    fixture_path: Path, oracle_report_path: Path, output_root: Path, template: PaperdollTemplate,
    *, repo_root: Path = REPO_ROOT, fail_on_mismatch: bool = True,
) -> dict[str, Any]:
    """Render and compare all 30 states against isolated Java RGB rasters."""
    fixture = load_composite_fixture(fixture_path, repo_root=repo_root)
    if fixture.oracle_report != oracle_report_path.resolve() or fixture.oracle_report_digest != _sha256(oracle_report_path):
        raise ValueError("composite fixture is not digest-bound to this Workbench oracle report")
    oracle = json.loads(oracle_report_path.read_text())
    oracle_archive = Path(oracle["spriteArchive"]["path"]).resolve()
    if oracle_archive != fixture.archive or oracle["spriteArchive"]["sha256"] != fixture.archive_digest:
        raise ValueError("Workbench oracle sprite archive disagrees with digest-bound fixture")
    captures = oracle.get("captures")
    expected = expected_states()
    if not isinstance(captures, list) or len(captures) != len(expected):
        raise ValueError("oracle comparison requires exactly 30 captures")
    output_root = output_root.resolve()
    try:
        output_root.relative_to((repo_root / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("client preview evidence must remain under repository tmp/") from exc
    frames_dir = output_root / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    evidence: list[dict[str, Any]] = []
    rendered: list[Image.Image] = []
    mismatch_frames = mismatch_pixels = 0
    for index, (capture, state) in enumerate(zip(captures, expected)):
        if any(capture.get(key) != value for key, value in state.items()):
            raise ValueError(f"oracle capture {index} does not match canonical state metadata")
        render_inputs = capture.get("renderInputs", {})
        if any(render_inputs.get(key) != state[key] for key in ("wantedAnimDir", "actualAnimDir", "mirrorX", "spriteOffset")):
            raise ValueError(f"oracle capture {index} renderInputs disagree with state metadata")
        if render_inputs.get("stepFrame") != (state["frame"] * 6 if state["kind"] == "walk" else 0):
            raise ValueError(f"oracle capture {index} has unexpected stepFrame")
        if render_inputs.get("layerOrder") != list(ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]]):
            raise ValueError(f"oracle capture {index} layerOrder disagrees with live direction matrix")
        if (
            render_inputs.get("layerAnimation") != list(fixture.layer_animation)
            or render_inputs.get("resolvedRgb") != {
                "hair": fixture.hair_colour, "top": fixture.top_colour,
                "bottom": fixture.bottom_colour, "skin": fixture.skin_colour,
            }
            or render_inputs.get("paletteIndices") != fixture.palette_indices
            or render_inputs.get("hairStyle") != 0
            or render_inputs.get("invisible") is not False
            or render_inputs.get("invulnerable") is not False
            or (render_inputs.get("x"), render_inputs.get("y")) != fixture.draw_origin
            or (render_inputs.get("width"), render_inputs.get("height")) != fixture.draw_size
            or render_inputs.get("topPixelSkew") != fixture.top_pixel_skew
            or render_inputs.get("overlayMovement") != 0
        ):
            raise ValueError(f"oracle capture {index} disagrees with digest-bound composite fixture")
        flattened = []
        for item in render_inputs.get("layers", []):
            definition = item.get("definition")
            if definition is not None:
                flattened.append(AnimationLayer(
                    item["slot"], item["appearanceId"], definition["name"], definition["spriteBase"],
                    definition["charColour"], definition["blueMask"], definition["hasA"], definition["hasF"],
                ))
        if {item.slot: item for item in flattened} != fixture.layers:
            raise ValueError(f"oracle capture {index} resolved layer definitions disagree with fixture")
        result = render_composite(fixture, state)
        filename = f"{index:02d}-{state['kind']}-{state['direction']}-{state['frame']}.png"
        path = frames_dir / filename
        result.image.save(path, format="PNG", optimize=False)
        isolated = capture["isolatedRaster"]
        if (
            (isolated.get("width"), isolated.get("height")) != fixture.canvas_size
            or isolated.get("backgroundRgb") != fixture.background_rgb
        ):
            raise ValueError(f"oracle capture {index} raster geometry/background disagrees with fixture")
        oracle_image = Image.open(isolated["pngPath"]).convert("RGB")
        mismatches = pixel_mismatch_count(result.image, oracle_image)
        mismatch_pixels += mismatches
        mismatch_frames += int(mismatches > 0)
        stored = state["spriteOffset"]
        master = template.frames[stored].master
        evidence.append({
            **state,
            "visualPose": template.pose_profiles[master].visual_pose,
            "logicalWidth": 84 if state["kind"] == "combat" else 64,
            "pngPath": str(path.relative_to(output_root)),
            "pngSha256": _sha256(path),
            "rawRgbSha256": raw_rgb_sha256(result.image),
            "oracleRawRgbSha256": isolated["rawRgbSha256"],
            "mismatchedPixels": mismatches,
            "canvas": {
                "width": fixture.canvas_size[0], "height": fixture.canvas_size[1],
                "drawX": fixture.draw_origin[0], "drawY": fixture.draw_origin[1],
            },
            "crop": dict(isolated["crop"]),
            "supplementalOfflineCrop": _background_crop(result.image, fixture.background_rgb),
            "renderInputsSha256": hashlib.sha256(
                json.dumps(render_inputs, sort_keys=True, separators=(",", ":")).encode()
            ).hexdigest(),
        })
        rendered.append(result.image)
    sheet_path = output_root / "contact-sheet.png"
    state_contact_sheet(rendered, expected).save(sheet_path)
    report = {
        "schema": "voidscape-client-preview-evidence/v1",
        "frameCount": 30,
        "fixturePath": str(fixture_path.resolve()),
        "fixtureSha256": fixture.digest,
        "oracleReport": str(oracle_report_path.resolve()),
        "oracleReportSha256": _sha256(oracle_report_path),
        "spriteArchive": {"path": str(fixture.archive), "sha256": fixture.archive_digest},
        "template": template.key,
        "templateReferenceDigests": dict(template.reference_digests),
        "backgroundRgb": fixture.background_rgb,
        "mismatchedFrames": mismatch_frames,
        "mismatchedPixels": mismatch_pixels,
        "valid": mismatch_frames == 0 and mismatch_pixels == 0,
        "contactSheet": {"path": str(sheet_path.relative_to(output_root)), "sha256": _sha256(sheet_path)},
        "captures": evidence,
    }
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "manifest.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    if fail_on_mismatch and not report["valid"]:
        raise ValueError(
            f"client preview differs from Java oracle in {mismatch_frames} frames / {mismatch_pixels} pixels"
        )
    return report


__all__ = [
    "ANIM_DIR_LAYER_TO_CHAR_LAYER", "AnimationLayer", "ArchiveSprites", "CompositeFixture",
    "CompositeResult", "DrawTrace", "SpriteFrame", "contact_sheet", "decode_sprite",
    "draw_sprite_clipping", "extract_composite_fixture", "load_composite_fixture", "pixel_mismatch_count", "raw_rgb_sha256",
    "load_reference_frames", "render_canonical_stored_players", "render_composite", "state_contact_sheet",
    "write_oracle_comparison",
]
