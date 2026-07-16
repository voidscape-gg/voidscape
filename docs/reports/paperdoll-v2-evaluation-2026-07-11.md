# Paperdoll V2 evaluation checkpoint

Date: 2026-07-11

Decision: **GO for a staff-only PC evaluation on disposable local QA data.**
**NO-GO for production-default activation, Android, TeaVM, or generated
avatars.** The legacy renderer remains the shipping path and the rollback
contract.

This report records the first complete evaluation checkpoint for the true 2x
Paperdoll V2 path. It does not approve a public release, production cache
publication, a server preset change, or replacement of the legacy paperdoll.
The generated catalog itself remains `shipping: false` and
`defaultEnabled: false`.

## Evaluated content and deterministic identity

The R9 catalog contains two base profiles (`male`, `female`) and six stable
hairstyle selectors in addition to selector `0` (`Classic`):

| Selector | Stable style ID | Label |
|---:|---|---|
| 1 | `rare_spikes` | Rare high spikes |
| 2 | `faded_buzzcut` | Faded buzzcut |
| 3 | `mohawk` | Mohawk |
| 4 | `textured_crop` | Textured crop |
| 5 | `slick_back_undercut` | Slick-back undercut |
| 6 | `high_topknot` | High topknot |

The source of truth is `content/appearance/v2/catalog.yaml`, whose checkpoint
SHA-256 is
`b9080221cfd5843b25e84faa814ce3860cc599d550fccd796b2ed00be71050b9`.
Selectors are permanent catalog identities; they do not depend on
`AnimationDef` order.

Two clean builds at
`tmp/appearance-studio/v2-evaluation-final-r9-a` and
`tmp/appearance-studio/v2-evaluation-final-r9-b` produced byte-identical
artifacts:

| Artifact | SHA-256 |
|---|---|
| `build/Paperdoll_V2.orsc` | `46f3fe89c01f55563b5692711ee054082ebd610d123cba506bae64cb334adbb7` |
| `build/selector-registry.json` | `41d13ebbbfd84328f7372e207f17eaff1884adc2b2c62633bf0e3162f6b057d0` |
| `build/selector-registry.properties` | `53ae87582971f73b90a30754fcc7a0c11cd0e2dac2f0daebfbc8c45e508ed804` |
| `build/legacy-compatibility/runtime.properties` | `302e561a5a3d999726b80fc88f7fc2976241cf4d55a06132494367b4b216fbc5` |
| `build/legacy-compatibility/manifest.json` | `2e2efc7378bd2467a33aff6f4a66e6feaa89355975a3bf776bf03ef510636250` |
| `build/report.json` | `6375e9ec221673b38a7e4ed84326bf381afc7dabc9c71d4bf23257c1f69118d2` |

The closed pack contains 18 assets, 16 stacks, 720 sprite records, and 18
stored frames per asset. The compatibility export contains 108 legacy overlay
frames and 14 QA thumbnails. Those thumbnails are evidence only; they are not
live `AvatarGenerator` integration.

## Runtime contract

The PC runtime is default-off and accepts only an explicit, non-symlinked
`Paperdoll_V2.orsc` below the repository `tmp/` tree. It also requires the
digest-pinned template/source contract and, in selector mode, a sibling
`selector-registry.properties`. Android and TeaVM never initialize this
runtime.

Selection is atomic for the whole player. V2 is used only when all of these
conditions hold:

- the pack and selector registry validate;
- selector `1..6` resolves for the player's male/female base profile;
- slots 0, 1, and 2 match head `8`, male body `2` or female body `5`, and legs
  `3`;
- hat slot 5 is zero; and
- every required stack, channel, frame, and runtime geometry input is present.

Any failed condition selects the untouched legacy whole-player path before a
V2 pixel is drawn. There is no frame-zero substitution and no mixed partial
player. Weapons, shields, and other non-substituted layers continue through
the legacy compositor around an eligible V2 base.

The digest-bound 1x compatibility overlays are a separate fallback for styles
`1..6` when V2 is intentionally forced off, its pack is missing, or its pack is
rejected. They require compatible head `8` and no hat. A nonzero hat suppresses
both V2 and the compatibility hair overlay, preserving the complete legacy hat
composition.

## Evidence summary

### Compiler and renderer

- The full Python Paperdoll V2 suite passed 33 tests.
- The correctly configured wider Appearance Studio suite passed 154 tests in
  437.201 seconds. A subsequently added archived-external-reservation regression
  test passed with the focused registry/repository-audit gate; current discovery
  contains 155 cases and every case has been exercised.
- The Java adversarial/compatibility/server policy suite passed, including
  strict pack, provenance, selector, allocation, geometry, fallback, and
  server-policy cases.
- The final repository `scripts/build.sh` passed after the runtime, Workbench,
  diagnostics, and combat-framing changes.
- The wider legacy Appearance Studio registry still passes
  `scripts/content.sh appearance validate --strict` with zero errors and zero
  warnings; archived external reservations remain occupied without pretending
  their removed `AnimationDef` is live.
- The final Python/Java oracle comparison at
  `tmp/appearance-studio/v2-evaluation-final-r9-a/evidence/java-oracle-final-r9`
  reports zero mismatched frames and zero mismatched pixels.
- `scripts/build-android.sh --debug` completed successfully. The TeaVM spike
  compiled 939 classes / 10,825 methods, and
  `scripts/package-web-teavm.sh --skip-build` produced
  `tmp/paperdoll-v2-cross-client-20260711/web-teavm`. This is compile/package
  compatibility only; both targets remain platform-gated to legacy at runtime.
- The Android output was a 17,437,404-byte debug APK with SHA-256
  `f2c95d2a47859e959b1f5d643815e090dc61ec7ef87872dd422928a6fc6b3b70`.
  TeaVM's 3,376,621-byte JavaScript had SHA-256
  `1954a7f7242fe3431058158722d089151229a41562fddc0a1f83ea4fc164cb38`.
- Selector resolution exercised all 12 positive hairstyle/profile routes. Its
  100,000-iteration preflight probe and 5,000 full three-slot render probe each
  allocated zero bytes.
- A 600-sample active mohawk benchmark recorded mean 29,676 ns, p50 29,250 ns,
  p95 32,375 ns, 600 V2 renders, zero legacy renders, and zero unexpected
  paths. The corresponding forced-legacy run recorded mean 40,695 ns, p50
  36,208 ns, and p95 40,584 ns. These are Workbench microbenchmarks, not a
  whole-client capacity promise.
- The saved active-runtime state measured a 28,168,848-byte (26.86 MiB) heap
  increase while loading the 4,923,218-byte pack and its 4,607,164 decoded ARGB
  bytes. Cached render surfaces account for 2,785,280 bytes (2.66 MiB). This is
  one desktop JVM load measurement, not a mobile budget or a steady-state heap
  guarantee.
- The loopback browser editor was exercised through its real DOM: paint, erase,
  pick, soft alpha, brush size, undo/redo, nudge, mirror, overlays, pose/frame
  switching, 1x/2x previews, and deterministic save all passed. A real
  game-derived PNG was pasted, nearest-neighbor scaled from 128x204 to 64x102,
  mirrored, applied, and saved; the saved layer contained 86 visible pixels.
  Undo plus save restored the exact one-pixel preimage SHA-256
  `02c3e22853634eaa1492f61de0e00457c000c29131f5bcef8b29189938fad460`.

### Complete visual matrices

The corrected Workbench matrix uses 176x224 actual-size panels and contains 420
captures per case: selector `0..6`, both base profiles, and all 30 canonical
walk/combat states.

| Case | Result |
|---|---|
| Active/default palette | 360 V2 hairstyle panels; 60 Classic legacy panels |
| Active/warm palette plus equipment | 360 V2 hairstyle panels with legacy weapon, shield, armour/accessory layers; 60 Classic legacy panels |
| Active/dark palette | 360 V2 hairstyle panels; 60 Classic legacy panels |
| Ordinary hat `151` | 420 whole-player legacy panels; all 360 nonzero styles suppressed |
| Cowboy hat `245` | 420 whole-player legacy panels; all 360 nonzero styles suppressed |
| Forced legacy | 420 legacy panels; 360 digest-bound compatibility overlays visible |
| Missing V2 pack | 420 legacy panels; 360 compatibility overlays visible |
| Rejected/corrupt V2 pack | 420 legacy panels; 360 compatibility overlays visible |

An independent reviewer opened all 360 positive hairstyle panels individually.
That review found a combat-capture framing defect rather than an art defect.
After correction, the reviewer opened all 84 changed combat panels
individually and passed them. A boundary scan of the corrected default matrix
found zero edge pixels in all 420 panels, with minimum margins of 4 pixels on
the left/right/top and 18 pixels on the bottom. All 336 walk panels remained
byte-identical; only the intended 84 combat captures changed. The four other
corrected matrices also contained no non-background boundary pixels.

Rare Spikes intentionally retains two digest-frozen sideburn-edge pixels inside
the conservative protected mask in `west` and `combat-west`. Review approved
those pixels as hair at the face edge; the locked base anatomy remains a
separate, unmodified layer. This exception is explicit in the style's
`art.json`, not silently ignored by QA.

The framing review is
`tmp/workbench-v2-framing-fixed/INDEPENDENT_COMBAT_FRAMING_REVIEW.md`.

### Live designer, persistence, equipment, and multiplayer

- The appearance panel previewed Classic plus all six styles through the actual
  UI for both base profiles.
- Accepted style `6` persisted on disconnect, hydrated on a fresh login, and
  rendered through V2 for male and female saves.
- A second Workbench client observed the remote player with style `6`, head
  `8`, female body `5`, and legs `3`; remote telemetry recorded V2 rather than
  legacy rendering.
- That final R9 remote observation ran in the locked 1024x668 game area plus
  12-pixel footer, projection shift `10`, with `exactTargetViewport:true`.
  Remote telemetry recorded 93 V2 renders and zero legacy renders, proving the
  normal 1024x680 scene path rather than only an isolated sprite fixture.
- Ordinary and Cowboy hats forced whole-player legacy without invisible or
  partially composed players.
- Chain/robe equipment that preserved base slots remained eligible. Plate body
  or plate legs that replaced the required base identity blocked the designer
  with an unequip message. The one-shot appearance authorization was cleared
  after a blocked submission, so a stale follow-up packet was rejected as
  suspicious.
- A disposable first-login account received the normal referral-bearing
  character-creation panel without a V2 Style row. This prevents the guarded
  evaluation controls from changing new-account referral semantics.
- The test player and temporary equipment were restored after the run.

### Evidence index

| Evidence | Location |
|---|---|
| Deterministic builds | `tmp/appearance-studio/v2-evaluation-final-r9-a`, `tmp/appearance-studio/v2-evaluation-final-r9-b` |
| Final offline/Java oracle comparison | `tmp/appearance-studio/v2-evaluation-final-r9-a/evidence/java-oracle-final-r9/report.json` |
| Corrected active/palette/equipment/hat matrices | `tmp/workbench-v2-framing-fixed/*.response.json` |
| Independent corrected framing review | `tmp/workbench-v2-framing-fixed/INDEPENDENT_COMBAT_FRAMING_REVIEW.md` |
| Selector/allocation/skin/two-player fixture | `tmp/workbench-v2-final-designer/selector-resolution.response.json` |
| Active performance | `tmp/workbench-v2-final-designer/benchmark-active-v2.response.json` |
| Pack/load memory measurement | `tmp/workbench-v2-final-designer/state-initial.json` |
| Browser editor/import smoke | `tmp/appearance-studio/editor-smoke-20260711/` |
| Machine-readable native import oracle | `tmp/appearance-studio/v2-editor-import-smoke-20260711/native-import-smoke-report.json` |
| Labeled Classic/V2 side-by-side frames | `tmp/appearance-studio/v2-evaluation-final-r9-a/build/comparisons/` |
| Per-stack contact sheets and individual previews | `tmp/appearance-studio/v2-evaluation-final-r9-a/build/previews/` |
| Forced-legacy matrix/performance | `tmp/workbench-v2-forced-legacy/` |
| Missing-pack compatibility matrix | `tmp/workbench-v2-missing-pack/` |
| Rejected-pack compatibility matrix | `tmp/workbench-v2-corrupt-pack/` |
| Designer, persistence, and equipment flow | `tmp/workbench-v2-final-designer/` |
| Remote observer | `tmp/workbench-v2-remote-observer/state-observing-wbtest.json` |
| Final R9 1024x680 remote scene | `tmp/workbench-v2-remote-observer/screenshots/20260711-052902-801-http.png` |
| First-login/referral panel | `tmp/workbench-v2-first-login/` |

## Server and designer safety policy

Production stays at `paperdoll_v2_evaluation_hair_style_max: 0` (or no setting).
The evaluation server refuses to start unless all of these are true:

- the config maximum is exactly `6` **and** the JVM has
  `-Dvoidscape.paperdollV2.evaluationServer=true`;
- the database is disposable SQLite and its name ends in `_qa`;
- production command lockdown is off;
- `avatar_generator` is false; and
- `character_creation_mode` is `0`.

The client designer additionally requires the Workbench flag, an active
selector runtime with maximum `6`, the explicit designer-evaluation flag, a
loopback server, an in-game server-developer account, and an eligible
appearance-change session. It never exposes the Style row during first-login
creation. The server accepts positive `hairStyle` only for that dev/evaluation
cohort, modern client version, valid gender restriction, selector `1..6`, and
the exact base identity. Other positive-style packets are rejected.

The existing appearance opcode and payload width are unchanged. The server now
decodes the existing head selection byte as an unsigned appearance ID, which
expands the valid interpretation of that same wire byte; this is a
semantic decoder correction, not a new field or packet-shape expansion.

This gate is intentionally destructive to unsupported saved styles outside the
evaluation cohort: the normal production appearance model has maximum `0` and
clamps unsupported modern styles to Classic. Never point the evaluation at a
production or reusable player database.

## Legacy versus R9 V2

| Dimension | Legacy paperdoll | R9 V2 | Evaluation conclusion |
|---|---|---|---|
| Visual quality | Authentic and universally compatible, but a 16x18-scale head cannot carry the desired hairline, fade, strand, and silhouette detail | True 2x semantic head/hair pixels produced readable fades, fringe, sideburns, napes, and six distinct silhouettes | V2 is materially better for custom PC hairstyles |
| Authoring effort | Fifteen to eighteen fragile final frames, sidecars, source-order identity, and duplicated archives | Six canonical masters, locked anchors/masks, deterministic phases/mirrors, stable selectors, strict pack, and previews | V2 front-loads tooling but makes each additional style safer and faster |
| Runtime cost | No extra V2 pack heap; mature path on every client | 26.86 MiB measured load delta and 2.66 MiB surfaces; the isolated 600-render benchmark was faster than the forced-legacy control | Acceptable for bounded desktop evaluation, not yet a fleet/mobile capacity result |
| Compatibility | PC, Android, TeaVM, hats, equipment, and live avatars already agree | PC-only; hats and invalid packs fall back safely, but avatars/mobile/web and native hats are not implemented | Legacy remains the release compatibility baseline |
| Maintenance | One old renderer, but manual numbering/art workflows are brittle | Strong manifests/oracles/fail-closed validation, at the cost of maintaining a second renderer and compatibility exporter | Worth continuing only while fallback and deterministic tests remain mandatory |

R9 is visually and technically worth continuing as a PC candidate. It is not
yet worth switching production players because its compatibility and release
operations lag its art/runtime quality.

## Remaining blockers

### Critical release blocker — production packaging and migration: NO-GO

The pack and catalog deliberately declare non-shipping/default-off state. No
R9 pack, selector, overlay, or thumbnail output was written/copied into a
production cache, either Authentic archive, or the client MD5. R9 also did not
raise the production selector/version maximum, add an opcode/payload field, or
change the database schema. These claims are scoped to this evaluation and do
not imply that those paths are clean relative to Git or free of pre-existing
worktree changes. A release still needs an owner decision, production
packaging/version cohort,
migration policy for saved selectors, soak/performance testing outside
Workbench, and a release rollback exercise.

### High compatibility blocker — Android and TeaVM: NO-GO

The shared client code compiles for both targets: the Android debug build passed,
and TeaVM compiled 939 classes / 10,825 methods before successful web packaging.
`mudclient` nevertheless hard-disables V2 initialization for Android and web
builds. No device/browser render matrix, memory budget, input/designer UX, or
frame-time acceptance evidence exists. They therefore remain legacy-only and
cannot join a V2 release cohort.

### Critical release blocker — avatar generator: NO-GO

The server evaluation policy requires `avatar_generator=false`. The generated
14 compatibility thumbnails prove deterministic decimation only; the live
`AvatarGenerator` has no V2 pack/selector compositor or approved legacy export
installation path. Website/account avatars would diverge from the selected
in-game style.

### High content blocker — native hats and broader equipment

Hats intentionally fall back to legacy, and plate replacements are not
designer-compatible. Native 2x hats, hair occlusion policies, deforming body
layers, and full equipment substitutions need separate template/art/runtime QA
gates. The known-bad Cowboy pixels remain a compatibility fixture and are not
approved art.

### Medium follow-up — independent facial hair and Looks

The manifest/compiler schemas support facial-hair layers and bundled Looks, but
R9 deliberately ships only hairstyles. A beard or mustache independent of the
existing hairstyle/Look identity would require real content pressure and a
separate versioned protocol decision; no such field is added here.

## Reversible activation and migration plan

This sequence is a plan for a later owner-approved PC cohort, not authorization
to execute it now:

1. Freeze the reviewed catalog/pack/selector/compatibility digests and publish
   them as a separate versioned V2 bundle; do not overwrite either Authentic
   archive.
2. Add and test a production-safe bundle lookup/signature policy to replace the
   current repository-`tmp` evaluation restriction, plus real avatar parity.
3. Define saved-selector downgrade behavior before raising any maximum. Back up
   the appearance rows and ensure disabling V2 does not silently destroy a
   player's selected style.
4. Exercise install, upgrade, rejection, missing-bundle, old-client, and
   rollback cases on a staging copy of production data. Keep selector `0` as
   the default and require the approved PC client/version cohort.
5. Enable only staff accounts first, monitor normal gameplay frame time, heap,
   disconnect persistence, remote rendering, and avatar parity, then stop for
   an owner review.
6. Expand beyond staff only after the production, avatar, compatibility, and
   rollback gates all pass. Android, TeaVM, and native hats/equipment remain
   separate opt-ins until they have their own visual/performance evidence.

## Rollback

Rollback is immediate because R9 outputs were not published into production
targets:

1. Stop using the Workbench Paperdoll V2 launch flags.
2. Leave/remove `paperdoll_v2_evaluation_hair_style_max`; the production value
   is `0`.
3. Start without `-Dvoidscape.paperdollV2.evaluationServer=true` and restore
   the normal `avatar_generator` setting.
4. Discard the disposable `*_qa` database if it contains positive selectors.
5. Do not copy anything from the R9 `tmp/` build into production caches.

Every client then uses the existing legacy renderer and Classic selector. R9
requires no archive restore, MD5 rollback, schema rollback, or protocol
downgrade; unrelated pre-existing worktree changes remain outside this rollback.

## Next approval gates

1. Run a bounded staff-only PC soak on disposable QA accounts and record normal
   gameplay frame-time/memory behavior outside capture scenarios.
2. Decide whether the R9 six-style collection is worth a production PC-only
   release cohort.
3. Before any release, define packaging/versioning, saved-selector downgrade,
   avatar parity, and an exercised release rollback.
4. Treat native hats/equipment, Android, TeaVM, and independent facial-hair
   fields as separate later features with their own visual and compatibility
   gates.
