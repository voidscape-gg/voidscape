# Void Arena

Void Arena is Voidscape's controlled death-match space. Players enter the lobby, challenge other eligible players, and fight inside allocated cages. Ranked PvP writes to Void Arena rating stats; custom solo challenges such as Sir Charles reuse the cage/session safety machinery without touching ranked tables.

## Core files

- `server/src/com/openrsc/server/content/voidarena/VoidArena.java` owns lobby entry/exit, player challenge state, cage allocation, countdowns, match resolution, boundary enforcement, teleport blocking, rating metadata, and Sir Charles sessions.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaConfig.java` defines lobby/exit tiles and cage bounds.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaRankedPolicy.java` contains deterministic rating transfer, result classification, public-network comparison, cooldown, and UTC-day policy helpers.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaSirPolicy.java` contains Sir Charles' finite-resource, cast-cadence, retreat-timer, and one-main-action priority rules.
- `server/src/com/openrsc/server/content/voidarena/VoidArenaKitSnapshot.java` serializes a player's real inventory, equipment, worn sprites, current skill levels, exact Prayer points, and active prayers while temporary Sir Charles supplies are active.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/VoidArenaHerald.java` handles leaderboard/help NPC actions.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/DmKing.java` handles Sir Charles talk/challenge/attack/range/spell/kill ownership checks. The class and cache keys keep the original DM King names for compatibility.

## Ranked Death Matches

Ranked eligibility is exactly the permanent/max-level gate of 99 Attack, 99 Strength, and 99 Defense. Hits, Prayer, Magic, Ranged, combat level, and current boosted or drained levels are not part of this gate. Pre-potting and current boosts are legal. Ranked combat awards normal combat XP.

Ranked rules are fixed to F2P items with prayer, ranged, and magic enabled. Both players must stay in the lobby, use a current Voidscape client, be out of combat, and cure any poison before challenge/setup admission and again before final confirmation. A setup expires after 120 seconds; changing its rules clears both players' acceptance and confirmation. The ranked admission policy is re-evaluated at final start so a stale setup cannot bypass a changed stat, network, pair-history, or database state.

Match cages are allocated from the shared slot list. Before either player is teleported, the server clears unowned ground items from the cage and must durably create the ranked session's `ACTIVE` row. Active matches block teleporting and trading, enforce their assigned cage bounds, reject cross-cage combat and delayed projectiles, and use exactly-once match and safe-death guards. Each cage includes a central prayer altar; it remains governed by the normal object and combat-state rules.

## Ranked Seasons and Rating

Seasons are UTC calendar months identified as `YYYY-MM`. The season changes automatically at `00:00 UTC` on the first day of each month; old rows that used `global` migrate to the read-only `LEGACY` season. The previous month's eligible visible champion is finalized automatically after the rollover grace period. Manual season reset/finalize commands are disabled.

Players begin each season at 1200 rating. The calculation uses K=32 Elo and transfers one integer delta from the loser to the winner, making each decisive result strictly zero-sum. Rating never falls below 1; a loss at the floor can therefore settle with a zero-point transfer while still recording the win/loss and consuming the anti-farm allowance. Ratings remain hidden from the public leaderboard until five decisive matches are complete.

Terminal policy:

- Death settles a rated win and loss.
- A participant disconnect or logout is an immediate forfeit: the opponent wins, the leaver loses, and the leaver's disconnect-loss count increments.
- A ten-minute timeout is a durable draw. It changes no rating, wins, losses, placement count, cooldown, or daily allowance.
- A graceful server shutdown settles active matches as `SERVER_SHUTDOWN_NO_CONTEST`; startup reconciliation settles orphaned `ACTIVE` rows as `SERVER_RESTART_NO_CONTEST`. Neither path changes rating or aggregate records.

Ranked pair admission is fail-closed when its audit history cannot be read or a WebSocket route hides the player's public address without the explicit trusted-proxy override. Players sharing the same public IPv4 address or public IPv6 `/64` cannot play a rated match together; the IPv6 prefix rule prevents privacy-address rotation from bypassing the same-household control. Private, loopback, link-local, and documentation-only addresses are not treated as a shared public network. This can conservatively block unrelated players on one enterprise or guest `/64`, matching the existing shared-IPv4-NAT tradeoff. After any death or forfeit, that pair may record at most one rated decisive result in a rolling 30-minute window and at most three in one UTC day. These checks use durable pair history across season rollover. Draws and no-contests do not consume either allowance. The same players may select unranked play when ranked admission is denied.

## Ranked Persistence and Recovery

`voidarena_ranked_match_sessions` is the authoritative lifecycle ledger. Its immutable UUID row captures the canonical player pair, season, starting ratings, cage, admission audit counters, and timestamps. A terminal settlement changes `ACTIVE` to `SETTLED` with exactly one of `DEATH`, `FORFEIT`, `TIMEOUT_DRAW`, `SERVER_SHUTDOWN_NO_CONTEST`, or `SERVER_RESTART_NO_CONTEST`. Decisive rating/stat updates and the terminal session write commit in the same database transaction; duplicate or non-active settlement attempts cannot apply rating twice. Neutral settlements preserve both starting ratings and carry no winner or loser.

Terminal arbitration is single-claim: synchronized match state chooses one winner/loser/reason, and a disconnect captured before arbitration overrides a competing death or timeout as `FORFEIT`. A database error does not release either participant's active-match lock. The immutable pending settlement retries with bounded backoff; an already-terminal UUID is accepted only after the durable row is read back and matches every expected terminal field. A conflicting row remains fail-closed for operator investigation. JDBC transactions also hold the shared connection's reentrant lock for the full begin/commit-or-rollback span, making settlement a single-writer operation rather than allowing another save/logger transaction to interleave on the connection.

The older `voidarena_ranked_matches` ledger remains available only for pre-monthly `LEGACY` audit. Never repair a live session by editing either table manually; preserve the evidence and let startup reconciliation or the normal settlement path close it.

Administrator inspection commands are read-only:

- `::arena season` shows the current `YYYY-MM` season, profile/session counts, top ratings, prior champion, and recent sessions.
- `::arena audit recent <limit>` shows current-season sessions; limits are clamped to 1-25.
- `::arena audit <player> <limit>` shows that player's current rating/record and recent current-season sessions.
- `::arena audit legacy <limit>` shows the migrated pre-monthly decisive ledger.

## Sir Charles

Sir Charles is a level-123 challenge hook in the Void Arena lobby around `(604, 2915)`. The lobby NPC uses id `862` and roams within a small Void Arena lobby box; each active fight spawns a dynamic attackable Sir Charles with id `863` inside the allocated cage. Both client definitions opt into player-composite NPC rendering, so their twelve sprite slots are player appearance IDs for the standard full-rune/rune-2h/power-amulet kit rather than fixed NPC animation layers.

Starting the challenge requires the player to be in the Void Arena lobby, out of combat, unpoisoned, unskulled, not already in a match/setup/challenge, using a custom client at version `10112` or newer, and passing the same permanent/max 99 Attack/Strength/Defense gate as ranked Death Matches. The challenge does not mutate an ineligible player's stats. If no cage is available, the challenge is denied.

The challenge is deliberately separate from ranked PvP:

- No Elo, placement, or leaderboard writes.
- No item loss, bones, drops, hardcore status change, or economic reward.
- No melee, magic, or ranged XP farming from the dynamic Sir Charles.
- No title progress or title/reward grant.
- Only the owning challenger can attack or cast on the dynamic Sir Charles; ranged is blocked because it is not part of the standard kit.

## Sir Charles Kit Safety

When a challenge starts, version 2 of `VoidArenaKitSnapshot` captures the player's complete inventory, equipment, worn sprites, every current skill level, exact Prayer state points, and active-prayer bitmap before any temporary mutation. It strictly validates item identity, amounts, noted/wielded state, durability, kill logs, container/skill/prayer counts, booleans, bounds, encoding, and trailing data before it changes live state. The bounded version-1 item-only decoder remains available only to recover a crash marker written by the earlier format; new snapshots are version 2 and legacy snapshots cannot be re-encoded.

Before capture, the player must acquire the account save reservation. The recovery marker is then stored durably before the temporary kit is applied, and MySQL stores its value as `MEDIUMTEXT` (`TEXT` on SQLite) so a valid exact snapshot cannot be truncated by the old 150-character cache column. Capture or marker persistence failure aborts the challenge. The reservation remains held for the entire temporary-kit session, causing concurrent autosave/logout-save requests to defer, and is released only after rollback or terminal/death recovery has committed the exact restored containers, equipment, player data, skills, cache, and marker removal in one database transaction.

Temporary player kit:

- Inventory-wielded gear: rune 2h sword, rune large helm, rune plate body, rune plate legs, diamond amulet of power. This temporary arena kit bypasses normal gear unlock requirements such as Dragon Slayer plate-body access, then restores the player's real kit snapshot afterward.
- Inventory supplies: one full strength potion, 400 air runes, 100 death runes, 500 fire runes, then cooked swordfish in every remaining slot. With the current five gear items, one potion, and three rune stacks, this starts the player with 21 cooked swordfish.
- Attack, Strength, and Defense current levels are normalized to their permanent/max values (which admission requires to be at least 99) before the temporary strength potion can be used. Pre-existing boosts or drains therefore cannot leak into the Sir Charles session; all other captured current levels and Prayer state are restored exactly afterward.

The snapshot is restored on win, loss, timeout, logout/forfeit, graceful shutdown, server-side cleanup, and pre-world-packet login recovery. Death restoration is deliberately deferred until generic death normalization finishes, preventing that normalization from overwriting the recovered current levels. Restore validates the entire snapshot before mutation, returns the player to the Void Arena lobby (or the safe arena exit on disconnect), and clears the recovery marker only with the successful durable restore. Corrupt or unpersistable recovery fails closed by retaining the marker and forcing logout instead of leaving the player online with a temporary or partially restored kit.

## Sir Charles AI Rules

The dynamic Sir Charles uses normal NPC combat stats plus scoped player-equivalent bonuses:

- 99 Attack, Strength, Defense, and Hits; combat level 123.
- Rune 2h-style attack/power and full-rune-style armor point overrides.
- Four total strength-potion doses. He drinks only when the legal repot condition is met, and each dose is visibly represented by an NPC overhead item bubble.
- Exactly 21 virtual swordfish, eaten only after melee combat is legally broken. The heal amount comes from the normal edible-item table (currently 14 hits for swordfish), and once Sir Charles opens a legal food window he eats one fish per tick until topped up or out of fish. Eat messages include the remaining count.
- Exactly 100 virtual Fire Blast casts using normal spell range, line-of-sight, combat-magic configuration, and path checks. Cast cadence follows the server's `milliseconds_between_casts` setting rounded up to whole game ticks. When his casts are exhausted he stops magic kiting and returns to legal melee pressure.
- PvP reattack timing is mirrored even though Sir Charles is an NPC: after he retreats to eat or kite, both Sir Charles and the challenger get the same `pvp_reattack_timer` window real PvP uses, and melee/Fire Blast re-engagement is blocked until that timer expires.
- A finite 99-Prayer pool with exactly Steel Skin, Ultimate Strength, and Incredible Reflexes active. It drains from the normal prayer definitions each tick, and every prayer multiplier turns off at zero. Sir Charles never uses the cage altar; the challenger may use it whenever the normal object/combat rules permit.
- One main action per game tick, in deterministic priority order: retreat to eat, eat, repot, cast, kite, then melee pressure. Movement, casting, eating, and re-engagement still obey ordinary server timing and legality checks.
- Rate-limited British fight quips use normal NPC overhead chat and react to player food, player hitpoints, probable missed Fire Blast casts, Sir Charles' own low-food state, and occasional random duel banter.

There are no hidden boss heals, forced damage, ranged attacks, infinite supplies, special mechanics, altar use, or post-fight drops. His advantage is mechanically perfect legal decisions and reaction speed, not combat-formula or resource cheats.

## Rewards

Victories send personal completion feedback and update Sir Charles' global W/L record. The two global counters update atomically. Void Arena challenges and ranked seasons do not award player titles; monthly rollover still records the champion metadata used by the leaderboard and admin tooling.

Each completed win, loss, or timeout reports both swordfish counts against their starting capacities, Sir Charles' effective Fire Blast cadence and casts used, plus his remaining potion doses and Prayer.

The Sir Charles lobby right-click `Challenge` row also shows his global W/L record. It defaults to `300-0` when no persisted cache exists, stores counters in global cache keys `void_arena_dmking_wins` and `void_arena_dmking_losses`, increments wins when Sir Charles defeats a challenger or wins by timeout/logout, and increments losses when a player beats him. Talking to Sir Charles uses a 51-line short British taunt pool and avoids immediately repeating the same line to the same player.

## Focused QA

The full build runs the ranked policy/persistence and Sir policy/snapshot checks. They can also be run directly while iterating:

```bash
scripts/test-void-arena-ranked-policy-java.sh
python3 scripts/test-void-arena-ranked-persistence.py
scripts/test-void-arena-kit-snapshot-java.sh
scripts/test-void-arena-sir-policy-java.sh
```

Use separate voidbot control ports for two-player live QA. Relevant commands include `arena-challenge <server-index>`, `arena-rules --ranked`, `arena-accept`, `arena-confirm`, `arena-decline`, `state arena`, and waits `arena-setup`, `arena-confirm`, `arena-started`, and `arena-ended`. Sir Charles QA can observe `state combat-events` and `events --since <sequence>` for NPC damage, projectiles, and action bubbles; `prayer-on <id>` / `prayer-off <id>` exercises the player's legal prayer path. See `docs/bot-api.md` for multi-daemon setup and exact argument forms.
