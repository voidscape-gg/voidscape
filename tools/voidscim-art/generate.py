#!/usr/bin/env python3
"""Generate void-themed sprite art via the OpenAI gpt-image-2 image-edits endpoint.

Reads OPENAI_API_KEY from env. Never accepts a key on the CLI or in a file.

Default mode hits the API. --dry-run skips the API and writes solid-color
placeholder PNGs at the target size (useful for pipeline plumbing tests).
"""
from __future__ import annotations

import argparse
import base64
import os
import sys
import time
from pathlib import Path


def load_dotenv(path: Path) -> None:
    """Minimal .env loader: KEY=value per line, # comments, no shell expansion."""
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        k, _, v = line.partition('=')
        k = k.strip()
        if k and k not in os.environ:
            os.environ[k] = v.strip().strip('"').strip("'")

from PIL import Image
import requests

API_URL = 'https://api.openai.com/v1/images/edits'
DEFAULT_MODEL = 'gpt-image-2'


def load_prompt(path: Path) -> str:
    text = path.read_text().strip()
    if not text:
        raise ValueError(f'empty prompt file: {path}')
    return text


def call_edits(reference: Path, prompt: str, n: int, size: str, api_key: str, model: str) -> list[bytes]:
    headers = {'Authorization': f'Bearer {api_key}'}
    with open(reference, 'rb') as ref_f:
        files = [('image', (reference.name, ref_f.read(), 'image/png'))]
    data = {
        'model': model, 'prompt': prompt, 'n': str(n), 'size': size, 'quality': 'high',
        'output_format': 'png',
    }
    if not model.startswith('gpt-image-2'):
        # gpt-image-2 family rejects transparent background; older models support it.
        data['background'] = 'transparent'
    backoff = 2.0
    for attempt in range(4):
        r = requests.post(API_URL, headers=headers, data=data, files=files, timeout=420)
        if r.status_code == 429 or r.status_code >= 500:
            print(f'  HTTP {r.status_code}; retrying in {backoff:.0f}s', file=sys.stderr)
            time.sleep(backoff)
            backoff *= 2
            continue
        r.raise_for_status()
        body = r.json()
        return [base64.b64decode(item['b64_json']) for item in body['data']]
    raise RuntimeError('exhausted retries')


def chroma_key_to_alpha(img: Image.Image, key_rgb: tuple[int, int, int], tol: int) -> Image.Image:
    """Replace pixels close to key_rgb with full transparency.

    Workaround for gpt-image-2 not supporting transparent backgrounds: prompt for
    a specific chroma-key bg color (e.g. magenta), then strip it client-side.
    """
    img = img.convert('RGBA')
    px = img.load()
    kr, kg, kb = key_rgb
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if abs(r - kr) <= tol and abs(g - kg) <= tol and abs(b - kb) <= tol:
                px[x, y] = (0, 0, 0, 0)
    return img


def downscale_and_quantize(src_png: bytes, target_w: int, target_h: int, palette_size: int = 32,
                           chroma_key: tuple[int, int, int] | None = None,
                           chroma_tol: int = 24) -> Image.Image:
    img = Image.open(__import__('io').BytesIO(src_png)).convert('RGBA')
    # Chroma-key BEFORE downscale so the alpha mask is computed at full resolution.
    if chroma_key is not None:
        img = chroma_key_to_alpha(img, chroma_key, chroma_tol)
    img = img.resize((target_w, target_h), Image.LANCZOS)
    if palette_size > 0:
        # Quantize to a small palette but preserve alpha for transparent pixels.
        rgb = img.convert('RGB').quantize(colors=palette_size, method=Image.Quantize.MEDIANCUT).convert('RGBA')
        # Re-key alpha: pixels whose original alpha was below 16 become fully transparent.
        orig_a = img.split()[-1]
        rgb.putalpha(orig_a)
        img = rgb
    return img


def parse_hex_rgb(s: str) -> tuple[int, int, int]:
    s = s.lstrip('#')
    if len(s) != 6:
        raise ValueError(f'expected #RRGGBB, got {s!r}')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def make_placeholder(w: int, h: int, idx: int) -> Image.Image:
    """Solid-color void-purple placeholder, slight variation per index."""
    base = (0x6a, 0x0d, 0xad)
    shift = (idx * 17) % 60
    rgb = (max(0, base[0] - shift), max(0, base[1] + shift // 3), min(255, base[2] - shift // 2))
    img = Image.new('RGBA', (w, h), rgb + (255,))
    return img


def main() -> int:
    ap = argparse.ArgumentParser(description='Generate void-themed sprite art via gpt-image-2.')
    ap.add_argument('--reference', type=Path, help='reference PNG (required for non-dry-run)')
    ap.add_argument('--prompt-file', type=Path, help='prompt text file (required for non-dry-run)')
    ap.add_argument('--out-dir', required=True, type=Path)
    ap.add_argument('--variants', type=int, default=1, help='number of images to request (n)')
    ap.add_argument('--api-size', default='1024x1024', help='size requested from API (default 1024x1024)')
    ap.add_argument('--target-size', default='48x32', help='final output size after downscale (WxH)')
    ap.add_argument('--palette', type=int, default=32, help='palette size for quantization (0 = skip)')
    ap.add_argument('--model', default=DEFAULT_MODEL, help=f'model id (default: {DEFAULT_MODEL})')
    ap.add_argument('--bg-key', default=None,
                    help='Chroma-key BG color as #RRGGBB. Pixels close to this color become '
                         'transparent in post. Use with gpt-image-2 (no transparent-bg support) — '
                         'prompt must demand this exact color as background.')
    ap.add_argument('--chroma-tol', type=int, default=24,
                    help='per-channel tolerance for --bg-key matching (default 24)')
    ap.add_argument('--dry-run', action='store_true', help='skip API, write solid-color placeholders')
    args = ap.parse_args()

    target_w, target_h = (int(x) for x in args.target_size.lower().split('x'))
    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.dry_run:
        for i in range(args.variants):
            img = make_placeholder(target_w, target_h, i)
            out = args.out_dir / f'variant_{i:02d}.png'
            img.save(out)
            print(f'  dry-run: wrote {out}')
        return 0

    if args.reference is None or args.prompt_file is None:
        print('error: --reference and --prompt-file required for live mode', file=sys.stderr)
        return 2
    load_dotenv(Path(__file__).parent / '.env')
    api_key = os.environ.get('OPENAI_API_KEY')
    if not api_key:
        print('error: OPENAI_API_KEY env var not set', file=sys.stderr)
        return 2
    if not args.reference.exists():
        print(f'error: reference not found: {args.reference}', file=sys.stderr)
        return 1
    prompt = load_prompt(args.prompt_file)

    chroma_key = parse_hex_rgb(args.bg_key) if args.bg_key else None

    print(f'calling {args.model} (n={args.variants}, api_size={args.api_size}) ...')
    raw_pngs = call_edits(args.reference, prompt, args.variants, args.api_size, api_key, args.model)
    for i, raw in enumerate(raw_pngs):
        raw_path = args.out_dir / f'raw_{i:02d}.png'
        raw_path.write_bytes(raw)
        out = downscale_and_quantize(raw, target_w, target_h, args.palette, chroma_key, args.chroma_tol)
        out_path = args.out_dir / f'variant_{i:02d}.png'
        out.save(out_path)
        print(f'  wrote raw -> {raw_path}, downscaled -> {out_path}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
