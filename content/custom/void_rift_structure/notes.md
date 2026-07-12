# Void Rift Structure

## Decisions

- Use original deterministic geometry rather than a third-party fantasy portal.
- Keep the complete XY footprint inside `+/-0.98` source tiles for the existing
  two-by-two scenery footprint.
- Build a low twelve-piece sunken plinth and broken inner bevel around a filled
  recessed core, then arrange six asymmetric supports around it. Three tall
  ribs frame a deliberately incomplete crown without covering the aperture;
  the other three terminate as shorter shards.
- Raise a closed, shallow-extruded jagged tear from the center so the Rift stays
  legible at RuneScape Classic's low camera angle and from edge-on views.
- Use flat gunmetal and restrained violet materials that survive the untextured
  OBJ-to-OB3 pipeline. The core is near-black but nonzero in every RGB channel.
- Keep every source face triangular. The integration step is responsible for
  creating the double-sided OB3 required by rotating RSC cameras.

## Regeneration

```bash
scripts/generate-void-rift-source.py
scripts/generate-void-rift-source.py --check
```

`--check` validates face count, dimensions, footprint, non-degenerate
triangles, support/crown counts, core coverage, materials, and exact generated
file contents. It also reports the deterministic hashes and model statistics.

## Test Notes

- Approved source output is 391 vertices / 584 triangles with source bounds
  `1.918094 x 1.847005 x 1.989281` tiles.
- Import at scale `128` with the pipeline's Blender-style Z-up mapping.
- Check four camera quadrants at both near and far zoom.
- Confirm terrain does not hide the core and no support is edge-on from every
  useful view.
- Confirm primary interaction can pick the filled central surface as well as
  the perimeter.
