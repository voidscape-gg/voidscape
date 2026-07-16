# Void Watchtower

## Decisions

- Generate wholly original, deterministic geometry in-repository; no external
  models, textures, UVs, shaders, or animation are involved.
- Keep the source footprint near 1.6 by 1.6 tiles and the silhouette 3.455
  tiles tall so the tower reads above the Enclave walls without becoming a
  massive opaque landmark.
- Use four asymmetric, leaning voidstone piers. The southeast pier is snapped
  below the lookout, while three narrow braces imply a once-complete frame.
- Build the lookout as seven separated wedges from a nine-part ring. Its center
  and two perimeter sections remain open, so it never becomes a complete slab
  that hides a player.
- Break the cage into five uneven posts, three rails, and five individually
  fractured crenellations. The incomplete circumference preserves sightlines
  and makes the ruin readable from every RSC camera quadrant.
- Restrict the bright violet to two beacon facets and one small pier sigil.
  Most surfaces use deep void, voidstone, and gunmetal flat colors.
- Keep all source faces triangular. Integration supplies the double-sided OB3
  faces needed by the rotating RuneScape Classic camera.

## Regeneration

    scripts/generate-void-watchtower-source.py
    scripts/generate-void-watchtower-source.py --check

The check rebuilds the mesh twice, validates exact group and material counts,
geometry bounds, grounding, the open platform center, non-degenerate triangles,
reviewed SHA-256 values, and committed OBJ/MTL bytes.

## Test Notes

- Integrated source output is 284 vertices and 444 triangles across 31 named
  components.
- Source bounds are 1.508384 by 1.527019 by 3.455000 tiles.
- Imported archive entry void_watchtower.ob3 is 7,036 bytes, using scale 128,
  Blender-style Z-up axis mapping, and double-sided face fills.
- Scenery id 1312 is a decorative, nonblocking two-by-two, type-zero object.
  Its three approved outer-corner placements are (100,302) direction 1,
  (124,302) direction 3, and (125,325) direction 6.
- Inspect all three instances from four camera quadrants at near and far zoom.
- Confirm the broken platform and open pier frame never fully hide a player.
- Confirm the small suspended beacon stays subordinate to the main Void Rift.
