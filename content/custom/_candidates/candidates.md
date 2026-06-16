# Portal Arch Model Candidates

Scope: candidate sourcing only. Nothing here has been imported into
`models.orsc`, registered as a `GameObjectDef`, or spawned.

Stock OB3 comparison from the current `models.orsc`: median 96 triangulated
faces, p95 846, max 2340. The raw triangle counts below are measured from each
downloaded GLB before cleanup, decimation, recolor, or axis changes.

| Candidate | Source | Direct download | License | Raw tris | Fit note | Preview |
|---|---|---|---|---:|---|---|
| `kay_lousberg_arch` | [Poly Pizza: Arch by Kay Lousberg](https://poly.pizza/m/uS8wgBVxOL) | [GLB](https://static.poly.pizza/482206a2-7c47-4edb-8d6e-d6d8cd55e6cf.glb) | Public Domain (CC0) | 436 | Best stock-range fit: segmented stone ring, upright, open center. | [preview.png](kay_lousberg_arch/preview.png) |
| `quaternius_arch_horseshoe` | [Poly Pizza: Arch by Quaternius](https://poly.pizza/m/PoFx4pgUbg) | [GLB](https://static.poly.pizza/2fd7d232-566e-42f7-a572-c9280768104c.glb) | Public Domain (CC0) | 148 | Extremely cheap and clean, but plain/smooth; would need a Voidstone palette pass. | [preview.png](quaternius_arch_horseshoe/preview.png) |
| `creativetrio_arch` | [Poly Pizza: Arch by CreativeTrio](https://poly.pizza/m/UNjhI7hudt) | [GLB](https://static.poly.pizza/094293c9-d569-43de-a9ba-32066df75ad5.glb) | Public Domain (CC0) | 898 | Slightly above stock p95 but below stock max; raw axes are sideways, so it needs rotation/uprighting. | [preview.png](creativetrio_arch/preview.png) |
| `quaternius_arch_stone` | [Poly Pizza: Arch by Quaternius](https://poly.pizza/m/QwWdOcNIMh) | [GLB](https://static.poly.pizza/b4f2ce7f-e692-4b4c-aa2e-0d6b6e42d552.glb) | Public Domain (CC0) | 4484 | Strongest portal-frame silhouette and block detail, but must be decimated before import. | [preview.png](quaternius_arch_stone/preview.png) |

## Rejected During Search

- [Poly Pizza: Arch Gate by Kay Lousberg](https://poly.pizza/m/fdpw2itXb8) -
  Public Domain (CC0), but rejected because the mesh includes barred gate
  geometry in the center opening.
- [Poly Pizza: Arch Door by Quaternius](https://poly.pizza/bundle/Modular-Dungeons-Pack-HaFPqhAp3w) -
  bundle entry is CC0, but rejected because the previewed asset is a door slab,
  not an open portal frame.
- [Poly Pizza: Rounded Door by Kenney](https://poly.pizza/m/2OSpTlMtpv) -
  Public Domain (CC0), but rejected because it is a door asset rather than an
  open stone arch ring.
- [Poly Pizza: Archway by Poly by Google](https://poly.pizza/m/d6lqRR2TU0i) -
  rejected because the page lists Creative Commons Attribution rather than CC0.
