# Voidscape Appearance Studio

Appearance Studio owns stable appearance registration, canonical legacy-frame
compilation, preview/validation, and a transactional authentic-profile
publisher. Planning never writes production targets. Apply requires the exact
hash-bound plan and profile; undo is guarded against later edits unless the
operator explicitly forces restoration.

## Paperdoll V2 R9 evaluation

The true 2x direction has a separate, default-off entry point. Individual
styles remain tmp-only; the content-owned catalog can materialize the complete
male/female evaluation collection:

```bash
scripts/appearance-v2.sh validate-template
scripts/appearance-v2.sh validate-catalog
scripts/appearance-v2.sh init my-hair tmp/appearance-studio/my-hair
scripts/appearance-v2.sh editor tmp/appearance-studio/my-hair
scripts/appearance-v2.sh build tmp/appearance-studio/my-hair
scripts/appearance-v2.sh validate tmp/appearance-studio/my-hair
scripts/appearance-v2.sh validate-pack tmp/appearance-studio/my-hair/build/Paperdoll_V2.orsc
scripts/appearance-v2.sh collection tmp/appearance-studio/my-collection
scripts/appearance-v2.sh build tmp/appearance-studio/my-collection
scripts/appearance-v2.sh test
```

`rsc-player-2x-v1` locks 128×204 walk and 168×204 combat canvases, six pose
masters, anchors, masks, semantic ARGB channels, deterministic propagation, and
runtime mirrors. The browser editor provides paint/erase/pick, file-picker or
clipboard PNG import, nearest-neighbour scale, nudge, mirror, undo/redo,
anchor/mask overlays, and 1×/2× actual-size preview. Builds write only below the
supplied `tmp/` workspace and
emit a strict `Paperdoll_V2.orsc`, 30 panels per stack, and a digest-bound
report marked `shipping:false` and `humanVisualApproval:false`.

Tintable native channels must use neutral grayscale pixels. Imported legacy
skin masks are normalized from `R=255,G=B` to the shared three-level neutral
skin ramp before compilation; preserving the red-mask RGB in a V2 skin channel
would double-tint the red component and create a visible face/body seam.

The R9 content catalog contains two base profiles and six permanent selector
IDs: Rare high spikes, Faded buzzcut, Mohawk, Textured crop, Slick-back
undercut, and High topknot. Selector `0` remains Classic. Two clean catalog
builds produced the same 720-entry, 18-asset, 16-stack pack SHA-256:
`46f3fe89c01f55563b5692711ee054082ebd610d123cba506bae64cb334adbb7`.
The selector and compatibility sidecars are also deterministic. The final
Python/Java comparison has zero mismatched frames and pixels.

The catalog and reports remain non-shipping/default-off. Builds do not write
caches, Authentic archives, MD5 data, definitions, selector maxima, packets,
saves, or avatar output. The 1x compatibility overlays and avatar thumbnails
are build-only evaluation artifacts.

### Content-owned hairstyle contract

Each promoted style owns six master PNGs and `art.json` under
`content/appearance/v2/hairstyles/<style>/`. Its permanent catalog record binds
the directory, every master digest, tint role, mask/attachment/connectivity
rules, eligible base profiles/platforms, and selector ID. Selector IDs are
never reused. `validate-catalog` rejects missing/drifted masters, invalid routes,
and a catalog that claims shipping/default activation.

Use `collection` only after individual workspace panels have human approval.
Build two clean collection workspaces and compare all top-level artifact hashes
before runtime QA. The authoritative Java comparison is:

```bash
scripts/appearance-v2.sh compare-oracle \
  tmp/appearance-studio/my-collection \
  tmp/appearance-studio/my-collection/build/java-oracle/<run>/report.json \
  tmp/appearance-studio/my-collection/evidence/java-oracle
```

### Guarded PC runtime

`scripts/run-workbench-client.sh --paperdoll-v2-workspace <workspace>` loads the
explicit tmp pack, sibling selector registry, and compatibility properties.
Add `--paperdoll-v2-designer-evaluation` only against the separately gated
disposable QA server. The launcher refuses a non-loopback designer server,
missing/misplaced sidecars, or incompatible flag combinations.

The runtime uses V2 only for selectors `1..6`, head `8`, male/female body `2`/
`5`, legs `3`, and no hat. All other cases select the complete legacy player
before drawing. Forced, missing, and rejected packs may use the digest-bound 1x
hair overlay; any nonzero hat suppresses it. The current adoption decision is
GO for staff-only PC disposable-QA evaluation and NO-GO for production default,
Android, TeaVM, and avatars. See
`docs/reports/paperdoll-v2-evaluation-2026-07-11.md`.

```bash
scripts/content.sh appearance validate
scripts/content.sh appearance validate --strict
scripts/content.sh appearance plan
scripts/content.sh appearance verify-workbench --report report.json --out tmp/evidence
scripts/content.sh appearance compare-workbench --report report.json --fixture tmp/evidence/composite-fixture.json --out tmp/evidence/client-preview
scripts/content.sh appearance cowboy-compare
scripts/content.sh appearance diagnose-adopted-cowboy
scripts/content.sh appearance semantic-evidence
scripts/content.sh appearance synthetic-demo
scripts/content.sh appearance headwear-init --name test-hat --reference partyhat --out tmp/appearance-studio/headwear/test-hat
scripts/content.sh appearance headwear-build --root tmp/appearance-studio/headwear/test-hat
scripts/content.sh appearance studio --workspace tmp/appearance-studio/workspace
scripts/content.sh appearance studio --cowboy
scripts/content.sh appearance look-plan --manifest content/appearance/proposals/future_mullet_mustache.yaml
scripts/content.sh appearance calibrate-template --out tmp/appearance-studio/r1/calibration
scripts/content.sh appearance publish-plan --bundle tmp/appearance-studio/my-run
scripts/content.sh appearance validate-plan --plan tmp/appearance-studio/my-run/plan.json --profile authentic
scripts/content.sh appearance apply --plan tmp/appearance-studio/my-run/plan.json --profile authentic
scripts/content.sh appearance undo --manifest tmp/appearance-studio/my-run/undo.json --profile authentic
```

## Manual headwear loop

`headwear-init` creates a deliberately small, non-shipping artist workspace
under `tmp/`. Its six transparent `masters/*.png` files are the actual logical
RSC canvases: five are 64x102 and `combat-west` is 84x102. The matching
`guides/*.png` files contain the real bald head, body, and legs at the same
coordinates. Put a guide underneath its master in any nearest-neighbour pixel
editor and move or repaint the hat until it sits correctly on the player.

`headwear-build` hardens alpha, protects opaque black from the legacy
transparency sentinel, propagates the six masters across all 18 stored frames,
derives each crop and sidecar from the authored placement, and writes 30
individual 88x112 player previews plus `build/contact-sheet.png`. The report is
deterministic and always records `shipping:false` and
`humanVisualApproval:false`. These commands cannot write outside `tmp/` and do
not modify either Authentic archive, definitions, IDs, or MD5 data. Production
packing is a separate approval-gated slice.

Exit codes are `0` for success, `1` for validation errors (or warnings under
`--strict`), and `2` for usage or configuration failures. The authentic profile
is the only supported Slice 1 target; custom-sprite profiles fail closed.

Every appearance owns a stable one-based appearance ID and reserves exactly 27
sprite indices, even when the legacy renderer loads only 15 or 18 frames. Any
numeric ZIP member not explicitly adopted by the registry remains occupied.
The approved managed range is `3705..3974`: ten complete reservations that are
currently empty in both archives and fit both sprite arrays. The generated
client/avatar bridge resolves managed IDs before the untouched legacy fallback.

Slice 3 introduced the first `rsc-player-v1` logical canvases and compiler. Its
global anchor/rectangle premise was later revoked by R0. R1 replaces it with
the calibrated v2 pose contract described below. Production authoring Studio is
disabled until R4 consumes those real masks; Cowboy comparison remains
read-only and never changes its pixels.

Slice 4 adds deterministic Cowboy candidate generation for its item definition,
appearance definition, constants, generated client/avatar registries, both
Authentic archives, client MD5 row, icon contract, and content metadata. The
first plan is intentionally byte-preserving for art and archives. Candidate
plans contain exact source/payload/preimage hashes, schema/tool/profile bindings,
and enough preimage data for rollback and guarded undo. Production apply remains
disabled by the Gate 4 review; the checked evidence applies only to copied roots.

Slice 5 adds production-capable Look and authoring-layer manifests. A Look keeps
hair and facial hair as separate transparent source layers, composes them over
the locked bald base, and emits one legacy 18-frame head block. `look-plan` is
advisory-only. The mullet + mustache allocation is now reserved at appearance
`247` and block `3705..3731`, but remains non-selectable with null production
art and unapproved production pixels.

The old `draft-look`, `art-qa`, and `review-draft-look` v1 evidence path is
fail-closed. It cannot generate or bless replacement pixels until R2–R5 provide
the exact compositor, semantic QA, and pose-mask editor.

### R0 — revoked Gate 5 evidence

The first Gate 5 draft and its zero-finding QA result are **revoked as visual
evidence**. Panel-by-panel review exposed structural defects: profile and combat
mustaches can attach behind the face; one global anchor model cannot describe
front, three-quarter, profile, and rear anatomy; the hair envelope excludes the
nape required by a mullet; combat previews can compress an 84-pixel logical
canvas into 64 pixels; undersized contact-sheet cells overlap; Studio anchor
overlays are hard-coded guides rather than the compiler's real landmarks; and
the current overlap/contact metrics can reward generic blobs and skin overlap
without proving anatomical attachment. Direction labels also hide the actual
visible pose being judged. Passing these checks means only that the current
mechanical rules were satisfied; it does not mean that the result looks correct.

Until remediation completes, draft output must not be cited as a valid visual
gate. Appearance `247` and sprite block `3705..3731` remain reservation-only:
production art is null, production pixels are unapproved, the preset is
non-selectable, the registry entry is reservation-only, both archives contain
no Look sprites, and no checksum or publish target has changed. R1's calibrated result is recorded below;
preview, QA, Studio, and pixel work
remain gated behind R2–R5.

### R1 — calibrated pose geometry

`rsc-player-v1` is now a v2 contract with six explicit visible poses: front,
three-quarter-front, profile, three-quarter-rear, rear, and combat-profile.
Each pose owns crown-relative forehead, face, nose, upper-lip, chin, ear,
occiput, neck, and nape landmarks plus digest-locked, full-canvas 1-bit masks for
allowed hair/facial hair, scalp and upper-lip attachment, nape-tail occupancy,
face and neck clearance, and protected anatomy. Rear facial targets are
explicitly null.
Combat landmarks and masks translate by the frame's `+5`/`+11` crown delta.

Canonical `head1`, `fhead1`, `head3`, `head4`, body, and legs references live
under the template and encode byte-identically to both Authentic archives.
`calibrate-template` verifies the locked derivation and writes six individual
review panels plus a combined non-shipping sheet. `--write-masks` is a
deterministic rebuild check. Hair/facial-hair manifests now declare
`propagation: rigid-head` and semantic required masks; other layer families fail
closed until they receive articulated templates.

### R2 — isolated renderer parity

`scripts/run-appearance-qa.sh 245` now captures two distinct forms of evidence.
The ordinary 640x480 world screenshots are supplemental visual context only.
The authoritative oracle is a Workbench-gated, opaque RGB 88x112 isolated
player raster produced by the same Java `drawPlayerCompositeLayers` body on a
separate cached graphics target. It records the exact 12-layer animation and
draw order, resolved definitions and palette, mirror/frame inputs, 64x102 draw
rectangle at `(12,2)`, output/crop geometry, sprite-archive digest, and raw RGB
digest while leaving the live surface untouched and restoring player state.

`verify-workbench` extracts those inputs into a digest-bound composite fixture.
`compare-workbench` runs the offline integer compositor in the identical
30-state order and compares every pixel against the isolated Java oracle. The
2026-07-10 Cowboy run passed with 0 mismatched frames and 0 mismatched pixels;
all 36 Cowboy PNG/sidecar files and both Authentic archives were unchanged.
This establishes mechanism parity only for the captured fixture inputs. It does
not make the visibly poor Cowboy artwork acceptable, does not establish
whole-world screenshot equality, and does not complete R3 semantic art QA or
any art correction.

### R3 — semantic attachment and composed-Look QA

Semantic QA v2 replaces the revoked blob/contact scores with pose-specific,
integer rules. Mullet layers must keep one dominant 8-connected component on
the scalp, reach usable nape pixels through the digest-locked neck-clearance
mask, meet per-pose occupancy/contact floors, and remain phase-stable. Mustache
layers are restricted to the upper-lip envelope, require one or two attached
lobes of at least two pixels, require both sides in front/three-quarter views,
and remain empty on rear views. Stored sidecars and canonical runtime mirrors
are re-derived rather than trusted.

Composed Look QA evaluates all 18 stored frames and the canonical 30 runtime
states. It rejects cross-layer overlap/occlusion, changes to the locked base
outside authored pixels, incomplete mirror/crop evidence, stale manifests,
changed template/reference/mask digests, forged thresholds, incomplete metrics,
and any report containing an error. Reports are always marked `shipping:false`,
`automatedContractOnly:true`, and `humanVisualApproval:false`. The
`semantic-evidence` command writes deterministic synthetic positive and
adversarial contract fixtures under `tmp/`; they are not proposed artwork.

The separate `diagnose-adopted-cowboy` command is compatibility-only. It proves
the existing Cowboy bytes and exact R2 compositor remain mechanically valid,
while recording `knownBad:true`, `visuallyAcceptable:false`, and
`humanVisualApproval:false`. Its six pose panels expose the existing
three-quarter/profile coverage, axis, and combat-silhouette failures. It is not
imported by the compiler, candidate builder, or publisher and does not enable
hat authoring. R4 must put the real pose masks and landmarks into the editor;
replacement pixels still require a later explicit human visual gate.

### R9 — complete PC hairstyle evaluation checkpoint

R9 is the separate true-2x hairstyle track. It promotes six digest-bound master
sets, male/female native bases, stable selectors, strict pack/registry loading,
whole-player fallback, generated 1x compatibility overlays, the guarded in-game
designer, and complete Workbench matrices. It does not promote the legacy
Cowboy or mullet/mustache drafts and does not make their earlier gates valid.

The final active/default matrix has 420 panels: 360 V2 hairstyle/profile states
and 60 Classic legacy states. Alternate palette and equipped matrices preserve
the same routing. Ordinary and Cowboy hats produce 420 legacy panels with all
nonzero styles suppressed. Forced, missing-pack, and rejected-pack matrices
produce 420 legacy panels with 360 compatibility overlays. Independent combat
framing review found no edge contact after the capture-origin correction.

The evaluation server/designer gate, persistence/multiplayer evidence,
performance measurements, exact hashes, remaining platform/avatar blockers,
and rollback are recorded in
`docs/reports/paperdoll-v2-evaluation-2026-07-11.md`. Production archives and
configuration are not R9 output targets; this statement is scoped to the V2
workflow and does not assert that those dirty-worktree files match Git HEAD.
