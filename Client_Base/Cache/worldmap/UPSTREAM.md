# Vendored world-map assets

Source: <https://github.com/2003scape/rsc-world-map>
Vendor SHA: `70f488a9e84df1991f12743101eefedaa5fcd8bc`
License: AGPLv3 (same as voidscape — see `COPYING` upstream)

## What's here

- `plane-0.png` — 2448 × 2736 pre-rendered RSC surface map. Produced by the sister library [`@2003scape/rsc-landscape`](https://github.com/2003scape/rsc-landscape) from canonical Jagex `.jag/.mem` archives. 3 px/tile, X-mirrored, sectors arranged right-to-left. **Then baked-in scenery** — see "Object overlay" below. Voidscape's world-map walker is surface-only by design; we don't ship `plane-{1,2,3}.png` from upstream.
- `labels.tsv` — 110 place-name labels, converted from `res/labels.json`. Columns: `text`, `x`, `y`, `size`, `align`, `bold`, `colour` (rgb or empty). Newlines in text are escaped as `\n`. Coordinates are PNG pixels in `plane-0.png`.
- `points.tsv` — 309 POI markers across 42 types, converted from `res/points.json`. Columns: `type`, `x`, `y`. Coordinates are PNG pixels in `plane-0.png`.
- `icons/<type>.png` — small sprite icons (5–11 px) for each POI type, copied verbatim from `res/key/`.

## Object overlay

Upstream's `res/objects.json` (~27k records — trees, rocks, fences, fungus, etc.) is rendered at runtime by `src/entity-canvas.js` as 3×3 "+" symbols on a transparent overlay. Each id+coord gets coloured:

- `id == 1` (regular tree)                            → green  `rgb(0,160,0)`
- `id ∈ {4, 38, 70, 205}` AND inside the wild box     → brown  `rgb(112,64,0)`
- everything else                                     → orange `rgb(175,95,0)`

Wild box (PNG-pixel post-transform): `x∈[1440,2304], y∈[286,1286]`.

We **bake this overlay into the plane PNGs at vendor time** via `scripts/bake-worldmap-objects.py`, so the runtime client has zero per-frame overlay cost. The PNGs in this directory are post-bake.

## Refresh procedure

When upstream bumps:

```bash
cd /tmp && rm -rf rsc-world-map && git clone --depth 1 https://github.com/2003scape/rsc-world-map.git
DEST=Client_Base/Cache/worldmap
cp /tmp/rsc-world-map/res/plane-0.png "$DEST"/  # surface-only — see note above
cp /tmp/rsc-world-map/res/key/*.png "$DEST/icons/"
python3 - <<'PY'
import json
SRC = "/tmp/rsc-world-map/res"
DEST = "Client_Base/Cache/worldmap"
labels = json.load(open(f"{SRC}/labels.json"))
with open(f"{DEST}/labels.tsv", "w") as o:
    o.write("# vendored from 2003scape/rsc-world-map res/labels.json\n")
    o.write("# text\\tx\\ty\\tsize\\talign\\tbold\\tcolour\n")
    for L in labels:
        t = L["text"].replace("\n", "\\n").replace("\t", " ")
        o.write(f"{t}\t{L['x']}\t{L['y']}\t{L.get('size',10)}\t{L.get('align','center')}\t{1 if L.get('bold') else 0}\t{L.get('colour','')}\n")
points = json.load(open(f"{SRC}/points.json"))
with open(f"{DEST}/points.tsv", "w") as o:
    o.write("# vendored from 2003scape/rsc-world-map res/points.json\n")
    o.write("# type\\tx\\ty\n")
    for P in points:
        o.write(f"{P['type']}\t{P['x']}\t{P['y']}\n")
PY
# Bake the scenery objects overlay into the plane PNGs:
scripts/bake-worldmap-objects.py /tmp/rsc-world-map/res/objects.json

# Re-apply Voidscape-only overlays: Void Island, Void Enclave,
# PK Catching Simulator arenas, labels, and POIs.
scripts/bake-voidscape-worldmap.py
```

Update the SHA above and add a `docs/DIVERGENCE.md` entry noting the upstream version.

## Why TSV and not the original JSON?

The PC client has only `discord-rpc.jar` on its classpath — no JSON library. Pre-converting at vendor time keeps the runtime parser trivial (~30 lines, in `WorldMapPanel.java`) and avoids dragging a 100 KB jar into the client.

## Coord system

Labels and POIs are in PNG-pixel coords for `plane-0.png` (2448 × 2736). The world-tile → PNG-pixel transform we use for the player marker is taken verbatim from upstream's `src/entity-canvas.js`:

```
pngX = 2446 - 3 * worldX                  // imageWidth (2448) - worldX*3 - 2
pngY = 3 * (worldY % 944) - 1             // (plane term cancels for per-floor Y)
```

The earlier "empirical fit at 2471" was wrong — labels in `labels.json` are placed at the visual centre of named regions, not at any specific tile, so fitting against them produced a constant ~25 px east of the real transform (≈8 tiles). The above values match upstream's entity placement exactly.
