#!/usr/bin/env python3
"""Convert an LPC (Liberated Pixel Cup) 4-directional sprite sheet into RuneScape Classic's
18-frame NPC animation layout, with a void-purple recolor. No AI generation — real hand-drawn
frames mapped into the engine's format.

LPC layout: 64x64 frames; rows = directions [0=up/back, 1=left, 2=down/front, 3=right];
columns = animation frames. walk.png (9 cols), slash.png (6 cols).

RSC layout: 5 directional sets x 3 walk frames + 3 combat frames = 18 frames.
  set0 front  = LPC down       set1 3/4-front = LPC down (reuse)
  set2 side   = LPC left       set3 3/4-back  = LPC up (reuse)    set4 back = LPC up
  combat (15-17) = LPC left SLASH  (side-profile -> matches the engine's side-on combat view)
Octants 5-7 are mirrored by the engine, so we only author the left side.
"""
import sys
from PIL import Image
import numpy as np

SRC = 'tmp/voidwraith'
OUT = 'tmp/voidwraith/frames'
F = 64

# which LPC columns become the 3 RSC frames (stand/mid/stride for walk; ready/mid/strike for combat)
WALK_COLS = [0, 2, 6]
SLASH_COLS = [0, 2, 4]

# void-purple luminance ramp: (lum 0..255) control points -> RGB
RAMP = [(0, (12, 4, 22)), (70, (40, 12, 70)), (130, (110, 45, 175)),
        (190, (180, 95, 240)), (235, (225, 165, 255)), (255, (245, 220, 255))]


def lpc_frame(sheet, row, col):
    return sheet.crop((col * F, row * F, col * F + F, row * F + F))


def recolor(img):
    a = np.asarray(img.convert('RGBA')).astype(np.float32)
    rgb, alpha = a[:, :, :3], a[:, :, 3]
    lum = (0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2])
    lvls = np.array([c[0] for c in RAMP])
    out = np.zeros_like(rgb)
    for ch in range(3):
        out[:, :, ch] = np.interp(lum, lvls, [c[1][ch] for c in RAMP])
    res = np.dstack([out, alpha]).astype(np.uint8)
    return Image.fromarray(res, 'RGBA')


def union_box(frames):
    box = None
    for f in frames:
        b = f.getbbox()
        if b is None:
            continue
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    return box


def main():
    walk = Image.open(f'{SRC}/walk.png').convert('RGBA')
    slash = Image.open(f'{SRC}/slash.png').convert('RGBA')
    # gather the source frames (un-cropped) so we can compute one shared box
    raw = {}
    for c in WALK_COLS:
        raw.setdefault('down', []).append(lpc_frame(walk, 2, c))   # front
        raw.setdefault('right', []).append(lpc_frame(walk, 3, c))  # side: RIGHT-facing (RSC convention,
        #   matches authentic NPCs; LPC LEFT here renders the NPC's side facing the wrong way on orbit)
        raw.setdefault('up', []).append(lpc_frame(walk, 0, c))     # back
    for c in SLASH_COLS:
        # RIGHT-direction slash: matches RSC's COMBAT_A base orientation (authentic NPCs face right
        # un-mirrored). The engine mirrors it (COMBAT_B) to face the other way, so the NPC always
        # ends up facing its opponent. Using the LEFT slash here renders the NPC facing AWAY.
        raw.setdefault('slashR', []).append(lpc_frame(slash, 3, c))
    allframes = [f for v in raw.values() for f in v]
    box = union_box(allframes)
    # pad the box a touch and keep feet anchored at bottom
    x0, y0, x1, y1 = box
    x0 = max(0, x0 - 1); x1 = min(F, x1 + 1); y1 = min(F, y1 + 1)
    crop = (x0, y0, x1, y1)
    bw, bh = x1 - x0, y1 - y0

    def conv(f):
        return recolor(f.crop(crop))

    # RSC 18-frame order
    sets = {
        0: [conv(f) for f in raw['down']],    # front
        1: [conv(f) for f in raw['down']],    # 3/4-front (reuse front; LPC has no true 3/4)
        2: [conv(f) for f in raw['right']],   # side (right-facing = RSC convention)
        3: [conv(f) for f in raw['up']],      # 3/4-back (reuse back)
        4: [conv(f) for f in raw['up']],      # back
        5: [conv(f) for f in raw['slashR']],  # combat (right-facing, RSC convention)
    }
    import os
    os.makedirs(OUT, exist_ok=True)
    idx = 0
    for s in range(6):
        for f in sets[s]:
            f.save(f'{OUT}/f{idx:02d}.png'); idx += 1
    print(f'wrote 18 frames ({bw}x{bh}) to {OUT}')

    # preview sheet
    from PIL import ImageDraw
    labels = ['front', '34front', 'side', '34back', 'back', 'combat']
    cw, ch = bw * 4, bh * 4
    sheet = Image.new('RGB', (cw * 3 + 10, ch * 6 + 20), (30, 30, 36))
    d = ImageDraw.Draw(sheet)
    for s in range(6):
        d.text((2, s * ch + 4), labels[s], fill=(255, 255, 0))
        for i, f in enumerate(sets[s]):
            c = Image.new('RGBA', f.size, (30, 30, 36, 255)); c.alpha_composite(f)
            sheet.paste(c.convert('RGB').resize((cw, ch), Image.NEAREST), (60 + i * cw, s * ch + 14))
    sheet.save('/tmp/voidwraith-frames.png')

    # walk + combat gifs ({0,1,2,1})
    def gif(frames, path, ms=160):
        seq = [frames[i] for i in (0, 1, 2, 1)]
        out = []
        for f in seq:
            c = Image.new('RGBA', f.size, (38, 38, 46, 255)); c.alpha_composite(f)
            out.append(c.resize((bw * 4, bh * 4), Image.NEAREST).convert('P', palette=Image.ADAPTIVE))
        out[0].save(path, save_all=True, append_images=out[1:], duration=ms, loop=0)
    gif(sets[0], '/tmp/voidwraith-walk-front.gif')
    gif(sets[2], '/tmp/voidwraith-walk-side.gif')
    gif(sets[5], '/tmp/voidwraith-combat.gif', 150)
    print('box', crop, 'aspect w/h', round(bw / bh, 3))


main()
