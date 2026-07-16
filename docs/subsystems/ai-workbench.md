# AI Workbench

The AI workbench is a dev-only PC client helper for visual iteration and local automation. It is disabled by default and only starts when the client is launched with `-Dvoidscape.workbench=true`, normally through `scripts/run-workbench-client.sh`.

## Entry Points

- `scripts/run-workbench-client.sh` mirrors `scripts/run-client.sh`, writes `Client_Base/Cache/ip.txt` and `port.txt`, then runs the PC client with workbench JVM properties.
- `PC_Client/src/orsc/OpenRSC.java` starts the workbench after the desktop client window and applet are initialized.
- `PC_Client/src/orsc/WorkbenchServer.java` owns the loopback HTTP server, JSON state snapshots, and screenshot file writes.
- `PC_Client/src/orsc/ORSCApplet.java` exposes a synchronized copy of the current rendered game frame and routes the backtick screenshot hotkey through the same capture path.

## Local HTTP Endpoints

Default base URL: `http://127.0.0.1:18787`.

- `GET /health` returns a minimal readiness payload with `ok`, `clientVersion`, `port`, and `generatedAt`.
- `GET /state` returns a JSON snapshot of client state: game dimensions/state, camera rotation/angle/zoom, the active `game.overheadPlayerLabelMode` (`0..2`), mouse state, local player basics when logged in, and selected interface state for the Auction House, world map, Stats, native shop window, bank, bestiary, duel journal, and Paperdoll V2 evaluation. The top-level `hud` object reports whether the location plaque setting is enabled, whether the plaque or original Wilderness fallback is eligible to render in the current frame, and the current Wilderness flag/level. `interfaces.advancedSettings` reports visibility, the selected numeric category, and production-owned click centers for the Interface category and Location plaque toggle; consumers must open the panel and select Interface before using the toggle target. `interfaces.duelJournal` exposes visibility/loading, the selected receipt id, history/swing counts, proof state/id, window/history/swing rectangles, and the close point so click and scroll checks use production geometry. The journal fixture accepts `proof=verified|failed|unavailable` to exercise all three production proof-card render states; it does not bypass the production renderer/model, but it does not simulate the v2 network parser or cryptographic replay. V2 diagnostics include activation/fallback reason, pack and selector digests, compatibility-export status, local/remote path telemetry, viewport/projection lock, palette indexes, merged appearance layers, raw appearance-panel/referral state, and guarded designer eligibility/selection/preview status. When a Void Rift is visible, `game.voidRiftTarget` exposes its projected `screenX`/`screenY` center plus world tile so `/input/click` can exercise the real scene picker without guessed coordinates.

`interfaces.stats` is `null` before the client exists; otherwise it exposes `visible`, `hoveredSkill` (`-1` when none), `scrollRows`, `questPoints`, the production `footer` rectangle, five `equipment` name/value rows, and every skill's `index`, `name`, `current`, `base`, rendered `levelText`, `lockable`, and `locked` values. Each skill also supplies `rowVisible`, its production `row` rectangle, and a `lockTarget` rectangle for a visible lockable skill, all in unscaled game-frame coordinates so hover, scrolling, and lock-click checks can use renderer-owned geometry. `footer`, `row`, and `lockTarget` are `null` while Stats is hidden or that complete skill row is clipped; reading `/state` never changes the production scroll position.

`interfaces.riftTeleport` exposes the desktop Void Rift panel without changing it: `visible`, the production panel and close rectangles, an optional Stay Here option/rectangle, and every travel card's server option index, numeric shortcut, label, category, hover state, and production rectangle. Hidden panels return `visible:false`, `layout:null`, and an empty card list. These diagnostics let viewport and interaction checks use the same responsive geometry as rendering and hit-testing.

`interfaces.pkCatching` exposes the production chooser cards, active session/HUD metrics, authoritative Trainer hint and projected marker center, results controls, modal tabs, selected board, both committed leaderboard models, and their renderer-owned rectangles. The fixture feeds the same reserved-message parser used by the live server; tab changes go through production mouse hit-testing.
- `GET /screenshot` saves an exact PNG copy of the current game frame plus a JSON sidecar under `tmp/workbench/screenshots/`, then returns the saved paths and image dimensions.
- `GET /captures/latest` returns the most recent saved capture path and metadata.
- `POST /input/click` accepts `{"x":301,"y":147,"button":"left"}` in unscaled game-frame coordinates and sends a synthetic click through the applet mouse handler.
- `POST /input/drag` accepts `{"fromX":0,"fromY":0,"toX":10,"toY":10,"button":"left","steps":8,"durationMs":450}` and runs a synthetic drag through the applet mouse handler.
- `POST /input/scroll` accepts `{"x":779,"y":193,"amount":4}` in unscaled game-frame coordinates and runs the shared client scroll path at that mouse position.
- `POST /input/key` accepts `{"key":"ENTER"}` or `{"char":"a"}` and sends a synthetic key press through the applet key handler.
- `POST /input/type` accepts `{"text":"hello"}` and types each character through the key handler.
- `POST /input/command` accepts `{"command":"quickauction"}` or `{"command":"::quickauction"}` and sends the existing client command packet.
- `POST /input/npc-action` accepts `{"action":"talk"}` (or `command1`/`command2`, aliases `op1`/`op2`) plus an NPC selector field and sends the matching NPC talk/command packet for the nearest matching visible NPC.
- `POST /dev/ready` uses the saved Existing User credentials to reach an in-game state, waits for the local player to appear, then clears blocking welcome/server-message dialogs and hides dev-iteration panels such as the Auction House and world map.
- `POST /dev/ui-panel` accepts `{"panel":"inventory"}` and opens a named Voidscape HUD/menu state, optionally capturing it. Supported visible panel keys include `hud`, `options-profile`, `options-settings`, `friends`, `ignore`, `magic`, `prayers`, `skills`, `quests`, `loot`, `bestiary`, `minimap`, `inventory`, and `account`. `options-settings` mirrors the production transition for each skin: the Voidscape modal replaces its parent Settings panel, while authentic captures retain their established parent composition.
- `POST /dev/viewport` accepts `{"index":0}` and applies a numbered desktop viewport scale preset via `ScaledWindow`.
- `POST /dev/world-reskin` accepts `{"mode":"auto"}`, `{"mode":"authentic"}`, or `{"mode":"void"}`. It updates the client-only world reskin flag, reloads the current region, and reports whether the void profile is active. Add `{"capture":"true"}` to save a screenshot in the same response.
- `POST /dev/reload-entity-sprites` reloads entity sprites through the same client path used at startup, useful after repacking candidate frames while the workbench client is still open.
- `POST /dev/paperdoll-v2-stack` accepts `{"stackId":"male_mohawk"}` for the older non-selector proof mode. It is unavailable to normal clients and does not change a save or server state.
- `POST /fixture/auction-house` requires the logged-in player to be an admin, dispatches `::workbenchauctionfixture`, and reseeds deterministic Auction House listings plus recent sales through the server DB layer.
- `POST /fixture/christmas-cracker` opens the production Christmas-cracker reel initializer with a validated deterministic `outcome`, `itemId`, `seed`, and named `phase` (`opening`, `fast`, `near-stop`, or `reveal`); optional `capture:true` saves the exact frame.
- `POST /fixture/duel-journal` requires a logged-in client and opens the production journal interface with a deterministic ten-duel history, both six-item stake offers, and 28 requester-only swings covering every combat mode, misses, and a landed zero-damage hit. Optional `{"capture":true}` saves the exact frame; the fixture is client-local and does not create a duel or database receipt.
- `POST /fixture/pk-catching` requires a logged-in client with Void Glass available and accepts `view` as `chooser`, `trainer`, `trainer-attack`, `trainer-hidden`, `results`, `leaderboard-medium`, or `leaderboard-hard`. The three Trainer fixtures exercise the stable destination, marker-free `ATTACK NOW`, and suppressed guidance states through the production metadata parser; optional `{"capture":true}` saves the exact frame.
- `POST /scenario/auction-house-open` opens the Auction House, captures Browse, first-listing selection, My Auctions, Intel, and Food category states, then returns a report with all saved image paths.
- `POST /scenario/christmas-cracker-roll` captures all four named reel phases and fails unless the near-stop still shows a near miss and the final center card exactly matches the authoritative result.
- `POST /scenario/pk-catching-ui` captures the difficulty chooser, Trainer destination, `ATTACK NOW`, hidden guide, results, Medium board, and Hard board. It fails unless the destination projects, attack/hidden states suppress the floor marker, and the Hard tab activates through production hit-testing.
- `POST /scenario/ui-panels` runs `/dev/ready`, opens the visible core Voidscape menu panels one by one, and saves screenshots for the UI concept-art pipeline.
- `POST /scenario/subscription-vendor-claim` requires a logged-in account whose save has `web_account_id` plus a matching `starter_card:<webAccountId> = 1` global marker, teleports to the Lumbridge Void Subscription Vendor, sends the real client NPC command packet for `Subscribe`, verifies no vendor shop opens, and captures before/after screenshots.
- `POST /scenario/subscription-card-redeem` requires a logged-in account with item `1602` in inventory, sends the real inventory item command packet for `Redeem`, waits for the card to be consumed, and captures before/after screenshots.
- `POST /scenario/appearance-frames` accepts a compiler-managed `appearanceId`, requires a logged-in stationary player using authentic sprites, and captures the complete 30-state matrix: eight walking directions times three frames plus Combat A/B times three frames. In addition to supplemental world screenshots, every state gets an authoritative opaque RGB 88x112 isolated player raster. The diagnostic raster invokes the same `drawPlayerCompositeLayers` body on a separate cached `MudClientGraphics` target with the fixed `(12,2)` / 64x102 draw contract; it does not swap or paint the live surface. Reports bind the sprite archive, all twelve appearance IDs and resolved definitions, palette indexes and resolved RGB, layer order, direction/mirror/frame inputs, crop, PNG and raw-RGB hashes. The scenario temporarily fixes modern hair/invisibility/invulnerability to their supported neutral values and restores them along with all layers, colours, direction/frame, camera, render-array entry, and combat timeout. `/scenario/cowboy-hat-frames` remains a compatibility alias for appearance `245`.
- `POST /scenario/paperdoll-v2-frames` accepts a tmp-only Paperdoll V2 workspace/pack and renders the complete 30-state `control` and `rare_hair` matrices into 176×224 panels. It validates the whole closed pack before rendering, uses isolated V2 classes for the exact pack-only oracle, and separately composes the V2 head/hair with live legacy body, legs, sword, and shield for human layer-order review. It clones the local player and never branches the normal renderer or mutates live appearance state. The generated report is an oracle input for `scripts/appearance-v2.sh compare-oracle`; production caches and selectors are not consulted or changed.
- `POST /scenario/paperdoll-v2-live-scene` selects a stack or catalog selector/profile on a temporary local-player fixture, requires the locked 1024x668 game area and projection shift 10, proves the normal scene compositor entered V2 through actor-specific telemetry, captures the live scene, and restores every changed field.
- `POST /scenario/paperdoll-v2-benchmark` measures exactly 600 local-player renders after warm-up on either the active V2 path or a separately launched forced-legacy path. It excludes remote actors, warm-up, HTTP/JSON, and screenshot I/O and fails on any unexpected render-path sample.
- `POST /scenario/paperdoll-v2-selector-resolution` exercises every selector/profile route plus named fallback cases, validates neutral skin sources, renders a two-player male/female control, and probes the preflight and complete three-slot loop for allocation. It is synthetic and does not send a packet or mutate live/server/save state.
- `POST /scenario/paperdoll-v2-runtime-matrix` renders selector `0..6`, both base profiles, and all 30 canonical states for one palette/equipment case. Its 420 captures identify V2 versus whole-player legacy, visible versus suppressed style, and native versus 1x compatibility-overlay hair. Use separate launches for active, forced-legacy, missing-pack compatibility-only, and rejected-pack cases.

The HTTP server binds to loopback only. Render scenarios do not touch server
packets, opcodes, player saves, DB schema, cache versioning, or gameplay
behavior. The separate Paperdoll V2 designer evaluation can send the existing
appearance packet and persist the existing `hairstyle` field, but only behind
the disposable-QA server/client gates described below. Fixture seeding uses an
admin-only server command and marks rows as `wb-fixture`/`wb-buyer` so they can
be cleared and reseeded safely.

Run `scripts/run-appearance-qa.sh 245` after `/dev/ready` to validate all 30
states, extract the self-contained offline fixture, compare its 24-bit raster
pixel-for-pixel with the isolated Java oracle, and write authoritative and
supplemental sheets under `tmp/workbench/appearance-qa/`. The wrapper also
hashes every Cowboy frame/sidecar and both Authentic archives before and after
the run. Exactness is scoped to the isolated fixture inputs; the full world
screenshot is not a pixel-parity oracle.

## Configuration

The wrapper script accepts optional environment overrides:

```bash
VOIDSCAPE_WORKBENCH_PORT=18788 scripts/run-workbench-client.sh
VOIDSCAPE_WORKBENCH_DIR=/tmp/voidscape-workbench scripts/run-workbench-client.sh
```

The R9 selector runtime is launched from an explicit tmp workspace:

```bash
scripts/run-workbench-client.sh \
  --paperdoll-v2-workspace tmp/appearance-studio/v2-evaluation-final-r9-a \
  --paperdoll-v2-designer-evaluation
```

`--paperdoll-v2-workspace` discovers the pack, sibling selector registry, and
legacy compatibility properties. `--paperdoll-v2-force-legacy` keeps the locked
viewport while selecting the complete legacy baseline.
`--paperdoll-v2-compatibility-only` launches the explicit missing-pack case from
only `legacy-compatibility/runtime.properties`. Designer evaluation cannot be
combined with forced legacy or compatibility-only mode and refuses a non-loopback
game server.

The matching QA server must separately pass
`PaperdollV2EvaluationPolicy`: exact maximum `6`, JVM evaluation-server flag,
disposable SQLite `*_qa`, production command lockdown off,
`avatar_generator:false`, and `character_creation_mode:0`. Production leaves
the maximum at zero. These are disposable-QA gates, not release configuration.

When the workbench is disabled, pressing backtick still saves an exact client-frame screenshot to the default workbench directory. This replaces the earlier macOS full-screen `screencapture` hook and makes screenshots independent of OS window placement.

## Current Boundaries

The workbench currently supports capture, state, synthetic local input, command
dispatch, dev-ready session setup, current-region world-reskin toggles, entity
sprite reloads, Auction House, Christmas-cracker, duel-journal, and PK-catching fixtures, Auction
House, Christmas Cracker, PK Catching, legacy appearance, isolated/live/selector/matrix/
benchmark Paperdoll V2 scenarios, Lumbridge Subscription Vendor claim/redeem
scenarios, and screenshots around those flows. It deliberately has no reusable
scenario DSL or new server packet. Paperdoll V2 is PC-only and default-off;
Android, TeaVM, avatars, and production caches remain outside this evidence.
The Paperdoll V2 browser editor is a separate loopback authoring server; it is
not served by the game Workbench.
