#!/usr/bin/env python3
"""Pack PNG(s) into an RSC sprite archive (.orsc ZIP) at given numeric indices.

Encodes per Sprite.unpack format (see extract_ref.py docstring). Header metadata
defaults to zeros unless a sidecar JSON is provided (e.g. from extract_ref.py).

Default mode is dry-run: writes <archive>.new.orsc. --commit replaces the original
atomically; requires <archive>.bak to already exist as a safety check.
"""
from __future__ import annotations

import argparse
import json
import os
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image

HEADER_FMT = '>iiBiiii'
HEADER_SIZE = 25
assert struct.calcsize(HEADER_FMT) == HEADER_SIZE


def encode(img: Image.Image, sidecar: dict | None = None) -> bytes:
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    width, height = img.size
    sc = sidecar or {}
    header = struct.pack(
        HEADER_FMT,
        width, height,
        1 if sc.get('requiresShift') else 0,
        sc.get('xShift', 0), sc.get('yShift', 0),
        sc.get('something1', 0), sc.get('something2', 0),
    )

    # On-disk format: int32 BE per pixel where the high byte is unused (always 0)
    # and value 0 means **transparent** (no alpha channel — magic-value convention).
    # PIL RGBA bytes are [R, G, B, A]. We map alpha < 128 -> 0 (transparent),
    # otherwise -> [0, R, G, B] with the high byte forced to 0. If a fully-opaque
    # pixel happens to be RGB=(0,0,0), bump it to (1,0,0) so it doesn't collide
    # with the transparency sentinel.
    rgba = img.tobytes()
    pixel_bytes = len(rgba)
    argb = bytearray(pixel_bytes)
    for i in range(0, pixel_bytes, 4):
        a = rgba[i + 3]
        if a < 128:
            argb[i] = argb[i + 1] = argb[i + 2] = argb[i + 3] = 0
        else:
            r, g, b = rgba[i], rgba[i + 1], rgba[i + 2]
            if r == 0 and g == 0 and b == 0:
                r = 1  # avoid collision with the all-zero "transparent" sentinel
            argb[i]     = 0  # high byte unused
            argb[i + 1] = r
            argb[i + 2] = g
            argb[i + 3] = b

    return header + bytes(argb)


def rewrite_archive(src: Path, dst: Path, replacements: dict[str, bytes]) -> None:
    """Copy src ZIP to dst, replacing/inserting entries from `replacements` (keyed by entry name)."""
    seen = set()
    with zipfile.ZipFile(src, 'r') as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename in replacements:
                zout.writestr(info.filename, replacements[info.filename])
                seen.add(info.filename)
            else:
                zout.writestr(info, zin.read(info.filename))
        for name, data in replacements.items():
            if name not in seen:
                zout.writestr(name, data)


def parse_sidecar_arg(arg: str | None) -> dict | None:
    if not arg:
        return None
    return json.loads(Path(arg).read_text())


def main() -> int:
    ap = argparse.ArgumentParser(description='Pack PNG(s) into an RSC .orsc archive.')
    ap.add_argument('--archive', required=True, type=Path, help='target archive path')
    ap.add_argument('--start-index', required=True, type=int, help='first entry index; subsequent inputs use start+1, start+2, ...')
    ap.add_argument('--inputs', required=True, help='comma-separated PNG paths')
    ap.add_argument('--sidecar', help='shared sidecar JSON for header metadata (per-input sidecars supported via <input>.json convention)')
    ap.add_argument('--commit', action='store_true', help='atomically replace the archive (default: dry-run, write <archive>.new.orsc)')
    args = ap.parse_args()

    inputs = [Path(p.strip()) for p in args.inputs.split(',') if p.strip()]
    if not inputs:
        print('error: no inputs', file=sys.stderr)
        return 2
    for p in inputs:
        if not p.exists():
            print(f'error: input not found: {p}', file=sys.stderr)
            return 1
    if not args.archive.exists():
        print(f'error: archive not found: {args.archive}', file=sys.stderr)
        return 1

    shared_sidecar = parse_sidecar_arg(args.sidecar)
    replacements: dict[str, bytes] = {}
    for offset, png_path in enumerate(inputs):
        index = args.start_index + offset
        per_sidecar_path = png_path.with_suffix(png_path.suffix + '.json')
        per_sidecar = parse_sidecar_arg(str(per_sidecar_path)) if per_sidecar_path.exists() else None
        sidecar = per_sidecar if per_sidecar is not None else shared_sidecar
        with Image.open(png_path) as img:
            replacements[str(index)] = encode(img, sidecar)
        print(f'  encoded {png_path} -> entry "{index}" ({img.width}x{img.height}, sidecar={"yes" if sidecar else "defaults"})')

    if args.commit:
        bak = args.archive.with_suffix(args.archive.suffix + '.bak')
        if not bak.exists():
            print(f'refusing to commit: backup not found at {bak}', file=sys.stderr)
            print(f'create one first: cp {args.archive} {bak}', file=sys.stderr)
            return 1
        # Write to temp file in same dir, then atomic-rename
        fd, tmp_name = tempfile.mkstemp(suffix='.orsc', dir=str(args.archive.parent))
        os.close(fd)
        tmp = Path(tmp_name)
        try:
            rewrite_archive(args.archive, tmp, replacements)
            os.replace(tmp, args.archive)
        except Exception:
            tmp.unlink(missing_ok=True)
            raise
        print(f'committed to {args.archive}')
    else:
        out = args.archive.with_suffix(args.archive.suffix + '.new')
        rewrite_archive(args.archive, out, replacements)
        print(f'dry-run: wrote {out} (use --commit to replace original)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
