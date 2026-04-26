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
