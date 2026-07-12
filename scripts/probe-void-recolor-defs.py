#!/usr/bin/env python3
"""Emit a reversible patch that sweeps entity definition colours toward Voidscape.

This is intentionally a probe: by default it does not edit source files. It scans
EntityHandler.java for NPCDef and AnimationDef constructor literals, shifts tint
colours toward the void palette while preserving luminance, and writes a unified
diff that can be applied with `git apply` or reversed with `git apply -R`.
"""

from __future__ import annotations

import argparse
import colorsys
import difflib
from pathlib import Path
from typing import Callable


REPO_ROOT = Path(__file__).resolve().parents[1]
ENTITY_HANDLER = REPO_ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
DEFAULT_PATCH = REPO_ROOT / "tmp/void-recolor/void-entity-recolor.patch"

VOID_STOPS = (
    (32, 12, 50),
    (88, 56, 76),
    (207, 187, 119),
)


def split_args(text: str) -> list[str]:
    args: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for i, ch in enumerate(text):
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in ("'", '"'):
            quote = ch
        elif ch in "({[":
            depth += 1
        elif ch in ")}]":
            depth -= 1
        elif ch == "," and depth == 0:
            args.append(text[start:i].strip())
            start = i + 1
    args.append(text[start:].strip())
    return args


def replace_constructor_args(line: str, ctor: str, mutate: Callable[[list[str]], bool]) -> tuple[str, bool]:
    marker = f"new {ctor}("
    cursor = 0
    changed = False
    out = []
    while True:
        pos = line.find(marker, cursor)
        if pos == -1:
            out.append(line[cursor:])
            return "".join(out), changed

        args_start = pos + len(marker)
        depth = 1
        quote: str | None = None
        escaped = False
        i = args_start
        while i < len(line):
            ch = line[i]
            if quote:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == quote:
                    quote = None
            elif ch in ("'", '"'):
                quote = ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        if i >= len(line):
            out.append(line[cursor:])
            return "".join(out), changed

        raw_args = line[args_start:i]
        args = split_args(raw_args)
        if mutate(args):
            changed = True
            out.append(line[cursor:args_start])
            out.append(", ".join(args))
            cursor = i
        else:
            out.append(line[cursor:i])
            cursor = i


def mutate_npc_args(args: list[str]) -> bool:
    try:
        sprite_index = args.index("sprites")
    except ValueError:
        return False
    changed = False
    for index in range(sprite_index + 1, min(sprite_index + 5, len(args))):
        new_value = recolor_literal(args[index])
        if new_value != args[index]:
            args[index] = new_value
            changed = True
    return changed


def mutate_animation_args(args: list[str]) -> bool:
    if len(args) < 7:
        return False
    changed = False
    for index in (2, 3 if len(args) >= 8 else -1):
        if index < 0:
            continue
        new_value = recolor_literal(args[index])
        if new_value != args[index]:
            args[index] = new_value
            changed = True
    return changed


def recolor_literal(value: str) -> str:
    stripped = value.strip()
    suffix = ""
    if stripped.endswith(("L", "l")):
        stripped, suffix = stripped[:-1], stripped[-1]
    try:
        if stripped.lower().startswith("0x"):
            numeric = int(stripped, 16)
            style = "hex"
        else:
            numeric = int(stripped, 10)
            style = "dec"
    except ValueError:
        return value

    # Keep sentinel mask selectors and empty/default tints intact.
    if numeric <= 3:
        return value
    shifted = shift_to_void(numeric)
    if shifted == numeric:
        return value
    if style == "hex":
        return f"0x{shifted:06x}{suffix}"
    return f"{shifted}{suffix}"


def shift_to_void(rgb_int: int) -> int:
    red = (rgb_int >> 16) & 0xFF
    green = (rgb_int >> 8) & 0xFF
    blue = rgb_int & 0xFF
    luma = luminance(red, green, blue)
    target = void_colour_at_luma(luma)
    target_luma = max(1.0, luminance(*target))
    scale = luma / target_luma
    scaled = tuple(clamp(round(channel * scale)) for channel in target)

    # Keep a little of the original saturation/value so the pass reads as a
    # hue sweep rather than a flat two-colour posterization.
    _, original_l, original_s = colorsys.rgb_to_hls(red / 255, green / 255, blue / 255)
    target_h, _, target_s = colorsys.rgb_to_hls(scaled[0] / 255, scaled[1] / 255, scaled[2] / 255)
    mixed_s = max(target_s, original_s * 0.35)
    final = colorsys.hls_to_rgb(target_h, original_l, min(1.0, mixed_s))
    return (clamp(round(final[0] * 255)) << 16) | (clamp(round(final[1] * 255)) << 8) | clamp(round(final[2] * 255))


def void_colour_at_luma(luma: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, luma / 255.0))
    if t < 0.55:
        return lerp(VOID_STOPS[0], VOID_STOPS[1], t / 0.55)
    return lerp(VOID_STOPS[1], VOID_STOPS[2], (t - 0.55) / 0.45)


def lerp(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(clamp(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def luminance(red: int, green: int, blue: int) -> float:
    return red * 0.2126 + green * 0.7152 + blue * 0.0722


def clamp(value: int | float) -> int:
    return max(0, min(255, int(value)))


def make_recolor_patch(source: Path) -> tuple[str, int, int]:
    original = source.read_text().splitlines(keepends=True)
    edited: list[str] = []
    npc_changes = 0
    animation_changes = 0

    for line in original:
        new_line, changed_npc = replace_constructor_args(line, "NPCDef", mutate_npc_args)
        new_line, changed_animation = replace_constructor_args(new_line, "AnimationDef", mutate_animation_args)
        edited.append(new_line)
        if changed_npc:
            npc_changes += 1
        if changed_animation:
            animation_changes += 1

    diff = "".join(difflib.unified_diff(
        original,
        edited,
        fromfile=str(source.relative_to(REPO_ROOT)),
        tofile=str(source.relative_to(REPO_ROOT)),
    ))
    return diff, npc_changes, animation_changes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ENTITY_HANDLER)
    parser.add_argument("--out", type=Path, default=DEFAULT_PATCH)
    args = parser.parse_args()

    source = args.source if args.source.is_absolute() else REPO_ROOT / args.source
    out = args.out if args.out.is_absolute() else REPO_ROOT / args.out
    diff, npc_changes, animation_changes = make_recolor_patch(source)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(diff)
    print(f"wrote {out}")
    print(f"NPCDef rows changed: {npc_changes}")
    print(f"AnimationDef rows changed: {animation_changes}")
    print("apply with: git apply " + str(out.relative_to(REPO_ROOT)))
    print("reverse with: git apply -R " + str(out.relative_to(REPO_ROOT)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
