# Void Rift Structure Source

- Origin: original Voidscape geometry generated in this repository
- Generator: `scripts/generate-void-rift-source.py`
- External assets: none
- Textures, UVs, shaders, animation, and transparency: none
- Coordinate system: Blender-style Z-up; one source unit is one game tile
- Repository license: AGPLv3

The OBJ and MTL are deterministic outputs, not conversions or derivatives of
downloaded work. The generator uses only Python's standard library and fixed
numeric specifications. It creates the segmented plinth, broad recessed core,
six varied supports, and three broken crown ribs directly as triangle meshes.

## Generated Source Statistics

- Vertices: 277
- Faces: 456 triangles
- Bounds: X `[-0.863003, 0.851883]`, Y `[-0.852140, 0.857449]`,
  Z `[0.000000, 2.380632]` source tiles
- Dimensions: `1.714886 x 1.709590 x 2.380632` source tiles
- Materials: 5 flat RGB colors; 24 core, 184 dark voidstone, 128 gunmetal,
  72 bruised-violet, and 48 rift-violet faces
- OBJ SHA-256: `49131c249d3c8ed9d16af1ef452b87ce953463daa0a2b0ff87f222f48fc2210f`
- MTL SHA-256: `108801d8add8cf23b9a2ac2dc7431cceddf784126f112adeed005e53b552432e`

Run `python3 scripts/generate-void-rift-source.py --check` to verify that the
committed source files match the original generator byte-for-byte and to print
their exact mesh statistics, bounds, material usage, and SHA-256 hashes.
