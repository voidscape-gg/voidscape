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
six varied supports, three broken crown ribs, and central faceted tear directly
as triangle meshes.

## Generated Source Statistics

- Vertices: 391
- Faces: 584 triangles
- Bounds: X `[-0.960777, 0.957317]`, Y `[-0.921349, 0.925656]`,
  Z `[0.000000, 1.989281]` source tiles
- Dimensions: `1.918094 x 1.847005 x 1.989281` source tiles
- Materials: 5 flat RGB colors; 48 core, 178 dark voidstone, 192 gunmetal,
  134 bruised-violet, and 32 rift-violet faces
- OBJ SHA-256: `6399773f347f9247bbc012db2c896a9955aea4d07281b7832a354f4262fa186b`
- MTL SHA-256: `1632b4bc040d5cd2b53fb248348c25292296b4100a01915e95648aa1dd0497f3`

Run `python3 scripts/generate-void-rift-source.py --check` to verify that the
committed source files match the original generator byte-for-byte and to print
their exact mesh statistics, bounds, material usage, and SHA-256 hashes.
