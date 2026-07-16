# Custom wearables

- Paperdoll V2 reached the 2026-07-11 R9 evaluation checkpoint. Adoption is GO
  only for staff PC testing on disposable local QA data; it is NO-GO for
  production default, Android, TeaVM, and avatars. The catalog remains
  `shipping:false` / `defaultEnabled:false`. R9 did not write/copy its outputs
  into production archives or MD5 and did not raise selector/version maxima or
  add an opcode/payload field or save schema. The existing selectable-head byte
  is decoded unsigned without changing its width; this does not mean dirty
  production paths match Git HEAD.
- R9 stable hairstyle selectors are: `0` Classic, `1` `rare_spikes`, `2`
  `faded_buzzcut`, `3` `mohawk`, `4` `textured_crop`, `5`
  `slick_back_undercut`, and `6` `high_topknot`. IDs are content-owned and never
  reused; they do not depend on `AnimationDef` order.
- R9 deterministic checkpoint hashes: catalog
  `b9080221cfd5843b25e84faa814ce3860cc599d550fccd796b2ed00be71050b9`,
  pack `46f3fe89c01f55563b5692711ee054082ebd610d123cba506bae64cb334adbb7`,
  selector properties
  `53ae87582971f73b90a30754fcc7a0c11cd0e2dac2f0daebfbc8c45e508ed804`,
  compatibility properties
  `302e561a5a3d999726b80fc88f7fc2976241cf4d55a06132494367b4b216fbc5`,
  compatibility manifest
  `2e2efc7378bd2467a33aff6f4a66e6feaa89355975a3bf776bf03ef510636250`.
- V2 preflight is atomic for the entire player. It requires selector `1..6`,
  head `8`, male/female body `2`/`5`, legs `3`, an empty hat slot, valid
  geometry, and a valid explicit tmp pack/selector registry. Any failure selects
  the untouched legacy player before drawing; never mix a partial V2 player or
  substitute frame zero.
- The generated 1x compatibility overlays preserve styles `1..6` during forced,
  missing-pack, or rejected-pack fallback, but only on compatible head `8` with
  no hat. Any ordinary/Cowboy hat suppresses both V2 and the overlay. Weapons,
  shields, and compatible non-substituted equipment remain legacy layers around
  the V2 base.
- The disposable evaluation server requires exact maximum `6` plus
  `-Dvoidscape.paperdollV2.evaluationServer=true`, SQLite database name ending
  `_qa`, production command lockdown off, `avatar_generator:false`, and
  `character_creation_mode:0`. The designer additionally requires Workbench,
  loopback, an active selector runtime, its designer flag, and a server-developer
  account. First-login creation never gets the Style row. Do not use reusable or
  production saves: production maximum `0` clamps unsupported styles to Classic.
- Avatar output remains blocked. The 14 generated thumbnails prove deterministic
  compatibility decimation, not live `AvatarGenerator` parity; evaluation
  deliberately requires the avatar generator off. Android debug and TeaVM
  compile/package gates pass (TeaVM: 939 classes / 10,825 methods), but
  Android/web builds hard-disable V2 runtime initialization and have no visual,
  memory, or performance evidence.
- R9 evidence and rollback live in
  `docs/reports/paperdoll-v2-evaluation-2026-07-11.md`. Rollback is removal of
  client/server evaluation flags plus disposal of the QA database; no archive,
  MD5, schema, or protocol downgrade is needed.
- The selected custom-client direction is true 2x Paperdoll V2.
  `rsc-player-2x-v1` uses 128x204 walk and 168x204 combat canvases, six masters,
  semantic ARGB channels, deterministic propagation, and a strict tmp-only
  `Paperdoll_V2.orsc` pack. R9 includes the dev-only 1024x680 scene and guarded
  PC selector/designer proof; do not treat its pack as a production cache.
- Broader Paperdoll V2 activation stays gated. Native hat/equipment occlusion,
  Android/TeaVM, avatars, production packaging/versioning, and saved-style
  downgrade each need separate approval and evidence.
- Hair proofs must preserve the locked base head and be inspected as individual
  actual-size panels. The rare Coal high-spike fixture includes forehead locks,
  sideburns, and a deliberately slim rear/nape; each of its six masters is one
  4-connected scalp-attached component.
- Paperdoll V2 native tint channels are neutral grayscale. Normalize imported
  legacy `R=255,G=B` skin pixels to the `0x76/0xB0/0xFF` semantic ramp before
  tinting; copying the red-mask RGB directly makes trial-skin faces salmon while
  the live legacy body uses the pale three-shade skin path.

- Voidscape's active `custom_sprites: false` path reads numeric worn frames from
  `Authentic_Sprites.orsc`.
- A server item `appearanceID` is one-based: `appearanceID = zero-based active
  AnimationDef runtime index + 1`.
- Pack worn frames into both the client and server Authentic sprite archives;
  update the client's `Cache/MD5.SUM` row afterward.
- Validate the mapping and decoded frames with `python3 -m voidscim
  validate-wielded`; do not trust source comments as numbering evidence.
- `custom_sprites: true` has a different AnimationDef layout and archive. A
  wearable added to the authentic path is not automatically compatible with
  OpenPK/Cabbage-style presets.
- OpenRSC derives noteability as `!stackable && (!untradeable || noteable)`, so
  every tradable non-stackable item is noteable even when the raw flag is false.
- Appearance Studio's R3 semantic reports are mechanical contract evidence only.
  A valid report must bind template/reference/mask/manifest digests, contain 18
  stored and 30 canonical runtime metrics, and remain explicitly non-shipping
  with no human visual approval.
- Long hair needs a separate `neck_clearance` forbidden mask. Do not fold that
  role into generic protected anatomy: facial-hair families may legitimately
  occupy different neck/face regions. Nape counts and depth must come from the
  scalp-attached primary component after subtracting neck clearance.
- The adopted Cowboy is a compatibility-only known-bad fixture. Mechanical
  byte/render parity must not be interpreted as acceptable hat geometry and its
  pixels remain read-only. New rigid hats use the tmp-only `headwear-init` /
  `headwear-build` loop: edit six full logical canvases against same-size real
  player guides, then judge all 30 actual-size previews. Pixel placement is the
  anchor; crops and sidecars are always derived afterward.
