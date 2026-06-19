#!/usr/bin/env python3
"""Build void KNIGHT options from layered LPC armor (plate + helmet [+ sword]), recolored void-purple.
Same RSC layout + right-facing conventions as the other tools. Each option = an ordered layer list."""
import io, urllib.request
from PIL import Image, ImageDraw
import numpy as np

RAW = "https://raw.githubusercontent.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator/master/spritesheets"
F = 64
WALK_COLS = [0, 2, 6]
SLASH_COLS = [0, 2, 4]
RAMP = [(0, (12, 4, 22)), (70, (40, 12, 70)), (130, (110, 45, 175)),
        (190, (180, 95, 240)), (235, (225, 165, 255)), (255, (245, 220, 255))]

BODY = "body/bodies/muscular/{a}.png"
LEGS = "legs/armour/plate/male/{a}.png"
TORSO = "torso/armour/plate/male/{a}.png"
# each option: name + list of layer templates in z-order (bottom first)
OPTIONS = [
    ("Void Knight (horned)", [BODY, LEGS, TORSO, "hat/helmet/horned/adult/{a}.png"]),
    ("Void Knight (greathelm)", [BODY, LEGS, TORSO, "hat/helmet/greathelm/adult/{a}.png"]),
    ("Void Knight (armet)", [BODY, LEGS, TORSO, "hat/helmet/armet/adult/{a}.png"]),
    ("Void Knight (sugarloaf)", [BODY, LEGS, TORSO, "hat/helmet/sugarloaf/adult/{a}.png"]),
]

_cache = {}
def fetch(path):
    if path in _cache: return _cache[path]
    try:
        im = Image.open(io.BytesIO(urllib.request.urlopen(RAW + "/" + path, timeout=30).read())).convert("RGBA")
    except Exception as e:
        print("  MISS", path, str(e)[:40]); im = None
    _cache[path] = im; return im

def composite(layers, a):
    base = None
    for tpl in layers:
        im = fetch(tpl.format(a=a))
        if im is None: continue
        if base is None:
            base = Image.new("RGBA", im.size, (0, 0, 0, 0))
        if im.size != base.size:
            t = Image.new("RGBA", base.size, (0, 0, 0, 0)); t.alpha_composite(im.crop((0,0,min(im.width,base.width),min(im.height,base.height)))); im = t
        base.alpha_composite(im)
    return base

def frame(sheet, row, col): return sheet.crop((col*F, row*F, col*F+F, row*F+F))

def recolor(img):
    a = np.asarray(img.convert("RGBA")).astype(np.float32); rgb, al = a[:,:,:3], a[:,:,3]
    lum = 0.299*rgb[:,:,0]+0.587*rgb[:,:,1]+0.114*rgb[:,:,2]
    lvls = np.array([c[0] for c in RAMP]); out = np.zeros_like(rgb)
    for ch in range(3): out[:,:,ch] = np.interp(lum, lvls, [c[1][ch] for c in RAMP])
    return Image.fromarray(np.dstack([out, al]).astype(np.uint8))

def union_box(frames):
    box = None
    for f in frames:
        b = f.getbbox()
        if b is None: continue
        box = b if box is None else (min(box[0],b[0]),min(box[1],b[1]),max(box[2],b[2]),max(box[3],b[3]))
    return box

def build(name, layers):
    walk = composite(layers, "walk"); slash = composite(layers, "slash")
    if walk is None or slash is None: print("  skip", name); return None
    raw = {"down":[],"right":[],"up":[],"slashR":[]}
    for c in WALK_COLS:
        raw["down"].append(frame(walk,2,c)); raw["right"].append(frame(walk,3,c)); raw["up"].append(frame(walk,0,c))
    for c in SLASH_COLS: raw["slashR"].append(frame(slash,3,c))
    box = union_box(sum(raw.values(), []))
    x0,y0,x1,y1 = box; x0=max(0,x0-1); x1=min(F,x1+1); y1=min(F,y1+1)
    def conv(f): return recolor(f.crop((x0,y0,x1,y1)))
    return {"front":conv(raw["down"][0]),"side":conv(raw["right"][0]),"back":conv(raw["up"][0]),"combat":conv(raw["slashR"][0])}

def main():
    results = []
    for name, layers in OPTIONS:
        print("building", name); r = build(name, layers)
        if r: results.append((name, r))
    cw, ch = 150, 200; cols = ["front","side","back","combat"]
    sheet = Image.new("RGB", (cw*4+180, ch*len(results)+20), (26,26,32)); d = ImageDraw.Draw(sheet)
    for ci,c in enumerate(cols): d.text((180+ci*cw+4, 4), c, fill=(255,255,0))
    for ri,(name,r) in enumerate(results):
        d.text((6, 20+ri*ch+ch//2), name, fill=(200,170,255))
        for ci,c in enumerate(cols):
            im = r[c]; canvas = Image.new("RGBA",(cw,ch),(26,26,32,255))
            s = min(cw/im.width, ch/im.height)*0.92; im2 = im.resize((max(1,int(im.width*s)),max(1,int(im.height*s))), Image.NEAREST)
            canvas.alpha_composite(im2, ((cw-im2.width)//2,(ch-im2.height)//2)); sheet.paste(canvas.convert("RGB"), (180+ci*cw, 20+ri*ch))
    sheet.save("/tmp/void-knights.png"); print("saved /tmp/void-knights.png with", len(results))

main()
