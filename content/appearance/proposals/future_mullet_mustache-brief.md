# Mullet + mustache Look — review brief

Status: proposal only. This document does not authorize image generation, master PNG creation, registry allocation, archive packing, or release.

The Look compiles two independently editable transparent layers—`future_mullet` and `future_mustache`—over the template's locked bald-head reference. The result is one ordinary 18-frame legacy head block. Runtime mirroring supplies east, north-east, south-east, and Combat B; no mirrored masters are authored.

## Proposed identity and allocation

- Stable appearance ID: `247`.
- Authentic sprite reservation: `3705..3731` (the first approved managed 27-slot block).
- Loaded output: `3705..3722`; `3723..3731` remains reserved.
- Animation: `mulletmustache`, player category, hair/skin masks matching the locked base, selectable head slot `0`.
- Eligibility: proposed as male-only (`gender_model: 5`) because the defining facial-hair silhouette is part of this named Look. A later separately reviewed variation can be unisex; Gate 5 may change this before reservation.

The proposal validator must continue to prove that ID `247` is neither owned nor tombstoned and that `3705..3731` is inside the approved managed namespace and absent from both archives. Approval would reserve these values in a later explicit registry change; this slice does not do so.

## Art direction

The mullet should read immediately at native RSC scale: restrained volume at the forehead and crown, short sides that preserve the face silhouette, and a longer rear section reaching toward—but not covering—the neck. It should feel scruffy and period-appropriate rather than glossy, photoreal, oversized, or comedic.

The mustache should be broad enough to survive at actual size, centered beneath the nose, and clearly separate from the mouth and chin. Avoid beard coverage, curled handlebar tips, skin-coloured pixels, or facial geometry changes.

Both layers use only transparent pixels plus exact grayscale hair-mask values `103`, `132`, and `160`. Edges are hard, single-pixel, and anti-alias-free. The locked head, face, neck, pose, canvas, and attachment anchors cannot move. Hair and mustache remain separate source layers even though publishing bakes them into one block.

AI imagery may be used only as a quarantined concept/import source. Every final pixel must be normalized and manually approved in Appearance Studio; AI output cannot establish final geometry, propagation, mirroring, or frame consistency.

## Review evidence required before art approval

- Layer-only matrices for hair and mustache.
- Composed and actual-size previews for all 18 stored frames and all runtime-mirrored directions.
- Zero palette, protected-anatomy, attachment, and cross-layer overlap errors.
- Deterministic encoded output and correct sidecars.
- A 30-state Workbench matrix and avatar render after a separately approved publish plan.
