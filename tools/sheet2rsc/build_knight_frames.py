#!/usr/bin/env python3
"""Build the 18-frame Void Knight, copying the BLACK KNIGHT's per-frame box geometry so combat
positioning/gap matches exactly. Each frame = tight pixel data placed via xShift/yShift in a logical
box (something1 x something2). Walk/angle: body centred. Combat: WIDER box + body left-of-centre, so
the engine (which centres the box) renders the body SET BACK with the arm reaching forward.
Per-frame sidecars carry the geometry. Mirrors BK ratios: combat s1 ~ 1.31x walk; combat body ~0.26 of box."""
from PIL import Image
import numpy as np
import os, json

SRC = "tmp/voidknight"
OUT = "tmp/voidknight/frames"
TARGET_H = 188
HEADROOM = 14           # something2 = TARGET_H + HEADROOM
PAD = 6
COMBAT_W_RATIO = 1.31   # BK: 84/64
COMBAT_BODY_FRAC = 0.26 # BK: combat body centre ~0.26 of box

angles = ["front", "q34front", "side", "q34back", "back"]
imgs = {n: Image.open(f"{SRC}/{n}.png").convert("RGBA") for n in angles}
imgs["c_ready"] = Image.open(f"{SRC}/combat_ready.png").convert("RGBA")
imgs["c_mid"] = Image.open(f"{SRC}/combat_mid.png").convert("RGBA")
imgs["c_strike"] = Image.open(f"{SRC}/combat_strike.png").convert("RGBA")

def scale_h(im):
    s = TARGET_H / im.height
    return im.resize((max(1, int(im.width * s)), TARGET_H), Image.LANCZOS)

sf = {n: scale_h(im) for n, im in imgs.items()}
SOMETHING2 = TARGET_H + HEADROOM

def head_cx(im):
    a = np.asarray(im)[:, :, 3]
    rows = np.where(a.any(axis=1))[0]
    band = a[rows[0]:rows[0] + max(4, im.height // 5)]
    cols = band.sum(axis=0).astype(float)
    return int((np.arange(im.width) * cols).sum() / cols.sum()) if cols.sum() else im.width // 2

# walk reference box width (front body, centred)
walk_ref_s1 = sf["front"].width + PAD * 2

def emit(name, im, combat, idx):
    """Trim transparent margins to tight pixel data, compute box + shifts, save png + sidecar."""
    bb = im.getbbox(); fig = im.crop(bb)
    fw, fh = fig.size
    hc = head_cx(im) - bb[0]            # head/body centre within the tight figure
    if not combat:
        s1 = max(walk_ref_s1, fw + PAD * 2)
        xshift = s1 // 2 - hc           # body centre -> box centre
    else:
        s1 = max(round(walk_ref_s1 * COMBAT_W_RATIO), fw + PAD)
        xshift = round(COMBAT_BODY_FRAC * s1) - hc   # body centre -> 0.26 of box (set back)
    yshift = SOMETHING2 - fh - PAD      # feet near bottom
    fig.save(f"{OUT}/f{idx:02d}.png")
    json.dump({"requiresShift": True, "xShift": int(xshift), "yShift": int(yshift),
               "something1": int(s1), "something2": int(SOMETHING2)},
              open(f"{OUT}/f{idx:02d}.png.json", "w"))
    return s1

def leg_lift(im, right, lift=8, hipfrac=0.56):
    a = np.asarray(im); H, W = a.shape[:2]; hipY = int(H * hipfrac); cx = W // 2
    out = a.copy(); sl = (slice(cx, W) if right else slice(0, cx))
    out[hipY:, sl] = 0; out[hipY:H - lift, sl] = a[hipY:H - lift, sl]
    out[:hipY] = np.where(a[:hipY, :, 3:4] > 0, a[:hipY], out[:hipY])
    return Image.fromarray(out, "RGBA")

os.makedirs(OUT, exist_ok=True)
idx = 0
for n in angles:
    base = sf[n]
    for variant in (base, leg_lift(base, True), leg_lift(base, False)):
        emit(n, variant, False, idx); idx += 1
for n in ("c_ready", "c_mid", "c_strike"):
    emit(n, sf[n], True, idx); idx += 1

# camera: front box (=something3) renders at camera1; pick so the body reads ~Black-Knight size
cam1 = walk_ref_s1                     # front renders ~1:1 with its box -> body ~ walk_ref_s1 wide
cam1 = round(walk_ref_s1 * 145 / walk_ref_s1)  # keep it explicit; tune vs BK in-game
cam1 = 110
cam2 = round(cam1 * SOMETHING2 / walk_ref_s1)
print(f"walk_ref_s1={walk_ref_s1} something2={SOMETHING2} -> suggested cam {cam1}x{cam2} (tune vs BK)")
