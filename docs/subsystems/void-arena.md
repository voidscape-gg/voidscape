# Void Arena

Void Arena is Voidscape's controlled death-match space. Players enter the lobby, challenge other eligible players, and fight inside allocated cages. Ranked PvP writes to Void Arena rating stats; custom solo challenges such as Sir Charles reuse the cage/session safety machinery without touching ranked tables.

## Core files

- `server/src/com/openrsc/server/content/voidarena/VoidArena.java` owns lobby entry/exit, player challenge state, cage allocation, countdowns, match resolution, boundary enforcement, teleport blocking, rating metadata, and Sir Charles sessions.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaConfig.java` defines lobby/exit tiles and cage bounds.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaKitSnapshot.java` serializes a player's real inventory, equipment, and worn sprites while temporary Sir Charles supplies are active.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidArenaHerald.java` handles leaderboard/help NPC actions.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/DmKing.java` handles Sir Charles talk/challenge/attack/range/spell/kill ownership checks. The class and cache keys keep the original DM King names for compatibility.

## Ranked Death Matches

Ranked challenges require the current ranked stat gate: 99 Attack, 99 Strength, 99 Defense, and 99 Hits. Ranked results write through the Void Arena stats path and can affect visible leaderboard state after placement rules. Unranked setup rules stay outside ranked Elo.

Match cages are allocated from the shared slot list. Active matches block teleporting, force players back inside their assigned cage if they leave its bounds, and clean up on death/logout/timeout. Each cage includes a central prayer altar as part of the deathmatch layout.

## Sir Charles

Sir Charles is a level-123 challenge hook in the Void Arena lobby around `(604, 2915)`. The lobby NPC uses id `862` and roams within a small Void Arena lobby box; each active fight spawns a dynamic attackable Sir Charles with id `863` inside the allocated cage. Both client definitions opt into player-composite NPC rendering, so their twelve sprite slots are player appearance IDs for the standard full-rune/rune-2h/power-amulet kit rather than fixed NPC animation layers.

Starting the challenge requires the player to be in the Void Arena lobby, out of combat, not already in a match/setup/challenge, using a custom client at version `10112` or newer, and passing the same ranked stat gate as ranked Death Matches. On beta worlds with `want_beta_onboarding_guide` enabled, accepting the challenge first sets the player's stats to 99 so testers can immediately try the fight. If no cage is available, the challenge is denied.

The challenge is deliberately separate from ranked PvP:

- No Elo, placement, or leaderboard writes.
- No item loss, bones, drops, hardcore status change, or economic reward.
- No melee, magic, or ranged XP farming from the dynamic Sir Charles.
- Only the owning challenger can attack or cast on the dynamic Sir Charles; ranged is blocked because it is not part of the standard kit.

## Sir Charles Kit Safety

When a challenge starts, `VoidArenaKitSnapshot` stores the player's current inventory, equipment, and worn sprites in player cache before applying the temporary kit. The snapshot is persisted immediately so login recovery can restore it if the session is interrupted.

Temporary player kit:

- Inventory-wielded gear: rune 2h sword, rune large helm, rune plate body, rune plate legs, diamond amulet of power. This temporary arena kit bypasses normal gear unlock requirements such as Dragon Slayer plate-body access, then restores the player's real kit snapshot afterward.
- Inventory supplies: one full strength potion, 400 air runes, 100 death runes, 500 fire runes, then cooked swordfish in every remaining slot. With the current five gear items and three rune stacks, this starts the player with 21 cooked swordfish.

The snapshot is restored on win, loss, timeout, logout, server-side cleanup, and login recovery. Recovery returns the player to the Void Arena lobby and clears the temporary snapshot cache after a successful restore.

## Sir Charles AI Rules

The dynamic Sir Charles uses normal NPC combat stats plus scoped player-equivalent bonuses:

- 99 Attack, Strength, Defense, and Hits; combat level 123.
- Rune 2h-style attack/power and full-rune-style armor point overrides.
- Virtual strength potion boosts during the countdown and whenever his boosted Strength decays, shown with an NPC overhead item bubble.
- The same virtual swordfish count as the challenger, eaten only after melee combat is legally broken. The heal amount comes from the normal edible-item table (currently 14 hits for swordfish), and once Sir Charles opens a legal food window he eats one fish per tick until topped up or out of fish. Eat messages include the remaining Sir Charles fish count.
- Unlimited virtual Fire Blast casts using legal spell range/path checks, including while melee combat is already engaged. Cast cadence follows the server's `milliseconds_between_casts` setting rounded up to whole game ticks. Sir Charles uses spell range to kite when casts are ready, his HP is low enough to seek a legal food window, or he is out of food and needs to deny melee pressure; he keeps checking legal Fire Blast casts while moving around the cage altar, then re-engages with A*-assisted melee pressure when pathing fails or the challenger is in finishing range.
- PvP reattack timing is mirrored even though Sir Charles is an NPC: after he retreats to eat or kite, both Sir Charles and the challenger get the same `pvp_reattack_timer` window real PvP uses, and melee/Fire Blast re-engagement is blocked until that timer expires.
- Unlimited scoped prayer with exactly Steel Skin, Ultimate Strength, and Incredible Reflexes toggled on; the combat formula applies only those three high-prayer bonuses.
- Rate-limited British fight quips use normal NPC overhead chat and react to player food, player hitpoints, probable missed Fire Blast casts, Sir Charles' own low-food state, and occasional random duel banter.

There are no hidden boss heals, forced damage, ranged attacks, special mechanics, or post-fight drops. Sir Charles' infinite resources are limited to magic casts, prayer, and re-potting so the challenge hook stays threatening after the mirrored food count is gone.

## Rewards

The first victory unlocks the reusable manual `DM Kingslayer` player title and sends a one-time global broadcast for that player. Repeat victories send personal completion feedback only.

Each completed win, loss, or timeout also reports the leftover swordfish counts for the player and Sir Charles against their actual starting capacities plus Sir Charles' effective Fire Blast cadence in milliseconds and game ticks.

The Sir Charles lobby right-click `Challenge` row also shows his global W/L record. It defaults to `300-0` when no persisted cache exists, stores counters in global cache keys `void_arena_dmking_wins` and `void_arena_dmking_losses`, increments wins when Sir Charles defeats/times out/logouts a challenger, and increments losses when a player beats him. Talking to Sir Charles uses a 51-line short British taunt pool and avoids immediately repeating the same line to the same player.
