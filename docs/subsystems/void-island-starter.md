# Void Island Starter Flow

Voidscape routes new accounts to a small Void Island after appearance creation. The island is a one-time onboarding step: players talk to the Void Herald, choose one of three paths, receive a permanent XP boost for that path's skills, get a matching starter kit, then teleport to the configured respawn location.

## Path State

- `server/src/com/openrsc/server/content/VoidPath.java` owns the starter path state.
- `void_path` stores the chosen path in the existing player cache. `1` is Warrior, `2` is Forager, and `3` is Arcanist.
- `void_path_starter_kit` records that the starter kit has been granted. This prevents starter-item farming if path state is ever manipulated during testing.
- `PlayerAppearanceUpdater` teleports unchosen new accounts to Void Island after appearance submission.
- `LoginPacketHandler` routes unchosen accounts saved on Tutorial Island, Tutorial Landing, or the legacy Void Island bounds back to current Void Island.

## Paths

- Warrior's Path grants 2x XP in Attack, Defense, and Strength. Starter kit: iron 2-handed sword, 10 cooked meat, bronze plate body, bronze medium helmet, and bronze legs.
- Forager's Path grants 2x XP in Fishing, Cooking, and Mining. Starter kit: net, fishing rod, 50 fishing bait, bronze pickaxe, tinderbox, 100 coins, 2 cooked meat, and 2 bread.
- Arcanist's Path grants 2x XP in Ranged and Magic. Starter kit: shortbow, 50 bronze arrows, blue wizard hat, wizard robe, 100 air runes, 50 mind runes, 25 fire runes, and 2 bread.

## Herald Flow

`server/plugins/com/openrsc/server/plugins/custom/npcs/VoidHerald.java` is the interaction point. For unchosen players it explains that each path grants permanent 2x XP plus a starter kit, opens the three-option menu, stores the path, grants the starter kit, teleports the player to spawn, and sends a short welcome box with the selected boost and kit summary.

If a player has already chosen a path, the Void Herald delegates to the existing Void Rush dialogue instead of showing the starter choices again.

## Client UI

The custom PC client recognizes the exact three path option prefixes and renders a centered card picker instead of the stock options menu. The cards are visual only; the server remains authoritative and receives normal menu replies. Keep the option prefixes intact when changing the server text:

- `Warrior's Path - 2x XP:`
- `Forager's Path - 2x XP:`
- `Arcanist's Path - 2x XP:`
