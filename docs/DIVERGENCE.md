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

### 2026-06-20 - Instant input feedback and FPS overlay

Added client-only feedback for accepted local walk/action path sends: when enabled in Advanced Settings > Interface, the client shows a short-lived projected ground marker on the resolved path destination while waiting for the server-authoritative result. The marker is off by default, persists in local `clientSettings.conf`, and clears on arrival, timeout, region rebase, or disabling the setting; inventory, combat, dialogue, trading, pathfinding, movement speed, packets/opcodes, server ticks, and game state remain server-confirmed. Also made a small in-game FPS readout always visible for responsiveness testing, positioned below the location plaque. Files: `mudclient.java`. Reversibility is removing the pending marker helpers/settings action and FPS overlay call.

### 2026-06-20 - Remote entity movement interpolation

Changed desktop/client presentation for non-local player and NPC movement from a fixed `C_MOVE_PER_FRAME` pixel step to an elapsed-time pixel budget with fractional carry, so remote entities keep the same server-authoritative waypoint targets while gliding at a steadier visual speed across variable client frame pacing. The local player remains on the existing authoritative movement path, local speculative walk prediction stays opt-in, and no server tick rate, packet/opcode shape, combat timing, pathfinding, schema, or cache asset changed. Files: `mudclient.java`. Reversibility is restoring the remote player/NPC loops to use `Config.C_MOVE_PER_FRAME` directly.

### 2026-06-20 - Forced launcher refresh for stale beta clients

Tightened hosted beta client-version enforcement after temporary beta-admin accounts could bypass `enforce_custom_client_version` and keep logging in with stale custom clients. The server now uses one shared `requiresClientUpdate` check for login, registration, and live player ticks, so any connected stale client receives a clear launcher-relaunch message, a short system-update timer when supported, and then a forced disconnect. The desktop client now treats login response `CLIENT_UPDATED` as a hard update stop: it shows the update notice briefly and closes so relaunching through the Voidscape launcher redownloads the current manifest payload. `CLIENT_VERSION` and voidbot moved to `10115` to force this binary behavior into the hosted update path. No new packets, opcodes, schema, cache archive, or gameplay rules changed; reversibility is restoring admin bypass semantics, dropping the live tick kick, and returning the version to `10114`.

### 2026-06-19 - Undead Siege Crumble and PvM projectile kiting tuning

Raised Undead Siege's normalized temporary Magic level independently from the shared combat stat level so Crumble Undead is reliable while preserving equalized run stats. PvM projectile targeting now has a one-tick edge grace for ranged and cast-on-mob magic: when an NPC is exactly one projectile range unit away and its next queued step would enter clear line-of-sight range, the player holds position instead of immediately walking forward. The Siege supply shop also consumes a hidden Voidscape server-message token to draw the player's current run points in the existing point-shop UI and refresh that value after buys. The grace is NPC-only, projectile-radius-only, and consumed once per targeting/firing attempt; PvP, melee catching, packet/opcode shape, client assets, schema, and normal item/economy behavior are unchanged. Reversibility is restoring the single Siege combat level, removing the `PathValidation.shouldHoldProjectileKiteLine` call sites, and dropping the hidden shop-balance UI token.

### 2026-06-19 - Undead Siege island manor arena

Moved Undead Siege off the public Wilderness Ruins footprint and onto a dedicated ocean-island sector at `h0x60y39`. The landscape patcher now bakes a broad island with a large ruined manor, roof bytes, wide broken wall breaches for undead pathing, porch/stoop floor tiles, shoreline barricades to prevent accidental exits, and a sparse abandoned-prop pass loaded from `SceneryLocsUndeadSiege.json`; server arena/chase/spawn/approach bounds and the client auto-reskin bounds now point to that island. The Siege reskin keeps timber, mossy, plank, and crumbled wall textures readable instead of flattening every house wall into the generic void wall texture, and same-instance edge slips are snapped back into the arena instead of ending the run. `WorldPopulator` loads the Siege props with the other Voidscape custom scenery, `Custom_Landscape.orsc` is regenerated for both server and client cache, `MD5.SUM` is refreshed, and `CLIENT_VERSION`/local `client_version`/voidbot move to `10114` because the landscape cache is client-visible. No new art, object definitions, packet/opcode shape, schema, or economy behavior changed; reversibility is removing the patcher block/loc file/load call, restoring the previous Siege coordinates, regenerating the landscape, and downgrading the version.

### 2026-06-19 - Undead Siege crossbow kit and compact Ruins arena

Undead Siege now uses a tighter Ruins-house footprint instead of the broader Wilderness Ruins field, keeping the void terrain/definition recolor scoped to the smaller minigame bounds. The temporary kit has moved off Rune 2H, shortbow arrows, and Fire Blast runes: players start with a crossbow, crossbow bolts, eight swordfish, a Staff of air, and a small Crumble Undead rune stack. The intermission point shop now exposes sprite-backed buys for bolts, Crumble Undead runes, Staff of air, tuna, cake, and swordfish. Siege NPCs set a generic ranged-ammo suppression flag so consumed bolts do not leave ground clutter inside the run, while normal ranged ammo behavior remains unchanged elsewhere.

### 2026-06-19 - Undead Siege visual reskin probe

Added a client-only world reskin probe for Undead Siege iteration. The shared `World` renderer now has an authentic terrain ramp and a gated Voidscape ramp, plus gated tile/door colour remapping that defaults to `auto` so only the Undead Siege arena bounds use the void pass unless the workbench forces `authentic` or `void` for screenshots. The AI workbench can reload the current region under a selected profile and reload entity sprites without a protocol change. Added `scripts/probe-void-recolor-defs.py`, which writes a reversible patch file for NPC/AnimationDef tint sweeps, and extended the NPC previewer with raw 27-frame entity-set playback, combat/walk direction cycles, onion skinning, and headless entity dumps. Files: `Config.java`, `mudclient.java`, `World.java`, `WorkbenchServer.java`, `NpcPreview.java`, `scripts/run-npc-preview.sh`, and `scripts/probe-void-recolor-defs.py`. No new art, cache assets, packet/opcode shape, schema, or client-version bump changed; reversibility is forcing `-Dvoidscape.worldReskin=authentic`, removing the probe endpoints/tooling, and discarding the generated recolor patch.

### 2026-06-19 - Bug-feed interaction and safe-death fixes

Fixed several bug-feed issues across the shared client and server. Chat history scrollbar presses are now consumed after the chat panel handles them, preventing the same click from also becoming a world/map action while keeping normal chat interaction intact. PK Catching Simulator training targets now block ranged and spell actions, and sessions return players safely if the synthetic target is unexpectedly removed. Scoped mini-game safe-death respawns are ignored in instance 0, Wilderness, and player-kill contexts so stale safe-death state cannot suppress normal bones/item-loss death handling. Mobile can use the Voidscape HUD skin instead of being forced back to the legacy layout, Android keeps a retained game client across transient Activity recreation, and the chat entry now allows longer `::bug` command text while normal chat remains capped at the old 80-character limit. The underlying RSC rule that unskulled players keep up to three protected items is unchanged. Files: `Panel.java`, `mudclient.java`, `GameActivity.java`, `PkCatchingSimulator.java`, and `Player.java`. No opcode, cache asset, schema, or client-version bump changed; reversibility is restoring the prior chat input, simulator target, scoped safe-death, mobile HUD, and Android lifecycle checks.

### 2026-06-19 - Beta FarmSim loot projections

Added a beta-only FarmSim workflow for NPC drop-rate tuning. Testers can apply melee-only `::farmkit` presets at flat 40/60/80/99 Attack/Strength/Defense/Hits, with ranged/prayer/magic reset to 1 and the agreed addy/rune/ruby-ammy gear, kill a representative cluster of NPCs, then run `::farmsim` to project their observed kill pace into a one-hour expected-loot popup with item sprites and quantities. The server samples real NPC kills, uses the authoritative drop tables plus bones/ashes and existing stackable-drop boosts, and sends the client UI through a hidden `@vsfarmsim@` server-message token consumed before chat display. Files: `FarmSim.java`, `Npc.java`, `RegularPlayer.java`, `BetaOnboardingGuide.java`, `mudclient.java`, `PacketHandler.java`, `Commands.md`, beta guide, and networking docs. No opcode, packet shape, cache asset, schema, economy persistence, or client-version bump changed; reversibility is removing the commands/sample hook and client hidden-message modal.

### 2026-06-19 - Sir Charles presentation and fight banter

Renamed the visible Void Arena DM King NPC definitions and player-facing challenge text to Sir Charles while keeping the internal DM King class names, ids, cache keys, and metadata channel stable for compatibility. Lobby talk now uses a shorter 256-combination British taunt pool, active fights add rate-limited overhead quips keyed to player food, player hitpoints, probable missed Fire Blast casts, Sir Charles' own low-food state, player-name jabs, and occasional random duel banter, and the client can now opt NPC definitions into player-composite rendering so Sir Charles' rune large helmet and other player equipment layers render from every direction. This is a visible NPC-definition/client-rendering/content change only; no opcode, packet shape, cache archive, Elo/ranked table, economy, or item-loss behavior changed.

### 2026-06-19 - Plain-language transparency page

Rewrote the standalone transparency page for average players instead of technical readers. Visible copy now explains the same trust systems as safer downloads, watched staff tools, item history, and daily checks, while hiding terms like manifest, hash, ledger, provenance, and audit from the main reading path. The existing public API shape and deeper proof links remain unchanged; no game packet, schema, cache, client-version, or gameplay behavior changed.

### 2026-06-19 - DM King unlimited pressure pass

Adjusted the Void Arena DM King challenge so the NPC no longer loses magic or prayer pressure after its mirrored swordfish count runs out. DM King now keeps scoped high combat prayers active indefinitely, casts unlimited legal Fire Blasts while still respecting spell range/path and PvP reattack timing, re-applies its visible strength-potion boost when Strength decay drops below the boosted level, and prefers magic kiting when out of food and under melee threat. The lobby DM King now roams within a small Void Arena box, the dynamic fight copy idles around the cage before the countdown starts, and the client suppresses generic `Talk-to` on the dynamic fight NPC while keeping lobby talk/challenge intact. No opcode, packet shape, cache asset, Elo/ranked table, economy, or item-loss behavior changed; reversibility is restoring finite virtual casts/prayer, the pinned lobby loc, and the generic NPC talk row.

### 2026-06-19 - Landing install help and funnel tracking

Added a compact public-beta install help strip, live basics counters, and same-origin ad-funnel click tracking to the public landing page. The page now gives quick Windows/APK/`::bug` guidance near the download buttons, surfaces server/build/update basics from `/api/public`, and tags launcher, Android, Discord rewards, and transparency clicks through a public `/api/funnel/click` endpoint with an admin-only `/api/admin/funnel` count summary. Tracking stores sanitized click metadata and UTM fields in the existing portal audit store; no raw IPs are exposed, and no game packet, schema, cache, client-version, or gameplay behavior changed.

### 2026-06-19 - Account integrity transparency scan

Extended the readonly transparency exporter and admin-only `::integrity` command beyond economy rows into account/stat integrity checks. The scan now flags unknown account groups, missing stat rows, invalid account flags/cache values, stale or suspicious subscription/reward cache rows, and recent sensitive staff commands, while treating known privileged accounts as review-only visibility rather than public accusations. The portal exposes only aggregate `accountIntegrity` counters on `/api/public` and `/api/integrity`; private names, cache keys, and command details stay in the staff-only findings file. No packet, schema, cache format, client-version, or gameplay behavior changed; the scanner reports findings only and does not auto-fix data.

### 2026-06-19 - Admin receipt lookup command

Added a private read-only `::receipts` admin command over the item provenance ledger. Admins can inspect recent rows or filter by player, item instance id, catalog id, or provenance command, with compact in-game output showing time, item, source/destination, actor/target, and stored receipt metadata. This keeps the public transparency page aggregate-only while giving trusted operators a fast way to answer "where did this item/card/value come from?" without SSHing into SQLite. No packet, schema, cache, client-version, or gameplay behavior changed; reversibility is removing the command plugin and its command reference.

### 2026-06-19 - DM King altar pressure and mirrored food

Kept the Void Arena cage prayer altars as intentional deathmatch geometry and made DM King handle altar play directly. His custom loop now checks legal Fire Blast casts independently from movement so he can keep casting on cooldown while pathing around the altar, and melee re-engage uses A*-assisted movement toward a legal adjacent pressure tile instead of the generic straight-line NPC chase that could wedge behind the altar. DM King's virtual swordfish count now mirrors the challenger's actual starting swordfish capacity, and his eat message reports the remaining count after each fish. No packet, client-version, scenery definition, schema, economy, Elo, or ranked-table behavior changed; reversibility is restoring the prior generic re-engage and fixed DM King food count.

### 2026-06-19 - DM King legal PvP timing and kite AI

Tightened DM King's Void Arena challenge AI around authentic PvP action limits. DM King can no longer eat while actively locked in melee combat; he must wait for the normal three-round retreat gate, break combat through the same NPC retreat path players see, and then consume finite swordfish one at a time using the normal edible heal table. Retreats now stamp both DM King and the challenger with the configured PvP reattack timer, and both DM King's melee/Fire Blast pressure and the challenger's attack/cast actions are blocked until that timer expires. His food decision now uses the live melee max-hit helper and tops up during a legal food window, while his combat loop can hold spell range, kite for Fire Blast windows, and re-engage with melee when magic is depleted, pathing fails, or the challenger is low. His virtual prayer state now explicitly toggles only Steel Skin, Ultimate Strength, and Incredible Reflexes, drains those three together, and applies only their scoped combat bonuses until prayer is exhausted. No packet, client-version, NPC-definition, economy, Elo, or ranked-table behavior changed; reversibility is restoring the prior threshold eat/cast/re-engage loop.

### 2026-06-19 - Reward and auction provenance coverage

Closed two high-trust item provenance gaps. The Lumbridge Subscription Vendor now writes `item_origin` receipts when starter/signup subscription cards are granted, subscription-card redemption writes a `player_inventory` to `subscription_ledger` transfer after the subscription ledger is extended, and moderator auction deletion writes an `auction_mod_delete` transfer from active listing to the seller's auction collectible queue. Signup-code receipts store only a short code suffix for correlation rather than raw bearer codes. No packet, cache-schema, client-version, or gameplay balance behavior changed; reversibility is removing the receipt calls from the vendor/card/auction paths.

### 2026-06-19 - Automated transparency integrity exports

Documented and reproducibly packaged the live transparency snapshot exporter. `scripts/export-integrity-summary.mjs` now supports `--quiet`/`PORTAL_INTEGRITY_QUIET=1` for timer-friendly one-line journald output, and `Deployment_Scripts/systemd/voidscape-integrity-export.{service,timer}` runs the readonly SQLite scan every five minutes to refresh `/var/lib/voidscape-portal/integrity-summary.json` plus staff-only `integrity-findings.json`. The portal continues to serve only the public-safe summary through `/api/public` and `/api/integrity`; no packet, gameplay, schema, cache, or client-version behavior changed.

### 2026-06-19 - Project-owned public source mirror

Published a clean public AGPL source mirror under `https://github.com/voidscape-gg/voidscape` and pointed the portal Source tab/build metadata at that project-owned account instead of a personal GitHub. The mirror is a fresh public-source commit with runtime DBs/logs/backups/developer memory and private deploy metadata excluded; the portal trusts deploy build metadata when it explicitly says the mirror commit is clean so the Source tab can report the recorded public baseline. Files: `PUBLIC_SOURCE.md`, `web/portal/dev-server.mjs`, `web/portal/script.js`, `web/portal/transparency.html`, `web/portal/transparency.js`, and `web/portal/build-meta.json`. No gameplay, packet, schema, cache, or client-version behavior changed; reversibility is pointing the metadata/link copy back to another AGPL source host.

### 2026-06-19 - DM King public W/L record

Added a lightweight global W/L record for DM King and surfaced it on his right-click `Challenge` row through the existing hidden Void Arena client metadata channel. The record defaults to `300-0` from global-cache fallback values, persists natural fight outcomes in `void_arena_dmking_wins` and `void_arena_dmking_losses`, counts DM King wins for kills, timeouts, and logout forfeits, and counts losses when a challenger defeats him. DM King talk now picks from a 256-combination cocky taunt pool without immediately repeating the same line to the same player. Files: `VoidArena.java`, `DmKing.java`, `mudclient.java`, and `docs/subsystems/void-arena.md`. No opcode, packet shape, schema, cache asset, Elo, or ranked table behavior changed; reversibility is removing the metadata payload, client label formatter, global-cache counter updates, and taunt bank.

### 2026-06-19 - Void Arena DM King challenge

Added DM King as a right-click solo challenge NPC in the Void Arena lobby. Eligible ranked-stat players can fight a dynamic level-123 DM King in an allocated cage under ranked-kit-style rules without Elo, placement, leaderboard, item-loss, drop, or XP rewards. The session snapshots and restores the player's real inventory/equipment across win, loss, timeout, logout, cleanup, and login recovery; both sides use finite standard supplies, with DM King limited to melee, Fire Blast, swordfish, one visible strength-potion sip, and virtual prayer. The player kit uses classic inventory-wielded rune gear, bypasses normal gear unlock requirements for the temporary arena loadout, and fills only the remaining inventory slots with food, so the visible swordfish capacity reflects gear/rune/potion slot pressure. The client-visible DM King definitions intentionally copy the Death Match Arena Void Knight appearance exactly while keeping DM King's own name, level, and behavior. On beta worlds with `want_beta_onboarding_guide`, starting the challenge auto-sets tester stats to 99. First victory unlocks the reusable `DM Kingslayer` title and broadcasts globally once per player; completed fights also report remaining player/DM King swordfish and DM King's effective Fire Blast cadence. Files: `VoidArena.java`, `VoidArenaKitSnapshot.java`, `DmKing.java`, `NpcDefsCustom.json`, `NpcLocsVoidArena.json`, `NpcId.java`, `Npc.java`, `Player.java`, combat/magic/ranged handlers, `PlayerTitle.java`, client NPC definitions/bubble rendering, and protocol docs. This bumps the custom client version to `10112` for visible NPC definitions only; no packet/opcode shape, economy source, or ranked table write changed. Reversibility is removing NPC ids `862/863`, the plugin/session path/title, and downgrading the client version to `10111`.

### 2026-06-18 - Global chat Discord read-only relay

Added an opt-in one-way relay from in-game global chat to a dedicated Discord webhook, intended for a read-only community channel where players can follow `::g` without being logged in. The relay covers only global chat from `::g` and `Global$`, explicitly excludes `::pk` and private messages, strips game formatting tags, and sends webhook payloads with Discord mentions disabled so in-game text cannot ping everyone, here, users, or roles. Files: `RegularPlayer.java`, `World.java`, `DiscordService.java`, `Server.java`, `ServerConfiguration.java`, and server configs. No packet, opcode, schema, cache asset, client-version, or gameplay rule changed; reversibility is disabling `want_discord_global_chat_relay` or removing the relay call sites/config keys.

### 2026-06-18 - Portal trust and integrity explainer

Added a compact, tabbed trust section to the public beta portal that addresses common RuneScape Classic private-server concerns without blocking the download funnel. The section keeps claims status-based: open downloads are live, while source/release hashes, staff audit summaries, item provenance, and economy scans are presented as next/design/planned work rather than already-finished guarantees. Files: `web/portal/index.html`, `web/portal/styles.css`, and `web/portal/script.js`. No packet, schema, cache asset, gameplay behavior, or portal API contract changed; reversibility is removing the integrity markup/styles/tab handler.

### 2026-06-18 - Android portrait orientation support

Unlocked the Android launcher/game activities from landscape-only orientation and taught the bitmap surface to resize the shared 512px-wide framebuffer for portrait devices instead of always drawing the classic 512x346 frame. Portrait keeps the same client width but expands the backing frame height up to a capped phone-friendly viewport, while landscape falls back to the original classic dimensions; touch mapping continues through the existing Android scale/offset path. Verified the debug APK builds and launches in a no-window Android emulator, with screenshots captured at `768x1280` portrait, `1280x768` landscape, and portrait-after-rotate while `GameActivity` stayed resumed. Files: `AndroidManifest.xml` and `RSCBitmapSurfaceView.java`. No packet, opcode, cache asset, schema, client-version, or gameplay behavior changed; reversibility is restoring the prior `sensorLandscape`/landscape manifest entries and fixed 512x346 surface frame.

### 2026-06-18 - Public beta admin command safety guard

Added a central command-dispatch safety guard for public beta worlds where testers are temporarily auto-admins. While `want_beta_onboarding_guide` is enabled, non-owner admin accounts are blocked from grief-prone commands: server restart/shutdown, global announcements/system messages, summoning or moving other players, moderation/rank/account/cache/quest tools, destructive inventory/bank actions, mass events, synthetic load bots, invulnerability/possession/disguise powers, and other debug/config commands. Self-directed beta conveniences remain available, with extra caps on `::item`/`::noteditem`/`::certeditem` amount, `::spawnnpc` radius/time, and a short per-player NPC-spawn cooldown so testers cannot accidentally overload the world. Files: `CommandHandler.java`. No packet, opcode, schema, cache asset, or client version changed; reversibility is removing the beta guard or disabling `want_beta_onboarding_guide`.

### 2026-06-18 - Beta bug reports and frictionless downloads

Added a public beta launch path for tester bug reports and no-login downloads. Players can now use `::bug <what happened>` / `::reportbug <what happened>` to write a `bug_reports` ledger row with reporter/location/client metadata, log the report server-side, and enqueue it to a dedicated Discord bug webhook when configured (falling back to the general Discord log queue otherwise). Added matching SQLite/MySQL patches for that report table; the bug webhook can be supplied through `discord_bug_reports_webhook_url` or the uncommitted `DISCORD_BUG_REPORTS_WEBHOOK_URL` environment variable. The portal now treats the desktop launcher and Android APK as explicit public download artifacts, exposes them on the landing page without requiring Discord login, and allows `/downloads/*` plus the launcher manifest while `PORTAL_PUBLIC_MODE=1`. Files: `RegularPlayer.java`, `DiscordService.java`, `ServerConfiguration.java`, server configs, `server/database/*/patches/2026_06_18_add_bug_reports.sql`, `web/portal/*`, `Commands.md`, and portal docs. No packet, opcode, client-version, item/NPC definition, or gameplay rule changed; reversibility is removing the command branch/config keys, dropping the `bug_reports` table if desired, and returning public-mode downloads to the prior hidden state.

### 2026-06-17 - Interactive beta tester toolkit

Upgraded `::beta` / `::betaguide` from a passive reference box into an interactive beta tester toolkit behind the existing `want_beta_onboarding_guide` gate. The menu now offers click-to-teleport hub and boss destinations, instant all/combat/skilling 99 stat presets, stat restore, spawnable beta kits for subscription cards, Void Keys, Void gear, combat supplies, skilling tools, coins, plus compact checklist/reference boxes with commands, coordinates, and item IDs. The Void Herald first-time beta prompt and the manual command both use the same `BetaOnboardingGuide` flow. Files: `BetaOnboardingGuide.java`, `Commands.md`, and `docs/subsystems/void-island-starter.md`. No packet, opcode, client, schema, or cache changes; reversibility is restoring the prior text-only guide or disabling the beta guide config.

### 2026-06-17 - In-game IGN beta referral rewards

Added an in-game beta referral flow for first-time character creation: the Voidscape client shows a short subscription-card invite prompt before the appearance designer, adds an optional `Invited by` in-game-name field, and appends that name to the custom appearance packet. The server accepts the appended field only for custom clients `>= 10111`, validates the inviter by IGN, rejects self/unknown/repeated referrals, mints a real `VOID-XXXX-XXXX` code in the existing `signup_code:<CODE>` vendor ledger, notifies online referrers immediately, stores offline notices for next login, and exposes earned referral codes through public `::codes` / `::refcodes`. Files: `BetaReferralReward.java`, `PlayerAppearanceStruct.java`, `PayloadCustomParser.java`, `PlayerAppearanceUpdater.java`, `PlayerLogin.java`, `BetaReferralCodes.java`, `mudclient.java`, `Config.java`, `tools/voidbot/protocol.py`, `Commands.md`, and networking docs. This is a custom packet-shape change with no opcode/enum ordinal shift; reversibility is removing the appended field/service/command and downgrading `CLIENT_VERSION`/`client_version` back to `10110`.

### 2026-06-17 - Beta onboarding, first-join announcements, and referral codes

Added beta launch support for fresh accounts: worlds can now broadcast a one-time global welcome when a brand-new player first joins, the Void Herald opens a one-time beta guide menu after starter-path selection, and players can reopen that guide with `::beta`. The portal referral path now mints one `VOID-XXXX-XXXX` subscription-card reward code for the referrer on every credited beta invite, reusing the existing `signup_code:<CODE>` vendor redemption ledger and admin sync/export path. Files: `WorldAnnouncementService.java`, `World.java`, `ServerConfiguration.java`, `VoidHerald.java`, `BetaOnboardingGuide.java`, `RegularPlayer.java`, `Admins.java`, `server/local.conf`, `Commands.md`, `web/portal/*`, and beta/subscription/announcement docs. No packet, opcode, cache asset, or `CLIENT_VERSION` change; reversibility is disabling the two new config flags and removing the referral reward-code records from the portal store.

### 2026-06-17 - World-map walker snap hardening

Hardened the server-side world-map walker when a map click lands on blocked terrain such as a wall, roof, coast, or F2P-restricted tile. `WorldPathfinder` now collects every walkable snap candidate within the existing 12-tile radius and runs A* to the first reachable candidate, instead of trusting the first walkable tile found by scan order. This avoids false `NO_PATH` results when that first snap tile is isolated but another nearby tile can be reached. Files: `server/src/com/openrsc/server/model/WorldPathfinder.java`. No packet, opcode, schema, cache asset, or `CLIENT_VERSION` change; reversibility is restoring the old single-tile `nearestWalkable` snap helper.

### 2026-06-17 - Skill-specific XP/hour HUD badge

Added a client-side bottom-right training badge that appears while XP is being gained and shows the current trained skill's XP/hour using the existing stat/experience update packets. The tracker records positive per-skill XP deltas regardless of the legacy top XP counter setting, hides after short inactivity, and keeps primary combat skills visible instead of letting paired Hits XP take over the badge. Files: `Client_Base/src/orsc/PacketHandler.java` and `Client_Base/src/orsc/mudclient.java`. No server packet, opcode, schema, cache asset, or `CLIENT_VERSION` change was added; reversibility is removing the badge draw helpers and restoring XP accounting to the old counter-gated path.

### 2026-06-17 - Bestiary catalog hides bone-only NPCs

Moved the `BEST` sub-tab's searchable NPC list from a client-side "all NPC definitions" scan to a server-authored catalog response. The catalog reuses existing `INTERFACE_OPTIONS` sub-option `16` and `SEND_BESTIARY` packet shape as mode `2`, listing only NPCs with at least one allowed regular drop-table row beyond guaranteed bones or ashes, so bone-only beasts no longer appear in search. The selected NPC detail view and observed `LOOT` snapshot remain on modes `1` and `0` respectively. Files: `ActionSender.java`, `BestiaryStruct.java`, `InterfaceOptionHandler.java`, `EntityHandler.java`, `mudclient.java`, `WorkbenchServer.java`, and protocol docs. Reversibility is returning BEST search to the client definition index and removing the catalog mode branch.

### 2026-06-17 - Bestiary browser usability pass

Reworked the client-side `BEST` sub-tab into a smoother drop-table browser without changing packets or server drop math. NPC search now uses a cached, ranked client index instead of scanning and sorting definitions every frame; result-list scrolling and drop-table scrolling have independent state; selecting an NPC opens a focused detail view with a Back control; typing a new query or pressing Escape returns cleanly to the searchable list. The dev workbench JSON state now exposes bestiary visibility, search/result counters, scroll offsets, and click coordinates so QA can drive search/select/back/drop-scroll flows through the existing loopback workbench. Files: `Client_Base/src/orsc/mudclient.java`, `PC_Client/src/orsc/WorkbenchServer.java`, and docs. Reversibility is removing the cached search/result-scroll helpers and workbench bestiary state block.

### 2026-06-17 - Loot tab and Bestiary split

Restored the right-panel `LOOT` sub-tab to the observed NPC loot tracker: killed NPCs show kill count headers and grids of item sprites with observed quantities from the existing `bestiaryloot` persistence. The searchable authoritative NPC drop-table browser now lives in a separate fourth `BEST` sub-tab; the compact player-info selector uses existing icon assets for stats, quests, loot, and bestiary. Bestiary rates render as rounded `1/N` odds instead of raw reduced fractions such as `34/4000`. This is a client UI/workbench/documentation repair only; it reuses the existing mode byte on `SEND_BESTIARY` and does not add opcodes, change packet shape, bump `CLIENT_VERSION`, or alter the database schema. Files: `Client_Base/src/orsc/mudclient.java`, `PC_Client/src/orsc/WorkbenchServer.java`, and protocol/divergence docs. Reversibility is moving the browser renderer back onto `LOOT` and removing the observed-loot grid helpers.

### 2026-06-17 - 3D terrain editor Phase 3 brush tools

Extended the dev-only in-client terrain editor from single-tile edits to brush operations. The editor now supports adjustable brush radius, raise/lower with radial falloff, smooth, flatten, water/grass/stone paint brushes, and in-memory undo/redo. Terrain rebuilds still use the existing client landscape renderer and unsaved override layer; this phase does not write `Custom_Landscape.orsc`, alter server state, add packets, or persist terrain. Files: `Client_Base/src/orsc/graphics/three/World.java`, `Client_Base/src/orsc/mudclient.java`, and `PC_Client/src/orsc/ORSCApplet.java`. Reversibility is removing the brush/snapshot stacks and null-grid scene add guard.

### 2026-06-17 - 3D terrain editor Phase 2 live edit proof

Extended the dev-only terrain editor mode so clicking the highlighted terrain tile can mutate the loaded landscape in memory and immediately rebuild the current 3D terrain view. Phase 2 supports single-tile raise/lower plus visual water/grass/stone painting through the existing sector fields and renderer path, using an unsaved in-memory override layer so normal sector reloads preserve live edits without touching `Custom_Landscape.orsc`. Files: `Client_Base/src/orsc/graphics/three/World.java`, `Client_Base/src/orsc/mudclient.java`, and `PC_Client/src/orsc/ORSCApplet.java`. This phase deliberately has no archive save path, no server persistence, and no packet/schema changes; reversibility is removing the editor override map, click/key handlers, and current-region rebuild hook.

### 2026-06-17 - 3D terrain editor Phase 1 hover proof

Added a dev-only terrain editor hover mode to the desktop client, toggled with `Ctrl+F9`, that reuses the existing scene pick buffer to map the mouse cursor back to the rendered landscape face and draw a translucent tile overlay in the 3D view. This is a Phase 1 interaction proof only: it does not mutate terrain, save landscape archives, alter packets, or touch server state. Files: `Client_Base/src/orsc/mudclient.java` and `PC_Client/src/orsc/ORSCApplet.java`. Reversibility is removing the toggle and overlay draw hook.

### 2026-06-16 - OB3 scenery model importer

Added the first Voidscape content tool for untextured 3D scenery models: `scripts/content.sh model import` can read OBJ, glTF, or GLB meshes, convert material or vertex colors into RSC's packed RGB555 face fills, write OB3 bytes, and insert them into the OpenRSC/JAG-style `models.orsc` archive without changing the renderer. The tool includes OB3/JAG readers, round-trip tests against stock cache entries, and a small `Void test cube` proof object registered as scenery id `1310` and spawned in the Void Enclave for local client validation. Files are concentrated in `tools/voidscape-content/`, `Client_Base/Cache/video/models.orsc`, `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/conf/server/defs/GameObjectDef.xml`, and `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`. Reversibility is removing id `1310`, its loc and source fixture, and deleting `void_test_cube.ob3` from the model archive with the same JAG writer.

### 2026-06-16 - Void portal arch model proof

Imported the selected CC0 Kay Lousberg arch candidate as `void_portal_arch.ob3`, using a derived untextured OBJ/MTL with flat Voidscape palette fills: deep violet stone, dark gunmetal shadow faces, and restrained gold accents. The archive insert preserves the existing JAG u24/u24 framing and appends a new client/server scenery definition at id `1311`, spawned in the Void Enclave beside the prior test cube for local validation. Files: `content/custom/void_portal_arch/`, `Client_Base/Cache/video/models.orsc`, `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/conf/server/defs/GameObjectDef.xml`, and `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`. No renderer, packet, schema, or behavior changes were added; reversibility is removing object id `1311`, its loc/source pack, and deleting `void_portal_arch.ob3` from the model archive.

### 2026-06-16 - In-game NPC drop-table browser

Reworked the custom Loot tab into a searchable NPC drop-table browser. Players can search any client-known NPC, select it, and receive an authoritative server-flattened drop table rendered with raw reduced ratios such as `1/128`, including guaranteed bone/ash drops and separate rows for different stack amounts. The feature reuses existing custom `INTERFACE_OPTIONS` sub-option `16` and `SEND_BESTIARY` wire opcode `105`, but extends their payloads with a mode byte plus ratio numerator/denominator fields, so `CLIENT_VERSION` and voidbot moved to `10110`. Files: `DropTable.java`, `ActionSender.java`, bestiary packet structs/parser/generator, `PacketHandler.java`, `mudclient.java`, `Config.java`, and `tools/voidbot/protocol.py`. Reversibility: restore the previous bestiary payload shape/client grid and downgrade the client version to `10109`.

### 2026-06-14 - Portal whitepaper story redesign

Rebuilt the pre-release portal whitepaper around a tighter story: "We love Classic. We are finishing what it started." The visible page now moves from premise, to what Classic left unfinished, to the Voidscape loop, to six proof-backed pillars, to a compact build-proof wall using current beta/workbench captures under `web/portal/assets/whitepaper/`. The public prelaunch page no longer embeds the MP4 background, relying on the static fallback for better scroll performance, and whitepaper screenshots can be expanded in a lightweight viewer. This is a static website/content change in `web/portal/index.html`, `web/portal/styles.css`, and `web/portal/script.js`; no game packets, schema, cache archives, or gameplay behavior changed. Reversibility is restoring the previous whitepaper markup/styles/script behavior and removing the added `feature-*-current.png` assets.

### 2026-06-14 - Beta in-client registration re-enabled

Friend beta account creation now allows the desktop client registration packet again. The live VPS still owns `server/local.conf` outside git, so the `Deploy beta` full workflow normalizes `want_packet_register: true` on the remote config after backing it up and before restarting `voidscape`. This keeps launcher/VPS beta signups working without committing runtime secrets or the deployed `local.conf`.

### 2026-06-14 - Voidscape top-menu classic-width polish

Narrowed the shared Voidscape top-menu glass panels back toward classic OpenRSC side-panel proportions while keeping them responsive: standard panels start at 196px on compact viewports and step up through larger widths as the screen preset grows, while Inventory keeps the classic grid-width frame. Open panels anchor under their active top-bar icon, start directly under the icon row, and only clamp when they would run off the right edge. Sub-header strips sit just inside the border, list/text content gains a viewport-aware inner inset, and the Stats panel now uses a real gutter between skill/equipment columns so typography does not converge at larger sizes. This is a draw/layout-only pass in `Client_Base/src/orsc/mudclient.java`; no packets, cache assets, schema, or gameplay behavior changed.

### 2026-06-14 - Void Rift city hub and Edgar spawn removal

The new static Void Rift at `(139,636)` now acts as a city travel hub: using it opens a destination menu for Void Enclave, Lumbridge, Draynor, Falador, Edgeville, Varrock, Al Kharid, Karamja, Yanille, Ardougne, Catherby, Seers, and Gnome Stronghold, with the same combat safety check as the older rift flow. Edgar's Lumbridge spawn was removed from `NpcLocs.json` so the wizard no longer appears in-world; his NPC definition and handler remain in place to avoid shifting positional custom NPC ids. Files: `server/plugins/com/openrsc/server/plugins/custom/misc/VoidRift.java` and `server/conf/server/defs/locs/NpcLocs.json`. No packets, schema changes, client definitions, object ids, NPC ids, or `CLIENT_VERSION` changes were added; reversibility is restoring the Edgar spawn block and removing the `(139,636)` city-rift branch.

### 2026-06-14 - Void Rift portal visual polish

Void Rifts now render as standalone ground portals keyed to scenery object id `1306`: the shared client gives rift instances a generated, terrain-following, pickable void-purple ground plate instead of the inherited `rockpool` shell, using a smoothed concentric dark-core/mid/edge mesh plus a low beveled obsidian-purple border so the surface reads like a framed opening in the floor. The effect paints rotating crescent arcs, inner spiral lines, subtle floor-hugging fissures, and sparse loot-beam-style sparkles from ground-plane projections so the animation stays flush under camera rotation. The Voidscape HUD also no longer draws the custom black backing rectangle behind generic cyan option-menu choices, leaving rift teleport prompts directly over the world. The server/client object definition names the model `voidriftportal`, while all rift plugins/teleports and placements still use the same object id; a second static rift placement was added at `(139,636)` for the local test character's current tile. Files: `Client_Base/src/orsc/mudclient.java`, `Client_Base/src/orsc/PacketHandler.java`, `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/conf/server/defs/GameObjectDef.xml`, and `server/conf/server/defs/locs/SceneryLocsVoidRift.json`. No packets, opcodes, schema changes, cache archives, object ids, or `CLIENT_VERSION` changes were added; reversibility is restoring the rift model name to `rockpool`, removing the generated rift model/overlay hook, restoring the option-menu backing draw, and removing the extra static loc.

### 2026-06-14 - Void Island starter teleport lock

Unchosen starter characters can no longer teleport out of Void Island before selecting a Void Path. The shared `Player.teleport(...)` path now refuses destinations outside the current or legacy starter island while `void_path` is unset, covering spells, objects, commands, and other server-side teleports without blocking the Herald completion teleport after a path is stored. Files: `VoidPath.java`, `Player.java`, and `Point.java`. No packets, schema changes, or client assets were added; reversibility is removing the central guard and coordinate helper.

### 2026-06-14 - Void Arena season champion title

Void Arena ranked season resets now preserve a season winner before wiping ratings: the reset preview shows the visible champion candidate, confirmation aborts if that candidate changes before the token is used, and a successful reset records the current champion in global cache while unlocking the reusable `Void Arena Champion` player title for the winner even if they are offline. The leaderboard and admin season preview now show the current champion alongside the top ratings. This also fixes the shared player-id-to-username query to use the real `players.id` column, which the champion display path exercises. Files: `VoidArena.java`, `PlayerTitle.java`, and the shared database cache/query persistence hooks. No client packets, schema changes, or cache assets were added; reversibility is removing the champion cache/title award hooks and title enum entry.

### 2026-06-13 - Void Arena four-cage ranked hall

Moved ranked Void Arena fights out of private instances and into a dedicated baked map sector at `h0x60y38`: four compact physical fight cages sit below a narrow public spectator hallway, with the Herald and bank chest in the hall and one prayer altar inside each cage. Cage perimeters use baked railings/jail bars instead of stone walls so spectators can visually read the matches. Ranked challenge acceptance now claims the first available cage, keeps both fighters in the public instance so spectators can watch, and still gates attacks so only assigned opponents can fight inside their assigned cage. The client lobby hitbox, server lobby/cage bounds, Herald/chest/altar locs, `Custom_Landscape.orsc` server/client copies, cache MD5, and the area plaque name were updated. No opcode, schema, or cache definition id changed; reversibility is restoring the old `VoidArenaConfig` coordinates/instancing behavior and removing the arena sector patch/loc updates.

### 2026-06-13 - Void Arena lobby bank chest

Added the first lobby utility pass for Void Arena: a static bank chest (`SceneryId.BANK_CHEST`, object id `942`) now loads at `(550,84)` from `SceneryLocsVoidArena.json`, and `WorldPopulator` loads that file alongside the other Void Arena/Colossus scenery. The existing Shantay bank chest handler now whitelists chests inside `VoidArenaConfig.isInsideLobby(...)`, preserving Ultimate Ironman and bank-pin checks while letting the Arena chest work on F2P/local presets like other Voidscape custom chests. No client version or protocol change was needed because the chest object already exists in the cache/client definitions. Reversibility: remove the scenery loc/load call and the Arena whitelist clause.

### 2026-06-13 - Void Arena leaderboard herald

Added the in-world Void Arena Herald as the ranked lobby's leaderboard NPC. New custom NPC id `861` is appended to server/client NPC definitions, spawned statically at `(552,84)` through `NpcLocsVoidArena.json`, and handled by `VoidArenaHerald` so left-click talk or right-click `Leaderboard` opens the existing top-5 Elo box with the 5-placement-match visibility rule. The lobby teleport/detection now uses the valid Colossus-plaza landing tiles around `(552,82)` instead of the blocked ocean-edge tiles. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10107` because the NPC definition is client-visible; no new packet/opcode or schema change was added. Reversibility: remove NPC id `861`, its spawn file/load call/plugin, restore the prior lobby constants if needed, and downgrade the version to `10106`.

### 2026-06-13 - Void Arena lobby rating visibility

Added the next Void Arena slice for placement-aware Elo visibility. Ranked ratings are now hidden until a player has completed 5 ranked matches: `::arena stats`, match result messages, the top-5 box, and the client-side right-click lobby labels all respect that threshold. The server broadcasts hidden `@vsarena@rating|username|rating|matches|visible` metadata through the existing server-message channel to custom clients `>= 10106`, and the client consumes it before chat display to show `Name (rating)` or `Name (unranked)` only inside the Void Arena lobby. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10106`; no new opcode or schema change was added. Reversibility: remove the hidden rating handlers/broadcasts and downgrade the version to `10105`.

### 2026-06-13 - Void Arena ranked combat foundation

Added the first ranked Void Arena slice as normal 1v1 PvP combat instead of Duel/staking. Players can use `::arena` to enter the lobby, right-click another eligible lobby player for a ranked fight, and start an instanced wilderness-style match with safe death handling, disconnect-loss handling, F2P gear gating, maxed combat-stat gating, and 1200-start Elo persistence in `voidarena_ranked_stats`. The client reuses custom `INTERFACE_OPTIONS` packet `199` with new sub-option `18` for ranked challenge requests, so `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10105`. Files: `server/src/com/openrsc/server/content/voidarena/`, `Player.java`, `World.java`, database schema/patches, `RegularPlayer.java`, `InterfaceOptions.java`, `PayloadCustomParser.java`, `InterfaceOptionHandler.java`, `Client_Base/src/orsc/mudclient.java`, `MenuItemAction.java`, `Config.java`, `tools/voidbot/protocol.py`. Reversibility: remove the arena service/hooks/schema patch and downgrade the client version; ranked stats are isolated in the new table.

### 2026-06-13 - Voidscape location plaque refresh

Replaced only the top-left location plaque cache PNG with the approved new dark-metal/gold/violet generated asset while leaving the chat frame and right-panel chrome on the prior stable assets after the broader raw asset swap proved too stretched/blurry in-game. Refreshed `Client_Base/Cache/MD5.SUM` for the plaque. Verified with the workbench client capture at `content/ui/menu-redesign/captures/plaque-only-check-contact-sheet.png`; no code, packets, schema, or gameplay behavior changed.

### 2026-06-13 - Voidscape menu concept pipeline

Added a repeatable workbench-to-ChatGPT concept loop for the full menu redesign pass. The PC workbench can now open named Voidscape UI states (`hud`, Options/Profile, advanced Settings, Friends/Ignore, Magic/Prayers, Skills/Quests/Loot, Minimap, Inventory, Account) and capture them through `POST /scenario/ui-panels`; `scripts/content.sh ui capture-panels|concepts|asset-prompts` records the screenshot manifest, prepares `gpt-image-2` concept prompts from those live captures and the approved style sheets, and prepares per-asset green-screen prompts from approved concepts. Verified the capture scenario against the live workbench client and generated contact sheets at `content/ui/menu-redesign/captures/contact-sheet.png`, `content/ui/menu-redesign/concepts/latest/contact-sheet.png`, and `content/ui/menu-redesign/assets/latest/contact-sheet-alpha.png`; the run produced 13 menu concepts plus 45 chroma-key and transparent reusable asset candidates under `content/ui/menu-redesign/`. Live API calls are gated behind `--call` and require `OPENAI_API_KEY` in the shell. Files: `PC_Client/src/orsc/WorkbenchServer.java`, `Client_Base/src/orsc/mudclient.java`, `tools/voidscape-content/voidscape_content/ui_concepts.py`, `tools/voidscape-content/voidscape_content/ui_assets.py`, `tools/voidscape-content/README.md`, `docs/subsystems/ai-workbench.md`, `docs/subsystems/content-pipeline.md`, `content/ui/menu-redesign/captures/latest.json`, `content/ui/menu-redesign/concepts/latest/`, `content/ui/menu-redesign/assets/latest/`. No packets, schema, cache assets, or gameplay behavior changed.

### 2026-06-13 - Voidscape topbar icon replacement

Replaced the six main Voidscape top-menu icon masters and baked size variants (`top-gear`, `top-social-smiley`, `top-stats-bars`, `top-book`, `top-map-scroll`, `top-bag`) with the approved second generated set, using object-specific crops so the wide map icon no longer bleeds into inventory. Replaced the account/profile switching button with a newly generated full button asset pair (`account-top-button`, `account-top-button-active`) instead of compositing a new glyph onto the old button. Refreshed `Client_Base/Cache/MD5.SUM` for those cache PNGs and added reusable `gpt-image-2` prompts for future account/profile icon and button iterations. Files: `Client_Base/Cache/voidscape/ui/skin/top-*.png`, `Client_Base/Cache/voidscape/ui/skin/account-top-button*.png`, `Client_Base/Cache/MD5.SUM`, `tools/voidscim-art/prompts/voidscape-profile-topbar-icon.txt`, `tools/voidscim-art/prompts/voidscape-account-top-button.txt`. No code, packets, schema, or gameplay behavior changed.

### 2026-06-13 - Voidscape top-menu panel spacing pass

Tightened the open panels behind the top HUD menu icons so their headers and inner content read as one aligned system. The shared Voidscape panel title plate is now shorter; Stats/Quests/Loot, Options/Profile/Settings, Friends/Ignore, and Magic/Prayers sub-header strips are inset to the title plaque's visual width and use the smaller list-row font under the Voidscape skin. Settings rows gained more left/right padding and compact labels trigger earlier on narrow panels. This is a draw/layout-only pass in `Client_Base/src/orsc/mudclient.java`; no packets, cache assets, schema, or gameplay behavior changed.

### 2026-06-13 - Voidscape UI asset pipeline

Added the first repeatable UI-art pipeline slice for filesystem PNG HUD skin assets. `content/ui/voidscape-topbar-icons.json` records the six top-tab icon slots, the 68x68 master size, baked size variants, and runtime normal/compact icon sizes. `scripts/content.sh ui spec|validate|preview|ingest-sheet` now routes through `tools/voidscape-content`, with sheet ingest staging output by default and requiring `--commit` to overwrite the live `Client_Base/Cache/voidscape/ui/skin` files. This deliberately avoids `.orsc` sprite packing, server packets, and renderer changes; the existing PNG UI path already preserves per-pixel alpha. Files: `tools/voidscape-content/voidscape_content/ui_assets.py`, `tools/voidscape-content/voidscape_content/__main__.py`, `tools/voidscape-content/voidscape_content/paths.py`, `content/ui/voidscape-topbar-icons.json`, `tools/voidscape-content/README.md`, `docs/subsystems/content-pipeline.md`. No opcode, schema, cache-version, or gameplay behavior changed.

### 2026-06-13 - Android bank smoke stabilization

Android bank QA is now repeatable from Windows/Git Bash without a native `sqlite3` install: `scripts/android-smoke.sh` creates a Python-backed sqlite shim for the subset of fixture snapshot/restore commands it needs, keeps the saved launcher endpoint on the normal Play path, and closes the login welcome dialog only when the shared client logs a fresh Android smoke close target. The compact custom bank gained Android touch polish discovered by that smoke loop: tapping bank search opens the soft keyboard, clearing search blurs/closes it on Android so the next bank tap is actionable, and dragging inside the bank grid scrolls the all-items list instead of requiring a desktop wheel. The focused `--only-auth-bank` smoke passed against `10.0.2.2:43598`, proving open/search/clear/touch-scroll/withdraw/deposit/deposit-all/loadout save/load and DB fixture restore. Files: `Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java`, `Client_Base/src/orsc/mudclient.java`, `scripts/android-smoke.sh`, `docs/subsystems/android-client.md`. No opcode, schema, or server gameplay behavior changed.

### 2026-06-13 - Android launcher endpoint persistence and bank touch polish

The Android `Play` path now honors the saved app-private `ip.txt` / `port.txt` endpoint instead of always overwriting the session with the platform default, so local QA can keep targeting a non-default dev server such as `10.0.2.2:43598`. The manual server dialog also pre-fills from that saved endpoint. Android bank QA was rerun against the rebuilt APK on the local server: the bank opens from the projected chest target, the compact translucent bank layout fits the classic framebuffer, search/clear works, stack counts no longer draw black chips, and withdraw/deposit still hit the shared action path. The bank search clear control keeps the same visual position but has a wider invisible hit target so phone taps near the `x` do not miss. Files: `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/CacheUpdater.java`, `Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java`. No opcode, schema, or gameplay behavior changed.

### 2026-06-12 - Android authenticated QA hardening

Android can now close the wilderness warning dialog through the same touch-layer early-close path used for welcome/server-message dialogs, with a wider Android hitbox around the close text so phone taps are not frame-timing dependent. The Android smoke script's default authenticated fixture now matches the seeded local dev account (`android` / `android`) and accepts that account's plaintext dev password during preflight while preserving the legacy salted-hash check for other fixtures. Files: `Client_Base/src/orsc/mudclient.java`, `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java`, `scripts/android-smoke.sh`. No opcode, server schema, or gameplay behavior changed.

### 2026-06-12 — Resume stale in-game sessions after disconnect

Closing the desktop client now leaves a combat/busy player in a resumable pending-disconnect state instead of immediately forcing logout. A follow-up login with the correct password from the same IP can reclaim the existing in-memory `Player`, cancel the pending unregister request/event, bind the new channel, reset client-side view caches, and resend the normal login state without re-running logout/login cleanup hooks. Active healthy sessions still return `ACCOUNT_LOGGEDIN`, wrong passwords still fail before resume, and once logout-save has actually started the normal fresh-login path still applies. Files: `server/src/com/openrsc/server/net/RSCConnectionHandler.java`, `server/src/com/openrsc/server/login/LoginRequest.java`, `server/src/com/openrsc/server/model/entity/player/Player.java`, `server/src/com/openrsc/server/net/rsc/LoginPacketHandler.java`, `server/src/com/openrsc/server/net/rsc/ActionSender.java`. No opcode, schema, or client protocol change.

### 2026-06-12 — Optional Voidscape chat box chrome

Added a local desktop setting for the Voidscape HUD chat box. Advanced Settings -> Chat now has a `Chat box` toggle that saves to `clientSettings.conf` as `chat_overlay`; turning it off hides only the bottom chat background/frame while leaving chat text, input, tabs, and tab clicks intact. No server packet, schema, cache, or gameplay behavior changed.

### 2026-06-12 — Simplify compact bank UI spacing

The custom bank now uses a smaller compact slot grid with dynamic panel height and the same translucent bronze fill/line treatment as the side inventory. Occupied and empty bank cells no longer get different fills, borders, or item backdrops; item presence is shown only by the item sprite and stack count. This removes the noisy double-lines, keeps the bottom inventory row clear of the frame at small window sizes, and makes the action row align to the slot grid with shorter `Deposit` / `Arrange <mode>` labels plus grouped item/note buttons in a slim separator toolbar. Bank item hover now adds only a subtle pulsing glow, while context menus use a fully consumed right-click path and clear stale menu state on close so hover cannot surface withdraw actions or block later input. Files: `Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java`. Reversibility: restore the previous compact slot constants, fixed panel height, ornate slot sprite drawing/backdrops, and old action-row labels/positions. No data, server, or opcode changes.

### 2026-06-10 — Fix appearance-menu arrows + magic/prayer list colours

Three player-reported HUD bugs:

1. **Appearance/makeover arrows did nothing.** The makeover panel's clickable arrow *buttons* are positioned once at client startup (at the startup window width), but the arrows are *redrawn* every frame at the live width — so after any window resize the hit-boxes sit offset from the drawn arrows and clicks land on empty space. Opcode 59 (`setShowAppearanceChange(true)`) only flipped a boolean and never rebuilt the panel, and `reposition()` skipped `panelAppearance`. Fix: rebuild the panel (`createAppearancePanel(-24595, S_CHARACTER_CREATION_MODE)`) when the makeover opens and on resize while open, so the hit-boxes are laid out at the same width the draw code uses. `-24595` skips the login-viewport side-effect (we're in-game).
2. **Magic spells you lack the level for showed bright green.** The "disabled" colour code for the void skin was `@gr3@`, which the text decoder maps to 0x40FF00 (lime green), not grey. Added a real grey code `@gry@` = 0x909090 and used it for the no-level case. Now: grey = no level, white = have level but lack runes, yellow = have level + runes.
3. **Prayer colouring used green for unavailable prayers.** Same `@gr3@`→`@gry@` fix. Now: grey = no level, white = have level but off, green (`@gre@`) only when the prayer is active.

Files: `Client_Base/src/orsc/graphics/two/GraphicsController.java` (new `@gry@` code), `Client_Base/src/orsc/mudclient.java` (`setShowAppearanceChange`/`reposition` rebuilds; magic+prayer no-level colour). Verified at 640×480: arrows cycle each category; spells/prayers above level render grey, active prayers green. Authentic-UI paths keep `@bla@`.

### 2026-06-10 — Location plaque width adapts to the area name (fixes overflow)

The compact location plaque was a fixed 120px wide, so long area names clipped past the ornate end-caps — "DEEP WILDERNESS" lost its leading "D" and ran off the right, and long towns (BARBARIAN VILLAGE, GNOME STRONGHOLD, SEERS PARTY HALL) would too. `drawVoidscapeLocationPlaque` now measures the title with `stringWidth` and sizes the plaque to `max(base, min(maxCap, titleWidth + 40))`, anchored top-left and capped (230 compact / 280 otherwise) so it never reaches the top-tab row. Verified at 640×480: DEEP WILDERNESS + "Level 30" and BARBARIAN VILLAGE both fit cleanly; short names (LUMBRIDGE) are unchanged.

Files: `Client_Base/src/orsc/mudclient.java` (`drawVoidscapeLocationPlaque` — title computed before width; adaptive width).

Reversibility: restore the fixed `width` expression. No data/opcode/version change.

### 2026-06-10 — Chat word-wrap in the compact HUD (readable long lines)

The earlier small-viewport pass clipped chat to the frame interior so long lines couldn't spill onto the world, but that truncated them. Replaced truncation with real word-wrap in both chat views:

- **ALL-tab overlay** (the default floating view): a new `voidscapeWrapColoredString` greedily wraps each message to the frame's interior width using `stringWidth` (which skips `@xxx@` colour codes), carries the active colour code onto continuation lines, and hard-splits any single over-long word. The overlay draws newest-at-bottom with wrapped lines stacking upward, the crown only on a message's first line.
- **CHAT/QUEST/PRIVATE/CLAN scroll tabs**: wrapped at message-add time via `voidscapeAddWrapped`, which splits the message into per-line `addToList` entries (continuation lines drop the crown; the sender is kept on every line so click-to-PM still works). This stays in mudclient — the shared `Panel.renderScrollingList2`/`addToList` (also used by shops, friends, etc.) is **not** modified, so non-chat list UIs are unaffected.

Verified at 640×480: long lines wrap to multiple fully-readable lines in both views, colours carry across the wrap, continuation lines align under the text, crown shows only on the first line, and short/colour-coded system messages are unchanged. Authentic UI and Android paths untouched (both wrap paths are gated on `useVoidscapeHudSkin()`).

Files: `Client_Base/src/orsc/mudclient.java` (`voidscapeWrapColoredString` / `voidscapeLastColorCode` / `voidscapeAddWrapped` helpers; ALL-tab overlay rewrite in `drawGame`; the 5 chat `addToList` call sites in `showMessage` routed through `voidscapeAddWrapped`).

Reversibility: restore the single-line overlay loop, point the 5 chat calls back at `panelMessageTabs.addToList`, and delete the three helpers; the frame clip from the earlier pass remains.

### 2026-06-10 — Tried + reverted: native 24px top-tab icons

An AI vision critique flagged the compact-HUD top-tab icons (640-wide; runtime-downscaled from the 34px assets to 24px) as the most visible blur. Generated native 24×24 variants from the 68px masters (Lanczos + gentle unsharp) and pointed the draw call at the size-matched variant. **A zoomed before/after at 640×480 showed the native-24 icons came out slightly dimmer and softer than the runtime 34→24 downscale** — the original was actually fine and the critique overstated it. Reverted: removed the `-24.png` assets and restored the unconditional 34-variant resolution in `drawVoidscapeTopTabs`. Net change: none. Recorded so we don't re-attempt this without first improving the source art's small-size contrast (the 34px asset has more punch than a Lanczos 68→24, suggesting the 34px was hand-tuned).

### 2026-06-10 — voidbot: headless command-driven game client + test harness

Added `tools/voidbot`, a screenshot-free way to drive and inspect the game entirely through commands, plus `tests/smoke.sh` (session → walk → npc dialog → kill → loot → bank, 26/26 green against the local server). A Python daemon (`voidbotd.py`) logs in over the real wire protocol, decodes server packets into live state (position, inventory, equipment, skills/XP, nearby NPCs, ground items, dialog/input-box, bank, timestamped messages/events), and serves a JSON-lines control socket; a thin CLI (`cli.py` via the `voidbot` wrapper) issues actions, queries, `wait <condition>`, `events --since`, and `admin "<::cmd>"`. The full handler→command spec is `docs/bot-api.md`; CLAUDE.md now mandates voidbot for all game interaction.

The wire protocol was reverse-engineered and validated against a live login capture (logging TCP proxy `tools/voidbot/capture-proxy.py`): the custom client is the server's `authenticClient == -1` path — plaintext framing, no ISAAC, no opcode rotation, RSA only at login; config + login use two separate connections; the password RSA block was reproduced byte-for-byte. No server/client code changed — voidbot only speaks the existing protocol and uses existing admin commands for test setup. Implementation language is Python (not Java) to avoid the GUI/static-init entanglement of reusing `Client_Base`, and because the plaintext protocol makes a from-scratch client tractable.

Known limitation: the bit-packed NPC AoI stream is decoded incrementally and can drop a single frame under rapid region changes; mitigated by a 3s "recently seen" NPC cache and caller-side retries. Tracking an NPC by `--server-index` is reliable; by `--id` across respawns is best-effort.

Files: `tools/voidbot/{protocol,voidbotd,cli}.py`, `tools/voidbot/voidbot`, `tools/voidbot/capture-proxy.py`, `tests/smoke.sh`, `docs/bot-api.md`, `CLAUDE.md` (Game interaction section). Reversibility: delete `tools/voidbot`, `tests/smoke.sh`, `docs/bot-api.md`, and the CLAUDE.md section. No data, schema, opcode, or gameplay change.

### 2026-06-10 — Input-box dismiss no longer soft-locks the player

Dismissing a server-driven input box (`SEND_INPUT_BOX`, e.g. the subscription vendor's signup-code prompt) via its Cancel button or by clicking outside the popup closed the dialog client-side **without replying**, leaving the plugin blocked in `Functions.inputBox` — the player couldn't move or act until the 60s timeout (reported as a total game freeze at the vendor). The client now sends an empty `INTERFACE_OPTIONS` sub-option `9` reply on either dismissal path, which unblocks the script immediately (`handleInputBoxReply` already accepted empty replies; `promptSignupCode` treats them as "no code"). No wire-format or server change.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — `sendServerPromptCancel()` helper; called from the inputX Cancel button and click-outside dismiss paths when the action is `SERVER_PROMPT`.

Reversibility: remove the helper + two calls. Follow-up candidate: ESC-key dismissal (currently no key closes the popup).

### 2026-06-10 — Small-viewport HUD fixes: chat clipping, magic panel height, compact chrome

Three fixes for the Concept HUD at the smallest (640x480) viewport preset, where most players run:

1. **Chat clipped to its frame.** Chat history/input was drawn unwrapped and unclipped (three draw paths: ALL-tab raw history, `Panel.renderScrollingList2` lists, text entry), so any line wider than the frame's ~312px inner width marched onto the world. The chat block now sets the surface clip to the frame interior (same pattern as the minimap clip) and restores it after `panelMessageTabs.drawPanel()`. Long lines truncate at the border; proper word-wrap at add-time is the follow-up.
2. **Magic & Prayer panel height.** The tab used the generic 250px fallback height while its fixed content stack (24px tab strip + 90px list + 68px description) is exactly 182px below contentTop = 250px total — content sat flush on the chrome's bottom border art. Added a content-driven branch in `voidscapePanelHeightFor` (`contentTop + 182 + 20`), giving the bottom border 4px of air. Size classes 3/4 numerically unchanged.
3. **Compact chrome at width ≤ 680** (`voidscapeCompactHud()`, keyed off the existing size-class 0): top tab buttons 54→40px (icons 34→24px, drawn from the 34px variants), location plaque 170x40→120x30, chat frame 120→96px tall, chat tab row 38→28px. All `VOIDSCAPE_TOP_TAB_SIZE` consumers (draw, panel/minimap anchors, keep-open bounds, hover/click hit-tests) now share `voidscapeTopTabSize()` so draw and hit-tests can't desync. Chat block drops from ~34% to ~27% of the 480px surface; chat history shows 4 lines instead of 5 in compact mode.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — chat clip set/restore in `drawGame`; `MAGIC_AND_PRAYER_TAB` branch in `voidscapePanelHeightFor`; `voidscapeCompactHud()`/`voidscapeTopTabSize()` + compact metrics in plaque/top-tab/chat geometry helpers.

Verified in-game at 640x480 via the AI workbench (long chat lines truncate at the frame, magic panel clears its border, compact chrome renders with matching hitboxes). Larger viewports unaffected (compact gate is size-class 0 only; 720+ keeps prior metrics). Authentic mode and Android untouched (`useVoidscapeHudSkin()` gated). Follow-ups noted from design critique: word-wrap chat lines instead of truncating, dedicated 24px top-tab icon exports (24px is currently downscaled from 34px), chat-tab spacing polish.

### 2026-06-10 — Prelaunch landing signup codes + vendor code-entry popup

Reworked the public landing page into a pure interest-list flow: a visitor submits email + desired username, the name is reserved (now also enforced at portal character creation against other emails), and a one-time `VOID-XXXX-XXXX` code is shown on-screen. The code is written to the game DB as a global `player_cache` row (`playerID=0`, `key=signup_code:<CODE>`, `value=1`). In-game, talking to the Lumbridge Void Subscription Vendor with no account-reserved card pops a text input box asking for the code; a valid entry flips the row to `2` and grants one tradable Subscription card 1602 (redeemed-before-grant, so a partial failure can't mint two cards). This shipped the client's first real `SEND_INPUT_BOX` support (custom client `10087`): the generic inputX popup renders the prompt and replies through the existing `INTERFACE_OPTIONS` packet sub-option `9` (formerly `UNUSED`) — no new wire opcode, no ordinal shifts (see `docs/subsystems/networking-protocol.md`). Plugins consume replies via the new blocking `Functions.inputBox` (multi()-style, 60s timeout, late replies dropped via a pending-flag guard). The Google button was removed from the landing; codes are minted only on the public reservations route, not for registered portal accounts. Hardening for public exposure: `PORTAL_BIND_HOST` (refuses non-loopback bind without `PORTAL_DATA_DIR`), `x-forwarded-for` trusted only from loopback peers or with `PORTAL_TRUST_PROXY=1`, and a per-IP daily signup cap (`PORTAL_SIGNUP_IP_DAILY_LIMIT`, default 10, 429 past it). New token-gated admin endpoints list/export the signup list (`GET /api/admin/signups[?format=csv]`, live redeemed status from the game DB) and resync unsynced codes (`POST /api/admin/signups/sync`).

Files touched:
- `server/src/com/openrsc/server/content/VoidSubscription.java` — signup-code cache-key constants/helpers.
- `server/src/com/openrsc/server/constants/custom/InterfaceOptions.java`, `net/rsc/parsers/impl/PayloadCustomParser.java`, `net/rsc/handlers/InterfaceOptionHandler.java`, `net/rsc/ActionSender.java`, `plugins/Functions.java` — INPUT_BOX_REPLY sub-option + blocking `inputBox` helper.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidSubscriptionVendor.java` — code-entry dialogue + redemption.
- `Client_Base/src/orsc/PacketHandler.java`, `mudclient.java`, `enumerations/InputXAction.java`, `Config.java` (`CLIENT_VERSION 10087`) — input box rendering + reply.
- `web/portal/dev-server.mjs`, `index.html`, `script.js`, `styles.css` — code mint/sync/landing UI/hardening/admin list.
- `web/portal/api-smoke.mjs`, `scripts/test-portal-api.sh` — coverage for codes, rate limit, reservation enforcement, admin list/sync.
- `docs/subsystems/subscription-cards.md`, `docs/subsystems/networking-protocol.md`, `web/portal/README.md`.

The vendor is deliberately non-exclusive: the plugin releases the NPC's busy/interaction state before showing the popup, so any number of players can hold the code prompt at once (verified with two concurrent clients); single-use safety comes from the redeem lock plus the ledger state, not NPC exclusivity. Also fixed in this slice: the portal character-creation bridge was inserting `players.group_id = 1` — which is `Group.ADMIN` — so every portal-created character was staff; it now uses the default `USER` group (10), with a regression assertion in `scripts/test-portal-api.sh`.

For the actual public launch, `PORTAL_PUBLIC_MODE=1` locks the dev server to the prelaunch surface only: static files, health, sanitized `/api/public` (no fake world stats), `POST /api/founder/reservations`, and token-gated admin; every other endpoint (register/login/dev-Google/characters/link-simulate/redeem/snapshots/downloads/manifest) returns 404, and the SPA pins navigation to the landing view. Deployment runbook (systemd + Caddy + backup + launch-day code resync) lives in `web/portal/README.md` § "Launching the public prelaunch site". Lockdown is regression-tested by a second public-mode server instance inside `scripts/test-portal-api.sh`.

Reversibility: revert the vendor prompt and portal signup-code paths; `signup_code:*` rows are plain `player_cache` data. The protocol change reuses existing packet numbers in both directions (`SEND_INPUT_BOX` 110 was already reserved; `INTERFACE_OPTIONS` sub-option 9 was unused), so authentic clients and older custom clients are unaffected — they simply never receive the input box.

### 2026-06-07 — Beta client black-window hardening

Hardened the hosted desktop beta after some Windows testers could open the launcher but saw a plain black game window. The PC client now disables the fragile Windows Java2D hardware pipelines before Swing starts, paints a Voidscape startup/connection status directly from the scaled viewport before the first game frame exists, and shows an explicit connection-failure state instead of silently leaving the canvas black. The launcher now treats manifest update failure as launch-blocking, so it will not fall through to a stale or mixed local client/cache after a partial update.

Files touched:
- `PC_Client/src/orsc/OpenRSC.java`, `ORSCApplet.java`, and `ScaledWindow.java` — Windows-safe Java2D flags plus bootstrap status/error rendering.
- `Client_Base/src/orsc/mudclient.java` — visible server-config connection status and failure message.
- `PC_Launcher/src/main/java/launcher/Voidscape/VoidscapeUpdater.java` — strict manifest update before Play and Windows-safe client launch flags.

Reversibility: revert these client/launcher startup changes and republish the beta launcher/update folder. No packet opcode, server config, client version, cache format, item/NPC definition, account data, or gameplay behavior changed.

### 2026-06-07 — Void Council starter intro

Added a one-time cinematic starter intro before the Void Path choice. New characters now enter a connected southern clearing on Void Island, click a Void Councilor to hear the void infection intro, then walk north to the existing Void Herald path area without a post-dialogue teleport. The starter route now tracks `void_intro_seen`, blocks northward movement until the council sequence finishes, reroutes unchosen players from the expanded starter island to the correct entry point, adds three council NPC definitions/spawns, marks Void Island as a non-PvP safe zone despite its wilderness coordinates, extends the custom landscape, renders cosmetic purple beams over the council props, and bumps the custom client/server version to `10070` for the client-visible NPC/cache changes.

Files touched:
- `server/src/com/openrsc/server/content/VoidStarterIntro.java`, `VoidPath.java`, `Point.java`, `WalkRequest.java`, `WorldWalkRequest.java`, and new-character login/appearance handlers — intro state, one-time dialogue, starter routing, safe-zone carve-out, and pre-dialogue path gate.
- `server/conf/server/defs/NpcDefsCustom.json`, `server/conf/server/defs/locs/NpcLocsVoidIsland.json`, and `server/src/com/openrsc/server/constants/NpcId.java` — Void Councilor definitions and spawns.
- `scripts/patch-void-enclave-landscape.py`, server/client custom landscape caches, and `Client_Base/Cache/MD5.SUM` — connected intro clearing terrain.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `Client_Base/src/orsc/mudclient.java`, `Config.java`, and matching server presets — client-side council definitions, cosmetic intro beams, starter-island wilderness overlay suppression, and client/server version `10070`.
- `docs/subsystems/void-island-starter.md` — starter flow documentation.

Reversibility: remove the intro cache key/routing hooks, council NPC ids/defs/spawns, landscape extension, cosmetic beam renderer, restore `CLIENT_VERSION`/`client_version` to `10069`, and remove the docs entry together. No packet opcode, combat formula, item definition, account schema, subscription behavior, Android-specific behavior, or launcher behavior changed.

### 2026-06-07 — Starter path refresh and settings profile polish

Tightened the first-session settings experience for new Voidscape players. The Void Herald now pushes fresh game settings and queues a save immediately after starter path selection, so the Profile panel shows the chosen path without waiting for a later settings refresh. New players default to manual camera, muted sound effects, hidden roofs, fog disabled, and kill feed enabled through client fallbacks plus server/database defaults. The desktop custom settings panel now keeps Profile as the primary tab, adds a gear-icon entry point that opens the existing custom Advanced settings modal, and moves camera angle / mouse button toggles to the top of Profile while removing total time from that panel.

Files touched:
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java` — immediate settings refresh and save after starter path choice.
- `Client_Base/src/orsc/Config.java` / `Client_Base/src/orsc/mudclient.java` — new-player client defaults, Profile settings toggles, gear icon loading, and Advanced modal entry point.
- `Client_Base/Cache/voidscape/ui/settings-gear.png` — shared launcher gear asset reused for the settings tab.
- `server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java`, `server/src/com/openrsc/server/model/entity/player/Player.java`, and core SQL schemas — new account defaults aligned across persistence paths.

Reversibility: revert the Herald refresh/save, client defaults/UI changes, gear cache asset, and DB default changes together. No packet opcode, item definition, NPC definition, combat formula, client-version, launcher binary, or Android-only behavior changed.

### 2026-06-07 — Deterministic DB patch ordering for beta deploys

Hardened database patch application after the friend beta VPS was updated with current server jars but an older `server/database` patch directory. The stale directory left `players.hairstyle` unapplied, causing current login loads to roll back halfway and kick new beta accounts during first appearance updates. The patch applier now sorts same-date SQL patches by filename after sorting by date, so additive schema patches run before later same-day data migrations. Operations docs now call out that server jars, `server/conf/`, and `server/database/` must deploy together, while preserving the live DB/log/runtime files and matching `client_version`.

Files touched:
- `server/src/com/openrsc/server/database/patches/PatchApplier.java` — deterministic date+filename patch ordering.
- `docs/OPERATIONS.md` — beta server update checklist for jars, config, migrations, DB backup, and endpoint verification.

Reversibility: revert the sorter and operations note. No protocol, gameplay, account data model, or client behavior changed; this only affects future patch application order.

### 2026-06-07 — Android wilderness player-target smoke

Verified Android wilderness player target selection against the shared player menu/action path without changing PvP mechanics. The shared client now emits `ANDROID_SMOKE_PLAYER_TARGET` and `ANDROID_SMOKE_PLAYER_ACTION` markers behind an app-private Android flag, and the existing context-menu logger now wakes up for player-target smoke. The Android smoke helper gained a focused authenticated `--only-auth-wilderness-target` pass that snapshots/restores the auth fixture's position and `group_id`, temporarily promotes it only to spawn a cinematic player at a quiet wilderness tile with a non-combat anchor, long-presses the projected player target, selects the logged attack row, asserts a shared `PLAYER_ATTACK_*` action, stops the cinematic scene, and restores the account.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated player target projection logs and player action logs for attack/item/spell menu dispatch.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — smoke-gated keys for starting/stopping the cinematic player fixture through the shared client.
- `scripts/android-smoke.sh` — focused `--only-auth-wilderness-target` mode, player target/action log assertions, fixture position/group restore, long-press attack-row selection, and screenshots `103`-`107`.
- `docs/subsystems/android-client.md` / `memory/android_emulation.md` — documented proof and checklist status.

Reversibility: remove the player-target smoke flag/logger hooks and focused smoke pass. No server behavior, packet opcode, DB schema, cache asset, combat formula, PvP rule, or normal player-menu behavior changed.

### 2026-06-07 — Android ground-loot readability smoke

Verified Android ground-item labels and rare-drop beams against the shared client render path without changing loot mechanics. The shared client now emits `ANDROID_SMOKE_GROUND_LOOT` markers behind an app-private flag for rare-beam and ground-item-label geometry, clamps ground-item labels to the classic framebuffer, and exposes a smoke-only key path that drops inventory slot 0 through the existing packet path. The Android smoke helper gained a focused authenticated `--only-auth-ground-loot` pass that snapshots/restores the auth player's inventory/cache rows, seeds a rare item fixture, drops it, asserts the beam/label bounds, and captures `102-auth-ground-loot-readable`.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated ground-loot label/beam logs, label clamping, and smoke-only drop helper using existing packet `246`.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — smoke-gated `G` key binding for the fixture drop.
- `scripts/android-smoke.sh` — focused `--only-auth-ground-loot` mode, login/foreground hardening, inventory/cache fixture snapshot/restore, beam/label assertions, and screenshot `102`.
- `docs/subsystems/android-client.md` / `memory/android_emulation.md` — documented proof and checklist status.

Reversibility: remove the ground-loot smoke flag/logger/key hooks and focused smoke pass; keep or revert the label clamp independently. No server behavior, packet opcode, DB schema, item definition, NPC definition, cache asset, client-version, or normal loot-drop mechanics changed.

### 2026-06-06 — Android settings persistence smoke

Verified Android settings/wrench behavior against the shared settings packet and SQLite persistence path without changing player-facing settings mechanics. The shared client now emits `ANDROID_SMOKE_SETTINGS` markers behind an app-private flag and exposes smoke-only key actions that open the wrench/Game tab, keep the classic options panel open by parking the mouse inside it, and toggle camera auto / one-button mouse through the existing game-setting packet. The Android smoke helper gained a focused authenticated `--only-auth-settings` pass that snapshots/restores the auth player's settings, forces a known baseline, captures open/changed/reloaded screenshots, asserts `cameraauto/onemouse/soundoff` after logout, relogs, and verifies the changed values reload into the visible panel.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android settings state logs and smoke-only open/toggle helpers that exercise existing packet `111`.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — smoke-gated key handling for settings open/toggle actions.
- `scripts/android-smoke.sh` — focused `--only-auth-settings` mode, settings DB snapshot/baseline/restore, rendered-frame waits, client-coordinate logout tap, screenshots `99`-`101`, and full-suite inclusion.
- `docs/subsystems/android-client.md` / `memory/android_emulation.md` — documented the proof and checklist status.

Reversibility: remove the settings smoke flag/logger/key hooks and focused smoke pass. No server behavior, packet opcode, DB schema, cache asset, or normal desktop/client settings UI behavior changed.

### 2026-06-06 — Android world-map smoke and PNG decoding

Fixed and verified Android world-map behavior without changing player-facing map mechanics. The shared world-map panel now falls back to Android's `ClientPort` PNG decoder when desktop `ImageIO` is unavailable, so packaged `worldmap/plane-0.png` and icon PNGs render on Android. The Android smoke helper gained a focused authenticated `--only-auth-world-map` pass that opens the map, proves zoom/pan/search/Back-close through app-private shared-client logs, clears the first-login `tutorial_appearance` cache key for DB-backed auth fixtures, and waits for rendered map frames before screenshots. Verified with `voidscape_atd35` for log assertions and `voidscape_api35` for readable visual screenshots, including Varrock search results.

Files touched:
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java` — Android PNG decode fallback, layout/debug accessors, pan/search helpers, and reusable layout preparation for smoke assertions.
- `Client_Base/src/orsc/mudclient.java` — flag-gated `ANDROID_SMOKE_WORLD_MAP` state logs, smoke-only key controls, keyboard focus handling, and Android Back-close support for the map.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — Android world-map touch capture plus smoke-gated key handling for open/zoom/pan/search.
- `scripts/android-smoke.sh` — focused world-map mode, auth fixture cleanup, rendered-frame waits, screenshots `94`-`98`, and full-suite inclusion.
- `docs/subsystems/android-client.md` / `memory/android_emulation.md` — documented the proof and checklist status.

Reversibility: remove the world-map smoke flag/logger/key hooks, the Android touch capture, and the `ClientPort` PNG fallback. No server behavior, packet opcode, DB schema, cache asset, or normal desktop world-map behavior changed.

### 2026-06-06 — Android magic/prayer panel tap smoke

Fixed and verified Android touch behavior for the shared magic/prayer side panel. Android now records short tap releases before the shared component stack can consume `mouseButtonClick`, and the magic/prayer panel consumes that recorded tap only when it falls inside the panel bounds. The smoke helper gained a focused authenticated `--only-auth-magic-prayer` pass that snapshots/restores the auth player's stats, selects Home teleport, verifies the self-cast context-menu action, switches to Prayers, toggles Thick Skin on/off, and restores the account. The smoke warns, rather than fails, if Home teleport's saved position does not move because the Android checklist target is panel selection/cast UI; the server-side self-cast teleport effect remains a separate protocol/mechanics follow-up.

Files touched:
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — records Android short-tap releases for shared-client panels that need post-component click handling.
- `Client_Base/src/orsc/mudclient.java` — consumes recorded Android taps in the magic/prayer panel, emits flag-gated `ANDROID_SMOKE_MAGIC_PRAYER_*` logs, and logs self-cast/prayer actions for smoke assertions.
- `scripts/android-smoke.sh` — focused `--only-auth-magic-prayer` mode, stat snapshot/restore, spell/prayer row retries, self-cast context-menu proof, prayer toggle assertions, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the proof, focused rerun mode, and checklist status.

Reversibility: remove the Android tap recorder/consumer, magic-prayer smoke flag/logger, and focused smoke pass. No server behavior, spell/prayer definitions, packet opcode, DB schema, client cache, or player-facing panel art changed.

### 2026-06-06 — Android worn-item interaction smoke

Verified Android wearable item touch behavior without changing equipment gameplay. The shared client now includes flag-gated Android smoke markers for inventory target wield state and dormant equipment-tab geometry/action logging, while the Android smoke helper can run a focused authenticated pass that seeds a Wooden Shield, equips it through the real inventory tap path, verifies `equipped=true`, taps it again through Voidscape's current non-equipment-tab flow, verifies `ITEM_UNEQUIP_FROM_INVENTORY`, and restores the auth player's inventory/equipment DB rows.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android equipment smoke logging and `equipped=` detail on inventory target logs.
- `scripts/android-smoke.sh` — focused `--only-auth-equipment` mode, wearable fixture seeding/restoration, current-config inventory wield/remove assertions, and dormant equipment-tab branch if `want_equipment_tab` is enabled later.
- `docs/subsystems/android-client.md` — documented the equipment/worn-item proof, focused smoke command, and checklist status.

Reversibility: remove the equipment smoke flag/logger, inventory `equipped=` log detail, and the equipment smoke pass. No server behavior, equipment packet opcode, DB schema, item definition, client cache, client-version, or player-facing equipment UI changed.

### 2026-06-06 — Android shop interaction smoke

Verified Android shop touch behavior against the shared classic shop interface without changing shop gameplay. The shared client now emits `ANDROID_SMOKE_SHOP_*` markers behind an app-private flag, and the Android smoke helper can run a focused authenticated shop pass that opens the Edgeville general store through the real NPC trade row, selects a shop slot, buys one item, sells it back, swipes over the classic non-scrollable shop grid, and restores the auth player's DB rows.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android shop smoke logging for shop geometry/state, slot selection, buy/sell packet sends, and context-menu action lists.
- `scripts/android-smoke.sh` — focused `--only-auth-shop` mode, shop fixture seeding/restoration, trade-row selection by logged action name, buy/sell assertions, non-scroll assertion, and shop screenshot capture points.
- `docs/subsystems/android-client.md` — documented the shop proof, focused smoke command, Google APIs visual-emulator ANR limitation, and checklist status.

Reversibility: remove the shop smoke flag/logger, context-menu action-list log detail, and the shop smoke pass. No server behavior, shop packet opcode, DB schema, item definition, client cache, client-version, or player-facing shop UI changed.

### 2026-06-06 — Android bank interaction smoke

Verified Android bank touch behavior against the shared custom bank interface without changing bank gameplay. The shared bank interface now emits `ANDROID_SMOKE_BANK_*` markers behind an app-private flag, and the Android smoke helper can run a focused authenticated bank pass that opens the bank chest, searches and clears search, scrolls, withdraws, saves a loadout, deposits, deposit-alls, loads the saved loadout, verifies persistence after logout, and restores the auth player's DB rows.

Files touched:
- `Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java` — flag-gated Android bank smoke logging for open/search/scroll geometry, bank actions, loadout panel, and save confirmation modal.
- `scripts/android-smoke.sh` — focused `--only-auth-bank` mode, bank fixture seeding/restoration, high-ID fixture rows to avoid live server item-ID cache collisions, log assertions, and bank screenshot capture points.
- `docs/subsystems/android-client.md` — documented the bank proof, focused smoke command, ATD black-frame limitation, and checklist status.

Reversibility: remove the bank smoke flag/logger and the bank smoke pass. No server behavior, bank packet opcode, DB schema, item definition, client cache, client-version, or player-facing bank UI changed.

### 2026-06-06 — Android chat message send smoke

Verified Android in-game chat entry and send against the shared client's normal chat packet path without changing gameplay chat behavior. The shared client now emits `ANDROID_SMOKE_CHAT_SEND` behind an app-private smoke flag after packet `216` is finished, and the Android smoke helper can run a focused authenticated pass that opens the in-game keyboard, taps the keyboard-open message entry, types a configurable message, presses Enter, and asserts the shared send log.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android chat-send smoke logging after the existing chat packet send.
- `scripts/android-smoke.sh` — chat-send smoke flag cleanup, focused `--only-auth-chat-send` mode, keyboard/message-entry coordinate knobs, log assertion, screenshots, and bad-server screenshot numbering update.
- `docs/subsystems/android-client.md` — documented the chat-send proof and marked the checklist items verified.

Reversibility: remove the chat-send smoke flag/logger, the chat-send smoke pass, and the checklist entry. No server behavior, account schema, gameplay chat rule, or opcode contract changed.

### 2026-06-06 — Android chat-tab selection smoke

Verified Android taps on the classic chat tab strip against the shared client's selected message tab without changing player-facing chat UI. The shared client now emits `ANDROID_SMOKE_CHAT_TAB` behind an app-private smoke flag whenever a bottom-strip tab tap changes `messageTabSelected`, and the Android smoke helper performs an authenticated sequence through `CHAT`, `QUEST`, `PRIVATE`, and `ALL`, asserting each shared selection and capturing screenshots.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android chat-tab selection logging inside the existing bottom-tab hitbox branch.
- `scripts/android-smoke.sh` — chat-tab smoke flag cleanup, authenticated chat-tab tap pass, focused `--only-auth-chat-tabs` rerun mode, screenshot numbering update, and environment knobs for tab coordinates/sequence.
- `docs/subsystems/android-client.md` — documented the chat-tab proof and marked the checklist item verified.

Reversibility: remove the chat-tab smoke flag/logger, the chat-tab smoke pass, and the checklist entry. No packet opcode, server behavior, account schema, or gameplay chat rule changed.

### 2026-06-06 — Android zoom gesture smoke

Verified Android vertical swipe-to-zoom against the shared client camera zoom state without changing player-facing zoom controls. The shared client now emits `ANDROID_SMOKE_ZOOM` behind an app-private smoke flag whenever Android-visible zoom state changes, and the Android smoke helper performs an authenticated vertical client-coordinate swipe after the in-game NPC projection is visible, asserts `C_LAST_ZOOM` or rendered camera zoom changed, and captures before/after screenshots.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android zoom state smoke logging in the existing camera/input update path.
- `scripts/android-smoke.sh` — zoom smoke flag cleanup, authenticated zoom swipe pass, focused `--only-auth-zoom` rerun mode, screenshot numbering update, and environment knobs for swipe coordinates/duration.
- `docs/subsystems/android-client.md` — documented the zoom proof and marked the checklist item verified.

Reversibility: remove the zoom smoke flag/logger, the zoom smoke pass, and the checklist entry. No packet opcode, server behavior, account schema, or gameplay zoom rule changed.

### 2026-06-06 — Android camera-rotate gesture smoke

Verified Android horizontal swipe-to-rotate against the shared client camera state without changing player-facing camera controls. The shared client now emits `ANDROID_SMOKE_CAMERA_ROTATE` behind an app-private smoke flag whenever camera angle/rotation changes from Android input, and the Android smoke helper performs an authenticated horizontal client-coordinate swipe after the in-game NPC projection is visible, asserts the shared camera state changed, and captures before/after screenshots. The smoke runner also cold-boots emulators without snapshots, fails authenticated passes immediately on login failure, and supports the lighter `voidscape_atd35` AOSP ATD automation AVD after the Google APIs image proved prone to System UI/Google-service ANRs in long headless runs.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android camera rotate smoke logging around the existing camera update branch.
- `scripts/android-smoke.sh` — camera smoke flag cleanup, client-coordinate swipe helper, authenticated camera rotate pass, login fail-fast handling, cold-boot emulator startup, screenshot numbering update, and environment knobs for swipe coordinates/duration.
- `docs/subsystems/android-client.md` — documented the camera rotate proof, the ATD automation AVD, and marked the checklist item verified.

Reversibility: remove the camera smoke flag/logger, the camera rotate smoke pass, and the checklist entry. No packet opcode, server behavior, account schema, or gameplay camera rule changed.

### 2026-06-06 — Android edge context-menu selection smoke

Verified Android context-menu row selection near clamped screen edges without changing gameplay behavior. The shared client now emits a generic `ANDROID_SMOKE_CONTEXT_MENU_ACTION` log for selected menu rows behind the existing app-private smoke flags, so smoke tests can assert non-NPC/object/inventory actions such as `LANDSCAPE_WALK_HERE`. The Android smoke helper now has an authenticated edge-menu pass that opens a context menu at a bottom-right client coordinate, checks the logged menu rectangle stays inside the classic framebuffer after clamping, taps the first row using the logged geometry, and asserts the expected shared action.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — flag-gated generic context-menu action logging.
- `scripts/android-smoke.sh` — edge menu fixture variables, menu geometry parser, clamp assertion, fail-fast action assertion, and edge-menu screenshots.
- `docs/subsystems/android-client.md` — documented the edge context-menu proof and marked the checklist item verified.

Reversibility: remove the generic context-menu action smoke helper, edge-menu smoke pass, and checklist entry. No packet opcode, server behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android long-press context menu

Made Android long-press reliably enter the shared client's right-click context-menu path. The touch layer now explicitly synthesizes one right-click event on long-press, de-duplicates the legacy timer and Android gesture callback, provides haptic feedback, and suppresses the release tap so players can review the `Choose option` menu before tapping a row. The shared client also logs context-menu geometry behind the existing Android smoke flags, and the smoke helper now long-presses the projected Void Herald target, screenshots the open menu, taps the first row, and asserts the selected shared action.

Files touched:
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — reliable long-press-to-right-click synthesis and release suppression.
- `Client_Base/src/orsc/mudclient.java` — flag-gated Android context-menu geometry logging.
- `scripts/android-smoke.sh` — client-coordinate long-press helper, authenticated context-menu smoke, DB timeout handling for fixture cleanup, NPC fixture positioning/fallback, fail-fast action assertions, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the context-menu smoke proof and marked the checklist item verified.

Reversibility: remove the Android long-press state changes, context-menu smoke log helper, script pass, and checklist entry. No packet opcode, server behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android item-on-scenery and item-on-NPC smoke

Verified Android item-on-target tapping against the shared object and NPC item-use paths without changing gameplay behavior. The smoke helper now has authenticated fixture passes that snapshot and restore the auth player's inventory, seed a harmless source item, select it from the Android inventory tab, tap the projected Void waystone and Void Herald targets, and assert the shared `OBJECT_USE_ITEM` and `NPC_USE_ITEM` actions in logcat. The passes also restore the auth player's position after temporarily placing them near the object/NPC fixtures.

Files touched:
- `scripts/android-smoke.sh` — item-on-target fixture variables, inventory selection helper, authenticated item-on-object and item-on-NPC passes, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the item-on-scenery/NPC smoke proof and marked the checklist item verified.

Reversibility: remove the item-on-target variables/helper/passes and uncheck the Android checklist entry. No packet opcode, server behavior, client input behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android item-on-item smoke

Verified Android item-on-item tapping against the shared client inventory selection path without changing gameplay behavior. The authenticated inventory smoke now snapshots the auth player's inventory rows, seeds a harmless two-slot fixture, taps the source item to select it, reopens the inventory tab, taps the rendered target slot, asserts the shared `ITEM_USE_ITEM` action, then waits for disconnect and restores the original inventory rows. The fixture defaults to coins plus tinderbox so it exercises the menu/packet path without triggering a crafting transformation.

Files touched:
- `scripts/android-smoke.sh` — item-on-item fixture variables, target slot seed/restore use, before/after screenshots, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the item-on-item smoke proof and marked item-on-item tapping verified.

Reversibility: remove the target-slot fixture variables and item-on-item smoke steps/checklist entry. No packet opcode, server behavior, client input behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android inventory tap smoke

Verified Android inventory item tapping against the shared client inventory menu/action path without changing gameplay behavior. The shared client now has a debug-only Android smoke hook, gated by an app-private `android-smoke-inventory-targets.flag`, that prints visible inventory slot centers and selected shared inventory actions to logcat. The smoke helper enables that flag only during an authenticated inventory pass, snapshots the auth player's inventory rows, temporarily seeds coins into slot 0, opens the inventory tab, taps the rendered slot center, asserts `ITEM_USE`, then waits for logout/disconnect and restores the original inventory rows.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android-only app-private inventory slot target logging and inventory action logging for shared item menu paths.
- `scripts/android-smoke.sh` — optional inventory slot/item/action environment variables, DB-backed inventory fixture snapshot/seed/restore, authenticated inventory tap pass, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the inventory smoke proof and marked inventory tapping verified.

Reversibility: remove the flag-gated inventory logging helpers and the inventory smoke pass/checklist entry. No packet opcode, server behavior, client input behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android scenery tap smoke

Verified Android scenery/object tapping against the shared client object menu/action path without changing gameplay behavior. The shared client now has a debug-only Android smoke hook, gated by an app-private `android-smoke-object-targets.flag`, that prints visible object screen coordinates and selected shared object actions to logcat. The smoke helper enables that flag only during an authenticated object pass, temporarily positions the auth player near a Void waystone, taps the projected object coordinate, asserts `OBJECT_COMMAND1`, then restores the player position. The auth text-entry helper also clears focused fields and clears logcat before submit so earlier negative-login smoke responses cannot poison later authenticated checks.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android-only app-private object target projection logging and object action logging for command, item-on-object, and spell-on-object paths.
- `scripts/android-smoke.sh` — optional object id/action/player-position environment variables, deterministic auth credential entry, object tap pass, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the object smoke proof and marked scenery/object tapping verified.

Reversibility: remove the flag-gated object logging helpers and the object smoke pass/checklist entry. No packet opcode, server behavior, client input behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android NPC tap smoke

Verified Android NPC tapping against the shared client NPC menu/action path without changing gameplay behavior. The shared client now has a debug-only Android smoke hook, gated by an app-private `android-smoke-npc-targets.flag`, that prints visible NPC screen coordinates and the selected shared NPC action to logcat. The smoke helper enables that flag only during an authenticated NPC pass, taps the projected Void Herald coordinate, and asserts the expected `NPC_TALK_TO` action. The logging hook also covers NPC attack, item-on-NPC, and spell-on-NPC actions when the smoke is pointed at a suitable fixture.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android-only app-private smoke flag checks, visible NPC target projection logging, and NPC action logging.
- `scripts/android-smoke.sh` — optional NPC id/action environment variables, client-coordinate tap mapping, authenticated NPC tap pass, and shifted bad-server screenshots.
- `docs/subsystems/android-client.md` — documented the NPC smoke proof and marked NPC tapping verified.

Reversibility: remove the flag-gated logging helpers and the NPC smoke pass/checklist entry. No packet opcode, server behavior, client input behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android terrain tap smoke

Verified Android terrain taps against the shared RSC walk path and made the auth smoke capable of proving it. No client input behavior needed to change: Android's screen-to-client coordinate conversion already matches the renderer's letterboxing, and single taps feed the shared `LANDSCAPE_WALK_HERE` flow. The smoke helper now captures before/after terrain-tap screenshots during an authenticated session and, when `ANDROID_SMOKE_AUTH_DB` is set, waits for logout/save and asserts the player's saved `x,y` changed.

Files touched:
- `scripts/android-smoke.sh` — optional SQLite position assertion and terrain-tap screenshots in the authenticated branch.
- `docs/subsystems/android-client.md` — documented the DB-backed movement assertion and marked terrain tap verified.

Reversibility: remove the terrain-tap screenshots/DB assertion and uncheck the checklist item. No packet opcode, server behavior, account schema, or client input behavior changed.

### 2026-06-06 — Android authenticated logout smoke

Made Android logout return to a clean, reusable login state and added authenticated emulator coverage for it. The shared client now clears Android keyboard state, pending logout state, open game tabs, menus, and advanced/settings overlays when jumping back to login. The smoke helper can optionally log into a local server with `ANDROID_SMOKE_AUTH_USER` / `ANDROID_SMOKE_AUTH_PASS`, close the welcome dialog, open the in-game settings panel, tap logout, verify the branded login home returns, and reopen Existing User with the soft keyboard. The helper also recovers from launcher-wrapper resume flakes and avoids `pipefail` false negatives when checking Android `dumpsys` output.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android logout-to-login cleanup in `jumpToLogin()`.
- `scripts/android-smoke.sh` — optional authenticated login/logout branch, GameActivity/wrapper recovery helper, and robust Android keyboard assertion.
- `docs/subsystems/android-client.md` — authenticated smoke instructions and checklist updates.

Reversibility: remove the Android-specific cleanup from `jumpToLogin()` and delete the authenticated smoke branch/checklist entries. No packet opcode, server behavior, account schema, or gameplay rule changed.

### 2026-06-06 — Android background/resume recovery

Made Android app-icon relaunch preserve an already-running game task instead of clearing it back through the updater flow. The launcher activity now exits immediately when it is opened on top of an existing task, while the game activity refreshes fullscreen, connectivity state, input focus, and rendering when it resumes or regains window focus. The smoke helper backgrounds the app from the login screen, relaunches through the launcher activity, asserts that `GameActivity` is resumed, and captures resumed keyboard/input screenshots.

Files touched:
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/ApplicationUpdater.java` — duplicate launcher-entry guard for backgrounded tasks.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` — resume/focus refresh for fullscreen, network state, focus, and redraw.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — background/resume screenshot coverage and checklist update.

Reversibility: remove the `isTaskRoot()` guard, restore `onResume()` to only hide system UI, and delete the background/resume smoke checkpoints. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android bad-server endpoint clarity

Made Android's manual server and connection-failure paths clearer for bad endpoints. The native wrapper now validates manual host/port input before launching the game, sockets use explicit connect/read timeouts instead of platform defaults, the startup server-config fetch now stops on a visible loading error instead of crashing/hanging before login, and the shared login retry loop still reports a final connection failure after failed socket attempts. Android-specific copy tells players the selected server cannot be reached and to reopen the app and choose Public. The smoke helper now drives a known-bad manual endpoint and captures the resulting startup error.

Files touched:
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/CacheUpdater.java` — manual host/port validation, visible wrapper status/toast errors, and keyboard dismissal before launch.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java`, `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` — visible Android loading-error/out-of-memory messages and repainting.
- `Client_Base/src/orsc/PacketHandler.java`, `Client_Base/src/orsc/mudclient.java` — explicit socket connect/read timeouts, startup config-fetch failure handling, Android-specific timeout/connection-failure login statuses, and retry exhaustion fix.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — bad-endpoint screenshot coverage and checklist update.

Reversibility: remove the manual validation/helper messages, restore the previous config-fetch exception and retry decrement behavior, and delete the bad-server smoke checkpoints. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android login error readability

Made Android login status messages dismiss the soft keyboard before drawing on login/recovery panels, and added an explicit missing-username/password validation path before network login. This prevents mobile players from seeing no response when submitting an incomplete login and keeps error text visible above the fields. On the compact Android existing-user panel, field labels hide while a status is present so two-line errors do not collide with labels. The Android smoke helper now clears saved credentials at the start of the run and captures a missing-password error screenshot.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android keyboard dismissal for login status and explicit missing login-field validation.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — deterministic missing-password screenshot coverage and checklist update.

Reversibility: remove the Android keyboard close from `showLoginScreenStatus()`, restore the empty-password early return, and delete the missing-password smoke checkpoint. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android login privacy simplification

Removed the desktop `Hide IP` toggle from Android's shared login panel and made Android hide last-login host/IP text by default. This keeps the cramped mobile login screen focused on account entry and credential saving while preserving the desktop privacy preference unchanged. The Android smoke helper now taps the centered Save button after the toggle is removed.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android-only `Hide IP` toggle suppression, default last-login host hiding, and centered Android Save button.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — smoke coordinate/checklist/documentation update.

Reversibility: allow `shouldOfferHideIpToggle()` on Android again and restore the old Save coordinate. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android saved login consistency

Made Android's saved-credentials path match the visible mobile login UI. Android now shows and wires the existing `Save` login button regardless of the desktop/server `S_WANT_REMEMBER` flag, loads app-private saved credentials back into the shared login fields on the next launch, rejects empty saves, and avoids accidental clicks when the remember control is absent. The smoke helper now saves credentials, relaunches the wrapper, and captures the prefilled existing-user form.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android-aware saved-credential load/draw/click gating and safer credential parsing.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — saved-credential persistence screenshot coverage and checklist update.

Reversibility: restore credential save/load gating to `S_WANT_REMEMBER` only and remove the added smoke checkpoints. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android login Back-button hardening

Made Android Back behavior predictable across the native wrapper and shared login screen. The soft-keyboard Back path now only dismisses the keyboard instead of sometimes reopening it, and hardware/system Back on a login sub-screen returns to the Voidscape welcome panel before Android exits the game activity. The smoke helper now captures server-picker Back, keyboard Back, login Back, recovery handoff, and create-account handoff screenshots so regressions are visible.

Files touched:
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java`, `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` — Android Back routing from the IME/view/activity into the shared client.
- `Client_Base/src/orsc/mudclient.java` — login-screen Android Back handling.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — expanded back-button/account-flow screenshot coverage.

Reversibility: remove the Android Back overrides and smoke checkpoints to return to the previous platform default. No packet opcode, server behavior, or account schema changed.

### 2026-06-06 — Android portal account handoff

Changed Android's public login-account path to match the beta portal-first policy. On Android, the Voidscape login home now labels the old `New User` action as `Create Account` and opens the configured account portal URL when available; the existing-user recovery action is labeled `Recover account` and opens the configured recovery URL. While the production portal URL is blank, both actions keep the player inside the login screen with a clear status instead of sending them into disabled legacy registration/recovery packet flows. Desktop client registration and recovery behavior is unchanged.

Files touched:
- `Client_Base/src/orsc/mudclient.java`, `Client_Base/src/orsc/multiclient/ClientPort.java` — Android-only portal handoff decisions, home-screen status copy, and platform URL-opening hook.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java`, `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java`, `Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java`, `PC_Client/src/orsc/osConfig.java`, `PC_Client/src/orsc/ORSCApplet.java` — platform URL launching and pre-release blank portal URL constants.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md` — account-handoff screenshot checkpoint and Android account-flow documentation.

Reversibility: restore Android's `New User` click path to `loginScreenNumber = 1`, remove the `ClientPort.openUrl` hook and portal URL constants if unused, and revert the smoke/docs wording. No packet opcode, server account schema, or registration policy changed.

### 2026-06-06 — Android branded shell and login QA loop

Made the Android player path visually match Voidscape before the game opens and enabled the shared custom login renderer on Android. The native Android updater now uses downscaled Voidscape launcher art, a dark panel, custom gold progress/play controls, dark themed server/manual dialogs, and Voidscape launcher icon densities instead of stock black screens, gray buttons, white AlertDialogs, and legacy OpenRSC icon art. The shared login background now falls back through `ClientPort` decoding on Android, and Android login/register panels use compact coordinates so the soft keyboard no longer covers the username/password fields. Added a repeatable `scripts/android-smoke.sh` helper to build/install the APK, launch `voidscape_api35` headlessly when needed, and capture wrapper/login screenshots.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — Android custom login enablement, platform PNG decode fallback, and compact Android login/register layout.
- `Android_Client/Open RSC Android Client/src/main/res/*`, `ApplicationUpdater.java`, `CacheUpdater.java` — branded native wrapper layouts, assets, drawables, progress/button/dialog styling, and dark manual server fields.
- `scripts/android-smoke.sh`, `docs/subsystems/android-client.md`, `docs/DEVELOPMENT.md` — repeatable Android emulator screenshot workflow and checklist updates.

Reversibility: restore `useVoidscapeLogin()` to desktop-only, remove the Android-specific login coordinates and ClientPort PNG fallback, restore the old updater layouts/dialogs, and delete the smoke helper. No packet opcode, server behavior, account schema, or gameplay formula changed.

### 2026-06-06 — Android beta launch hardening

Made the Android client buildable again after shared chat flag rendering picked up desktop-only AWT/ImageIO imports, and changed the Android cache launcher from developer-first server selection to a one-tap public Voidscape play path. Shared flag rendering now decodes PNG flag assets through the existing platform `ClientPort` hook and falls back to a tiny raw-pixel country-code badge when an asset is missing, keeping `Client_Base` Android-safe. Once cache setup completes, Android shows `Ready to play` and a single `Play` button targeting `5.161.114.251:43596`; long-pressing Play keeps public/emulator/LAN/manual server options available for testers.

Files touched:
- `Client_Base/src/orsc/graphics/two/GraphicsController.java` — removed direct desktop image/font dependencies from shared flag rendering and added platform decode plus raw-pixel fallback.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/CacheUpdater.java`, `orsc/osConfig.java`, `res/layout/updater.xml` — one-tap public Play target with advanced long-press server picker.
- `Android_Client/CLAUDE.md`, `docs/subsystems/client-cache.md`, `docs/subsystems/android-client.md`, `docs/CONFIG-MATRIX.md`, `docs/RELEASE-CHECKLIST.md` — documented the Android launch flow, build/test bar, and release checks.

Reversibility: restore the old server-selection dialog on cache completion and remove the public Android host constant. The renderer change should remain unless shared client code is split by platform, because Android cannot compile direct AWT/ImageIO imports.

### 2026-06-06 — Wilderness bounty mark

Added a low-friction Wilderness bounty mark system for PvP activity. The world now periodically marks one eligible Wilderness player with the existing red skull appearance byte, lets the first valid PvP attacker claim the hunt, and scores claimant kills, target counter-kills, and real escapes without changing multi-combat or forcing 1v1. Bounty payouts are blocked only for same non-local IP pairs and repeated account-pair rewards inside a 30-minute cooldown; normal combat outcomes still proceed.

Files touched:
- `server/src/com/openrsc/server/content/wilderness/BountyHunter.java`, `World.java`, `Player.java`, `Mob.java`, `RangeEvent.java`, `ThrowingEvent.java`, `SpellHandler.java` — mark lifecycle, red skull rendering, claim hooks, scoring, Void key payout, and cleanup.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — `::bounty` status command.
- `docs/subsystems/bounty-hunter.md` — subsystem behavior, hooks, scoring, and abuse controls.

Reversibility: remove the controller and combat/status hooks. No packet opcode or client change was introduced because skull type `2` already renders red in existing clients.

### 2026-06-06 — Local staff account console

Turned the retired portal staff placeholder into an active local staff console. The sidebar now exposes a Staff tab where a support/admin token can search accounts by email, inspect account/subscription/recovery/session state, review linked characters, scan recent audit and abuse-signal rows, change account status, grant or clear subscription time, grant or revoke the starter subscription card, and revoke sessions. This is still a local prototype surface over the token-gated admin API, not production RBAC.

Files touched:
- `web/portal/index.html`, `web/portal/script.js`, `web/portal/styles.css` — active Staff navigation, account lookup UI, admin action wiring, and responsive staff table/control styling.
- `web/portal/README.md` — documented the visible Staff tab scope.

Reversibility: hide the Staff nav item and section, restore the retired placeholder, and remove the script/style helpers. No game packets, server behavior, or schema application changed.

### 2026-06-06 — Portal recovery and starter-card abuse controls

Added the next account-management hardening slice to the local portal scaffold. Recovery codes can now be consumed to reset a portal password, revoke old sessions, and issue a fresh session. Starter-card anti-abuse now records salted IP/email/identity signal hashes and keeps suspicious signup clusters active while withholding only the free starter subscription card for staff review. A token-gated local admin API can inspect account state, review/lock accounts, grant/clear subscriptions, grant/revoke starter cards, and revoke sessions. Production Google OAuth and payment checkout paths are explicit `501` stubs until real providers are wired.

Files touched:
- `web/portal/dev-server.mjs` — recovery-code reset endpoint, starter-card grant decision/signals, local admin endpoints, OAuth/payment stubs, and email canonicalization.
- `web/portal/api-smoke.mjs`, `scripts/test-portal-api.sh` — smoke coverage for recovery, admin actions, production stubs, and IP-bucket starter-card review.
- `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`, `web/portal/README.md`, `web/portal/schema/README.md`, `docs/CONFIG-MATRIX.md`, `docs/RELEASE-CHECKLIST.md` — documented the recovery/support path, low-friction abuse policy, local admin guard, and production integration gaps.

Reversibility: remove the local portal endpoints/signals and their tests/docs. No game packet, opcode, OpenRSC schema application, or client login behavior changed.

### 2026-06-06 — Subscription-card redeem workbench scenario

Extended the dev-only PC workbench with `POST /scenario/subscription-card-redeem`, which finds item `1602` in the logged-in client's inventory, sends the same inventory command packet used by the real `Redeem` menu action, waits for the card to be consumed, and captures before/after screenshots. This closes the local validation loop for portal signup → game login → starter-card claim → card redemption → account-wide subscription readback without adding gameplay packets or production behavior.

Files touched:
- `PC_Client/src/orsc/WorkbenchServer.java` — redeem scenario endpoint and packet helper.
- `docs/subsystems/ai-workbench.md`, `docs/subsystems/subscription-cards.md` — documented the claim/redeem verification sequence.

Reversibility: remove the workbench endpoint and docs; no server, protocol, DB schema, or player-facing behavior depends on it.

### 2026-06-06 — Portal-first account subscriptions

Made the unreleased beta path portal-first and moved subscription time to the web-account boundary. Client packet registration is disabled in shipped configs, portal-created OpenRSC saves now receive a `web_account_id` player-cache link, subscription-card redemption extends the global `acct_sub:<webAccountId>` expiry instead of per-character cache state, and the Lumbridge vendor checks an account-level `starter_card:<webAccountId>` marker before granting the normal tradable Subscription card. The local portal bridge mirrors these markers when `PORTAL_OPENRSC_DB` is configured and reads account expiry back so the dashboard reflects in-game card redemption.

Files touched:
- `server/src/com/openrsc/server/content/VoidSubscription.java`, `server/plugins/.../SubscriptionCard.java`, `VoidSubscriptionVendor.java`, `PlayerLogin.java` — account-linked subscription expiry, linked-account redemption guard, and starter-card claim flow.
- `server/src/com/openrsc/server/database/*`, `server/*.conf`, `CharacterCreateRequest.java` — global long cache helper and portal-first registration settings/response.
- `web/portal/dev-server.mjs`, `web/portal/schema/*`, `scripts/test-portal-api.sh`, `web/portal/api-smoke.mjs` — local account marker/subscription bridge and schema/test coverage.
- `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`, `docs/subsystems/subscription-cards.md`, `docs/BETA-PLAYTEST-GUIDE.md`, `docs/CONFIG-MATRIX.md`, `docs/RELEASE-CHECKLIST.md`, `web/portal/README.md` — documented portal-first signup and account-wide subscriptions.

Reversibility: re-enable `want_packet_register`, restore per-character `void_sub_expires` reads/writes, and change the vendor marker back to a per-character key if Voidscape later chooses client-first registration or character-scoped subscriptions. No opcode or packet payload shape changed.

### 2026-06-05 — Portal-backed local game character creation

Added the first real character-creation bridge from the account portal into a configured local OpenRSC SQLite game database. When `PORTAL_OPENRSC_DB` is set, `POST /api/characters` now requires a 4-20 character game password, creates a normal `players` row plus initialized `curstats`, `maxstats`, `experience`, and `capped_experience` rows, snapshots the created character back through the existing OpenRSC read path, and records the created `playerId` in the account roster. Without a configured game DB, the portal keeps the previous preview-only behavior.

Files touched:
- `web/portal/dev-server.mjs` — real SQLite-backed character creation path with preview fallback and current-client-compatible game password hashing.
- `web/portal/index.html`, `web/portal/script.js`, `web/portal/styles.css` — game-password field, request wiring, and cleaner character creation messaging/layout.
- `scripts/test-portal-api.sh`, `web/portal/api-smoke.mjs` — fixture DB coverage that verifies created player/stat rows and linked roster state.
- `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`, `web/portal/README.md` — documented the local bridge and remaining production limits.

Reversibility: remove the SQLite write helpers and restore `/api/characters` to always call the preview creator. This does not change packets, opcodes, the PC client login flow, or production schema application. The bridge remains local/dev-only unless `PORTAL_OPENRSC_DB` is explicitly configured.

### 2026-06-05 — Simplified portal to account management

Narrowed the web portal prototype from a public content hub back to a simple account-management surface. The visible portal now starts on an Account tab with local email/password or dev Google sign-in, and the sidebar only exposes Account, Characters, Subscription, and Security. Highscores, market, activity, staff, news, referral gateway, and launch-board surfaces are retained only as hidden prototype compatibility markup/API paths for now.

Files touched:
- `web/portal/index.html`, `web/portal/styles.css` — simplified visible navigation and first screen, removed marketing/news-style clutter from the active portal experience.
- `web/portal/script.js` — routes retired hashes back to Account and guards retired render targets.
- `web/portal/README.md` — updated portal scope to reflect account/subscription focus.

Reversibility: re-enable the hidden views or restore the earlier portal navigation if public website content returns. No game packet, launcher binary, database migration, or OpenRSC login behavior changed.

### 2026-06-05 — Portal Google sign-in scaffold

Added the first Google-ready account-management slice to the web portal while keeping the classic game login untouched. The portal schema now supports external account identities, the local dev API can create or link a simulated Google identity to a normal portal account/session, the portal UI exposes a Google sign-in button on the founder/account card, and the launcher Account icon opens the portal dashboard route in the browser.

Files touched:
- `web/portal/schema/*/001_web_accounts.sql`, `scripts/test-portal-schema.sh` — nullable password hashes for OAuth-only accounts plus `web_account_identities` constraints.
- `web/portal/dev-server.mjs`, `web/portal/api-smoke.mjs` — local Google identity/session flow and smoke coverage.
- `web/portal/index.html`, `web/portal/script.js`, `web/portal/styles.css` — Google sign-in UI and security-state handling.
- `PC_Launcher/src/main/java/launcher/Voidscape/*`, `PC_Launcher/README.md`, `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`, `web/portal/README.md` — portal route handoff and architecture docs.

Reversibility: remove the dev endpoint/UI, drop `web_account_identities`, restore `password_hash NOT NULL`, and point launcher Account back to the base portal URL. No packet, opcode, game-login, client-cache, or OpenRSC player-schema behavior changed.

### 2026-06-05 — Beta command and playtest documentation hardening

Aligned the current beta-facing docs with the live Voidscape build after removing abandoned prototypes and adding several tuning/test helpers. The player/admin command reference, friend beta playtest guide, and client cache docs now describe the simplified global chat flow, rested XP status command, balance telemetry commands, local client version `10069`, and current local port handling instead of stale prototype references.

Files touched:
- `Commands.md`, `docs/BETA-PLAYTEST-GUIDE.md` — added missing player/admin command entries and removed stale guidance around scrapped systems.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — aligned in-game command help text with the live commands.
- `docs/subsystems/client-cache.md`, `docs/DEVELOPMENT.md`, `docs/SERVER-PRESETS.md` — updated client version, local connection examples, and current split combat/skilling XP config keys.

Reversibility: documentation/help-text only; no schema, packet, cache, gameplay, launcher, or database behavior changed.

### 2026-06-05 — Removed experimental ranked arena

Scrapped the safe ranked arena prototype before release after local two-client testing showed the command-driven duel wrapper was brittle and did not feel worth keeping. Removed the arena command surface, Elo/cache helper, duel/death hooks, command help entry, and active-system docs so PvP and normal duels return to the existing OpenRSC behavior.

Files touched:
- `server/src/com/openrsc/server/content/VoidArenaRankings.java` — removed the ranked-arena helper and match state.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — removed `::arena`/alias command handling and command-list help text.
- `server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java`, `server/src/com/openrsc/server/model/entity/player/Player.java` — removed ranked-arena exceptions from PvP XP and duel-death cleanup.
- `docs/subsystems/progression-balance.md`, `docs/CODEMAP.md` — removed active ranked-arena documentation.

Reversibility: restore the removed helper and references from git history if a better arena design returns. Existing local player-cache keys such as `void_arena_rating` are harmless if left behind. No DB schema, packet, opcode, client cache, or launcher behavior changed.

### 2026-06-05 — Removed experimental expeditions, recycler, and bounty board

Scrapped three gameplay-system prototypes before release: Void Expeditions, Void Recycler/Void Surge, and Void Bounty Board. Their commands, server hooks, client/server scenery definitions, Void Enclave hopper placement, and helper classes were removed so the current balance baseline stays focused on rested XP, milestone rewards, guaranteed resources, subscriptions, and core Wilderness behavior.

Files touched:
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — removed `::expedition`, `::recycle`, and `::bounty` command branches/list entries.
- `server/src/com/openrsc/server/login/LoginRequest.java`, `server/src/com/openrsc/server/model/entity/player/Player.java`, `server/src/com/openrsc/server/model/entity/npc/Npc.java` — removed login, XP/drop, and kill/death hooks.
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/conf/server/defs/GameObjectDef.xml`, `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json` — removed the Void Recycler object definition and placement.
- `docs/subsystems/progression-balance.md`, `docs/CODEMAP.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/networking-protocol.md` — removed active-system documentation for the scrapped features.

Reversibility: restore the deleted helpers and hooks from git history if one of these ideas returns. Existing player-cache keys from local testing are harmless if left behind. No database schema, packet, opcode, launcher, or beta-server operation changed.

### 2026-06-05 — Simplified global chat with IP country flags

Simplified global chat output to `username: message` styling and added an optional country-flag icon beside the sender name. The server resolves the sender's current public IP to a two-letter country code, caches it per IP/player, and sends a compact `@flg@CC` text token before the green username. The updated custom client renders that token as a small country flag icon and exposes a Chat settings toggle for players who want to hide their own flag; the toggle is on by default.
Local development can set `global_chat_local_country_code` to force a localhost flag for rendering tests without changing production IP behavior.

Files touched:
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java`, `server/src/com/openrsc/server/model/world/World.java` — simplified global-chat formatting and reused the same flag prefix for direct and queued global messages.
- `server/src/com/openrsc/server/content/GlobalChatIpFlags.java`, `ServerConfiguration.java`, `ActionSender.java`, `GameSettingHandler.java`, `Player.java`, `GameSettingsStruct.java`, `PayloadCustomGenerator.java` — IP lookup, cache persistence, config keys, settings packet byte, and player toggle handling.
- `Client_Base/src/orsc/graphics/two/GraphicsController.java`, `Client_Base/src/orsc/mudclient.java`, `PacketHandler.java`, `Config.java` — flag token rendering, Advanced Settings toggle, settings readback, and client/server version `10069`.
- `Client_Base/Cache/voidscape/flags/` — tiny country-flag PNG assets sourced from the MIT-licensed `lipis/flag-icons` GitHub project, with source SVG and license text included.
- `server/*.conf`, `docs/subsystems/networking-protocol.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — version/config documentation.

Reversibility: disable `want_global_chat_country_flags` to suppress all flag prefixes, or revert the listed files and downgrade the client/server version after removing the appended settings byte.

### 2026-06-05 — Trial Voidscape skin and clothing palettes

Added a first visual trial for Voidscape-only top, bottom, and skin colours in the character designer. The client appends eight custom clothing colours (`Void`, `Frost`, `Blood`, `Ember`, `Gold`, `Toxic`, `Moon`, `Coal`) and five trial skin tones (`Dawn`, `Rose`, `Bronze`, `Umber`, `Ash`). Hair keeps the bright prototype palette, skin remains custom-only, and clothing now cycles through both the classic colours and the muted Voidscape colour families. The custom clothing ramp uses softer five-step shading so full shirts and pants are less bright/high-contrast. Trial skin tones use a skin-specific three-shade ramp, so the preview and live paperdoll avoid flat colour fills.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — appended palettes, named selector values, restored classic top/bottom selector cycling, and restricted skin selector cycling to the grounded trial range.
- `Client_Base/src/orsc/graphics/two/GraphicsController.java` — generalized the custom luster mapping, added a custom skin-tone ramp, and gave clothing its own softer five-step shade ramp.
- `server/src/com/openrsc/server/model/PlayerAppearance.java`, `server/src/com/openrsc/server/service/PlayerService.java`, `server/src/com/openrsc/server/constants/Constants.java` — expanded accepted clothing/skin bounds, unlocked trial skin tones, fixed invalid-load fallback defaults, and aligned avatar colour arrays.
- `server/database/{mysql,sqlite}/{core,retro}.*` — updated fresh-database appearance defaults to valid Voidscape palette indexes.
- `Client_Base/src/orsc/Config.java`, `server/*.conf` — client/server version `10068`.
- `docs/subsystems/networking-protocol.md`, `docs/subsystems/player-appearance-rendering.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — documented the versioned visual trial.

Reversibility: restore skin selector cycling across the full skin array, remove the appended custom palette entries and renderer ramps, lower the server validation caps, and downgrade the client/server version after deciding not to ship the trial palette.

### 2026-06-05 — Custom-only hair selector

Restricted the character designer's hair selector to Voidscape's custom palette only. The client now cycles hair colours `10..17` (`Void`, `Frost`, `Blood`, `Ember`, `Gold`, `Toxic`, `Moon`, `Coal`) and normalizes any old/default value to `Void` when the appearance screen opens. The server validates appearance updates against the same range, and a database patch migrates saved authentic hair colours to nearby Voidscape families.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — hair selector wrap logic now uses only `10..17`; default appearance hair colour is `Void`.
- `server/src/com/openrsc/server/model/PlayerAppearance.java` — appearance validation now requires hair colour `10..17`.
- `server/database/{mysql,sqlite}/patches/2026_06_05_voidscape_hair_colours_only.sql` — migrates saved old hair colours to custom colour indexes.
- `Client_Base/src/orsc/Config.java`, `server/*.conf` — client/server version `10064`.
- `docs/subsystems/networking-protocol.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — documented the versioned selector/validation change.

Reversibility: restore selector cycling across `playerHairColors.length`, loosen server validation back to `0..17`, remove or avoid the migration patch on fresh databases, and downgrade the client/server version after deciding old colours are selectable again.

### 2026-06-05 — Voidscape hair luster ramps

Restored the richer look of the custom hair colours on every default head/beard shape by changing the client renderer's grayscale hair-mask tinting for Voidscape colours. Instead of flat-multiplying each classic sprite shade directly, the eight custom colours now normalize each default head's grayscale hair-mask clusters to the same three neutral shades used by the side-swept PNG overlay (`#5c5c5c`, `#8a8a8a`, `#ffffff`) and then apply the exact same colour transform. This preserves authentic head silhouettes while making Void, Frost, Blood, Ember, Gold, Toxic, Moon, and Coal match the earlier swept-hair prototype across short, long, alternate-short, and bearded heads.

Files touched:
- `Client_Base/src/orsc/graphics/two/GraphicsController.java` — added swept-overlay shade mapping for the eight custom hair colours in both one-mask and two-mask sprite paths.
- `Client_Base/src/orsc/Config.java`, `server/*.conf` — client/server version `10063`.
- `docs/subsystems/networking-protocol.md`, `docs/subsystems/player-appearance-rendering.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — documented the renderer-only visual upgrade.

Reversibility: remove the swept-overlay shade helper and fall back to the original grayscale multiply path, then downgrade the client/server version once no players depend on the richer custom palette rendering.

### 2026-06-05 — Palette-first classic head colours

Disabled the experimental modern PNG hair style selector in the Voidscape character designer so the shipped flow is now unambiguous: `Head Type` chooses the default OpenRSC head/beard shape, and `Hair` chooses the colour, including Void, Frost, Blood, Ember, Gold, Toxic, Moon, and Coal. The default head sprites already use RSC's grayscale hair mask, so no live sprite archive rewrite was needed; clamping `hairStyle` back to Classic prevents the fixed swept overlay from making colours look tied to one head shape.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — removed the visible Style row and disabled its hidden click targets on the Voidscape appearance panel.
- `Client_Base/src/orsc/Config.java`, `server/src/com/openrsc/server/model/PlayerAppearance.java`, `server/*.conf` — client/server version `10062` and modern hair style cap set to `0`.
- `server/database/{mysql,sqlite}/patches/2026_06_05_palette_first_hair_styles.sql` — normalizes previously saved overlay selections back to Classic after the older style-colour migration.
- `docs/subsystems/networking-protocol.md`, `docs/subsystems/player-appearance-rendering.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — documented the palette-first shipped state.

Reversibility: restore a positive `MAX_MODERN_HAIR_STYLE`, re-enable the Style selector/click targets, and bump the client/server version after deciding which overlay shapes should support every default head type.

### 2026-06-05 — Modern hair style and colour split

Reworked the first PNG-backed modern hair set so shape and colour are separate. The character designer now treats the bottom selector as `Style` (`Classic`, `Swept`) and the top-right selector as the named hair colour (`Void`, `Frost`, `Blood`, `Ember`, `Gold`, `Toxic`, `Moon`, `Coal`, plus the authentic colours). The swept PNG hair frames were converted from baked purple art into neutral shaded masks, and the renderer tints those masks with the existing `hairColour` palette at draw time. This means every future modern PNG hairstyle automatically supports every hair colour without generating one asset folder per colour.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — hair-colour labels, style label, and runtime tinting for modern PNG hair overlays.
- `Client_Base/Cache/voidscape/hair/style_01/` — converted the swept style to neutral shaded PNG frames; removed duplicate colour-as-style folders `style_02..08`.
- `Client_Base/src/orsc/Config.java`, `server/src/com/openrsc/server/model/PlayerAppearance.java`, `server/*.conf` — client/server version `10061` and modern style cap reduced to the one real shape currently shipped.
- `server/database/{mysql,sqlite}/patches/2026_06_05_modern_hair_styles_use_palette_colours.sql` — migrates legacy saved colour-style rows to `hairstyle = 1` plus the matching `haircolour`.
- `docs/subsystems/networking-protocol.md`, `docs/subsystems/player-appearance-rendering.md`, `docs/recipes/add-custom-hairstyle.md`, `tools/hairstyle-art/README.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — documented the shape/colour split.

Reversibility: restore colour-specific style folders, set `MAX_MODERN_HAIR_STYLE` back to `8`, remove runtime tinting, and downgrade the client/server version after converting any saved `haircolour`/`hairstyle` combinations back to the older colour-as-style representation.

### 2026-06-05 — Voidscape default hair and beard colours

Added the eight Voidscape recolour themes to the classic player hair palette so every default OpenRSC hairstyle and beard-capable head sprite can use Void, Frost, Blood, Ember, Gold, Toxic, Moon, and Coal without relying on the newer PNG overlay shapes. The server appearance validator now accepts the expanded hair colour index range, and the client/server version moved to `10059` so stale clients do not save or receive palette indexes they cannot display.

Follow-up: constrained the PNG-backed modern hair overlays to the single compatible swept-hair base head and moved the client/server version to `10060`. Changing a default head/beard or gender in the character designer now resets the overlay selector to Classic, which keeps the new palette colours from being masked by a fixed overlay shape.

Files touched:
- `Client_Base/src/orsc/mudclient.java` — appended the eight custom colours to `playerHairColors` and guarded modern overlay drawing to the compatible base head.
- `server/src/com/openrsc/server/model/PlayerAppearance.java` — raised the accepted hair colour cap to `0..17` and clamps incompatible saved overlay/head combinations back to Classic.
- `Client_Base/src/orsc/Config.java`, `server/*.conf`, `docs/subsystems/networking-protocol.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — client/server version sync and docs.

Reversibility: change any saved player hair colour values above `9` back to an authentic palette value, remove the eight appended client colours, restore the server validation cap to `9`, and downgrade the client/server version once no clients can select the removed colours.

### 2026-06-05 — Starter modern hair recolor set

Expanded the new PNG-backed hair overlay system from one proof style to eight selectable recolor styles: Void, Frost, Blood, Ember, Gold, Toxic, Moon, and Coal. After testing larger custom spiky shapes, the recolor-only direction proved much stronger: every style keeps the readable swept silhouette and uses ARGB palette/halo differences for identity instead of changing the frame shape. The character-design panel now shows named hair styles and client/server style caps accept values `0..8`.

Files touched:
- `Client_Base/Cache/voidscape/hair/style_{02..08}/` — new 18-frame ARGB overlay PNGs plus placement sidecars.
- `Client_Base/src/orsc/Config.java`, `Client_Base/src/orsc/mudclient.java` — client version/style cap and named selector labels.
- `server/src/com/openrsc/server/model/PlayerAppearance.java`, `server/*.conf` — server style clamp and `client_version` sync.
- `docs/subsystems/networking-protocol.md`, `docs/CONFIG-MATRIX.md`, `docs/subsystems/subscription-cards.md` — version notes.

Reversibility: set saved `hairstyle` values above `1` back to `0` or `1`, remove the extra style cache folders, restore max style to `1`, and downgrade the client/server version once no clients can select the removed styles.

### 2026-06-05 — Modern PNG hair overlay foundation

Added a latest-client-only character hair overlay path so Voidscape can ship full ARGB PNG hairstyles without forcing every new concept through the old indexed RSC head-sprite palette. Player appearance now persists a clamped `hairStyle`, custom appearance packet `235` sends it as an 11th byte, and custom `SEND_UPDATE_PLAYERS` type `5` appends it for clients `10057+`. The PC client exposes a `Classic` / starter style selector on the Voidscape character-design panel, previews the overlay on the locked head frames, and draws style PNG frames from `Client_Base/Cache/voidscape/hair/style_XX/` over the real head animation during player rendering.

Files touched:
- `server/src/com/openrsc/server/model/PlayerAppearance.java`, `PlayerData.java`, `PlayerService.java`, `GameDatabase.java`, MySQL load/save paths, `PayloadCustomParser.java`, `PlayerAppearanceUpdater.java`, `GameStateUpdater.java` — persisted and transmitted `hairStyle`.
- `server/database/{mysql,sqlite}/patches/2026_06_05_add_player_hairstyle.sql` — additive `hairstyle` column migration.
- `Client_Base/src/orsc/Config.java`, `ORSCharacter.java`, `PacketHandler.java`, `mudclient.java`, `VoidscapeHairOverlay.java`, `graphics/two/GraphicsController.java`, `util/PngSpriteLoader.java` — client version bump, style decode, PNG loading, and ARGB overlay drawing.
- `Client_Base/Cache/voidscape/hair/style_01/` — starter modern hair overlay frames and placement sidecars.
- `server/*.conf`, `docs/subsystems/networking-protocol.md`, `docs/CONFIG-MATRIX.md`, `docs/CODEMAP.md` — version and protocol documentation.

Reversibility: set all saved `hairstyle` values to `0`, stop sending/reading the extra style byte, remove `VoidscapeHairOverlay` and the cache directory, then downgrade `CLIENT_VERSION`/server `client_version` back to the previous compatible value. The DB column is additive and harmless if left behind.

### 2026-06-05 — Manual wig editor import placement

Updated the hairstyle manual wig editor so imported PNG hair art is not immediately squeezed into the current tiny RSC frame. Import now trims faint transparent padding with an alpha threshold, opens a placement overlay, and lets the artist fit, scale, nudge, apply, or cancel before the result is committed and normalized to legal RSC hair-mask pixels. This keeps RSC's original head frame sizes and recolour constraints intact while making generated or external hair concepts usable as hand-placed reference stamps.

Files touched:
- `tools/hairstyle-art/hairstyle_tool.py` — import placement UI and thresholded alpha trim in the web editor.
- `tools/hairstyle-art/README.md`, `docs/recipes/add-custom-hairstyle.md` — usage notes.

Reversibility: remove the placement controls and restore the old direct `drawImage(..., frame.width, frame.height)` import path. No game server, client cache, packet, opcode, or launcher behavior changed.

### 2026-06-05 — Guaranteed resource nodes

Added transient minimum-yield protection for normal rocks and trees, plus a session-local dry-streak breaker for normal gathering actions. Mining rocks and woodcutting trees now stay available until that node instance has produced at least three successful harvests, then return to the normal depletion/respawn rules. After four consecutive eligible failures on the same mining ore, modern woodcutting log, or normal fishing spot/action, the next attempt succeeds and resets that player's streak. The mechanic keeps all existing tool, level, fatigue, batch timing, XP, post-minimum depletion, and shared-resource checks, and deliberately leaves tutorial gathering and big-net fishing on their original special-case logic. Admins can seed a current-player test streak with `::gatherstreak`.

Files touched:
- `server/src/com/openrsc/server/content/GuaranteedResources.java` — transient per-player failure counters, node yield counters, and guarantee notification.
- `server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java`, `woodcutting/Woodcutting.java`, `fishing/Fishing.java` — success/failure hooks for normal gathering attempts plus minimum-yield depletion protection for rocks/trees.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java` — admin-only diagnostic seeding command.
- `docs/subsystems/progression-balance.md`, `docs/CODEMAP.md` — subsystem documentation.

Reversibility: remove the `GuaranteedResources` calls from the three skill scripts and delete the helper. No DB schema, packet, opcode, client cache, client-version, launcher, or beta-server operation changed.

### 2026-06-05 — Rested XP session window

Added a player-cache-backed Rested XP window for beta pacing. On login, eligible offline time accrues rested time after 30 minutes away at one rested second per offline second, capped at 45 minutes. Normal non-quest training awards 1.5x XP while rested time remains, and the rested timer drains by real logged-in session time so the first 45 minutes of the next session are boosted until the pool is empty. This helps returning players catch up without changing the base global combat/skilling rates, subscription rates, drop tables, or per-skill multipliers. `::rested` / `::restedxp` reports the current timer and tuning numbers.

Files touched:
- `server/src/com/openrsc/server/content/RestedExperience.java` — timer accrual, session-time drain, 1.5x XP bonus, display, and cache-key handling.
- `server/src/com/openrsc/server/login/LoginRequest.java`, `server/src/com/openrsc/server/login/PlayerSaveRequest.java` — accrue on login and record last-seen time on logout saves.
- `server/src/com/openrsc/server/model/entity/player/Player.java` — spend rested pool after normal XP multipliers and before skill XP is applied.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — `::rested` status command.
- `docs/subsystems/progression-balance.md`, `docs/CODEMAP.md` — subsystem documentation.

Reversibility: remove the `RestedExperience` calls from login/logout/XP award paths and the `::rested` branch. Existing `rested_xp_pool` / `rested_xp_last_seen` cache rows are harmless if left behind. `rested_xp_pool` stores seconds in this version and is clamped to the 45-minute cap on read. No DB schema, packet, opcode, client cache, client-version, or Android-specific change.

### 2026-06-05 — Make-All cooking and smelting menus

Added explicit Make-All style menu choices for two high-click production flows. With `batch_progression` enabled, using cookable food on a range/fire now asks `Cook 1`, `Cook All`, or `Cancel`; using ore on a furnace now asks `Smelt 1`, `Smelt 5`, `Smelt 10`, `Smelt All`, or `Cancel`. Each selected repeat still runs through the existing per-item script delays, fatigue checks, success/burn/failure rolls, XP grants, and progress-bar updates, so this removes repeated clicks without turning production into instant bulk conversion.

Files touched:
- `server/plugins/com/openrsc/server/plugins/authentic/skills/cooking/ObjectCooking.java` — explicit cooking repeat menu for normal cookable items.
- `server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java` — explicit furnace smelting repeat menu and corrected maximum repeat calculation for multi-ore bars.

Reversibility: restore the previous auto-repeat count calculation in `ObjectCooking` and `Smelting`. No DB schema, packet, opcode, client cache, client-version, or Android-specific change.

### 2026-06-05 — Resizable desktop UI scaling

Improved the PC client's existing Swing scaling shell so fresh desktop installs still open at the original RSC size, but players can drag the window larger and have the whole rendered game/UI scale with it. The applet's logical canvas remains `512x346` in the default window-follow mode, preserving classic layout and click coordinates while presenting a larger, crisp viewport on modern monitors. Existing saved fixed scaling choices in `clientSettings.conf` are preserved and only clamped if they no longer fit the active monitor.

Details:
- `ScaledWindow` now builds only scale factors that fit the monitor, keeps default startup at classic size, scales/centers the rendered image when the desktop window is stretched, and explicitly uses nearest-neighbor rendering hints for integer scaling.
- `OpenRSC` detects whether a saved scaling scalar exists before choosing default window-follow mode.
- The settings wrench now labels the controls as `UI scale` and `Scale filter` with `Window`, `Crisp`, `Soft`, and `Smooth` names.
- Scaling saves now preserve other keys in `clientSettings.conf` instead of replacing the file with only scaling keys.

Files touched:
- `PC_Client/src/orsc/OpenRSC.java`, `PC_Client/src/orsc/ScaledWindow.java` — PC startup/default scaling and crisp render hints.
- `Client_Base/src/orsc/mudclient.java` — settings labels and safer scaling-setting persistence.
- `docs/subsystems/client-cache.md`, `docs/CODEMAP.md` — documentation.

Reversibility: restore the old viewport-resizing behavior in `ScaledWindow`, remove the window-follow mode in `mudclient`, and restore the old settings labels. No DB schema, packet, opcode, cache format, server behavior, Android renderer, or client-version change.

### 2026-06-05 — Progression balance rewards and telemetry

Added a first balance-tuning slice for beta progression. Void Island path boosts are now early-game accelerators instead of permanent account-wide advantages: each selected path still grants 2x XP in its listed skills, but only until the boosted skill reaches level 50. Level-up handling now pays modest coin milestone rewards for configured skill-level and total-level thresholds using existing `player_cache` keys to prevent repeats.

Added `BalanceTelemetry`, an in-memory admin report surface for beta tuning. It records effective XP awarded by skill/player, NPC kills, NPC item quantities, and rare-drop events during the current telemetry window. Admins can inspect it with `::balancereport [xp|players|npcs|drops]` and reset the window with `::balancereport reset`.

Files touched:
- `server/src/com/openrsc/server/content/VoidPath.java`, `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java` — starter boost cap and onboarding copy.
- `server/src/com/openrsc/server/content/ProgressionMilestones.java`, `server/src/com/openrsc/server/model/Skills.java` — milestone reward hooks.
- `server/src/com/openrsc/server/content/BalanceTelemetry.java`, `server/src/com/openrsc/server/model/entity/npc/Npc.java`, `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java` — beta telemetry counters and admin reports.
- `docs/subsystems/void-island-starter.md`, `docs/subsystems/progression-balance.md`, `docs/CODEMAP.md` — subsystem documentation.

Reversibility: restore `VoidPath.boostsSkill(...)` to ignore the level cap, remove the `ProgressionMilestones` call from `Skills.addExperience(...)`, and remove the telemetry hooks/admin command. No DB schema, packet, opcode, client cache, or client-version change.

### 2026-06-04 — Temporary beta auto-admin accounts

Newly created characters are temporarily inserted with `Group.ADMIN` during the friend beta so trusted testers can immediately use teleport, item, stat, and scenario commands without manual promotion. This only affects new account creation after deployment; existing characters keep their saved `group_id`.

Files touched:
- `server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java`, `server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java` — explicit `group_id` write during player creation.
- `docs/BETA-PLAYTEST-GUIDE.md`, `docs/OPERATIONS.md` — beta-only auto-admin warning and updated tester flow.

Reversibility: remove `group_id` from the create-player insert and the extra `Group.ADMIN` bind parameter, or replace it with `Group.USER`. No DB schema, packet, opcode, or client cache change.

### 2026-06-04 — Friend beta launcher packaging

Added a hosted-server packaging path for private friend betas. `scripts/package-friend-beta.sh` builds the PC client/cache update payload, writes a SHA-256 manifest, embeds the target server host/port plus manifest URL into the launcher jar, and emits `dist/friend-beta/VoidscapeLauncher.jar` for testers. The launcher now reads bundled or sidecar `voidscape-launcher.properties` after system/env overrides and runs the update manifest automatically when Play is clicked, so a fresh tester install can download the client/cache before launching.

The packaged client now resolves the Voidscape login background and world-map files from `Config.F_CACHE_DIR` instead of hard-coded `Cache/...` paths. The launcher runs the client with the downloaded cache directory as its working directory, where `Config.F_CACHE_DIR` falls back to `.`, so hard-coded nested cache paths produced blank login/world-map assets in the beta package.

Files touched:
- `PC_Launcher/src/main/java/launcher/Voidscape/VoidscapeLauncherConfig.java` — bundled/sidecar launcher settings and blank release-safe website/account URL defaults.
- `PC_Launcher/src/main/java/launcher/Voidscape/VoidscapeUpdater.java` — Play runs manifest verification/download before launching when configured.
- `Client_Base/src/orsc/mudclient.java`, `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java` — packaged-cache asset path fixes.
- `scripts/package-friend-beta.sh` — hosted beta package builder.
- `docs/BETA-PLAYTEST-GUIDE.md`, `docs/OPERATIONS.md`, `PC_Launcher/README.md` — hosted friend beta workflow docs.

Reversibility: remove the package script and restore launcher config to env/system-only endpoint lookup. No game server behavior, DB schema, opcode, packet shape, or client cache format changed.

### 2026-06-04 — Voidscape desktop launcher/updater shell

Replaced the visible PC launcher flow with a Voidscape-specific Swing launcher instead of the old OpenRSC multi-server picker. The new shell uses Voidscape artwork, a large Play action, Update/Repair/Settings controls, account/website links, status/progress, and compact news/subscription cards. Runtime files now default to `~/.voidscape/client` so double-clicked launchers do not scatter cache files into arbitrary working directories; `--portable` preserves the legacy `./Cache` behavior for local testing.

The launcher seeds local dev builds from `Client_Base/Open_RSC_Client.jar` and copies local cache assets from `Client_Base/Cache` while preserving player-specific files. A safer updater foundation was added around SHA-256 manifest properties, temp downloads, and atomic replacement; unlike the competitor launcher inspected during design, it does not weaken TLS/certificate validation.

Files touched:
- `PC_Launcher/src/main/java/launcher/Main.java`, `Launcher.java` — route startup to the Voidscape shell and change the default cache path.
- `PC_Launcher/src/main/java/launcher/Voidscape/` — new launcher config, cache/updater, and Swing UI.
- `PC_Launcher/src/main/resources/images/voidscape/` — launcher artwork and icon assets.
- `scripts/run-launcher.sh` — canonical build/run wrapper.
- `docs/subsystems/client-cache.md`, `docs/DEVELOPMENT.md` — launcher/cache docs.

Reversibility: route `Launcher.initializeLauncher()` back to `launcher.Fancy.MainWindow` and restore `Main.configFileLocation = "Cache"`. No database or protocol migration.

### 2026-06-03 — Player title catalog dialogue hub

Reworked `::titles` from a dense title list into a dialogue-style hub with category pages. The command now offers unlocked, all, unique, common, and rarest title views; catalog pages show 10 compact rows with previous/next navigation, and selecting a locked title reports the relevant requirement or unique-title owner. The custom PC client recognizes these title menu payloads and renders them as a centered black modal with category tabs, replacing the stock cyan top-left options menu while still sending ordinary menu replies. Added a server-side rarity score/label on `PlayerTitle` so the rarest view is intentionally ordered without adding client protocol or schema changes.

Files touched:
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java` — title hub, category aliases, 10-row paged catalog, compact row labels.
- `server/src/com/openrsc/server/content/PlayerTitle.java` — rarity score/label helpers.
- `Client_Base/src/orsc/mudclient.java` — custom black-box title modal, category tabs, page buttons, and side-panel click shielding.
- `docs/subsystems/player-titles.md` — command behavior update.

Reversibility: revert the command helpers and rarity helpers. No data migration and no opcode/client-version change.

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

**External API key hygiene**: image-generation tooling reads API credentials only from gitignored local environment files. No API key is committed to the repository or passed through checked-in scripts.

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
| voidweb | 58 | 3283 | DoorDef "Void Web" (id 217) — four cuttable perimeter gate webs |
| voidwindow | 59 | 3284 | DoorDef "Void Window" (id 218) — sanctum E/W walls |

Plus two orphan sprites at index 2761 (a "Void Sigil" item-icon attempt before realising the user wanted landscape assets, not items). Temporary `Authentic_Sprites.orsc.bak*` snapshots were created during local sprite-packing work but are not part of the committed artifact set.

**DoorDef structure surprise** (`server/src/.../external/DoorDef.java` vs `Client_Base/.../entityhandling/defs/DoorDef.java`). Server-side field name is `modelVar1` (matches the XML element); client-side field name is `wallObjectHeight`. The XStream-style XML deserializer maps element names to server fields directly, so the XML stays valid. The client constructor's 7th positional arg is `wallObjectHeight` — same value, different name. modelVar1=192 = standard interior wall height, 275 = Highwall, 70 = battlement. Knowing that the integer is a *height* (not a model-id) is what made it possible to set custom-textured walls at the right scale (e.g. `Void Highwall` uses 275 to match the perimeter Highwall it replaces).

**Landscape texture pipeline (`mudclient.java:14738-14806`)**. Texture sprites live at `spriteTexture + i` in the sprite archive. The engine quantizes to a 256-color palette per texture — `dictionary[0] = 0xFF00FF` (magenta) is the transparency entry, and any `RGB(0,0,0)` pixel in the source sprite gets rewritten to magenta before quantization, becoming transparent at render time. **Implication for our textures**: prompts must explicitly forbid pure black (use `RGB(15,5,25)` or darker indigo for shadows instead) or the engine will punch transparent holes through the wall. All five AI-generated textures pass a "0 pure-black pixels" check. The void web texture is the *exception*: it deliberately keeps `RGB(0,0,0)` between the strands so the gaps are see-through — but that texture is now orphan.

**Custom `GameObjectDef` "Healing pool" (id 1296)**. Reuses the existing `fountain` model but with `command1=Drink` so the right-click menu actually appears. Discovery showed that the standard fountain (id 26) has `command1=WalkTo` — and `mudclient.java:7789` explicitly skips menu-adding for `WalkTo` objects, meaning left-click never sends an action packet to the server, meaning no `OpLoc` trigger fires. That's why the original `VoidEnclaveHealingPool` plugin "did nothing" before the GameObjectDef append. Mirrored on client `EntityHandler.loadGameObjectDefinitions B()` per the Edgar precedent. Bumped `CLIENT_VERSION` 10012 → 10013 at this step.

**Bank chest F2P bypass** (`ShantayPassNpcs.java:458-465`). `BANK_CHEST` (id 942) is normally gated by `MEMBER_WORLD` — F2P worlds get "you must be on a members' world to do that" instead of opening the bank. We'd reused id 942 for the enclave bank chest, so on voidscape (F2P) it was rejecting players. Patched to bypass the member check when the chest's world coords are inside the enclave footprint. Same pattern works for any future F2P-bypass-on-coords needs.

**Outcome — final aesthetic state**. Outer perimeter is tall AI-textured void stone (`voidouter`) with 4 web-sealed gates (custom Void web id 217, JSON-registered, cuttable). Sanctum interior walls are AI-textured void brick (`voidbricks`) with a pentagram-mural sigil at the north midpoint and stained-glass void windows at the east + west midpoints. Bank shrine + healing-pool shrine use the same voidbricks with one sigil-mural accent each. Floor is plain neutral grey (`TileDef` id 25, RGB 80/80/80) — purple lives only in the wall textures + sigil murals + windows for contrast. Pre-existing wilderness scenery (trees, gravestones, mushrooms — 19 items per the boot log) stripped at boot via `WorldPopulator`'s `WANT_VOID_ENCLAVE` filter. Bank chest, prayer altar, healing pool sit free-standing in the courtyard.

**`CLIENT_VERSION` evolution this session**: 10012 (slice-5 baseline) → 10013 (custom Healing pool def) → 10014 (`S_WANT_CUSTOM_LANDSCAPE = true` for custom landscape archive) → 10015 (added Void Floor TileDef) → 10016 (Void Wall DoorDef + voidbricks TextureDef) → 10017 (added voidouter + voidsigilwall TextureDefs and DoorDefs + 2nd TileDef) → 10018 (added voidweb + voidwindow TextureDefs and DoorDefs + 3rd TileDef). Each bump matched in `local.conf`'s `client_version`. Server enforces version match via `enforce_custom_client_version: true` so stale clients are rejected at login.

**Files touched** (incremental on top of slice-5 baseline):
- `server/src/.../io/WorldLoader.java:504-512` — F2P landscape selection bug fix.
- `server/src/.../database/WorldPopulator.java:71-83` — Void Enclave scenery filter (with diagnostic log).
- `server/src/.../ServerConfiguration.java` — `WANT_VOID_ENCLAVE` flag (already present from slice 5).
- `server/conf/server/defs/DoorDef.xml` — five new void DoorDefs (Void Wall 214, Void Highwall 215, Void Sigil Wall 216, Void Web 217, Void Window 218). Void Web is currently orphan.
- `server/conf/server/defs/TileDef.xml` — three new TileDefs (25 grey, 26 bright magenta, 27 mid purple).
- `server/conf/server/defs/GameObjectDef.xml` — Healing pool (id 1296).
- `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json` — 4 custom Void web (id 217) BoundaryLocs at the perimeter gates.
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json` — 19 amenity/atmosphere placements.
- `server/conf/server/data/Custom_Landscape.orsc` — patched sector h0x52y44 (binary, regenerable via `scripts/patch-void-enclave-landscape.py`).
- `server/local.conf` — `want_void_enclave: true`, `custom_landscape: true` (with the trailing duplicate removed), `client_version: 10018`.
- `server/plugins/.../authentic/npcs/alkharid/ShantayPassNpcs.java:458-465` — F2P bank-chest bypass on enclave coords.
- `server/plugins/.../authentic/misc/CutWeb.java` — `isWeb()` helper recognises both vanilla web id 24 and custom Void web id 217.
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
- The custom Void Web DoorDef (id 217), TextureDef (voidweb id 58), and sprite (3283) are live at the four enclave gate boundaries. Reverting them requires restoring those BoundaryLocs to vanilla web id 24 or another gate object.
- `WorldLoader.java` change is a pure ordering tweak; preservation/authentic worlds with `custom_landscape: false` get exactly the same behaviour as before.
- The `ShantayPassNpcs.java` bypass is bounds-gated to the enclave coords only; no other bank chest in the world is affected.
- Rollback should use git history for the tracked sprite archive; temporary local `.bak*` sprite snapshots are not part of the committed artifact set.

**Open follow-ups** (deferred):
- Custom Void Acolyte NPC standing in the sanctum (Edgar pipeline + AI-generated character sprite).
- Roofs over the buildings — last attempt with `roofTexture=1` looked broken when the user toggled "show roofs" on; needs an AI-textured roof variant + verifying the `roofTexture` byte's lookup path (TileDef? something else?).
- Cleanup pass to remove the orphan Void Sigil ground-item sprite (low priority — it doesn't break anything).
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

**2026-06-04 deployment note**:
- Fresh `custom` SQLite imports already include `itemstatuses.kill_log` because `core.sqlite` grew the column at the same time the boot patch was added. `JDBCPatchApplier` now treats the specific `2026_04_26_item_kill_log.sql` duplicate-column error as already applied, so existing older databases still get the migration while fresh beta databases boot cleanly and mark the patch executed.


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

### 2026-04-29 — Voidscape PC login screen refresh

Restored the Voidscape-specific PC login presentation as a login-only client change. Gameplay HUD/dialog rendering remains upstream/OpenRSC baseline.

**Client changes**:
- `Client_Base/Cache/login/voidscape-login-background.png` — cached 512×346 generated login background with no baked-in buttons, menus, counters, or other fake UI.
- `Client_Base/src/orsc/mudclient.java` — PC login renderer now draws the Voidscape background, stone modal frames, actual OpenRSC actions (`New User`, `Existing User`, submit/cancel, forgot password), and custom field chrome for login, account creation, and password recovery.
- Android login flow keeps the original OpenRSC panel path via `useVoidscapeLogin() == false`.

**Reversibility**: remove the `useVoidscapeLogin()` branch and the cache image to return to stock login rendering. No packets, server behavior, gameplay UI, or account flow semantics changed.

### 2026-04-29 — Voidscape starter flow: appearance screen, Void Island, one-time paths

New-account first-login flow now skips Tutorial Island after appearance confirmation and routes players to a small isolated **Void Island** starter area. This is a bespoke custom-landscape island, not the Wilderness Void Enclave.

**Client changes**:
- `mudclient.java` — `character_creation_mode: 0` uses a simple Voidscape-styled appearance panel over the login background. The original invisible control wiring remains; only the chrome/spacing changed.
- `mudclient.java` — the specific three-option Void Herald menu is intercepted and rendered as a custom modal with three large cards instead of the stock text option list.
- The path cards render native RSC item sprites directly: rune 2h for Warrior, rune pickaxe for Forager, and magic shortbow for Arcanist. No separate path-art PNGs are required.

**World / server changes**:
- `scripts/patch-void-enclave-landscape.py` — now also patches sector `h0x48y37`, creating a small isolated island around `(24,24)` in an unused F2P ocean sector. The earlier experimental east-map island sector `h0x63y55` is restored from authentic bytes each run so no stale island remains.
- `server/conf/server/data/Custom_Landscape.orsc` and `Client_Base/Cache/video/Custom_Landscape.orsc` — rebuilt from that script.
- `VoidPath.java` — persistent cache key `void_path`; new-player island entry `(24,26)`.
- `PlayerAppearanceUpdater.java` — after first appearance submit, unchosen players teleport to Void Island.
- `NpcId.VOID_HERALD(839)`, matching server/client NPC definitions, and `NpcLocsVoidIsland.json` — Void Herald now spawns at `(24,24)`. Important gotcha: the first attempt used `(742,887)` and terrain loaded, but the F2P NPC spawn filter skipped it because `Formulae.isP2P(false, x, y)` treats `x >= 431` as members territory.
- `LoginPacketHandler.java` / `VoidPath.java` — unchosen test characters saved on the earlier east-map island bounds `(734..750, 880..894)`, Tutorial Island, or the Tutorial Landing are routed to the current Void Island spawn during login.
- `VoidHerald.java` — NPC says exactly `choose your path`, then sends the three path options. After a choice it writes `void_path` and teleports the player to the configured respawn location.
- `Player.incExp()` — selected path doubles XP for its skills:
  - Warrior's Path: Attack, Defense, Strength.
  - Forager's Path: Fishing, Cooking, Mining.
  - Arcanist's Path: Ranged, Magic (`MAGIC`, `GOODMAGIC`, `EVILMAGIC` covered).

**Persistence**: no schema migration. The one-time path choice uses the existing `player_cache` table. Existing accounts with no `void_path` can still choose once if they reach the Void Herald.

**Reversibility**: restore the landscape archives via the patch script after removing the `VOID_ISLAND_SECTOR` patch, remove the NPC loc/defs/plugin, and remove the `VoidPath` hooks from appearance submit and XP gain. No protocol or client-version bump.

### 2026-04-29 — PK Catching Simulator: PvP-style catching trainer

New server-side training minigame for practicing RSC-style PK catching without automating a real client. Players talk to the **PK catching trainer** at `(214, 437)`, confirm a five-minute drill, get assigned one of three ocean-island arenas, and chase a synthetic player-like target. `::leave` exits early. Right-click **Highscore** on the trainer shows the player's rank plus the top 10 completed five-minute catch counts.

**Gameplay shape**:
- Target starts still at `(360, 71)`-equivalent inside the assigned arena and only begins running after the first successful attack. This avoids the confusing "already running while I load in" state.
- One mixed mode rolls easy/medium/hard movement internally instead of exposing three player-facing modes. The target alternates straight lines, diagonals, cutbacks, corner goals, obstacle routes, and center resets so it does not just trace the arena border.
- Catch distance is PvP-style range 2 using Chebyshev distance (`Point.withinRange`) and checks both the player's current tile and next queued movement tile. This was required for the visual/server-position gap on two-square straight and diagonal catches.
- Successful catches enter a simulator-owned combat lock: player and target are placed on the same tile, opponents are set for the normal combat look, zero-damage bubbles/sounds are emitted, no XP/HP loss/drops happen, and the target runs again after the PvP retreat condition.
- Drill duration uses the existing system-update timer packet; the client label was generalized from "Automatic server restart in" to "Time remaining" so the timer makes sense for minigames.
- Completed five-minute rounds persist best catch count to `conf/server/data/pk_catching_sim_highscores.properties`. The file is runtime data and gitignored.

**Arena / multiplayer support**:
- `scripts/patch-void-enclave-landscape.py` now bakes three identical walkable ocean islands into `Custom_Landscape.orsc` for both server and client cache:
  - arena 1: sector `h0x55y38`, bounds `344..378, 56..86`
  - arena 2: sector `h0x56y38`, +48 X offset
  - arena 3: sector `h0x57y38`, +96 X offset
- Runtime arena slots reserve one arena per player and spawn one target per session. A fourth simultaneous player receives a busy message. This is not true instancing, but it gives isolated practice lanes without new protocol or region-loader work.
- Temporary walls and obstacles are registered as GameObjects and tile traversal masks are saved/restored on cleanup. This fixed the earlier "walked through pillar borders" issue: visual obstacles alone are not enough; the traversal mask must also block.
- The target stepper uses `PathValidation.checkAdjacent(...)` and tile masks (`CollisionFlag.FULL_BLOCK`) before each one-tile move so the runner cannot cut through arena walls or obstacles.

**NPCs and rendering**:
- Added custom NPC ids 840 and 841. The server and client must append them in the same order because NPC ids are positional in the loader; a missing client-side append falls back to the "Ana (not in a barrel)" sentinel.
- Trainer id 840 is non-attackable, has `Highscore` as its right-click command, and is spawned by `NpcLocsVoidIsland.json` at `(214, 437)`.
- Target id 841 is attackable and rendered like a geared player: rune-style plate/legs/medium helm/kite, beard, and standard humanoid camera bounds. It is still an NPC on the wire; the simulator owns the player-like behavior.

**PvP-combat lessons learned**:
- Normal NPC combat cannot be used directly for this trainer. It caused target-first attacks, early fight termination, "fighting air" phantom combat, and re-attack lockouts because the NPC combat event and player-vs-player timing rules do not share the same state machine.
- The stable approach is a synthetic NPC with a `pkcatchsim_owner` attribute and a simulator-only combat event. `AttackHandler` and `WalkToMobAction` special-case that owned NPC just enough to treat attack pathing as PvP catching while still routing through the normal client attack click path.
- Follow-up fix: `PluginHandler` now resolves trigger methods by assignable parameter types after exact lookup misses, so NPC subclasses such as the simulator's synthetic target still match `AttackNpcTrigger.blockAttackNpc(Player, Npc)` and `onAttackNpc(Player, Npc)` without reflection errors.
- Do not make the runner a real player session or automate client input. The target is server-side only; it simulates the state transitions a fleeing player creates.
- Reattack too fast is real PvP behavior. The simulator sets both `player.setRanAwayTimer()` and `target.setRanAwayTimer()` when the combat lock releases, and `AttackHandler` blocks too-early reattacks using `PVP_REATTACK_TIMER`. This makes early clicks stop the player instead of instantly starting another fight.
- "Clicked from too far" should not cancel pursuit. Real PvP attack clicks keep the attacker following the target and fire when range becomes valid. The simulator preserves following + `WalkToMobAction` and inspects the pending action each tick instead of stopping the player on the original out-of-range click.
- The fake combat round uses the PvP retreat rule we observed: release after the catcher's third made swing. The event alternates zero-damage swings on a two-tick cadence and increments `hitsMade`; no normal NPC damage calculation runs.
- Phantom combat was cleared by aggressively removing stale normal combat state on both mobs: stop combat events, clear opponents/last opponents, reset hits made, following, busy flags, simulator combat attributes, and combat sprites. Mixing normal `CombatEvent` state with simulator state is what left the client attacking empty air.
- Attack clicks while already locked must be consumed with a "Wait for the combat lock to end." message; attack clicks after unlock but before the PvP reattack timer expires must be blocked by the reattack timer path.
- Real player combat is blocked while either player is in a catching session, including melee, ranged, and spell triggers, so the training island cannot become an accidental PvP arena.

**Tick/update-order lessons**:
- NPC movement normally processes before player packets. That made the target effectively one tick "in the future" relative to what the player saw, especially on two-square catches. `SyntheticPvpTarget.updatePosition()` now only resets path; simulator movement happens later in the minigame tick after attack checks, matching the client's recently rendered position much more closely.
- `Mob.face(...)` should not be blindly called on a moving NPC. The engine comment is correct: it marks a separate sprite update and can make the client show a stationary run-in-place frame before the movement step. The simulator now sets the facing needed for the movement packet, calls `resetSpriteChanged()`, then `setLocation(next, false)`.
- After combat unlock, `nextMoveTick = tick` is important. `tick + 1` creates a visible pause where the target stands/runs in place before escaping.

**Files touched**:
- `server/plugins/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulator.java` — new minigame plugin, session slots, target AI, fake PvP combat, scoring, highscores, `::leave`, login return handling.
- `server/src/com/openrsc/server/plugins/handler/PluginHandler.java` — trigger method lookup supports subclass arguments such as the synthetic catching target.
- `server/src/com/openrsc/server/net/rsc/handlers/AttackHandler.java` and `server/src/com/openrsc/server/model/action/WalkToMobAction.java` — simulator-owned target uses PvP catch distance/pathing/re-attack behavior while remaining an NPC.
- `server/conf/server/defs/NpcDefsCustom.json`, `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/conf/server/defs/locs/NpcLocsVoidIsland.json` — trainer/target definitions and trainer spawn.
- `scripts/patch-void-enclave-landscape.py`, `server/conf/server/data/Custom_Landscape.orsc`, `Client_Base/Cache/video/Custom_Landscape.orsc` — three ocean-island arena floors.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java`, `Commands.md` — player-visible `::leave` documentation.
- `Client_Base/src/orsc/mudclient.java` — timer label generalized to "Time remaining".
- `.gitignore` — runtime catching highscore file ignored.

**Testing notes**:
- Rebuild plugins after simulator edits: `ant -f server/build.xml compile_plugins`.
- Rebuild core when touching `AttackHandler` / `WalkToMobAction`: `ant -f server/build.xml compile_core`.
- Rebuild client when touching `EntityHandler` / `mudclient`: `ant -f Client_Base/build.xml compile`.
- Regenerate arenas after patcher edits: `python3 scripts/patch-void-enclave-landscape.py`.
- End-to-end manual test: log in, talk to trainer at `(214, 437)`, confirm, verify target stands still until first attack, verify two-square straight/diagonal catches, verify no phantom combat after repeated locks, verify early reattack is blocked, verify `::leave`, verify full timer completion records personal best/top 10.

**Reversibility**: remove the plugin, NPC defs/locs/client defs, attack/walk special-cases, and arena sector patch. Restore `Custom_Landscape.orsc` by rerunning the patcher after deleting the catchsim sector logic. Delete the runtime properties highscore file if a clean leaderboard is desired. No schema migration and no opcode change.

### 2026-04-30 — World-map autowalk reliability + Voidscape map overlays

Two fixes to make the world-map walker feel like a real navigation tool instead of a debug slice.

**Autowalk recovery.** `AutoWalkEvent` used to remove a 40-tile chunk from its master route as soon as it fed the `WalkingQueue`. If the normal movement queue reset anywhere inside that chunk (NPC block, door/gate timing, diagonal mismatch, etc.), the consumed tiles were gone. In the final chunk this meant `remaining.isEmpty()` and the event silently ended even though the player was not at the destination; in earlier chunks it tried to recover with only a 5k-node search budget, which was too small for many cross-region routes. The event now stores the final goal, treats an empty `remaining` deque as "done" only when the player is actually on that goal tile, otherwise re-paths from the current tile with the full `WorldPathfinder.DEFAULT_NODE_CAP`, and sends the recovered route back to the client so the green polyline stays honest.

**Destination snap radius.** `WorldPathfinder` now snaps blocked world-map clicks within 12 tiles instead of 5. This handles imprecise clicks on roofs, walls, coasts, and custom compounds more gracefully while still requiring the resulting snapped tile to be walkable and F2P-valid.

**Custom world-map overlays.** Added `scripts/bake-voidscape-worldmap.py`, which draws Voidscape-only custom terrain onto `Client_Base/Cache/worldmap/plane-0.png` and appends idempotent custom blocks to `labels.tsv` / `points.tsv`. It covers Void Island, Void Enclave, the three PK Catching Simulator arenas, Edgeville custom amenities/trainer, the Void Auctioneer, Edgar, and the Voidling marker. `UPSTREAM.md` now includes this script in the world-map refresh procedure so future upstream map refreshes don't erase the custom layer.

Files touched:
- `server/src/com/openrsc/server/event/rsc/impl/AutoWalkEvent.java`
- `server/src/com/openrsc/server/model/WorldPathfinder.java`
- `scripts/bake-voidscape-worldmap.py`
- `Client_Base/Cache/worldmap/{plane-0.png,labels.tsv,points.tsv,UPSTREAM.md}`

Reversibility: restore the map PNG/TSV files from git and remove the bake script for the visual layer; revert the two server source files for walker behaviour. No protocol change, DB migration, or client-version bump.

### 2026-04-30 — Autowalk scene route overlay + broader door/gate opening

The world-map route can now be shown in the main 3D game view as green tile overlays for each tile in the active route. The world-map panel has a `Tiles` toggle for this overlay, defaulting off; it is client-only and uses the existing `WORLD_WALK_ROUTE` data, so there is no opcode or version bump.

Autowalk door/gate handling now uses shared server-side rules via `AutoOpenRouteObstacle`. `WorldPathfinder` plans through the same simple closed doors/gates that `WalkingQueue` can open at runtime. Boundary doors/gates with route-style commands (`Open`, `Walk through`, `Go through`) move the player onto the destination tile in the same movement tick after opening, avoiding the "open, close, still stuck" failure. This includes diagonal boundary doors (dirs 2/3) that block as `FULL_BLOCK_A/B` instead of as cardinal wall edges. Autowalk-opened boundary doors are left open instead of scheduling the original closed door to respawn. Blocking scenery doors/gates use known passable replacements where available; otherwise they are briefly removed and respawned so the route can continue instead of stalling on a closed pass-through.

Runtime/testing note: this feature touches server core classes, client Java, and cache-backed world-map assets. For server Java changes (`AutoWalkEvent`, `WorldPathfinder`, `WalkingQueue`, `AutoOpenRouteObstacle`), rebuild `server/core.jar` and fully restart the server; the JVM does not hot-reload core classes. For client Java changes (`mudclient`, `Scene`, `GraphicsController`, `WorldMapPanel`), rebuild and relaunch the client. For cache/world-map PNG/TSV changes, relaunch the client so it reads the updated cache files.

Recommended full local test cycle:

```bash
scripts/build.sh
SERVER_PID=$(lsof -tiTCP:43596 -sTCP:LISTEN || true)
[ -n "$SERVER_PID" ] && kill "$SERVER_PID"
pkill -f 'orsc.OpenRSC' 2>/dev/null || true

# terminal 1: keep this running
scripts/run-server.sh

# terminal 2: after the server logs "Game world is now online"
scripts/run-client.sh
```

Use `lsof -tiTCP:43596 -sTCP:LISTEN` for the server PID. The running server command line is a generic `java -classpath ... com.openrsc.server.Server local.conf`, so broad `pkill -f openrsc` patterns often miss it and leave stale code running. `scripts/run-client.sh` writes `Client_Base/Cache/ip.txt` and `port.txt` from `server/local.conf`, which prevents local client/server port drift during testing.

Files touched:
- `Client_Base/src/orsc/graphics/two/GraphicsController.java`
- `Client_Base/src/orsc/graphics/three/Scene.java`
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java`
- `server/src/com/openrsc/server/model/AutoOpenRouteObstacle.java`
- `server/src/com/openrsc/server/model/WalkingQueue.java`
- `server/src/com/openrsc/server/model/WorldPathfinder.java`

Reversibility: remove the `Tiles` button/scene projection helper and revert `WalkingQueue`/`WorldPathfinder` to the old inline gate-only checks. No protocol change, DB migration, or client-version bump.

### 2026-04-30 — Void Enclave v2 aesthetic pass

Reworked the Wilderness Void Enclave from a rectangular utility compound into a stepped obsidian citadel.

**Visual / layout changes**:
- Generated a new five-panel enclave texture sheet and packed replacement texture sprites into the existing TextureDef slots `55..59` (`Authentic_Sprites.orsc` indices `3280..3284`). No new TextureDefs were added, avoiding another crown/media-slot collision.
- `scripts/patch-void-enclave-landscape.py` now builds the enclave from a stepped octagonal floor mask, generates the outer wall bytes from that mask, adds a north altar apse plus west/east amenity alcoves, and paints a ritual-floor pattern through the central court.
- The four clickable gate boundaries use custom DoorDef id `217` as "Void web". `CutWeb.java` handles both knife/weapon use and direct `Slice` clicks, so each side of the enclosure has a cuttable entrance.
- `SceneryLocsVoidEnclave.json` was re-staged around the new plan: altar in the north apse, bank chest west, healing pool east, obelisks/candles around the central ritual court, skulls/cauldron/pillars as perimeter set dressing. Edgeville chest/altar entries are retained.
- `bake-voidscape-worldmap.py` mirrors the new footprint and now clears the old baked enclave raster area before drawing, making repeated bakes idempotent for this overlay.

Files touched:
- `tools/voidscim-art/out/void-enclave-v2/*` — generated concept sheet, cropped texture PNGs, and sidecar metadata.
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — replacement texture sprites at `3280..3284`.
- `scripts/patch-void-enclave-landscape.py`, `server/conf/server/data/Custom_Landscape.orsc`, `Client_Base/Cache/video/Custom_Landscape.orsc`.
- `server/conf/server/defs/locs/{BoundaryLocsVoidEnclave.json,SceneryLocsVoidEnclave.json}`.
- `server/conf/server/defs/DoorDef.xml`, `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, `server/plugins/com/openrsc/server/plugins/authentic/misc/CutWeb.java`.
- `scripts/bake-voidscape-worldmap.py`, `Client_Base/Cache/worldmap/{plane-0.png,labels.tsv,points.tsv}`.
- `Client_Base/src/orsc/Config.java` — client version `10019` → `10020` for the cache/client definition refresh; matching local server config updated in gitignored `server/local.conf`.

Reversibility: restore `Authentic_Sprites.orsc` from git history, rerun the landscape patcher after reverting the footprint/scenery edits, and rerun the world-map bake from a clean world-map base. No schema migration or protocol change.

### 2026-04-30 — Autowalk boundary-door stuck fix

Fixed a blocker where the world-map autowalker could leave movement feeling disabled after opening a boundary door.

Cause: `AutoOpenRouteObstacle` replaced closed boundary doors with DoorDef id `11` and manually moved the player onto the next tile. Id `11` is visually an open doorframe, but it still has `doorType=1`, so it leaves collision behind. For diagonal boundary doors that collision is `FULL_BLOCK_A/B` on the destination tile itself, which can make the next movement packet fail immediately.

Fix: autowalk-opened boundary doors are now temporarily removed, scheduled to respawn after 3 seconds, and the blocked step is retried by `WalkingQueue` on the next tick. Scenery doors/gates keep the existing passable-replacement-or-temporary-removal behavior.

Files touched:
- `server/src/com/openrsc/server/model/AutoOpenRouteObstacle.java`

Runtime note: rebuild `server/core.jar` and fully restart the server. No client change or version bump.

### 2026-04-30 — Void Enclave cuttable gate webs

The v2 enclave gate objects are now proper cuttable web gates instead of decorative boundary walls, and global left-click web cutting is enabled.

Changes:
- DoorDef id `217` is named `Void web` with client/server `Slice` + `Examine` commands.
- `CutWeb.java` always handles direct clicks on Void web id `217`.
- Global `want_leftclick_webs` is now enabled, and the client static default `S_WANT_LEFTCLICK_WEBS` is true, so vanilla web id `24` also gets `Slice` as its primary action.
- Client version bumped `10020` → `10022` so stale clients with old boundary definitions are rejected.

Files touched:
- `server/conf/server/defs/DoorDef.xml`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `server/plugins/com/openrsc/server/plugins/authentic/misc/CutWeb.java`
- `Client_Base/src/orsc/Config.java`
- `server/local.conf` (gitignored)

### 2026-04-30 — Void Enclave Acolyte NPC

Added a resident NPC to make the enclave feel inhabited instead of purely utilitarian.

Details:
- New custom NPC id `842`: `Void Acolyte`, a non-attackable robed figure with the `Commune` command.
- Spawned at `(219, 348)` in the enclave sanctum via `NpcLocsVoidEnclave.json`, loaded only when `WANT_VOID_ENCLAVE` is enabled.
- New `VoidAcolyte` plugin handles both talk and `Commune`, with dialogue about the enclave, its safe-zone walls, and the web-sealed gates.
- Client NPC definition appended in positional order after id `841`; client version bumped `10022` → `10023`.

Files touched:
- `server/conf/server/defs/NpcDefsCustom.json`
- `server/conf/server/defs/locs/NpcLocsVoidEnclave.json`
- `server/src/com/openrsc/server/database/WorldPopulator.java`
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidAcolyte.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `server/local.conf` (gitignored)

### 2026-04-30 — Void Enclave General Store

Added a F2P-only themed general store inside the Void Enclave.

Details:
- New custom NPC id `843`: `Void Quartermaster`, a non-attackable trader with the `Trade` command.
- Spawned at `(212, 349)` near the enclave's west-side bank chest via `NpcLocsVoidEnclave.json`.
- New `VoidGeneralStore` plugin registers a true general store named `Void Enclave`.
- Stock is intentionally limited to server definitions with `isMembersOnly: 0`: web-cutting supplies, food, black robes, black weapons/armour, F2P runes, bronze arrows, skulls, and silk.
- Client NPC definition appended after id `842`; client version bumped `10023` → `10024`.

Files touched:
- `server/conf/server/defs/NpcDefsCustom.json`
- `server/conf/server/defs/locs/NpcLocsVoidEnclave.json`
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidGeneralStore.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `server/local.conf` (gitignored)

### 2026-04-30 - Void Rifts prototype removed

We prototyped a dynamic world event outside the Void Enclave, tested it through an admin spawn command, then removed it before committing because the current direction moved away from rift events.

Removed behavior:
- Scheduled `VoidRifts` registrar that opened timed world rifts.
- Runtime rift markers using object id `1147` with a `void_rift` attribute.
- Temporary Voidling spawns around rift sites.
- Admin/dev command `::voidrift` and aliases `::spawnrift`, `::forcerift`, `::vrift`.

Learned:
- The prototype was cleanly isolated to one registrar/plugin and one command, so removal did not require cache changes, definition id cleanup, or a client version bump.
- If world events return later, keep them behind an explicit config flag from the start and document the reward loop before enabling scheduled spawns.

Files removed or updated:
- Removed `server/plugins/com/openrsc/server/plugins/custom/misc/VoidRifts.java`
- Removed `server/plugins/com/openrsc/server/plugins/custom/commands/VoidRiftCommand.java`
- Updated `Commands.md`

### 2026-04-30 — In-game visual modes

Added optional in-game visual styles while preserving the classic view as the default player choice.

Details:
- The client can apply a world-scene-only post-render pass when enabled. `Voidscape` mode uses charcoal/purple color grading, a soft vignette, subtle scanline texture, and low-strength animated purple edge glows.
- `HD` mode is an HD-lite post-process, not a GPU renderer: it adds adjustable contrast/saturation, warmer top lighting, water/vegetation/material tuning, optional water shimmer, optional bloom on bright pixels, optional vignette, and low-strength scene glows while leaving UI untouched.
- The post-render pass also remaps obvious vegetation, warm wood/roof materials, and neutral stone into the Voidscape palette so trees, fences, roofs, and walls do not retain the classic green/brown/gray look.
- The pass runs immediately after `Scene.endScene` and before UI rendering, so chat, inventory, minimap, overhead labels, route highlights, and menus stay readable/classic.
- Settings now exposes `Game Look - Classic/HD/Voidscape`. When `HD` is selected, Advanced → Visuals also exposes HD intensity, HD color, sunlight, water shimmer, soft bloom, and vignette controls.
- The choice is persisted through the normal game-settings packet as custom setting byte `47` (`setting_game_look_mode`, values `0=Classic`, `1=HD`, `2=Voidscape`). The older boolean cache key `setting_voidscape_scene_overlay` is still read as a compatibility fallback.
- HD tuning is persisted as game setting indexes `50`-`55` (`setting_hd_intensity`, `setting_hd_saturation`, `setting_hd_bloom`, `setting_hd_vignette`, `setting_hd_water_shimmer`, `setting_hd_sunlight`) and is sent as optional trailing bytes after profile stats.

Files touched:
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/src/orsc/PacketHandler.java`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
- `server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java`
- `server/src/com/openrsc/server/net/rsc/handlers/GameSettingHandler.java`
- `server/src/com/openrsc/server/net/rsc/struct/outgoing/GameSettingsStruct.java`
- `server/src/com/openrsc/server/model/entity/player/Player.java`

### 2026-04-30 — Void Enclave V3 architecture and scenery pass

First real world-art pass after the global Voidscape overlay. This moves the enclave beyond post-processing by giving the baked map its own generated material set, clearer building silhouettes, and denser themed scenery.

Details:
- Generated a six-cell Voidscape architecture atlas and cropped it into 128x128 texture sprites: outer crystal stone, inner obsidian brickwork, sigil panel, slate roof, cracked ritual floor, and arched void window.
- Packed those textures into `Authentic_Sprites.orsc` at archive slots `3285-3290` and registered TextureDef ids `60-65`.
- Relocated the remaining media/crown sprites out of the texture-loader growth range: `3286-3294` now mirror at `3318-3326`, and the grey/gold crowns now mirror at `3345-3346`. `mudclient.loadSprites()` and `EntityHandler.loadCrowns()` load the relocated slots.
- Added DoorDef ids `219-223` for V3 bastion walls, inner walls, sigil panels, roof edging, and rift windows. Added TileDef id `28` for the generated cracked-stone floor and a client ElevationDef for the generated roof material.
- Reworked `patch-void-enclave-landscape.py`: perimeter walls now use the V3 bastion/sigil textures, the north sanctum is an enclosed shrine with a south entrance, the west shop/bank and east healing-pool huts now have actual facades and door openings, and enclave floors use the generated cracked-stone tile outside the ritual accents.
- Added decorative object defs `1297-1302` (`Void brazier`, `Void obelisk`, `Void monolith`, `Void crystal`, `Void supply crate`, `Void dead tree`) and placed them through `SceneryLocsVoidEnclave.json`.
- Regenerated both server and client `Custom_Landscape.orsc` copies. Client version bumped `10025` → `10026`.

Files touched:
- `Client_Base/Cache/video/Authentic_Sprites.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/src/orsc/mudclient.java`
- `server/conf/server/data/Custom_Landscape.orsc`
- `server/conf/server/defs/DoorDef.xml`
- `server/conf/server/defs/GameObjectDef.xml`
- `server/conf/server/defs/TileDef.xml`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `scripts/patch-void-enclave-landscape.py`
- `tools/voidscim-art/prompts/void-enclave-v3-architecture-atlas.txt`
- `server/local.conf` (gitignored)

### 2026-04-30 - Void Enclave cleanup and vanilla-style web recolor

Toned the V3 enclave back down after the first scenery pass overfilled the space, then replaced the custom door-like web art with a purple recolor of the original web silhouette.

Details:
- Reduced `SceneryLocsVoidEnclave.json` to the core readable props: altar, bank chest, healing pool, two braziers, two dead trees, and the retained Edgeville one-offs.
- Widened the west/east hut entrances in `patch-void-enclave-landscape.py` so shop and healing entrances stay visually and physically clear.
- Repacked archive slot `3283` (`voidweb`, TextureDef id `58`) with a purple recolor of the authentic web texture source. The custom DoorDef id stays `217`, so the four perimeter gates remain cuttable without shifting ids.
- Matched the client and server DoorDef height to the authentic web height (`192`) and kept the primary command as `Slice`.
- Client version bumped `10026` -> `10027` for the changed cache/definition pair.

Files touched:
- `Client_Base/Cache/video/Authentic_Sprites.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `server/conf/server/data/Custom_Landscape.orsc`
- `server/conf/server/defs/DoorDef.xml`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `scripts/patch-void-enclave-landscape.py`
- `server/local.conf` (gitignored)

### 2026-04-30 - Void Enclave relocation to 113,315

Moved the whole enclave to a new west-Wilderness position centered on `(113,315)`.

Details:
- `patch-void-enclave-landscape.py` now bakes the enclave into sector `h0x50y43` and restores the old sector `h0x52y44` from authentic landscape data so the previous location is removed.
- Runtime boundary, scenery, safe-zone, healing-pool, F2P bank-chest, and client wilderness-overlay checks now use the moved footprint (`X=98..128`, `Y=300..330`; gate webs at `(113,300)`, `(113,331)`, `(98,315)`, `(129,315)`).
- `WorldPopulator` clears authentic scenery/boundaries and base NPC spawns from a buffered rectangle around the new footprint (`X=100..126`, `Y=303..328`) before custom enclave content loads, removing the local trees, stumps, and wilderness clutter.
- The two enclave NPCs now have small roaming boxes, and the Edgeville Void Auctioneer plus PK catching trainer also roam instead of standing on a single tile.
- World-map overlay label/POIs were moved to the new enclave coordinates. Client version bumped `10027` -> `10028`.
- Follow-up fix: the PK catching trainer plugin now identifies the trainer by dedicated NPC id `840` instead of exact coordinate `(214,437)`, so `Talk-to` and `Highscore` continue to work while the trainer roams.

Files touched:
- `scripts/patch-void-enclave-landscape.py`
- `server/conf/server/data/Custom_Landscape.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`
- `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `server/conf/server/defs/locs/NpcLocsVoidEnclave.json`
- `server/conf/server/defs/locs/NpcLocsAuction.json`
- `server/conf/server/defs/locs/NpcLocsVoidIsland.json`
- `server/src/com/openrsc/server/database/WorldPopulator.java`
- `server/src/com/openrsc/server/model/Point.java`
- `server/plugins/com/openrsc/server/plugins/authentic/npcs/alkharid/ShantayPassNpcs.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveHealingPool.java`
- `server/plugins/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulator.java`
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/Cache/worldmap/plane-0.png`
- `Client_Base/Cache/worldmap/labels.tsv`
- `Client_Base/Cache/worldmap/points.tsv`
- `server/local.conf` (gitignored)

### 2026-04-30 - Void Enclave waystones

Added useful scenery to the open south entry wings without blocking movement.

Details:
- Added object id `1303`, `Void waystone`, using the existing obelisk model with a `Return` command.
- Placed paired waystones at `(106,321)` and `(120,321)` inside the moved enclave.
- Added `VoidEnclaveWaystone` plugin support so either waystone sends players back near Edgeville at `(217,449)`.
- Client version bumped `10028` -> `10029` for the new object definition.

Files touched:
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `server/conf/server/defs/GameObjectDef.xml`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveWaystone.java`
- `server/local.conf` (gitignored)

### 2026-04-30 - Voidscape pass maintenance notes

Things learned during the auto-walker, overlay, and enclave passes that should guide future changes:

- Client/server definition arrays are positional. Adding object, NPC, tile, door, texture, or packet-backed settings must keep ids aligned and should bump the client version when the client-visible contract changes.
- The enclave now has several coordinate-bound systems that must move together: baked landscape, boundary locs, scenery locs, NPC locs, safe-zone checks, healing pool bounds, bank-chest bypass, wilderness-overlay suppression, world-map labels, and `WorldPopulator` cleanup.
- If an NPC is allowed to roam, plugins should not identify it by exact `x,y` unless the coordinate is intentionally part of the interaction contract. The PK catching trainer regression came from moving id `840` to a roaming box while its minigame handler still required `(214,437)`.
- `scripts/patch-void-enclave-landscape.py` is the source of truth for the baked enclave shape. Manual edits to `Custom_Landscape.orsc` will drift unless the script is updated first and both server/client cache copies are regenerated.
- Custom web gates remain DoorDef id `217` with TextureDef id `58`; `CutWeb` recognizes them alongside authentic webs. The current art is a purple recolor of the vanilla web silhouette, which reads better than the earlier door-like web texture.
- Keep the enclave readable. Dense random props made the space feel premium in screenshots but hurt navigation and blocked entrances. Prefer a few functional anchors: bank chest, shop NPC, healing pool, altar, cuttable webs, and return waystones.
- Runtime pathing bugs around auto-walk and doors should be tested by opening a door through auto-walk and immediately clicking a second destination; this catches disabled-walk state regressions quickly.

### 2026-04-30 - Server performance pass 1

First lag-reduction pass after reviewing the current server hot paths.

Changes:
- `Player.processOutgoingPackets()` now writes all queued packets for a player and flushes Netty once per player tick instead of `writeAndFlush` per packet. This reduces syscall / event-loop churn without changing packet order.
- `ActionSender` now reuses stateless payload generators, and `Player` caches its inbound payload parser after login, avoiding per-packet parser/generator allocation.
- `PluginHandler` now caches resolved trigger methods, logs missing trigger types once, and moves per-plugin action traces from INFO to DEBUG. This cuts reflection and disk-log churn during normal gameplay.
- `World.load()` now builds the auto-walk `TelePointGraph` and preloads `WaypointGraph` during startup instead of on the first world-map walk request, removing first-click disk/graph stalls.
- `WorldPathfinder` avoids repeated `Point` allocation for F2P checks, pre-sizes its open/visited structures from route distance, and uses an int-coordinate `AutoOpenRouteObstacle` path for blocked-edge probing.
- `PcapLoggerService` now stays stopped when `want_pcap_logging` is false, and drops overflow jobs with a warning instead of throwing if packet capture is intentionally enabled and overrun.
- `server/local.conf` now has `want_pcap_logging: false`; packet capture is a targeted debugging tool, not a default runtime setting.
- Corrected `docs/subsystems/world-tick-loop.md` to record `Constants.REGION_SIZE = 48`, not `8`.

Files touched:
- `server/src/com/openrsc/server/model/entity/player/Player.java`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
- `server/src/com/openrsc/server/plugins/handler/PluginHandler.java`
- `server/src/com/openrsc/server/model/world/World.java`
- `server/src/com/openrsc/server/model/WorldPathfinder.java`
- `server/src/com/openrsc/server/model/AutoOpenRouteObstacle.java`
- `server/src/com/openrsc/server/util/rsc/Formulae.java`
- `server/src/com/openrsc/server/service/PcapLoggerService.java`
- `server/local.conf` (gitignored)
- `docs/subsystems/world-tick-loop.md`
- `docs/PERFORMANCE.md`

### 2026-04-30 - Autowalk collision correctness + waypoint guide

Fixes the wall-punching/stuck failure mode in the world-map autowalker.

Changes:
- `WorldPathfinder` now uses a terrain-only variant of the server's real `PathValidation.checkAdjacent` rules instead of maintaining a second hand-rolled wall check. The old planner had an east/west wall-direction mismatch, so it could accept route edges the `WalkingQueue` later rejected.
- `PathValidation.checkAdjacentStatic(...)` mirrors player movement collision while deliberately ignoring NPC/player blockers. Dynamic blockers remain a runtime recovery concern; static walls and full-block tiles now match between planning and walking.
- `AutoOpenRouteObstacle` now only treats a closed scenery door/gate as passable when that object actually blocks the candidate step. The previous 3x3 nearby-object scan could let an unrelated nearby gate make a wall edge look passable.
- Added `WaypointGraph`, a Java loader for `waypoints.rev` (`16,038` nodes / `57,036` edges on the local copy). Long same-floor routes may use it as a high-level guide, but graph legs are still validated by server collision and only fall back to bounded local A*, so stale waypoint edges and ignored gate metadata cannot authorize walking through walls.

Runtime notes:
- The loader tries `server/conf/server/data/waypoints.rev` first, then `~/RSCRevolution2/waypoints.rev`. It is warmed during world load; if neither exists, autowalk falls back to grid A* and logs that no waypoint graph was found.
- The TypeScript source at `/Users/s/Desktop/dizb0t/src/game/waypoints.ts` is useful documentation for the binary format, but its walker straight-lines between graph nodes and ignores `gates.dat`; Voidscape intentionally does not copy that trust model.

Files touched:
- `server/src/com/openrsc/server/model/PathValidation.java`
- `server/src/com/openrsc/server/model/WorldPathfinder.java`
- `server/src/com/openrsc/server/model/AutoOpenRouteObstacle.java`
- `server/src/com/openrsc/server/model/WaypointGraph.java`

### 2026-04-30 - Server performance telemetry

Added a low-overhead rolling telemetry stream for general server optimization.

Changes:
- `ServerPerformanceTracker` samples every game tick and logs compact `PERF` summaries when `perf_telemetry` is enabled. The summary includes tick `p50/p95/p99/max`, late/skipped tick counts, packet totals, queue pressure, and stage `p95` timings for world update, NPCs, players, events, walk-to-actions, messages, client update generation, incoming packets, outgoing packets, and cleanup.
- `LoginExecutor` exposes queue sizes so save/login pressure is visible without attaching a debugger.
- `Server` now exposes SQL executor queue sizes and feeds packet counts/stage durations into the tracker from the existing tick benchmark path.
- Added `scripts/perf-watch.sh` to tail only `PERF` lines and late-tick warnings from the server log.
- Added `scripts/perf-smoke.sh` to drive a quick local path/save pulse through the visible Java client and print the resulting telemetry.
- Local `server/local.conf` enables telemetry at 30-second intervals with a 512-tick window.

Files touched:
- `server/src/com/openrsc/server/util/ServerPerformanceTracker.java`
- `server/src/com/openrsc/server/Server.java`
- `server/src/com/openrsc/server/ServerConfiguration.java`
- `server/src/com/openrsc/server/LoginExecutor.java`
- `scripts/perf-watch.sh`
- `scripts/perf-smoke.sh`
- `server/local.conf` (gitignored)
- `docs/PERFORMANCE.md`

### 2026-04-30 - Synthetic server load harness

Added an in-process synthetic player swarm for repeatable game-thread load tests.

Changes:
- `SyntheticLoadService` can spawn up to 500 dummy `Player` instances into the real world player list/regions. They use normal player tick processing, walking queues, region membership, AOI scans, NPC/player update generation, and event scheduling, but are marked as `dummyplayer` / `loadtest`.
- Admins can run `::loadbots start <count> [radius] [intervalTicks]`, `::loadbots status`, and `::loadbots stop` in-game. `::loadtest` is an alias.
- Dummy players are excluded from `::saveall`, autosaves, and database-backed logout saves. They also bypass auth and socket lifecycle paths.
- `ClientLimitations(int clientVersion)` is public so the load service can build valid dummy player capability state without a network login.
- Added `scripts/perf-load.sh`, which uses the visible logged-in Java client to start/stop the swarm and print recent telemetry.

Verification:
- `./scripts/build.sh` passes.
- `./scripts/perf-load.sh 50 130 18 2` held 50 synthetic players plus the real client across the autosave boundary with 0 late/skipped ticks; warm tick `p95` stayed in the `12.6-16.5ms` range, update-generation `p95` stayed in the `3.6-6.7ms` range, and the next full interval after stop returned to one real player.

Files touched:
- `server/src/com/openrsc/server/util/SyntheticLoadService.java`
- `server/src/com/openrsc/server/Server.java`
- `server/src/com/openrsc/server/net/rsc/ClientLimitations.java`
- `server/src/com/openrsc/server/model/entity/player/Player.java`
- `server/src/com/openrsc/server/login/PlayerSaveRequest.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`
- `scripts/perf-load.sh`
- `docs/PERFORMANCE.md`

### 2026-04-30 - Rare drop loot beams

Added an OSRS-style rare drop beam for NPC drops that come from rare drop tables.

Changes:
- `DropTable` now marks `Item` instances returned from tables flagged `rare`.
- `Npc` transfers that marker onto the spawned `GroundItem`, preserving existing stackable, noted, Void Amulet, Splendor, and Ring of Avarice behavior.
- Player-dropped inventory/equipment items also get the beam when their unnoted, non-stackable item ID appears in a rare NPC drop table, which gives a cheap manual visual test path without making generic stacks like coins glow.
- The custom ground-item packet appends one rare-beam byte for client versions `10030+`; older/custom-admin clients remain on the previous packet shape.
- The Java client stores the flag per ground item and draws a projected void-purple tapered cone, base pulse, purple-only sparkles, and subtle swirl motion after the 3D scene render and before ground-item names.
- Rare beams are coalesced by ground tile on the client, so stacked rare drops draw one beam instead of compounding the translucent effect.
- Added the admin test command `::dropwave <npc_id> [count] [radius]` to spawn and immediately player-credit-kill a capped batch of NPCs using their normal drop tables. The command is capped at 20 NPCs, radius 8, with a 5 second per-player cooldown.

Protocol:
- `Client_Base/src/orsc/Config.java:CLIENT_VERSION` bumped `10029` -> `10030`.
- `server/local.conf:client_version` bumped `10029` -> `10030`.

Files touched:
- `server/src/com/openrsc/server/content/DropTable.java`
- `server/src/com/openrsc/server/constants/NpcDrops.java`
- `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- `server/src/com/openrsc/server/model/entity/GroundItem.java`
- `server/src/com/openrsc/server/external/ItemLoc.java`
- `server/src/com/openrsc/server/GameStateUpdater.java`
- `server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java`
- `Client_Base/src/orsc/PacketHandler.java`
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/Config.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`
- `server/plugins/com/openrsc/server/plugins/shared/DropObject.java`
- `server/local.conf` (gitignored)

### 2026-04-30 - Profile wrench + advanced settings window

Reworked the custom wrench so the first panel is player-focused instead of a two-tab social/game settings list.

Changes:
- The desktop wrench now opens a single `Profile` panel with current XP rates, chosen Void path, the path's 2x skill bonus, total played time, and only the normal social privacy toggles. Security rows and the online-list shortcut were removed from this view.
- Added a centered advanced settings window opened from `Advanced settings` at the bottom of the wrench panel. It groups gameplay, loot, visual, chat, and interface options into a custom-drawn modal instead of sending players through the old dense list.
- Added advanced controls for the new rare loot beams, hide combat XP drops, hide bones, ground item labels/modes, Classic/HD/Voidscape game look, roofs/fog/flicker, XP drops/counter, inventory count, batch progress, fight menu, side menu, kill feed, name tags, and related client toggles.
- Added persisted game-setting bytes `48` (`setting_rare_drop_beams`) and `49` (`setting_hide_combat_xp_drops`). The settings packet now also appends Void path, combat/skilling XP-rate tenths, and total played seconds for the profile panel.
- The loot-beam renderer now respects the `Rare loot beams` toggle, and regular combat XP drops can be suppressed while preserving level-up drops.
- Follow-up: `Game look` now defaults to Classic for new/unset players so the base game opens with the vanilla scene palette. The profile section headings use purple accent text instead of dark text on the gray panel.

Files touched:
- `Client_Base/src/orsc/Config.java`
- `Client_Base/src/orsc/PacketHandler.java`
- `Client_Base/src/orsc/mudclient.java`
- `server/src/com/openrsc/server/model/entity/player/Player.java`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
- `server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java`
- `server/src/com/openrsc/server/net/rsc/handlers/GameSettingHandler.java`
- `server/src/com/openrsc/server/net/rsc/struct/outgoing/GameSettingsStruct.java`

### 2026-04-30 - Prelaunch microsite prototype

Added a static `web/prelaunch/` one-page prelaunch gate.

Details:
- Generated a project-local premium old-school isometric courtyard background and a new original stone Voidscape wordmark asset.
- Built a compact single-screen page around the client login UI language: dark framed panel, gold text, purple accent, a name-reservation/subscription offer, and an expandable username/email founder-pass form with referral progress.
- Added three RSC-style character composites above the reward steps so the prototype reads as Classic-era instead of generic fantasy.
- The referral system is currently a front-end prototype backed by `localStorage`; production registration should replace the storage helpers in `script.js` with API-backed persistence before accepting real prelaunch signups.
- Documented the production founder-pass architecture, anti-abuse model, and go-live checklist in `docs/PRELAUNCH-FOUNDER-PASS.md`.

Files added:
- `docs/PRELAUNCH-FOUNDER-PASS.md`
- `web/prelaunch/index.html`
- `web/prelaunch/styles.css`
- `web/prelaunch/script.js`
- `web/prelaunch/README.md`
- `web/prelaunch/assets/voidscape-prelaunch-bg.png`
- `web/prelaunch/assets/voidscape-wordmark.png`
- `web/prelaunch/assets/rsc-ranger.png`
- `web/prelaunch/assets/rsc-knight.png`
- `web/prelaunch/assets/rsc-mage.png`

### 2026-04-30 - Void Rush survival minigame

Added `Void Rush`, a Classic-era void survival minigame. Players queue through the `Void Herald`, enter a rectangular void arena, dodge tick-based rows/columns of corrupted void energy with safe gaps, and the last surviving player receives one Christmas cracker.

Gameplay / server changes:
- New `server/plugins/.../custom/minigames/voidrush/` package with config, queue, instance, wave, and NPC-dialogue classes.
- Default queue requires 2 players, caps at 20, and teleports entrants to a waiting room before countdown.
- Waves can travel north/south/east/west, increase stride/gap difficulty by round, and support later double-wave rounds.
- Elimination handles standing on dangerous swept tiles, logout/disconnect, leaving the arena, and zero-player no-winner endings.
- Winner reward is guarded by a single `rewardAwarded` flag and uses existing `ItemId.CHRISTMAS_CRACKER` (`575`).
- Admin test helper `::voidrushbots [count]` / `::vrbots [count]` queues the caller plus synthetic load bots. Dummy load bots bypass the one-account-per-IP minigame restriction; real players still do not.
- For local bot-backed tests, `::loadbots stop` removes the synthetic players; Void Rush then ends naturally on the next tick if only the real tester remains. There is not yet a dedicated `::voidrush stop` / force-end command for active real-player matches.

Client / visual changes:
- Added custom outgoing packet `SEND_VOID_RUSH_WAVE` (wire id `102`) carrying wave direction, swept line range, gap bounds, and warning/lethal state.
- Java client renders wave visuals locally from that single packet as independent projected tile quads plus fallback center markers. This replaced object spam / teleport-bubble spam and avoids minimap leakage.
- Void Rush bounds disable side menu/minimap/world-map UI while preserving normal world walking and top mouse menu behavior.
- Teleport-bubble packet parsing now always consumes the payload even when the local display cap is full.

Landscape / content changes:
- Baked floor tiles for the ocean-isolated arena/lobby into `Custom_Landscape.orsc` on both server and client cache copies.
- Added `Void Herald` NPC spawn/dialogue hookup in the Void Enclave NPC locs/plugin.

Verification:
- `./scripts/build.sh` passes.
- Local live test with `::voidrushbots 10` starts a full Void Rush instance with 11 players.
- Forced-prize test by stopping bots confirmed winner message, broadcast, and exactly one persisted Christmas cracker (`catalogID=575`) for player `void`.
- Manual full-flow test was confirmed by user after running the command themselves.

Files touched:
- `server/plugins/com/openrsc/server/plugins/custom/minigames/voidrush/*`
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`
- `server/conf/server/defs/locs/NpcLocsVoidEnclave.json`
- `server/conf/server/data/Custom_Landscape.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`
- `scripts/patch-void-enclave-landscape.py`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
- `server/src/com/openrsc/server/net/rsc/PayloadValidator.java`
- `server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java`
- `server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java`
- `server/src/com/openrsc/server/net/rsc/struct/outgoing/VoidRushWaveStruct.java`
- `Client_Base/src/orsc/PacketHandler.java`
- `Client_Base/src/orsc/mudclient.java`

### 2026-05-01 - Dragon sword split into three components

Split the completed Dragon sword acquisition path into three component items plus a Smithing assembly step.

Details:
- Added custom items `1598` Dragon sword hilt, `1599` Dragon sword blade, and `1600` Dragon sword tip. All three are F2P, tradeable, non-stackable, and noteable.
- Sliced three Dragon sword component inventory sprites directly from the vanilla Dragon sword sprite. They are packed into `Client_Base/Cache/video/Authentic_Sprites.orsc` at archive slots `2763`, `2764`, and `2765`, exposed as client sprite IDs `613`, `614`, and `615`.
- Jakut no longer sells completed Dragon swords. Varrock Swords stocks one hilt as the F2P-area shop source.
- Dragon-type NPC tables now drop the blade component. The OpenPK dragon drop table also replaces the old completed Dragon sword drop with the blade.
- Main demon-type NPC tables now drop the tip component.
- The three component IDs are treated as rare-drop items for purple rare-drop beams when they appear on the ground.
- Using any component on an anvil now requires a hammer, all three unnoted components, and 70 Smithing. Success consumes the pieces, creates `ItemId.DRAGON_SWORD`, and awards 500 Smithing XP.
- Present and Halloween cracker prize paths no longer award completed Dragon swords, so assembly is the controlled completion path.
- Bumped the custom client version to `10031` for the new visible item definitions and sprite cache, to `10032` when replacing the first-pass generated component art with source-sliced Dragon sword pieces, and to `10033` when making the pieces F2P.

Shop and rate notes:
- Varrock Swords is the hilt source: shopkeeper NPC `56` and assistant NPC `130`, around `137,526`, with one `Dragon sword hilt` in stock.
- With `want_openpk_points` disabled, blade drop rates are `1/128` from Dragon `196`, Red Dragon `201`, Blue Dragon `202`, and Black Dragon `291`; King Black Dragon `477` is `2/130`.
- With `want_openpk_points` disabled, tip drop rates are `1/128` from Lesser Demon `22`, Lesser Demon maze clone `181`, and Black Demon `290`; Greater Demon `184` is `2/128`.

Files touched:
- `Client_Base/Cache/video/Authentic_Sprites.orsc`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `server/conf/server/defs/ItemDefsCustom.json`
- `server/plugins/com/openrsc/server/plugins/authentic/misc/HalloweenCracker.java`
- `server/plugins/com/openrsc/server/plugins/authentic/npcs/lostcity/Jakut.java`
- `server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/VarrockSwords.java`
- `server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java`
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VarrockSwordsOpenPk.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/Present.java`
- `server/src/com/openrsc/server/constants/ItemId.java`
- `server/src/com/openrsc/server/constants/NpcDrops.java`
- `server/src/com/openrsc/server/model/entity/npc/Npc.java`

### 2026-05-01 - PvE melee XP awarded on successful hits

Added `world.melee_gives_xp_hit` as a combat XP timing divergence. The tracked presets keep the flag disabled by default; Voidscape's gitignored `server/local.conf` enables it.

Details:
- Successful player melee hits against NPCs now award the corresponding Attack/Strength/Defense/Hits XP immediately when `melee_gives_xp_hit` is enabled.
- PvP melee XP remains death-based.
- Ranged and magic code behavior are unchanged: ranged still uses `ranged_gives_xp_hit`, and magic still awards spell XP on successful cast/finalization.
- Voidscape's gitignored `server/local.conf` enables both `melee_gives_xp_hit` and `ranged_gives_xp_hit`, so local PvE melee and ranged XP are both awarded on successful hits.
- NPC death still uses tracked melee damage for loot ownership, but skips the melee XP payout when on-hit melee XP is enabled to avoid double awards.
- On-hit melee XP is capped by total paid melee-XP damage per NPC life. If an NPC heals or regenerates, the same spawned NPC cannot produce more than one base-HP worth of melee hit XP before it dies and respawns.

Files touched:
- `server/src/com/openrsc/server/ServerConfiguration.java`
- `server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java`
- `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- `server/default.conf`
- `server/preservation.conf`
- `server/uranium.conf`
- `server/rsccabbage.conf`
- `server/rsccoleslaw.conf`
- `server/openpk.conf`
- `server/2001scape.conf`
- `docs/subsystems/combat-system.md`

### 2026-05-01 - Client cinematic camera modes

Added desktop-client camera presets for first-person and close promotional capture angles.

Details:
- `F4` now cycles camera modes: Classic, First person, Close, Low angle, and Orbit.
- Cinematic modes stay anchored to the local player instead of using detached freecam, avoiding normal-player off-screen scouting issues.
- Mouse drag and arrow keys adjust cinematic pitch/rotation. Mouse wheel adjusts distance for Close, Low angle, and Orbit; First person keeps a zero camera offset.
- `F5` toggles a HUDless capture view that hides chat/UI/health/name overlays while keeping the rendered scene, rare-drop beams, and scene click-to-walk active.
- `F6` saves camera point A, `F7` saves camera point B, and `F8` plays/stops a smooth player-anchored camera path between the two points.
- `F9` toggles a slow orbit for simple showcase shots; if used from Classic mode it switches into Orbit mode first.
- The existing first-person path still hides the local player sprite so the view is not blocked by the player billboard.

Files touched:
- `Client_Base/src/orsc/mudclient.java`
- `PC_Client/src/orsc/ORSCApplet.java`

### 2026-05-01 - Cinematic scene staging and recording helper

Added local promo-footage tooling around the existing synthetic player system.

Details:
- `::cinematic bossfight [actors] [bossNpcId] [radius]` spawns an admin-only staged boss-fight scene around the command user. Default scene is 18 rune-armored dummy player actors fighting King Black Dragon NPC `477`.
- The staged actors are real in-process dummy `Player` instances, marked with `dummyplayer` and `cinematicbot`, so they render through the normal player update path but still avoid DB save/auth/socket lifecycle paths.
- Actors wear rune armor, team-colored capes, and a visible Dragon sword weapon sprite. The scene driver keeps them facing the boss, cycles combat sprites, and emits staged damage/chat updates without running real loot/XP/death gameplay.
- `::cinematic stop` cleans up the staged actors and boss NPCs. `::cinematic status` reports the active scene.
- `scripts/record-cinematic.sh [durationSeconds] [actors] [bossNpcId] [radius]` starts the scene through the focused Java client, records the main display to `output/cinematics/*.mov` via macOS `screencapture`, and stops the scene afterward.
- Local `ffmpeg` is not required for the helper; the Homebrew `ffmpeg` install on this machine currently fails to launch because its x265 dylib dependency is missing.

Files touched:
- `server/src/com/openrsc/server/util/SyntheticLoadService.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`
- `scripts/record-cinematic.sh`

### 2026-05-01 - Paid equipment quest-right unlocks

Added per-player paid alternatives for the legacy equipment quest gates without marking the quests complete.

Details:
- Rune plate body/top, Dragon sword, Dragon battle axe, and Dragon square shield still accept the authentic quest completions.
- Players who have not completed the matching quest can buy the equipment right for 500,000 coins from the `Void Requisitioner` in Varrock.
- The `Void Requisitioner` is custom NPC `844`, spawned at `129,511` where the live `void` character was standing when requested, with a tight wander box of `128,510` to `130,512`.
- Client-side NPC definition `844` is appended after `Void Quartermaster` in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`; missing this step renders the server NPC as the "Ana (not in a barrel)" sentinel.
- The visible NPC was changed from the first robed/scythe placeholder to a dark-clothed requisitions clerk so he does not read as a wizard.
- Bumped the custom client version to `10035` for the client-visible NPC definition and appearance refresh.
- The purchase stores player-cache keys such as `quest_equipment_right_lost_city`; it only unlocks wearing the equipment and does not grant quest points, quest rewards, travel access, or any other quest state.
- Equip failure messages now tell players to either complete the named quest or buy the right from the `Void Requisitioner` in Varrock.

Files touched:
- `server/src/com/openrsc/server/model/QuestEquipmentUnlocks.java`
- `server/src/com/openrsc/server/model/container/Equipment.java`
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidRequisitioner.java`
- `server/conf/server/defs/NpcDefsCustom.json`
- `server/conf/server/defs/locs/NpcLocs.json`
- `server/src/com/openrsc/server/constants/NpcId.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `docs/recipes/add-npc.md`

### 2026-05-01 - Retired Voidling wilderness NPC

Removed the standalone Voidling NPC from active gameplay.

Details:
- Removed the four static Voidling wilderness spawns from `NpcLocs.json`.
- Removed the Voidling enum constant and NPC drop table, so no gameplay path references it for spawning or loot.
- Replaced custom NPC id `838` with an inert reserved placeholder on both server and client. This preserves the positional custom NPC list so later ids such as `Void Herald` and `Void Requisitioner` do not shift.
- Removed the Voidling world-map label and bake-script marker.
- Kept an unused reserved animation slot in the client for the same positional reason; the Voidling NPC art is no longer referenced by any active NPC definition.
- Bumped the custom client version to `10036` for the client-side definition change.

Files touched:
- `server/conf/server/defs/NpcDefsCustom.json`
- `server/conf/server/defs/locs/NpcLocs.json`
- `server/src/com/openrsc/server/constants/NpcDrops.java`
- `server/src/com/openrsc/server/constants/NpcId.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/Cache/worldmap/labels.tsv`
- `scripts/bake-voidscape-worldmap.py`

### 2026-05-01 - Void Key and Void Chest

Added wilderness-keyed loot chest content for Void Enclave.

Details:
- Added item `1601` `Void Key`, client sprite id `616`, packed at `Authentic_Sprites.orsc` archive entry `2766`.
- Local/admin testing can spawn the key with the normal item command, e.g. `::item 1601 1`.
- The Void Key icon was generated through the local `tools/voidscim-art` GPT image pipeline from the prompt `a dark obsidian void key with violet glowing runes and a cyan crystal eye`.
- Added custom scenery ids `1304` `Void chest` closed and `1305` `Void chest` open, using recolored `VoidChestClosed.ob3` and `VoidChestOpen.ob3` model entries packed into `models.orsc`.
- The Void chest model faces south, is scaled 1.5x visually, and keeps a 1-tile interaction footprint at the requested coordinate.
- Placed the closed Void chest at Void Enclave coordinate `113,315`.
- Opening the chest consumes one Void Key, briefly swaps the object to the open model, and rolls one reward from a weighted table of PK-useful supplies: gems, runes, food certificates, ore/coal certificates, big bones, coins, and adamant arrows.
- Void Keys are an extra rare wilderness-only NPC bonus drop. Safe zones are excluded.
- Void Key drop chance uses `1 / max(128, 1200 - npcCombatLevel * 6)`, so higher-level wilderness NPCs have better odds. Examples: level 10 is 1/1140, level 50 is 1/900, level 100 is 1/600, level 150 is 1/300, and level 179+ caps at 1/128.
- Void Keys are included in the rare-drop item list so ground drops receive the purple rare-drop beam.
- Bumped the custom client version to `10037` for the new item sprite, item definition, object definitions, and model archive entries.

Files touched:
- `server/conf/server/defs/ItemDefsCustom.json`
- `server/conf/server/defs/GameObjectDef.xml`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `server/src/com/openrsc/server/constants/ItemId.java`
- `server/src/com/openrsc/server/constants/NpcDrops.java`
- `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidChest.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/Cache/video/Authentic_Sprites.orsc`
- `Client_Base/Cache/video/models.orsc`

### 2026-05-01 - Auction House hardening and UI refresh

Hardened Voidscape's fixed-price Auction House and refreshed the PC client interaction flow.

Details:
- Fixed expiry refunds to return only `amount_left`; partially sold listings no longer refund their original full stack.
- Wrapped expiry refund + sold-out marking, seller collection, moderator removal, cancel, and buy persistence in DB transactions that also save the affected player inventory/bank containers.
- Listing creation now rejects invalid items, zero/negative values, total prices below 1gp per item, and totals that do not divide evenly by quantity.
- Buy/cancel delivery now prefers inventory when it can hold the item, including stackables and noted multi-quantity non-stackables, then falls back to bank.
- Removed the stale packet-level 100-total-level auction gate so the single Void Auctioneer is the real access gate.
- Invalid auction option packets are rejected instead of null-switching.
- Auction list hours-left changed from byte to short, allowing full 7-day listings to display correctly.
- PC client Auction House now has corrected sort labels (`Price Low`, `Price High`, `Each Low`, `Each High`), visible expiry hours, quick quantity buttons, safer amount/price parsing, and a two-click purchase confirmation.
- Bumped the custom client version to `10038` for the auction packet/UI change.

Files touched:
- `server/src/com/openrsc/server/content/market/Market.java`
- `server/src/com/openrsc/server/content/market/task/*MarketItemTask.java`
- `server/src/com/openrsc/server/content/market/task/OpenMarketTask.java`
- `server/src/com/openrsc/server/content/market/task/PlayerCollectItemsTask.java`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
- `server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java`
- `server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java`
- `Client_Base/src/com/openrsc/interfaces/misc/AuctionHouse.java`
- `Client_Base/src/orsc/PacketHandler.java`
- `Client_Base/src/orsc/Config.java`
- `docs/subsystems/auction-house.md`

### 2026-05-01 - Custom bank V2 UI and inventory-only loadouts

Reworked the custom bank into a cleaner single-screen V2 while keeping the existing bank container and core bank packets intact.

Details:
- Bank tabs are now explicit `All` / `Tab N` buttons with adaptive compact labels when space is tight.
- Search moved into a top control row with `Loadouts` beside it; shared button text baseline was fixed so compact labels no longer hit their borders.
- Loadouts moved out of anonymous number buttons into a panel with visible `Load`, `Save`, and `Clear` actions per slot.
- `Clear` persists through a new `InterfaceOptions.BANK_CLEAR_PRESET` action (`199`, sub-op `14`) and refreshes the preset packet back to the client.
- Loadouts are inventory-only on both client and server. Saving clears equipment preset data, loading no longer deposits/equips worn gear, and the custom bank no longer exposes equipment-tab or worn-gear UI.
- `Rearrange` is now a compact dropdown (`Off`, `Swap`, `Insert`) instead of three always-visible buttons.
- Deposit/withdraw paths now guard stale slots and non-positive amounts more defensively.
- `BankUtil` cert helpers now use static primitive lookup/switch logic instead of rebuilding lists per call.

Files touched:
- `Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java`
- `Client_Base/src/orsc/util/BankUtil.java`
- `server/src/com/openrsc/server/model/container/Bank.java`
- `server/src/com/openrsc/server/model/container/BankPreset.java`
- `server/src/com/openrsc/server/constants/custom/InterfaceOptions.java`
- `server/src/com/openrsc/server/net/rsc/handlers/BankHandler.java`
- `server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java`
- `server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java`

Reversibility: flip `want_custom_banks` off to return to the authentic bank UI. Reverting only the V2 UI requires also removing interface sub-op `14`; otherwise it is harmless but unused.

### 2026-05-01 - Voidscape Android client bootstrap

Moved the legacy Android wrapper from an OpenRSC-branded best-effort app toward a Voidscape dev APK.

Details:
- Android app id is now `com.voidscape.client`, the app label is `Voidscape`, and the Gradle output is `voidscape.apk`.
- Removed the old APK self-update path that pointed at OpenRSC/rsc.vet. `ApplicationUpdater` now acts as a short Voidscape bootstrap screen before cache/server setup.
- The Android build now packages a clean generated asset copy of `Client_Base/Cache` under APK assets, excluding mutable local files (`config.txt`, `credentials.txt`, `hideIp.txt`, `ip.txt`, `port.txt`, `uid.dat`).
- `CacheUpdater` seeds bundled cache assets into app-private storage first, then optionally checks `osConfig.CACHE_URL` if a future Voidscape cache endpoint is configured.
- Replaced the OpenRSC world selector with Voidscape server choices: emulator host `10.0.2.2:43596`, LAN default `192.168.1.100:43596`, and a manual host/port dialog.
- Raised Android `minSdkVersion` to `23` to match APIs the wrapper already uses.
- Removed obsolete/protected Android permissions and stopped relying on hardcoded external storage paths.
- Android rendering now preserves the game framebuffer aspect ratio with letterboxing instead of stretching to every screen; touch input maps through the same transform.
- Desktop-only PNG loading for the Voidscape login and world-map assets now goes through a reflection-based `PngSpriteLoader`, avoiding compile-time AWT/ImageIO imports in Android builds while preserving PC behavior.
- Added `scripts/build-android.sh` to select JDK 17 and fail clearly when Android SDK configuration is missing.

Files touched:
- `Android_Client/Open RSC Android Client/build.gradle`
- `Android_Client/Open RSC Android Client/src/main/AndroidManifest.xml`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/ApplicationUpdater.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/CacheUpdater.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/updater/md5.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java`
- `Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java`
- `Android_Client/Open RSC Android Client/src/main/res/layout/applicationupdater.xml`
- `Android_Client/Open RSC Android Client/src/main/res/layout/updater.xml`
- `Android_Client/Open RSC Android Client/src/main/res/values/strings.xml`
- `Android_Client/Open RSC Android Client/src/main/res/xml/network_security_config.xml`
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java`
- `Client_Base/src/orsc/util/PngSpriteLoader.java`
- `scripts/build-android.sh`

Build note: Android verification requires a local Android SDK (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `Android_Client/local.properties`). Verified locally with Homebrew Android command-line tools at `/opt/homebrew/share/android-commandlinetools`; debug build emits `Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk`.

### 2026-05-01 - Voidscape cracked V brand icon

Selected the cracked stone `V` icon direction from the 4x4 Voidscape concept sheet and wired it into the app-facing launcher icons.

Details:
- Added master branding assets under `assets/branding/`.
- Android launcher density icons now use the Voidscape cracked `V`.
- PC launcher and PC client window icons now use the same `V` mark.
- The Linux desktop SVG fallback now uses a compact vector approximation of the same cracked `V` concept.
- `assets/branding/voidscape-icon-v-discord-512.png` is the Discord-ready square export.

Files touched:
- `assets/branding/voidscape-icon-v-master.png`
- `assets/branding/voidscape-icon-v-discord-512.png`
- `assets/branding/voidscape-icon-v-desktop-256.png`
- `Android_Client/Open RSC Android Client/src/main/res/drawable-*/ic_launcher.png`
- `PC_Launcher/src/main/resources/images/icon.png`
- `PC_Launcher/vet.rsc.OpenRSC.Launcher.svg`
- `Client_Base/src/res/icon.png`

### 2026-05-01 - Standalone Discord community setup package

Created a standalone Discord community setup package for Voidscape. This is intentionally not connected to the legacy OpenRSC Discord service, game server config, webhooks, account linking, cross-chat, or in-game systems.

Details:
- Added a server setup guide covering roles, categories, channels, forum usage, permissions, AutoMod, raid protection, onboarding, pinned starter messages, moderation workflow, and launch checklist.
- Generated Discord-ready community assets: server icon, server banner, high-resolution banner source, and invite splash/background.
- Added a local one-shot setup bot script that uses Discord's official API through Node's built-in `fetch`, reads its token only from `DISCORD_BOT_TOKEN`, and dry-runs by default unless `--apply` is passed.
- The setup bot creates or updates the baseline roles, categories, text channels, voice channels, forum channels, permission overwrites, pinned starter messages, forum starter posts, server branding, and Discord-native AutoMod rules.
- The setup bot is idempotent by Discord object name where practical, so reruns update existing roles/channels instead of duplicating the server layout.
- Onboarding remains a manual Discord UI review step because parts of that flow are gated by Community/server feature state and are easier to verify visually.
- The live Voidscape Discord server was initialized through the setup bot, then a polished development/vision announcement was posted manually through VoidBot using current in-game screenshots.

Files touched:
- `docs/community/discord-server-setup.md`
- `docs/community/discord-setup-bot.md`
- `assets/discord/README.md`
- `assets/discord/server-icon-512.png`
- `assets/discord/server-banner-1920x1080.png`
- `assets/discord/server-banner-960x540.png`
- `assets/discord/invite-splash-1920x1080.png`
- `scripts/setup-discord.js`

Safety note: bot tokens must never be committed. The setup flow documents token use through environment variables only, and operators should regenerate/remove the bot token after a setup session.

### 2026-05-01 - Discord announcement access gate

Added a standalone persistent Discord access-gate bot for the live Voidscape community server.

Details:
- `#announcements` can now act as the only public channel for users without the `Member` role.
- The gate posts a pinned `Enter Voidscape` button in `#announcements`; clicking it grants the configured member role and unlocks the rest of the public server.
- The setup path (`--setup`) rewrites Discord channel permission overwrites so `@everyone` can only view/read the gate channel, while non-staff public channels allow the `Member` role.
- The serve path (`--serve`) connects to Discord Gateway using Node's built-in WebSocket support, listens for message-component interactions, and grants the `Member` role through Discord's REST API.
- Staff channels remain staff-scoped; the gate script does not alter game code, game config, OpenRSC Discord services, webhooks, cross-chat, or account linking.
- The live local listener is running in a detached `screen` session named `voidscape-discord-gate`; the token is read from the macOS keychain and is not committed.

Files touched:
- `scripts/discord-access-gate.js`
- `docs/community/discord-server-setup.md`
- `docs/community/discord-setup-bot.md`

Operational note: the button requires the persistent listener to be online. Check it with `screen -ls` and `tail -f output/discord-bot/access-gate.log`.

### 2026-05-02 - Death Match Arena and Void Knight

Added a Voidscape-owned Death Match Arena gated by the new **Void Knight** NPC.

Details:
- Added NPC id `845` (`VOID_KNIGHT`) as the static basement challenger and NPC id `846` (`VOID_KNIGHT_ARENA`) as the spawned fight actor, both with no helmet/gloves/boots, white beard, classic void skin, rune platebody, rune platelegs, rune weapon, and amulet.
- Expanded the Void Enclave from the tight `102..123,305..325` footprint to `98..128,300..330`, widened the safe-zone/client overlay/cleanup bounds with it, spread out the altar/shop/healing/bank amenities, and added a ladder down at `(122,313)`.
- Removed the old two-step arena lobby flow. There is now one static Void Knight downstairs at `(984,664)`; attacking him starts the fight immediately. His right-click menu is intentionally attack-only, with no Challenge/Return dialogue actions. The desktop client also suppresses the default `Talk-to` row and ignores blank NPC `command2` labels so this menu cannot render a stray standalone `Void Knight` row.
- Added an enclave ladder warning/confirmation before entering the downstairs challenge chamber.
- Added `DeathMatchArena`, a custom minigame plugin that owns enclave/basement ladders, basement return, the private boss spawn, fight cleanup, one-at-a-time arena occupancy, and the victory announcement. The spawned Void Knight uses normal NPC combat for melee, death, XP, drops, and pathing instead of the earlier synthetic duel loop.
- Added `VoidKnightBoss`, a core combat script/boss pulse that gives the spawned Void Knight 99 combat stats, a strength-potion boost to 118 strength, 22 swordfish at low HP, defensive prayer switching against ranged/magic only, delayed boss/player re-engagement, Fire Blast pressure when the player runs or kites, and session-owned movement tactics where the boss can break melee, step/kite inside the arena, force a quick cast, then re-engage.
- Added Void Knight completion rewards: coins, one noted supply/material roll, one random standard rune item, and a rare 1-in-32 dragon component roll. No Void equipment is on the boss reward table.
- Converted the Void Chest's old certificate-style rewards into noted base items, preserving the certificate-equivalent bulk amounts for ore and food and making other bulk non-stackable chest rewards noted too.
- The deathmatch sector moved from the original wilderness-ocean sector `h0x54y38` to a far floor-0 ocean sector `h0x68y50`; the broken underground test sector `h0x54y57` and original sector are restored to authentic terrain by the patcher. The new basement chamber and compact altar arena sit at `X=976..992`, `Y=635..670`; the boss spawns around `(984,643)`, the player starts at `(984,651)`, and the arena altar sits at `(984,647)` for normal `Recharge at` prayer flow during the match.

Files touched:
- `server/plugins/com/openrsc/server/plugins/custom/minigames/DeathMatchArena.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidChest.java`
- `server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/VoidKnightBoss.java`
- `server/src/com/openrsc/server/constants/NpcId.java`
- `server/src/com/openrsc/server/database/WorldPopulator.java`
- `server/src/com/openrsc/server/model/Point.java`
- `server/conf/server/defs/NpcDefsCustom.json`
- `server/conf/server/defs/locs/BoundaryLocsVoidEnclave.json`
- `server/conf/server/defs/locs/NpcLocsVoidEnclave.json`
- `server/conf/server/defs/locs/SceneryLocsVoidEnclave.json`
- `server/conf/server/defs/locs/NpcLocsDeathMatchArena.json`
- `server/conf/server/defs/locs/SceneryLocsDeathMatchArena.json`
- `server/plugins/com/openrsc/server/plugins/authentic/npcs/alkharid/ShantayPassNpcs.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveHealingPool.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidEnclaveWaystone.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java` (`CLIENT_VERSION` 10038 -> 10045)
- `Client_Base/src/orsc/mudclient.java`
- `scripts/patch-void-enclave-landscape.py`
- `scripts/bake-voidscape-worldmap.py`
- `server/conf/server/data/Custom_Landscape.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`
- `Client_Base/Cache/worldmap/plane-0.png`
- `Client_Base/Cache/worldmap/points.tsv`
- `server/local.conf` local override `client_version: 10045` (gitignored)

Reversibility: remove the plugin, NPC id/defs/locs/client definition, world-populator loc load, and deathmatch sector logic from the patcher, then rerun the patcher. No schema migration or opcode change.

### 2026-05-02 - Edgeville Void Rift

Added a clickable **Void Rift** at `(192,443)` in the grassy/muddy Edgeville clearing.

Details:
- Added custom scenery id `1306` `Void Rift`, reusing the existing Mage Bank `rockpool` model instead of custom-generated rift art.
- The earlier generated `VoidRift1.ob3` through `VoidRift4.ob3` model entries and client animation hook were removed after playtesting because they read as scenery sitting on top of the grass instead of a portal embedded in the landscape.
- `models.orsc` was repacked to remove the generated rift model entries and return to the stock `rockpool.ob3` asset.
- Placed the rift exactly at `(192,443)` via `SceneryLocsVoidRift.json`, loaded under the existing `WANT_VOID_ENCLAVE` custom-content gate.
- Added `VoidRift` `OpLocTrigger`: clicking `Enter` prompts for confirmation and then teleports the player to the Void Enclave at `(113,314)`.
- Bumped the custom client version to `10049` for the stock-pool object definition update and model cache checksum.

Files touched:
- `server/conf/server/defs/GameObjectDef.xml`
- `server/conf/server/defs/locs/SceneryLocsVoidRift.json`
- `server/src/com/openrsc/server/database/WorldPopulator.java`
- `server/plugins/com/openrsc/server/plugins/custom/misc/VoidRift.java`
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- `Client_Base/src/orsc/Config.java`
- `Client_Base/Cache/video/models.orsc`
- `Client_Base/Cache/MD5.SUM`

### 2026-05-02 - Karamja Fishmonger

Added a custom **Karamja Fishmonger** NPC at `(363,713)`.

Details:
- New NPC id `847` with right-click `Cook fish` and `Note fish` commands, plus dialogue for both actions.
- `Cook fish` converts unnoted raw fish into cooked fish at a 50% return rate. The fishmonger only processes raw fish stacks of at least two, so a single raw fish is left alone instead of being consumed for no output.
- `Note fish` converts unnoted raw and cooked fish into banknotes using the existing noted-item system, not certificates.
- Supported fish: shrimp, anchovies, sardine, herring, trout, pike, salmon, tuna, lobster, swordfish, shark, cod, mackerel, bass, manta ray, and sea turtle.

### 2026-05-02 - Player Titles

Added the first cosmetic player-title slice.

Details:
- New `PlayerTitle` registry in core with cache-backed unlocks and active-title selection.
- New player command `::titles` / `::title` opens a menu of unlocked titles and allows clearing the active title.
- Public overhead chat is prefixed with the active title, for example `[Voidbane] Hello`.
- Defeating the Void Knight unlocks the `Voidbane` title. Titles are cosmetic only and grant no stat, XP, drop-rate, or combat benefit.

### 2026-06-03 - Auction House Market Intel

Added a market-intel slice to the custom Auction House.

Details:
- Added `auction_sales` schema patches for MySQL and SQLite, plus database structs/query methods for sale history, 7-day item summaries, hot items, and recent sales.
- `BuyMarketItemTask` now writes a sale-history row inside the same purchase transaction that creates seller proceeds, updates/sells out the listing, and saves the buyer inventory/bank.
- `OpenMarketTask` keeps raw packet `132` but adds subtype `2` after the existing reset/listing chunk payloads. The new payload includes per-item active/last/average/volume stats, hot 7-day items, and recent completed sales. This is a packet-shape change, so the custom client version moved to `10051`.
- `AuctionHouse.java` now has an **Intel** tab and a selected-listing market snapshot. Public completed-sale intel is anonymous; buyer/seller names stay in the DB row for auditability but are not displayed in the UI.

### 2026-06-03 - Auction House browse UI refresh

Refreshed the PC client Auction House browse experience to make it less text-heavy and easier to scan.

Details:
- Replaced the vertical text-only category list with compact item-icon category tiles while keeping the same filter behavior.
- Reworked the Browse tab top row, listing rows, and empty state with denser visual hierarchy, item sprites, match counts, per-unit pricing, total pricing, and expiry at a glance.
- Reworked the selected-listing purchase panel into a detail card with a larger item sprite, seller/time details, quantity/each/total chips, the existing 7-day market snapshot, quick amount buttons, and the two-click purchase confirmation flow.

### 2026-06-03 - AI workbench capture/state MVP

Added a dev-only PC client workbench for faster visual iteration.

Details:
- Added a loopback-only HTTP server, enabled through `scripts/run-workbench-client.sh`, with `/health`, `/state`, and `/screenshot` endpoints.
- Screenshots now copy the exact rendered game frame from `ORSCApplet` and save a PNG plus JSON sidecar under `tmp/workbench/screenshots/`.
- The backtick screenshot hotkey uses the same exact-frame capture path instead of the previous macOS full-screen `screencapture` process.
- Documented the subsystem in `docs/subsystems/ai-workbench.md`. No packet, opcode, DB schema, cache version, or gameplay behavior changed.

### 2026-06-03 - AI workbench control bridge and Auction House smoke

Extended the dev-only PC client workbench from capture-only into a first automation loop.

Details:
- Added loopback-only input endpoints for click, key, type, and existing client command dispatch.
- Added `/captures/latest` for retrieving the most recent saved PNG/sidecar metadata.
- Added `/dev/ready`, which uses the saved Existing User login flow, waits for the local player, clears welcome/server-message dialogs, and hides iteration-blocking client panels for faster visual testing.
- Added `/scenario/auction-house-open`, which opens the Auction House through `::quickauction`, drives tabs/categories through synthetic client input, and saves a small screenshot report for Browse, selection, My Auctions, Intel, and Food category states.
- Synthetic mouse/key actions go through the same applet handlers as user input. No packet, opcode, DB schema, cache version, or gameplay behavior changed; command dispatch uses the existing client command packet.

### 2026-06-03 - AI workbench Auction House fixtures

Added deterministic Auction House fixture seeding for local visual automation.

Details:
- Added admin command `::workbenchauctionfixture` / `::workbenchahfixture`, which clears only `wb-fixture`/`wb-buyer` rows and reseeds 6 active listings plus 7 recent sale-history rows through the existing server database layer.
- Added `POST /fixture/auction-house` to the PC workbench and made `/scenario/auction-house-open` seed the fixture before capturing the UI, so Browse, My Auctions, Intel, and Food-category screenshots have stable content.
- Added an explicit `Market` auction-cache refresh request so fixture reseeds show up even when the active auction count is unchanged. No packet, opcode, DB schema, cache version, or gameplay behavior changed outside the admin-only local testing path.

### 2026-06-03 - Adaptive wilderness hobgoblin spawns

Added crowd-responsive behavior for the level-32 hobgoblin starter zone in the surface wilderness.

Details:
- The zone around `(217,255)` counts distinct logged-in player IPs every 30 seconds.
- At three unique IPs, the existing static hobgoblins respawn twice as fast (`84s -> 42s`); at five unique IPs, they respawn three times as fast (`84s -> 28s`).
- At four or more unique IPs, runtime-only extra hobgoblins spawn in the same zone, capped at five extras. Extras do not persist to spawn JSON and do not respawn after death.
- Players in the zone receive a small server message when extra hobgoblins are spawned.
- Added admin-only `::wildhobdebug [status|off|0-20]` to simulate crowd pressure during local testing. No packet, opcode, DB schema, cache version, or client behavior changed.

### 2026-06-03 - Void Herald world announcements

Added a server-side world-announcement layer for high-signal social events.

Details:
- New `WorldAnnouncementService` sends purple Void Herald messages through existing quest/server chat, with config gates for the master feature, milestones, and skulled Wilderness PKs.
- Skill broadcasts fire for selected high-level milestones and configured max level; total-level broadcasts fire at sparse total-level thresholds. Player-cache keys prevent repeat broadcasts for the same milestone.
- A passive `PlayerKilledPlayerTrigger` announces Wilderness PKs only when the defeated player is currently skulled, without changing normal death, loot, skull, kill-count, or client kill-feed behavior.
- Added admin-only `::announcepreview [skill|total|pk]` for local styling checks. No packet, opcode, DB schema, cache version, or client behavior changed.

### 2026-06-03 - Rare drop beam policy and customization

Refined the purple rare-drop beam from a broad rare-table marker into a curated, player-customizable loot alert.

Details:
- Added `LootBeamSettings`, which owns the default beam item list, per-player cache keys, and default/custom mode evaluation.
- `NpcDrops.isRareDropItem` now returns the curated default beam list instead of scanning every item in every rare table. This keeps common rare-table filler from glowing while preserving beam-worthy chase items.
- Ground-item updates now apply `LootBeamSettings.shouldShowBeam(player, groundItem)` per viewer before writing the existing rare-beam byte, so players can have different beam preferences without a packet or client version change.
- Added regular-player `::lootbeam` / `::lootbeams` commands for list/defaults/add/remove/mode/reset. Item lookup accepts IDs or item names.
- Retuned the custom client's procedural beam art with a darker outer veil, purple-only highlights, no filled center dot, rotating crescent bands, and lighter sparkles. The Advanced Settings row is now labeled `Loot beams`.
- Documented the subsystem in `docs/subsystems/rare-drop-beams.md`. No opcode, DB schema, cache version, or protocol shape changed.

### 2026-06-03 - Player title catalog and overhead display

Added Voidscape player titles as a cosmetic progression layer.

Details:
- Added `PlayerTitle`, a 100-title registry with automatic requirements for total level, combat level, quest points, Wilderness player kills, NPC kills, single-NPC kill counts, and real RSC skill levels, plus manual slots for event, auction-house, rare-drop, and Voidscape-specific rewards.
- Title unlocks persist through existing player-cache keys, and the active title persists through `player_title_active`. New unlock keys use the short `pt_u_` prefix so all title ids fit under the current cache-key schema limit; legacy `player_title_unlocked_` keys remain readable.
- Titles are scoped as reusable or unique. Reusable titles, such as `Voidbane`, can be earned by every qualifying player; unique titles are first-claim exclusives and each account can hold only one unique title total through the `pt_unique_claim` cache key.
- `::titles` refreshes retroactive automatic unlocks, opens the title catalog hub, and supports `::titles list [page]`, `::title <name>`, `::title clear`, and `::titles count`. The catalog now uses paged menu controls with selectable Next/Previous buttons, category tabs, and row-click detail pop-ups that show requirements, progress, scope, rarity, and lock/owner state before equipping.
- Custom-client appearance updates now send the active title as an extra string for client version `10052+`; the client renders the username and red active title as one overhead line while leaving `displayName` and `accountName` unchanged. Authentic/retro clients are not sent the new field.
- Documented the subsystem in `docs/subsystems/player-titles.md`. No DB schema changed; this is a custom-client packet-shape change gated by the client version bump to `10052`.

### 2026-06-03 - Void Island starter kits and onboarding polish

Smoothed the one-time Void Island starter choice so each path now grants practical starting items in addition to its permanent XP boost.

Details:
- `VoidPath` now owns path starter-kit definitions and a `void_path_starter_kit` player-cache key so starter items are granted once per account without a schema migration.
- Warrior receives an iron 2-handed sword, bronze armour, and cooked meat; Forager receives gathering tools, bait, 100 coins, and food; Arcanist receives bow, reduced arrows, runes, wizard gear, and food.
- `VoidHerald` now explains that paths include permanent 2x XP plus starter gear, includes kit hints in the options menu for non-custom clients, grants the kit after choice, and sends a post-choice welcome box after teleporting to spawn.
- The custom PC client's three path cards now mention the included starter kits while keeping the same normal menu reply flow. No opcode, DB schema, cache version, or client-version change.
- Documented the subsystem in `docs/subsystems/void-island-starter.md`.

### 2026-06-03 - Subscription cards and Lumbridge Void vendor

Added a Void-styled subscription vendor near Lumbridge spawn and a redeemable subscription card as a progression-support feature.

Details:
- Superseded on 2026-06-05 by the claim-only vendor flow below; the vendor no longer sells cards or opens a shop.
- Added item `1602` (`Subscription card`) with a `Redeem` inventory action and NPC `848` (`Void Subscription Vendor`) with a `Subscribe` interaction.
- The card uses a dedicated AI-generated inventory icon at client sprite id `617` / archive entry `2767`, and the vendor shares the Void Auctioneer's shadow-broker appearance instead of the robe-based acolyte silhouette.
- At introduction time, the vendor opened the native shop window directly and used a custom buy handler for persisted stock/tier purchases instead of dialogue-based confirmation.
- Subscriptions are one-week account unlocks. Redeeming a card stores or extends the existing player-cache expiry timestamp under `void_sub_expires`; legacy boolean `void_subscription` flags are removed instead of granting permanent access.
- Subscribed accounts use at least `10x` global combat XP and `6x` global skilling XP; the base Voidscape development rates are now documented as `7x` combat and `4x` skilling. Higher special-mode rates are not reduced.
- At introduction time, the vendor started each tier with 20 cards, tier 0 cost 10,000 coins per card, and each sold-out tier doubled the next restock price.
- At introduction time, vendor stock and tier persisted in existing `player_cache` global rows using `playerID = 0`; no DB schema migration was added.
- Bumped the custom client/server version to `10054` and raised active custom item caps so the new card is accepted by the client and presets. Version `10054` extends custom `SEND_SHOP_OPEN` with a 32-bit per-item display-price override for exact dynamic shop prices; no opcode changed.
- Bumped the custom client/server version to `10055` for a settings-profile payload extension. The wrench Profile panel now shows whether the account is subscribed and displays the effective global combat/skilling XP rates from the server.
- Documented the subsystem in `docs/subsystems/subscription-cards.md`.

### 2026-06-03 - Void Enclave solo boss upgrade

Upgraded the existing solo-instanced Void Knight beneath the Void Enclave into a more complete boss encounter.

Details:
- `VoidKnightBoss` now has HP-based phases, stronger late-fight melee/magic pressure, visible phase callouts, adaptive void-guard protection in the final phase, phase-scaled Fire Blast pressure, and a Void Siphon special that drains prayer, heals the knight, and can chip the player.
- `DeathMatchArena` keeps the solo-instance shell but adds dodgeable Void Rupture mechanics under the player's feet and distance pressure that discourages edge-stalling without changing packets, DB schema, client version, or arena map data.
- Victory now records normal NPC progression manually for the plugin-owned kill path: total NPC kills, recent NPC kill packet state, per-NPC Void Knight kill cache, `Voidbane`, and kill-count messaging.
- Boss rewards still guarantee coins, supplies, and one rune item, and now add global announcements for rare dragon-component rolls plus a rarer existing Void gear chase roll. `Void-Touched` unlocks on rare Void gear.
- Added missing `ItemId.VOID_SCIMITAR` and included Void Scimitar in the curated default loot-beam list so all Void boss chase gear is beam-worthy.

### 2026-06-03 - Static account portal prototype

Added `web/portal/`, a standalone static click-through prototype for the future Voidscape website/account-management portal.

Details:
- The prototype opens on an operational account dashboard rather than a marketing landing page and includes tabs for character management, subscription status/redeem flow, security, highscores/titles, market intel, activity feed, staff tools, and public news/downloads.
- It uses copied Voidscape visual assets plus downscaled AI-generated transparent UI/art sheets in `web/portal/assets/` to establish the portal art direction without wiring a backend.
- Current behavior is sample-data-only JavaScript for tab switching, character/class selection, segmented-control state, and redeem-card preview feedback.
- No game server behavior, login packet, opcode, database schema, client cache, or account persistence changed.

### 2026-06-03 - Portal landing and account architecture slice

Extended the static portal prototype toward the release website/account-management goal.

Details:
- `web/portal/index.html` now opens on a public Voidscape landing page with early-access founder-pass fields, local referral progress for the free weekly subscription card reward, downloads/news/status surfaces, and a direct path into character and subscription management.
- `web/portal/script.js` now keeps local prototype state for founder passes and character rosters, enforces the planned 10-character web-account cap in the browser prototype, updates selected-character title/location/level/gear/appearance state, and keeps subscription preview state consistent between the sidebar and subscription tab.
- Added `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`, which records the current OpenRSC one-player-row login model, the safe web-account ownership bridge, suggested web account/session/character/entitlement tables, the character-state API contract, and why client-login/protocol changes are deferred.
- Updated `web/portal/README.md` to reflect the landing/founder-pass scope and production architecture doc.
- No game server behavior, login packet, opcode, database schema, client cache, or account persistence changed.

### 2026-06-03 - Portal local API scaffold

Added a zero-dependency local API/server scaffold for the website/account portal so the prototype can exercise real request/response flows before touching OpenRSC login or the game database.

Details:
- `web/portal/dev-server.mjs` serves the portal and exposes local JSON-backed API endpoints for founder reservations, referral reward simulation, web account register/login, bearer-session account reads, character creation previews with a server-enforced 10-character cap, and subscription-card redemption previews.
- The same local API now serves public website payloads for world status, XP rates, founder counts, news, downloads, highscores, market intel, and activity feed. The portal hydrates those surfaces from `/api/public` when available, with static fallback data for direct-file review.
- Portal account passwords are hashed with Node's `crypto.scrypt` and per-password salts; sessions are stored server-side as SHA-256 token hashes. The store defaults to `/tmp/voidscape-portal-api/dev-store.json` unless `PORTAL_DATA_DIR` is set.
- `web/portal/script.js` now prefers the local API when available and keeps the existing `localStorage` fallback for static-file review.
- When `PORTAL_OPENRSC_DB` or `OPENRSC_SQLITE_DB` points at a local OpenRSC SQLite database, the portal can load read-only saved-character snapshots for title, levels, subscription state, last login, location, appearance fields, and wielded gear without mutating game data or changing login behavior.
- Added local character link challenges: authenticated portal accounts can start a hashed one-time `::link <code>` proof flow for an existing OpenRSC save, then use a dev-only simulate endpoint to verify it and merge the saved character into the web-account roster with `linkStatus: linked`.
- Added portal-owned SQLite and MySQL/MariaDB schema drafts under `web/portal/schema/` for web accounts, hashed sessions, up-to-10 character links, link challenges, founder reservations/referrals, entitlements, audit events, and abuse-signal buckets. These are reference web schema files, not auto-applied OpenRSC migrations.
- Added `scripts/run-portal.sh` and `scripts/test-portal-api.sh`; the smoke test covers founder reservation, referral unlock, account registration, saved-character link challenge verification, roster cap enforcement, subscription redemption, account reads, and the read-only saved-character snapshot endpoint.
- Added `scripts/test-portal-schema.sh` to apply the SQLite portal schema draft and verify slot limits, unique character ownership, active founder-name uniqueness, and foreign-key integrity.
- Updated `web/portal/README.md`, `docs/DEVELOPMENT.md`, and `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md` with the local API usage and remaining production gaps.
- No game server behavior, login packet, opcode, database schema, client cache, or OpenRSC account persistence changed.

### 2026-06-04 - Portal account security controls

Extended the local website/account portal with API-backed account security controls.

Details:
- `web/portal/dev-server.mjs` now exposes local-only endpoints for password rotation, recovery-code generation, and ending other active portal sessions. Recovery codes are shown to the user once, stored only as hashes, and older active codes are revoked when a new set is generated.
- The Security tab now hydrates from API state, shows score/checklist/session rows, lets a signed-in account rotate its portal password after confirming the current password, generates one-time recovery codes, and can revoke other bearer sessions while preserving the current one.
- Added `web_recovery_codes` to both portal-owned SQLite and MySQL/MariaDB schema drafts, plus schema smoke-test coverage.
- `scripts/test-portal-api.sh` now covers recovery-code generation, invalid current-password rejection, password rotation, and session revocation.
- This remains a local website prototype: no game server behavior, login packet, opcode, client cache, OpenRSC account persistence, email recovery, or production password-reset flow changed.

### 2026-06-04 - Portal character roster state cards

Polished the account portal's character-management view so character state is easier to scan visually.

Details:
- The Characters tab now renders a 10-slot roster rail, state badges for preview/saved/linked characters, compact loadout tokens for equipped gear, and clearer level/total/title/status hierarchy on each roster card.
- The selected-character dashboard reuses the same gear-token system while keeping full gear names visible there and full item names available as card-token tooltips.
- Desktop and mobile browser checks now verify the character view renders 3 default cards, 10 slot markers, 16 gear tokens, and no horizontal overflow or clipped gear-token labels.
- No game server behavior, login packet, opcode, client cache, OpenRSC account persistence, or portal API contract changed.

### 2026-06-04 - Portal explicit account sign-in mode

Made returning-account access visible on the landing founder card instead of relying on the hidden "account exists, then try login" fallback.

Details:
- The founder card now has a compact Reserve/Sign in segmented control. Sign-in mode hides the reserved-username field, changes the heading and submit text, switches password autocomplete to `current-password`, and calls the existing `/api/accounts/login` endpoint directly.
- Added an explicit `[hidden]` CSS rule so grid-styled labels respect hidden state across the portal.
- Desktop and mobile browser checks verify a throwaway local account can sign in through the visible UI, store a session token, hide the username row, and render with no horizontal overflow.
- No game server behavior, login packet, opcode, client cache, OpenRSC account persistence, or portal API contract changed.

### 2026-06-04 - Portal concept batch and landing gateway

Generated a fresh website concept batch through the user's Chrome/ChatGPT session and folded one concrete direction into the real portal.

Details:
- ChatGPT produced eight text-prompt concept images for Voidscape landing, account dashboard, roster, market, highscores, subscription, and security surfaces. They were saved under `.codex-artifacts/chatgpt-concepts/` for review; local reference-file upload failed because Chrome extension file access is disabled, so these concepts did not receive the actual local assets.
- The landing page now includes a six-card portal gateway row for roster, subscription, security, market, titles, and wilderness feed, plus a dedicated referral reward band for the free one-week subscription-card prize.
- Desktop and mobile browser checks verify the landing view renders six gateway cards, the reward band, no horizontal overflow, and no clipped gateway text.
- No generated concept image was shipped into `web/portal/assets/`; the implementation is real HTML/CSS using existing Voidscape assets.
- No game server behavior, login packet, opcode, client cache, OpenRSC account persistence, or portal API contract changed.

### 2026-06-04 - Portal invite-code referral flow

Replaced the founder card's referral prototype with a real local invite-code path while preserving the dev-only simulate shortcut for quick testing.

Details:
- Landing links with `?ref=<code>` are now captured into local portal state, shown as an active invite code on the founder card, and sent as `referrerCode` when reserving early access or creating a local portal account.
- `web/portal/dev-server.mjs` now records credited referral pairs in the JSON store, rejects unknown/self-referral codes, prevents duplicate referral-pair credits, exposes the referred-by state in founder responses, and grants the free weekly subscription-card entitlement when a founder reaches two real local referrals.
- `scripts/test-portal-api.sh` now verifies invalid-code rejection, self-referral rejection, two real referred reservations, founder reward unlock, and the existing account/character/security/subscription flows. `scripts/test-portal-schema.sh` also checks duplicate referral-pair and self-referral constraints in the SQLite schema draft.
- This remains a local website/account-management prototype: no game server behavior, login packet, opcode, client cache, OpenRSC account persistence, email verification, or production abuse/risk scoring changed.

### 2026-06-04 - Portal character paper-doll preview

Added a lightweight character-state renderer to the account portal dashboard so selected characters read as more than generic class art.

Details:
- The selected-character panel now renders a compact CSS paper-doll with stable dimensions, appearance colors, cape/weapon silhouettes, and a source badge layered over the existing RSC class sprite as a faint reference.
- Starter characters created locally or through the local portal API now carry default appearance data; linked OpenRSC snapshots preserve `appearanceData` and wielded `equipment` in account responses and browser local state.
- Gear categorization now treats arrows/bows as ranged and weapon names before generic rune/magic matching, so items like `Rune 2h sword` drive the correct melee silhouette while rune stacks still categorize as magic.
- `scripts/test-portal-api.sh` now asserts starter appearance data plus linked saved appearance/equipment state. Browser QA captured desktop and 390px mobile dashboard screenshots with no horizontal overflow and no console errors.
- This remains a visual website/account-management prototype: no game server behavior, login packet, opcode, client cache, OpenRSC account persistence, or true RSC sprite-composition renderer changed.

### 2026-06-04 - Portal founder reward wallet

Made the early-access referral prize visible and consumable inside the local account portal.

Details:
- Account API responses now include a `rewards` summary for granted founder free weekly subscription-card entitlements, and the Subscription tab shows a founder reward wallet with ready/empty states.
- Added `POST /api/subscriptions/redeem-founder`, which consumes a granted founder reward entitlement once, extends the account subscription by seven days, records consumption timing on the entitlement, and returns the refreshed account state.
- The Subscription tab now updates its title and meter from actual account subscription state, so unsubscribed accounts show an empty meter and active subscriptions show a full local-prototype meter.
- `scripts/test-portal-api.sh` now verifies founder reward wallet state, one-time founder reward redemption, no double-spend, and normal redeem-code stacking after the founder reward.
- Browser QA covered the ready and consumed reward states on desktop plus the consumed state at 390px mobile width with no horizontal overflow and no console errors.
- This remains a local website/account-management prototype: no game server behavior, login packet, opcode, client cache, OpenRSC account persistence, production payment flow, or real subscription-card item redemption changed.

### 2026-06-04 - Portal local download links

Connected the public website's download cards to the local build artifacts produced by Voidscape's wrapper scripts.

Details:
- `/api/public` now reports dynamic download metadata for `Client_Base/Open_RSC_Client.jar` and `PC_Launcher/OpenRSC.jar`, including availability, size, timestamp, and local download URLs when the jars exist.
- Added local `GET /downloads/pc-client` and `GET /downloads/launcher` endpoints that serve the built jars with attachment headers. Missing artifacts return a local `download_not_built` error instead of a dead link.
- The public download cards render as real links when artifacts are available and disabled status cards when they are not, while Android notes and server status remain informational placeholders.
- `scripts/test-portal-api.sh` now checks download availability metadata and verifies the PC client artifact endpoint when a jar is present. Browser QA covered the download cards on desktop and 390px mobile widths with no horizontal overflow and no console errors.
- This remains a local website/account-management prototype: no launcher updater, production CDN, release signing, game server behavior, login packet, opcode, or client cache behavior changed.

### 2026-06-04 - Launcher layered asset skin polish

Reworked the PC launcher around separately generated Voidscape art layers instead of a static mockup or generic Swing controls.

Details:
- `VoidscapeLauncherWindow` now draws the background GIF, logo, generated Play button states, top action icons, and window controls as individual layered assets, with transparent hit zones mapped over the generated art.
- The generated news and updater-panel assets were removed; the launcher now draws a simpler coded updater panel so status text and progress do not collide with baked artwork.
- Only the top utility icons keep the yellow hover/click outline. The Play button now swaps generated normal/hover/pressed/release images instead, and the coded updater area plus close/minimize controls no longer draw the yellow highlight.
- Dynamic updater text now uses the fantasy display font stack with a small shadow and lower placement inside the coded status panel.
- Animated launcher backgrounds can be tested with `VOIDSCAPE_BACKGROUND_ANIMATION=/path/to/background.gif`, loaded from `~/.voidscape/client/launcher/background.gif`, or bundled as `images/voidscape/layered/background.gif`. The static PNG remains the fallback.
- No game server behavior, login packet, opcode, or client cache protocol changed.

### 2026-06-05 - Hairstyle sprite art tooling

Added a local tooling pipeline for AI-assisted player hairstyle sprites without changing live game content.

Details:
- `tools/hairstyle-art/hairstyle_tool.py` can extract an existing 18-frame head animation into AI and labeled grids, write a ChatGPT prompt, import a returned grid, normalize pixels into RSC hair/skin masks, validate frame dimensions and mask counts, render recolor previews, and dry-run pack frames into an `.orsc` archive.
- `tools/hairstyle-art/README.md` documents the workflow and the legacy mask rules: grayscale pixels recolor as hair/clothing, `R=255 && G=B` pixels recolor as skin, and zero pixels remain transparent.
- `docs/recipes/add-custom-hairstyle.md` records the integration checklist for turning a finished art sheet into an actual character-creation option.
- A first `head1` reference/prompt bundle was generated under ignored `tools/hairstyle-art/out/` for local experimentation.
- No client/server code, DB schema, packet, opcode, client cache, client-version, or live sprite archive change was added.

### 2026-06-05 - Hairstyle player-scale preview gate

Extended the local hairstyle art tool with an in-game-scale player preview before live cache packing.

Details:
- Added `hairstyle_tool.py player-preview`, which composes candidate head frames onto the real base player body and legs using the client sprite shift metadata, 64x102 player draw size, layer order, and mask recolouring rules.
- The preview sheet renders all 18 poses across default blonde, black, and purple hair colours, with zoomed poses plus actual-size strips so silhouette and head attachment issues are visible before touching `Authentic_Sprites.orsc`.
- Updated the custom hairstyle recipe to make player-scale previewing the gate before any pack/commit step.
- Restored the live server and local client sprite archives back to the original `head1` frames after the rejected whacky merge test.
- No DB schema, packet, opcode, client-version, or shipped gameplay behavior changed.

### 2026-06-05 - Hairstyle wig compositor workflow

Reworked the hairstyle art pipeline around a locked bald base plus transparent hair-only frames so generated art cannot deform the face, neck, or head attachment point.

Details:
- Added `wig-bootstrap`, `import-wig-grid`, `wig-validate`, `wig-preview`, `wig-live`, and `wig-bake` commands to `tools/hairstyle-art/hairstyle_tool.py`.
- `wig-bootstrap` extracts immutable bald-base frames and a hair-only canvas from an existing authentic head style, then writes a hair-only ChatGPT prompt and grid.
- `wig-preview` and `wig-live` composite the hair onto the locked base and render it through the same client-scale paperdoll preview used by `player-preview`; `wig-live` serves an auto-refreshing local browser page for save-and-check pixel iteration.
- `wig-bake` outputs normal full-head frames only after the transparent hair canvas passes validation and preview.
- Added `docs/subsystems/player-appearance-rendering.md` to document the server appearance array, client paperdoll renderer, mask-colour rules, and why whole-head AI redraws are unsafe.
- Updated `tools/hairstyle-art/README.md`, `docs/recipes/add-custom-hairstyle.md`, and `docs/CODEMAP.md` so the wig compositor is the default custom hairstyle workflow.
- Generated a local ignored proof bundle under `tools/hairstyle-art/out/wig_head1_locked_test/`, including a preview that reconstructs authentic `head1` hair over the locked bald base.
- No live sprite archive, DB schema, packet, opcode, client-version, or shipped gameplay behavior changed.

### 2026-06-05 - Manual wig frame editor

Added a local browser-based pixel editor for hand-authoring Voidscape hairstyle hair layers when image generation cannot preserve the RSC frame constraints.

Details:
- Added `hairstyle_tool.py wig-editor`, which serves a local HTML canvas editor backed by the existing wig manifest and `hair-dir` frame format.
- The editor shows every locked bald head frame, edits a transparent hair layer, provides legal hair-mask swatches, paint/erase/pick tools, per-frame nudge/copy/clear controls, PNG import for the current frame, save-to-disk, validation, and player-scale preview refresh.
- Missing hair frames are created as transparent PNGs with sidecar metadata copied from the locked base, so a clean manual project can start from only a `wig_manifest.json`.
- Server-side saves sanitize the canvas into RSC-safe grayscale hair-mask pixels before writing frame PNGs, keeping the existing `wig-validate`, `wig-preview`, and `wig-bake` flow intact.
- Updated `tools/hairstyle-art/README.md` and `docs/recipes/add-custom-hairstyle.md` to document the manual workflow.
- No live sprite archive, DB schema, packet, opcode, client-version, or shipped gameplay behavior changed.

### 2026-06-05 - Pre-release portal landing rebuild

Rebuilt the portal's first Account view into a layered pre-release landing page based on the generated Voidscape launch mockup.

Details:
- Added a shipped `web/portal/assets/prelaunch/` asset set with chroma-keyed foreground PNG layers, generated claim-button normal/hover/pressed states, an optimized audio-free H.264 MP4 background, and a poster fallback.
- Reused the launcher stone-and-void `Voidscape` logo on the landing page so the website matches the downloadable client launcher branding.
- Replaced the plain Account hero with a full-screen pre-release scene that keeps the real `founder-form` account fields wired to the local account API while hiding the portal sidebar/topbar only on the landing view.
- Added responsive fallback layout rules so the ornate generated form frame is used on desktop and a stacked coded form panel is used on narrow screens.
- The existing Characters, Subscription, and Security account-management views remain available internally, but the focused landing page no longer shows shortcut links to them.
- No OpenRSC game server behavior, DB schema, packet, opcode, client cache, launcher behavior, or gameplay system changed.

### 2026-06-05 - Founder card vendor claim flow

Changed the pre-release claim flow so a website signup reserves a physical subscription card that is collected in-game from the Lumbridge Subscription Vendor instead of starting subscription time on the website.

Details:
- The pre-release form now requires a portal password for claim, then swaps the ornate signup panel into a confirmation state telling the player to claim the card from the Subscription Vendor in Lumbridge.
- The local portal API grants one `founder_free_subscription` entitlement on pre-release account creation and rejects the old web-side founder redemption endpoint with `claim_founder_card_in_game`.
- When `PORTAL_OPENRSC_DB` points at a local game SQLite database, the portal writes a global `player_cache` marker named `founder_sub_card:<normalized username>` with value `1`.
- The Lumbridge Void Subscription Vendor checks that marker when spoken to, gives the matching character one physical Subscription card if they have a free inventory slot, then marks the marker value `2` so it cannot be claimed again.
- Redeeming the physical card remains unchanged: only using the item starts or extends the 7-day subscription timer.
- No packet, opcode, client cache, client-version, item definition, or NPC definition changed.

### 2026-06-05 - Google-first pre-release reservation flow

Simplified the website pre-release claim UX so players reserve a desired username with Google instead of creating an email/password portal account.

Details:
- The landing form now shows only one text field, `Username to reserve`, plus a `Login with Google` button.
- The desktop landing form now uses the same coded panel as the responsive layout instead of the generated form-frame asset, avoiding baked-in email/password art after the Google-only pivot.
- Added subtle CSS motion to the pre-release scene: background/video breathing, logo/card idle movement, side-character drift, panel glow, and a Google button sheen with a reduced-motion fallback.
- Restored the real animated desktop background by serving MP4 files as `video/mp4` with byte-range support and by layering the video above the static fallback instead of behind negative z-index background layers.
- The reservation success state now offers both `Download launcher` and `Manage characters`, so the pre-release flow has an explicit route into the character manager after Google reservation.
- Removed the unrelated generated art strip from the character manager and clarified newly-created game-save badges as `Created save` / `created game save`.
- The Subscription view now shows account-specific effective XP rates and reveals the founder reward wallet when a free Lumbridge card is reserved.
- The local dev Google endpoint can synthesize a `@google.voidscape.local` identity from the requested username, so local testing no longer needs a visible email field.
- Google reservation requests now reject missing or invalid reserved usernames instead of silently generating a random fallback name.
- Successful Google reservations still create/load the portal account, reserve the founder username, create the starter portal character preview, grant the founder subscription-card entitlement, and write the `founder_sub_card:<normalized username>` marker when `PORTAL_OPENRSC_DB` is configured.
- A reserved-name preview card can now be converted into the real OpenRSC character with the same username, preserving the portal roster slot instead of blocking creation or duplicating the character count.
- The character manager pre-fills the creation name from the selected reserved-name card, making the post-reservation `Manage characters` handoff direct.
- Added portal smoke coverage for invalid Google usernames, cross-account reserved-name collisions, and reserved Google name conversion into a real OpenRSC-created save.
- The desktop pre-release MP4 background is fixed to the viewport and explicitly resumed when the landing view is active, so desktop refreshes/navigation do not fall back to a static-looking scene.
- The success panel now confirms the reserved username and directs the player to claim the free card from the Lumbridge Subscription Vendor.
- No OpenRSC packet, opcode, client cache, client-version, item definition, NPC definition, or in-game command changed.

### 2026-06-05 - Founder card live-client claim verification

Added a repeatable AI workbench scenario for testing the in-game founder subscription-card handoff end to end.

Details:
- `GET /state` now reports native shop visibility and whether the open shop contains subscription card item `1602`, giving automated checks a real client-side assertion beyond screenshots.
- Added `POST /scenario/subscription-vendor-claim`, which teleports the logged-in workbench account to the Lumbridge Void Subscription Vendor, sends the same client NPC command packet used by the real `Subscribe` menu action, waits for the native shop to open with item `1602`, and saves before/after screenshots.
- Live local verification seeded `founder_sub_card:void = 1`, ran the scenario as the saved admin account, confirmed the marker changed to `2`, confirmed exactly one item `1602` was saved in inventory, then reran the scenario and confirmed the shop still opened without granting a second free card.
- The full-inventory branch was also verified by filling `void`'s inventory, resetting the marker to `1`, rerunning the scenario, and confirming the marker remained available, the saved subscription-card count stayed at one, and the in-client warning told the player to free an inventory slot.
- Updated the AI workbench and subscription-card subsystem docs with the local regression recipe.
- No server packet, opcode, DB schema, item definition, NPC definition, client cache, or client-version changed.

### 2026-06-05 - Pre-release game-login onboarding

Completed the website handoff from Google username reservation into a playable local game login. After `Login with Google` reserves the requested username, the landing panel now asks for a 4-20 character game password, calls the existing `/api/characters` bridge with the reserved name, converts the reserved roster card into the real OpenRSC SQLite save when `PORTAL_OPENRSC_DB` is configured, then reveals the launcher download once the game login exists.

Details:
- The pre-release success panel now has two states: `Password needed` while the reserved roster card is still preview-only, and `Ready in-game` after the OpenRSC player row has been created.
- The launcher download and character-manager action are hidden until the reserved username has a real game password, so the player flow is Google reserve -> set game password -> download launcher -> log in with the same username/password.
- Desktop and mobile browser QA verified the onboarding state, invalid-password validation, final launcher handoff, active animated MP4 background, and zero horizontal overflow.
- Local SQLite verification confirmed the created test players have current-client-compatible password hashes/salts, initialized stat rows, and the reserved founder subscription-card marker.
- No OpenRSC packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or in-game behavior changed.

### 2026-06-05 - Claim-only subscription vendor

Changed the Lumbridge Void Subscription Vendor from a paid card shop into a pure reserved-card pickup NPC. Talking to the vendor or right-clicking `Subscribe` now checks the `founder_sub_card:<normalized username>` global cache marker, grants one physical Subscription card only when the marker is available, and otherwise tells the player that no subscription card is ready for that character.

Details:
- Removed the vendor's shop-opening path, stock state, price tiers, coin purchase handler, and sell-back behavior.
- Full-inventory claims still leave the marker available and tell the player to free an inventory slot.
- Updated the AI workbench vendor scenario so opening a shop is treated as a failure for this NPC.
- Updated subscription-card, workbench, beta guide, and release checklist docs to match the claim-only flow.
- No OpenRSC packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or subscription-card redemption behavior changed.

### 2026-06-05 - Minimal portal character dashboard

Simplified the web character manager so it only creates and displays game logins. The Characters page now shows each character as a real saved avatar render plus name and level, and the create form only asks for username and game password; starter path, spawn choice, appearance setup, and onboarding are handled inside the game client.

Details:
- Removed the visible starter-path cards, spawn selector, roster rail, linked-state badges, gear-token loadouts, and detailed character readout from the active Characters view.
- The Google pre-release handoff and manual character creation now send only username/password to the API; local preview fallback uses an RSC sprite image instead of exposing web-side class choice.
- When `PORTAL_OPENRSC_DB` is configured, account reads refresh linked or portal-created characters from the OpenRSC SQLite save before returning `/api/account`, so a logout-saved appearance, equipment, combat level, total level, and avatar image update the dashboard.
- The portal now serves existing game-server avatar PNGs from `server/avatars/<db>+<playerId>.png` through a numeric-ID-only `/openrsc/avatar/<playerId>.png` route and falls back to the existing RSC class sprites until the first generated avatar exists.
- Local Voidscape config enables OpenRSC's avatar generator so logging out writes the real character PNG used by the account dashboard.
- Updated portal docs to describe the intentionally minimal character manager and the live-save refresh behavior.
- No OpenRSC packet, opcode, DB schema, client cache, client-version, launcher binary, or in-game onboarding behavior changed.

### 2026-06-05 - Minimal account portal flow

Reworked the signed-in account-management shell so the Account navigation item opens a dashboard home instead of returning to the pre-release landing page.

Details:
- The authenticated Account dashboard now shows only account identity, subscription state, character count, reserved-card state, security state, and direct links to Characters, Subscription, and Security.
- Character roster cards include the saved character render, name, level, and subscription state, keeping the account list scannable without reopening a detailed character panel.
- The Subscription view was reduced to account status plus whether a reserved founder subscription card is waiting at the Lumbridge Subscription Vendor.
- The Security view keeps live recovery-code generation and session cleanup, but hides password controls for Google-only accounts so it no longer advertises controls that cannot apply.
- No OpenRSC packet, opcode, DB schema, client cache, client-version, launcher binary, or in-game behavior changed.

### 2026-06-06 - Combat formula simulator harness

Added a local measurement harness for the active classic OpenRSC combat formula path before any balance edits.

Details:
- `scripts/combat-sim.sh` runs `tools/combat-sim/combat_sim.py`, a standalone Python simulator that mirrors the current melee accuracy/damage, ranged accuracy/damage, magic success/damage, melee capes, and the main melee cadence branches.
- Built-in scenarios cover low-level PvM, rune PvM against a Lesser Demon, maxed rune 2h PvP under both `3-1` and `2-2`, ranged pressure against a Lesser Demon, and Fire Wave PvP pressure.
- The simulator supports custom JSON scenarios and text or JSON output, so formula changes can be compared before and after without starting a server.
- Updated the combat subsystem docs, combat-tuning recipe, and code map to make the simulator part of the standard combat-change workflow.
- No OpenRSC packet, opcode, DB schema, client cache, client-version, item definition, NPC definition, launcher binary, or live in-game behavior changed.

### 2026-06-06 - Voidscape combat formula tuning

Replaced the inherited OpenRSC physical armour model with a Voidscape armour split: armour now contributes 60% of its value to avoidance and also mitigates physical hit damage by `min(24%, armourPoints / 1200)`. Player-target magic damage is scaled to 92% after the spell roll, while NPC-target magic remains unchanged.

Details:
- `CombatFormula` now applies the armour split to melee and ranged accuracy/damage, preserving non-zero successful physical hits at a minimum of 1 damage.
- `SpellHandler` now passes the target into combat spell damage helpers so Fire Wave, Iban Blast, god spells, and other combat spells receive player-target tuning consistently.
- The combat simulator now supports both `openrsc` and `voidscape` rulesets and expands the built-in matrix across early PvM, mid PvM, dragons, PvP archetypes, ranged pressure, and magic pressure.
- Added `docs/COMBAT-TUNING-REPORT.md` with rationale and before/after simulator metrics.
- No server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, or launcher binary changed.

### 2026-06-06 - PvP melee big-hit momentum

Added a PvP-only melee momentum rule to preserve RSC's satisfying chained-big-hit moments after the armour/burst tuning pass. A player-vs-player melee hit at or above 75% of the target-adjusted max hit grants one momentum stack against that same target; the attacker's next successful melee hit against that target rolls damage twice and keeps the higher roll, then consumes the stack. A miss or target swap clears it.

Details:
- The rule lives in `CombatFormula` and only applies when both melee combatants are players. PvM, ranged, magic, and multi-player pile behavior are unchanged.
- The combat simulator mirrors the rule in the `voidscape` ruleset while `--rules openrsc` remains a no-momentum baseline.
- Updated the combat subsystem docs, simulator README, and combat tuning report with the rule and simulator results.
- No server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, or launcher binary changed.

### 2026-06-06 - Portal-hosted launcher update manifest

Promoted launcher auto-updates from a static beta packaging trick into a portal-hosted release path. When the launcher has a portal URL but no explicit manifest URL, it now derives `<portalUrl>/api/launcher/manifest.properties`; the portal dev API serves that manifest with SHA-256 entries for the built PC client jar and non-runtime `Client_Base/Cache` files.

Details:
- `VoidscapeUpdater` continues to verify each manifest entry by SHA-256 and atomically replace changed files before launching the cached client.
- The portal serves `/downloads/pc-client` plus safe `/downloads/cache/...` paths used by the manifest, while refusing traversal and runtime cache files such as credentials, endpoint files, `hideIp.txt`, and launcher settings.
- `HEAD` smoke checks work for the manifest and jar download endpoints, making deploy verification simple behind HTTPS/proxies.
- Updated launcher, portal, cache, and operations docs with the portal-hosted and static-beta update flows.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client-version, or live combat behavior changed.

### 2026-06-07 - Focused Android auth smoke

Added a narrow Android login verifier so emulator auth failures can be diagnosed before running the full authenticated UI suite.

Details:
- `scripts/android-smoke.sh --only-auth-login` defaults to the local `AndroidMap/androidmap1` fixture and `server/inc/sqlite/voidscape.db`, verifies the SQLite password hash, checks that the launcher selected `10.0.2.2:43596`, submits credentials through Android input, waits for the successful login response, checks immediate Android runtime crash logs, and force-stops cleanly.
- The Android emulator docs now point auth debugging at this focused path before bank/shop/world-map or other deep in-game smoke modes.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or live gameplay behavior changed.

### 2026-06-07 - Android lifecycle relaunch hardening

Hardened Android activity/render ownership so successful login followed by launcher resume or relaunch does not start duplicate game clients or crash on stale renderer state.

Details:
- `CacheUpdater` now ignores repeated `Play` taps once launch has begun and disables the button while the selected endpoint is being written.
- `GameActivity` now asks the active shared `mudclient` to disconnect/stop when the Activity is destroyed, interrupts and briefly joins the old game thread, and nulls Activity-owned input/render references after teardown.
- The shared client startup loop now exits cleanly while waiting for initial configs if `threadState` is driven negative during Android teardown.
- `RSCBitmapSurfaceView` now guards drawing until `MudClientGraphics.pixelData` and sane dimensions exist, avoiding startup races where the Android view is asked to draw before the classic framebuffer is ready.
- `scripts/android-smoke.sh --only-auth-lifecycle` verifies login, welcome close, Home/background, launcher resume, duplicate launcher relaunch, logout, post-logout keyboard input, and crash-free logcat.
- PC and Android login parity screenshots were captured through the PC workbench and Android lifecycle smoke; Android intentionally keeps `Create Account` and larger touch targets while preserving the branded Voidscape background/panel.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or live gameplay rule changed.

### 2026-06-07 - Android screen-profile coverage

Added small-phone and tablet-ish emulator coverage to catch layout and tap-scaling issues beyond the baseline medium phone.

Details:
- Created local AVD profiles `voidscape_small_api35` (`768x1280 @ 320dpi`) and `voidscape_tablet_api35` (`2560x1600 @ 320dpi`) using the installed Android 35 Google APIs ARM64 image.
- `scripts/android-smoke.sh --only-auth-lifecycle` now dismisses Android's OS-owned fullscreen education card before tapping login controls.
- Android smoke credential entry now taps the username/password fields explicitly instead of relying on Enter to move focus, which fixed small-profile login entry.
- Android lifecycle logout now uses classic client coordinates for the wrench/settings tab and logout button, which fixed tablet-profile scaling where screen-percent taps missed the control.
- The crash detector now fails only on app-relevant fatal exception blocks, avoiding false failures from emulator system-service noise such as NFC warnings.
- Verified lifecycle smoke on `voidscape_small_api35` and `voidscape_tablet_api35`; both completed login, resume, duplicate launcher relaunch, logout, post-logout keyboard, and crash checks.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or live gameplay rule changed.

### 2026-06-07 - Android launch branding polish

Finished the Android OS-facing launch identity so the app icon and first app-owned frames match the Voidscape wrapper instead of exposing legacy/default Android surfaces.

Details:
- Added an Android 8+ adaptive launcher icon resource that pairs a dark background with the Voidscape cracked-`V` foreground while keeping the legacy density PNGs as fallback.
- Added a `VoidscapeLaunchTheme` on the exported `ApplicationUpdater` Activity, with Android 12+ splash attributes for a centered Voidscape launch icon and a dark Voidscape scene window background for the handoff into the updater layout.
- Avoided using a centered bitmap layer in the generic window background after emulator screenshots showed it could clip during the portrait-to-landscape transition.
- Verified `scripts/build-android.sh`, manual cold-start/icon screenshots in `/tmp/voidscape-android-launch-branding-v3/manual`, and the normal wrapper/login smoke in `/tmp/voidscape-android-launch-branding-v3-smoke`.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or live gameplay rule changed.

### 2026-06-07 - Content factory first slice

Added the first supported content-authoring layer so custom items, NPCs, bosses, arenas, textures, and ChatGPT-assisted art can start from one repeatable workflow instead of scattered hand edits.

Details:
- Added `scripts/content.sh` as the canonical wrapper for `tools/voidscape-content` plus the existing `tools/voidscim-art` item-sprite pipeline.
- Added content-pack scaffolding under `content/custom/<slug>/` with `content.yaml`, notes, art prompt/source/working/final folders, and a reusable Voidscape ChatGPT art brief.
- Added `scripts/content.sh report` for current safe allocation points; the first report shows next item id `1603`, next NPC id `852`, and next item spriteID `618` / archive index `2768`.
- Added `scripts/content.sh validate` guardrails for sequential server item/NPC IDs, client/server content coverage, custom item sprite archive availability, content-pack shape, and client version visibility. Historical OpenRSC/Voidscape drift is reported as warnings; new tail drift that would strand beta clients remains an error.
- Documented the content pipeline in `docs/subsystems/content-pipeline.md`, updated the development/code-map docs, and added persistent memory for the new workflow convention.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client cache, client-version, launcher binary, or live gameplay rule changed.

### 2026-06-07 - Concept-driven desktop HUD skin

Rebuilt the desktop in-game HUD around the ChatGPT concept-art pass instead of the classic OpenRSC tab strip.

Details:
- Added lightweight generated PNG chrome/icons under `Client_Base/Cache/voidscape/ui/skin/` and listed them in `Client_Base/Cache/MD5.SUM` so cache packaging/updaters can carry the skin.
- Desktop custom UI now draws a 4:3 concept-style HUD: top-left location plaque, top-right ornate icon tabs plus coin panel, framed bottom chat box/tabs, and a large right-side stats/skills/equipment panel.
- The PC client default viewport is now `1024x768` via `OpenRSC` and `ScaledWindow`, matching the concept's 4:3 shape while staying practical for beta testers.
- When a server enables `want_custom_ui`, fresh accounts now default to custom UI on desktop; Android ignores this desktop skin and keeps its separate mobile UI path.
- Verified `scripts/build.sh` and PC workbench screenshots at `1024x768`, including login, default HUD, and stats panel.
- No OpenRSC server packet, opcode, DB schema, item definition, NPC definition, client-version, launcher binary, or live gameplay rule changed.

### 2026-06-07 - Concept HUD layout pass (iteration)

Tightened the concept-driven desktop HUD against the in-game concept art (deep-wilderness reference) after the first pass rendered too large with cut-off chrome.

Details:
- Fixed the bottom chat-tab cutoff: the message tabs were drawn at `gameHeight - 5` into 30px sprites that spilled off the bottom edge. Rebuilt them as a full-width row of five ornate tabs (`All Messages`, `Chat History`, `Quest History`, `Private History`, `Report Abuse`) that sit fully inside the buffer, with a single source-of-truth `voidscapeChatTabRect()` shared by draw, click hit-testing, and the chat-frame reposition.
- Repositioned the chat frame, message panels, input line, and the floating ALL-tab messages to live inside the new frame above the tab row; pulled the right STATS & SKILLS panel up and made it snug to its content (no empty bottom gap).
- Reworked the STATS & SKILLS panel to match the concept: a two-column STATS grid (Combat XP / Skill XP rates, Total Level, Total XP | Combat Lvl, Quests, Days Played — all real client fields), single-line skill rows split combat-left / non-combat-right with right-aligned levels, and a 3+2 EQUIPMENT STATUS block (`Armour`, `Weapon Aim`, `Weapon Power` | `Magic`, `Prayer`). Skill hover detail is now a floating tooltip instead of a fixed footer.
- Location plaque now reads `WILDERNESS` / `DEEP WILDERNESS` (level ≥ 30) with the wilderness level + coords when in the wild; suppressed the classic bottom-right skull/`Wilderness Level` overlay for the voidscape skin so it no longer collides with the chat tabs.
- Raised `getUITabsY()` for the voidscape skin so the still-classic inventory/magic/friends/quest panels render clear of the bottom chat tab row (those panels are not yet skinned to the concept — tracked as a follow-up; the concept only specifies the stats panel).
- Verified via the AI workbench at `1024x768`: login, default HUD, stats/skills (low-level and maxed), inventory, magic, and minimap tabs, plus a deep-wilderness scenario (`setstats 60` + `tele`).
- Still open: the on-screen window size (engine renders the HUD at a fixed `1024x768` and only scales up, so a physically smaller window needs a separate scaling change) and skinning the non-stats tabs. No server packet, opcode, DB schema, definition, client-version, launcher binary, or gameplay rule changed.

### 2026-06-07 - Concept HUD: skin remaining side-panel tabs

Extended the voidscape desktop HUD skin from the stats panel to the rest of the side-panel tabs so the whole in-game UI shares the concept's ornate dark-metal / purple-gem aesthetic.

Details:
- Added a shared `drawVoidscapePanelChrome()` (frame + dark bg + title bar) reused by every tab.
- Skinned and repositioned into the top-right ornate panel (matching the stats panel): Inventory (themed slots + equipment sub-tab), Magic & Prayer (spell/prayer lists), and Friends/Ignore. Each draws plus its mouse hit-testing and the relevant `Panel` widget (`repositionCustomUI`) were aligned to the new origin; the friends "Remove" column and click bands were re-expressed relative to the panel origin instead of `getGameWidth()`.
- Restored quest access (the original voidscape stats panel had dropped the skills/quests sub-tab): added a SKILLS/QUESTS toggle to the stats panel and a `drawVoidscapeQuestList()` quest journal in the concept style.
- Options/settings (opened via the coin panel) is intentionally left in the classic style for now — kept internally consistent (widget anchor reverted to classic) rather than half-skinned. Tracked as a follow-up.
- Reused existing `Client_Base/Cache/voidscape/ui/skin/` chrome plus in-game item/rune/equip sprites — no new art assets generated, so no `MD5.SUM`/cache changes.
- Verified via the AI workbench at 1024x768 (deep-wilderness, maxed stats, spawned inventory): skills, quests, inventory, magic, prayer, friends, and minimap tabs all render in the cohesive ornate panel; magic/prayer and skills/quests toggles confirmed working. `scripts/build.sh` passes. No server packet, opcode, DB schema, definition, client-version, launcher binary, or gameplay rule changed.

### 2026-06-07 - Concept HUD: top-bar order, iron icons, panel-under-icon, Options skin

Iterated the desktop HUD top bar and side panels on user feedback.

Details:
- Re-ordered the top tab bar to the authentic-style order (left to right: Options, Friends, Stats, Magic/Prayer, Minimap, Inventory) via a single source-of-truth `VOIDSCAPE_TOP_TAB_ORDER`/`VOIDSCAPE_TOP_TAB_ICONS` used by draw + click hit-testing.
- Generated two iron-style ornate icons (gpt-image-1.5, forced into the existing pewter palette) for Inventory (leather satchel) and Options (cog), replacing the misleading sword/flat-gear; coin readout is now display-only.
- Each side panel now opens directly under its own top icon (`voidscapeRightPanelX()` anchors to the active tab's icon, clamped on-screen); the bottom chat frame uses a stable `voidscapeRightPanelHomeX()` so it doesn't resize when the active panel moves.
- Widened the friends/magic/quest list content to fill the ornate panel width instead of floating at the legacy 196px.
- Skinned the Options/Profile panel into the ornate frame under the gear icon (dark themed boxes, readable text). Fixed the settings click hit-testing for the new position: `handleSocialSettingsClicks` recomputed its own `panelTop` from `getUITabsY()-240`, so it was re-pointed at the voidscape panel top to keep toggle clicks aligned with the drawn rows (verified Camera angle / Mouse buttons toggles).
- Reused existing chrome + two new top-icon PNGs under `Client_Base/Cache/voidscape/ui/skin/` (`top-bag.png`, `top-gear.png`); not added to `MD5.SUM` yet (pending final art sign-off).
- Verified via the AI workbench at 1024x768. Remaining: typography pass. No server packet, opcode, DB schema, definition, client-version, launcher binary, or gameplay rule changed.

### 2026-06-08 - Concept HUD: authentic menu behaviour + chrome/Options polish

Quality + faithful-behaviour pass on the desktop voidscape HUD.

Details:
- Menu now mirrors authentic RSC: side panels open on HOVER over the top icon, auto-close when the mouse leaves the icon+panel region, and only one panel shows at a time. Rewrote `handleVoidscapeHudSkinTabClick` (now hover-driven, every frame) + added `mouseOverVoidscapeActivePanel()` / `voidscapePanelHeightFor()` for the keep-open bounds.
- Minimap is mutually exclusive again (driven by `showUiTab == MINIMAP_AND_COMPASS_TAB` for the skin, not the persistent `drawMinimap` flag) — it no longer stays open over other panels.
- `drawVoidscapePanelChrome` dark fill now reaches the frame's inner bottom edge (right-panel-frame.png has ~4.3% top/bottom, ~7.8% side borders); fill tucks under the bottom border so no gameplay peeks through and the corner gems still draw on top.
- Options/Profile panel: widened content to the panel inner width, replaced the gear sub-icon with a second text tab ("Profile | Settings", equal halves) — Settings opens the existing advanced-settings window; snugged the panel height. Fixed the settings hit-test gate/split for the wider voidscape panel.
- Verified via AI workbench: hover-open, auto-close, minimap exclusion, no panel bottom gaps, Options tabs + toggles. `scripts/build.sh` passes. No server packet, opcode, schema, definition, client-version, launcher, or gameplay change.

### 2026-06-08 - Concept HUD: menu fixes, map placement, options/chat polish + critique tool

- Fixed Magic↔Prayer and Friends↔Ignore sub-tab switching: the click hit-gates were hardcoded to 196px but the voidscape panels were widened, so the right tab was only clickable in a narrow strip. Hit-split + gates now use the panel width.
- Minimap now opens directly under its top icon (`voidscapeMinimapX()`), not pinned far-right.
- Options "Profile" rows: values are right-aligned to the panel edge (new `drawSettingsRow` helper) so rows fill the container; classic UI unchanged.
- Chat box interior is now solid-dark across the full chat area so messages read clearly instead of fighting the game world behind them.
- Added `scripts/ui-critique.py`: sends a HUD screenshot to the OpenAI vision API for an actionable design critique, to drive iteration.
- Verified via workbench. `scripts/build.sh` passes. No server/packet/gameplay change.

### 2026-06-08 - Smaller default window with sub-1.0 downscale

The desktop client now opens at a smaller default (~768x576) and can be dragged larger, instead of being pinned at 1024x768. The HUD still renders to a fixed 1024x768 buffer (so layout/quality is unchanged); the ScaledWindow downscales that buffer to the window when below 1.0x and upscales above it.

Details:
- `ScaledWindow`: added `MIN_WINDOW_SCALE = 0.75f` (MIN_VIEWPORT 768x576). `updateWindowScaleFromViewport()` now clamps the window-follow scalar to [0.75, ...] instead of [1.0, ...] and no longer floors the draw size at the base, so windows smaller than 1024x768 downscale the buffer. Default window size, initial minimum size, and `getMinimumViewportSizeForScalar()` (window-scale mode) use MIN_VIEWPORT. The applet/mudclient buffer stays pinned at 1024x768 via `resizeAppletToBase()`.
- Verified the 1024 buffer + HUD are unaffected via the workbench (it captures the internal frame, not the OS window); the on-screen window size needs visual confirmation on a real display.
- No server/packet/gameplay change.

### 2026-06-08 - HUD: full-menu containment, minimap +30%, chat opacity, combat-style selector

- Containment: inset every right-side panel's content so headers, tabs, dividers, labels and values sit inside the ornate stone frame (no overflow). Applied the Options-style inset (`+27` origin, `width-58`) to Magic (`magicPanelX`/`magicPanelWidth`), Stats (`innerX`/`innerW`), Friends (`var3`/`var5`), and the shared `repositionCustomUI` widget bounds (`vInnerX`/`vInnerW`, which cover the magic/social/quest lists). Inventory grid was already centered (`panelX+(panelW-245)/2`) and left unchanged. Audited via a parallel multi-agent pass + verified each panel visually.
- Minimap: ~30% larger for the voidscape skin only — box `156x152 -> 203x198` and projection scale `192 -> 250` scale together so it looks identical but bigger; `voidscapeMinimapX()` clamp, World-Map button size, and auto-close rect updated to match.
- Chat: halved the chat-box interior opacity (alpha `240 -> 120`) so the world shows through more while text stays legible. Also moved the chat input line up off the bottom border with horizontal padding so typed text no longer overflows the frame.
- Combat-style selector (`drawDialogCombatStyle`): for the voidscape skin, moved it below the location plaque (`sy 15 -> 92`, plaque spans y18..86), scaled it ~25% (`width 175 -> 219`, row height `20 -> 25`), and skinned it dark-fantasy (near-black rows, purple selection bar, gold header, cream text, purple border). Classic position/size/style unchanged. Concept art generated at tmp/uiwork/concept_combat.png for further polish (per-row icons + gem frame) if wanted.
- New tool: scripts/ui-critique.py (vision critique) used throughout. `scripts/build.sh` passes. No server/packet/gameplay change.

### 2026-06-08 - Location plaque shows the town/area name

The voidscape location plaque now names the town/area you're in (Lumbridge, Varrock, Falador, Edgeville, Ardougne, ...) the same way it already showed WILDERNESS/DEEP WILDERNESS, instead of always reading "VOIDSCAPE". Ported the server's `Point.returnLocationName()` coordinate table client-side into `voidscapeAreaName(worldX, worldY)` (a list of `inBounds` town boxes) so there's no packet/protocol change — the client already has the player's world coords. Wilderness still uses the existing `inWild`/`voidscapeWildLevel` path; unmapped areas fall back to "VOIDSCAPE". The custom Void-Council intro sits on Edgeville's coords, so it currently reads EDGEVILLE (a safe-zone/enclave name would need the server's safe-zone bounds, which the client doesn't have).

### 2026-06-08 - Revert smaller-window downscale; beta requires want_custom_ui

Two beta regressions fixed:
- The sub-1.0 default window (768, downscaling the fixed 1024 HUD buffer) looked pixelated/blurry on real displays. `ScaledWindow.MIN_WINDOW_SCALE` is back to 1.0 — the window opens at the native 1024x768 (crisp 1:1) and can be dragged larger. A smaller crisp window isn't possible without a true low-res HUD.
- The voidscape HUD only renders when the SERVER tells the client custom UI is on (`Player.getCustomUI()` gated by `want_custom_ui`). The deployed beta VPS had `want_custom_ui: false`, so every tester got the classic UI. Set it to `true` on the VPS `server/local.conf` and restarted (DB + config backed up). `getCustomUI()` defaults to true when `want_custom_ui` is on, so all testers now get the HUD by default.

### 2026-06-09 - Concept HUD: compact native desktop viewport

The desktop client now opens at a native compact 4:3 buffer/window instead of downscaling a larger HUD. The default preset is 640x480, with a new local Options/Profile row to cycle five native screen sizes (640x480, 720x540, 800x600, 896x672, 1024x768) while keeping the interface matched to the chosen viewport. The top gold-stack readout was removed from the HUD because it consumed space without being an actionable menu button. Compact viewports pin side panels to the right edge and lower the chat-frame minimum width so Options/Friends/Stats no longer slide left over the chat frame; larger windows still support the existing scaled-window path. Fresh accounts now default to a closer camera zoom (`setting_last_zoom` 35 instead of 125) so the game view reads less like a zoomed-out map. No server packet, opcode, schema, launcher, Android, or gameplay rule changed.

Follow-up menu QA made the HUD panels responsive within those native presets instead of leaving near-1024-sized menus on the 640/720/800 buffers. Non-inventory side panels now scale width, inner inset, content top, and Stats height by viewport class; small presets hide the optional equipment-status block and use a one-column Stats summary, while 896/1024 keep the fuller panel. Inventory deliberately keeps the original OpenRSC 49x34 slot grid and 48x32 item draw area so item sprites are not clipped or blurred. Bottom chat buttons use compact labels/icons on narrow tabs to avoid icon/text collisions, the Stats top tabs use the same heavier typography as the Options tabs, panel content gets extra header padding, and the chrome underlay now backs transparent frame pixels so gameplay does not leak through menu gaps. Verified with PC workbench screenshots for Options, Inventory, Stats, Magic, Friends, and chat tabs at 640x480, 800x600, 896x672, and 1024x768. No server packet, opcode, schema, launcher, Android, or gameplay rule changed.

Final QA/art pass fixed the remaining inventory stack-count clipping and reduced HUD asset scaling blur. Stack amounts in the Voidscape inventory now draw lower in the 49x34 slot so coin counts like `18100` are no longer clipped by the top of the item draw area. The UI skin loader now discovers all PNGs in `Client_Base/Cache/voidscape/ui/skin/`, allowing native-size variants without touching the Java asset list every time. Added generated 34px top-tab icon variants, 16/20px chat-tab icon variants, and 12/14px stats/equipment icon variants; the HUD selects those exact-size files when present instead of shrinking larger rasters at runtime, and `Client_Base/Cache/MD5.SUM` lists them for cache packaging/updaters. A ChatGPT concepts pass produced four HUD kit directions; concept 2's thinner frame direction was chroma-keyed, cleaned, imported as optional `right-panel-frame-thin.png`, and drawn with fallback to the original frame. Follow-up polish replaced that first thin-frame import with a cleaner narrow-rail asset, shrank the title plate when thin chrome is active, anchored compact Options/Friends panels under their own top icons, and moved the Options/Profile logout row down so it no longer collides with Duel. Verified with workbench screenshots for Inventory/Options/Stats at 640x480, 800x600, and 1024x768. No server packet, opcode, schema, launcher, Android, or gameplay rule changed.

### 2026-06-09 - Concept HUD: filigree border kit via gpt-image-2 + nine-slice chrome rendering

Replaced the flat "thin" HUD border kit after user feedback that the new borders looked wrong, and fixed the pixelated/stretched look of panel chrome at non-native sizes.

Details:
- Art pipeline: sent the in-game stats panel, the original textured frame asset, and a full HUD screenshot to OpenAI `gpt-image-2` (now live; it rejects `input_fidelity`, which only exists on gpt-image-1.5) to generate three border-kit concept directions; the user picked "steel filigree". A second extraction round produced each asset (tall frame, title plate, section header) as an individual HD image on a pure-green background; chroma-key + despill + LANCZOS downscale produced `right-panel-frame-thin.png` (244x368), `right-panel-title.png` (241x38), `right-panel-section.png` (326x27). Sources kept in `tmp/uiwork/border-concepts/`; `Client_Base/Cache/MD5.SUM` updated.
- Nine-slice chrome: panel frames were one sprite stretched (nearest-neighbor) to every panel width/height, which pixelated and distorted corners. `drawVoidscapeNineSlice` / `drawVoidscapeThreeSliceH` now render corners and bar end-caps 1:1, stretch rails along one axis only, and keep the top/bottom center gems undistorted via 1:1 center segments (slices cached per asset). Used by `drawVoidscapePanelChrome` (frame + title), `drawVoidscapeSectionHeader`, and the bottom chat frame, which now reuses the same filigree frame asset so the whole HUD shares one border kit.
- Layout/formatting fixes from a workbench review at every preset: the stats panel no longer overlaps the bottom chat-tab row (height now capped above `voidscapeChatTabTop()`, was `gameHeight-96`); the SKILLS/QUESTS toggle and STATS header moved down 6-7px so they clear the frame's top rail + gem; the magic/prayer spell list gained breathing room below its tab strip; the inventory panel height is snug to the 6-row grid (plus the Equipment sub-tab strip when enabled) instead of leaving a dead zone; chat interior alpha raised 120 -> 170 because pixel sampling showed 120 was illegible over bright water/grass.
- Verified via PC workbench screenshots of Options/Stats/Magic/Inventory plus the default HUD at 640x480, 800x600, and 1024x768. `scripts/build.sh` passes. No server packet, opcode, DB schema, definition, client-version, launcher, Android, or gameplay change.

### 2026-06-09 - Concept HUD: minimap chrome, forged-pewter icon set, gem/chat-frame fixes

Extended the filigree HUD kit to the last unskinned elements and fixed three user-reported chrome bugs.

Details:
- Minimap: replaced the bare purple box border with the same nine-sliced filigree frame as the side panels (drawn over the map edge after map content, so the hard map cut tucks under the rails), and restyled the "World Map" button as a section-bar chrome button with gold label and purple hover tint. Classic-skin path unchanged.
- Top tab icons: replaced the six clashing top-bar icons (cartoon smiley, glossy purple bars, mixed materials) with a matched hand-forged pewter set generated via the gpt-image-2 concept->extract pipeline (grid concept sheets, per-icon green-screen extraction, chroma-key + LANCZOS to 34px variants plus 68px bases). Files: `top-gear`, `top-social-smiley`, `top-stats-bars`, `top-book`, `top-map-scroll`, `top-bag` (+ new `-34` variants), all listed in `MD5.SUM`. Concept/source art in `tmp/uiwork/border-concepts/`.
- Double-gem clash: the title plate asset had its own top-center gem that stacked under the panel frame's gem; erased it from `right-panel-title.png` (clean column transplant) so each panel shows a single frame gem.
- Chat frame stability: chat-frame width was anchored on `voidscapeRightPanelHomeX()`, which tracks the ACTIVE panel's width — opening the wider inventory panel visibly resized the chat box. Added `voidscapeRightPanelBaseWidth()` (size-class width without the inventory special case) and anchored the chat frame on it.
- Chat fill peek: the dark interior fill extended 2px past the frame's bottom edge (and under its thin rail); the fill now ends 8px above the frame bottom, tucked fully under the rail.
- Verified via workbench screenshots at 640x480 (top bar, minimap + button, chat closed vs inventory open, single-gem title). `scripts/build.sh` passes. No server packet, opcode, schema, definition, client-version, launcher, Android, or gameplay change.

### 2026-06-09 - Void Colossus raid boss, slice 1 (arena + portal + placeholder boss)

First slice of the Void Colossus group-raid feature: a custom arena, portal access, and a placeholder boss spawn. Combat carve-out, cracker drops, and custom art come in later slices.

Details:
- Landscape: new `patch_colossus_sector` in `scripts/patch-void-enclave-landscape.py` bakes a dark circular "void plaza" (checkered floor, bright ritual core under the boss, mid-purple ring walkway) into unused sector `h0x59y38` (worldX 528-575, worldY 48-95, adjacent to Void Rush). The surrounding ocean tiles are left intact so the plaza is escape-proof without wall bytes. Writes both `server/conf/server/data/Custom_Landscape.orsc` and `Client_Base/Cache/video/Custom_Landscape.orsc`. Chosen east of x=336, so the sector is non-wilderness and PvP-safe by geography (no `Point.java` change).
- Boss: Void Colossus NPC id 852 appended to `NpcDefsCustom.json` (5000 HP, level 350, att/str 250, def 180, respawn 300s) and to client `EntityHandler.java` with the King Black Dragon dragon sprite (165) as a placeholder at beyond-KBD camera scale (700x500). `NpcId.VOID_COLOSSUS(852)` added. Spawned at runtime (arena center 552,71) by a `StartupTrigger` plugin so the WorldPopulator P2P filter can't strip it; `shouldRespawn` left true so the default respawn loop applies.
- Access: two Void Rifts (object 1306) in `SceneryLocsVoidColossusArena.json` — one in the Void Enclave hub (113,321), one in the arena (553,83) — gated by a new `want_void_colossus` config flag (`ServerConfiguration` + `WorldPopulator` registration + `local.conf`). The `VoidColossusArena` plugin (`custom/minigames/voidcolossus/`) implements both the startup spawn and the `OpLocTrigger` portals (confirm dialog + teleport bubble + teleport).
- Client version bumped 10070 -> 10071 (landscape cache change) in `Config.java` + `local.conf`; `MD5.SUM` updated for `Custom_Landscape.orsc`.
- Verified in workbench: arena plaza renders, the giant placeholder Colossus stands at center and shows the Attack/combat-style menu, both rifts register, and the arena->enclave teleport round-trip works. No new opcodes. `scripts/build.sh` passes.

### 2026-06-09 - Dev test commands for headless/workbench content testing

Added `server/plugins/com/openrsc/server/plugins/custom/commands/VoidTestCommands.java` (dev-only `CommandTrigger`) so custom content can be exercised by id/coords instead of pixel-precise mouse clicks: `::atnpc <id>` walks to + attacks the nearest NPC through the real attack path (fires `AttackNpcTrigger`), `::atobject <id> [2]` operates the nearest scenery through the real `OpLocTrigger` path, `::walkto <x> <y>` paths to a tile, and `::npcinfo [id]` prints a target's HP/coords/combat state. Both action commands reproduce the exact `WalkToMobAction`/`WalkToObjectAction` dispatch used by `AttackHandler`/`GameObjectAction`, so they are authentic tests, not shortcuts. Documented in `Commands.md`. No protocol, schema, or gameplay change — pure dev tooling gated on `isDev()`.

### 2026-06-09 - Void Colossus raid boss, slice 2 (multi-attacker combat + boss offense)

The Void Colossus can now be fought by any number of players at once and fights back, without entering the engine's 1v1 combat lock.

Details:
- Multi-attacker carve-out: a new plugin package `server/plugins/.../custom/minigames/voidcolossus/` intercepts attacks via `VoidColossusCombat` (`AttackNpcTrigger.blockAttackNpc` returns true for npc 852, so the engine's default `startCombat()`/`CombatEvent` never runs — the boss is never `inCombat()`, so the second-attacker block at `AttackHandler` "I can't get close enough" can never fire). Each melee attacker instead runs a per-player `ColossusMeleeSiegeEvent` (modelled on `RangeEvent`, `DuplicationStrategy.ONE_PER_MOB`): it walks the player adjacent, rolls the real `CombatFormula.doMeleeDamage`, and applies damage exactly like `CombatEvent.inflictDamage` (subtractLevel + `Damage` flag + `addCombatDamage` + `awardMeleeHitExperience`), killing via `npc.killedBy(player)`. It never sets the player's opponent/combat sprite, so no 1v1 lock is imposed. Ranged and magic already bypassed the lock and feed the same damage maps.
- Boss offense: a persistent per-boss `ColossusBossEvent` (started at spawn, idles cheaply and self-heals to full when the arena empties) retaliates against all attackers on staggered timers — single-target void slam (~4t), arena-wide roar with AOE damage (~15-25t), and a prayer-draining void torrent with a projectile (~10-18t) — using the `VoidKnightBoss` damage pattern. Boss never chases.
- Engine edits (two, minimal): (1) `ProjectileEvent` no longer calls `setChasing` on an NPC flagged `raid_boss`, so ranged/magic attackers aren't chased out of the arena; (2) `GameStateUpdater.updateNpcAppearances` scales the NPC hit-bar's cur/max fields into 0-100 when max > 255 — the fields are single unsigned bytes and 5000 HP wrapped; the client only uses the ratio so this is visually lossless and untouched for all authentic NPCs (max authentic HP ~250). The boss is tagged `raid_boss` at spawn.
- Verified in workbench: melee lands and drops boss HP (5000 -> 4968...) while `combat=false` throughout (no lock), the 5000-HP health bar renders, the boss retaliates (roar/torrent/prayer drain) and damages/can kill the attacker, and on-hit XP flows. True simultaneous two-player melee was then verified with two live clients: a second account (StepTwo) was registered and logged in alongside StepAlt; both attacked the Colossus at once (both adjacent, both targeted by the roar, boss HP dropping 5000 -> 4947 -> 4774) with combat=false throughout, and StepTwo independently dealt damage and was killed by the boss when fighting it solo. No new opcodes. `scripts/build.sh` passes.

### 2026-06-09 - Void Colossus raid boss, slice 3 (cracker scatter on death + respawn)

On death the Void Colossus scatters 10 free-for-all Christmas crackers, then respawns on the standard timer.

Details:
- `VoidColossusCombat` now also implements `KillNpcTrigger`. `blockKillNpc` returns true so `onKillNpc` fires; the boss is deliberately NOT added to `Npc.removeHandledInPlugin`, so the engine's default drop (bones) + `remove()`/respawn still run — the plugin only adds the cracker hoard on top. `onKillNpc` spawns 10 ownerless `GroundItem`s (Christmas cracker, id 575) at random walkable plaza tiles (validated against `CollisionFlag.FULL_BLOCK`, skipping the boss centre) with an extended ~6.4-minute despawn, and broadcasts a server-wide kill message. Ownerless ground items are free-for-all (any player can see/take them); the cracker id is already on the loot-beam default list so each gets a purple beam for free.
- Respawn is unchanged stock behaviour: the runtime-spawned boss keeps `shouldRespawn=true`, so `Npc.remove()` schedules its return after the def's `respawnTime` (300s) with HP and damage maps reset.
- Dev tooling (extends the earlier test-command set, all via real action paths): `::killnpc`/`::damagenpc` (drive the real death/damage path), `::take`/`::grounditems`/`::talknpc`/`::opnpc`/`::dropinv`. These make headless/workbench verification of drops and pickups possible without pixel-hunting menu rows.
- Verified in workbench: killing the boss prints "10 Christmas crackers scatter across the plaza!", `::grounditems` lists 10 crackers all marked FFA at scattered tiles (plus the default bones), `::take 575` picks one up into the inventory, purple loot beams render over each, and the boss vanishes into its respawn window. No new opcodes. `scripts/build.sh` passes.

### 2026-06-09 - Dev test commands: full action coverage

Expanded `VoidTestCommands` (dev-only) so the actions needed to test custom content can all be driven by id/coords instead of pixel-precise mouse clicks: `::take`/`::grounditems` (pick up + list ground items, real `TakeObjTrigger`), `::talknpc`/`::opnpc` (real `TalkNpcTrigger`/`OpNpcTrigger`), `::dropinv` (real drop event), and `::killnpc`/`::damagenpc` (real death/damage path), on top of the earlier `::atnpc`/`::atobject`/`::walkto`/`::npcinfo`. Each reproduces the exact WalkTo-action + trigger dispatch its packet handler uses, so plugins fire authentically. Documented in `Commands.md`. Pure dev tooling gated on `isDev()` — no protocol, schema, or gameplay change.

### 2026-06-09 - Void Colossus raid boss, slice 4 (custom boss sprite)

Replaced the King Black Dragon placeholder with a custom Void Colossus sprite — an armoured purple void titan generated from the user's concept art.

Details:
- Art: gpt-image-2 generated three full-body colossus concepts (faithful / hulking / regal) on green screen using the user's own boss concept as reference; the user picked "faithful". Chroma-keyed + despilled + cropped to a single 105x200 figure (`tmp/colossus-art/`).
- Animation wiring: renamed the pre-reserved `reserved_custom_npc_838` AnimationDef (client `EntityHandler.java`, list index 233) to `voidcolossus`, and pointed NPCDef 852 `sprites1` at index 233. The animation's runtime `number` (1755, verified empirically via a temporary println — `listIndex=233 number=1755`) determines the archive slot. Packed the colossus image into all 18 frames (1755-1772) of `Authentic_Sprites.orsc` via `tools/voidscim-art/pack.py` (a stationary boss never enters COMBAT_A/B, so identical frames are fine).
- Camera sizing: `camera1`/`camera2` are the sprite's projected WIDTH/HEIGHT (confirmed in `Scene.drawSprite`/projection at `Scene.java`), set independently — a mismatch squishes the sprite. The correct on-screen aspect must account for the sprite's logical-box padding (`something1/something2` vs actual pixels), not just the raw art aspect. Final values `camera1=250, camera2=350` (≈0.71) render the tall titan correctly proportioned and sized to clear the custom HUD's top bar at melee range. Mirrored in `NpcDefsCustom.json`.
- CLIENT_VERSION 10071 -> 10072 (sprite cache change) in `Config.java` + `local.conf`; `MD5.SUM` updated for `Authentic_Sprites.orsc`. Build passes. Verified in workbench: boss renders as the custom colossus in the arena and (spawned via `::createnpc`) in the open. No new opcodes, no gameplay change.

### 2026-06-09 - Engine: per-entity instancing (phased visibility) + Void Colossus solo instances

Added a real (phased) instancing layer to the engine and converted the Void Colossus from a shared open-world boss into per-player solo instances.

Engine layer:
- `Entity.instanceId` (default 0 = the normal shared overworld) with getter/setter + `sharesInstanceWith`. Inherited by Player, Npc, GroundItem, GameObject.
- The four per-player visibility gathers in `RegionManager` (`getLocalPlayers`/`getLocalNpcs`/`getLocalGroundItems`) now also require `observer.instanceId == entity.instanceId`. Scenery objects (`getLocalObjects`) are intentionally NOT filtered (shared world geometry, so an instanced player still sees the plaza + exit rift). Because the whole overworld is id 0, the filter is a no-op there — verified the normal world is unaffected (NPCs, players, scenery, item drop/pickup all work). This is the single choke point that builds every per-player update packet, so phasing it isolates everything (combat, projectiles, loot) for free.

Void Colossus solo instances:
- `VoidColossusArena` is now a session manager (no more startup shared boss). Entering the hub rift assigns the player a fresh `instanceId`, teleports them to the (shared) arena coords, and spawns their own boss tagged with that id. All instances live at the SAME coordinates but are phased — so there is one physical arena, no landscape copies. Killing the boss scatters 10 phase-private crackers (tagged with the boss's instanceId in `VoidColossusCombat`) and schedules an in-instance respawn (12s) so the player keeps farming. Exit rift, death (`PlayerDeathTrigger`), and logout (`PlayerLogoutTrigger`) tear the instance down and restore `instanceId` 0. The kill announcement is now per-player (was a server-wide broadcast).
- The combat carve-out, boss AI, and cracker code needed almost no change: phasing means each instance's boss/loot is only visible to its owner, so the existing fixed-center (552,71) logic is correct for every instance.
- Verified with two live clients: both players standing on the same tiles (552,74) each saw only their own boss and themselves — never each other; one player killing their boss left the other's at full HP with phase-private loot; in-instance respawn, death-teardown (returned to Lumbridge, phase cleared), and clean exit all confirmed. No new opcodes. `scripts/build.sh` passes.

### 2026-06-09 - Void Colossus: custom move animation + solo balance pass

Gave the stationary boss an attack animation and tuned it for solo "hard but fair".

Move animation:
- A stationary NPC normally shows one frame forever. The boss now plays its attack-pose frames on each move by setting its combat sprite from the AI event: `boss.setSprite(8)` (slam) / `setSprite(9)` (roar/torrent) makes the client render the combat-A/B frames (15-17); because the boss's `combatWith` is null, `Mob.inCombat()` stays false, so this does NOT re-impose the 1v1 lock or remove the boss from view. `ColossusBossEvent` resets the sprite to an idle facing 2 ticks later. A new arms-raised "slam" pose (generated via gpt-image-2 from the idle colossus, fitted to the same 105x200 anchor so it doesn't jump) is packed into archive frames 1770-1772; the idle stays in 1755-1769. There is only one visual attack-animation slot (both combat-A and combat-B render frames 15-17, differing only in timing), so the three moves share the raised-arms pose. `combatSprite` lowered 84 -> 6 so the boss slams in place instead of lunging far left.
- Engine note (from research): `Mob.setSprite(int)` is the public hook to play an NPC's combat animation outside a CombatEvent; safe as long as `combatWith` stays null.

Balance:
- HP 5000 -> 1000 and defence 180 -> 130 (combat-sim showed 5000 HP = a ~30-min kill; 1000 HP/def 130 is ~4 min for a maxed rune scimitar, much faster with a 2h or the Void Mace's 2.5x). Slam damage 0-8 -> 0-10. Measured incoming ~1.2-2 HP/sec — threatening but sustainable with food while managing the prayer-drain torrent. All values are simple constants in `ColossusBossEvent` / `NpcDefsCustom.json`, easy to retune.
- CLIENT_VERSION 10072 -> 10073 (sprite archive change), MD5 updated. Build passes. Verified in-instance: HP 1000, the boss visibly rears up (arms raised, void energy) during moves then returns to idle, centered. No new opcodes.

### 2026-06-09 - Void Colossus: 5-angle directional sprites, colossal scale, faces the player

Final pass on the boss's look — directional art, colossal size, and correct facing — plus dev tooling to make all of it testable.

Directional sprites:
- The boss was previously one front sprite repeated across every frame, so rotating the camera showed the same flat image ("not enough angles"). It now has five distinct hand-fitted views packed into `Authentic_Sprites.orsc`: front (1755-1757), ¾-front (1758-1760), side (1761-1763), ¾-back (1764-1766), back (1767-1769), plus the raised-arms attack pose (1770-1772). The client's NPC frame selector (`mudclient.drawNPC`, `var11 = 7 & (rsDir + (cameraRotation+16)/32)`) maps these five sets across all eight compass orientations (three are mirrored), so a player orbiting the camera sees the Colossus turn in 3D. All five were generated from the picked concept on green screen and chroma-keyed to the same 105x200 / logical 153x214 anchor so frames don't jump.

Colossal scale:
- Camera (sprite billboard size) `675x950 -> 820x1150` in client `EntityHandler.java` NPCDef 852 (and mirrored in `NpcDefsCustom.json`). `camera1` is width, `camera2` height; the ratio is held at the 153:214 logical-sprite aspect so scaling up does NOT squish (an earlier mismatch had — fixed). The boss now stands ~5.3x the logical sprite, reading as ~5-6x the player in-world: it towers over and fills the frame at melee range. Rendering is client-side from the client's own NPCDef, so this is a code change only — no cache/`CLIENT_VERSION`/MD5 bump (stays 10074 from the directional-sprite pack).

Faces the player:
- A `createnpc`-style stationary boss has no combat AI, so it kept its spawn facing (often its back to the player). Fix: `ColossusBossEvent.run()` now calls `boss.face(nearestAttacker)` every tick when not mid-attack-animation (cheap — `face` is a no-op when unchanged; the boss is stationary so it never walks), and `VoidColossusArena.spawnBoss` faces the arena entrance (south) on spawn so it greets approaching players front-on before the AI takes over.

Dev tooling (dev-gated, in `VoidTestCommands`):
- `::colossus` drops you straight into a fresh solo instance via the real spawn/teleport path (`VoidColossusArena.devEnter` -> `enterInstance`) — no walking to the hub rift. A static singleton handle on the arena plugin exposes the entry flow.
- `::colossuspeace [on|off]` toggles `ColossusBossEvent.PEACEFUL`: the boss still faces players and plays animations but deals no damage, so the arena can be inspected (size, angles, facing) without dying. Defaults off; resets off on restart. Used to verify this whole pass.
- Verified in workbench (peaceful mode): boss spawns colossal and front-on, the attack loop alternates idle <-> raised-arms with damage splats + roar when peace is off, and the five packed angles are confirmed distinct by decoding the archive. Build passes. No new opcodes.

### 2026-06-09 — Void Knight NPC (id 853): Black Knight clone, recoloured void-purple

Added a Void Knight (server NpcId 853, `VOID_KNIGHT_VOIDBORN`) — a faithful Black Knight recoloured to void-purple with an identical combat profile. Key finding: the Black Knight is **not** a baked sprite. It is three pieces of standard plate **equipment** — `fullhelm`(#18) + `platemailtop`(#32) + `platemaillegs`(#42) — each filled dark grey (`0x303030`/`0x404040`) via its AnimationDef. So it is fully recolourable. Two earlier attempts failed because colour *masks* (`drawSpriteClipping` `colorMask`/`colorMask2`) only tint **grey** (`R==G==B`) pixels — the Black Knight's dark fill and the existing teal Void Knight's (id 845) hand-painted plate both ignore a purple mask. The fix is to add new equipment AnimationDefs with a void-purple **fill** instead.

Implementation (client `EntityHandler.loadAnimationDefinitions` + `loadNpcDefinitions`):
- Appended void-purple fills of the same three layers after the void-equipment block (runtime idx 236 `platemailtop`, 237 `platemaillegs`, 238 `fullhelm`), plus reused the existing void scimitar (idx 229) as the weapon.
- NPCDef 853 = `sprites {239,236,237,-1,229,...}`, camera `145x220`, walk/combat models `6/6/5` — byte-for-byte the Black Knight's geometry/camera/animation, so its combat gap, speed, directional angles and timing are identical. NPCDef colour fields are `0` (the equipment carries its own fill; no NPC tint needed).
- The helm plume is baked **pure-red** (`R!=G` pixels: `228/218/165/150,0,0`) that no colour mask or blueMask can reach (confirmed: the real Black Knight's plume is red too, just hidden against black). To get a fully-purple helm we packed a custom **`voidhelm`** sprite block (idx 239): extracted the 18 fullhelm frames (archive `number=297`), repainted only the four red shades to a glowing lavender ramp (metal left grey so its fill tints it), and packed at archive entries `1809-1826` (`tools/voidscim-art/{extract_ref,pack}.py`). The `voidhelm` AnimationDef fills the grey metal void-purple; the lavender plume is baked.
- Cleanup: removed the dead AI-`voidknight` AnimationDef; renamed the duplicate `VOID_KNIGHT(853)` enum to `VOID_KNIGHT_VOIDBORN` (two `VOID_KNIGHT` constants — 845 and 853 — would not have compiled).

Files touched:
- `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` — purple equipment + `voidhelm` AnimationDefs; NPCDef 853.
- `server/conf/server/defs/NpcDefsCustom.json` — NPCDef 853 (`sprites1=239, sprites2=236, sprites3=237, sprites5=229`, colours 0, camera `145x220`).
- `server/src/com/openrsc/server/constants/NpcId.java` — `VOID_KNIGHT(853)` → `VOID_KNIGHT_VOIDBORN(853)`.
- `Client_Base/Cache/video/Authentic_Sprites.orsc` — `voidhelm` frames at 1809-1826 (`.bak` kept).
- `Client_Base/Cache/MD5.SUM` — refreshed `Authentic_Sprites.orsc` hash.
- `CLIENT_VERSION` 10082 → 10083 in `Config.java` + `client_version` in `server/local.conf` (sprite-cache change).

Verified in the NPC previewer (`tools.NpcPreview --dump 853`): cohesive void-purple from all 8 directions, lavender helm crest, purple void scimitar, and combat at the correct distance against a live Man (id 11). Reversibility: revert the listed files and restore `Authentic_Sprites.orsc` from `.bak`. No packet/opcode, schema, or gameplay-rule change.

### 2026-06-09 — Void Knight v2: baked void colorway + unarmed

Follow-up to the Void Knight entry above. The flat `0x6a0dad` luster fill read as one dark tone because the plate's gray ramp tops out at luminance ~144, below `sweptHairShade`'s 150 "bright" threshold — so the recolour only ever hit the two *dark* luster shades (the `::appearance` void hair looks richer because its sprite has bright grays that reach the full highlight). Replaced the fill approach with a **baked void colorway**: extracted the helm/body/legs plate blocks (archive numbers 297/351/378), mapped each gray pixel through a smooth indigo-shadow → bright-violet-highlight gradient (`tools/voidscim-art/void_bake.py`), recoloured the platebody's gold trim to a lavender accent, kept the lavender helm plume, and packed three baked blocks: `voidhelm` (1809, re-packed), `voidbody` (1836), `voidlegs` (1863). Their AnimationDefs use `charColour 0` (show baked pixels as-is). Also removed the void scimitar — the knight is now unarmed per design.

Files: `EntityHandler.java` (replaced the 3 purple-fill equipment defs with baked `voidhelm`/`voidbody`/`voidlegs`; NPCDef 853 → `sprites {236,237,238,-1,...}`), `NpcDefsCustom.json` (853 → `sprites1=236, sprites2=237, sprites3=238, sprites5=-1`), `Authentic_Sprites.orsc` (+`voidbody`/`voidlegs` blocks, re-baked `voidhelm`), `MD5.SUM`, `CLIENT_VERSION` 10083 → 10084 + `client_version`. Verified live in-game (workbench `/dev/ready` login + `createnpc 853` + screenshot): renders the rich colorway at proper scale, unarmed. `.bak` of the archive kept.

### 2026-06-09 — Void dungeon mobs: 7 Tier-A recolours (ids 854-860)

Added seven void NPCs for an upcoming void dungeon, all via the cheap Tier-A recolor path (NO sprite baking, NO archive change, NO version bump): **Void Spider (854), Void Giant (855), Void Wolf (856), Void Demon (857), Void Ogre (858), Void Wizard (859), Void Unicorn (860)**.

Two recolor mechanisms, both proven this pass:
- **Gray creature sprites tinted by a charColour AnimationDef.** Empirical pixel checks showed the base spider (100% gray), wolf (99%), demon (96%), unicorn (90%) sprite blocks are gray-ramped — exactly why the authentic Ice/Shadow/Red spider and red/black demon/dragon variants exist (each is the ONE shared sprite tinted by its AnimationDef charColour). So a void variant = a new same-named `AnimationDef("spider"/"wolf"/"demon"/"unicorn", "npc", 0x6a0dad, …)` which name-dedups onto the shared sprite block and tints it void-purple via the luster. Idx 239-242. The creatures' grays reach the bright luster shade, so they get a rich dark→bright purple (richer than the plate did).
- **Base-body humanoids tinted by NPCDef colour fields.** Giant/Ogre/Wizard reuse the gray base body {6/7,1,2} with void colour fields (hair `0x6A0DAD`, top/bottom `0x4A2C6F` clothing-luster, skin `0x6a0dad`). Added a void-purple `boots` fill (idx 243) in slot 10 to cover the base body's baked-brown feet; dropped the ogre's red leather + club so it reads cleanly purple.

Skipped goblin (21% gray) and dragon (61% gray) — they tint only partially (baked skin/belly); they'd need baking for a clean look. The full 47-candidate survey is in `docs/void-dungeon-roster.md` for the rest of the dungeon.

Files: `EntityHandler.java` (4 creature charColour defs + void boots, idx 239-243; 7 NPCDefs ids 854-860), `NpcId.java` (7 enums), `NpcDefsCustom.json` (7 entries). Verified in the previewer (all 8 angles) and live in-game (spawned via workbench `createnpc`). Reversibility: revert the three files. No packet/opcode, schema, cache, or version change.

### 2026-06-09 — Void Dungeon (shared black-void area, ids 854-860 + boss)

Added a shared, persistent **Void Dungeon** on the black-void underground (Floor 2, footprint X52-80 / Y2440-2514). Key finding: empty underground tiles render as pure black and are walkable + combat-functional (verified live: an aggressive void spider pathed to and engaged the player). So the dungeon needs **no landscape patch** — it's built entirely from server-side JSON rendered by already-existing client defs (void walls 214/215, rift 1306, mobs 854-860), so **no client cache change and no CLIENT_VERSION bump**. The black void IS the aesthetic.

Structure (south→north difficulty gradient): a closed void-wall perimeter with zone-divider walls (3-tile doorways) splitting it into Spider Warren → Howling Den → Brute Hall → Void Sanctum → the boss chamber "Maw of the Void" (Void Demon lvl 82 + Void Wizards). Entered via a Void Rift in the Void Enclave (108,322) that teleports to the south entrance (66,2512); a dungeon rift returns players. Shared (no instancing) — the engine handles NPC respawns via `respawnTime`.

Implementation:
- `scripts/gen-void-dungeon.py` — generator that emits the three layout JSON files (308 boundary walls, 27 scenery = rifts/torches/pillars, 21 tiered NPC spawns). Re-run to iterate the layout.
- `server/conf/server/defs/locs/{Boundary,Scenery,NpcLocs}VoidDungeon.json` — generated.
- `server/plugins/.../custom/misc/VoidDungeon.java` — `OpLocTrigger` rift entry/exit (clone of the Colossus arena rift pattern, minus the instancing).
- `WANT_VOID_DUNGEON` config flag (`ServerConfiguration.java` + `local.conf`), gating three loaders in `WorldPopulator.java` (Boundary/Scenery/NPC cases).
- `NpcDrops.java` — tiered void drops: trash drop coins + runes; brutes/casters add gems + a rare void-gear chance (`VOID_SCIMITAR/BOW/MACE/AMULET` 1593-1596); the Void Demon boss is the jackpot.

Verified live in-game (teleported through all zones): void brick walls render with doorways, spiders swarm the entrance, the Void Giant looms in the Brute Hall, and the Void Demon (lvl 82) is aggressive and engages on sight. Reversibility: set `want_void_dungeon: false` (or revert the listed files). No packet/opcode, schema, cache, or client-version change.

### 2026-06-10 — Void Dungeon: dark void floor texture

Gave the Void Dungeon a real dark void floor (and a readable minimap) instead of pure black. The world→sector mapping (`WorldLoader.loadSection`): `sectionX = worldX//48 + 48`, `sectionY = (worldY%944)//48 + 37`, `height = worldY//944`. The dungeon footprint (X52-80, Y2440-2514, Floor 2) spans three sectors — `h2x49y48/49/50` — which exist in the landscape archive as all-null (black) tiles. Extended `scripts/patch-void-enclave-landscape.py` with `patch_dungeon_sector()` that bakes only the dungeon's own tiles with the proven Colossus-plaza recipe (a 2×2 `FLOOR_INDOOR`/`FLOOR_V3_STONE` dark checker, elevation 66, no wall bytes — the walls remain JSON boundaries). The rest of each sector stays black void.

Files: `scripts/patch-void-enclave-landscape.py` (+`patch_dungeon_sector`, wired into `main`), `server/conf/server/data/Custom_Landscape.orsc` + `Client_Base/Cache/video/Custom_Landscape.orsc` (3 sectors patched), `Client_Base/Cache/MD5.SUM` (Custom_Landscape hash), `CLIENT_VERSION` 10084→10085 + `client_version` (landscape cache change). Verified in-game: the dark checkered floor renders across all zones with mobs on it. Reversibility: re-run the patcher without the dungeon block (or restore the archive from `Authentic_Landscape.orsc`). No packet/opcode or gameplay change.

### 2026-06-10 — Void Dungeon expanded (bigger layout, denser mobs)

Reworked `scripts/gen-void-dungeon.py` from a single stacked-rectangle cavern into a winding multi-room dungeon: 10 rooms (entrance hall, spider warren + nest, howling den, beast pit, brute hall, giants' rest, void sanctum, antechamber, Maw of the Void boss chamber) joined by 8 corridors. The walkable area is the union of room/corridor rectangles; walls auto-place on every walkable↔void edge so doorways form wherever a corridor meets a room (no hand-placed gaps). Mob count up from 21 → **86** (dense packs scattered per room: 24 spiders, 12 wolves + 6 unicorns, 11 ogres + 8 giants, 7 wizards + 5 knights, 2 demons + adds). Walls 308 → 656, scenery → 50.

The generator now emits the walkable-tile mask to `server/conf/server/data/void_dungeon_floor.json`; the landscape patcher (`patch_dungeon_sector`) is now mask-driven (reads that file, groups tiles by sector, patches exactly the dungeon's tiles) so the dark floor always matches the generated layout — now spanning 4 sectors (h2x49y47-50, all pre-existing). Entrance moved to (72,2552). `CLIENT_VERSION` 10085→10086 + `client_version` + `MD5.SUM` (landscape change). Verified in-game across all rooms. No packet/opcode/schema change.

### 2026-06-10 — Login field QA + voidbot hardening

Fixed the Voidscape login/new-user/recovery renderer so decorative custom fields no longer rely on the legacy `Panel` text-entry painter. The panels still use `Panel` for focus/click/key handling, but text values are now drawn through a clipped custom renderer so long saved usernames and max-length passwords stay inside their boxes. Server-prompt input cancellation now goes through one shared path for Cancel, outside-click, Escape, and Android Back, sending the empty `INTERFACE_OPTIONS` reply only for `SERVER_PROMPT` so blocked scripts are released immediately. Dev tooling follow-up: `tools/voidbot/voidbot` and `tests/smoke.sh` are executable, prefer `python3`, `voidbot start` waits for a real logged-in daemon before returning success, `voidbot say` uses the same encrypted chat payload format as the PC client, and `docs/bot-api.md` now distinguishes current implemented commands from the protocol roadmap.

### 2026-06-10 — Prelaunch portal release QA

Reviewed the public prelaunch portal path for release readiness. Public mode remains locked to static assets, `/api/health`, `/api/public`, signup-code reservations, and token-gated admin endpoints, while client downloads and account APIs stay hidden. Browser QA found the post-signup success state could push the issued code/help text below the fixed landing viewport, so the portal now adds a `prelaunch-claimed` body state and compacts only the completed-signup layout. The page head copy now presents the signup as a real pre-release page instead of a prototype.

### 2026-06-11 — Void Sparrow wilderness scouting

Added a tradable **Void Sparrow** item (id 1603) with a `Release` inventory action for wilderness scouting. The player body stays at the original tile, visible and attackable by other players; only that player's view stream is detached to a temporary scout location. Scouting is wilderness-only, lasts 30 seconds, requires 10 seconds out of combat, snaps back if the body moves or enters combat, and is leashed to 96 tiles from the body. While active, server-side walk packets move the scout view instead of the body, and most interaction packets are blocked so it is for information gathering rather than remote attacking, looting, banking, trading, or object/NPC abuse. The first beta cooldown is persisted per player at 5 minutes, capping max uptime around 10% while keeping the tool common enough to test.

Implementation notes: `Player` now owns a `voidScoutLocation` separate from `getLocation()` plus `getViewLocation()` helpers; `RegionManager`, `GameStateUpdater`, `Mob.withinAuthenticRangeAdditionally`, and `ActionSender.sendWorldInfo` route observer-side local entity, object, wall, item, and floor updates through the view location. `WalkRequest` and `WorldWalkRequest` queue scout movement against that view point, while `PayloadProcessorManager` allow-lists only scout cancel, movement, logout, chat/social, combat-style, settings, and known-player maintenance packets during scouting. Client version `10089` adds explicit scout UI packets: inbound `VOID_SCOUT_CANCEL` on custom wire opcode `37`, and outbound `SEND_VOID_SCOUT_STATE` on custom wire opcode `103`. The PC client renders the original body as a cloned, frozen local-player marker with a Void Sparrow overhead bubble, blocks ground-click walking during scout mode, flies the view with arrow keys, and returns with Escape. Client version `10090` replaces the temporary purple-tinted feather with an AI-generated sparrow icon packed at `Authentic_Sprites.orsc` archive entry `2768` (client spriteID `618`), so the inventory item and overhead bubble share the same art. Client version `10091` repacks that icon a little smaller at the same archive entry and changes scout arrow-key movement to held-key, screen-relative, one-tile nudges based on server-confirmed scout view coordinates instead of repeatedly clearing keys and guessing from local render state. Client version `10092` appends remaining milliseconds to `SEND_VOID_SCOUT_STATE`, renders a compact top-center countdown, restores the 30-second duration, and sends nearby players a throttled `A void sparrow flies overhead.` message when the scout view passes within 5 tiles.

### 2026-06-11 — Android shared-client parity after desktop tooling additions

Restored Android APK builds after shared-client desktop work introduced two Android-incompatible surfaces. The Android Gradle source set now excludes the `Client_Base/src/tools` package so Swing/AWT-only developer tools such as `tools.NpcPreview` are not compiled into the APK, and the Android `orsc.ScaledWindow` stub implements the new desktop screen-size preset methods as fixed no-ops because Android scales through `RSCBitmapSurfaceView` rather than a Swing window. `scripts/build-android.sh` passes again with the shared `CLIENT_VERSION` 10092 client code. Reversibility: remove the source-set exclusion/stub methods only if Android gets a separate shared-client source boundary or native viewport preset support. No packet/opcode, schema, gameplay behavior, or server config changed.

### 2026-06-11 — First local Content Studio GUI

Added a dependency-free local browser GUI for the custom-content pipeline. `scripts/content-studio.sh` serves `tools/content-studio/` on `127.0.0.1:8765`, shows the same fragile allocation points and validation findings as `scripts/content.sh report/validate`, lists packs under `content/custom`, and scaffolds new item/NPC/boss/arena/texture content packs through the existing `voidscape_content.manifest.scaffold_pack()` helper. This gives custom art/content work a safe front door without bypassing the proven command-line pipeline. Reversibility: delete `scripts/content-studio.sh` and `tools/content-studio/`. No packet/opcode, cache, schema, gameplay behavior, or server/client runtime changed.

### 2026-06-11 — Void Colossus instance cleanup hardening

Fixed a lifecycle bug in the solo Void Colossus arena: the arena implemented `PlayerDeathTrigger`/`PlayerLogoutTrigger`, but death was not firing that trigger path and the Colossus logout predicate returned false, so dying or disconnecting in the private phase could leave stale instance state behind. `Player.killedBy()` now fires `PlayerDeathTrigger` after death drops and respawn positioning but before the player receives refreshed world state; `World.unregisterPlayer()` also fires `PlayerLogoutTrigger` so forced disconnects/timeouts clean up like graceful logout. The Colossus arena now opts into both hooks only while the player owns an instance, clears combat/busy state, unregisters the private boss, and removes tracked phase-private cracker drops when the instance ends. Also aligned the Colossus definitions (`NpcDefsCustom.json` `sprites1=233`, client strength `110`) with the custom boss art and tuned solo server stats. Verified locally with voidbot: `::colossus` spawns a visible private boss, `::killnpc 852` scatters crackers and respawns, and the custom NPC preview still renders the packed Colossus art. No packet/opcode or schema change.

### 2026-06-11 — Void Colossus sprite repair pass

Rebuilt the Void Colossus animation block after the first art pass had wrong POV/facing, rubbery walk frames, weak combat spacing, and a side attack that did not read correctly in RSC's forced side-view combat renderer. Using the ChatGPT concepts flow, generated fresh green-screen source sheets: a five-view turnaround (front, 3/4 front, side, 3/4 back, back) and a three-frame side slam strip. Added `scripts/build-colossus-sprites.py` to slice/chroma-key those sheets into the legacy 105x200 frames with the existing 153x214 logical anchor, duplicate only subtle heavy-step idle/walk variants, flip the attack strip to match vanilla Combat A's right-facing convention, and emit a contact sheet before packing. Repacked `Authentic_Sprites.orsc` entries `1755-1772`, tuned Colossus visual timing to `walkModel=24`, `combatModel=16`, `combatSprite=32`, refreshed `MD5.SUM`, and bumped `CLIENT_VERSION`/`client_version`/voidbot to `10093`. Verified with `scripts/run-npc-preview.sh --dump`: `dir4` is player-facing/front, all eight octants read as one rotating body, walk frames stay anchored, and combat A/B mirror from the correct vanilla side-view convention. Reversibility: restore `Client_Base/Cache/video/Authentic_Sprites.orsc` from git or `.bak`, revert the NPCDef timing values and version bump, and delete the builder script.

### 2026-06-11 — Void Colossus 3D ranged-boss rework

Reworked the Void Colossus into a hybrid 3D encounter. The client still cannot render true animated 3D NPCs, so the polished Colossus body remains the high-detail NPC sprite sheet, while a 4x4 non-blocking scenery object (`Void Colossus`, id 1307, model `voidcolossus3d`) generated by `scripts/build-void-colossus-model.py` provides the rift pedestal/base under it. Standard ranged/magic targeting, hit splats, damage credit, death, and drops stay on the visible NPC. Melee attacks against the boss are blocked with a clear ranged/magic message, while object Attack/spell clicks on the base route back into the NPC where possible.

Gameplay shifted to a stationary ranged/magic boss: the Colossus never walks or melee-chases, fires regular void shard projectiles, casts a slower void-claw magic/prayer-drain pulse, and summons capped phase-private voidborn knights that are cleaned up on exit/death/respawn. Boss model/base cache, client/server definitions, and voidbot's login version require `CLIENT_VERSION`/`client_version` `10094`; no packet/opcode or schema shape changed. Reversibility: restore `Authentic_Sprites.orsc`/`models.orsc`, remove object id 1307 + the model builder, and revert the Colossus plugin combat bridge/AI files.

### 2026-06-11 — Void Colossus presentation repair

Polished the Colossus presentation after live inspection showed the full procedural model was too thin/amateur, the boss health bar was not visible, and boss projectiles aimed above players. `scripts/build-void-colossus-model.py` now produces a low 3D rift pedestal instead of a humanoid body, plus two short-lived charge effect models (`voidshardcharge`, `voidclawcharge`) and two Colossus-only projectile sprites (`3167`/`3168`) so the cache assets are repeatable. The high-quality Colossus sprite frames are repacked into `Authentic_Sprites.orsc` entries `1755-1772` for the visible body. Client/server object defs append visual-only ids `1308`/`1309`, and `ColossusBossEvent` flashes those charge models while firing projectile types `7`/`8`.

The client now loads nine projectile sprites, fixes projectile source/target Y offsets so Colossus shots end at player height and player shots hit the boss body, suppresses the irrelevant default `Talk-to` menu entry on the raid boss, and draws a dedicated Void Colossus boss HP panel from the scaled NPC health update plus the client NPC max-hit definition. Cache/client changes require `CLIENT_VERSION`/`client_version` `10095`; no packet/opcode/schema shape changed. Reversibility: restore `models.orsc`/`Authentic_Sprites.orsc`, remove object ids `1308`/`1309`, revert projectile type selection and the Colossus HUD/projectile helper code, then downgrade the client version.

The Colossus arena also implements `PlayerLoginTrigger` now: reconnecting inside the private arena or with a stale Colossus instance tears down the tracked boss/base/summons/drops, resets the player's phase, and returns them to the Void Enclave. This prevents the visual base from surviving without the NPC body after forced disconnects or dev-client restarts.

QA follow-up: Colossus object spell casting now validates line-of-sight against the visible model's outside edge instead of the hidden NPC anchor inside the 4x4 scenery footprint, so the boss no longer blocks combat magic aimed at itself. `tools/voidbot` also gained `object-action` with object-id-aware prewalk and `cast-object` so future boss tests can drive real scenery/ranged/magic packets without mouse clicks.

### 2026-06-11 — Developer protocol login shortcuts

Added two no-mouse local QA login paths. `tools/voidbot/voidbot login --user U --pass P` is now an alias for the protocol daemon start path and launches the daemon with `nohup` so the real game session can survive normal terminal wrapper exit. `scripts/run-client.sh` now accepts `--login USER:PASS` or `--user USER --pass PASS`, exports the credentials only to the child process environment, and the PC client reads those env vars (or direct `voidscape.autologin.*` JVM properties for advanced launches), waits a few startup frames, and then calls the existing `login(...)` path so the visible client sends the same normal login packet as a player. This is desktop/dev-only and deliberately skipped on Android. Reversibility: remove the script flags, `devAutoLogin*` fields/methods in `mudclient`, and the `voidbot login` alias/nohup wrapper change. No packet/opcode, schema, cache, or gameplay behavior changed.

### 2026-06-11 — Void Dungeon revenant-cave pass

Turned the shared Void Dungeon into a higher-risk revenant-cave-style Wilderness PvM area. The generated entry rift moved from the safe Void Enclave to unsafe Wilderness at `(112,296)`, just north of the Enclave wall; entering now costs `5,000` coins and teleports to the dungeon vestibule at `(72,3252)`, while the internal exit rift at `(72,3250)` returns players to `(112,297)` instead of safety. This keeps the cave shared and non-instanced, preserves the existing void NPC/drop loop, and uses the engine's existing folded-floor Wilderness rules for PvP underground. No packet/opcode/schema shape changed.

`scripts/gen-void-dungeon.py` now emits a compact cave on Floor 3 sectors `h3x49y44-45`: a calm threshold room, web cross, two side pockets, a central void chamber, and a small Maw chamber. The entry vestibule starts around level-2 Wilderness and the Maw reaches roughly level-12, so the cave is risky without becoming a long empty hike. Generated output is now 1,514 floor tiles, 274 boundary walls, 26 scenery entries, and 40 NPC spawns. Re-ran `scripts/patch-void-enclave-landscape.py` so the new mask is baked into `server/conf/server/data/Custom_Landscape.orsc` and `Client_Base/Cache/video/Custom_Landscape.orsc`; `MD5.SUM`, `CLIENT_VERSION`, server `client_version`, and voidbot's client version moved to `10097` because the landscape cache changed. The rift interaction no longer opens a multi-choice chatbox prompt: clicking the surface rift checks/removes the fee and enters, and clicking the cave rift exits. Beta/release docs now point testers at the new entry and fee. Reversibility: restore the generated Void Dungeon loc/floor files and `Custom_Landscape.orsc`, set the rift plugin back to the Enclave coordinates, and downgrade the client version/cache hash.

### 2026-06-11 — Void Dungeon rift confirmation and combat picker polish

Follow-up to the compact Void Dungeon pass. The instant rift interaction was too easy to misclick and hid a broader client issue: ordinary server `multi()` prompts were still anchored inside the new Voidscape chat frame, so mouse selection could be awkward even though number keys worked. Standard prompts now draw above the Voidscape chat frame with matching click bounds, while the custom path/title menus keep their bespoke layouts. The Void Dungeon surface rift again asks for confirmation before entering, the cave exit asks before leaving, and the entry fee is now `100,000` coins with a second server-side coin/combat recheck after the prompt. The combat-style picker also returns to the original OpenRSC `175x100` desktop / `140x100` Android sizing instead of the oversized Voidscape variant. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10098`; no packet shape changed.

### 2026-06-11 — First cohesive economy pass

Applied the approved first-pass economy direction: local base XP is now `10x` combat and `2x` skilling, while account subscription cards add a modest `+1x` to both groups instead of flooring accounts to the old `10x/6x` model. The server profile payload, portal prototype, smoke tests, and subscription-card messaging now report the same effective `11x/3x` subscribed baseline.

Void gear specials are now scoped to Void PvM. `VoidContent` centralizes the Void-NPC policy and item multipliers; Void Scimitar grants a 15% melee accuracy/damage bonus against Void NPCs, Void Mace grants 20% against Void NPCs, Void Shortbow grants 15% ranged accuracy/damage against Void NPCs and only fires without arrows against Void NPCs, and Void Amulet's 1.5x stackable-drop multiplier now applies only to Void NPC drops. The previous global Void Mace `2.5x` NPC damage hook was removed so ordinary PvM, PKing, and staking keep their visible-stat baseline.

The Void Chest was retuned toward processable economy goods: more ores, coal, raw fish, gems, and Herblaw secondaries, with smaller weights for finished cooked food and coins. High-level Woodcutting now has a rare utility bridge: players with 90+ Woodcutting can very rarely find a tradable Void Sparrow while cutting yew or magic logs, with magic logs weighted as the primary source and level 99 giving an extra chance bump. This ties a PK scouting item back to skillers without adding a new currency or replacing ordinary supply chains.

### 2026-06-11 — Food economy supply pass

Tightened custom food sources so cooked food comes from players with Cooking rather than from Void reward machines. Void Chest and Void Knight arena supply rewards now pay raw lobster/swordfish instead of noted cooked food, keeping the output useful while giving cooks the value-add step. The Karamja Fishmonger no longer cooks fish for a half share; he remains useful as a raw/cooked fish notary so fishers and cooks can move bulk goods without bypassing the skill. The OpenPK points shop no longer stocks cooked lobster/shark, avoiding a custom direct-food faucet if that mode is enabled. Authentic RSC monster drops, quests, and low-tier general-store food were left alone.

### 2026-06-11 — Ranged supply pass

Protected the arrow/fletching loop after making Void Shortbow consume normal arrows outside Void PvM. Void Chest no longer pays finished adamant arrows; it now pays mithril/adamantite arrowhead bundles, which still reward Void PvM but require shafts, feathers, and Fletching to become ammunition. Lowe's OpenPK replacement shop was reduced from huge stocks of iron-through-adamant arrows and high-tier bows to starter-grade bronze arrows, bolts, shortbows, longbows, and crossbows. Authentic archery shops, authentic NPC arrow drops, starter bronze-arrow access, and the Void Shortbow's Void-only no-ammo behavior were left intact.

### 2026-06-11 — Herblaw supply pass

Removed custom finished-potion faucets from OpenPK points and Void Knight supply rewards so combat consumables flow through Herblaw instead of direct reward shops. Void rewards now seed unidentified herbs and secondaries, including tarromin/limpwurt for strength potions, harralander/red spider eggs for restoration potions, marrentill/unicorn horn for cure poison, and smaller ranarr/kwuarm/snape-grass chances for higher-value PvP supplies. Larger Void Dungeon mobs also gained modest limpwurt drops, keeping Void PvM useful without bypassing the potion-making step.

### 2026-06-11 — Firemaking ash prayer loop

Added a simple Prayer sink for Firemaking output. Normal logs still leave authentic `Ashes` for old quest compatibility, while oak, willow, maple, yew, and magic logs now leave `Warm ashes`, `Bright ashes`, `Sacred ashes`, `Blessed ashes`, and `Void ashes` (`1604-1608`). Higher Firemaking above a log's requirement gives a capped chance to leave a second ash. The new ashes reuse the classic ash heap sprite with richer client-side recolor masks, avoiding cache archive churn. Players can use any ash tier on prayer altars to receive scaling Prayer XP, keeping the loop within existing RSC actions. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10099` because the new item definitions are client-visible; no packet shape or cache archive changed.

### 2026-06-11 — Void Sparrow woodcutting source refinement

Expanded the Void Sparrow economy source from a flat magic-log-only rare roll into a small high-level Woodcutting ladder. Players need 90+ Woodcutting; yew logs roll at a much rarer rate, magic logs roll at the better primary-source rate, each level above 90 reduces the denominator slightly, and level 99 adds a further bonus. The item remains tradeable and unchanged mechanically, so this creates a skiller-to-PKer utility loop without adding a new currency, crafting chain, packet, schema, or client cache change.

### 2026-06-11 — Player-made arrow economy polish

Opened the existing Smithing x50 arrowhead recipes under `BATCH_PROGRESSION` so smiths can choose `50 arrow heads (5 bars)` instead of only repeating the x10 recipe. Fletching's better-log shaft output now reads the existing `ItemLogCutDef.xml` `shaftAmount` values, which makes oak through magic logs produce the intended 15/20/30/40/50 shafts when `more_shafts_per_better_log` is enabled; local Voidscape config now enables that flag. This keeps the ammo loop inside existing RSC items: miners supply ore/coal, smiths make arrowheads, woodcutters/fletchers make shafts and arrows, and PKers buy finished ammo. No packet, schema, client version, or cache change.

### 2026-06-11 — Cooking food economy polish

Closed the last confusing edge on the food loop. The Karamja Fishmonger is now presented as a notary only in both server and client NPC definitions, with `Note fish` as the single right-click command; legacy `Cook fish` requests still receive a refusal if an old/test client somehow sends them. Cooking cape holders now receive a small effective-level bonus on burn rolls, making maxed cooks better high-end bulk processors without creating extra raw fish or bypassing Fishing. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10100` because the fishmonger command/description are client-visible; no packet shape or cache archive changed.

### 2026-06-11 — Voidscape menu QA and HUD icon pass

Polished the custom Voidscape menu layer after QA found the PK Catching Simulator highscore box could leave the client feeling frozen and the first replacement top-tab icons did not fit the game. Server message boxes now render in the blocking-dialog path for the custom UI, background chat/panel input is suppressed while blocking dialogs are open, and the desktop mouse bridge can close tall server message boxes without feeding the click through to the world. The shared right-click menu uses the Voidscape dark/purple skin, right-side panels use consistent anchors/sizing across 640/800/1024 widths, compact HUD icons draw smaller, and bottom chat tabs center their icon+label groups. Replaced the rejected local placeholder top-tab icons with a ChatGPT-web-generated green-screen sheet, chroma-keyed/cropped into the existing `top-*.png` and `top-*-34.png` assets. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10101` because the HUD cache and client behavior are visible to players; no packet shape changed.

### 2026-06-11 — Bank readability and OpenRSC-symbol HUD icon refresh

Restyled the custom bank interface to match the Voidscape menu skin without burying items under pure black panels: bank/inventory slot fills and frame bodies now use translucent dark-purple surfaces, stack counts draw with shadow at the bottom of slots, modal/context-menu text is fitted to available width, and the loadout/save/clear flows share the same frame language. The top-tab symbols were refreshed again from user-provided ChatGPT green-screen exports, this time preserving the original OpenRSC icon identities (wrench, smiley, stat bars, spellbook, map, bag) while applying the simpler forged-iron Voidscape material. No packet, schema, or gameplay behavior changed.

### 2026-06-11 — ChatGPT mockup bank skin integration

Integrated the ChatGPT mockup asset pass into the live client skin. New green-screen exports were chroma-keyed into HD top-tab frames/glyphs, location plaque, chat frame/tabs, bank panel frame, bank slot cells, bank tabs, bank action buttons, search frame, close button, and chat/report glyphs. The custom bank now draws those Voidscape skin sprites directly, uses a wider mockup-style 10-column grid, centers item sprites inside larger cells, and compresses vertical offsets so the full bank and inventory fit cleanly at the 640x480 viewport without clipping item counts or bottom chat tabs. The PC client exposes a small public skin-sprite draw wrapper for custom interfaces; no packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-11 — Top-tab icon small-size bake

The top-tab glyphs still read soft in-game because the client draws exact 32px/42px size variants inside 40px/54px tab frames, and the existing variants were plain downscales from the 68px masters. Added `scripts/rebuild-topbar-icons.py` to regenerate all top-tab size variants from the checked-in masters with tiny-UI contrast, unsharp masking, and a subtle silhouette stroke. Rebuilt the 20/24/30/32/34/36/40/42 variants for the six OpenRSC-identity top icons and added their hashes to `Client_Base/Cache/MD5.SUM`, so launcher/cache validation tracks the assets the HUD actually uses. No gameplay, packet, or schema behavior changed.

### 2026-06-12 — Bank overlay QA fixes

Follow-up QA on the Voidscape HUD/bank pass found that hover-open top-tab panels could remain layered over the custom bank at larger native viewports, covering the bank search and note controls. Opening/rendering the bank now suppresses those side panels until the bank closes, keeping bank as the blocking overlay it visually appears to be. The desktop key bridge also normalizes Escape to the client key value expected by the custom bank, and Escape now closes the bank after dismissing any bank modal/loadout/arrange state. No packet, schema, client version, or gameplay behavior changed.

### 2026-06-12 — Bank spacing and legacy item sprite recovery

Fresh QA on the mockup-style bank found the default desktop viewport still felt cramped because it was using the compact 640-safe layout. The bank now switches to a roomier 10-column layout at normal desktop sizes, adds modest breathing room between the bank grid, controls, and inventory grid, and removes the repeated gold item-shelf line from occupied slots so the cell backgrounds no longer read as visual noise. Item sprite lookup also now resolves old `spriteID=-1` definitions through numeric `items:N` locations on the legacy sprite-pack path, and the missing numeric/named custom item icons were packed into `Authentic_Sprites.orsc` with stable numeric slots so they no longer fall through to the magenta checker placeholder. Cache/client changes require `CLIENT_VERSION`/`client_version` `10102`; no packet, schema, or gameplay behavior changed.

### 2026-06-12 — Bank functional QA fixes

Deep bank QA covered search, take-as item/note toggles, arrange modes, loadout save/load/clear, left-click withdraw/deposit, right-click fixed/X/all withdraw/deposit, deposit-inventory, large-bank page tabs, All tab, and page-two withdrawal through the workbench client. The custom bank now preserves the selected bank/inventory slot while an X-amount prompt is open, so `Withdraw-X`, `Deposit-X`, and cert `Uncert+Deposit-X` can submit against the intended item instead of silently doing nothing. Bottom-edge context menus are capped above the Voidscape chat-tab click region so inventory-row menu options remain clickable at desktop viewports. Bank inventory clicks now route through the bank while the bank is open instead of being swallowed by the chat/background panel, and server-side deposit-inventory snapshots unique inventory catalog IDs before mutation so stackable and non-stackable inventory rows are all deposited in one action. The workbench `/state` payload now exposes bank layout/items/presets and active input-X state, plus drag support, so future UI QA can assert behavior from live client state instead of relying on screenshots alone. No packet, schema, cache, or gameplay-loop behavior changed.

### 2026-06-12 — Inventory and bank item-background readability pass

Compared multiple inventory slot background palettes against live item sprites and settled on a warm bronze fill instead of the previous near-black cells. The normal inventory now uses `#3C3125` at reduced opacity with a bronze grid line, and its side-panel body skips the opaque panel texture in favor of a low-alpha underlay so the world remains visible behind the item grid. The custom bank uses the same color family at reduced alpha inside its framed slot art. This keeps item silhouettes readable while tying the item grids back into the forged-metal UI instead of making them feel like black holes. `scripts/run-workbench-client.sh` also accepts extra `VOIDSCAPE_WORKBENCH_JVMARGS`, which lets future visual QA test slot colors/opacities through JVM properties without source edits. No packet, schema, cache, or gameplay behavior changed.

### 2026-06-12 - HUD account switcher

Added a Voidscape-skinned account menu to the upper HUD so players can switch between up to three locally saved accounts without retyping credentials. Successful logins are recorded in `Client_Base/Cache/accounts.txt` with the display name and combat level, the legacy `credentials.txt` single-account file is migrated into the list when needed, and selecting another saved account logs out then immediately authenticates with that account. The current account row is informational instead of clickable; non-current rows can be removed from the same panel, and the Add row disappears once all three slots are filled. `Add account` opens an in-game username/password form, validates credentials through existing custom `INTERFACE_OPTIONS` sub-option `15` plus a hidden `@vsacct@...` server-message response, and only saves the entry after the server accepts it, so bad passwords never persist or create a temporary logged-in session. New account-menu PNGs live under `Client_Base/Cache/voidscape/ui/skin/` with MD5 entries for cache packaging; `accounts.txt` is ignored and excluded from Android bundled assets because it is local runtime data. No new wire opcode, schema, cache archive, or gameplay rule changed.

### 2026-06-12 - Android bank and chat hitbox QA

Tightened the custom bank's Android geometry so its 10-column bank and inventory grids fit inside the classic 512px game framebuffer instead of rendering off-screen. Android now uses smaller bank cells, shorter vertical gaps, and a narrower panel while preserving the desktop bank layout. Bank and inventory stack counts no longer draw a black shadow plate behind the green/cyan amount text. The Voidscape desktop chat overlay also now only forwards mouse events to the chat panel when the pointer is over actual chat controls: tabs, the text entry, or a visible history list. Empty decorative chat-frame space is click-through again, so world clicks are not swallowed by the transparent overlay. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-12 - Android toolchain and wrapper API modernization

Modernized the Android client build to Gradle 8.13, Android Gradle Plugin 8.13, `compileSdk 36`, `targetSdk 35`, and `minSdk 26`, while keeping shared/client Java source compatibility at 1.8. Jetifier was removed because no legacy support-library dependencies remain, the non-launcher activities now explicitly declare `android:exported="false"`, and obsolete RenderScript build knobs were removed for AGP 8. `CacheUpdater` now uses a single-thread executor plus main-thread handler instead of `AsyncTask`; `GameActivity` uses `WindowInsetsController` for immersive mode on modern Android, a modern back callback on Android 13+, and non-forced soft-keyboard show/hide calls; drawable loading now uses the themed `getDrawable` overload. The debug APK builds warning-clean on JDK 17 and launches through the updater into `GameActivity`; in-game authenticated smoke still requires a reachable local server. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-12 - Android authenticated UI smoke hardening

Extended the Android smoke harness after the non-bank panel QA pass. Focused authenticated UI smokes now close the welcome panel from logged client coordinates instead of blind center taps, named screenshots use a timeout-backed `adb screencap` helper so emulator capture stalls cannot hang the run, and the equipment fixture cleanup detects whether the optional `equipped` table exists before falling back to the current SQLite `itemstatuses.wielded` inventory state. Focused Android smokes passed for chat tabs, chat send, equipment equip/remove, magic/prayer selection and toggles, world-map zoom/pan/search/close, settings persistence, and shop/context-menu buy/sell behavior against the local `android/android` fixture. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-13 - Android viewport bank QA and context-menu parity

Finished the next Android UI QA slice against the local `android/android` fixture on port `43598`. Ground-loot readability and wilderness player-target smokes passed, and the shared right-click menu now uses the translucent Voidscape shell on Android when the server advertises custom UI while keeping the rest of Android's native mobile HUD path. The bank smoke harness now waits for a stable rendered bank-object coordinate before tapping and uses a corrected wrapper `Play` fallback for tablet-density layouts. Authenticated bank smokes passed at `720x1280 @ 320dpi` and `1200x1920 @ 240dpi`, covering open/search/scroll/withdraw/loadout save/deposit/deposit-all/loadout load with visual checks for stack counts and bottom chat-tab overlap. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-13 - Bestiary tab

Added a Bestiary page as the third right-panel player-info tab beside Skills and Quests. It reuses existing NPC kill-count history for the monster list, records future NPC drops into a new additive `bestiaryloot` table without backfilling old drops, and intentionally includes ordinary/clutter drops so the page reflects what actually fell for the player. The Bestiary renders as a loot-tracker feed: each killed NPC gets a header like `Goblin x 12`, followed by a grid of item sprites with observed quantities overlaid. The client requests snapshots through custom `INTERFACE_OPTIONS` sub-option `16`; the server replies through appended outbound `SEND_BESTIARY` on custom wire opcode `105` with NPC ids, kill counts, and observed item totals. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10103` for the packet/UI contract change.

### 2026-06-13 - Combat XP locks

Added per-skill XP locks for the combat-facing stats players care about preserving: Attack, Defense, Strength, Ranged, Prayer, and Magic. Hits/Hitpoints is intentionally not lockable because melee training grants Hits XP alongside Attack/Defense/Strength, and skilling stats are intentionally out of scope. Lock state persists in `player_cache` as `xp_lock_mask`, is enforced in both `Player.incExp(...)` and the direct `Skills.addExperience(...)` path, and can be toggled during combat from tiny lock icons on eligible Voidscape skill rows. The client sends toggles through custom `INTERFACE_OPTIONS` sub-option `17`, while the existing `SEND_EXPERIENCE_TOGGLE` packet appends a 32-bit lock mask for custom clients `>= 10104`; `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10104`. The right-panel loot-tracker tab now displays as `LOOT` instead of `BESTIARY`.

### 2026-06-13 - Combat style selector geometry cleanup

Kept the combat style selector in the original OpenRSC left-side HUD area while offsetting it below Voidscape's location plaque so the two overlays no longer collide. The selector keeps the classic grey/red styling, ties rendered rows and click hitboxes to shared geometry helpers, and scales its width/row height slightly across Voidscape desktop size classes. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-13 - Voidscape HUD tab behavior correction

Re-anchored the Voidscape-skinned top menu tabs to the original OpenRSC tab strip behavior: a top-right strip with original left-to-right order of Options/Friends/Magic/Stats/Minimap/Inventory and fixed right-column side panels instead of experimental under-icon panel anchoring. The cells and glyphs now scale from the side-panel column width, so the six buttons keep roughly the same visual proportion as original RSC instead of remaining tiny on larger desktop windows. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-13 - Compact clear-glass right-panel pass

Started standardizing the Voidscape side panels around the RSC-friendly clear-glass treatment instead of the heavier generated chrome. Inventory now uses a compact transparent grid shell; Friends/Ignore and Magic/Prayer use a shared slim glass panel with lower-alpha bodies and padded spell/prayer list content; Skills/Quests/Loot plus Options now sit in the same slimmer panel language with slightly stronger text contrast for dense rows; and the account switcher popup uses the same glass shell with lighter native row surfaces. A responsive workbench sweep now covers 640x480, 800x600, and 1024x768, with the loot-panel scenario waiting for the async tracker snapshot before capture. No packet, schema, cache archive, or gameplay behavior changed.

### 2026-06-13 - Void Arena Death Match setup

Replaced the Void Arena lobby's ranked-only right-click flow with a `Death Match` setup dialog. Ranked remains the fixed competitive ruleset with max Attack/Strength/Defense/Hits and F2P-only loadouts; unranked challenges can toggle F2P-only gear, prayer, ranged, and magic. The old duel/staking option is suppressed inside the Void Arena footprint so players use wilderness-style arena combat rather than the duel system. Match rules are enforced server-side through attack, spell, and prayer hooks, and players are restored to full Hits when entering, starting, returning from, or leaving arena fights. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10108` because custom `INTERFACE_OPTIONS` sub-option `18` now carries a rule mask and arena lobby metadata includes ranked eligibility.

### 2026-06-14 - Void Arena underground lava hall

Relocated the four-cage Void Arena Death Match footprint from the above-ground ocean test sector `h0x60y38` to the real underground sector `h3x60y38`. The new map keeps the same compact four cages and narrow spectator hall, but bakes the entire footprint into a dark cave/lava fight-club space; the old above-ground sector is restored to authentic terrain during landscape generation. Lobby, cage, Herald, bank chest, altar, client right-click, and area-plaque bounds now point to the underground coordinates, with cage prayer altars centered in each arena. The eight empty neighboring underground sectors and non-walkable space around the arena footprint are baked as lava so the cages feel suspended over a dangerous underground field instead of falling off into flat black. The lobby/cage edge also gets a light decorative pass using existing void braziers, obelisks, monoliths, and crystals from the cache. The regenerated `Custom_Landscape.orsc` is synced to both server data and the client cache, with the cache MD5 updated; no packet, schema, client version, or combat rule changed.

### 2026-06-14 - Void Arena Death Match QA fixes

Fixed the first Death Match QA pass after live testing showed the setup dialog could require repeated clicks, lobby player menu labels leaked `(unranked)` onto every option, cage fighters did not get an attack row, challenge messages were too vague, and unranked fights could be held open forever. The client now handles the setup dialog in the input path with one consumed click per action, keeps rating/unranked labels scoped to the `Death Match` menu row, and exposes `Attack` while both players are inside an arena cage. The server now sends explicit request messages naming the challenger and rules, and unranked fights reset both online players back to the lobby after five minutes without recording a ranked result. No packet, schema, cache, or client version changed.

### 2026-06-14 - Void Arena duel-style Death Match handshake

Moved Death Match setup from a client-owned one-shot popup to a server-owned duel-style handshake while keeping the actual combat as wilderness-style 1v1 PvP. Reciprocal `Death Match` requests now open a shared setup screen, rule changes reset both accept flags, both players must accept to reach a confirm screen, and both must confirm before the server reserves the first available cage, heals both fighters, teleports them in, and starts a five-second center-screen countdown. Normal teleports are blocked for active Death Match fighters through the central `Player.teleport(...)` path, with arena-managed moves using an explicit bypass, so home teleport and future plugin teleports cannot escape a match; the spell and boundary-teleport paths also check the arena before consuming resources or moving a player. Combat and magic are denied until the countdown has elapsed, trading is blocked for either side of an active match, unranked fights draw after five minutes, and ranked fights draw after ten minutes without changing rating. `CLIENT_VERSION`, local `client_version`, and voidbot moved to `10109`; the existing `INTERFACE_OPTIONS` sub-option `18` action byte now distinguishes challenge/decline/update/accept/confirm, and the hidden `@vsarena@` channel now carries setup, close, and countdown controls.

### 2026-06-14 - Void Arena match-end cleanup hardening

Tightened Death Match lifecycle cleanup after QA moved from setup into match-ending cases. Every end path now uses the same arena session cleanup for online fighters: close the client-side setup/countdown overlay, reset combat/trade/duel/menu/walk state, clear instance, heal Hits to full, and refresh inventory/equipment/prayer state. Active fighters are also checked every movement tick and snapped back to their assigned cage start tile if a pathing or object edge ever places them outside their cage. No packet shape, schema, cache, or client version changed.

### 2026-06-14 - Void Arena ranked match ledger

Added an authoritative `voidarena_ranked_matches` ledger for resolved ranked Death Matches. Each ranked result now records season, winner, loser, both players' before/after ratings, applied delta, disconnect-loss flag, arena slot, and start/end timestamps after rating stats save successfully. MySQL and SQLite core schemas plus additive migration patches were updated together so future leaderboard, crown, season-reset, and anti-abuse work can audit individual ranked outcomes instead of inferring history from aggregate stats alone. No packet shape, cache, client version, or combat rule changed.

### 2026-06-14 - Void Arena ranked audit commands

Added read-only administrator tooling for the ranked Death Match season. `::arena season` now previews the current season id, ranked match count, top ratings, and the latest ledger rows, while `::arena audit`, `::arena audit recent <limit>`, and `::arena audit <player> <limit>` inspect recent ranked match outcomes from the new ledger. The ledger query surface joins player names and rating before/after values so staff can review disconnect losses, placement outcomes, repeated pairings, and rating movement before destructive season-reset or reward tooling exists. No packet shape, schema, cache, client version, or gameplay rule changed.

### 2026-06-14 - Void Arena ranked reset guardrail

Added an administrator-only ranked reset flow for the current Void Arena season. `::arena season reset` now opens a destructive preview, emits a short-lived confirmation token, summarizes affected ranked profiles, preserves the ranked match ledger, and blocks confirmation while any Death Match setup or fight is active. `::arena season confirm <token>` re-checks that ranked profile and ledger counts have not changed since preview, resets affected profiles to the configured starting rating with zero wins/losses/disconnect losses, increments each profile's `resetCount`, clears cached ratings, and refreshes visible lobby rating labels. No packet shape, schema, cache, client version, or combat rule changed.

### 2026-06-14 - Voidscape Discord rich presence branding

Removed the PC client's hardcoded OpenRSC Discord application id, default status copy, and `openrsc_logo` image reference from Discord rich presence. The client now reads `voidscape.discordApplicationId` / `VOIDSCAPE_DISCORD_APPLICATION_ID` and disables RPC when no Voidscape application id is configured, preventing players from advertising the inherited Open RuneScape Classic application. The launcher passes the same Discord branding properties from packaged settings, system properties, or environment variables, and friend-beta packaging can embed them for testers. No packet shape, schema, cache, or client version changed.

### 2026-06-20 - PC client local walk prediction Phase 3

Added client-side presentation prediction for ordinary ground walk clicks without changing server tick rate, opcodes, path validation, or gameplay timing. Accepted-looking local ground clicks now enqueue a short speculative visual route immediately, while authoritative `PLAYER_COORDS` packets reconcile against the predicted world-tile path; matching packets keep the walk seamless, stopped/adjusted packets ease the local render position back to the server tile, and region changes still snap through the existing authoritative path. The PC workbench `/state` payload now exposes render pixels, local/world tiles, and prediction/correction flags so responsiveness QA can verify open-ground clicks, invalid scene clicks, server corrections, and rapid destination changes from live client state.

### 2026-06-18 - PC client frame pacing Phase 1

Reduced desktop-client frame-pacing hitches without changing server tick rate, opcodes, or gameplay timing. The PC scaled-window handoff no longer blocks the game loop on `SwingUtilities.invokeAndWait`, the interpolation scaler now draws into a reusable buffer instead of allocating stitched scaled images every paint, and live-game roof/wall visibility now updates from cached scene state instead of removing and re-adding roof grid models every frame. Added an opt-in `FramePacingMonitor` controlled by `-Dvoidscape.framePacing=true` so future smoothness work can compare FPS and frame-time consistency in the same scene.

### 2026-06-18 - PC client smooth camera Phase 2

Smoothed the classic PC camera presentation without changing server tick rate, opcodes, pathing, combat, or gameplay timing. Classic camera follow and rotation now feed `scene.setCamera` through a render-time elapsed-clock smoother using the same smoothstep-style interpolation curve as the cinematic camera path, while cinematic camera modes keep their existing controls and teleports/region jumps still snap to the authoritative target instead of drifting across the map.

### 2026-06-18 - Public beta landing download funnel

Changed the public portal landing from a prelaunch/whitepaper-first funnel into a play-now beta page. The first screen now leads with direct desktop launcher and Android APK downloads, keeps Discord as an optional reward/community step, shows real current-build screenshots immediately below the download block, and wires Discord reward buttons into the existing OAuth flow. `GET /api/public` now returns launcher/APK availability in public mode so the ad-facing landing can reflect built artifacts without requiring Discord auth. No gameplay, packet, schema, or client-version behavior changed.

### 2026-06-18 - Public integrity API and staff command ledger

Added a central staff command audit record for every moderator/admin command attempt, including beta-blocked attempts, using the existing `staff_logs` table with compact sanitized metadata (`command`, `status`, `category`, optional rough target, and arg count). The portal now exposes `GET /api/integrity` plus an `integrity` block in `GET /api/public`; the payload only publishes aggregates and sanitized recent entries, never IPs, raw player identifiers, or full command arguments. In dev it can aggregate OpenRSC SQLite `staff_logs`; in production it can read a pre-sanitized `PORTAL_INTEGRITY_SNAPSHOT`, otherwise it reports that the game snapshot is not connected yet. No gameplay, packet, schema, or client-version behavior changed.

### 2026-06-18 - Integrity snapshot bridge and live landing counters

Added `scripts/export-integrity-summary.mjs` to turn the game `staff_logs` command-audit rows into a public-safe `integrity-summary.json` snapshot for the portal host. The public landing trust section now hydrates from the `integrity` payload in `/api/public`, showing compact live counters for staff command attempts, blocked beta-admin attempts, build-hash status, and scan/provenance status while the tab copy updates from the same data. Portal smoke coverage now exercises the exporter, snapshot ingestion, public-mode `/api/integrity`, and the landing payload. No gameplay, packet, schema, or client-version behavior changed.

### 2026-06-18 - Staff item provenance receipts

Added an `item_provenance_events` ledger plus DB insert path for staff-created items from admin spawn commands. `item`, `noteditem`, `bankitem`, `grounditem`, `fillbank`, and `bestinslot` now write compact item receipt rows with actor/target ids, command, destination, catalog id, amount, timestamp, and optional note; the public integrity exporter and portal surface only aggregate counts and sanitized recent receipt metadata. No gameplay, packet, cache, or client-version behavior changed.

### 2026-06-18 - Economy integrity scanner

Extended the public integrity exporter with a read-only economy scan against the game SQLite database. The scan flags hard data anomalies such as impossible stack amounts, unknown item catalog ids, broken inventory/bank/equipment references, duplicate item ownership rows, invalid storage slots, out-of-range max/current stats, and impossible XP values. The website receives only public-safe counters and categories through `economyScans`, while the private findings JSON stays on the server for the admin-only `::integrity` command (`summary`, `recent`, and `player <name>`). No packet, cache, client-version, or gameplay rule changed; the scanner records findings only and does not auto-fix player data.

### 2026-06-19 - NPC item-origin provenance receipts

Started broadening the item provenance ledger from staff-created items into a real lifecycle audit trail. NPC loot drops now write `item_origin` receipts into `item_provenance_events` with source `npc_drop`, public-safe destination categories (`ground` or `inventory_avarice`), catalog id, amount, noted flag, first eligible player, location, and compact NPC metadata. Player drops, successful ground-item pickups, death drops, completed trades, shop buys/sells, and auction-house list/buy/cancel/collect flows now write `item_transfer` receipts with source/destination categories for public aggregation. The integrity exporter and portal expose aggregate origin, transfer, NPC-drop, and source-category counts without publishing player names or raw item-instance trails. No packet, cache, client-version, or gameplay rule changed.

### 2026-06-19 - Public item receipt board

Added a compact visual receipt board to the public landing trust section's Items tab. The board renders live public provenance source bars and a short anonymized recent receipt feed from `itemProvenance.sources` and `itemProvenance.recent`, so visitors can see item-audit evidence without needing raw player data or Discord login. No gameplay, packet, schema, cache, or client-version behavior changed.

### 2026-06-19 - Public build proof board

Added a compact Source tab proof board to the public landing trust section. The portal now computes SHA-256 hashes for the launcher and Android APK files it is serving, exposes launcher-manifest proof for the client runtime/cache payload, and reads deployed source metadata from `web/portal/build-meta.json` or local git when available. The UI shows short hash tiles plus source/commit status without requiring Discord login. No gameplay, packet, schema, cache, or client-version behavior changed.

### 2026-06-19 - Dedicated public transparency page

Moved the full public trust dashboard off the ad-facing landing page and onto `/transparency`. The landing page now keeps a compact Trust teaser linked from nav/footer, while the standalone transparency page renders the password/source/staff/items/scans tabs, public build proof, item receipt board, and a staff command ledger from the same sanitized `/api/public` integrity payload. Public-mode portal smoke coverage now asserts the landing stays light and `/transparency` ships the standalone dashboard. No gameplay, packet, schema, cache, or client-version behavior changed.

### 2026-06-19 - Bug-feed triage fixes

Fixed the first Discord `#bug-feed` pass. Physical Subscription cards now redeem for unlinked beta characters through a `char_sub:<playerId>` fallback while linked characters keep the account-wide `acct_sub:<webAccountId>` ledger; beta referral rewards now block obvious same-connection alt referrals; mutating `::lootbeam` commands immediately flush player cache so recent settings survive quick logout/reconnects; the launcher Account icon opens the public-safe `#account` route; the custom bank All tab clamps negative scroll rows; classic-mode bank loadouts now remember and re-wield saved inventory-worn gear; the friends/ignore panel no longer renders placeholder `W` padding beside `Remove`; combat-style sync ignores stale conflicting server echoes right after a local choice; and closed channels get a short reconnect grace before logout is scheduled. No packet shape, schema, or cache archive changed.

### 2026-06-19 - Android Play review server default hardening

Hardened the Android wrapper after Play Console rejected production bundle `2 (1.0.1)` for broken functionality. Release builds now use the Play package id `com.voidscape.gg`, version `1.0.2` / code `3`, and always default fresh installs to the public Voidscape server even when the review device reports emulator-like build fingerprints. The debug-only server picker still supports `10.0.2.2`, LAN, and manual endpoints for local smoke tests, but release builds hide that picker and ignore saved developer endpoints. The Android smoke harness now derives its package id from `ANDROID_SMOKE_APP_ID` with `com.voidscape.gg` as the default, and Gradle can sign release bundles from the external Voidscape upload keystore when credentials are supplied outside Git. No packet, cache, schema, or gameplay rule changed.

### 2026-06-19 - Undead Siege minigame

Added `Undead Siege`, a Void Herald wave-survival minigame inspired by classic undead horde play while staying RSC-native and server-side only. Solo players or nearby party members enter a private Void Dungeon phase with a temporary safe kit, fight escalating zombie/skeleton/ghost waves for per-run points, buy supplies through intermission menus, and receive a conservative loot payout based on highest cleared wave; wave 10 unlocks the reusable `Gravewalker` title. The feature adds reusable arena kit snapshot support plus scoped safe-death/default-NPC-reward suppression attributes so temporary kits and spawned undead cannot leak item loss, default drops, or combat XP while ordinary NPC deaths continue using the existing default flow unless explicitly flagged. Blocking `PlayerLogoutTrigger` cleanup now runs inline before logout save so safe-kit restorations persist before the player row and item tables are written; blocking `KillNpcTrigger` cleanup also runs inline only for flagged suppressed-death NPCs so Siege point/wave accounting happens before those spawned NPCs are unregistered. The Shilo Village Nazastarool quest hook ignores suppressed-death NPCs so Siege milestone bosses can reuse those existing NPC definitions without spawning quest phases or writing quest cache keys. Ranged ammo drops now inherit the target's instance id so temporary arrows/throwing items stay inside private phases, and dev-only `::undeadsiege` commands provide a fast QA entry path. Files: `ArenaKitSnapshot.java`, `Player.java`, `Npc.java`, `PluginHandler.java`, combat XP suppression call sites, `UndeadSiege*`, `VoidHerald.java`, `VoidTestCommands.java`, `ShiloVillageNazastarool.java`, ranged projectile helpers, and `PlayerTitle.java`. No packet, opcode, client cache, schema, or client-version change was added.

### 2026-06-19 - Undead Siege Wilderness Ruins pass

Moved `Undead Siege` from the large private Void Dungeon footprint to a compact private instance over the labelled Wilderness `Ruins` area around `(167,256)`, reusing the authentic abandoned scenery there instead of adding cache assets or packets. Siege-spawned NPCs now opt into a generic force-chase NPC attribute so they keep homing toward assigned players across the ruin bounds and do not retreat at low health, while ordinary NPC behavior remains unchanged. Early non-boss Siege undead now receive low temporary combat stats, starting at 6 hitpoints and low defense on wave 1, so the opening rounds die in a few ranged hits before later waves scale back up.

### 2026-06-19 - Undead Siege Ruins pathing and supply shop pass

Improved the `Undead Siege` Ruins iteration after in-game testing showed undead could wedge behind the wilderness trees and abandoned-building clutter. Force-chase NPCs now use a deeper A* search, and Siege tracks per-NPC movement progress so stalled undead are quietly pulled to nearby walkable approach tiles with a clear lane back toward the player, preserving the obstacle-rich Ruins feel while keeping waves moving. Between-wave supplies now use the existing shop interface as a point-priced supply screen with real item sprites for swordfish, strength potion, bow/ammo, and rune bundles; `Shop` supports per-item display prices, and the custom PC client renders the sentinel shop as `Undead Siege Supplies` with point labels and buy-only controls. No new opcode, packet shape, cache asset, schema, or client-version bump was added.

### 2026-06-19 - Undead Siege arcade combat loop pass

Tightened `Undead Siege` toward an arcade horde loop after live testing showed standard RSC melee combat was trapping ranged players. Siege now snapshots combat-relevant player stats, applies equal temporary level-40 combat/prayer/magic/ranged/melee stats for the run, restores those stats through the same safe cleanup paths as the temporary kit, and starts players with 8 swordfish. Siege undead opt into a scoped contact-attack NPC hook that deals capped bump damage without calling normal `startCombat`, so players can kite and shoot while ordinary NPCs continue using default combat; contact misses can chip occasionally, while player attacks against Siege undead use a scoped damage floor so early waves do not devolve into repeated 0s. Scoped safe-death runs now skip the stock death packet before cleanup, avoiding normal item-loss death-screen messaging while still using the mini-game restoration flow. Between-wave supply time now lasts about 30 seconds, announces `::shop`, and lets players reopen the sprite shop during that window. The PC client forces a lighter fog distance while the local player is inside the Siege Ruins footprint, with no new packet, opcode, cache asset, schema, or client-version bump.

### 2026-06-19 - Bug-feed follow-up fixes

Fixed the follow-up Discord `#bug-feed` pass. Void Acolyte and Void Quartermaster menus now use non-echoing `multi(...)` prompts before their explicit `say(...)` branches, so selected player dialogue appears once instead of twice. World-map autowalk now survives unwanted NPC aggro by pausing during the mandatory no-retreat opening combat rounds, then using the existing NPC-retreat state/sound/plugin path and re-pathing from the current tile; deliberate attack clicks and PvP opponents still cancel autowalk immediately. The Void Colossus rift confirmation now says `Enter the arena` from the hub and `Leave the arena` from the fight side instead of the generic `Enter the rift` label on both paths. The stale `10111` Void chest/bank/Sir Charles reports were traced to out-of-date client definitions and the launcher update path already moves fresh installs to `10112`. No packet, opcode, cache, schema, or client-version change was added.

### 2026-06-19 - Portal live online count

Fixed the public beta website status panel so `players online` is no longer hardcoded to zero. When `PORTAL_OPENRSC_DB` is configured, `/api/public` now reads the live OpenRSC SQLite `players.online` count and returns that value to the landing/header status UI, falling back to zero only if the game DB is unavailable. No gameplay, packet, schema, cache, or client-version behavior changed.

### 2026-06-19 - Android portrait HUD pass

Kept Android portrait mode as supported player-facing UI and fixed the first reported portrait pain points. Android context menus now use larger touch rows independent of the chosen font size, and the Voidscape minimap frame is inset so its right-edge chrome no longer clips against narrow portrait buffers. The public Android APK version moved to `1.0.3` / code `4` so fresh downloads and upgrades pick up the current client definitions, including the Sir Charles NPC text for Death Match arena id `862`. No gameplay, packet, schema, or cache format changed.
