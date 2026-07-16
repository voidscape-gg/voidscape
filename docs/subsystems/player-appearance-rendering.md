# Player appearance rendering

Voidscape inherits the OpenRSC RuneScape Classic paperdoll renderer. The server
does not render player pixels. It sends appearance animation IDs and colour
indexes; the client draws layered 2D sprite flipbooks every frame.

## Paperdoll V2 status

The approved visual direction is a true 2x paperdoll for the custom PC client,
with the existing renderer retained as the shipping path and rollback. The R9
checkpoint implements a default-off, Workbench-gated runtime evaluation for
male and female bases plus six stable hairstyle selectors. It is approved only
for staff on a disposable local QA server; production default, Android, TeaVM,
and avatar rendering remain unapproved.

Android debug and TeaVM compilation/package tests pass through the shared
client source, but `mudclient` deliberately leaves `paperdollV2Runtime` null for
both platform builds. Compilation is not activation or parity evidence.

`rsc-player-2x-v1` defines 128x204 walking and 168x204 combat canvases, six
locked pose masters, semantic ARGB channels, anchors/masks, deterministic
propagation and mirroring, and a strict `Paperdoll_V2.orsc` pack. The browser
editor and Java Workbench renderer exercise the same contract. The PC scene
proof uses a 1024x668 game area plus the 12-pixel footer and projection shift
10, preserving the classic field of view at true 2x density.

The canonical catalog is `content/appearance/v2/catalog.yaml`. Selector `0`
means Classic; selectors `1..6` are Rare high spikes, Faded buzzcut, Mohawk,
Textured crop, Slick-back undercut, and High topknot. The catalog declares
`shipping:false` and `defaultEnabled:false`. Its R9 SHA-256 is
`b9080221cfd5843b25e84faa814ce3860cc599d550fccd796b2ed00be71050b9`;
the deterministic pack SHA-256 is
`46f3fe89c01f55563b5692711ee054082ebd610d123cba506bae64cb334adbb7`.

The PC runtime accepts only an explicit, canonical, non-symlinked pack below
repository `tmp/`, validates pinned template/source digests, and validates a
sibling selector registry before activation. It preflights one immutable
selector/profile result for the complete player. V2 is used only for head `8`,
male body `2` or female body `5`, legs `3`, a known selector, valid geometry,
and an empty hat slot. Any failure chooses the complete legacy player before a
V2 pixel is drawn; it never substitutes frame zero and never leaves a partial
V2 player. Weapons, shields, and non-substituted compatible equipment remain
legacy-composited around the V2 base.

The compiler also emits digest-bound 1x hairstyle overlays. They preserve the
style when V2 is forced off, missing, or rejected, but only for compatible head
`8` with no hat. Any ordinary or Cowboy hat suppresses both V2 and that overlay,
so the full legacy hat composition wins. This behavior passed complete
420-panel matrices for active, forced, missing-pack, corrupt-pack, ordinary-hat,
and Cowboy-hat cases.

See `docs/reports/paperdoll-v2-evaluation-2026-07-11.md` for deterministic
hashes, visual/runtime evidence, blockers, adoption recommendation, and
rollback.

V2 semantic skin layers are neutral grayscale, not copied legacy red-mask RGB.
When importing `R=255,G=B` skin pixels, the compiler-facing workspace converts
the legacy shade byte to `0x76`, `0xB0`, or `0xFF` before applying the selected
skin RGB. This keeps Dawn/Rose/Bronze/Umber/Ash heads visually aligned with live
legacy arms during the hybrid proof; fixed eyes and facial details remain in
their untouched fixed channel.

## Server state

`server/src/com/openrsc/server/model/PlayerAppearance.java` stores the player's
base colours and selectable body/head IDs:

- `hairColour`, `topColour`, `trouserColour`, `skinColour`
- `head`, `body`
- `getSprites()` returns the unequipped 12-slot appearance array:
  head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet,
  cape.

`server/src/com/openrsc/server/model/entity/player/Player.java` stores the live
merged appearance in `wornItems`. Login initializes it from
`PlayerAppearance.getSprites()` in
`server/src/com/openrsc/server/service/PlayerService.java`, then equipment
changes call `Player.updateWornItems(...)` to replace the affected slot.

`server/src/com/openrsc/server/constants/AppearanceId.java` defines the slot
numbers. The first twelve slots are transmitted to clients:

- `0` head
- `1` shirt
- `2` pants
- `3` shield
- `4` weapon
- `5` hat
- `6` body armour
- `7` leg armour
- `8` gloves
- `9` boots
- `10` amulet
- `11` cape

### Paperdoll V2 evaluation policy

Production leaves `paperdoll_v2_evaluation_hair_style_max` absent or `0`.
Positive V2 selectors are available only when the QA server passes the complete
startup policy in
`server/src/com/openrsc/server/appearance/PaperdollV2EvaluationPolicy.java`:
the maximum is exactly `6`, the JVM has
`-Dvoidscape.paperdollV2.evaluationServer=true`, the database is disposable
SQLite with a name ending `_qa`, production command lockdown is off,
`avatar_generator` is false, and `character_creation_mode` is `0`. A partial or
unsafe configuration aborts startup.

The appearance handler additionally requires a server-developer player, a
modern client, valid male/female restriction, selector `1..6`, and the exact V2
head/body identity. Accepted values use the existing `hairstyle` save field;
no schema or opcode changed. Login rehydrates that evaluation appearance only
for the same guarded dev cohort. Outside it, the production maximum remains
zero and unsupported positive styles resolve to Classic, which is why the
evaluation must never use a production or reusable database.

Opening the evaluation designer also checks the live merged slots. A hidden
head, a body other than `2`/`5`, or legs other than `3` blocks the session and
asks the player to unequip plate/head-slot gear. First-login character creation
is never session-eligible and retains the normal referral control without a V2
Style row.

## Network contract

`server/src/com/openrsc/server/GameStateUpdater.java` writes the appearance
update. Normal custom-client updates send the `wornItems` count, then each
appearance ID as a `short`, followed by hair, top, trouser, and skin colour
indexes. Modern custom clients already receive the existing one-byte
`hairStyle` tail, which R9 reuses for selector `1..6`. Retro/authentic clients
receive byte-sized or converted IDs and no V2 route.

Changing the number, meaning, or encoding of appearance fields would be a packet
contract change and must follow `docs/subsystems/networking-protocol.md`.
Adding another head animation normally does not need a new opcode if it uses the
existing appearance ID flow.

## Client composition

`Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` defines
client `AnimationDef` entries. Each definition has:

- animation name, such as `head1`, `head4`, `sword`
- category, such as `player` or `equipment`
- `charColour`, which decides the primary recolour mask
- `genderModel`
- `hasA` combat-facing frames
- `hasF` mirrored/special frames

`Client_Base/src/orsc/mudclient.java` assigns runtime sprite bases in
`loadEntitiesAuthentic()`. Unique animation names reserve sprite blocks; repeated
names share the same block with different recolours.

Compiler-managed appearances now resolve through generated authentic-profile
registries before this legacy positional fallback. The client uses the one-based
appearance ID to obtain an explicit `AnimationDef` and sprite base; the server
avatar generator uses the same ID/base contract. Cowboy `245` is the adopted
compatibility fixture at base `1890`. Managed IDs fail closed under
`custom_sprites:true`, while every unmanaged ID retains the original positional
lookup. New managed blocks are reserved at the Gate-1-approved `3705..3974`
range and bypass source-order allocation.

`Client_Base/src/orsc/PacketHandler.java` reads appearance updates into
`ORSCharacter.layerAnimation[]`, then stores colour indexes on the character.

`Client_Base/src/orsc/mudclient.java` draws players in `drawPlayer(...)`. The
renderer computes the current direction and animation frame, then loops through
`animDirLayer_To_CharLayer` so layers draw in the correct front/back order. For
each visible slot, it looks up the `AnimationDef`, selects the sprite frame,
applies sidecar shifts and scaling, recolours mask pixels, and draws onto the
scene.

## Mask colours

The sprite pixels are tiny palette masks, not full-colour art:

- grayscale pixels (`R == G == B`) are recoloured by the definition's
  `charColour`.
- if `charColour` is `1`, the client uses the player's hair colour.
- if `charColour` is `2`, the client uses the player's top colour.
- if `charColour` is `3`, the client uses the player's trouser colour.
- pixels where `R == 255 && G == B` are recoloured by the player's skin colour.
- archive pixel value zero is transparent.

Voidscape custom hair colours (`Void`, `Frost`, `Blood`, `Ember`, `Gold`,
`Toxic`, `Moon`, and `Coal`) normalize the default head/beard grayscale clusters
to the side-swept PNG overlay's three neutral shade values (`#5c5c5c`,
`#8a8a8a`, `#ffffff`). The client then applies the same colour transform used by
the swept overlay, so every default head and beard keeps its authentic
silhouette while matching the richer custom colours from the prototype.

The Top/Bottom selectors include the classic clothing colours plus the appended
Voidscape clothing colours. Classic clothing keeps the original grayscale
multiply path; the Voidscape clothing colours reuse the same eight colour
families with muted RGB values and a softer five-step shade ramp for shirt and
trouser grayscale masks. Trial skin tones use their own appended palette indexes
(`Dawn`, `Rose`, `Bronze`, `Umber`, `Ash`) plus a skin-specific three-shade ramp
on the `R == 255 && G == B` skin mask pixels. This is still the original RSC
paperdoll pipeline; only the palette indexes and mask-to-shade mapping changed.

AI-generated anti-aliasing or arbitrary full-colour pixels will not recolour
like authentic RSC art.

## Hairstyle implication

Vanilla RSC hair is baked into the head animation sheet. Changing hair by
redrawing a whole head is fragile because any face, neck, or sidecar drift
becomes visible when the client attaches the head to the fixed player body.

Voidscape currently ships the default/classic head workflow. The production
appearance maximum remains zero. The guarded R9 evaluation reuses the existing
`hairStyle` byte for stable V2 selectors `1..6`, while its generated 1x overlays
serve only as a Workbench fallback. Neither output is installed into a
production cache or avatar path.

Safe art workflows:

1. For authentic/default heads, extract a locked vanilla bald base, author
   transparent hair-only frames, preview the locked base plus hair on the real
   client-scale paperdoll, then bake the hair onto the base to produce a normal
   full-head frame set.
2. For the true 2x PC evaluation, author six transparent masters through
   Appearance Studio, add a stable catalog selector, build a tmp-only pack and
   compatibility export, and pass the compiler, Java oracle, complete runtime
   matrix, persistence, multiplayer, and fallback gates. Production activation
   is a later release decision.

See `docs/recipes/add-custom-hairstyle.md` and
`tools/hairstyle-art/README.md` for the current tooling.

## Canonical authoring template

Appearance Studio uses the locked `rsc-player-v1` v2 template rather than
treating cropped PNG dimensions as geometry. Walking layers use 64x102 logical
canvases and combat layers use 84x102. Six legacy direction masters map to
explicit visible poses: front, three-quarter-front, profile,
three-quarter-rear, rear, and combat-profile. Each pose owns crown-relative
anatomy landmarks and digest-locked full-canvas 1-bit masks for legal geometry,
scalp/upper-lip attachment, nape occupancy, face clearance, and protected
anatomy. Combat phases translate these contracts by the same crown delta as the
sprite. Southeast, east, northeast, and Combat B remain runtime mirrors.

Canonical bald, short-hair, long-hair, long-beard, body, and legs references
are owned by the template and encode byte-identically to both Authentic
archives. The rigid compiler currently accepts only calibrated hair and
facial-hair layers; hats, clothing, hands, and equipment require their own later
contracts. Cowboy remains a read-only legacy comparison.

## Transactional publishing

`scripts/content.sh appearance publish-plan --bundle <run-directory>` renders
all Cowboy-owned integration regions from its appearance manifest, stages both
Authentic archives, verifies worn-entry parity and the client-only inventory
icon, updates the staged client MD5 row, and creates a content-addressed plan.
The plan binds the exact schema, tool version, authentic output profile,
candidate payloads, target preimages, modes, and paths.

`validate-plan` rejects stale targets or tampered blobs. `apply` replaces files
through same-directory temporary files and rolls every prior replacement back
if any step or undo-receipt write fails. `undo` restores byte preimages only
while every target still equals its planned postimage; `--force` is the explicit
escape hatch for intentionally discarding later edits. Client and server
archives remain independent ZIP roots: only the managed worn entries must be
identical, while inventory icon entry `2788` remains client-only and only the
client archive is recorded in `MD5.SUM`.

## Bundled Looks and character selection

A Look is still one ordinary saved head appearance. Its manifest references
ordered transparent authoring-layer manifests, so hair and facial hair remain
independently editable while compilation bakes them over the digest-locked bald
base into one 18-frame block. No new paperdoll slot, save field, or multiplayer
packet is introduced.

Generated client/server `GeneratedLookPresets` tables preserve legacy selectable
heads `1, 4, 6, 7, 8` and add only active managed Looks. The client stores the
selector as the existing zero-based wire value but resolves previews through the
one-based managed appearance lookup. The server decodes that byte unsigned,
validates the resulting stable ID through the same generated table, and saves it
in the existing `headsprite` field. Retro clients receive the Look manifest's
generated fallback; unsupported custom-sprite profiles remain excluded.

The reserved `future_mullet_mustache` Look is the first draft-art fixture:
appearance `247`, sprite block `3705..3731`, male-only, retro fallback `8`.
Its separate mullet and mustache layers compile deterministically to one head;
rear-facing mustache masters are explicitly hidden. `draft-look`, `art-qa`, and
`review-draft-look` operate only under `tmp/` and produce full-player evidence
for every walk/combat direction and phase. This reservation is not active:
production art is null, the preset is non-selectable, and the archives contain
no entries in its reserved block.

R0 records that the initial Gate 5 output is **not valid visual evidence**,
despite its zero-finding automated report. Individual panels show
profile/combat mustaches anchored behind the face, while the single
global-anchor contract cannot model
front, three-quarter, profile, and rear anatomy. The permitted hair envelope
does not reach the nape, so it cannot express the defining rear mass of a
mullet. The preview path also compresses 84-pixel combat geometry into 64
pixels, uses undersized contact-sheet cells that overlap, and labels source
directions instead of the actual visible poses. Studio's displayed anchor marks
are hard-coded decorations rather than the compiler landmarks, and current
contact/overlap thresholds can score detached-looking blobs as valid merely for
touching skin. Those checks prove conformance to the implemented geometry, not
correct placement or acceptable appearance.

R1 replaced that faulty premise with canonical per-pose anatomy: explicit
upper-lip, nose, forehead, scalp, ear, neck, nape, and protected landmarks for
front, three-quarter, profile, rear-three-quarter, rear, and combat-profile
views. Normative masks are mode-1 PNGs derived from locked authentic references,
not generic rectangles. Mullet manifests require scalp and nape masks;
mustaches require upper-lip attachment and hide both rear masters. The old
authoring Studio now fails closed until R4 displays these real pose masks.
`scripts/content.sh appearance calibrate-template` produces individual review
panels and a combined deterministic sheet.

R2 now ports the exact integer sidecar placement, per-direction layer order,
combat centering, runtime mirror behavior, palette-mask transforms, and legacy
limb adjustments into the offline compositor. Workbench supplies a self-contained
oracle fixture rather than making Python rediscover `AnimationDef` source order:
the authentic archive digest, 12 appearance IDs and resolved definitions,
palette indices/RGB, flags, draw order, and all state inputs are explicit. The
same Java compositor body renders an isolated opaque RGB 88x112 oracle on a
separate diagnostics-only target; world screenshots remain supplemental. A live
Cowboy run matched all 30 isolated states exactly (0 mismatched frames and 0
pixels), restored the local player, and left all Cowboy sources and both archives
unchanged.

That parity proves the preview mechanism for the captured fixture, not visual
quality. Cowboy still looks wrong and remains a read-only compatibility fixture;
its pixels were not corrected.

R3 adds a separate semantic contract over the calibrated geometry. Hair must
stay inside the allowed mask, avoid face/protected/neck-clearance pixels, attach
its dominant 8-connected component to the scalp, and—where applicable—reach
usable nape pixels on that same component. Facial hair must fit the upper-lip
envelope, form one or two attached lobes, cover both sides in visible frontal
poses, face forward in profile, and stay absent on rear masters. Rigid phases
must normalize identically by crown delta; stored and mirrored crops/sidecars
are re-derived.

The composed Look validator checks ordered layers over the locked bald base for
all 18 stored and 30 runtime states. Any collision, later-layer occlusion, base
mutation outside authored pixels, stale digest, forged threshold, incomplete
metric set, or error finding invalidates the evidence. Passing reports are still
machine-labelled non-shipping, automated-only, and lacking human visual
approval. Synthetic fixtures prove the rules accept and reject the intended
geometry; they are not art samples.

The Cowboy remains outside that authoring contract. Its compatibility-only
diagnostic confirms archive/R2-renderer mechanics while explicitly marking the
art known-bad and visually unacceptable. Per-pose panels show three-quarter
hair exposure, rear-three-quarter axis drift, and a profile/combat silhouette
ratio of 2.0. The reserved Look registry entry remains non-runtime: ID `247`
and `3705..3731` are reserved, art is null and unapproved, the preset is not
selectable, and both archives contain no entry in that block. R4 editor work and
later human art approval are still required before new pixels can ship.

## R9 hairstyle evaluation boundary

R9 promotes six reviewed hairstyle master sets into
`content/appearance/v2/hairstyles/` and replaces the original one-style proof
catalog. Two independent builds produced identical pack, selector, compatibility,
and report bytes. The build validates 16 male/female control/style stacks and
all 30 canonical states. The final offline/Java comparison reports zero
mismatched frames and pixels.

Live Workbench validation covered default, alternate palette, fully equipped,
ordinary-hat, Cowboy-hat, forced-legacy, missing-pack, and rejected-pack
matrices. The corrected active matrix contains 360 V2 style panels and 60
Classic legacy panels. Hat matrices contain 420 whole-player legacy panels and
no visible nonzero hairstyle. Compatibility-only matrices contain 360 1x
overlay panels plus 60 Classic panels. Selector preflight and a full three-slot
render probe allocated zero bytes; local/remote telemetry distinguishes the V2
and legacy paths.

The adoption boundary is intentionally narrow:

- **GO:** staff-only PC testing, Workbench launch flags, explicit tmp pack,
  disposable SQLite `*_qa` database, server-developer accounts.
- **NO-GO:** production default, public accounts, production/reusable saves,
  Android, TeaVM, live avatar generation, and production cache/archive/MD5
  publication.
- **Rollback:** remove the client/server evaluation flags and discard the QA
  database. The unchanged legacy renderer resumes without an archive, schema,
  or protocol rollback.

## Manual rigid-headwear authoring

The first practical authoring slice bypasses the unfinished browser editor and
does not use the hair/facial-hair semantic masks. `appearance headwear-init`
creates six transparent full logical-canvas masters under `tmp/`, seeded from a
verified authentic `partyhat`, `wizardshat`, or `mediumhelm` block. Matching
guide PNGs render the locked real player at the same coordinates. The pixels'
position on that canvas is the attachment contract; no reference crop is
resized and no sidecar offset is copied onto new geometry.

`appearance headwear-build` applies only the template's integer crown deltas,
normalizes hard alpha and safe black, derives all 18 crops/sidecars, and renders
the canonical 24 walk plus 6 combat states through the existing integer draw
rules. Every preview is 88x112 and the scale-1 contact sheet is the human art
gate. Workspace loading verifies the template and both Authentic archive
digests, while all output is confined to `tmp/`. This slice does not allocate an
appearance or item, pack either archive, update MD5, change the live renderer,
or alter a packet/save contract. Publishing remains a later explicit gate.
