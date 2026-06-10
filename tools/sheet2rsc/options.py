#!/usr/bin/env python3
"""Build several void-recolored NPC OPTIONS from LPC, for the user to pick.
Fetches body+head layers, composites, recolors, maps to RSC layout (right-facing side/combat
per the convention), and renders a front/side/back/combat preview per option + a combined sheet."""
import io, urllib.request
from PIL import Image, ImageDraw
import numpy as np

RAW = "https://raw.githubusercontent.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator/master/spritesheets"
F = 64
WALK_COLS = [0, 2, 6]
SLASH_COLS = [0, 2, 4]
RAMP = [(0, (12, 4, 22)), (70, (40, 12, 70)), (130, (110, 45, 175)),
        (190, (180, 95, 240)), (235, (225, 165, 255)), (255, (245, 220, 255))]

# (name, body_template, head_template) — {a} = walk|slash. head composited over body.
OPTIONS = [
    ("Void Brute (orc)", "body/bodies/muscular/{a}.png", "head/heads/orc/male/{a}.png"),
    ("Void Horror (minotaur)", "body/bodies/muscular/{a}.png", "head/heads/minotaur/male/{a}.png"),
    ("Void Stalker (lizard)", "body/bodies/muscular/{a}.png", "head/heads/lizard/male/{a}.png"),
    ("Void Hound (wolf)", "body/bodies/muscular/{a}.png", "head/heads/wolf/male/{a}.png"),
    ("Void Risen (zombie)", "body/bodies/zombie/walk/zombie.png|body/bodies/zombie/slash/zombie.png", None),
]


def fetch(path):
    try:
        return Image.open(io.BytesIO(urllib.request.urlopen(RAW + "/" + path, timeout=30).read())).convert("RGBA")
    except Exception as e:
        print("  MISS", path, e); return None


def layer(body, head):
    if head is None:
        return body
    out = Image.new("RGBA", body.size, (0, 0, 0, 0))
    out.alpha_composite(body)
    if head.size != body.size:
        h = Image.new("RGBA", body.size, (0, 0, 0, 0))
        h.alpha_composite(head.crop((0, 0, min(head.width, body.width), min(head.height, body.height))))
        head = h
    out.alpha_composite(head)
    return out


def frame(sheet, row, col):
    return sheet.crop((col * F, row * F, col * F + F, row * F + F))


def recolor(img):
    a = np.asarray(img.convert("RGBA")).astype(np.float32)
    rgb, al = a[:, :, :3], a[:, :, 3]
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    lvls = np.array([c[0] for c in RAMP])
    out = np.zeros_like(rgb)
    for ch in range(3):
        out[:, :, ch] = np.interp(lum, lvls, [c[1][ch] for c in RAMP])
    return Image.fromarray(np.dstack([out, al]).astype(np.uint8))


def union_box(frames):
    box = None
    for f in frames:
        b = f.getbbox()
        if b is None: continue
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    return box


def build(name, body_tpl, head_tpl):
    if "|" in body_tpl:  # explicit walk|slash
        bw = fetch(body_tpl.split("|")[0]); bs = fetch(body_tpl.split("|")[1])
    else:
        bw = fetch(body_tpl.format(a="walk")); bs = fetch(body_tpl.format(a="slash"))
    hw = fetch(head_tpl.format(a="walk")) if head_tpl else None
    hs = fetch(head_tpl.format(a="slash")) if head_tpl else None
    if bw is None or bs is None:
        print("  skip", name); return None
    walk = layer(bw, hw)
    slash = layer(bs, hs)
    raw = {"down": [], "right": [], "up": [], "slashR": []}
    for c in WALK_COLS:
        raw["down"].append(frame(walk, 2, c)); raw["right"].append(frame(walk, 3, c)); raw["up"].append(frame(walk, 0, c))
    for c in SLASH_COLS:
        raw["slashR"].append(frame(slash, 3, c))
    box = union_box(sum(raw.values(), []))
    x0, y0, x1, y1 = box; x0 = max(0, x0 - 1); x1 = min(F, x1 + 1); y1 = min(F, y1 + 1)
    def conv(f): return recolor(f.crop((x0, y0, x1, y1)))
    return {"front": conv(raw["down"][0]), "side": conv(raw["right"][0]),
            "back": conv(raw["up"][0]), "combat": conv(raw["slashR"][0])}


def main():
    results = []
    for name, b, h in OPTIONS:
        print("building", name)
        r = build(name, b, h)
        if r: results.append((name, r))
    # combined sheet: one row per option, columns front/side/back/combat
    cw, ch = 130, 180
    cols = ["front", "side", "back", "combat"]
    sheet = Image.new("RGB", (cw * 4 + 150, ch * len(results) + 20), (26, 26, 32))
    d = ImageDraw.Draw(sheet)
    for ci, c in enumerate(cols):
        d.text((150 + ci * cw + 4, 4), c, fill=(255, 255, 0))
    for ri, (name, r) in enumerate(results):
        d.text((6, 20 + ri * ch + ch // 2), name, fill=(200, 170, 255))
        for ci, c in enumerate(cols):
            im = r[c]
            canvas = Image.new("RGBA", (cw, ch), (26, 26, 32, 255))
            s = min(cw / im.width, ch / im.height) * 0.92
            im2 = im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.NEAREST)
            canvas.alpha_composite(im2, ((cw - im2.width) // 2, (ch - im2.height) // 2))
            sheet.paste(canvas.convert("RGB"), (150 + ci * cw, 20 + ri * ch))
    sheet.save("/tmp/void-options.png")
    print("saved /tmp/void-options.png with", len(results), "options")


main()
