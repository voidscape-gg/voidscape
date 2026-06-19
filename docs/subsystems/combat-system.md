# Combat System

Damage formulas, accuracy, prayer effects, weapon styles, special attacks, and NPC AI. Plugin/scripting architecture is in `scripting-plugins.md`; the tick loop is in `world-tick-loop.md`.

## Combat tick and state

Combat is driven by `CombatEvent` — `server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java`. Extends `GameTickEvent`. Implements turn-based alternation between attacker and defender each tick cycle.

Tracking:
- `setOpponent(Mob)` and `setCombatEvent(CombatEvent)` set "in combat".
- `CombatState` enum — `server/src/com/openrsc/server/model/states/CombatState.java`: `ERROR`, `LOST`, `RUNNING`, `WAITING`, `WON`.
- Combat continues while both mobs are adjacent, logged in, not respawning, not removed.

Tick cycle:
- **PvE** default: 3-tick attacker round, 1-tick defender round.
- **PvP standard**: 3-1 vs 2-2 depending on PID order (lower PID gets the 2-2).
- **PvP duel**: always 2-2.
- **NPC attacking player**: forced 2-2.

`CombatEvent.run()` executes per tick, alternating `roundNumber % 2` (attacker vs defender) and applying delays via `setDelayTicks()`.

## Damage formula

`server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java`.

**Melee max hit**:
```
maxRoll = floor(StrengthLevel * prayerBonus) + bonusConstant + styleBonus
maxRoll *= (weaponPowerPoints + 64)
damageNumerator = nextInt(maxRoll) + 320
damage = damageNumerator / 640
```
- `bonusConstant`: 8 (player), 0 (NPC).
- `styleBonus`: +3 for matching aggressive/accurate/defensive, +1 for controlled.
- Strength prayers: 1.05 (Burst of Strength), 1.10 (Superhuman), 1.15 (Ultimate Strength).

**Ranged max hit**:
```
maxRoll = (RangedLevel + 8) * (arrowPower + 1 + 64)
```
- Arrow powers: bronze darts 15, rune darts 50, dragon arrows 50, spears 29–69.
- Bow aim bonus: shortbow 10, longbow 15, magic longbow 40.

**Magic damage**: uniform roll from 0 to floor(spellPower); damage does not roll against target defence. God spells up to 18, charged +7.
- Spell success itself is checked earlier by `Formulae.castSpell()` using caster magic level, spell requirement, and magic equipment.
- Player targets take 92% of rolled magic damage, floored with successful non-zero hits preserved at 1.

**Accuracy / hit chance**:
```
if (accuracy > defence):
    hitChance = 1 - ((defence + 2) / (2 * (accuracy + 1)))
else:
    hitChance = accuracy / (2 * (defence + 1))
```
- Melee accuracy: `(AttackLevel + bonusConstant + styleBonus) * (weaponAim + 64)`
- Defence: `(DefenseLevel * prayerBonus + bonusConstant + styleBonus) * (64 + armourPoints * 0.60)`

**Physical armour mitigation**:
```
reduction = min(0.24, armourPoints / 1200)
damage = floor(damage * (1 - reduction))
```
- Applies to melee and ranged damage after the hit roll.
- Successful non-zero hits are preserved at a minimum of 1 damage.
- Intent: armour should reduce burst and still help avoidance, without making armoured combat mostly misses.

**Special mechanics**:
- **Defence cape**: blocks 50% of melee damage to player.
- **Attack cape**: prevents zero hits (re-rolls misses).
- **Strength cape**: +20% damage on hits ≥ 50% of max hit.
- **PvP melee momentum**: player-vs-player melee hits at or above 75% of the target-adjusted max hit grant one momentum stack against that same target. The next successful melee hit rolls damage twice and keeps the higher roll, then consumes the stack; a miss or target swap clears it.
- **Poison**: 10 levels; each tick reduces power by 2, deals `floor(power/10)` damage.
- **Ring of Recoil**: reflects 10% of damage taken; breaks at 40 reflected total.

### Local measurement harness

`scripts/combat-sim.sh` runs `tools/combat-sim/combat_sim.py`, a standalone simulator that mirrors the active classic formula path without changing server behavior. Use it before and after combat changes to capture baseline hit chance, max hit, average damage, simple melee fight timing, one-way ranged pressure, and one-way magic pressure:

```bash
scripts/combat-sim.sh --list
scripts/combat-sim.sh --scenario pvm-rune-lesser
scripts/combat-sim.sh --scenario all --trials 50000
scripts/combat-sim.sh --rules openrsc --scenario all --trials 50000
```

It is not a full game engine: it does not model eating, plugins, NPC combat scripts, pathing, dragonfire, player decisions, or live server state. Treat it as a formula/cadence harness, then verify meaningful changes in-game.

## Prayer effects

`server/src/com/openrsc/server/model/entity/player/Prayers.java`.

Offensive (Attack):
- Clarity of Thought (×1.05)
- Improved Reflexes (×1.10)
- Incredible Reflexes (×1.15)

Offensive (Strength):
- Burst of Strength (×1.05)
- Superhuman Strength (×1.10)
- Ultimate Strength (×1.15)

Defensive (Defence):
- Thick Skin (×1.05)
- Rock Skin (×1.10)
- Steel Skin (×1.15)

Utility: Rapid Restore, Rapid Heal, Protect Items, Paralyze Monster, Protect from Missiles.

Drain rate: `server/src/com/openrsc/server/event/rsc/impl/PrayerDrainEvent.java`.

## Weapon styles

`server/src/com/openrsc/server/net/rsc/handlers/CombatStyleHandler.java`.

Constants in `server/src/com/openrsc/server/constants/Skills.java`: `CONTROLLED_MODE`, `AGGRESSIVE_MODE`, `ACCURATE_MODE`, `DEFENSIVE_MODE`.

Applied via `styleBonus()` in `CombatFormula` — modifies Attack/Strength/Defense by +3 (or Attack +3 for controlled).

## Special attacks (combat scripts)

Location: `server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/`.

Interface: `CombatScript` with `executeScript(attacker, victim)` and `shouldExecute()`.

Loaded by `CombatScriptLoader` via reflection from package `com.openrsc.server.event.rsc.impl.combat.scripts.all`.

Examples:
- `Salarin.java` — drains player Attack/Strength by 50% on aggro.
- `SilverlightEffect.java` — reduces demon stats by 15% when attacked with Silverlight.
- `ElvargPrayerDrain.java` — drains prayer points during combat.
- `DragonFireBreath.java` — AOE magic damage.
- `NpcPoisonPlayerScript.java` — applies poison on hit.

## PvP vs PvE

`CombatEvent.isPvPCombat` flag set when both combatants are players.

Branches:
- PvP tick cycling: PID-based 3-1 vs 2-2 (`CombatEvent.java:43–73`).
- NPC vs player: forced 2-tick (`:41`).
- Duel flag (`:45`) overrides to 2-2.

PvP-specific:
- Immunity check (`canBeReattacked()` in `AttackHandler`) prevents re-engagement for X ticks in wilderness.
- Wilderness PK check: `pl.getLocation().inWilderness() || player.getConfig().USES_PK_MODE` (`:60`).
- PvE only: combat scripts, aggression ranges, respawning.

### PvP action constraints

The core PvP restrictions to mirror for player-like custom fights are:

- A player can only retreat from active combat after the opponent has made at least three hits; otherwise `WalkRequest` rejects the walk with "You can't retreat during the first 3 rounds of combat".
- A legal PvP retreat timestamps both combatants with `setRanAwayTimer()` and resets their combat events. `pvp_reattack_timer` defaults to 5 game ticks, and `Player.canBeReattacked()` allows re-engagement only once `ranAwayTimer + pvp_reattack_timer <= currentTick`.
- Melee attacks check the target player's reattack timer in `AttackHandler`; PvP spell casts check both the caster and target timers in `SpellHandler`.
- Inventory actions, including food and potions, are blocked while `player.inCombat()` by the normal item handlers. Food/potions become legal only after combat is broken.
- Follow and item-on-player actions also respect the target player's reattack timer.

DM King is technically an NPC, so the Void Arena challenge explicitly mirrors these PvP gates around his custom AI: when DM King retreats, both sides are timestamped, and neither DM King's melee/Fire Blast nor the challenger's attack/cast actions can re-engage until the same PvP timer expires.

XP distribution:
- PvP: `combatExperience()` formula.
- PvE melee, authentic mode: `Npc.handleXpDistribution()` awards XP after NPC death based on damage tracking.
- PvE melee, Voidscape/on-hit mode: `melee_gives_xp_hit` awards successful melee-hit XP immediately through `Npc.awardMeleeHitExperience()`, while death distribution still uses damage tracking for loot ownership. Per-NPC XP-paid damage is capped to the NPC's base hit total, so healing/regeneration cannot create more than one NPC-life of melee XP.
- PvE ranged: `ranged_gives_xp_hit` can award ranged XP immediately, with death distribution subtracting already-paid hit XP.
- Magic: damage spells award configured spell XP on successful cast/finalization rather than kill XP.

## Multi-combat zones, retreating, freezing

**Wilderness / multi-combat**:
- Defined in `server/src/com/openrsc/server/model/Point.java` static block.
- `WildernessLocation` objects with bounds and `WildState` (`MEMBERS_WILD`, `F2P_WILD`).
- Helpers: `inWilderness()`, `inFreeWild()`, `wildernessLevel()`.
- Multi-combat check: combine `getLocation().inWilderness()` with aggression flags.

**Retreating**:
- NPC behavior states `State.RETREAT` and `State.TACKLE_RETREAT` in `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java`.
- Triggered by `shouldRetreat()` (per-NPC override).
- Retreat fires on NPC's own turn in `CombatEvent` (`:176–179`) before damage.
- Combat timer reset at `resetRanAwayTimer()`.

**Freezing / paralyze**:
- Prayer paralyze: `Prayers.PARALYZE_MONSTER` blocks NPC damage (`CombatEvent.java:237–238`).
- Ice spells: handled via combat scripts or spell handler (not in core formula).
- Movement: `WalkToAction` blocked by `PathValidation` if mob is busy/in combat.

## NPC combat AI

`NpcBehavior.java`:

State machine:
- `ROAM` — idle, scanning for aggro targets
- `AGGRO` — chasing target
- `COMBAT` — engaged
- `RETREAT` — running away
- `TACKLE` — gnome ball minigame state

`handleRoam()` target picking:
1. Check aggression flag and location (wilderness, player aggro radius, etc.).
2. Iterate `getViewArea().getPlayersInView()`.
3. Filter by `withinRange(npc, aggroRadius)` (default 4–10 tiles, config-dependent).
4. `canAggro(player)` (level diff, immunity, etc.).
5. Last-opponent timeout (15-tick threshold before re-aggro).
6. Trigger `AggroEvent`, set `state = State.AGGRO`.

`handleCombat()`:
- Guarded by `npc.inCombat()`.
- Checks `shouldRetreat()`.
- Tracks damagers for PvE XP.

Special behaviors:
- `blackKnightsFortress` flag → always aggressive
- Draynor Manor skeleton — ignores aggression rules
- Tackling — gnome ball minigame special-case

Retaliation: implicit via `setOpponent()` in `AttackHandler`; combat event loop handles the rest.

## Pitfalls / non-obvious

1. **Tick-cycle math depends on PID and combat type.** PvP 3-1 vs 2-2 swap is determined by lower PID; if `SHUFFLE_PID_ORDER` is on, this can desync subtly mid-fight.
2. **Combat scripts load via reflection.** Order is undefined when multiple match `shouldExecute()`. Don't rely on script ordering for stacking effects.
3. **Damage formula uses `(power + 64)` × inflated rolls.** Small changes to `bonusConstant` (8 vs 0) have outsized effects via the multiplier.
4. **Style bonus is +3, not +1.** Easy to misread when reverse-engineering authentic formulas.
5. **Prayer drain rate is opaque.** Stored externally in `PrayerDef.xml`; no in-code formula doc — read both sides if tuning.
6. **NPC aggro radius is partly hardcoded.** `BLACK_KNIGHT` and `BANDIT_AGGRESSIVE` ranges baked into `NpcBehavior` constructor, not data-driven.
7. **Defence cape 50% block is unconditional.** Players wearing it dramatically reduce melee damage; balance accordingly when tuning content.
8. **Strength cape +20%** only fires on hits ≥50% of max hit — easy to forget when computing expected DPS.
9. **Use the simulator before manual checks.** `scripts/combat-sim.sh --rules openrsc` gives the inherited baseline; default `voidscape` should match live formula behavior.
10. **On-hit melee XP is a deliberate Voidscape divergence.** Keep PvP death-based unless specifically rebalanced; PvP on-hit XP is much easier to farm between cooperating players.

## Glossary candidates

- **CombatEvent** — per-fight `GameTickEvent` orchestrating attacker/defender alternation.
- **Combat script** — pluggable NPC-specific effect (poison, drain, AOE) loaded by reflection.
- **Style bonus** — Attack/Strength/Defense modifier from selected combat mode.
- **Prayer bonus** — multiplier applied to a stat when its prayer is active.
- **Attacker/Defender** — combat roles per round; swap every 2-3 ticks.
- **PID-based round assignment** — PvP uses login order (PID) to decide which player gets the 2-2 vs 3-1 cycle.
- **Aggro / aggression range** — NPC scan radius for choosing players to attack.
- **Last-opponent timeout** — 15 ticks before an NPC may re-aggro the same target.
