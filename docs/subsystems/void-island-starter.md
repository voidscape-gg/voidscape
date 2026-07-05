# Void Island Starter Flow

Voidscape routes new accounts to a small Void Island after appearance creation. The island is a one-time onboarding step: players land in a connected Void Council clearing and pick one of two onboarding tracks, then choose one of three starter paths, receive an early-game XP boost for that path's skills, get a matching starter kit, and teleport to the configured respawn location.

## Welcome Choice (onboarding tracks)

The first thing a new character sees is a 2-way menu (the custom client renders it as a card picker):

1. **"I've played Classic — what's new in Voidscape?"** — council lore, then the Void Archivist tour (see Veteran Tour below).
2. **"Skip"** — marks the intro seen, opens the path picker immediately, lands in Lumbridge.

State and wiring:

- `server/src/com/openrsc/server/content/VoidOnboarding.java` owns the track state (`void_onboard_track`: 2 veteran / 3 skip; `1` is a retired guided sentinel migrated to skip on login) and the option-string constants (client contract).
- `server/plugins/com/openrsc/server/plugins/custom/onboarding/VoidWelcome.java` drives the menu and dispatches tracks. It is fired from `PlayerAppearanceUpdater` through the `VoidWelcomeTrigger` plugin trigger (blocking dialogue must run in plugin context, never in packet context), and from `PostLoginReadyTrigger` on login resume after the player has been registered with the world.
- Dismissing the menu keeps the track unset; talking to any councilor or relogging re-prompts. Legacy accounts that already saw the lore (`void_intro_seen`) never see the welcome menu retroactively.
- The council gate at y=32 still blocks the path north until the lore is seen; skip stores `void_intro_seen` so the gate lifts without dialogue.
- `VoidPath.openPathChoice(player, npc)` is the extracted path-choice dialogue; the Herald calls it with an NPC, the skip/login-resume paths call it NPC-less.

## Retired Guided Tour

The old **"I'm new to Classic"** guided track and Tutorial Isle runtime are retired. Fresh players cannot choose it. `VoidOnboarding.retireGuidedState(...)` migrates stale `void_onboard_track=1`, `void_guided_stage`, or `void_guided_kit` accounts into the skip/path-choice flow, marks `void_intro_seen`, and removes guided cache keys. The Tutorial Isle terrain may still exist in cache assets and `Point.inVoidTutorialIsle(...)` remains only as a recovery coordinate helper; `WorldPopulator` no longer loads the old Tutorial Isle NPC/scenery loc files, and walk handlers no longer run guided gate checks.

## Veteran Tour ("what's new in Voidscape?")

`server/src/com/openrsc/server/content/VoidVeteranTour.java`, driven by the Void Archivist (NPC 868, at 26,28 on the island and 127,650 beside the Lumbridge subscription vendor). A veteran-track player must complete the first Archivist visit before the Herald opens path choice. That first veteran visit runs two one-time demos, then an "ask me about..." topic menu (repeatable forever, also at the Lumbridge spawn):

- **Rested XP demo:** explains the mechanic and fills the pool to the 45-minute cap via `RestedExperience.grantFull(player)` (guarded by `void_vet_rested_gift`; the method drains elapsed session time first so the stale drain marker can't eat the gift). This reward is gated to players who chose the veteran welcome track.
- **Loot beam demo:** spawns an owner-scoped dragonstone ground item beside the Archivist (20s TTL, well under the 64s owner-only window). Picking it up dissolves it (`TakeObjTrigger` on the `void_vet_beam_demo` attribute) — a flavor beat, not an exploit. This demo is gated to players who chose the veteran welcome track.
- **Topic menu:** XP rates / paths / trading (auction house + subscription cards) / chat & titles / PvP (arena, bounty) / QoL (map autowalk, `::b`, `::commands`, `::qoloptout`). Never mentions clans/parties or beta commands (cut/locked down).

## Path State

- `server/src/com/openrsc/server/content/VoidPath.java` owns the starter path state.
- `void_path` stores the chosen path in the existing player cache. `1` is Warrior, `2` is Forager, and `3` is Arcanist.
- `void_path_starter_kit` records that the starter kit has been granted. This prevents starter-item farming if path state is ever manipulated during testing.
- `void_intro_seen` records that the Void Council intro finished. If a new player disconnects before the sequence ends, the intro plays again on their next login.
- `PlayerAppearanceUpdater` sends unchosen new accounts to the council clearing after appearance submission.
- `LoginPacketHandler` routes unchosen accounts saved on Tutorial Island, Tutorial Landing, the legacy Void Island bounds, the current starter island, or retired guided state back to the appropriate starter entry point.
- While `void_path` is unset and the player is on Void Island, the shared server teleport path blocks any destination outside the starter island. This keeps spells, commands, object teleports, and other escape routes from skipping the path choice.

## Council Intro

`server/src/com/openrsc/server/content/VoidStarterIntro.java` owns the first-login intro. Players start in the southern clearing at `(24,37)`, surrounded by three Void Councilors. The council speaks one line at a time, then releases the player with a message that the path north is open. No teleport occurs after the dialogue; the clearing is part of the same starter island and is connected directly to the Void Herald path area.

The desktop client draws purple beams over decorative skull/item tiles in the intro clearing using fixed tile markers. Those beams are cosmetic only and do not create ground items or loot interactions.

Void Island is carved out as a safe zone even though its coordinates sit inside the classic wilderness-level calculation. Players cannot attack each other there, the wilderness overlay is suppressed, and starter players are not eligible for wilderness-only systems while they are choosing a path.

## Paths

- Warrior's Path grants 2x XP in Attack, Defense, and Strength until each boosted skill reaches level 50. Starter kit: iron 2-handed sword, 10 cooked meat, bronze plate body, bronze medium helmet, and bronze legs.
- Forager's Path grants 2x XP in Fishing, Cooking, and Mining until each boosted skill reaches level 50. Starter kit: net, fishing rod, 50 fishing bait, bronze pickaxe, tinderbox, 100 coins, 2 cooked meat, and 2 bread.
- Arcanist's Path grants 2x XP in Ranged and Magic until each boosted skill reaches level 50. Starter kit: shortbow, 50 bronze arrows, blue wizard hat, wizard robe, 100 air runes, 50 mind runes, 25 fire runes, and 2 bread.

## Herald Flow

`server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java` is the interaction point. For unchosen players it explains that each path grants 2x XP until level 50 plus a starter kit, opens the three-option menu, stores the path, grants the starter kit, and teleports the player to spawn. Veteran-track players who have not completed the required Archivist first visit are redirected south before the path menu opens. Starter kit grant is preflighted before the path is committed; if the full kit cannot fit, the player keeps no path and can try again after clearing space. On beta worlds with `want_beta_onboarding_guide` enabled, the Herald then opens the beta guide menu once and stores `void_beta_guide_seen`; otherwise it sends the short welcome box with the selected boost and kit summary.

The repeatable beta toolkit command is `::beta` / `::betaguide`. `server/src/com/openrsc/server/content/BetaOnboardingGuide.java` owns the menu flow for click-to-teleport test locations, 99-stat presets, beta item kits, FarmSim drop-rate projections, checklist text, common coordinates, and item IDs. The toolkit is gated by `want_beta_onboarding_guide`, so release worlds can disable the stat/item helpers without changing player commands.

If a player has already chosen a path, the Void Herald delegates to the existing Void Rush dialogue instead of showing the starter choices again.

`VoidPath.boostsSkill(...)` enforces the level cap per boosted skill, so old characters with an existing path keep their path identity but stop receiving the starter multiplier on any boosted skill that has reached level 50.

## Client UI

The custom PC client recognizes the exact three path option prefixes and renders a centered card picker instead of the stock options menu. The cards are visual only; the server remains authoritative and receives normal menu replies. Keep the option prefixes intact when changing the server text:

- `Warrior's Path - 2x XP:`
- `Forager's Path - 2x XP:`
- `Arcanist's Path - 2x XP:`

The welcome choice uses the same pattern (`isVoidscapeWelcomeMenu` / `drawVoidscapeWelcomeMenu` in `mudclient.java`). Its prefixes come from `VoidOnboarding.OPTION_*`:

- `I've played Classic`
- `Skip the intro`

A mismatched string safely degrades to the stock options menu. The onboarding NPCs (864-868) exist in both `NpcDefsCustom.json` and the positional client list in `EntityHandler.java` — append-only, same order on both sides. Adding them bumped `CLIENT_VERSION` to 10122.
