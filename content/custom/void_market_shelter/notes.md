# Void Market Shelter

## Decisions

- Use original deterministic geometry rather than a downloaded fantasy stall.
- Keep the source footprint at `3.24 x 2.02` tiles for the nonblocking
  three-by-two scenery definition. The integrated target is model name
  `void_market_shelter`, scenery id `1313`.
- Build four slender, leaning supports from mismatched voidwood and gunmetal.
  Their irregular heights carry a sloped two-panel cloth canopy with a jagged
  front edge, contrasting repairs, and one torn hanging tab.
- Leave the front, sides, and central volume open. The model has no solid floor
  or full-height wall; the rear counter and side supply shelf stay below eye
  level so players and Enclave landmarks remain readable through it.
- Add restrained inhabited details: counter legs, two shelf tiers, two supply
  bundles, weathered hanging ropes, and two small bright-violet Rift charms.
- Use flat colors only. Textures, UVs, transparency, and external assets are
  intentionally absent so the source survives the RSC OBJ-to-OB3 pipeline.
- Keep every source face triangular. Integration is responsible for the
  double-sided OB3 fills required by RuneScape Classic's rotating camera.

## Regeneration

```bash
scripts/generate-void-market-shelter-source.py
scripts/generate-void-market-shelter-source.py --check
```

`--check` validates exact output bytes and hashes, dimensions, grounding,
triangle indices and areas, component and material counts, the bright-accent
budget, a clear central player/sight corridor, the lack of broad floor faces,
and the lack of large opaque wall-like faces below the canopy.

## Test Notes

- Approved source output is 235 vertices / 330 triangles with source bounds
  `3.240000 x 2.020000 x 2.460000` tiles.
- The installed cache model uses scale `128`, Blender-style Z-up axis mapping,
  centered and grounded coordinates, and double-sided face fills.
- Scenery id `1313` is integrated as type `0` (nonblocking), width `3`, height
  `2`, at Void Enclave `(102,317)` with direction `0`.
- Check the shelter from all four camera quadrants at both near and far zoom.
- Confirm the low counter reads as an inhabited quartermaster stall without
  hiding players, the Rift, or the route through the market court.
