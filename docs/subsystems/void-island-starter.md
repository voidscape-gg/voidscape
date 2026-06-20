# Void Island Starter Flow

Voidscape routes new accounts to a small Void Island after appearance creation. The island is a one-time onboarding step: players begin in a connected Void Council clearing, hear the infection intro, walk north to the Void Herald, choose one of three paths, receive an early-game XP boost for that path's skills, get a matching starter kit, then teleport to the configured respawn location.

## Path State

- `server/src/com/openrsc/server/content/VoidPath.java` owns the starter path state.
- `void_path` stores the chosen path in the existing player cache. `1` is Warrior, `2` is Forager, and `3` is Arcanist.
- `void_path_starter_kit` records that the starter kit has been granted. This prevents starter-item farming if path state is ever manipulated during testing.
- `void_intro_seen` records that the Void Council intro finished. If a new player disconnects before the sequence ends, the intro plays again on their next login.
- `PlayerAppearanceUpdater` sends unchosen new accounts to the council clearing after appearance submission.
- `LoginPacketHandler` routes unchosen accounts saved on Tutorial Island, Tutorial Landing, the legacy Void Island bounds, or the current starter island back to the appropriate starter entry point.
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

`server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java` is the interaction point. For unchosen players it explains that each path grants 2x XP until level 50 plus a starter kit, opens the three-option menu, stores the path, grants the starter kit, and teleports the player to spawn. On beta worlds with `want_beta_onboarding_guide` enabled, the Herald then opens the beta guide menu once and stores `void_beta_guide_seen`; otherwise it sends the short welcome box with the selected boost and kit summary.

The repeatable beta toolkit command is `::beta` / `::betaguide`. `server/src/com/openrsc/server/content/BetaOnboardingGuide.java` owns the menu flow for click-to-teleport test locations, 99-stat presets, beta item kits, FarmSim drop-rate projections, checklist text, common coordinates, and item IDs. The toolkit is gated by `want_beta_onboarding_guide`, so release worlds can disable the stat/item helpers without changing player commands.

If a player has already chosen a path, the Void Herald delegates to the existing Void Rush dialogue instead of showing the starter choices again.

`VoidPath.boostsSkill(...)` enforces the level cap per boosted skill, so old characters with an existing path keep their path identity but stop receiving the starter multiplier on any boosted skill that has reached level 50.

## Client UI

The custom PC client recognizes the exact three path option prefixes and renders a centered card picker instead of the stock options menu. The cards are visual only; the server remains authoritative and receives normal menu replies. Keep the option prefixes intact when changing the server text:

- `Warrior's Path - 2x XP:`
- `Forager's Path - 2x XP:`
- `Arcanist's Path - 2x XP:`
