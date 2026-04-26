# Recipe: add a custom wielded sprite (different shape from any existing item)

For when you want a wielded weapon/armor that **doesn't** look like an existing recolored sprite (sword, shortsword, mace, etc.). The Void Scimitar approach (reuse "sword" + tint via `charColour`) only works because the sword sprite block has tintable palette regions; bow sprites don't, and many other equipment sprites don't either. This recipe covers the deeper path: pack new wielded frames into the legacy `Authentic_Sprites.orsc` archive at the right runtime sprite-ID slot.

**Prerequisites**: an existing item with the wielded shape you want as a base (e.g. for the Void Shortbow we used the authentic longbow's 15 frames). You'll recolor those frames per-pixel rather than generate new ones — preserves frame-to-frame consistency across all 8 directions + walking poses.

## Why the obvious approach doesn't work

- `Custom_Sprites.osar` (the GZIP'd palette-indexed archive parsed by `orsc.graphics.two.SpriteArchive.Unpacker`) is **only consulted when `Config.S_WANT_CUSTOM_SPRITES=true`**. Voidscape runs with this flag `false` (and flipping it shifts every AnimationDef runtime index by 330+ — see DIVERGENCE 2026-04-25 Void Scimitar slice 5).
- In the `S_WANT_CUSTOM_SPRITES=false` codepath (`GraphicsController.spriteSelect(AnimationDef, int)` lines 325-338), the renderer **completely ignores `animation.name`** and uses `sprites[animation.getNumber() + offset]` from the legacy `Authentic_Sprites.orsc` ZIP. So packing a uniquely-named entry in the `.osar` does nothing.
- `charColour` tinting works for some sprite blocks (sword, shield) but not others (longbow). Whether a block is tintable is encoded in the sprite pixels themselves (specific reserved colors that get replaced by `colorVariant` in `drawSpriteClipping`). Bow sprites have plain ARGB pixels with no tint mask — `charColour` has no visible effect.

## Step 1 — append a new AnimationDef and discover its `number`

`animationDef.number` is auto-assigned at startup by `mudclient.loadEntitiesAuthentic()`:
- iterate AnimationDefs in source order
- if the name was seen before → reuse that previous entry's `number`
- if new name → assign `animationNumber`, then `animationNumber += 27` (with a hard jump from 1998 → 3300)

So the runtime `number` is determined by how many **unique-named** entries come before yours in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java::loadAnimationDefinitions()`. Append your AnimationDef **after** the closing brace of the `if (Config.S_WANT_CUSTOM_SPRITES)` block (so it loads unconditionally), then discover its number with a temporary println:

```java
// in mudclient.java::loadEntitiesAuthentic, right after `.number = animationNumber;`
if (s.equals("yourname")) {
    System.out.println("[debug] anim '" + s + "' number=" + animationNumber);
}
```

Build (`scripts/build.sh`), run the client, grep stdout. Remove the println once you have the number.

You also need the `number` of the **existing** AnimationDef whose sprite shape you want to reuse — same trick.

## Step 2 — extract the base sprites

`tools/voidscim-art/extract_ref.py` takes an archive index and writes the PNG + sidecar JSON. The wielded sprite block is 15 contiguous frames at `existing.number .. existing.number + 14` (or 18 frames if `hasA=true`, or 27 if `hasF=true`):

```bash
cd tools/voidscim-art
for i in $(seq 0 14); do
  python3 extract_ref.py \
    --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc \
    --index $((BASE + i)) \
    --out out/base_frames/frame_$(printf '%02d' $i).png
done
```

Each frame has its own dimensions (the 15 longbow frames range 12×55 to 25×65 — different player rotations have different silhouette widths). The sidecar JSON preserves `xShift`, `yShift`, etc.

## Step 3 — recolor pixel-for-pixel

Identify the unique colors in the source frames:

```python
from collections import Counter
from PIL import Image
colors = Counter()
for i in range(15):
    img = Image.open(f'out/base_frames/frame_{i:02d}.png').convert('RGBA')
    for px in img.getdata():
        if px[3] > 0:
            colors[(px[0], px[1], px[2])] += 1
print(colors.most_common())
```

For our longbow: only 3 colors (`#894b00` body, `#682d00` shadow, `#271e1b` string detail). Build a `COLOR_MAP` dict and apply it to every opaque pixel across all 15 frames:

```python
COLOR_MAP = {
    (137, 75, 0):  (126, 58, 204),  # main body
    (104, 45, 0):  (74, 20, 140),   # shadow
    (39, 30, 27):  (0, 0, 0),       # detail
}
for i in range(15):
    img = Image.open(f'out/base_frames/frame_{i:02d}.png').convert('RGBA')
    out = Image.new('RGBA', img.size, (0,0,0,0))
    sp, dp = img.load(), out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r,g,b,a = sp[x,y]
            if a == 0: continue
            new = COLOR_MAP.get((r,g,b), (r,g,b))
            dp[x,y] = (*new, a)
    out.save(f'out/recolored/frame_{i:02d}.png')
    # copy sidecar
```

Don't forget to copy each `frame_NN.png.json` sidecar alongside its recolored PNG so `pack.py` picks up the right header (xShift, yShift, slot dimensions).

## Step 4 — pack at the new slot

```bash
INPUTS=$(printf "out/recolored/frame_%02d.png," 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 | sed 's/,$//')
python3 pack.py \
  --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc \
  --start-index $NEW_BASE \
  --inputs "$INPUTS" \
  --commit
```

`pack.py --commit` requires `Authentic_Sprites.orsc.bak` to exist — `cp` it before the first commit if needed.

## Step 5 — set the JSON `appearanceID`

Server-side, your item's `appearanceID` in `server/conf/server/defs/ItemDefsCustom.json` should equal your AnimationDef's runtime index in the `EntityHandler.animations` list (the position resulting from your `animations.add(...)` call). Use the void-scimitar pattern: append after the `S_WANT_CUSTOM_SPRITES` block, then JSON `appearanceID` = (count of preceding entries that load at runtime). For the Void Shortbow, that lands at 231 (one after the Void Scimitar's 230). Empirical verification: temporarily set the AnimationDef name to `"sword"` with a bright `charColour`; if the wielded shape becomes a tinted sword at the JSON ID you tried, you've got the right slot.

## Step 6 — restart server (for JSON reload) + client

`Authentic_Sprites.orsc` is read at client startup; the JSON is read at server boot. Both must restart. The full cycle:

```bash
# kill running server (find by port; pkill patterns are unreliable)
SERVER_PID=$(lsof -t -i :43596) && [ -n "$SERVER_PID" ] && kill $SERVER_PID
pkill -f "Open_RSC_Client.jar"
sleep 2
scripts/build.sh
nohup scripts/run-server.sh >/tmp/server.log 2>&1 & disown
until grep -q "now online on TCP" /tmp/server.log; do sleep 1; done
nohup scripts/run-client.sh & disown
```

## Pitfalls

- **`pkill -f "openrsc"` doesn't match** the running server because the command line shows `java -classpath ...` not "openrsc". Use `lsof -t -i :43596` to find the PID by port.
- **Duplicate sidecar warning**: `pack.py` looks for `<input>.png.json`; if you forget to copy them alongside the recolored frames, it falls back to zeros and the sprites render with wrong shifts (player's hand floats away from the bow, etc.).
- **Frame count**: if `hasA=true`, you need 18 frames at `[base..base+17]`; if `hasF=true`, 27 frames at `[base..base+26]`. The longbow uses 15 (no fighting / no special).
- **animationNumber jump**: `animationNumber` jumps from 1998 → 3300 during the iteration. If your slot lands near 1998, the jump might affect your math — verify empirically with the println.
- **Don't edit `Custom_Sprites.osar` for `S_WANT_CUSTOM_SPRITES=false` worlds**. It's the wrong archive in this codepath. The voidbow entry I briefly added there did nothing and was reverted.
