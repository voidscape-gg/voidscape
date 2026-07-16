# Void Watchtower Source

- Origin: original Voidscape geometry generated in this repository
- Generator: scripts/generate-void-watchtower-source.py
- Imported model name: void_watchtower
- Integrated scenery definition: id 1312
- External assets: none
- Textures, UVs, shaders, animation, and transparency: none
- Coordinate system: Blender-style Z-up; one source unit is one game tile
- Repository license: AGPLv3

The OBJ and MTL are deterministic outputs, not conversions or derivatives of
downloaded work. The standard-library generator directly constructs four
fractured piers, three sparse braces, an open seven-piece lookout, an incomplete
metal cage with jagged crenellations, three inset sigils, and a small suspended
beacon crystal. Every face is a triangle and every material is a flat RGB color.

## Generated Source Statistics

- Vertices: 284
- Faces: 444 triangles
- Named components: 31
- Bounds: X [-0.747652, 0.760732], Y [-0.761980, 0.765039],
  Z [0.000000, 3.455000] source tiles
- Dimensions: 1.508384 by 1.527019 by 3.455000 source tiles
- Material faces: 101 deep-void, 78 dark-voidstone, 86 edge-voidstone,
  106 gunmetal, 69 bruised-violet, and 4 beacon-violet
- OBJ SHA-256: 149bf7093fae3e3e64288ada58e2196f561879d183f4efb6463f353b87af24ca
- MTL SHA-256: 9487387c50510d825b3dfea72c7ccaefcebad4b8f357bfe2af8eb20b2fad5c42

## Integrated Cache Artifact

- Archive: `Client_Base/Cache/video/models.orsc`
- Entry: `void_watchtower.ob3`
- Import transform: Blender axis mapping, scale `128`, centered X/Z, grounded
  RSC Y, double-sided fills
- Vertices / faces: 284 / 444 triangles
- OB3 bounds: X `[-97, 97]`, Y `[-442, 0]`, Z `[-98, 98]`
- OB3 size: 7,036 bytes
- OB3 SHA-256: `c3c525484cffebc3877e706915a429e5c6470c68171c1e992cb3c1a10ae53324`

Run python3 scripts/generate-void-watchtower-source.py --check to validate the
committed files byte-for-byte and print the mesh statistics and reviewed hashes.
