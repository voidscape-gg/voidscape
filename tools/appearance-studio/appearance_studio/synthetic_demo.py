from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image
from voidscim.sprite_io import encode

from .compiler import compose_look, expand_rigid_layer
from .preview import render_layer_matrix
from .template import load_template


def _masters(template, kind: str):
    result = {}
    for frame in template.frames:
        if frame.master in result: continue
        image = Image.new("RGBA", frame.size, (0, 0, 0, 0))
        x, y = frame.crown
        if kind == "hair":
            for dx, dy, shade in ((-2, 1, 103), (-1, 0, 132), (0, 0, 160), (1, 1, 132), (2, 2, 103)):
                image.putpixel((x + dx, y + dy), (shade, shade, shade, 255))
        else:
            for dx in (-2, -1, 1, 2): image.putpixel((x + dx, y + 13), (132, 132, 132, 255))
        result[frame.master] = image
    return result


def build_synthetic_demo(template_path: Path, out: Path) -> dict:
    template = load_template(template_path)
    hair = expand_rigid_layer(_masters(template, "hair"), "hair", "hair-mask", template)
    facial = expand_rigid_layer(_masters(template, "facial-hair"), "facial-hair", "hair-mask", template)
    if hair.findings or facial.findings: raise ValueError("; ".join(hair.findings + facial.findings))
    out.mkdir(parents=True, exist_ok=True)
    base = [Image.new("RGBA", frame.size, (0, 0, 0, 0)) for frame in template.frames]
    look = compose_look(base, [hair, facial])
    hashes = []
    frames = out / "frames"; frames.mkdir(exist_ok=True)
    for offset, canvas in enumerate(look):
        from .geometry import derive_sprite
        sprite = derive_sprite(canvas)
        png = frames / f"frame_{offset:02d}.png"; sidecar = frames / f"frame_{offset:02d}.png.json"
        sprite.image.save(png); sidecar.write_text(json.dumps(sprite.sidecar, indent=2, sort_keys=True) + "\n")
        hashes.append(hashlib.sha256(encode(sprite.image, sprite.sidecar)).hexdigest())
    render_layer_matrix(hair).save(out / "hair-matrix.png")
    render_layer_matrix(facial).save(out / "facial-hair-matrix.png")
    actual = Image.new("RGBA", (sum(image.width for image in look), 102), (20, 24, 28, 255))
    x = 0
    for image in look: actual.alpha_composite(image, (x, 0)); x += image.width
    actual.save(out / "actual-size-look-strip.png")
    report = {"schema": "voidscape-synthetic-look/v1", "shipping": False, "frameCount": 18,
              "layers": ["future_mullet", "future_mustache"], "encodedSha256": hashes,
              "findings": [], "hasF": False}
    (out / "build.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return report
