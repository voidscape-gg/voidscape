from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any, Mapping, Sequence

from PIL import Image, ImageDraw

from .client_preview import ANIM_DIR_LAYER_TO_CHAR_LAYER, state_contact_sheet
from .v2_compiler import V2CompileResult, V2CompiledFrame
from .v2_workspace import DEFAULT_TINTS
from .workbench_report import expected_states


def _rgb(value: int) -> tuple[int, int, int]:
    return value >> 16 & 255, value >> 8 & 255, value & 255


def _mix_alpha(destination: int, colour: int, alpha: int) -> int:
    if alpha >= 256:
        return colour
    inverse = 256 - alpha
    red = ((destination >> 16 & 255) * inverse + (colour >> 16 & 255) * alpha) >> 8
    green = ((destination >> 8 & 255) * inverse + (colour >> 8 & 255) * alpha) >> 8
    blue = ((destination & 255) * inverse + (colour & 255) * alpha) >> 8
    return red << 16 | green << 8 | blue


def draw_argb_sprite_clipping(
    target: Image.Image,
    frame: V2CompiledFrame,
    x: int,
    y: int,
    width: int,
    height: int,
    *,
    mirror_x: bool,
    top_pixel_skew: int = 0,
    colour_transform: int = 0xFFFFFFFF,
) -> None:
    """Integer-for-integer port of GraphicsController.drawArgbSpriteClipping."""
    sprite = frame.cropped.convert("RGBA")
    sprite_width, sprite_height = sprite.size
    if min(sprite_width, sprite_height, width, height) <= 0:
        return
    source_start_x = source_start_y = 0
    destination_first_column = top_pixel_skew << 16
    scale_x = (sprite_width << 16) // width
    scale_y = (sprite_height << 16) // height
    skew_per_row = -(top_pixel_skew << 16) // height
    sidecar = frame.sidecar
    if sidecar.requires_shift:
        logical_width, logical_height = sidecar.logical_width, sidecar.logical_height
        if not logical_width or not logical_height:
            return
        scale_x = (logical_width << 16) // width
        scale_y = (logical_height << 16) // height
        x_shift = sidecar.x_shift
        if mirror_x:
            x_shift = logical_width - sprite_width - x_shift
        y_shift = sidecar.y_shift
        x += (logical_width + x_shift * width - 1) // logical_width
        y_delta = (y_shift * height + logical_height - 1) // logical_height
        y += y_delta
        destination_first_column += y_delta * skew_per_row
        if x_shift * width % logical_width:
            source_start_x = ((logical_width - width * x_shift % logical_width) << 16) // width
        if y_shift * height % logical_height:
            source_start_y = ((logical_height - height * y_shift % logical_height) << 16) // height
        width = (scale_x + ((sprite_width << 16) - (source_start_x + 1))) // scale_x
        height = ((sprite_height << 16) - source_start_y - (1 - scale_y)) // scale_y
    if width <= 0 or height <= 0:
        return

    opacity = colour_transform >> 24 & 255
    transform_red, transform_green, transform_blue = _rgb(colour_transform)
    source = sprite.load()
    destination = target.load()
    destination_first_column += x << 16
    for row in range(height):
        destination_y = y + row
        if not 0 <= destination_y < target.height:
            continue
        source_y = (source_start_y + row * scale_y) >> 16
        if not 0 <= source_y < sprite_height:
            continue
        destination_x_fixed = destination_first_column + row * skew_per_row
        source_x_fixed = ((sprite_width << 16) - source_start_x - 1) if mirror_x else source_start_x
        source_x_step = -scale_x if mirror_x else scale_x
        for column in range(width):
            destination_x = (destination_x_fixed >> 16) + column
            source_x = source_x_fixed >> 16
            source_x_fixed += source_x_step
            if not 0 <= destination_x < target.width or not 0 <= source_x < sprite_width:
                continue
            red, green, blue, alpha = source[source_x, source_y]
            if alpha == 0:
                continue
            colour = red << 16 | green << 8 | blue
            if opacity < 255 or colour_transform != 0xFFFFFFFF:
                alpha = alpha * opacity // 255
                colour = (
                    ((red * transform_red // 255) << 16)
                    | ((green * transform_green // 255) << 8)
                    | (blue * transform_blue // 255)
                )
            if alpha <= 0:
                continue
            old_red, old_green, old_blue = destination[destination_x, destination_y]
            old = old_red << 16 | old_green << 8 | old_blue
            blended = colour if alpha >= 250 else _mix_alpha(old, colour, alpha + 1)
            destination[destination_x, destination_y] = _rgb(blended)


def _canonical_state(state: Mapping[str, Any]) -> Mapping[str, Any]:
    key = state.get("kind"), state.get("direction"), state.get("frame")
    canonical = next((item for item in expected_states()
                      if (item["kind"], item["direction"], item["frame"]) == key), None)
    if canonical is None or any(state.get(name) != value for name, value in canonical.items()):
        raise ValueError("Paperdoll V2 preview state differs from the canonical 30-state contract")
    return canonical


def render_v2_stack(
    result: V2CompileResult,
    stack_id: str,
    state: Mapping[str, Any],
    *,
    tints: Mapping[str, int] = DEFAULT_TINTS,
    v2_only: bool = False,
    paperdoll_slots: Sequence[int] | None = None,
) -> Image.Image:
    canonical = _canonical_state(state)
    stack = next((item for item in result.manifest["renderStacks"] if item["id"] == stack_id), None)
    if stack is None:
        raise ValueError(f"unknown Paperdoll V2 render stack {stack_id!r}")
    if set(tints) != set(DEFAULT_TINTS) or any(
            isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 0xFFFFFF
            for value in tints.values()):
        raise ValueError("Paperdoll V2 tint set must provide every exact 24-bit role")
    preview = result.manifest["preview"]
    if v2_only and paperdoll_slots is not None:
        raise ValueError("Paperdoll V2 preview cannot combine v2_only with explicit slots")
    included_slots = ({0, 5} if v2_only else None) if paperdoll_slots is None else set(paperdoll_slots)
    if included_slots is not None and (not included_slots or any(
            isinstance(slot, bool) or not isinstance(slot, int) or not 0 <= slot <= 11
            for slot in included_slots)):
        raise ValueError("Paperdoll V2 preview slots must be unique integers from 0 through 11")
    canvas = Image.new("RGB", (preview["width"], preview["height"]), _rgb(preview["backgroundRgb"]))
    assets = [result.asset(asset_id) for asset_id in stack["assets"]]
    by_slot = {slot: [asset for asset in assets if asset.paperdoll_slot == slot] for slot in range(12)}
    for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[canonical["wantedAnimDir"]]:
        if included_slots is not None and slot not in included_slots:
            continue
        for asset in by_slot[slot]:
            for channel in asset.channels:
                frame = channel.frames[canonical["spriteOffset"]]
                frame_zero = channel.frames[0]
                logical_width = frame.sidecar.logical_width
                frame_zero_width = frame_zero.sidecar.logical_width
                draw_width = logical_width * preview["drawWidth"] // frame_zero_width
                draw_x = preview["drawX"] - (draw_width - preview["drawWidth"]) // 2
                transform = 0xFFFFFFFF if channel.tint_role == "fixed" else 0xFF000000 | tints[channel.tint_role]
                draw_argb_sprite_clipping(
                    canvas, frame, draw_x, preview["drawY"], draw_width, preview["drawHeight"],
                    mirror_x=bool(canonical["mirrorX"]), colour_transform=transform,
                )
    return canvas


def overlap_pixels(result: V2CompileResult, left_asset: str, right_asset: str, offset: int) -> int:
    def mask(asset_id: str) -> Image.Image:
        asset = result.asset(asset_id)
        size = result.template.frames[offset].size
        combined = Image.new("1", size, 0)
        for channel in asset.channels:
            alpha = channel.frames[offset].canvas.getchannel("A").point(lambda value: 255 if value else 0).convert("1")
            # logical OR for 1-bit images
            combined = Image.frombytes("1", size, bytes(a | b for a, b in
                                                         zip(combined.tobytes(), alpha.tobytes())))
        return combined
    left, right = mask(left_asset), mask(right_asset)
    return sum(bin(a & b).count("1") for a, b in zip(left.tobytes(), right.tobytes()))


def tint_diagnostic_sets() -> dict[str, dict[str, int]]:
    default = dict(DEFAULT_TINTS)
    cool = {**default, "hair": 0x486A9A, "facial-hair": 0x486A9A, "primary": 0x315F96}
    vivid = {**default, "hair": 0xB85C2C, "facial-hair": 0xB85C2C, "primary": 0x244C86,
             "secondary": 0xF2D04F}
    return {"default": default, "cool": cool, "vivid": vivid}


def comparison_panel(control: Image.Image, native: Image.Image) -> Image.Image:
    one_x = control.resize((88, 112), Image.Resampling.NEAREST)
    one_x_upscaled = one_x.resize((176, 224), Image.Resampling.NEAREST)
    panel = Image.new("RGB", (352, 244), (22, 25, 30))
    panel.paste(one_x_upscaled, (0, 20))
    panel.paste(native.convert("RGB"), (176, 20))
    draw = ImageDraw.Draw(panel)
    draw.text((4, 4), "1x legacy control (nearest 2x)", fill=(240, 230, 205))
    draw.text((180, 4), "native 2x", fill=(240, 230, 205))
    return panel


def raw_rgb_sha256(image: Image.Image) -> str:
    return hashlib.sha256(image.convert("RGB").tobytes()).hexdigest()


__all__ = [
    "comparison_panel", "draw_argb_sprite_clipping", "overlap_pixels", "raw_rgb_sha256",
    "render_v2_stack", "tint_diagnostic_sets",
]
