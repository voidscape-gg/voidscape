# rsc-mapgen — vendored

Vendored from [`sean-niemann/RSC-Landscape-Generator`](https://github.com/sean-niemann/RSC-Landscape-Generator) (commit at vendor time was the `dist/mapgen.jar` shipped as a prebuilt artifact). MIT licensed — see `LICENSE`.

We use it to produce the colored world-map PNG voidscape's slice-5 UI clicks on. The Node.js alternative `@2003scape/rsc-landscape` has a node-canvas regression on Node ≥ 20 that kills the terrain layer — only POI icons render. This Java tool has zero canvas/Node coupling, parses the same `land*.jag/.mem` + `maps*.jag/.mem` archives, and produces a colored output by design.

## Usage

Don't invoke directly — go through `scripts/render-worldmap.sh` from the repo root. The wrapper:

1. Copies voidscape's `server/conf/server/data/maps/land63.{jag,mem}` and `maps63.{jag,mem}` into the input slot the JAR expects (`tools/rsc-mapgen/input/jag/{land,maps}.{jag,mem}`).
2. Invokes `java -jar mapgen.jar jag` — produces `map.png` (4 floors stacked vertically, 2304 × 10944).
3. Splits the stacked PNG into 4 floor PNGs at `Client_Base/Cache/worldmap/floor{0..3}.png`.

The split / scale / crop math is in the wrapper script — keep it there, not in this directory.

## Why vendor instead of clone?

- Reproducibility — the upstream isn't tagged and we want a known-working snapshot.
- No internet required for builds / re-renders.
- The artifact is 17 KB; the cost is trivial.

If we need to refresh the snapshot, fetch the latest from the upstream repo and replace `mapgen.jar` + `UPSTREAM-README.md`. Keep `LICENSE`.

## Compatibility

- Accepts `maps28.jag` and later (RSC-era client cache format).
- Voidscape ships `land63.jag/.mem` + `maps63.jag/.mem` in `server/conf/server/data/maps/`, which are inside the supported range. If the vendor SHA bumps to an even newer cache format, re-test.
