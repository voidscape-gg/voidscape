#!/usr/bin/env bash
# render-worldmap.sh — produce voidscape's per-floor world-map PNGs.
#
# Drives the vendored rsc-mapgen (sean-niemann/RSC-Landscape-Generator) — a
# pure-Java tool that parses RSC client cache archives and emits a colored
# world map image. The Node.js alternative @2003scape/rsc-landscape is the
# more obvious choice but its node-canvas pipeline produces only POI icons
# on Node ≥ 20; this Java tool sidesteps that.
#
# Output: Client_Base/Cache/worldmap/floor{0..3}.png
# Source: server/conf/server/data/maps/{land,maps}63.{jag,mem}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MAPGEN_DIR="$REPO_ROOT/tools/rsc-mapgen"
MAPS_SRC="$REPO_ROOT/server/conf/server/data/maps"
MAPS_INPUT="$MAPGEN_DIR/input/jag"
OUT_DIR="$REPO_ROOT/Client_Base/Cache/worldmap"

# Each floor section in the stacked output is 2736 px tall (2304 wide).
# The 4-floor grand image is 2304 × 10944.
FLOOR_W=2304
FLOOR_H=2736

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found on PATH" >&2
    exit 1
fi
if ! command -v magick >/dev/null 2>&1; then
    echo "ERROR: imagemagick (magick) not found on PATH — brew install imagemagick" >&2
    exit 1
fi
if [[ ! -f "$MAPGEN_DIR/mapgen.jar" ]]; then
    echo "ERROR: $MAPGEN_DIR/mapgen.jar missing — see tools/rsc-mapgen/README.md" >&2
    exit 1
fi

# Stage v63 archives where mapgen expects them.
mkdir -p "$MAPS_INPUT"
cp "$MAPS_SRC/land63.jag" "$MAPS_INPUT/land.jag"
cp "$MAPS_SRC/land63.mem" "$MAPS_INPUT/land.mem"
cp "$MAPS_SRC/maps63.jag" "$MAPS_INPUT/maps.jag"
cp "$MAPS_SRC/maps63.mem" "$MAPS_INPUT/maps.mem"

cd "$MAPGEN_DIR"
rm -f map.png
echo "==> Generating world map (rsc-mapgen)"
java -jar mapgen.jar jag 2>&1 | tail -1   # mapgen spams a per-percent progress line; keep last

if [[ ! -f map.png ]]; then
    echo "ERROR: mapgen.jar did not produce map.png" >&2
    exit 1
fi

# Split the 4-floor stacked image into per-floor PNGs.
mkdir -p "$OUT_DIR"
echo "==> Splitting into per-floor PNGs"
for floor in 0 1 2 3; do
    OFFSET=$(( floor * FLOOR_H ))
    OUT="$OUT_DIR/floor${floor}.png"
    magick map.png -crop "${FLOOR_W}x${FLOOR_H}+0+${OFFSET}" +repage "$OUT"
    SIZE=$(stat -f%z "$OUT")
    echo "    floor${floor}.png — ${FLOOR_W}x${FLOOR_H}, $((SIZE / 1024)) KB"
done

echo "==> Done. Output: $OUT_DIR"
