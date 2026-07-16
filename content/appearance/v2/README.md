# Paperdoll V2 catalog

`catalog.yaml` is the content-owned, non-shipping source of truth for the
Paperdoll V2 collection. It owns stable `hairStyle` selector numbers, male and
female base identities, locked art sources, generated stack names, eligibility,
QA cases, and the current hat-occlusion fallback. Selector `0` is permanently
Classic and routes directly to the legacy renderer; its pack-only QA control
mapping is stored separately and cannot activate V2. Positive selectors are
never renumbered or reused.

The catalog does not enable V2. `shipping` and `defaultEnabled` are both false.
Both bases and all six hairstyles are PC-only `qa-ready` after deterministic
validation and independent visual QA; production activation remains a separate
final-owner gate. Android and TeaVM V2 remain disabled or compile-only. Until
per-hat masks are approved, only hat appearance `0` may use V2; every nonzero
hat requires a whole-player legacy fallback.

## Materialize and validate

```bash
scripts/appearance-v2.sh validate-catalog
scripts/appearance-v2.sh collection tmp/appearance-studio/v2-collection --force
scripts/appearance-v2.sh validate tmp/appearance-studio/v2-collection
scripts/appearance-v2.sh build tmp/appearance-studio/v2-collection
```

The collection command is deterministic and writes only below `tmp/`. It
creates separate male/female legacy controls and full native 2x head, body, and
legs assets. Native bases are faithful nearest-neighbour semantic-channel
derivations of the locked RSC sources, not redrawn bodies. The female body is
read from the existing `fbody1` block in both Authentic archives and is accepted
only when all 18 per-entry digests and client/server bytes agree; neither archive
is modified.

Each base gets a pack-only control stack, a full native-base stack, and one
native stack per eligible hairstyle. The build writes all assets to the strict
`Paperdoll_V2.orsc` proof pack and emits both `build/selector-registry.json` and
strict sorted-ASCII `build/selector-registry.properties` sidecars. The JSON is
validated against `paperdoll-v2-selector-registry.schema.json`; the properties
projection is independently parsed and checked for exact equivalence. It binds
the catalog, template/source/mask digests, and final pack digest, declares
unknown selectors and unsupported hats as legacy fallbacks, and contains no
labels or human prose. The default-off PC selector runtime consumes the strict
properties projection; the JSON remains the readable provenance and QA
contract.

QA is catalog-declared: every control, native base, and base/style combination
renders all 30 canonical states. Every live preview must contain non-background
pixels in the locked head, body, and legs bands. Native cases cover slots 0/1/2;
current runtime activation and cross-platform rollout remain separate approval
gates.

The build also emits a non-shipping compatibility bundle under
`build/legacy-compatibility/`. Selectors `1..6` receive all 18 transparent
`VoidscapeHairOverlay`-format PNGs and strict sidecars at 1×; selector `0`
emits no overlay because it routes directly to Classic. The downscale is an
explicit nearest 2:1 decimation with the globally locked, topology-preserving
sample phase `(0,0)`, so it cannot vary with Pillow resampling behavior or by
style/frame. Every 1× overlay must remain one 4-connected component. If
decimation creates an orphan, the exporter may discard only one detached
one-pixel component per frame while retaining the largest component; any larger
or additional detachment fails closed, and the manifest records every removed
pixel. Fourteen transparent 64×102 male/female PNGs
are representative QA thumbnails only, not server-generated player avatars.
The bundle manifest records that activation, client-cache writes, server-avatar
writes, and selector-limit changes are all false.

The hairstyle directories contain deterministic review material. Mechanical
validation and a valid pack alone do not constitute human visual approval. The
catalog's `qa-ready` state records the separately completed review; it still
does not make an asset selectable until the production activation gate opens.
