# Void Arena

Void Arena is Voidscape's controlled death-match space. Players enter the lobby, challenge other eligible players, and fight inside allocated cages. Ranked PvP writes to Void Arena rating stats; custom solo challenges such as DM King reuse the cage/session safety machinery without touching ranked tables.

## Core files

- `server/src/com/openrsc/server/content/voidarena/VoidArena.java` owns lobby entry/exit, player challenge state, cage allocation, countdowns, match resolution, boundary enforcement, teleport blocking, rating metadata, and DM King sessions.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaConfig.java` defines lobby/exit tiles and cage bounds.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaKitSnapshot.java` serializes a player's real inventory, equipment, and worn sprites while temporary DM King supplies are active.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidArenaHerald.java` handles leaderboard/help NPC actions.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/DmKing.java` handles DM King talk/challenge/attack/range/spell/kill ownership checks.

## Ranked Death Matches

Ranked challenges require the current ranked stat gate: 99 Attack, 99 Strength, 99 Defense, and 99 Hits. Ranked results write through the Void Arena stats path and can affect visible leaderboard state after placement rules. Unranked setup rules stay outside ranked Elo.

Match cages are allocated from the shared slot list. Active matches block teleporting, force players back inside their assigned cage if they leave its bounds, and clean up on death/logout/timeout.

## DM King

DM King is a level-123 challenge hook in the Void Arena lobby at `(604, 2915)`. The lobby NPC uses id `862`; each active fight spawns a dynamic attackable DM King with id `863` inside the allocated cage. Both NPC definitions intentionally copy the Death Match Arena Void Knight visual exactly; only the name, combat stats, and challenge behavior differ.

Starting the challenge requires the player to be in the Void Arena lobby, out of combat, not already in a match/setup/challenge, using a custom client at version `10112` or newer, and passing the same ranked stat gate as ranked Death Matches. On beta worlds with `want_beta_onboarding_guide` enabled, accepting the challenge first sets the player's stats to 99 so testers can immediately try the fight. If no cage is available, the challenge is denied.

The challenge is deliberately separate from ranked PvP:

- No Elo, placement, or leaderboard writes.
- No item loss, bones, drops, hardcore status change, or economic reward.
- No melee, magic, or ranged XP farming from the dynamic DM King.
- Only the owning challenger can attack or cast on the dynamic DM King; ranged is blocked because it is not part of the standard kit.

## DM King Kit Safety

When a challenge starts, `VoidArenaKitSnapshot` stores the player's current inventory, equipment, and worn sprites in player cache before applying the temporary kit. The snapshot is persisted immediately so login recovery can restore it if the session is interrupted.

Temporary player kit:

- Inventory-wielded gear: rune 2h sword, rune large helm, rune plate body, rune plate legs, diamond amulet of power. This temporary arena kit bypasses normal gear unlock requirements such as Dragon Slayer plate-body access, then restores the player's real kit snapshot afterward.
- Inventory supplies: one full strength potion, 400 air runes, 100 death runes, 500 fire runes, then cooked swordfish in every remaining slot. With the current five gear items and three rune stacks, this starts the player with 21 cooked swordfish.

The snapshot is restored on win, loss, timeout, logout, server-side cleanup, and login recovery. Recovery returns the player to the Void Arena lobby and clears the temporary snapshot cache after a successful restore.

## DM King AI Rules

The dynamic DM King uses normal NPC combat stats plus scoped player-equivalent bonuses:

- 99 Attack, Strength, Defense, and Hits; combat level 123.
- Rune 2h-style attack/power and full-rune-style armor point overrides.
- One virtual strength potion during the countdown just before the fight starts, shown with an NPC overhead item bubble.
- 26 virtual swordfish, eaten one at a time with a player-like delay.
- 100 virtual Fire Blast casts using legal spell range/path checks, including while melee combat is already engaged. Cast cadence follows the server's `milliseconds_between_casts` setting rounded up to whole game ticks.
- 99 virtual prayer points draining Steel Skin, Ultimate Strength, and Incredible Reflexes bonuses; when depleted, bonuses stop.

There are no hidden boss heals, forced damage, infinite supplies, ranged attacks, special mechanics, or post-fight drops.

## Rewards

The first victory unlocks the reusable manual `DM Kingslayer` player title and sends a one-time global broadcast for that player. Repeat victories send personal completion feedback only.

Each completed win, loss, or timeout also reports the leftover swordfish counts for the player and DM King against their actual starting capacities plus DM King's effective Fire Blast cadence in milliseconds and game ticks.

The DM King lobby right-click `Challenge` row also shows his global W/L record. It defaults to `300-0` when no persisted cache exists, stores counters in global cache keys `void_arena_dmking_wins` and `void_arena_dmking_losses`, increments wins when DM King defeats/times out/logouts a challenger, and increments losses when a player beats him. Talking to DM King uses a 256-combination taunt pool and avoids immediately repeating the same line to the same player.
