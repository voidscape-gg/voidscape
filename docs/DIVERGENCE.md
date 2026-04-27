# Divergence from upstream OpenRSC

Voidscape is a hard fork of [Open-RSC/Core-Framework](https://github.com/Open-RSC/Core-Framework).

## Vendor reference

| Field | Value |
|---|---|
| Upstream repo | `Open-RSC/Core-Framework` |
| Vendored SHA | `fc74d38e2ead0a5864b48ae191b7184a391777cf` |
| Vendored date | 2026-04-24 |
| License | AGPLv3 (inherited — see `LICENSE`) |
| Branch vendored from | default branch at the time of clone |

The full upstream tree at this SHA is kept locally at `upstream/openrsc-snapshot/` (gitignored — recreate via `scripts/fetch-upstream-snapshot.sh`). Use it for diffing voidscape changes against the original; never edit it.

## How divergence is tracked

Voidscape's git history starts from a single squashed vendor commit. Every voidscape change after that point is documented in this file under **Changes** below. Each entry should record:

- A short title and date.
- What was changed and why.
- Files touched (high level — full diff is in git).
- Any reversibility / upstream-sync implications.

Keep entries terse. The git log has the details.

## Changes

### 2026-04-24 — bootstrap of `server/local.conf` (voidscape preset)

Created `server/local.conf` from `preservation.conf` as voidscape's working preset. **Not tracked in git** (gitignored per upstream + voidscape rules) — bootstrap on a fresh checkout via:

```bash
cp server/preservation.conf server/local.conf
# then re-apply the overrides below
```

Voidscape overrides applied:
- `database.db_name: voidscape` — distinct DB so vanilla preservation DBs aren't shadowed
- `world.server_name: Voidscape` (and `server_name_welcome`, `welcome_text`)
- `world.want_fatigue: false` — first deliberate QoL divergence from authentic. Reason: fatigue is the most universally disliked authentic mechanic and the user's stated goal is "mostly authentic with QoL". All other progression knobs (`game_tick: 640`, `combat_exp_rate: 1`, `skilling_exp_rate: 1`, etc.) remain authentic.
- `world.is_localhost_restricted: false` — dev-only override; the upstream default unconditionally rejects auth from 127.0.0.1 (`CharacterCreateRequest.java:182`). Localhost auth is required for our local testing loop. Not a gameplay divergence.

### 2026-04-24 — F2P world (`member_world: false`)

Set `world.member_world: false` in `local.conf`. Voidscape is **F2P** by default; members content is gated off. `can_feature_membs: true` is retained so the underlying capability is intact and the flag can be flipped back without rebuilding.

Why: explicit choice during foundation testing. Aligns with the early-RSC nostalgia angle (2001-era was F2P-only) without going as far as switching to the `2001scape.conf` preset, which strips much more content.

Reversibility: flip back to `member_world: true` and restart. No data migration.

Verified: server boots in ~1.9s, listens on TCP 43596 + WS 43496, loads 836 NPCs / 1593 items / 27782 scenery / 3609 NPC spawns / 1019 ground items / 50 quests / 9 minigames / 455 plugin handlers; identifies itself as "Voidscape" in logs.

### 2026-04-24 — roofs hidden by default + toggle exposed in Game Options

Flipped three flags so the client launches with roofs hidden, and the existing (but previously gated) "Hide Roofs" menu entry appears alongside the camera-mode toggle in the Game Options panel.

Files touched:
- `Client_Base/src/orsc/Config.java` — `C_HIDE_ROOFS = true` (default state on every client launch); `S_SHOW_ROOF_TOGGLE = true` (so `mudclient.java:276`'s `authenticSettings` calculation falls into custom UI mode at object init, before the server's settings packet arrives, so the tabbed options panel renders correctly from boot).
- `server/local.conf` — `show_roof_toggle: true` so the server-side value matches what the client expects on login.

Why: first QoL pass. Roofs occluding indoor scenes is the universally-disliked default; original RSC and OSRS communities both gravitate to roofs-off. The OpenRSC menu wiring already existed, just gated by an `S_*` flag — voidscape ungates it.

UI placement: same Game Options tab as camera mode (manual/auto). Same list-row pattern. No new UI authored; we just exposed what was already there.

Reversibility: flip the three flags back. No data migration. Players who explicitly enabled roofs in their session keep that toggle for the session, but the next client launch defaults back to hidden.

### 2026-04-24 — XP drops enabled by default (green)

Floating "+N Skill exp" text now appears on every XP gain (and "+1 Skill level" on level-up). Same RuneLite-style overlay players expect from OSRS.

Files touched:
- `Client_Base/src/orsc/Config.java` — `C_EXPERIENCE_DROPS = true` (drops on by default), `C_EXPERIENCE_COUNTER_COLOR = 4` (green text), `S_EXPERIENCE_DROPS_TOGGLE = true` (server gate; the in-game color/speed/counter sub-menu rides on this same flag, so users can re-tune in the Game Options panel).
- `server/local.conf` — `experience_drops_toggle: true` so the server-side value matches.

Why: second QoL pass. RuneLite popularized on-screen XP drops; their absence is one of the most jarring "this feels old" moments when coming to RSC from OSRS.

Implementation note: feature is OpenRSC-native (overlay component built at `mudclient.java:17092`), we just ungated it. No new render code authored.

Reversibility: flip the four flags. Players can recolor or disable in-game without a config change.

### 2026-04-24 — QoL pack 1 (10 features enabled)

Batch flip of upstream-shipped features that were gated off by default. All ten map to OSRS / RuneLite ergonomics RSC players coming from OSRS expect. Same playbook as the roof / XP-drop entries: feature lives in the upstream code, we just ungate it (server config + client `S_*` static default for clean init).

Features enabled:
1. **Drop-X** — drop-N items dialog (`want_drop_x` / `S_WANT_DROP_X`).
2. **Bank presets** — saved bank loadouts (`want_bank_presets` / `S_WANT_BANK_PRESETS`).
3. **Batch skilling** — auto-repeat skilling actions with progress bar (`batch_progression` / `S_BATCH_PROGRESSION`, plus `C_BATCH_PROGRESS_BAR = true` for default-on bar).
4. **Camera zoom** — scroll-wheel zoom (`zoom_view_toggle` / `S_ZOOM_VIEW_TOGGLE`; conf was already true from preservation, sync'd the static default).
5. **Hide fog** — fog hidden by default + toggle (`fog_toggle` / `S_FOG_TOGGLE` / `C_HIDE_FOG = true`).
6. **Ground item names** — names overlay on dropped items (`ground_item_names` / `S_GROUND_ITEM_NAMES` / `C_GROUND_ITEM_NAMES = true`; conf already true).
7. **Equipment tab** — separate gear-stats interface tab (`want_equipment_tab` / `S_WANT_EQUIPMENT_TAB`).
8. **Banknotes** — item certificates without an NPC (`want_bank_notes` / `S_WANT_BANK_NOTES`).
9. **Inventory count overlay** — `X / 30` indicator (`inventory_count_toggle` / `S_INVENTORY_COUNT_TOGGLE` / `C_INV_COUNT = true`).
10. **Keyboard shortcuts level 2** — number keys select right-click menu options, with `(1)`, `(2)`… labels rendered in the menu (`want_keyboard_shortcuts: 2` / `S_WANT_KEYBOARD_SHORTCUTS = 2`). Bumped from upstream preservation default of 1 (where keys work but labels don't show) for discoverability.

Files touched:
- `Client_Base/src/orsc/Config.java` — 14 static defaults bumped.
- `server/local.conf` — 8 conf keys flipped.

**SQLite schema dependency** (gotcha discovered post-pack): enabling `want_bank_presets: true` requires the `bankpresets` table to exist in the SQLite DB. Without it, every player save fails with `no such table: bankpresets`, which silently breaks the logout flow (player save fails → `logoutSaveSuccess()` never runs → player is never removed from `world.getPlayers()` → next login attempt gets `ACCOUNT_LOGGEDIN`). Fix: apply the upstream-shipped addon migration. On a fresh checkout, run:

```bash
sqlite3 server/inc/sqlite/voidscape.db < server/database/sqlite/addons/add_bank_presets.sqlite
```

Other addon-style features that may need their migrations applied if enabled later: `add_auctionhouse.sqlite`, `add_clans.sqlite`, `add_equipment_tab.sqlite`, `add_npc_kill_counting.sqlite` — see `server/database/sqlite/addons/`.

Why: voidscape's stated direction is "smooth as butter" RSC. RuneLite normalized this set of conveniences in the OSRS world; their absence is the first thing OSRS-trained players notice when touching RSC.

Reversibility: per-feature, flip the corresponding pair back. No data migration. Existing player save data is forward-compatible (banknotes, equipment tab, presets all use add-only schemas).

Risk notes:
- **Banknotes + bank presets** are economy-affecting. Reasonable for a private server with a small player base; revisit if/when we open up multiplayer.
- **Batch skilling** changes the feel of all gathering skills. If the user reports it feeling "too AFK", we can disable per-skill or tune the batch rate (search for `S_BATCH_PROGRESSION` usage).
- **Keyboard shortcuts level 2** adds `(N)` prefixes inside right-click menu rows. Visually busier than vanilla; revert to `1` if the labels feel cluttered.

### 2026-04-24 — Right-click bank / shop direct access

Right-clicking a banker now exposes a "Bank" menu option that opens the bank UI directly, skipping the dialogue tree. Same for shopkeepers ("Trade" option).

Files touched:
- `Client_Base/src/orsc/Config.java` — `S_RIGHT_CLICK_BANK = true`, `S_RIGHT_CLICK_TRADE = true`.
- `server/local.conf` — matching `right_click_bank: true` / `right_click_trade: true`.

Why: NPC dialogue gating before banking/shopping is the slowest part of every banking trip in vanilla RSC. Removing it is one of the highest-leverage QoL flips available.

Implementation lives entirely in upstream plugins — `server/plugins/.../authentic/npcs/Bankers.java`, `GeneralStore.java`, `CraftingEquipmentShops.java`, `AuburysRunes.java`, plus the `Gardener.java` custom NPC. The flags just enable already-coded paths.

Reversibility: flip the four flags. No data migration.

### 2026-04-24 — Equipment tab rollback / fog hardened off / zoom speed bump

Three small follow-ups after testing QoL pack 1:

**Equipment tab disabled** — clicking the new "Equipment" sub-tab inside the inventory interface froze the client. `S_WANT_EQUIPMENT_TAB` flipped back to `false` and `want_equipment_tab: false` in `local.conf`. Reason: upstream feature appears to misbehave in our config combo (likely interacts badly with `S_WANT_BANK_PRESETS` or a session state we haven't isolated). Not worth debugging now — feature wasn't in the original priority list.

**Fog: hardened off, toggle removed** — fog is now permanently hidden with no in-game way to re-enable it.
- `mudclient.java` — the client setter `setOptionHideFog(boolean b)` now ignores the server-pushed value and forces `C_HIDE_FOG = true`. This neutralizes the per-player `setting_showfog` cache override (which was making test player "void" see fog after they'd previously toggled it).
- `mudclient.java` — the "Fog - On/Off" menu entry render block was deleted. The entry no longer appears in Game Options.
- Server-side `Player.getHideFog()` and `S_FOG_TOGGLE` are unchanged — the client just ignores them. Cleaner than mass-editing server plugins; reversible by restoring the setter and re-rendering the entry.

Why the harder approach: user wanted fog removed as a player option entirely, not just default-off. Soft-default (per QoL pack 1) loses to a stored player override; this hardcoding doesn't.

**Zoom speed** — `ORSCApplet.java:755` `zoomIncrement` bumped from `10` to `25` per scroll-wheel tick. Range is `[0, 255]` so each tick now covers ~10% (was ~4%, painfully slow per user testing). Smoothness (frame interpolation) would need new tween code; not worth it yet.

### 2026-04-24 — Fog polarity fix + zoom bump (round 2)

**Fog**: previous "harden off" change had the polarity inverted — `C_HIDE_FOG` is misleadingly named in OpenRSC. Reading `mudclient.java:5223-5240` shows that `C_HIDE_FOG = true` actually applies a *closer* fog distance (`gameWidth*2 + cameraZoom*2 - 124` ≈ 2400 at default zoom), while `C_HIDE_FOG = false` pushes fog out to `cameraZoom * 6` ≈ 4500 (effectively invisible at standard view distances). Variable name reads as "hide fog" but stored value semantically tracks "show fog" (matches the cache key `setting_showfog` used in `Player.java:3252`). Flipped:
- `Config.java:42` — `C_HIDE_FOG = false` (the value that hides fog).
- `mudclient.java setOptionHideFog` — now forces `C_HIDE_FOG = false` regardless of server input.

**Zoom (round 2)**:
- Arrow-key zoom step bumped from `± 2` to `± 8` per tick (`mudclient.java:12231-12232` and `12249-12250`), to match the *perceptual* speed of rotation (also `± 2`, but rotation is over a 360° arc and reads as much faster). Replaced the `if (< 254) += 2` pattern with `Math.min(255, x + 8)` and the symmetric `Math.max(0, x - 8)` for clean clamping at the new step size.
- Scroll-wheel `zoomIncrement` bumped from `25` to `40`.

Reversibility: per-feature, restore the changed values. No data migration.

### 2026-04-25 — World-map auto-walker, slice 1 (server pathfinder + driver + admin command)

First slice of the world-map auto-walker feature (full plan: open the world map with `M`, click anywhere, character routes there). This slice ships only the server-side pathfinder and walking driver, exposed via an admin command — no UI yet.

Files added:
- `server/src/com/openrsc/server/model/WorldPathfinder.java` — sparse hash-keyed A* with a 50k-node frontier cap. Same-floor only for now (cross-floor via `ObjectTelePoints.xml` is slice 3). Walks the in-memory `TileValue` grid, ignoring transient mob/player blockers (those are handled at walk-time by `WalkingQueue.reset()` + our event's re-pathfind fallback). F2P regions are pruned via `Formulae.isF2PLocation` when `member_world: false`.
- `server/src/com/openrsc/server/event/rsc/impl/AutoWalkEvent.java` — `GameTickEvent` that drains the precomputed path into the `WalkingQueue` in 40-tile chunks (queue capacity is 50). Self-cancels on combat / busy / displacement; a 5k-node recovery re-pathfind handles knock-back / NPC-shove cases. `DuplicationStrategy.ALLOW_MULTIPLE` because `cancelAutoWalk()` stops the prior event in-tick before the cleanup pass runs — `ONE_PER_MOB` would reject the replacement.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Pathto.java` — `::pathto x y [floor]` admin command (gated on `isAdmin()`). Reports tile count, nodes explored, and ms.

Files touched:
- `server/src/com/openrsc/server/model/entity/player/Player.java` — `autoWalkEvent` field plus `getAutoWalkEvent` / `setAutoWalkEvent` / `cancelAutoWalk()` (idempotent; only clears the WalkingQueue if an auto-walk was active).
- `server/src/com/openrsc/server/model/entity/Mob.java` — `setOpponent(opponent)` now calls `cancelAutoWalk()` when opponent is non-null and the mob is a player. This is the combat-cancel hook; covers both attacker and defender because both sides get `setOpponent` during the combat handshake.
- `server/src/com/openrsc/server/net/rsc/handlers/WalkRequest.java` — `cancelAutoWalk()` before `resetAll/resetPath`. Covers 3D-click and minimap-click cancel, since both route through `WALK_TO_POINT`.

**Discovery correction**: docs/CODEMAP.md / earlier subsystem notes claimed `Constants.REGION_SIZE = 8`. Actual is `48` (matches RSC's sector size). Verified at `server/src/com/openrsc/server/constants/Constants.java:77`. Will be corrected when CODEMAP gets its next pass.

Tested: `::pathto 122 509` from Lumbridge spawn walked continuously to Varrock without teleport jumps; mid-walk attack on a chicken cancelled the auto-walk; mid-walk 3D click handed off cleanly to the new walk.

Reversibility: revert all six files. No data migration. No protocol change (slice 1 stays admin-command-only; opcodes land in slice 2).

### 2026-04-25 — Coords HUD overlay (always-on)

Always-visible top-left text showing `worldX, floorY (FN)` while in-game. Floor index is `worldY / 944`; the displayed Y is `worldY % 944` so upstairs / dungeons read in floor-local terms (otherwise upstairs Lumbridge prints Y ≈ 1592 instead of 648).

Files touched:
- `Client_Base/src/orsc/Config.java` — `C_SHOW_COORDS = true` (new flag, defaults on).
- `Client_Base/src/orsc/mudclient.java` — drawShadowText call inside the always-evaluated UI render block, immediately after the inventory-count overlay.

Reason: dev/test ergonomic for the auto-walker work — knowing exact tile coords without typing `::loc` makes verifying `::pathto x y` results trivial. Also useful for any future tile-coordinate-driven feature.

Reversibility: flip `C_SHOW_COORDS = false` (or revert the two files).

### 2026-04-24 — Disconnect = immediate unregister (no combat-log grace)

`RSCConnectionHandler:149` (the channel-close hook) now calls `player.unregister(FORCED, ...)` instead of `WAIT_UNTIL_COMBAT_ENDS`. Closing the client window now releases the player slot immediately rather than waiting up to 60 seconds for `canLogout()` to flip true.

Why: dev workflow was painful — closing the client and immediately trying to reconnect failed with "already logged in" because the combat-cooldown / busy-state checks in `canLogout()` could keep the player record in `world.getPlayers()` until the 60-second `UnregisterRequest` timeout fired.

Tradeoff: anti-combat-log protection is disabled for disconnects (a player could quit mid-fight by closing the window to escape). Acceptable for voidscape's current single-player dev posture; revisit when the server opens to multiplayer.

The explicit in-game Logout button still uses `FAIL_IN_COMBAT` (in `net/rsc/handlers/Logout.java:15`), so a player choosing to logout in combat still gets blocked.

## Re-syncing with upstream (future)

If we want to pull upstream changes:

1. Fetch latest upstream into a fresh `/tmp/openrsc-clone`.
2. Update the SHA in this file.
3. Re-run `scripts/fetch-upstream-snapshot.sh` so `upstream/openrsc-snapshot/` reflects the new SHA.
4. Manually merge / cherry-pick desired upstream changes into voidscape's working tree. We're a hard fork — there is no automated rebase.
5. Add a divergence entry recording the resync.

This is intentionally manual: we don't expect to track upstream tightly.

### 2026-04-25 — World-map auto-walker, slice 2 (WORLD_WALK_REQUEST + WORLD_WALK_ROUTE opcodes)

Wires the network round-trip for the auto-walker. Slice 1's pathfinder + walking driver now reachable from the client over the wire (still no UI; debug command `::wmw x y` types from chat).

New opcodes (append-only, never insert):
- `OpcodeIn.WORLD_WALK_REQUEST` — wire ID **35** (custom protocol). Payload: 4 bytes — `short destX; short destY` (floor encoded as `destY / 944`).
- `OpcodeOut.SEND_WORLD_WALK_ROUTE` — wire ID **100**. Payload: `byte ok; byte reason; short count; count × { short x; short y }`. Worst case ~6 KB at 1500-tile path; fits comfortably under the framing length cap.

Files added:
- `server/src/com/openrsc/server/net/rsc/struct/incoming/WorldWalkStruct.java`
- `server/src/com/openrsc/server/net/rsc/struct/outgoing/WorldWalkRouteStruct.java`
- `server/src/com/openrsc/server/net/rsc/handlers/WorldWalkRequest.java` — runs `WorldPathfinder` and registers an `AutoWalkEvent` on success; sends the route back either way (8 reason codes including `BUSY`, `COMBAT`, `NO_PATH`, `CAP_EXHAUSTED`, `CROSS_FLOOR`, `SAME_TILE`).

Files touched:
- `OpcodeIn.java`, `OpcodeOut.java` — appended new entries (never reordered).
- `PayloadCustomParser.java` — case `35` → `WORLD_WALK_REQUEST` in switch; `isPossiblyValid` requires length 4; `parse()` reads two shorts.
- `PayloadCustomGenerator.java` — `opcodeMap` entry 100; `generate()` writes ok/reason/count + tile pairs.
- `PayloadValidator.java` — class-instance check for `WorldWalkRouteStruct`.
- `PayloadProcessorManager.java` — bind `WORLD_WALK_REQUEST` to `WorldWalkRequest.class`.
- `ActionSender.java` — `sendWorldWalkRoute(player, ok, reason, route)` helper.
- `Client_Base/src/orsc/net/Opcodes.java` — `WORLD_WALK_REQUEST(35)` appended to `Out` enum.
- `Client_Base/src/orsc/PacketHandler.java` — case `opcode == 100` → `handleWorldWalkRoute(length)` reading the wire format.
- `Client_Base/src/orsc/mudclient.java` — `worldWalkRouteOk/Reason/X/Y` fields, `sendWorldWalkRequest(x, y)` method, `setWorldWalkRoute(ok, reason, xs, ys)` receiver (chats `World-walk: N tiles.` or `World-walk failed: reason=N`), `::wmw <x> <y>` debug chat command.

Protocol contract bump:
- `Client_Base/src/orsc/Config.java:CLIENT_VERSION` 10010 → **10011**.
- `server/local.conf:client_version` 10010 → **10011** (local.conf is gitignored — re-apply on a fresh checkout).

Free wire IDs picked: 35 (in) and 100 (out) were unused in the existing parser/generator maps. Future opcode adds should re-verify free IDs before claiming.

Tested: `::wmw 122 509` from Lumbridge succeeded with `World-walk: ~280 tiles.`. Failure paths verified: `::wmw 999 999` → `reason=1` (no_path), in-combat → `reason=7`, same tile → `reason=4`, cross-floor → `reason=5` (slice 3 unlocks).

Reversibility: revert all listed files and downgrade `client_version` back to 10010. The `::wmw` chat shortcut is removed in slice 5 once the world-map UI lands.

### 2026-04-25 — World-map auto-walker, slice 3 (cross-floor pathfinding via TelePointGraph)

Pathfinder now routes across floors through ladders / stairs. Two edge sources, both built lazily on first auto-walk request:

- **`ObjectTelePoints.xml`** — ~18 explicit `(point + command) → point` entries (Underground Pass, Watchtower mining camps, Lumbridge ↔ dwarven mine, etc.). Used as exact-match edges.
- **Climbable scenery (1×1 ladders)** — every `GameObject` whose `command1`/`command2` is one of `climb-up, climb up, go up, climb-down, climb down, go down` contributes an edge from each walkable adjacent tile to `(sameX, Formulae.getNewY(sameY, dir))`, mirroring `Ladders.coordModifier` for height-1 objects. This is the slice 3 v1.5 expansion — the XML alone is too sparse to reach most basements / upper floors.

Files added:
- `server/src/com/openrsc/server/model/TelePointGraph.java` — graph builder + accessor. Logs `XML edges: N, scenery sources: M, scenery edges: K, source tiles: T` on first build for diagnostics.

Files touched:
- `server/src/com/openrsc/server/model/WorldPathfinder.java` — accepts a `TelePointGraph`; `expand()` adds graph edges as zero-cost neighbours after the 8-cardinal sweep; cross-floor rejection only fires when `graph == null`.
- `server/src/com/openrsc/server/event/rsc/impl/AutoWalkEvent.java` — detects non-adjacent next-step in the planned path; if it matches a `TelePointGraph` edge from the player's current tile, calls `Player.teleport(x, y, false)` and resumes; otherwise falls back to the existing displacement-recovery re-pathfind. `feedChunk` now stops at telepoint boundaries instead of letting `Path.addStep` interpolate through walls.
- `server/src/com/openrsc/server/external/EntityHandler.java` — `getObjectTelePoints()` accessor (was previously only available via per-point lookup).
- `server/src/com/openrsc/server/model/world/World.java` — `getSceneryLocs()` accessor; `getTelePointGraph()` lazy-built per-world.
- `Client_Base/src/orsc/mudclient.java` — coords HUD now displays absolute server Y (was floor-local `Y % 944`). The HUD value is what `::wmw` and `::pathto` expect, so users can paste it directly.

**Caveats:**
- Multi-tile staircases (`def.getHeight() > 1`) are skipped — destination depends on object direction in a way the static graph can't predict cleanly. Future slice 3.x.
- Quest-gated ladders are NOT excluded; the auto-walker can teleport through them even when the player would normally fail the prereq. F2P region filtering at the destination protects against accidentally landing in P2P, but quest gates within a reachable region still get bypassed. Acceptable for voidscape's single-player dev posture; revisit for multiplayer.
- F2P region filter is applied per-edge at expansion time, not at graph build — so the graph shape is the same regardless of `member_world`, only traversal differs.

Tested: walk down a Lumbridge / Edgeville ladder manually, then `::wmw <surface coord>` from the basement — auto-walks back to the ladder and climbs up. F2P → P2P gating still rejects P2P-only destinations. Reverse direction also works. Combat / 3D-click cancel still effective mid cross-floor walk.

Reversibility: revert all five files. The graph is purely runtime; no persisted state.

### 2026-04-25 — World-map auto-walker, slice 4 (rendered world map PNGs)

The slice-5 UI clicks on a world map; this slice produces it. After two false starts the working pipeline is:

- **`tools/rsc-mapgen/mapgen.jar`** — vendored from [`sean-niemann/RSC-Landscape-Generator`](https://github.com/sean-niemann/RSC-Landscape-Generator) (MIT). Pure Java, no native deps. Parses `land63.{jag,mem}` + `maps63.{jag,mem}` and emits a 2304×10944 colored PNG with all 4 floors stacked vertically.
- **`scripts/render-worldmap.sh`** — wrapper. Stages voidscape's archives into the JAR's input slot, runs the JAR, splits the output into 4 per-floor PNGs at `Client_Base/Cache/worldmap/floor{0..3}.png` via ImageMagick.

Output sizes: F0=315 KB, F1=111 KB, F2=7 KB, F3=8 KB (~440 KB total).

False starts that didn't ship:
- **Server-side grayscale renderer (`::rendermap` admin command)** — initial v1 attempt rendered tiles from `TileValue.traversalMask` directly. Worked but produced a stark walkable-vs-blocked silhouette that didn't resemble the minimap (the server has no terrain-colour palette). Removed.
- **`@2003scape/rsc-landscape` (Node.js)** — installs and parses our archives correctly (`print-sector` works), but `generate-map` produces a black canvas with only POI icons. Likely a `node-canvas` regression on Node ≥ 20 (no terrain `fillRect` actually lands pixels). The same Node 25 environment runs other tools fine. Skipped after confirming the bundled-archive output is identical-broken.

Files added:
- `tools/rsc-mapgen/{mapgen.jar, LICENSE, README.md, UPSTREAM-README.md}` — vendored generator + provenance.
- `scripts/render-worldmap.sh` — wrapper.
- `Client_Base/Cache/worldmap/floor{0..3}.png` — committed render output.

Files touched:
- `.gitignore` — `tools/rsc-mapgen/input/` and `tools/rsc-mapgen/map.png` (regenerated each run).
- `docs/DIVERGENCE.md` (this entry).

Files removed:
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RenderWorldMap.java` — the superseded grayscale admin command. Plugin handler count returns to 456 (was 457 with this plugin).

Re-rendering: `scripts/render-worldmap.sh`. ~5 seconds. Required when the world geometry changes (rare — voidscape doesn't actively edit the landscape) or when bumping the `rsc-mapgen` vendor.

Pixel↔tile mapping (for slice 5 click handling): the per-floor image is 2304 × 2736. The world is 1008 × 944 tiles per floor — the renderer crops empty outer regions, so the image isn't a 1:1 grid. Slice 5 will calibrate empirically (click a known landmark, derive the affine transform).

### 2026-04-25 — World-map auto-walker, slice 5 (UI panel + click-to-walk)

The visible feature. RSCR2-style modal dialog, opened via a "World Map" button that appears under the minimap on hover.

Files added:
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java` — windowed dialog. Title bar with "Voidscape World Map" + "Close window" link. Floor tabs (Ground / Up 1 / Up 2 / Dungeon). Zoom +/− / Reset buttons in the right column. Map content centered on the player, scaled by current zoom (default 10×, range 1× to 24× via scroll wheel or the buttons). Yellow player marker + "You are here" label. Hand-baked landmark labels for ~13 F2P locations.

Files touched:
- `Client_Base/src/orsc/mudclient.java`:
  - `worldMapPanel` field; `worldWalkRouteX/Y/Reason` fields are now read by the panel for the route polyline.
  - "World Map" hover button rendered in `drawUiTabMinimap` when the cursor is over the minimap, the minimap-tab-icon area, or the button itself.
  - `drawUi` top: intercept clicks on the panel before any other handler can fire (using last frame's window rect, so side-panel handlers don't receive a click meant for the dialog).
  - `drawUi` bottom: render the panel last, so it draws on top of side panels, minimap, and chat tabs.
  - `runScroll`: routes the wheel to `worldMapPanel.adjustZoom()` when the dialog is open.

Pixel ↔ tile transform: rsc-mapgen renders sectors X=48..63 (covering world tile X 0..768) × Y=37..55 (world tile Y 0..912) at 3 px/tile, then flips the whole image on the X axis. Map math:
- `pngX = 2303 − worldX × 3`
- `pngY = (worldY % 944) × 3`

World tiles X=768..1008 and Y=912..944 are not rendered — those regions fall outside the PNG. Voidscape's F2P content all lives within the rendered range, so this is fine in practice.

Click flow: `mudclient.handleClick` → world tile via the inverse transform → `sendWorldWalkRequest(x, y)` → server's `WorldWalkRequest` runs the pathfinder + drives `AutoWalkEvent` (slice 1) → `SEND_WORLD_WALK_ROUTE` (slice 2) → panel renders the polyline in green dots.

Discovery notes:
- The Ctrl+M hotkey was tried first but doesn't work on macOS — `controlPressed` doesn't track the macOS Command/Control through this client's input stack reliably. The hover-button is the canonical opener.
- The grayscale "::rendermap" command from slice 4's first attempt has been retired in favour of the `rsc-mapgen`-based PNG.

Caveats / known gaps versus RSCRevolution2's reference:
- No POI icons (altars, cooking guilds, etc.) on the map.
- Hand-baked landmark list is ~13 names; RSCR2 ships dozens with smart placement.
- No search box, no favourites bar.
- These are deferred to slice 6 (polyline polish) and beyond.

Reversibility: revert the two files. The committed `Cache/worldmap/floor*.png` PNGs from slice 4 stay either way.

### 2026-04-25 — World-map auto-walker, slice 5b (vendored rsc-world-map assets + UX polish)

Replaces slice 5's hand-baked rendering with a port of `2003scape/rsc-world-map@70f488a` (AGPLv3). The plane PNGs are the same upstream renderer's output (2448×2736, 3 px/tile, X-mirrored — different transform from slice 4's `rsc-mapgen` output) and we vendor 110 place-name labels, 309 POI markers, and 42 sprite icons alongside. Refresh procedure documented in `Client_Base/Cache/worldmap/UPSTREAM.md`.

Files added:
- `Client_Base/Cache/worldmap/UPSTREAM.md` — vendor SHA + refresh procedure.
- `Client_Base/Cache/worldmap/plane-{0..3}.png` — replaces slice 4's `floor{0..3}.png` (the slice-4 PNGs are removed; the `rsc-mapgen` pipeline is no longer used for client rendering).
- `Client_Base/Cache/worldmap/labels.tsv`, `points.tsv`, `icons/*.png`, `stone-background.png` — overlay data.

Files touched:
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java` — major rework: 4 discrete zoom levels (0.5×, 1×, 2×, 4×) replacing slice 5's 1×–24× continuous zoom; drag-pan with 3 px deadzone; stone-textured zoom buttons matching upstream; cursor-focused wheel zoom; **75% × 75% windowed dialog** (was fullscreen); **default zoom level 2 (4×)** so the player marker lands in a ~58 × 43 tile view comparable to the in-game minimap radius; floor-switch re-centers the map. New `pollMouse(mx, my, buttonDown, gameWidth, outWorld)` API replaces single-shot `handleClick`; the panel manages drag transitions internally and walk only fires on a non-drag release.
- `Client_Base/src/orsc/mudclient.java`:
  - `drawUi` top: switched to the new `pollMouse` API, called every frame so drag state advances even when the cursor leaves the content rect.
  - `drawUi` bottom: render unchanged (still last, on top).
  - `drawUiTabMinimap`: World Map button is now persistent whenever the minimap tab is the active side panel (`showUiTab == Config.MINIMAP_AND_COMPASS_TAB`), in addition to the existing hover behavior.
  - `runScroll`: plumbs `mouseX/Y` through to `adjustZoom(delta, focusX, focusY)` so the wheel zoom centers on the cursor.
  - `setWorldWalkRoute`: numeric `reason=N` chat dump replaced by per-code messages (e.g. `1 → "Can't find a path there."`).
- `PC_Client/src/orsc/ORSCApplet.java` — `mouseDragged` returns early if the world-map panel is visible, so camera rotation doesn't fight the drag-pan underneath.

Pixel ↔ tile transform (matches `Client_Base/Cache/worldmap/UPSTREAM.md`):
- `pngX = 2471 − worldX × 3`
- `pngY = (worldY % 944) × 3`

(Different origin from slice 4's `rsc-mapgen` transform; this one is empirically calibrated against 7 city labels, ±10 px.)

Server-side change — `server/src/com/openrsc/server/model/WorldPathfinder.java`:
- Added `nearestWalkable(end, radius)` ring search and a snap step in `findPath`. When a click lands on a wall, building roof, coastline, or (on F2P worlds) a P2P-restricted region, the pathfinder snaps to the nearest walkable tile within radius=5 (Chebyshev — matches the in-game minimap radius). Maintains floor and F2P invariants. Without this, "click anywhere on the world map" was silently failing on every imprecise click; this is the difference between an annoying feature and a useful one.

POIs and labels render only on plane 0 — upstream's `labels.json` / `points.json` are floor-0-only. Floors 1–3 show the geometry without overlay text; this matches upstream behavior.

Reversibility: revert the listed files and restore `floor{0..3}.png` from git. Server change is additive (new method + snap branch) and reverts cleanly. No protocol or opcode changes — `CLIENT_VERSION` stays at 10010.

### 2026-04-25 — Home teleport spell (level 0, no runes, top of spellbook)

A new type-0 (self-cast) magic spell named "Home teleport" that drops the caster at Lumbridge (120, 648). Zero level requirement, zero runes, zero XP. Inherits the existing wilderness-20+ block, mod-room block, and Karamja/plague-sample handling automatically (everything in `SpellHandler.canTeleport` and the pre-switch cleanup applies because the spell goes through `handleTeleport` via the standard `CAST_ON_SELF` opcode 137).

To put the spell at the **top** of the spellbook (matching the reference UX from RSCRevolution2-style servers), the wire ID had to be 0 — meaning every existing spell shifted by +1. The spell-ID encoding lives in `Constants.spellMap`, the client's hardcoded `EntityHandler.loadSpellDefinitions()` ArrayList, and (loosely, by name) `SpellDef.xml`. All three were edited together; the `Spells` enum is index-independent (per its own header comment) so adding `HOME_TELEPORT` was positional only.

Files touched:
- `server/conf/server/defs/SpellDef.xml` — new `<SpellDef>` as first entry. Empty rune map written as paired `<requiredRunes></requiredRunes>` (NOT self-closing — XStream returns `null` for `<requiredRunes/>` and `getRunesRequired().entrySet()` would NPE).
- `server/src/com/openrsc/server/constants/Spells.java` — `HOME_TELEPORT` enum value (above `LUMBRIDGE_TELEPORT` for readability).
- `server/src/com/openrsc/server/constants/Constants.java` — `spellMap`: `HOME_TELEPORT → 0`, every existing entry +1 (47 entries shifted; new last entry is `CHARGE → 48`).
- `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` — `case HOME_TELEPORT: player.teleport(120, 648, true); break;` at the top of the `handleTeleport` switch.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — `spells.add(new SpellDef("Home teleport", "Teleports you to Lumbridge", 0, 0, 0, empty runes clone))` as the FIRST entry in `loadSpellDefinitions()`. Empty `LinkedHashMap` clone works because the existing pattern constructs a fresh map at the top of the method and only puts to it before each subsequent `spells.add`.

Protocol contract bump:
- `Client_Base/src/orsc/Config.java:CLIENT_VERSION` 10011 → **10012**.
- `server/local.conf:client_version` 10011 → **10012** (local.conf is gitignored — re-apply on a fresh checkout).

The bump distinguishes this spell-ID wire-format change from the prior 10010 → 10011 bump that landed with the world-walk opcodes. A stale 10011 client jar would have a matching version handshake but mis-interpret every spell-cast packet (its index 0 = Wind strike, server's index 0 = Home teleport).

Reversibility: revert all five source files, downgrade `CLIENT_VERSION` back to 10011. The spellMap shift is the only non-trivial part — it's mechanically reversed by walking the `put(..., N)` lines back one. No data migration; no player save touches the spell list.

Tested in-game: spellbook first entry reads "Level 0: Home teleport" in yellow, click teleports to Lumbridge with the standard bubble GFX + "spellok" sound, 1.9s cooldown observed, wilderness 20+ blocks the cast with the standard "mysterious force" message, and Wind strike (now wire ID 1) + Lumbridge teleport (now wire ID 16) still work as before.

### 2026-04-25 — Auto-open basic doors and farm gates on walk-into

RSC1's authentic behaviour is to ship every interior door / farm gate closed and force a manual click to open. With the world-map auto-walker now plotting cross-region routes, this was the dominant friction source — "I clicked Lumbridge and got NO_PATH because the farm gate is closed." Voidscape's stance is mostly authentic + small QoL, and auto-opening trivial pass-throughs is an obvious QoL.

The change touches three files and works in two layers (planner + walker):

1. **`server/src/com/openrsc/server/model/WorldPathfinder.java`** — `canStep` now treats a step blocked by `FULL_BLOCK` or wall flags as **passable** when there's an auto-openable closed gate within a 3×3 of either the source or destination tile. "Auto-openable gate" = scenery with `GameObjectDef.name == "gate"` (case-insensitive exact, so quest variants like "metal gate" / "ardounge wall gateway" / "gnome stronghold gate" / "Sturdy Iron Gate" stay impassable) AND `command1 == "open"`. F2P-region guard preserved so a free player can't get planned into a P2P-only quest gate. Bumped `DEFAULT_NODE_CAP` from 50 000 to 500 000 along the way — 50k exhausted on dense urban routes (Edgeville → Lumbridge through Varrock).
2. **`server/src/com/openrsc/server/model/WalkingQueue.java`** — when `PathValidation.checkAdjacent` rejects a step for a player, we look first for a basic wall door (`DoorDef.name == "Door" / command1 == "Open"`) on the blocking edge and replace it with id 11 (open variant); failing that, we scan a 3×3 around source and dest for scenery gate ids 57 (`GATE_METAL_GENERIC_CLOSED` → 58) or 60 (`GATE_WOODEN_GENERIC_CLOSED` → 59) and swap them. In both cases we re-queue the step (`Path.addDirect`) and schedule a 3-second auto-close via `Functions.addloc` — same behaviour as a normal click, minus the teleport.
3. **`server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`** — added `::pf <x> <y>` admin command for diagnosing world-walk failures. Reports whether the path was found, the explored-node count, and the `WorldPathfinder.Reason` enum value.

Excludes by design: gates with named variants (Al Kharid toll, magic doors, jail doors, blacksmith's, every quest-restricted door — they all have explicit handlers in `DoorAction.java` and stay manual), and any wall whose `DoorDef.name` isn't exactly "Door". The pathfinder allows planning through other generic gates by name, but the walker only auto-opens 57/60, so for unhandled gate ids the walker stalls at the gate and the player clicks manually (which is correct for things like the Al Kharid toll). NPCs continue to bump on closed doors (`mob.isPlayer()` guard). Cardinal moves only — diagonals don't auto-open.

Reversibility: revert the three files. Pure additive changes inside existing failure branches.

### 2026-04-25 — Edgar (Lumbridge → Edgeville teleport NPC)

A new custom NPC named "Edgar" stands in Lumbridge at (124, 640) in classic blue wizard robes. Right-click → **"Teleport to Edgeville"** sends the player to the Edgeville bank entrance (217, 449) with the standard teleport bubble. Free, no level requirement, no inventory cost, no wilderness check (the NPC is unreachable from wilderness anyway, and `player.teleport` already clears combat).

The mechanism uses RSC's existing `command1` slot in the NPC def — the client renders any non-empty `command` string as an extra right-click option, and the server routes the click through `OpNpcTrigger.onOpNpc(player, npc, command)`. **No protocol or opcode change**; reuses the existing `NPC_COMMAND` opcode 202.

Files touched:
- `server/conf/server/defs/NpcDefsCustom.json` — appended id 836 ("Edgar", wizard sprites/colors mirroring the vanilla Wizard NPC, `command: "Teleport to Edgeville"`, `attackable: 0`, `aggressive: 0`).
- `server/conf/server/defs/locs/NpcLocs.json` — static spawn at (124, 640) with min == max == start (no patrol).
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — appended `npcs.add(new NPCDef("Edgar", ...))` at the end of `loadNpcDefinitions4`, before the `Config.S_WANT_CUSTOM_SPRITES` block. **Position-dependent**: client-side IDs are assigned by `i++` and must match the server JSON's `id`. Append after the previous-last NPC ("Ash" at id 835).
- `server/plugins/com/openrsc/server/plugins/custom/npcs/EdgevilleTeleportWizard.java` — new file; implements `OpNpcTrigger`. Guards on NPC id 836 + `command.equalsIgnoreCase("Teleport to Edgeville")`, then `player.teleport(217, 449, true)`. `blockOpNpc` returns `true` for the matching case to claim the action.

No `CLIENT_VERSION` bump — wire format unchanged. No `NpcId.java` enum entry (literal id in the plugin is fine for a one-off custom NPC).

Recipe-doc gap surfaced during implementation: `docs/recipes/add-npc.md` doesn't mention the `Client_Base/EntityHandler.java` step at all, and its "≥ 10000" custom-ID guidance is aspirational — actual practice (Voidscape NPCs at 800-835) is **next sequential after the highest existing ID**, because the client's `i++` ordering forces sequential alignment. Both gaps documented in the new `docs/recipes/add-right-click-action-npc.md` playbook.

Reversibility: revert the four files. Nothing else references id 836.

Tested in-game: Edgar visible at (124, 640); right-click menu shows "Teleport to Edgeville"; clicking it lands the player at the Edgeville bank with the standard bubble GFX. Server log confirms `EdgevilleTeleportWizard.onOpNpc` fires on each click. Repeated teleport works (walked back and re-clicked); no leftover state.

### 2026-04-25 — Void Scimitar, slices 1–3 (tooling + dual-skill schema + item def stub)

First three slices of voidscape's first **custom weapon**. Tooling layer first, then schema extension to support dual-skill requirements (a generally useful primitive, not Void-Scimitar-specific), then the item def itself reusing an existing wielded sprite. Slices 4–6 (AI-generated art + dedicated wielded sprite block) land separately.

**Slice 1 — sprite-art pipeline (`tools/voidscim-art/`)**

First content-pipeline tool in the repo. Three Python scripts under `tools/voidscim-art/`:

- `extract_ref.py` — pulls a sprite from `Authentic_Sprites.orsc` (a deflate ZIP keyed by integer-string entries 0–3695) to PNG + sidecar JSON. Implements `Sprite.unpack` exactly: 25-byte big-endian header (`width i32, height i32, requiresShift u8, xShift i32, yShift i32, something1 i32, something2 i32`) followed by `width × height` int32 ARGB pixels.
- `pack.py` — inverse of extract. Default mode is dry-run (writes `<archive>.new`); `--commit` requires a `.bak` to exist before atomic-renaming over the original. Per-input `<input>.json` sidecars override the shared `--sidecar`.
- `generate.py` — wraps OpenAI's `gpt-image-2` `/v1/images/edits` endpoint (multipart, multi-image reference). Reads `OPENAI_API_KEY` from env only; never accepts a key on the CLI. Default request is `1024x1024 quality=high`, downscaled to a target sprite size with PIL `LANCZOS` + optional palette-quantize. `--dry-run` skips the API and writes solid-color placeholders for plumbing tests.

Each script also has a `prompts/*.txt` companion (icon and wielded variants). Roundtrip verified: extract → pack → extract on Bronze Scimitar inventory icon (entry 2198) is byte-equal at 2585 bytes; the other 2258 archive entries are preserved with zero content mismatches.

**Per-sprite vs. slot dimensions** — extracting Bronze Scimitar's icon revealed the actual sprite is 20×32, not 48×32. The 48×32 figure (e.g. `mudclient.java:3313` and `Sprite.getUnknownSprite(48, 32)`) is the *display slot* size; per-sprite dimensions are stored in the header and shifted into the slot via `xShift`/`yShift` (and the `something1`/`something2` fields, which match the slot canvas). AI prompts and `--target-size` should match the per-sprite dimensions, not the slot.

**Slice 2 — dual-skill requirement schema**

`ItemDefinition` only supported a single `(requiredLevel, requiredSkillIndex)` pair (`server/src/com/openrsc/server/external/ItemDefinition.java:112`,`:119`). Existing items that require two skills (spear → Att 5+, throwing knife → Att, staff of iban → Att, battlestaves → Att) hardcode the secondary check by name keyword in the three enforcement sites — not data-driven.

Added two optional fields with `-1` defaults so existing items are unaffected:
- `secondaryRequiredLevel` (default `-1`)
- `secondaryRequiredSkillIndex` (default `-1`)

JSON loader (`org.json` based, not Gson) at `server/src/com/openrsc/server/external/EntityHandler.java:369-407` was extended with `if (item.has("secondaryRequiredLevel")) toAdd.setSecondaryRequiredLevel(...)` after the constructor call — no constructor signature change, no risk to the 1,898 existing item entries across `ItemDefs.json` + `ItemDefsCustom.json` (none of which carry the new fields).

The three enforcement sites each got a parallel `if (secondaryLevel >= 0 && ...)` block, separate from the existing keyword-driven optional check (so spears + def-driven secondary don't interfere):
- `server/src/com/openrsc/server/model/container/Equipment.java:668` (the `ableToEquip` path; gated on `hasRequirement` so the `NO_LEVEL_REQUIREMENT_WIELD` config preset still skips it).
- `server/src/com/openrsc/server/model/entity/player/Player.java:1095` (`checkEquipment2`, the equipment-tab path).
- `server/src/com/openrsc/server/model/entity/player/Player.java:1172` (`checkEquipment`, the no-equipment-tab path).

The duplication across the three sites was *not* refactored — that's pre-existing tech debt, and CLAUDE.md rule "don't refactor on the side" applies. The new check mirrors the same player-message format ("You need to have a Magic level of 70").

**Slice 3 — Void Scimitar item def stub (id 1593, reuses appearance 53)**

First voidscape custom item, allocated as the next sequential ID after Boomstick (id 1592). Stats are ~25% above Rune Scimitar (which is +44/+44 at level 40 Att): Void Scimitar is **+55 weaponAimBonus / +55 weaponPowerBonus / +8 magicBonus / 0 armour / 0 prayer / 64000 basePrice**, level **70 Attack + 70 Magic** to wield (verified `Skill.MAGIC.id() = 6` in voidscape's non-INFLUENCE preset via `Skills.java:236-237` registration order). `isUntradable: 1` to keep the test item out of any drop/trade flows. `appearanceID: 53` reuses the Rune Scimitar wielded sprite as a temporary placeholder — replaced with a dedicated sprite block in slice 6.

**Recipe-doc gap surfaced (third time)**: `docs/recipes/add-item.md` originally said "Voidscape custom items: keep them above 10000 so they never collide with future authentic items." That guidance was wrong — `EntityHandler.getItemDef(int)` does `items.get(id)` against a positional `ArrayList` (`server/src/com/openrsc/server/external/EntityHandler.java:842-847`), so an item with id 10000 in JSON loaded after Boomstick (id 1592) lands at list position 1593 and `::item 10000` returns null. The recipe and its checklist were corrected to "next sequential after the highest existing ID". Same gap as the Edgar NPC discovery — the OpenRSC engine treats id ≡ load-order position for both items and NPCs. Worth a future audit pass through every "≥ 10000" claim in `docs/recipes/`.

**`restrict_item_id` config bump** (local.conf): preservation/voidscape inherited `restrict_item_id: 1289` from upstream, which is the **authentic-only cap** — it blocks `Inventory.add()` for any item id > 1289 (`server/src/com/openrsc/server/model/container/Inventory.java:104-109`), shows the player "World doesn't allow itemid N; only allows up to 1289", and silently rejects the spawn. This shadows every voidscape custom item including the pre-existing Boomstick (id 1592) and now Void Scimitar (id 1593). Bumped to **`restrict_item_id: 9999`** in `server/local.conf:79` — leaves headroom for future custom content without disabling the restriction entirely (id < 0 disables, but a sane upper bound catches genuinely-broken IDs from typos). `local.conf` is gitignored — this override must be re-applied on fresh checkouts (alongside the other local.conf overrides documented in prior entries: `member_world: false`, `is_localhost_restricted: false`, `client_version: 10012`, etc.).

**Client-side `EntityHandler` registration**: same gotcha as Edgar — the client's `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` has its own positional `items` list and reports `itemCount() - 1` as `maxItemId` to the server during login (`Client_Base/src/orsc/mudclient.java:15111` `tellLimitations`). If the server has item id N but the client's list only goes up to N-1, `Inventory.add()` fails with **"Your client could not receive {item}, it drops to the ground"** (`Inventory.java:115-122`). Appended `items.add(new ItemDef("Void Scimitar", ..., 1593))` at the end of `loadItemDefinitions()` — must be at position 1593 (after Boomstick at 1592) since both server and client iterate by load order. Client jar requires rebuild + restart for the change to take effect; the running PC client wedge cached the old itemCount otherwise. **Updated `docs/recipes/add-item.md`** to make the Client_Base step mandatory in the file table (was previously listed as "only if a new sprite is needed", which is wrong — every new item id requires the client-side append regardless of whether the sprite is shared).

Files touched:
- `server/conf/server/defs/ItemDefsCustom.json` — appended a 23-line entry after the previous-last "Boomstick" (id 1592). Surgical text insert (not Python `json.dump`, which normalizes the file's pre-existing inconsistent formatting and produces a noise-only 39-line diff).

Build clean, server boots through the item-loading phase without any `JSONException` (verified via background server boot — the unrelated `BindException` on a leftover server instance happens *after* item-def loading + grounditems + login executor).

**Reversibility**:
- Slice 1: `rm -rf tools/voidscim-art/`. Pure additive; no other files touched.
- Slice 2: revert the four files (one new field block + three enforcement sites). Existing items unaffected because the fields default to `-1`.
- Slice 3: remove the 23-line block from `ItemDefsCustom.json`.

No protocol bump (no opcodes touched). No DB migration (item defs are static and re-loaded on boot).

### 2026-04-25 — Void Scimitar, slices 4–5 (AI-generated inventory icon + wielded recolor)

Finishes the Void Scimitar feature. Inventory icon is unique voidscape art generated via OpenAI's image API; wielded sprite is a void-purple recolor of the existing "sword" wield block (Slice 5 deviated from the original "18 unique frames" plan in favour of the "minimum-viable recolor" alternative the Plan agent flagged — the recolor lands the visual in 5 minutes vs. several hours of frame-consistency iteration). Final state: `::item 1593 1` produces a chunky purple curved-blade icon in the inventory; equipping it shows a sword silhouette tinted royal purple in-hand.

**Slice 4 — inventory icon**

Two gotchas surfaced in the sprite pipeline that the discovery missed:

1. **The RSC sprite binary format does not use alpha channel.** Each int32 pixel is `0x00RRGGBB` with the high byte unused; **transparency is signalled by the sentinel value `0x00000000`**, not by alpha. First extract tried to byte-swap `[A,R,G,B]` → `[R,G,B,A]` which produced fully-transparent PNGs. Fixed in `tools/voidscim-art/extract_ref.py` and `pack.py`: `0` → transparent, anything else → opaque RGB with alpha forced to 255. Pure-black opaque pixels bumped to `(1,0,0)` on encode to avoid colliding with the transparency sentinel.

2. **`appearanceID` (server) and `spriteID` (client) are decoupled for inventory icons.** The Plan and discovery treated them as the same number. They are not. The inventory icon is rendered by `GraphicsController.spriteSelect(ItemDef)` using `client.ItemDef.spriteID + mudclient.spriteItem (=2150)` — a purely client-side index into `Authentic_Sprites.orsc`. The wielded sprite uses the server-sent `appearanceID` flowing through `wornItems[]` → `EntityHandler.animations.get(id)` (a separate list). All seven authentic scimitar tiers reuse `client.spriteID = 83` and recolor at render time via `pictureMask`. **Implication**: a unique inventory icon can be wired without touching the wielded path at all — just pack a new sprite at a fresh archive index, update `client.ItemDef.spriteID` to point there, set `pictureMask = 0` (no recolor — the sprite carries its own colors).

Asset generation flow:
- First attempt with `gpt-image-2` API: 403 — "organization must be verified". User completed identity verification at https://platform.openai.com/settings/organization/general but the dashboard goes through an "Identity in review" state that gates API access for an unknown duration.
- Fallback to `gpt-image-1.5` (no verification gate). The `background=transparent` parameter produced mostly-empty PNGs (22 opaque pixels of 1M) — the model doesn't honour that flag reliably. Without the flag, it produced a usable filled-block purple scimitar but on a black background; alpha-keying via simple luma threshold (R+G+B ≤ 30 → transparent) recovered the silhouette cleanly.
- Final pass used `gpt-image-2` via the **ChatGPT web UI** (verification not required for the web product, only the API), prompted with the same text and reference image. Output was substantially better — chunky filled blade, gold crossguard, green-pommel detail — and already had proper alpha. Downloaded PNG → `tools/voidscim-art/out/manual-icon-1024.png` → downscaled to 40×29 with PIL `LANCZOS` and a hard alpha threshold (a > 96) to crisp-up the edges → packed into `Authentic_Sprites.orsc` at index 2757 (= `spriteItem` 2150 + chosen `spriteID` 607). Final 40×29 has 183 opaque pixels.

Files touched in slice 4:
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — entry 2757 added (committed via `pack.py --commit`; `.bak` retained per the tool's safety check). Tracked binary file; the diff is large but localised.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — Void Scimitar's client `ItemDef` spriteID flipped 53 → 607, spriteLocation `"items:53"` → `"items:607"`, pictureMask kept at 0 so the AI-painted colors render as-is.
- `tools/voidscim-art/generate.py` — added `--model` flag (was hardcoded), `background=transparent` and `output_format=png` defaults, and `.env` loader (`load_dotenv`) so the OpenAI key never has to be passed on the CLI or pasted into a tool call. `.env` and `.env.example` added; `.env` is in the tool's `.gitignore`.
- `tools/voidscim-art/prompts/void-scimitar-icon.txt` — iterated to: explicit "FULLY FILLED PIXEL ART, NOT a line drawing", "CANVAS FILL: edge-to-edge", reference-image specifics. The first version produced thin outline art at low canvas-fill — the explicit canvas-fill clause was the unlock.

**Slice 5 — wielded recolor (instead of unique frames)**

The big gotcha that re-shaped the slice: **everything inside `if (Config.S_WANT_CUSTOM_SPRITES) { … }` in `loadAnimationDefinitions` (`Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java:4408-4876`, all the entries commented `//229` through `//558`) is skipped at runtime in voidscape because `S_WANT_CUSTOM_SPRITES=false`** (`Client_Base/src/orsc/Config.java:131`). The numbered comments (//229, //476 "Bronze Scimitar", //482 "Rune Scimitar", etc.) are misleading — those AnimationDefs simply don't exist in the runtime list. The "scimitar" sprite block the discovery and Plan agent referenced is also dormant under the authentic-sprite pipeline. So registering a new animation INSIDE the conditional does nothing.

Mitigation: append the new AnimationDef AFTER the closing brace of the `S_WANT_CUSTOM_SPRITES` block, so it loads unconditionally. Lands at runtime index **230** (not 229 — comment numbering is off-by-one from runtime because the `if (S_ALLOW_BEARDED_LADIES)` branch at //6 contributes 2 source lines but only 1 runtime entry, AND there appears to be one further off-by-one we didn't isolate; verified empirically by setting `appearanceID: 229` first and observing the wielded sprite rendered as a scythe (which is //228), implying the actual runtime index of `//228 scythe` is 229. Bumped to 230 and got the intended void-purple sword shape).

Files touched in slice 5:
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — appended `animations.add(new AnimationDef("sword", "equipment", 0x6a0dad, 0, true, false, 0));` after `S_WANT_CUSTOM_SPRITES` block closes. Reuses the unconditional "sword" sprite block (animations 47–53 are all `name="sword"` with different colors); the Void Scimitar then renders with the same wielded silhouette as authentic swords/scimitars but with a deep void-purple charColour.
- `server/conf/server/defs/ItemDefsCustom.json` — Void Scimitar `"appearanceID": 53` → `"appearanceID": 230`. (Server-side appearanceID is the runtime index into the client's `EntityHandler.animations` list, transmitted via the player-appearance update path.)

**Forward compatibility caveat**: the runtime index `230` is dependent on `S_WANT_CUSTOM_SPRITES` staying `false`. If voidscape ever flips that flag on, all 330+ entries inside the conditional load and our new entry shifts to position 559 or thereabouts, breaking the void scimitar's wielded look. Documented inline in the source comment; if/when that flag flips, hunt for `voidscape:` markers in `EntityHandler.java` and re-set the JSON `appearanceID` to wherever the entry actually lands at runtime.

**Reversibility (slices 4 + 5)**:
- Restore `Authentic_Sprites.orsc` from `Authentic_Sprites.orsc.bak`.
- Revert the `Client_Base/src/.../EntityHandler.java` edits (one item-def line + one new animation line).
- Revert `ItemDefsCustom.json`'s `appearanceID` and revert any other Void Scimitar field changes.
- `rm -rf tools/voidscim-art/out/` to clear scratch artifacts.

**OpenAI key hygiene**: the user pasted the same `sk-proj-...` key into chat three times during this work. Tooling reads exclusively from `tools/voidscim-art/.env` (gitignored) — no key was ever committed or passed via Bash command. The key should still be rotated; treating it as compromised given the chat-transcript exposure.

No protocol bump. No DB migration. No new opcodes. No plugin recompile (item defs are JSON; AnimationDefs are client-only).

### 2026-04-26 — Void Bow (id 1594): F2P 80-Ranged bow that fires without ammo

Second voidscape custom weapon. F2P, tradeable, admin-spawnable (`::item 1594 1`); requires Ranged 80 to wield; damage tier matches a magic-shortbow + rune-arrow setup (`rangedPower = 40`). Reuses the wielded animation slot (`appearanceID 108`) shared by all magic/yew/maple bows — only the inventory icon is unique.

**No-ammo branch** — `RangeEvent.run()` (`server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java:131`) gets a pre-ammo-lookup branch: if `RangeUtils.isVoidBow(weaponId)`, set `ammoId = weaponId` and skip both `takeAmmoFromInventory` and `takeAmmoFromEquipment`. The bow id flows downstream as the ammo sentinel — `CombatFormula.rangedPower()` adds a `case VOID_BOW: return 40;` arm next to `RUNE_ARROWS`, and `RangeUtils.handleArrowLossAndDrop()` early-returns when `isVoidBow(arrowId)` so no ground item gets spawned. `applyPoison` checks `POISONED_ITEMS.contains(arrowId)` which the void bow id never matches, so no extra guard is needed there. `OSRS_COMBAT_RANGED` is `false` in voidscape's `local.conf`, so the parallel `OSRSCombatFormula.Ranged` path was not touched; if that flag ever flips on, mirror the `VOID_BOW` case there too.

**Bow whitelist** — `RangeUtils.BOWS` and `RangeUtils.SHORT_BOWS` (`server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java:33,43`) gate which equipped weapons trigger `RangeEvent` and the 4-tile shortbow firing radius. Void bow added to both. Also added to `ALLOWED_PROJECTILES` for parity even though `canFire()` is bypassed by the early-branch — keeps the map self-consistent if anything else ever consults it.

**Members-flag gotcha** — first slice copy-pasted the Magic Shortbow line in `Client_Base/src/.../EntityHandler.java`, which is `membersItem=true` (12th boolean in the 15-arg `ItemDef` constructor at `Client_Base/src/com/openrsc/client/entityhandling/defs/ItemDef.java:55`). On an F2P-flagged world the client refused to wield with a "members item" message even though the server JSON had `isMembersOnly: 0`. Fix is to flip that 12th boolean to `false` for any F2P custom item — the client-side def is consulted independently of the server def.

**Sprite pipeline** — same flow as the Void Scimitar: `tools/voidscim-art/extract_ref.py` → `gpt-image-1.5 /v1/images/edits` (gpt-image-2 needs API verification; the `gpt-image-1.5` fallback works) → manual crop-to-bbox → LANCZOS to 30×26 → alpha threshold 32 (no palette quantization, no dilation). `pack.py --commit` lands at archive index 2758 (= 2150 + spriteID 608). Sidecar header is 30×26 with `xShift=9, yShift=3, something1=48, something2=32` to center within the 48×32 inventory slot. Note: bumped `generate.py`'s HTTP timeout from 180→420s — gpt-image-1.5 quality=high regularly took longer than 3 minutes per request.

**Iteration history** — first attempt used the magic-shortbow sidecar's 33×21 dimensions and produced a "smushed" icon (the AI bow is near-square, and forcing it into wide-and-short proportions distorted it). Second pass at 36×32 looked correct but read as too large in the slot. Settled on 30×26 with `xShift=9, yShift=3` — preserves the bow's ~1.0 aspect and matches the visual size of authentic bow icons.

**Reversibility**:
- Restore `Authentic_Sprites.orsc` from the `.bak`.
- Revert one line in each of: `ItemDefsCustom.json`, `ItemId.java`, `RangeUtils.java` (BOWS, SHORT_BOWS, ALLOWED_PROJECTILES, `isVoidBow`, ammo-loss skip), `RangeEvent.java`, `CombatFormula.java`, `Client_Base/.../EntityHandler.java`.
- Nothing else references id 1594.

**Slice 5 — custom wielded sprite (15 recolored frames)**

Final renamed item: **Void Shortbow**. The icon already gives a strong purple silhouette in inventory, but the wielded sprite was the standard brown longbow (since `appearanceID 108` shares the longbow animation). The Void Scimitar's `charColour` recolor approach doesn't work for bows — confirmed empirically: setting an AnimationDef with `name="sword", charColour=0xff0000` rendered red (the sword sprite block has tintable palette regions), but `name="longbow", charColour=0x6a0dad` had no visible effect (the longbow sprite block has plain ARGB pixels with no tint mask). And the obvious fallback — packing a uniquely-named entry into `Custom_Sprites.osar` — does nothing in voidscape because `Config.S_WANT_CUSTOM_SPRITES=false` makes `GraphicsController.spriteSelect()` use `sprites[animation.getNumber() + offset]` from the legacy `Authentic_Sprites.orsc` ZIP, ignoring `animation.name` entirely.

Working approach: pack 15 recolored frames into the legacy ZIP at the runtime sprite-ID slot the new AnimationDef gets assigned by `mudclient.loadEntitiesAuthentic()`. The slot is auto-computed at startup (`animationNumber += 27` per unique name, with a 1998 → 3300 jump), so it must be discovered empirically — temporary `System.out.println` printed `voidbow.number=1674` and `longbow.number=729`. Extracted longbow's 15 frames at IDs 729..743 (per-frame shapes 12×55 to 25×65 for the 8 directions + walking poses), found just 3 unique colors across all opaque pixels (`#894b00` body, `#682d00` shadow, `#271e1b` string), pixel-mapped to `(126,58,204)` mid amethyst, `(74,20,140)` deep void, `(0,0,0)` black string, packed at IDs 1674..1688 with sidecars carrying the original xShift/yShift values. JSON `appearanceID 108 → 231` (one after Void Scimitar's 230). The full procedure is documented as `docs/recipes/add-custom-wielded-sprite.md` for future custom weapons that can't reuse a tintable existing block.

Files touched in slice 5:
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — 15 new entries at indices 1674..1688 (committed via `pack.py --commit`; `.bak` retained).
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — appended one AnimationDef line after the Void Scimitar's: `animations.add(new AnimationDef("voidbow", "equipment", 0, 0, false, false, 0));`. The `name="voidbow"` is informational-only under `S_WANT_CUSTOM_SPRITES=false` (renderer uses `.number`, not name) but matches the entry's intent and lets us read it as documentation.
- `server/conf/server/defs/ItemDefsCustom.json` — `appearanceID: 108 → 231`, `name: "Void Bow" → "Void Shortbow"`.

**Pitfalls / gotchas surfaced (also in the recipe)**:

1. **`charColour` only tints sprite blocks with reserved palette indices.** Sword/shield blocks have them; bow/some-armor blocks don't. The `//252-257 longbow` entries inside `S_WANT_CUSTOM_SPRITES` have non-zero `charColour` values that produce no visible tint at runtime — vestigial.
2. **`Custom_Sprites.osar` is unused in voidscape's render path.** Easy trap: parsing the file (which has `equipment/longbow` as a 4-color palette-indexed entry) suggests it's the source of the wielded look. It isn't — `Authentic_Sprites.orsc` is. The `.osar` is only consulted when `S_WANT_CUSTOM_SPRITES=true`.
3. **Sprite-ID slot is auto-assigned and slightly nontrivial.** Counting unique names by hand is error-prone (the `if (S_ALLOW_BEARDED_LADIES)` and other conditionals contribute non-obvious counts). Always discover via temporary `System.out.println` rather than precompute.
4. **`pkill -f "openrsc"` doesn't kill the server** because the running command line is `java -classpath ...` with no "openrsc" substring. Use `lsof -t -i :43596` to find the PID. This had a real cost: spent debugging time chasing why JSON `appearanceID` changes weren't taking effect — the server simply hadn't restarted from session start.
5. **Members-flag in `ItemDef`** is a bool at index 12 of the 15-arg constructor (after `pictureMask, blueMask`). Copy-pasting from a P2P item (e.g. Magic Shortbow) inherits `membersItem=true` even if the server JSON has `isMembersOnly: 0` — the client refuses to wield with a "members item" message. **Both sides** of the def must agree.

**Reversibility (slice 5)**:
- Restore `Authentic_Sprites.orsc` from `.bak` (this also reverts slice 3's icon work; reapply if keeping the icon).
- Remove the one-line AnimationDef append from `EntityHandler.java`.
- Set `appearanceID 231 → 108` in `ItemDefsCustom.json`.

No protocol bump. No DB migration. No new opcodes. No plugin recompile.

### 2026-04-26 — Void Amulet (id 1595) + Void Mace (id 1596)

Two more voidscape custom items in one session, building on the Void Shortbow plumbing. Both F2P, tradeable, admin-spawnable; both with custom mechanics that required new server hooks.

**Void Amulet (id 1595)** — Dragonstone Amulet stats (armour+3, aim+10, power+6, magic+3, prayer+3, neck slot 10), but F2P-flagged. Mechanic: kills against NPCs that drop stackable loot are multiplied 1.5× (rounded down) when the killer wears the amulet. Required hooks at *both* `Npc.java` drop paths — the obvious `dropStackItem(int, int, Player)` (line 595) AND the less-obvious **invariableItems loop** (line 430). Common NPCs (men, goblins) declare their coin/rune drop in the always-invariable list, which constructs a `GroundItem` directly with `item.getAmount()` and bypasses `dropStackItem()` entirely. We learned this by adding a `LOGGER.info` to `dropStackItem`, killing 5 men in a row, and observing zero log lines despite getting 3-4gp drops. Fix is to apply the same multiplier to `item.getAmount()` in the invariable loop, gated on the killer wearing the amulet AND `getItemDef(item.getCatalogId()).isStackable()` so non-stackable invariable drops (bones from a `dropItems` path that calls invariable, etc.) aren't affected. Stacks multiplicatively with Ring of Splendor's coin bonus (RoS adds first, void amulet multiplies the boosted total).

**Void Mace (id 1596)** — Rune Mace stats with `weaponPowerBonus` bumped 28 → 40, `requiredLevel: 60` (Attack) + `secondaryRequiredLevel: 60` (Strength) using the dual-skill schema added by the Void Scimitar slices. Mechanic: 2.5× damage **against NPCs only** (PvE-only multiplier; PvP unchanged). Hook is a single post-roll line in `CombatEvent.java:189` — after `damage = doMeleeDamage(...)`, multiply by 2.5 if `hitter.isPlayer() && target.isNpc() && hasEquipped(VOID_MACE)`. Mirrors the Strength Cape +20% pattern (also a post-roll multiplier). `osrs_combat_melee=false` in voidscape's `local.conf` so only the classic `CombatFormula` path needs the hook; if the OSRS flag flips on, also patch `OSRSCombatFormula.Melee.doMeleeDamage`.

**Sprite work** — both items got AI-generated inventory icons via gpt-image-1.5 (Void Amulet at archive slot 2759, Void Mace at 2760). The mace icon took multiple iterations: the first batch was 3D-rendered (smooth gradients, sparkles), the second was flat pixel-art but spike-less, the third had spikes but small head + long handle, the fourth landed on big spiky head + short stout handle. Lesson: prompt explicitly for "FLAT pixel art, NO 3D shading, NO sparkles, hand-placed pixels" AND "head occupies 60-70% of canvas, handle short" — both directives needed.

**Wielded sprites** — both attempted `charColour` tinting first (the cheap Void-Scimitar trick of reusing an existing tintable sprite block). For the amulet, `charColour=0x6a0dad` on a "necklace" AnimationDef *did* tint, but a tiny yellow gem region bled through at one rotation angle (the necklace sprite has a second-color region the engine doesn't tint via charColour). For the mace, `charColour` had no visible effect at all. Both fell back to the **per-frame recolor recipe** documented in `docs/recipes/add-custom-wielded-sprite.md`:
- Discover the runtime `animationNumber` slot via temp `System.out.println` in `mudclient.loadEntitiesAuthentic`.
- Extract existing frames from the legacy `Authentic_Sprites.orsc` archive (necklace=18 frames at 621..638, mace=18 frames at 783..800).
- Pixel-map source colors to void purples (necklace had 3 grey shades; mace had 7 greys for the head + 4 browns for the handle which we mapped to purples + near-blacks respectively).
- Pack at the new slot (voidneck=1701..1718, voidmace=1728..1745) with sidecars copied verbatim to preserve xShift/yShift.
- Reference via a unique-named AnimationDef ("voidneck", "voidmace") so `loadEntitiesAuthentic` allocates a fresh slot.

**JSON appearanceID mapping** — empirically `JSON N → animations.get(N-1)` in voidscape's setup. Each new AnimationDef appended after a previous one gets `JSON appearanceID = (previous's JSON ID) + 1`. After this session: void scimitar=230, void shortbow=231, void amulet=232, void mace=233.

**Gotchas surfaced**:
1. **Drop-quantity hooks on NPCs are split across at least three paths**: invariable drops (always-fired, bypass `dropStackItem`), bones (separate `dropItems` step), and rolled drops (the random-table path that *does* call `dropStackItem`). Boosting only one path looks correct in unit-style grep but fails most common NPC tests — coverage requires hitting all stackable construction points.
2. **`pkill -f "openrsc"` doesn't match the running server** (Java command line is `java -classpath ... ant runserver`). Use `lsof -t -i :43596` to find the PID. This bit us *again* during M3 testing — server wasn't restarting between Java edits, the user reported "no crazy hits" because the new code was in `core.jar` but the running JVM still ran the old class.
3. **`charColour` doesn't tint mace or longbow sprite blocks** in voidscape's `S_WANT_CUSTOM_SPRITES=false` setup. It does tint sword, shield, and necklace blocks (with a leak on the necklace's gem region). Per-frame palette recolor is the reliable fallback for any block whose pixels lack a reserved tint mask.
4. **Non-equipment-tab worlds** (voidscape: `want_equipment_tab=false`) keep wielded items in inventory with an `isWielded` flag rather than in a separate Equipment container. `Equipment.hasEquipped(int)` handles both modes correctly via the inventory-iteration branch — no caller-side check needed.

**Reversibility (both items)**:
- Restore `Authentic_Sprites.orsc` from `.bak` (this also reverts the Void Shortbow icon + wielded frames; reapply if keeping those).
- Revert one line each: `ItemDefsCustom.json` (id 1595, 1596 entries), `ItemId.java` (`VOID_AMULET`, `VOID_MACE`), `Npc.java:430-444` (drop boost block) + `Npc.java:601-607` (`dropStackItem` boost), `CombatEvent.java:189-194` (mace multiplier), `Client_Base/.../EntityHandler.java` (2 `ItemDef` lines + 2 `AnimationDef` lines: `voidneck`, plus the void mace icon line and the `voidmace` AnimationDef).

No protocol bump. No DB migration. No new opcodes. No plugin recompile.

### 2026-04-26 — Void Enclave (safe-zone hub in the Wilderness)

A small Ferox-Enclave-inspired safe-zone carve-out inside the Wilderness, south of the Graveyard of Shadows. Server bounds **X=210..228, Y=343..360** (Wild ~12-14, just past Edgeville's wilderness wall). Inside the enclave: PvP is disabled, the wilderness skull/level overlay is hidden, and three amenities — bank chest, prayer altar, healing pool (drink → full HP) — sit in two small walled buildings under a high-walled outer perimeter with one south-side gate. Gated on a new config flag `want_void_enclave` (default false; `true` in voidscape's `local.conf`).

**Safe-zone primitive.** Extended `WildernessLocation.WildState` (`server/.../entity/WildernessLocation.java`) with a fourth value `SAFE_ZONE`. `Point` (`server/.../model/Point.java`) gets one new rectangle in its static init, a new `isInSafeZone()` method (delegates to existing `getWildernessLocation()`), and a new branch in `returnLocationName()` that prints "Void Enclave" before the generic wilderness label. PvP gate added in **two** places that mirror each other: `Player.checkAttack()` (`Player.java:894-897`) and `Mob.checkAttack()` (`Mob.java:850-852`). Both early-return false if either party is in a safe zone, with a player-facing message in the Player path and silent in the Mob path (matching that file's existing comment-out style for wilderness-block messages). Block sits **before** the `USES_PK_MODE` branch so it applies in both PK-mode and non-PK-mode worlds.

**Why two `checkAttack` files.** `Player.checkAttack()` runs for player-initiated attacks; `Mob.checkAttack()` runs for the symmetric path used during NPC-initiated and certain combat-event flows. Skipping the Mob copy leaves a half-disabled safe zone — players can be attacked back from inside the rectangle. Discovery flagged this duplication; both must move together.

**Wilderness UI overlay (client-side).** The "skull + Wilderness + Level: N" overlay in `mudclient.java:5385-5409` is computed entirely client-side from the player's local coords — no server packet involved. Suppression therefore lives in the client: same rectangle hardcoded next to the existing `centerX > 0` check (`mudclient.java:5391-5400`), reconstructing server X from `worldOffsetX + playerLocalX + midRegionBaseX - 2304` and server Y from `playerLocalZ + worldOffsetZ + midRegionBaseZ - 1776`. When inside, `inWild = false` and the overlay is skipped. **No** `CLIENT_VERSION` bump — purely visual, no wire change.

**Buildings & amenities.** Layout is two small walled rooms under a high-walled outer perimeter, generated by a small Python script (committed via the JSON outputs):
- Outer ring: 19 horizontal Highwall segments along Y=343 and Y=361 (one south slot replaced with a Highwall Door at X=219 as the gate), plus 18 vertical Highwall segments at X=210 and X=229 — 74 boundaries total.
- Bank house (NW interior, 4×3): basic Wall perimeter with a Door at the south wall midpoint, single bank chest scenery (id 942) at (215, 347).
- Chapel (NE interior, 5×4): basic Wall perimeter with a south-wall Door, Altar (id 19, 2×1) at (223, 348), and fountain scenery (id 26, 2×2) at (225, 348).
Boundaries land in `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json` (106 entries — outer + both buildings); sceneries in `SceneryLocsVoidEnclave.json` (3 entries). `WorldPopulator.loadCustomLocs()` loads both unconditionally on the new flag — *not* gated under `LOCATION_DATA == 2` like the other custom loc files, because voidscape runs `location_data: 1`.

**Object-id corrections found during implementation.** Initial discovery cited altar id 27 and fountain id 34; actual ids are **19** (Altar) and **26** (fountain) per the parsed sequential `<GameObjectDef>` order in `GameObjectDef.xml`. Bank Chest id 942 was correct. The mistake was a discovery-agent hallucination — verified by counting `<GameObjectDef>` opening tags. The altar reuses the existing `Prayer.java` handler (matches on `command.equalsIgnoreCase("recharge at")`), and the bank chest reuses the existing `ShantayPassNpcs.java` handler (matches on id 942 + "Open"). No code change needed for either.

**Healing pool plugin.** New `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveHealingPool.java` implementing `OpLocTrigger`. Position-gated (id 26 *and* obj coords inside the enclave rect) so other fountains in the world keep their inert "WalkTo" behaviour. Modeled on `MagicalPool.java` — `blockOpLoc` returns true unconditionally for the enclave fountain (no command check, since fountain `command1=WalkTo` makes left-click the only meaningful interaction). Heals to full HP on every drink, no cooldown, idle "The water is refreshing" message at full HP.

**Why no "Drink" right-click option.** Fountain id 26's `GameObjectDef` has `command1=WalkTo, command2=Examine` — there is no built-in "Drink" command. Adding one would require either editing the shared fountain def (would affect Lumbridge fountain etc.) or appending a brand-new GameObjectDef + mirroring it in `Client_Base/.../EntityHandler.java` + `CLIENT_VERSION` bump (the Edgar-precedent dance). Deferred — left-click-to-heal is acceptable for v1, and matches the in-world feel of "step up to the pool to drink." Revisit if discoverability turns out to be a problem in playtest.

**Terrain not edited.** Approach A (scenery-only) was chosen up front per planning. The buildings sit on whatever ground the Wilderness already has at those coords (grass/rock). `Custom_Landscape.orsc` is unchanged; `WANT_CUSTOM_LANDSCAPE` stays false. If the courtyard floor look matters later, a Slice 6 polish would either drive the external `Open-RSC/2D-Landscape-Editor` (catalogued in `tools/rsc-toolbox/README.md`) or write a small custom byte-patcher around `Sector.pack/unpack()`.

**Files touched**:
- `server/src/.../entity/WildernessLocation.java` — `SAFE_ZONE` enum value.
- `server/src/.../model/Point.java` — registration in static block, `isInSafeZone()`, `returnLocationName()` branch.
- `server/src/.../entity/player/Player.java:894-897` — safe-zone PvP block.
- `server/src/.../entity/Mob.java:850-852` — safe-zone PvP block (mirror).
- `server/src/.../ServerConfiguration.java` — `WANT_VOID_ENCLAVE` field + `tryReadBool` line.
- `server/src/.../database/WorldPopulator.java` — two new conditional `loadGameObjLocs` calls under the flag.
- `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json` (new, 106 entries).
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json` (new, 3 entries).
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveHealingPool.java` (new).
- `server/local.conf` — `want_void_enclave: true`.
- `Client_Base/src/orsc/mudclient.java:5391` — overlay-suppression block + server-coord reconstruction.

**Reversibility**:
- Set `want_void_enclave: false` in `local.conf` → buildings + amenities vanish at next boot. Safe-zone code stays compiled but registration is still active in `Point.java`'s static block (would need to comment out one line for full revert).
- The `SAFE_ZONE` enum value, `isInSafeZone()` method, and the two `checkAttack` early-returns are inert when no rectangle of that state is registered.
- Deleting the two new JSON files is harmless when the flag is off.

No protocol bump. No DB migration. No new opcodes. Plugin recompile required (new `.java` file).

**Known follow-ups / playtest items**:
1. Boundary `direction` (0=horizontal, 1=vertical) was assumed without an authoritative reference — most vanilla wall data is baked into `.orsc` tile bytes, not the JSON loc files (`BoundaryLocs.json` only has 967 entries). If walls render facing the wrong way after restart, swap dir 0/1 across the JSON.
2. Fountain heal is unconditional on left-click. If players accidentally heal mid-PvP-chase by clicking the fountain while running through, that's a feature not a bug for a *safe zone*.
3. The enclave coords sit in standard Wilderness tile space; a player can still teleport *out* (Wild ≤ 20 allows teleports), which matches the Ferox "easy escape" feel. Teleports *in* obviously not blocked — there's no telepad mechanic in vanilla RSC.

### 2026-04-26 — Void Enclave aesthetic iteration: AI textures, landscape patcher, custom DoorDef/TileDef pipeline

Long iteration on top of the original slice-5 enclave to push the visual identity from "JSON-spawned scenery on grass" to a proper void-themed cult compound built into the landscape data itself. Major artifacts: a custom `.orsc` byte-patcher (`scripts/patch-void-enclave-landscape.py`), five AI-generated texture sprites, three custom `TileDef` colors, five custom `DoorDef` wall types, one custom `GameObjectDef` ("Healing pool" id 1296), two real engine bugs found and fixed.

**Why it took a major engine excursion**: vanilla RSC has *three* completely different rendering paths for "geometry inside a building" — landscape tile bytes (walls/floors/roofs in the .orsc), JSON-loaded boundary objects (clickable walls registered at runtime), and JSON-loaded scenery objects (3D models registered at runtime). They have different visibility flags (`DoorDef.unknown`), different collision-mask paths, different click semantics (only JSON-registered objects have GameObjects → only those are clickable for triggers like CutWeb), and different texture systems (TileDef = solid RGB color; TextureDef = sprite from `Authentic_Sprites.orsc`). The aesthetic depth we wanted required moving most geometry into the landscape bytes (for minimap visibility + "real wall" feel) while keeping clickable elements (the gate webs) as JSON boundaries. Took several wrong turns to learn which bucket each thing belongs in.

**The landscape patcher (`scripts/patch-void-enclave-landscape.py`)**. Idempotent Python script: reads the enclave's source sector (`h0x52y44`) from `Authentic_Landscape.orsc`, applies wall/floor patches to the right tile bytes, repacks into both the server's `Custom_Landscape.orsc` and the client's `Cache/video/Custom_Landscape.orsc`. Per-tile byte layout decoded from `server/src/com/openrsc/server/io/Tile.java` (10 bytes/tile: elevation, groundTexture, groundOverlay, roofTexture, horizontalWall, verticalWall, 4-byte diagonalWalls). Tile index within sector = `x * 48 + y` (column-major, per `Sector.getTile(x,y)` line 52). Wall byte value = `DoorDefId + 1` (zero means no wall). Coordinate math: our enclave (worldX 208..229, worldY 341..361) fits entirely in sector `h0x52y44` covering worldX 192..239, worldY 336..383. Sector-name math derived from `WorldLoader.loadWorld()` lines 524-535: for floor 0, `sectorX = (sx + 2304) / 48`, `sectorY = (sy + 1776) / 48`. The patcher carries the same enclave layout that previously lived in `BoundaryLocsVoidEnclave.json` (now empty except the 4 web boundaries — see below) so wall bytes and JSON boundaries can't drift apart.

**Direction byte gotcha** (`patch-void-enclave-landscape.py` direction mapping, fixed empirically by decoding Edgeville bank's `h0x52y46` sector). Vanilla data convention is **inverted from what the byte names suggest**: walls running east-west (the north/south *faces* of a building) live in the **`verticalWall`** byte, walls running north-south (east/west faces) in **`horizontalWall`**. So our JSON `direction 0` (east-west wall) → `verticalWall` (byte 5), `direction 1` → `horizontalWall` (byte 4). First pass had this swapped → the entire compound rendered as a chaotic zigzag of misaligned walls until we cross-checked Edgeville's wall pattern.

**Two real engine bugs found**:

1. **`WorldLoader.java:504-512` ignored `WANT_CUSTOM_LANDSCAPE` on F2P worlds.** The original logic was `if MEMBER_WORLD then (custom_landscape ? Custom : Authentic) else F2P`. Voidscape runs `member_world: false`, so the server always loaded `F2PLandscape.orsc` no matter what `custom_landscape` was set to — meaning our patched `Custom_Landscape.orsc` was sitting on disk and never read. The visual symptom: NPCs and players walked straight through the void perimeter walls because the server's traversal mask was computed from the unpatched F2P landscape. Fix: reorder the condition so `WANT_CUSTOM_LANDSCAPE` short-circuits to `Custom_Landscape.orsc` regardless of `MEMBER_WORLD`. The boot-log line `WorldLoader: - Loading landscape from .../<file>.orsc` is the diagnostic — anytime walls don't block, check that line first.

2. **`local.conf` had a duplicate `custom_landscape` key.** The voidscape preset block had our `custom_landscape: true` near `want_void_enclave`, but the original preservation-template tail still had `custom_landscape: false` at line 223. The YAML loader logged `Duplicate key: custom_landscape` (warning, not error) and the *last* value won → false → custom landscape disabled even after the WorldLoader fix. Removed the trailing duplicate. Lesson: when the YAML loader warns about duplicate keys, that's almost always silent override — don't ignore it.

**`TileDef` colour encoding gotcha** (`Scene.java:393-413`). `TileDef.colour` is **NOT** a raw RGB integer the way most tile/sprite systems work. Positive values are *indices into `resourceDatabase`* (the texture atlas); negative values are *RGB555 packed and bit-inverted*: `value = -((R<<10 | G<<5 | B) + 1)` for 5-bit R/G/B (0..31). Output is then expanded back to RGB888 by `(R<<19) + (G<<11) + (B<<3)` so it lands in the upper bits of each color byte. First "Void Floor" attempt used positive `2952528` thinking it was a raw RGB int → engine tried `resourceDatabase[2952528]` → `ArrayIndexOutOfBoundsException` on first render of an enclave tile → client crash, ghost session left on server. Correct encoding for our final neutral grey is `-10571` = R=10/G=10/B=10 → renders RGB888 (80, 80, 80). The bright magenta accent is `-20698` = R=25/G=6/B=20 → RGB888 (200, 48, 160). Three TileDefs total: `Void Floor` (id 25, grey), `Void Floor Accent` (id 26, bright magenta), `Void Floor Mid` (id 27, mid purple — currently unused after the ritual-circle pattern was reverted to plain grey).

**`DoorDef.unknown` rendering gotcha** (`World.java:829`). Walls in the landscape-byte path render only when `getUnknown() == 0` (or when `showInvisibleWalls` debug flag is true — voidscape doesn't set it). Vanilla "passable" boundaries — webs, doorframes, doors — have `unknown=1` because they were always *meant* to be placed via the JSON boundary loader, which goes through a different render path that doesn't check `unknown`. So when we tried to bake a custom Void Web straight into the landscape bytes, it was invisible regardless of how thick the texture looked. Two takeaways: (a) for custom landscape-baked walls, set `unknown=0` to render via the byte path, (b) clickable walls (which need GameObjects for `OpBoundTrigger`/`UseBoundTrigger`) must be JSON-registered, and JSON-registered walls *do* render even with `unknown=1`.

**Wall bytes vs JSON boundaries — the GameObject divide.** Walls in the .orsc tile bytes have **no GameObject** in the world's gameobjects map. Players can walk into them (collision works) but right-click does nothing (no event source). Walls in JSON `BoundaryLocs*.json` are loaded by `WorldPopulator` into `world.gameobjects` and become fully clickable. For the four perimeter spider webs we explicitly use the JSON path (4 entries in `BoundaryLocsVoidEnclave.json`) so the existing `CutWeb.java` plugin can hook the cut interaction. Landscape gate tiles are left empty (no wall byte) so the JSON web is the only obstacle. After a cut, `delloc(obj)` removes the GameObject and the player walks through; 30s later `addloc(...)` re-registers it.

**AI texture pipeline (`tools/voidscim-art/generate.py`)**. Already had the `gpt-image-2` integration for item icons. Two extensions for landscape texture work: (1) added `--bg-key #RRGGBB --chroma-tol N` flags that post-process opaque output into transparent — pre-resize, replace pixels within tolerance of the key color with `(0,0,0,0)`. This is the documented OpenAI workaround for `gpt-image-2`'s inability to natively output transparent backgrounds (per the community thread + `replicate.com/openai/gpt-image-2` docs); the trick is to demand a specific chroma-key bg color in the prompt (we use pure magenta `#FF00FF` since it's unlikely to appear in any artwork) then strip it client-side. (2) Verified via the OpenAI Python SDK source that `gpt-image-2` does NOT support `background=transparent` (only `opaque`/`auto`) and does NOT support the `input_fidelity` param (1.5-only). Our existing `generate.py` already gated `background=transparent` away from `gpt-image-2` so no API-rejection risk — only the chroma-key hack was missing.

**Five AI-generated textures**, all packed into `Client_Base/Cache/video/Authentic_Sprites.orsc` at indices `spriteTexture (3225) + TextureDef slot`. New `TextureDef` entries appended to client `EntityHandler.loadTextureDefinitions()` **before** the `if (Config.S_WANT_CUSTOM_SPRITES)` conditional block so they always load — voidscape has `S_WANT_CUSTOM_SPRITES=false` so anything in `loadCustomTextureDefinitions()` doesn't load and would shift our slot numbers if we'd appended after. (First attempt put voidbricks *after* the conditional → runtime slot was 60 in code but only 56 textures actually loaded → `ArrayIndexOutOfBoundsException: Index 60 out of bounds for length 56` → client crash. Lesson: count active runtime entries, not file-line count.)

| Texture | TextureDef id | Sprite idx | Used by |
|---|---|---|---|
| voidbricks | 55 | 3280 | DoorDef "Void Wall" (id 214) — sanctum interior walls |
| voidouter | 56 | 3281 | DoorDef "Void Highwall" (id 215) — outer perimeter, tall |
| voidsigilwall | 57 | 3282 | DoorDef "Void Sigil Wall" (id 216) — pentagram-mural accents |
| voidweb | 58 | 3283 | DoorDef "Void Web" (id 217) — **orphan**, unused, replaced by vanilla web |
| voidwindow | 59 | 3284 | DoorDef "Void Window" (id 218) — sanctum E/W walls |

Plus two orphan sprites at index 2761 (a "Void Sigil" item-icon attempt before realising the user wanted landscape assets, not items) and the two `Authentic_Sprites.orsc.bak*` snapshots already on disk for rollback.

**DoorDef structure surprise** (`server/src/.../external/DoorDef.java` vs `Client_Base/.../entityhandling/defs/DoorDef.java`). Server-side field name is `modelVar1` (matches the XML element); client-side field name is `wallObjectHeight`. The XStream-style XML deserializer maps element names to server fields directly, so the XML stays valid. The client constructor's 7th positional arg is `wallObjectHeight` — same value, different name. modelVar1=192 = standard interior wall height, 275 = Highwall, 70 = battlement. Knowing that the integer is a *height* (not a model-id) is what made it possible to set custom-textured walls at the right scale (e.g. `Void Highwall` uses 275 to match the perimeter Highwall it replaces).

**Landscape texture pipeline (`mudclient.java:14738-14806`)**. Texture sprites live at `spriteTexture + i` in the sprite archive. The engine quantizes to a 256-color palette per texture — `dictionary[0] = 0xFF00FF` (magenta) is the transparency entry, and any `RGB(0,0,0)` pixel in the source sprite gets rewritten to magenta before quantization, becoming transparent at render time. **Implication for our textures**: prompts must explicitly forbid pure black (use `RGB(15,5,25)` or darker indigo for shadows instead) or the engine will punch transparent holes through the wall. All five AI-generated textures pass a "0 pure-black pixels" check. The void web texture is the *exception*: it deliberately keeps `RGB(0,0,0)` between the strands so the gaps are see-through — but that texture is now orphan.

**Custom `GameObjectDef` "Healing pool" (id 1296)**. Reuses the existing `fountain` model but with `command1=Drink` so the right-click menu actually appears. Discovery showed that the standard fountain (id 26) has `command1=WalkTo` — and `mudclient.java:7789` explicitly skips menu-adding for `WalkTo` objects, meaning left-click never sends an action packet to the server, meaning no `OpLoc` trigger fires. That's why the original `VoidEnclaveHealingPool` plugin "did nothing" before the GameObjectDef append. Mirrored on client `EntityHandler.loadGameObjectDefinitions B()` per the Edgar precedent. Bumped `CLIENT_VERSION` 10012 → 10013 at this step.

**Bank chest F2P bypass** (`ShantayPassNpcs.java:458-465`). `BANK_CHEST` (id 942) is normally gated by `MEMBER_WORLD` — F2P worlds get "you must be on a members' world to do that" instead of opening the bank. We'd reused id 942 for the enclave bank chest, so on voidscape (F2P) it was rejecting players. Patched to bypass the member check when the chest's world coords are inside the enclave footprint. Same pattern works for any future F2P-bypass-on-coords needs.

**Outcome — final aesthetic state**. Outer perimeter is tall AI-textured void stone (`voidouter`) with 4 web-sealed gates (vanilla web id 24, JSON-registered, cuttable). Sanctum interior walls are AI-textured void brick (`voidbricks`) with a pentagram-mural sigil at the north midpoint and stained-glass void windows at the east + west midpoints. Bank shrine + healing-pool shrine use the same voidbricks with one sigil-mural accent each. Floor is plain neutral grey (`TileDef` id 25, RGB 80/80/80) — purple lives only in the wall textures + sigil murals + windows for contrast. Pre-existing wilderness scenery (trees, gravestones, mushrooms — 19 items per the boot log) stripped at boot via `WorldPopulator`'s `WANT_VOID_ENCLAVE` filter. Bank chest, prayer altar, healing pool sit free-standing in the courtyard.

**`CLIENT_VERSION` evolution this session**: 10012 (slice-5 baseline) → 10013 (custom Healing pool def) → 10014 (`S_WANT_CUSTOM_LANDSCAPE = true` for custom landscape archive) → 10015 (added Void Floor TileDef) → 10016 (Void Wall DoorDef + voidbricks TextureDef) → 10017 (added voidouter + voidsigilwall TextureDefs and DoorDefs + 2nd TileDef) → 10018 (added voidweb + voidwindow TextureDefs and DoorDefs + 3rd TileDef). Each bump matched in `local.conf`'s `client_version`. Server enforces version match via `enforce_custom_client_version: true` so stale clients are rejected at login.

**Files touched** (incremental on top of slice-5 baseline):
- `server/src/.../io/WorldLoader.java:504-512` — F2P landscape selection bug fix.
- `server/src/.../database/WorldPopulator.java:71-83` — Void Enclave scenery filter (with diagnostic log).
- `server/src/.../ServerConfiguration.java` — `WANT_VOID_ENCLAVE` flag (already present from slice 5).
- `server/conf/server/defs/DoorDef.xml` — five new void DoorDefs (Void Wall 214, Void Highwall 215, Void Sigil Wall 216, Void Web 217, Void Window 218). Void Web is currently orphan.
- `server/conf/server/defs/TileDef.xml` — three new TileDefs (25 grey, 26 bright magenta, 27 mid purple).
- `server/conf/server/defs/GameObjectDef.xml` — Healing pool (id 1296).
- `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json` — 4 vanilla-web (id 24) BoundaryLocs at the perimeter gates.
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json` — 19 amenity/atmosphere placements.
- `server/conf/server/data/Custom_Landscape.orsc` — patched sector h0x52y44 (binary, regenerable via `scripts/patch-void-enclave-landscape.py`).
- `server/local.conf` — `want_void_enclave: true`, `custom_landscape: true` (with the trailing duplicate removed), `client_version: 10018`.
- `server/plugins/.../authentic/npcs/alkharid/ShantayPassNpcs.java:458-465` — F2P bank-chest bypass on enclave coords.
- `server/plugins/.../authentic/misc/CutWeb.java` — `isWeb()` helper recognises both vanilla web id 24 and (orphan) Void Web id 217.
- `server/plugins/.../custom/misc/VoidEnclaveHealingPool.java` — new OpLocTrigger for fountain id 1296.
- `Client_Base/src/orsc/Config.java` — `S_WANT_CUSTOM_LANDSCAPE = true`, `CLIENT_VERSION = 10018`.
- `Client_Base/src/orsc/mudclient.java:5391-5398` — wilderness skull/level overlay suppression inside enclave bounds (slice 4, kept).
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — mirrors of: 3 TileDefs, 5 TextureDefs, 5 DoorDefs, 1 GameObjectDef, plus the orphan Void Sigil ItemDef removed before commit.
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — 6 sprite inserts (orphan 2761 + textures 3280-3284).
- `Client_Base/Cache/video/Custom_Landscape.orsc` — patched sector mirror (binary).
- `scripts/patch-void-enclave-landscape.py` — new, the byte-patcher.
- `tools/voidscim-art/generate.py` — `--bg-key` + `--chroma-tol` + chroma-key-to-alpha helpers.
- `tools/voidscim-art/prompts/void-{sigil,bricks,outer,sigil-wall,web,window}.txt` — six prompt files (sigil + web are orphan-but-archived for future use).

**Reversibility**: 
- Set `want_void_enclave: false` and `custom_landscape: false` in `local.conf` → enclave content + custom landscape both dormant; wall/floor/scenery byte additions in `Custom_Landscape.orsc` simply aren't loaded.
- The orphan DoorDefs (Void Web id 217), TextureDefs (voidweb id 58), and sprite (3283) don't render but also don't cost anything — leaving them in place avoids id-shift cascades through the wall byte values for everything else.
- `WorldLoader.java` change is a pure ordering tweak; preservation/authentic worlds with `custom_landscape: false` get exactly the same behaviour as before.
- The `ShantayPassNpcs.java` bypass is bounds-gated to the enclave coords only; no other bank chest in the world is affected.
- All AI-generated sprites have `Authentic_Sprites.orsc.bak` and `.bak.preSigil` snapshots on disk for rollback.

**Open follow-ups** (deferred):
- Custom Void Acolyte NPC standing in the sanctum (Edgar pipeline + AI-generated character sprite).
- Roofs over the buildings — last attempt with `roofTexture=1` looked broken when the user toggled "show roofs" on; needs an AI-textured roof variant + verifying the `roofTexture` byte's lookup path (TileDef? something else?).
- Cleanup pass to remove the orphan Void Web DoorDef, voidweb TextureDef, voidweb sprite, and Void Sigil ground-item sprite (low priority — they don't break anything).
- World-map PNG regeneration (`scripts/render-worldmap.sh`) so the in-game minimap world-view shows the enclave silhouette.


### 2026-04-26 — Item Memory: per-instance kill log on the Rune 2H sword

Each in-memory `ItemStatus` now carries an optional `kill_log` string and the `itemstatuses` table grew a matching nullable `TEXT` column (MySQL + SQLite, with patches `2026_04_26_item_kill_log.sql` for both engines). The format is a compact custom encoding — `npcId:count,npcId:count;pvpCount` (e.g. `11:412,5:38;1`) — to avoid pulling a JSON parser into the hot save path. A tiny `ItemKillLog` helper handles parse/serialize/increment.

When a player lands a kill while wielding a Rune 2H Sword (catalog id 81), a new plugin (`custom/items/RuneTwoHKillLog.java`) increments the per-instance counter on the wielded item via the `KillNpcTrigger` chain. PvP kills go through a parallel `PlayerKilledPlayerTrigger` (newly wired in `Player.killedBy:2266` — the trigger interface existed in the codebase but was never invoked anywhere) and are gated to wilderness only. **Quirk**: `PluginHandler` only fires `on*` when the corresponding `block*` returns true, but `true` also suppresses the Default action (loot/XP/etc.). Since we want passive recording without taking over the kill flow, both increments live as side effects inside the `block*` predicate, which always returns false. The plugin uses the inventory-scan path for wielded-weapon detection because voidscape runs with `want_equipment_tab: false`.

Examine had to change protocol: the PC client renders inventory examine entirely from its own cached `ItemDef.description` and never round-trips to the server. Added a new appended inbound opcode `ITEM_EXAMINE_REQUEST` (server enum + wire opcode 36 + `PayloadCustomParser` mapping/length-validator/parse-body sites + `ItemExamineRequestStruct` + `ItemExamineRequest` handler). The client now sends the inventory slot on examine instead of rendering locally; the server resolves slot → `Item` instance, augments the static description with `Has slain N NPCs and M players in combat.` for Rune 2H instances with a non-empty kill log, and replies via the existing `SEND_MESSAGE` channel. Equipment-tab examine path is untouched (not used because `want_equipment_tab: false`). Ground item examine is untouched (`GroundItem` has no `itemID`). `CLIENT_VERSION` bumped 10018 → 10019; `client_version` in `local.conf` matched.

**Scope decisions (option A2)**: per-instance state lives in `itemstatuses` keyed by `itemID` exactly like `durability`, but **drop/pickup and trade reset the kill log** — `GroundItem` carries no `itemID` and `PlayerTradeHandler:450` reconstructs the receiver's item with a fresh `itemID`, so the existing reset behaviour is left intact. Sold/dropped Rune 2H starts blank for the next owner; no special soulbinding. Tracking covers any wielded Rune 2H regardless of how it was acquired (`assignItemID`-style fallback paths included).

**Files touched**:
- `server/database/{mysql,sqlite}/{core,retro}.{sql,sqlite}` — added `kill_log TEXT NULL` column.
- `server/database/mysql/patches/2026_04_26_item_kill_log.sql`, `server/database/sqlite/patches/2026_04_26_item_kill_log.sql` — new ALTERs.
- `server/database/sqlite/queries/item.xml` — added `{killLog}` placeholder to `item.createItem`.
- `server/src/.../model/container/ItemStatus.java` — `killLog` field + accessors.
- `server/src/.../model/container/ItemKillLog.java` (new) — parse/serialize/increment helper.
- `server/src/.../database/struct/PlayerInventory.java` — `killLog` struct field.
- `server/src/.../database/GameDatabase.java:820` — populate `inventory[i].killLog` from real ItemStatus on save (unlike the pre-existing durability=100 stub bug, which was left alone — out of scope).
- `server/src/.../database/impl/mysql/MySqlGameDatabase.java` — 8 sites updated (3 load + 3 bulk-save + `queryItemCreate` + `queryItemUpdate`).
- `server/src/.../database/impl/mysql/MySqlQueries.java` — `save_ItemCreate` and `save_ItemUpdate` query strings.
- `server/src/.../database/impl/sqlite/SqliteGameDatabase.java` — 2 bulk-save sites (load inherits from MySql).
- `server/src/.../model/entity/player/Player.java:2266` — fire `PlayerKilledPlayerTrigger` (interface had been dead since vendor SHA).
- `server/src/.../net/rsc/enums/OpcodeIn.java` — appended `ITEM_EXAMINE_REQUEST`.
- `server/src/.../net/rsc/parsers/impl/PayloadCustomParser.java` — opcode 36 mapping, length-validator, body parser.
- `server/src/.../net/rsc/struct/incoming/ItemExamineRequestStruct.java` (new).
- `server/src/.../net/rsc/handlers/ItemExamineRequest.java` (new).
- `server/src/.../net/rsc/PayloadProcessorManager.java:137` — bind handler.
- `server/plugins/.../custom/items/RuneTwoHKillLog.java` (new) — `KillNpcTrigger` + `PlayerKilledPlayerTrigger`.
- `Client_Base/src/orsc/net/Opcodes.java` — appended `ITEM_EXAMINE_REQUEST(36)` in `Out`.
- `Client_Base/src/orsc/mudclient.java:8203` — examine menu now passes inventory slot, not catalog id.
- `Client_Base/src/orsc/mudclient.java:13083` — examine dispatch sends new opcode instead of rendering locally.
- `Client_Base/src/orsc/Config.java` — `CLIENT_VERSION = 10019`.
- `server/local.conf` — `client_version: 10019`.

**Reversibility**:
- Disable cleanly by deleting the plugin (`RuneTwoHKillLog.java`), reverting `mudclient.java:13083` to local rendering, dropping the opcode entries, and reverting `CLIENT_VERSION`. The DB column can stay — it's nullable and unread once the read sites are reverted.
- The `kill_log` column is additive; existing rows have NULL and load fine in older code paths if the column is dropped.

**Open follow-ups** (deferred — were sliced out of the plan as v1.5+):
- **PvP kill recording is wired but untested** — needs a second account in wilderness to verify `PlayerKilledPlayerTrigger` actually fires now that it's invoked from `Player.killedBy`.
- Slice 5 polish: pluralise NPC names ("412 goblins" instead of "2 NPCs"), top-5-with-overflow display, named PvP kills.
- Drop/trade preservation (option A1): would need extending `GroundItem` to carry `itemID` + `ItemStatus`, plus the trade flow at `PlayerTradeHandler:450`. Big lift, deliberately out.
- Other 2H weapons (Adamant, Mithril, Dragon, etc.) — currently scoped strictly to catalog id 81.
- Pre-existing bug noted: `MySqlGameDatabase.queryItemCreate:2018` only sets 5 of the 6+ placeholders in `save_ItemCreate` (never binds `itemId`). Extended to bind `kill_log` for consistency, but the underlying broken-binding pre-dates voidscape and is left for a separate fix.


### 2026-04-26 — Auction House (Void Auctioneer at Edgeville)

Enabled the upstream OpenRSC auction house and adapted it to voidscape's design: a fixed-price marketplace gated behind a single custom NPC.

**Context**: Upstream had ~70% of an auction house already built (`Market` service, `AuctionItem`/`ExpiredAuction` models, ~12 DB queries, `OpenMarketTask`, `BuyMarketItemTask`, `NewMarketItemTask`, `CancelMarketItemTask`, full PC client UI in `AuctionHouse.java`, NPC dialogue plugin, and a populated `NpcLocsAuction.json` with 19 spawns) — but it was gated behind `spawn_auction_npcs: false` plus `LOCATION_DATA == 2` in `WorldPopulator`, the schema lived in `database/{mysql,sqlite}/addons/` (not auto-applied), and several TODOs (expiry, fee, listing cap) were stubbed out.

**Voidscape design**:
- Fixed-price first-buyer-wins (partial buys allowed) — same as upstream
- **Single NPC** "Void Auctioneer" (custom id 837) at Edgeville bank south side (217, 460) — replaces upstream's 19 city spawns
- Custom appearance: white party hat (sprite 154) + scythe (sprite 228) + auctioneer signature gloves (sprite 46), deep-purple void palette (`topColour=0x4B0082`, `bottomColour=0x301934`)
- Ironman block + bank-PIN check preserved; upstream's level-100 gate dropped (single-NPC location is the gate)
- 6 listings per player, 7-day expiry, 5% on-sale tax (gp sink — destroyed at point of sale)
- PC client only (Android excluded by upstream `!Config.isAndroid()` check)
- No new opcodes — uses existing `INTERFACE_OPTION` + raw packet 132

**Files touched**:
- `server/local.conf` — `spawn_auction_npcs: true`
- `server/database/{mysql,sqlite}/patches/2026_04_26_add_auctionhouse.sql` (new) — idempotent `auctions` + `expired_auctions` schema, auto-applied via `JDBCPatchApplier`
- `server/src/com/openrsc/server/database/WorldPopulator.java:268` — lifted auction NPC spawn out of `LOCATION_DATA == 2` block (matches `WANT_VOID_ENCLAVE` pattern)
- `server/src/com/openrsc/server/constants/NpcId.java` — `VOID_AUCTIONEER(837)`
- `server/conf/server/defs/NpcDefsCustom.json` — new id 837 def with void palette + scythe + party hat sprites
- `server/conf/server/defs/locs/NpcLocsAuction.json` — replaced 19 upstream spawns with single (217, 460) Void Auctioneer
- `server/plugins/.../custom/npcs/VoidAuctioneer.java` (new) — Talk + right-click "Auction" handlers; opens `ActionSender.sendOpenAuctionHouse`
- `server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java:203` — fixed `playerAuctionCount` SQL: `seller='?'` literal → real `?` parameter (was a silent always-zero bug — listing cap depended on this)
- `server/src/com/openrsc/server/content/market/MarketItem.java` — `TIME_LIMIT` restored from `Integer.MAX_VALUE` to `60*60*24*7` (7 days)
- `server/src/com/openrsc/server/content/market/task/NewMarketItemTask.java` — added 6-listing cap check via `queryPlayerAuctionCount`
- `server/src/com/openrsc/server/content/market/task/BuyMarketItemTask.java` — restructured: DB transaction (seller credit + auction update) wrapped in `database.atomically(...)` and runs **before** inventory/coin mutation; 5% on-sale tax deducted from seller proceeds
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java:2367` — appended `Void Auctioneer` NPCDef so the client renders it (otherwise falls back to "Ana (not in a barrel)" sentinel)

**Reversibility**:
- Set `spawn_auction_npcs: false` to disable end-to-end (the spawn loader now checks just this flag).
- The schema patch is idempotent — leaving the tables in place is harmless.
- The upstream `Auctioneers.java` plugin is unmodified and remains dormant (NPC ids 794/795 have no spawn rows).

**Open follow-ups** (deferred):
- No buy-confirmation dialog (easy misclick)
- Expired-listing collect path is implemented in upstream (`PlayerCollectItemsTask`) but the in-client invocation point should be sanity-checked end-to-end
- Discord webhook (`want_discord_auction_updates`) — config key absent; integration left disabled
- Mod-delete UI surface exists server-side but no client trigger
- `BuyMarketItemTask` retains a TOCTOU window between auction fetch and DB transaction; under contention two near-simultaneous buyers could both clamp to the same `amount_left`. Acceptable for current scale.

### 2026-04-26 — Voidling: aggressive wilderness NPC at (63, 363)

New voidscape original NPC (id 838) spawned at wilderness level 11. Mirrors level 13 rat (id 47) combat stats so it slots into existing combat math without adding a new tier. Aggro is automatic in wilderness (`NpcBehavior.java` bypasses the level-2x+1 cap), so a level 1 player gets attacked the same as a level 99.

**Why id 838, not >=10000 like the recipe says**: the NPC loader at `EntityHandler.java:245-282` reads NPCs into a flat `ArrayList` and never reads the JSON `"id"` field — IDs are POSITIONAL. NpcDefs.json ends at 793; NpcDefsCustom.json appends from 794. Voidling is the next entry after Void Auctioneer (837), so it gets 838 by file order. The `"id"` field is documentary, not load-bearing. The `>=10000` advice in `docs/recipes/add-npc.md:16` is wrong for this loader and should be corrected separately.

**Sprite — engine-render gotchas (this slice was painful, save next-time):**
1. **`NPCDef.sprites[0]` holds the AnimationDef LIST INDEX, not the archive sprite index.** Pack-at-3696 will silently render whatever-is-there because animation #3696 doesn't exist. Voidling registers a new `AnimationDef("voidling","npc",...)` at the end of `loadAnimationDefinitions()` (runtime list idx 233), and the engine auto-assigns it 18 contiguous archive slots at boot.
2. **Engine `loadEntitiesAuthentic()` numbers each unique-named animation sequentially (+27 per name), with a 1998→3300 jump.** Discover the assigned slot empirically with a temp `System.out.println` in `mudclient.java:14428` — voidling lands at archive **slots 1755–1772** (15 walk + 3 attack frames). The 1755 slots overwrite orphaned sprites that no other animation references.
3. **`camera1` × `camera2` is the WORLD-SPACE bounding box** the sprite gets stretched into — not pixel size. Rat is `346×136` (2.5:1 squat 4-leg). Voidlings are humanoid; using rat camera made them appear **flat** like creatures lying horizontally. Standard humanoid box `145×220` (1:1.52) fixed it.
4. **`something1`/`something2` are the LOGICAL REFERENCE FRAME** (102×144 here, copied from zombie ID 41). Each of the 18 sprites has its own width/height/xShift/yShift positioning the silhouette inside that frame. Different frames have different widths (narrow front/back, wide side profiles) — same convention as the authentic zombie sprite block.
5. **Slot direction mapping** (per `mudclient.drawNPC` math): 0–2 south, 3–5 SE, 6–8 east, 9–11 NE (engine mirrors for NW), 12–14 north, 15–17 combat. `var14 = animFrameToSprite_Walk[stepFrame/walkModel%4] + var13*3` for walk, combat uses `+ var13*3` with `var13=5` and `animFrameToSprite_CombatA` for the 3-frame attack cycle.
6. **`combatSprite=30`** lands the voidling at the hit-bubble's screen position. Rat's `combatSprite=45` was too far back (gap from player); humanoid `5` was too forward (overlap). The bubble offset is hardcoded `±30` in `mudclient.java:5139,5143` — engine-global, can't override per-NPC without engine surgery.

**Sprite art pipeline** — frames came from a user-supplied 1536×1024 sprite sheet (charcoal grey BG, ~7×9 grid of poses). `tools/voidscim-art/` was extended to: chroma-key the grey BG, bbox-crop, scale-to-fit a 102×144 reference frame, write per-frame sidecars with computed `xShift`/`yShift` (bottom-anchored). Slots 12–14 (true back view) needed frames the sheet didn't have — generated 4 GPT variants with `gpt-image-2` using `r6_c1` as a reference, picked 3 (raw_00/02/03 from `voidling-back-attempts/`). Slots 9–11 (NE) reuse SE frames horizontally flipped for visual asymmetry.

**Drops**: bones (engine auto-drop, NOT in the table — duplicating it would double-drop) plus one rolled item from a 12-entry weighted table (total weight 63, padded to 128 with `addEmptyDrop` so ~50% of kills give bones only). Mirrors Mugger (NpcId 21) structure. Items: 5 air, 4 fire, 25-80 coins (3-bucket roll), 2 chaos, iron bar, bronze med helm, steel axe, iron 2H sword, 2 nature, 4 law — weights 14/10/8/8/4/4/4/4/2/2/2/1.

**Spawn**: 4 voidlings in a 6x6 patrol box (60-65, 360-365), starts at (61,361), (64,361), (61,364), (64,364). Wilderness level = `1 + (427-y)/6` → level 11 at y=363.

**Final NPCDef values**: `attack=16, str=15, hits=10, def=12, combatlvl=13, aggressive=1, attackable=1, respawnTime=74, sprites[0]=233, camera1=145, camera2=220, walkModel=7, combatModel=7, combatSprite=30, hairColour=topColour=bottomColour=skinColour=0`.

**Files touched**:
- `server/conf/server/defs/NpcDefsCustom.json` — id 838 def
- `server/conf/server/defs/locs/NpcLocs.json` — 4 spawn entries
- `server/src/com/openrsc/server/constants/NpcId.java` — `VOIDLING(838)`
- `server/src/com/openrsc/server/constants/NpcDrops.java` — weighted drop table
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java:2374,4931` — NPCDef registration + new "voidling" AnimationDef
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — 18 frames packed at slots 1755–1772 (pre-state at `.bak.preVoidling`)
- `tools/voidscim-art/prompts/voidling.txt`, `voidling-back.txt`, `voidling-ne-back.txt` (new) — generation prompts

**Reversibility**:
- `git checkout` reverts source/JSON edits.
- Sprite archive rollback: `cp Authentic_Sprites.orsc.bak.preVoidling Authentic_Sprites.orsc`.
- No opcode/packet changes — CLAUDE.md rule 3 not triggered.

### 2026-04-27 — Edgeville-area amenities: bank chest + prayer altar

Two one-off scenery placements appended to `SceneryLocsVoidEnclave.json` (the umbrella custom-loc file gated by `WANT_VOID_ENCLAVE`):

- **Bank chest** (id 942) at `(210, 439)` — north-west corner of Edgeville. Reuses `ShantayPassNpcs.onOpLoc`'s generic id-942 handler (`showBank`). On Voidscape's F2P world the handler short-circuits with `"you must be on a members' world"` for any chest outside the existing Void-Enclave bounding-box bypass; widened the F2P bypass to a per-coord allow-list (renamed flag `isVoidEnclaveBankChest` → `isVoidscapeF2pChest`) and added `(210, 439)` to it.
- **Prayer altar** (id 19) at `(217, 442)` — same row as the chest. Uses the standard `Prayer.onOpLoc` "Recharge at" handler — no plugin change needed. Direction `4` (south-facing) places it horizontally; `0` rendered vertically and bisected the building footprint.

Both placements are inside the file's `WANT_VOID_ENCLAVE` gate even though they're far from the enclave compound (y=341–361). The file is misnamed as "voidscape custom scenery one-offs"; rename later if more are added.

### 2026-04-27 — Crown sprite collision fix (Void Enclave textures vs. crown slots)

Admin chat crowns were rendering as a void-window stained-glass texture. Root cause: `Authentic_Sprites.orsc` is one zip archive shared by both the texture loader and the media-sprite loader. Texture id N is loaded at archive slot `spriteTexture (3225) + N` linearly. The Void Enclave commit (`df758e7`) added 5 new TextureDefs (ids 55–59), pushing the texture range from `3225–3279` (vanilla, 55 textures) to `3225–3284` (post-voidscape, 60 textures) — but slot 3284 was already the **grey mod crown** sprite (`crowns:0`), and slot 3285 the **gold mod crown**. The texture builder overwrote both archive slots with 65561-byte texture blobs (voidwindow at 3284, a stale aborted 6th-texture blob at 3285), so the crown lookup pulled void-texture data and the chat-line render code (`GraphicsController.drawColoredString → spriteSelect(crowns.get(iconSprite - 3284))`) painted that as a 13×13 crown.

**Fix** — relocate the two collided crown SpriteDefs out of the texture range without disturbing the texture loader's linear formula:

1. Inserted the original 597-byte grey + gold crown PNGs (extracted from `Authentic_Sprites.orsc.bak`, the truly-vanilla archive — note `.bak.preVoidling` is *already corrupted* because the Voidling commit took its backup *after* the void-enclave texture pack landed) into the current archive at fresh slots `3296` and `3297` (gap range `3296–3299` was empty in the archive; chose the lowest two).
2. `EntityHandler.loadCrowns`: `crowns.get(0).slot = 3296`, `crowns.get(1).slot = 3297`. The other three crowns (dark grey 3286, star 3287, key 3288) stay at original slots — they were never overwritten.
3. `mudclient.loadSprites`: replaced `loadSprite(3284, "media", 11)` with `loadSprite(3286, "media", 9) + loadSprite(3296, "media", 2)`. Skipping 3284/3285 in the media loader is *required* — `loadTexturesAuthentic()` runs first and processes the texture into `sprites[3284]` (palette extraction, magenta-transparency remap); the old media call would then re-load it as a raw RGB sprite over the texture, breaking 3D rendering of the void window. With the skip, `sprites[3284]` retains the texture-loader-processed voidwindow, and `sprites[3296]/[3297]` hold the crown PNGs read by `spriteSelect`.
4. `GraphicsController.drawColoredString` line 1493 (`crowns.get(iconSprite - 3284)`) is **unchanged** — that math is index-into-list, gated on `iconSpriteIndex` which stays at 3284. The slot move only touches the SpriteDef field that `spriteSelect` reads.

**Why not just move the new void textures off slots 3284/3285?** The texture loader is rigidly linear (`for i in 0..textureCount(): loadSprite(spriteTexture + i, ...)`). Relocating one texture's archive slot would require either inserting filler TextureDef entries to bump it, or special-casing the loader. Moving two crown SpriteDefs is one-line each and leaves the formula untouched.

**Files touched**:
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — appended two zip entries `3296`, `3297` (each 597 bytes). Pre-state saved at `.bak.preCrownFix`.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java:531-540` — slot args `3284→3296`, `3285→3297` in `loadCrowns()`.
- `Client_Base/src/orsc/mudclient.java:14477` — split the media-load into two calls that skip slots 3284/3285.

**Reversibility**: `cp Authentic_Sprites.orsc.bak.preCrownFix Authentic_Sprites.orsc` + `git checkout` reverts everything. No server-side change, no opcode change.

**Lesson for future texture additions**: vanilla had 55 textures and ~5 free archive slots before the crown range starts at 3284. Voidscape used all 5 and bled into the crowns. Any **6th** voidscape texture would need this same relocation pattern — either move it past slot 3294 (after the 11-sprite media block at 3284–3294) or relocate the colliding crown. Add a guard comment near `loadTextureDefinitions` if texture count creeps further.

### 2026-04-27 — Custom bank UI enabled (search, tabs, organize, wealth)

Flipped `want_custom_banks: true` in `server/local.conf`. This activates `CustomBankInterface` (search box, multi-tab paging, drag-to-reorder, wealth display) and is the OpenRSC kitchen-sink bank rewrite. Equipment tab stays off (`want_equipment_tab: false`) — the equipment-tab path is broken and presets cover the loadout use case anyway.

**The fix that nobody upstream landed.** OpenRSC's custom bank has been "broken" for years: any client that enables it freezes on bank-open. Root cause turned out to be a missing-sprite crash, not a logic bug:
- `EntityHandler.loadGUIParts()` (`EntityHandler.java:491-506`) registers `GUIparts` slots 38–53 (equipment slot icons, bank toggle icons, **`BANK_PRESET_OPTIONS`** gear icon, kept-on-death icon) with `authenticSpriteID = -1`. They live only in OpenRSC's optional custom-sprite archive (path `"GUI:42"` etc.) which voidscape doesn't ship.
- `GraphicsController.spriteSelect(SpriteDef)` (`GraphicsController.java:348-355`) takes the authentic-sprite path when `S_WANT_CUSTOM_SPRITES = false` (our case): `return sprites[sprite.getAuthenticSpriteID()]` — i.e. `sprites[-1]` → `ArrayIndexOutOfBoundsException`.
- `CustomBankInterface.onRender():140` unconditionally drew `BANK_PRESET_OPTIONS` whenever `S_WANT_BANK_PRESETS` was true (which voidscape has been running since 2026-04-24). The exception was caught by `mudclient.drawUi`'s try/catch, rethrown as `RSRuntimeError`, and re-fired every frame — observable as "client freeze immediately on bank open" in the upstream issue tracker.
- The other slot 38–53 sprites only render when `want_equipment_tab` or `want_items_on_death_menu` are on, so the freeze appears specifically when **bank presets + custom banks** are both on without custom sprites — explaining the long-standing "presets + custom bank = freeze" folklore.

**Fix — systemic, in `GraphicsController.spriteSelect(SpriteDef)`** (`Client_Base/src/orsc/graphics/two/GraphicsController.java:348`): when the authentic-sprite path is taken (`!S_WANT_CUSTOM_SPRITES`) and the looked-up id is out of bounds, return `Sprite.getUnknownSprite(28, 28)` instead of throwing. This prevents the entire family of "missing custom sprite freezes the render thread" failures, not just `BANK_PRESET_OPTIONS`. Other slot 38–53 sprites (equipment-tab icons, items-on-death icon) now render as visible placeholders if those features are enabled later — strictly better than a silent freeze.

A targeted guard at `CustomBankInterface.java:140` was the first attempted fix and is now obsolete (the preset-edit-mode entry button it tried to render was removed entirely as part of the UX overhaul below — see next entry).

**Why not load the missing sprites?** They live in an archive we'd have to author or import. The placeholder is a one-line systemic fix that covers every site.

**Files touched**:
- `server/local.conf:279` — `want_custom_banks: true`.
- `Client_Base/src/orsc/graphics/two/GraphicsController.java:348-358` — defensive sprite lookup with placeholder fallback.

**Reversibility**: flip `want_custom_banks` back to false. The placeholder branch is dead in that path. No data migration, no opcode change.

**Lesson**: when an upstream feature is "known broken on bank open," check sprite-array indexing before assuming logic bugs. The OpenRSC custom UI silently depends on a custom-sprite archive that's not part of the default client distribution.

### 2026-04-27 — Custom bank UX overhaul (RSCR2-style loadouts, save modal, transparency)

Round-two work on the now-functional custom bank, aimed at matching RSCRevolution2's polished loadout UX. The vanilla OpenRSC custom-bank UI exists but its preset-edit modal is unusable without the missing custom-sprite archive (the equipment-slot icons it shows are slot 38–48, all `authenticSpriteID = -1`); the preset save flow was server-gated on `WANT_EQUIPMENT_TAB` and silently rejected every save/load with a "suspicious player" flag. Players could load presets with `ctrl+1/2` but had no way to save one.

**Server-side fixes** (`server/src/com/openrsc/server/`):
- `model/container/BankPreset.java:18` — `PRESET_COUNT` 2 → 3 (matches RSCR2; client + server in lockstep).
- `model/container/BankPreset.java:207` — `attemptPresetLoadout()` now ends with `ActionSender.showBank(player)` instead of `player.resetBank()`. The old behavior closed the bank dialog on every loadout load (via `hideBank` packet); the new behavior re-sends the bank contents so the UI reflects the new state and the player stays in the bank.
- `net/rsc/handlers/BankHandler.java:113-121, 137-145` — removed the `WANT_EQUIPMENT_TAB` early-return on `BANK_LOAD_PRESET` and `BANK_SAVE_PRESET`. The upstream gate also called `setSuspiciousPlayer(true, ...)` on every attempt, which is why the existing `S_WANT_BANK_PRESETS=true` voidscape config had been quietly setting that flag for everyone who tried to use the feature. Bank presets are independent of the equipment-tab UI; the gate was wrong. Added confirmation chat messages (`Saved current loadout to slot N` / `Loaded loadout N`) on each path so the player gets feedback.

**Client-side overhaul** (`Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java`):
- `presetCount` 2 → 3; added `ctrl+3` hotkey for loadout 3.
- Removed the gear/P preset-edit-entry button entirely. The original UI (`renderPresetEdit`) is dead code for voidscape — its equipment-slot grid relies on the missing custom-sprite archive and would render as 11 placeholder squares. Replaced with a single click-to-save modal (next bullet).
- New `renderSaveConfirm(slot)` modal (318×268, centered): shown when player clicks an empty loadout slot or right-clicks a filled one. Renders a 6×5 inventory grid (authentic RSC orientation) with current inv contents, a hint line, and Save/Cancel buttons. Save sends opcode 27 (now reaches the un-gated server handler) and clears the modal; Cancel discards. Pattern: a single `pendingSavePresetSlot` field flips `onRender` into the modal branch.
- Loadout buttons widened (17 → 22 px) and labeled with a "Loadouts:" tag to the left. Cyan number indicates a filled slot, white indicates empty (driven by `isPresetEmpty()` checking that all `presets[slot].inventory[i].getItemDef()` are null). Click empty → confirm modal; left-click filled → load; right-click filled → confirm-overwrite modal.
- Removed the "Total wealth: X gp" header line — was overlapping the loadout label and consuming title-bar real estate; not worth the space.
- Bank panel body alpha lowered 160 → 100 so the world is visible behind the bank (matches RSCR2 transparency).
- "Deposit All" → "Deposit Inv." (label parity with RSCR2's "Deposit Inventory"; shortened to fit the existing 75 px button).
- Cert visual: flipped `want_cert_as_notes: true` (`server/local.conf`) and `S_WANT_CERT_AS_NOTES = true` (`Client_Base/src/orsc/Config.java`). Withdrawn-as-note items now render as a paper backdrop with the item's icon overlaid (OSRS-style), instead of a generic certificate sprite. Wiring already existed at `mudclient.java:3331+` and `CustomBankInterface.java:349+` — the static-default flip just makes it the default for fresh client launches without requiring a server-config round trip.

**State the player sees on next interaction:**
- Empty loadout slot (`1`/`2`/`3` in white) → click → confirm modal → Save → cyan number → can re-click to load. Save flagged in chat.
- Filled slot (cyan) → left-click loads, bank stays open with new bank state. Right-click re-opens confirm modal to overwrite.

**Why three loadouts and not more?** RSCR2 has three; the SQLite `bankpresets` table has no slot-count constraint (rows are stored by `playerID + slot`), so bumping further is just a constant change. Three is plenty for typical use.

**Reversibility**: per-toggle. `want_cert_as_notes: false` reverts the visual. Reverting `PRESET_COUNT` to 2 leaves slot-2 rows orphaned in DB but no crash (the load handler clamps via `presetSlot >= PRESET_COUNT` check). The save-confirm modal and click-empty-to-save flow can be reverted by removing `pendingSavePresetSlot` field and `renderSaveConfirm` method; the surrounding code falls back to direct `saveSetup`/`loadPreset` calls.

**Lesson — server-side feature gates**: the `WANT_EQUIPMENT_TAB` precondition on bank-preset save/load was a copy-paste error in the upstream code (presets are entirely independent of the equipment-tab UI). The compounding `setSuspiciousPlayer` made the failure mode silent — there's no error in logs, just rejected packets. When a feature is enabled in config but doesn't work, grep its handler for unrelated config gates that may be quietly returning early.

### 2026-04-27 — Cursed Greatsword (custom item) + `voidscim-art` AI sprite pipeline

First end-to-end custom-item add: a recolored rune 2H sword (purple/dark palette, named "Cursed Greatsword", id `1597`, lvl-40 Attack, +70/+70 aim/power inherited from rune 2H). Inventory icon AI-generated; wielded animation set is a per-pixel recolor of the rune 2H frames.

**Tooling — `tools/voidscim-art/voidscim/`** (new Python package, `python -m voidscim <verb>`):

- `new-icon` — generate a brand-new inventory icon via gpt-image-1.5 multi-image edits, using a curated 3×3 reference montage (8 authentic items + DRAW HERE cell) on magenta (`#FF00FF`) BG. `_chroma_key_magenta()` is a 3-stage numpy despill (KILL hard magenta, TAPER edge fade, DESPILL channel correction) — needed because `background=transparent` still leaves halos on saturated magenta.
- `variations` — riff on an existing item (2×2 grid).
- `fit` — auto-fit a chroma-keyed PNG into the RSC 48×32 inventory canvas. NEAREST default; LANCZOS opt-in.
- `register --like <id>` — wire a fitted sprite into the game in three coordinated edits: (a) allocate next free archive slot in `Authentic_Sprites.orsc` and pack the sprite, (b) inject an `items.add(new ItemDef(...))` line into `EntityHandler.loadItemDefinitions()` with all 14 args (incl. wieldability fields inherited from `--like` source), (c) append an entry to `server/conf/server/defs/ItemDefsCustom.json` so the server side has the same item. `--commit` toggles dry-run vs apply. `restore <id>` undoes all three.
- `recolor-wielded` — extract a source item's wielded-sprite frames from the archive and apply a `src_hex:dst_hex,...` color map per pixel. Produces drop-in PNGs + sidecar JSONs preserving `xShift`/`yShift`.
- `pack-wielded` — pack recolored frames into `Authentic_Sprites.orsc` at a freshly allocated contiguous block, then **simulate the engine's runtime AnimationDef numbering** (the `+=27 per unique-named entry, hard jump 1998→3300` loop in `mudclient.loadEntitiesAuthentic()`) to compute the `appearanceID` we need to stamp onto the item's `ItemDef`. Appends a new `AnimationDef` line to `loadAnimationDefinitions()`.

**Critical landmines logged for future:**
- `ItemDefsCustom.json` uses key `"items"` (plural) — vanilla `ItemDefs.json` uses `"item"`. The server loads `JSONObject.getNames(object)[0]`, so it works either way *until you have both files* and load order silently picks one. The register-cmd reads the existing top-level key dynamically rather than hardcoding.
- Archive slot `2761` looked free per discovery but was occupied by an orphan sigil sprite. The allocator now skips occupied slots and reports them rather than overwriting.
- `loadAnimationDefinitions` is `private static void` in the snapshot, not `private void` — initial regex was too strict.
- `AnimationDef`'s `charColour` field, when copied from the rune 2H sword's `"sword"` animation, is `65535` (white-tint). For pre-recolored frames you must override `args[2] = "0"` (matching voidneck/voidmace pattern), otherwise the engine re-tints your purple sword to white in-game.

**Failed experiment — `regenerate-wielded` (scrapped, code removed):** to get a *shape*-different (not just color-different) wielded sprite set, tried two approaches with gpt-image-1.5 multi-image edits:

1. **Grid mode**: tile 18 source frames into a 6×3 1024² grid + send target icon as second reference, ask the model to "redraw each cell at the same angle but with the new weapon's shape." Result: model flattened all 18 cells to a single canonical diagonal angle.
2. **Per-frame mode**: one API call per source frame (~$1, 18 calls), source frame upscaled to 1024² as image 1 (POSE TEMPLATE), target icon as image 2 (STYLE TEMPLATE), with explicit "preserve angle from image 1" prompting. Result: same failure — model treats image 1 as loose inspiration and image 2 as the dominant signal, regenerating the icon shape at its preferred canonical view regardless of pose. Verified with $0.12 of API spend on 2 frames before scrapping the rest.

Conclusion: gpt-image-1.5 cannot do shape-different + per-frame angle-preserved wielded sprites. The only paths to shape-different wielded sets are (a) procedural rotation of the icon by per-frame principal-axis angle (chunky pixel-rotation artifacts on small sprites), (b) hand-illustration. We're sticking with `recolor-wielded` for now: same shape as the source weapon, custom palette. The `regenerate_wielded_cmd.py` and `call_edits_multi_image` helper have been removed; the discovery is preserved here so we don't re-burn API spend rediscovering it.

**Files touched** (in-game state):
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — packed Cursed Greatsword inventory icon at slot `2762` (sprite_id 612) + 18 wielded frames at slots `1782`–`1799`.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — registered `Cursed Greatsword` ItemDef (id 1597, sprite 612, wearable 8216) and appended `cursed_greatsword` AnimationDef.
- `server/conf/server/defs/ItemDefsCustom.json` — registered Cursed Greatsword with `appearanceID: 235` (computed from runtime list-position simulation).

**Reversibility**: `python -m voidscim restore 1597 --commit` reverts the 3 registration edits. Archive bytes at slots `2762` + `1782..1799` remain (would orphan); `restore` could be extended to truncate/zero them but that's not implemented.

**Lesson — AI image gen for game sprites**: gpt-image-1.5 is good at *generating new authentic-style icons* (single canonical view, no constraints) and bad at *steering* — preserving an exact pose, an exact rotation, or matching across many calls. For pixel-art that needs per-frame consistency (rotation, lighting, animation), AI-edit is the wrong tool; recolor + procedural transforms beat it.
