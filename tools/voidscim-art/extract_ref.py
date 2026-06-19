#!/usr/bin/env python3
"""Extract a sprite from an RSC sprite archive (ZIP) to PNG + sidecar JSON.

Sprite binary format (Client_Base/src/com/openrsc/client/model/Sprite.java):
  25-byte big-endian header:
    width (i32), height (i32), requiresShift (u8),
    xShift (i32), yShift (i32), something1 (i32), something2 (i32)
  pixel data:
    width * height int32, big-endian, ARGB packed: (A<<24)|(R<<16)|(G<<8)|B
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path

from PIL import Image

HEADER_FMT = '>iiBiiii'
HEADER_SIZE = 25
assert struct.calcsize(HEADER_FMT) == HEADER_SIZE


def decode(data: bytes) -> tuple[Image.Image, dict]:
    if len(data) < HEADER_SIZE:
        raise ValueError(f'sprite too short: {len(data)} bytes')
    width, height, req_shift, x_shift, y_shift, s1, s2 = struct.unpack_from(HEADER_FMT, data, 0)
    n_pixels = width * height
    pixel_bytes = n_pixels * 4
    if len(data) < HEADER_SIZE + pixel_bytes:
        raise ValueError(f'sprite truncated: header says {width}x{height}={n_pixels}px, '
                         f'need {HEADER_SIZE + pixel_bytes} bytes, got {len(data)}')

    # On disk: each int32 BE is the pixel value. High byte is unused (always 0);
    # **transparency is signaled by the int value 0**, not by the alpha byte.
    # All non-zero values are opaque RGB.
    src = data[HEADER_SIZE:HEADER_SIZE + pixel_bytes]
    rgba = bytearray(pixel_bytes)
    for i in range(0, pixel_bytes, 4):
        if src[i] == 0 and src[i + 1] == 0 and src[i + 2] == 0 and src[i + 3] == 0:
            # Fully transparent
            rgba[i] = rgba[i + 1] = rgba[i + 2] = rgba[i + 3] = 0
        else:
            rgba[i]     = src[i + 1]  # R
            rgba[i + 1] = src[i + 2]  # G
            rgba[i + 2] = src[i + 3]  # B
            rgba[i + 3] = 255          # A (opaque)

    img = Image.frombytes('RGBA', (width, height), bytes(rgba))
    sidecar = {
        'width': width, 'height': height,
        'requiresShift': bool(req_shift),
        'xShift': x_shift, 'yShift': y_shift,
        'something1': s1, 'something2': s2,
    }
    return img, sidecar


def main() -> int:
    ap = argparse.ArgumentParser(description='Extract a sprite from an RSC .orsc ZIP archive.')
    ap.add_argument('--archive', required=True, type=Path)
    ap.add_argument('--index', type=int, help='entry index (entry name = str(index))')
    ap.add_argument('--out', type=Path, help='output PNG path')
    ap.add_argument('--sidecar', type=Path, help='output sidecar JSON (default: <out>.json)')
    ap.add_argument('--list', action='store_true', help='list archive entries and exit')
    args = ap.parse_args()

    with zipfile.ZipFile(args.archive, 'r') as zf:
        if args.list:
            names = sorted(zf.namelist(), key=lambda n: int(n) if n.isdigit() else -1)
            print(f'{len(names)} entries in {args.archive}')
            print(f'first: {names[0]}  last: {names[-1]}')
            return 0
        if args.index is None or args.out is None:
            print('error: --index and --out are required (unless --list)', file=sys.stderr)
            return 2
        try:
            data = zf.read(str(args.index))
        except KeyError:
            print(f'error: index {args.index} not found in archive', file=sys.stderr)
            return 1

    img, sidecar = decode(data)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    img.save(args.out)
    sidecar_path = args.sidecar or args.out.with_suffix(args.out.suffix + '.json')
    sidecar_path.write_text(json.dumps(sidecar, indent=2))
    print(f'extracted index {args.index}: {sidecar["width"]}x{sidecar["height"]} -> {args.out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
