# Void Market Shelter Source

- Origin: original Voidscape geometry generated in this repository
- Generator: `scripts/generate-void-market-shelter-source.py`
- Model name: `void_market_shelter`
- Integrated scenery id: `1313`
- External assets: none
- Textures, UVs, shaders, animation, and transparency: none
- Coordinate system: Blender-style Z-up; one source unit is one game tile
- Repository license: AGPLv3

The OBJ and MTL are deterministic outputs, not conversions or derivatives of
downloaded work. The standard-library generator creates the four mismatched
posts, open canopy frame, patched and torn cloth panels, low counter and shelf,
supply bundles, ropes, and faceted violet charms directly as triangle meshes.

## Generated Source Statistics

- Vertices: 235
- Faces: 330 triangles
- Bounds: X `[-1.620000, 1.620000]`, Y `[-1.020000, 1.000000]`,
  Z `[0.000000, 2.460000]` source tiles
- Dimensions: `3.240000 x 2.020000 x 2.460000` source tiles
- Materials: 7 flat RGB colors; 96 dark voidwood, 108 gunmetal, 25
  bruised-violet cloth, 25 charcoal cloth-patch, 36 supply-wood, 24 rope, and
  16 bright-violet charm faces
- Components: 4 posts, 4 canopy-frame beams, 4 cloth elements, 7 low
  furnishing groups, 2 ropes, and 2 charms
- OBJ SHA-256: `b921c7a13329e1092229e338b39f1c73c33163ad8dae5af5bd2ea78b09b0e026`
- MTL SHA-256: `63fe6594c8b51f9f370233e371c74fbe9fe967db9ab3f5ba19aaccd17b639146`

## Integrated Cache Artifact

- Archive: `Client_Base/Cache/video/models.orsc`
- Entry: `void_market_shelter.ob3`
- Import transform: Blender axis mapping, scale `128`, centered X/Z, grounded
  RSC Y, double-sided fills
- Vertices / faces: 235 / 330 triangles
- OB3 bounds: X `[-207, 207]`, Y `[-315, 0]`, Z `[-129, 129]`
- OB3 size: 4,384 bytes
- OB3 SHA-256: `b4c487c24502ef9a500fc89658555f88003d957d95f7dce692d7af256e524b9d`

Run `python3 scripts/generate-void-market-shelter-source.py --check` to verify
that the committed source files match the generator byte-for-byte and to print
their exact mesh statistics, bounds, material usage, component counts, and
SHA-256 hashes.
