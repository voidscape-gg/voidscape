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

### PK Catching Simulator parity

The simulator target is a synthetic NPC for rendering and zero-damage lock cadence, but manual melee attack requests use the PvP catching contract. `AttackHandler` applies `PVP_CATCHING_DISTANCE` and the moving-target `MAX_PVP_MELEE_ATTACK_DISTANCE` gate; `WalkToMobAction.isWithinInteractionReach` checks the player's next legal queued movement and `PathValidation.checkAdjacentDistance(..., true, false)`; the simulator action remains `isPvPAttack()` so the configured PIDless post-movement pass can execute it once. A legal catch still stacks the catcher onto the target tile when simulator combat starts, matching RSC's short next-step stack. No simulator radius expansion or collision bypass is permitted, and only the plugin's executed `onAttackNpc` callback may increment the score.

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
- NPCs attacking players use an effective Attack/Strength floor of 15, then gain a small offence bonus from level 40 upward: `+1` per 8 levels, capped at `+12`.

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
- For NPCs attacking players, the same floor/scaling described under melee max hit is applied to the NPC Attack level before the accuracy roll.
- In player-versus-NPC physical combat, the final NPC attack roll is multiplied by `1.10` when the NPC attacks the player, and the final NPC defence roll is multiplied by `1.10` when the player attacks the NPC. These are roll multipliers rather than percentage-point hit-chance changes; PvP, NPC-vs-NPC, damage, and Magic are unchanged.

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
- **PvP melee momentum**: player-vs-player melee hits at or above 68% of the target-adjusted max hit grant one momentum stack against that same target. The next successful melee hit rolls damage twice and keeps the higher roll, then consumes the stack; a miss or target swap clears it.
- **Poison**: 10 levels; each tick reduces power by 2, deals `floor(power/10)` damage.
- **Ring of Recoil**: reflects 10% of damage taken; breaks at 40 reflected total.

### Local OpenRSC baseline trial

`openrsc_classic_combat_baseline: true` is an opt-in server-only trial switch. It restores the inherited OpenRSC classic formula by using full armour points for physical avoidance, removing physical armour damage mitigation and PvP melee momentum, restoring unscaled player-target magic damage, and bypassing Voidscape's NPC effective-level boosts and `1.10` PvE physical roll multipliers. It deliberately preserves Void gear target bonuses, prayers, skill capes, Protect from Magic and its boss exceptions, custom NPC damage floors, combat scripts, XP policy, and all non-formula content.

The Java default is `false`, and the launch config does not enable it. The ignored `server/local.conf` may enable it for playtesting while `osrs_combat_melee` remains `false`. No client or packet change is required. No-Magic stake duels remain ordinary duels under this mode: the committed replay proof is unavailable because its frozen formula contract describes the normal Voidscape classic rules, not this trial baseline.

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

Utility:
- Rapid Restore
- Rapid Heal
- Protect Items
- Paralyze Monster
- Protect from Missiles
- Protect from Magic: blocks most NPC/boss-origin magic damage against players only. Player-cast PvP spells still use normal magic damage scaling, Void Knight boss Fire Blast is blockable, and Sir Charles' Void Arena magic is deliberately unblockable.

Drain rate: `server/src/com/openrsc/server/event/rsc/impl/PrayerDrainEvent.java`.

## Weapon styles

`server/src/com/openrsc/server/net/rsc/handlers/CombatStyleHandler.java`.

Constants in `server/src/com/openrsc/server/constants/Skills.java`: `CONTROLLED_MODE`, `AGGRESSIVE_MODE`, `ACCURATE_MODE`, `DEFENSIVE_MODE`.

Applied via `styleBonus()` in `CombatFormula`: Accurate adds 3 Attack, Aggressive adds 3 Strength, Defensive adds 3 Defence, and Controlled adds 1 to all three.

### Private duel combat journal

- The shared `DuelJournalSession` begins only when both stake confirmations have completed and the players actually enter duel combat. It snapshots both accepted stake offers at that point; cancellation, failed approach, or any other `Duel.resetAll()` before combat completion discards the in-memory session without creating a receipt.
- On each duel melee round, `CombatEvent` snapshots the attacker's current combat mode immediately before calling the selected melee formula. `MeleeHitResult` carries the hit/miss decision and final damage from that same formula call, so recording adds no RNG roll, distinguishes a landed zero-damage hit from a miss, and occurs before damage application so a lethal swing is included.
- Ordinary death settlement completes the shared session exactly once after stake payout handling. Eligible proof settlement instead freezes the receipt before lethal damage and atomically persists it with the verified witness after payout. `::duel` opens the latest receipt and up to ten history rows; `::duel <receipt-id>` selects another receipt from that private history.
- The journal displays both accepted stake offers but returns only the requesting participant's numbered melee swings, exact mode, hit/miss result, and damage. Participant authorization is enforced in the database query, and the opponent's journal hits and modes are not returned. A verified technical witness is transiently processed by the local client and necessarily contains both participants' replay inputs, but the UI model retains only proof state/id and still has no opponent-swing model.

### Melee duel-proof lock and committed combat stream

- Ordinary stake-duel combat awards no experience. `Player.shouldBlockExperienceGain` checks the actual `Duel.duelCombatStarted` boundary, and `Skills.addExperience` repeats the policy as a final backstop for direct or party-shared awards. This covers melee/death, Hits, ranged-hit, Magic-cast, skilling, and indirect XP attempted during the fight without changing the player's persistent XP-freeze setting. Normal XP resumes after duel reset; Void Arena matches are separate and unaffected.
- Every player can persistently disable all XP with `::freezexp on`, restore it with `::freezexp off`, and inspect it with `::freezexp status`. The owner-only persistent `::dueling on|off|status` switch controls ordinary duels: off cancels request/offer/confirmation/proof setup states, blocks every late entry boundary, and deliberately lets already-started combat finish.

- Classic-formula stake duels with the locked No Magic rule now pause after final confirmation for a server + two-client commit/reveal/lock handshake. Magic-allowed duels, `osrs_combat_melee` worlds, and `openrsc_classic_combat_baseline` trial worlds are ineligible; they must never receive a partial green verification claim.
- Canonical context v3 orders the players by database id and binds the proof id, protocol/RNG/formula/context versions, matching final rules from both players, identity, each displayed/base combat level, starting HP/recoil and combat state, equipment, and both accepted stake offers. The server's context-bound commitment is durable before either client is challenged. Exact context chunks are then delivered before a client contributes entropy; an honest client hashes and parses them and attests the locally visible subset of its own state, both visible combat levels, the displayed opponent identity, and both displayed stake offers. Both client commitments are durable before either client reveals; both reveals and the final lock are durable before the clients ACK; combat starts only after the ACKs are durably `LOCKED` and the database accepts the guarded `LOCKED -> COMBAT` transition.
- The locked context and stake inventory are checked again before approach and on both sides of the final combat-start database write. Players remain busy and the entire starting context is held stable through entry; the ordinary approach now overlaps the handshake. Once adjacent, the existing `LOCKED` and `COMBAT` updates run sequentially inside one atomic transaction after both exact ACKs, removing one avoidable 640ms callback tick without removing a durability stage. If the pair is not adjacent, the guarded post-lock approach and separate combat transition remain the fallback. During proof combat, committed Attack/Strength/Defence stats and equipped gear are frozen, while combat styles and permitted prayer toggles/tiers remain deliberately dynamic and are recorded per swing. Prayer drain and HP damage continue normally. Carry-over PvP melee momentum is cleared before context creation. A separate post-lock watchdog cancels a duel that never reaches combat. Timeout, disconnect, malformed state, entropy failure, database failure, changed items/context, or unreachable players reset both duel states before combat and transfer no stake.
- `Duel.duelCombatStarted` is set only after the shared `CombatEvent` is successfully registered. Death handling keys stake settlement from that flag rather than the older offer-window `duelActive` flag, so a pre-combat death or partial combat entry cannot award the accepted stake to the other player.
- Every stake duel gives first swing to the participant with the lower displayed/base combat level. Exact combat-level ties use a 50/50 bit mapped canonically to the lower/higher database id, so confirmation order cannot bias the result. Eligible proof duels always consume and record that bit as draw one from the locked private stream to preserve later RNG boundaries, but ignore it for unequal levels; context v3 lets the independent verifier enforce the same rule. Ineligible stake duels draw an ordinary coin only on a tie. Temporary boosts and drains do not affect initiative because the displayed level is calculated from maximum/base stats.
- After the durable `LOCKED -> COMBAT` gate, one `DuelProofMeleeReplay` owns the eligible duel's stream. It enforces alternating canonical participants and resolves every classic-melee random choice without falling back to global RNG: accuracy, any unbounded Attack-cape activation/reroll loop, damage, the optional momentum damage roll, Defence-cape activation, and Strength-cape activation. The stream uses semantic draw records and rejection-sampled boundaries so the same ordered bytes can be replayed later.
- Immediately before each covered swing, the server records both active combat modes and the relevant prayer tiers, then asserts the committed Attack/Strength/Defence values, equipment combat points, and cape eligibility have not drifted. Styles and permitted prayers may change between swings; equipment and formula-relevant combat stats may not. Momentum is kept per participant inside the proof replay. Ordinary journal privacy remains unchanged and never models or renders the opponent's swing/style rows.
- Poison ticks, generic external damage, desert heat, active-cannon mutation, and stat restoration are paused or blocked for the whole active proof duel so a non-melee event cannot bypass the covered terminal boundary or consume a committed stake. Fresh Ring-of-Recoil state treats missing usage as zero, clamps reflection to remaining capacity, and shatters only the exact worn ring at exhaustion. A weapon-allowed proof duel is cancelled before commitment if its accepted ring stake would require that same physical worn ring; spare unworn rings remain stakeable, and No Weapons may safely unequip it first. The terminal verifier starts from both committed HP values and exact recoil state, applies every direct hit and eligible capacity-bounded recoil in order, suppresses recoil after a direct kill, and requires exactly one final death with winner HP above zero and loser HP zero. Before lethal direct or recoil damage is applied, `CombatEvent` freezes and independently verifies canonical witness v2. Stake settlement is refused unless the frozen participants, winner/loser pair, exact accepted stake rows, swing rows, and completion time all match the receipt.
- Witness v2 contains context v3 and all three seed openings plus the canonical per-swing input tape, committed starter, exact final draw count, terminal cause, winner, and completion time. The verifier re-derives the lower-level/tied-coin starter and every covered result, and rejects a missing, extra, reordered, or tampered swing; an early/late/non-damaging terminal; or an HP/recoil/receipt/stake mismatch. After payout, receipt+witness+`COMBAT -> VERIFIED` commit atomically off-thread. Any exception or persistence failure is fail-closed and cannot produce the green state.
- Client cohort `10136` performs the same independent replay over the participant-authorized v2 witness and context v3, binds it to the private receipt, discards the raw transfer, and shows green `COMBAT REPLAY VERIFIED` only on success. During the retained login session it also keeps a bounded set of seed-free pre-combat lock anchors outside mutable handshake state: bare `::duel` must select the newest history row and match the newest retained proof id, commitments, context, and lock exactly, so substituting a freshly fabricated proof id fails red. Login/logout/reconnect resets those local anchors; older unanchored receipts therefore fall back to the self-contained witness and the external-log limitation below. A legal retreat has no terminal receipt, so its explicit `retreat` abort retires only that matching terminal-less anchor and falls back to the prior one; failure aborts retain their omission alarm. Magic-allowed and OSRS-formula receipts remain gray/unavailable; malformed or inconsistent evidence is red/failed. The normal modal retains only the requester's swings/styles, but the technical witness necessarily discloses both players' replay inputs and seeds to that participant's local verifier, and an instrumented client can inspect them. The proof establishes deterministic RNG and HP/recoil replay conditional on committed static context and the server-recorded dynamic style/prayer tape; it cannot cryptographically prove each dynamic toggle was the exact live user action. There is no website, public append-only log, or backfill, so it also cannot prove the absence of selective abort/omission/deletion or make a malicious client report honestly.

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
- Level-0 Home Teleport is denied anywhere in the Wilderness, throughout an active duel, or while the caster's active opponent is another player. NPC combat remains eligible, and there is no post-combat cooldown beyond those exact checks.
- In a stake duel with retreat allowed, a legal retreat ends the duel with no winner and no stake transfer; each offered item remains with its original owner and no Duel Journal receipt is created. An active melee proof is intentionally stored as `ABORTED / RETREAT`, not as a verification failure. With No Retreat selected, the walk is rejected and the proof duel continues. Retreat is cancellation, not forfeiture; changing it into an automatic stake loss would be a separate duel-rule change.
- Melee attacks check the target player's reattack timer in `AttackHandler`; PvP spell casts check both the caster and target timers in `SpellHandler`.
- Inventory actions, including food and potions, are blocked while `player.inCombat()` by the normal item handlers. Food/potions become legal only after combat is broken.
- Follow and item-on-player actions also respect the target player's reattack timer.
- Void Dungeon underground PvP is 1v1-only. If either player is inside the generated underground Void Dungeon footprint, third-party melee, ranged, throwing, and magic attacks are denied while either player has an active or recent player opponent. Legal melee rounds refresh the pair through the final hit; projectile attacks mark only after weapon and ammo validation; offensive self-casts are rejected; and denied magic is checked before spell-failure rolls. NPC-vs-player and player-vs-NPC combat are deliberately unchanged.
- Generic instance isolation requires matching instance ids for entity lookup, attack eligibility, combat start and continuation, delayed projectile effects, offensive spell targeting/finalization, trade, and item-on-player. The retired Void Wyrm no longer creates a private phase, but these shared guards remain required by other instanced minigames.

### Void gear PvE policy

- Void Scimitar and Void Shortbow multiply both offensive accuracy and damage by `1.15` only when the defender is a Void NPC. The Shortbow also waives ammunition only for those targets.
- Void Sceptre (item `1596`, whose internal constant retains the historical `VOID_MACE` name) requires 60 Magic. Against Void NPCs it multiplies the ordinary spell-success roll weight and spell power by `1.15`; player targets and non-Void NPCs use the unchanged formula. It does not autocast, batch spells, save runes, or affect alchemy.
- Void Amulet uses Diamond Amulet of Power base stats and multiplies eligible stackable Void-NPC drops by `1.5`; it has no special PvP combat effect.
- Void gear uses a death-only top sort value. This does not change alchemy value, noted/stackable loss, skull behavior, the unskulled keep-three rule, or Protect Item's one extra slot.

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
- NPC movement blocking uses global `npc_blocking` unless a Wilderness tile is being checked, where `wilderness_npc_blocking` can override it. Voidscape launch configs set the Wilderness override to `0`, so aggressive NPCs still aggro and fight there but do not body-block player movement.
- NPC auto-aggro also respects explicit retreat immunity: when a retreat stamps a player's `ranAwayTimer`, aggressive NPCs wait for `Player.canBeReattacked()` before auto-aggro can pick that player again. Ordinary combat completion still uses the normal combat-timer path so AFK training can keep flowing. Scripted force-chase targets bypass this gate.
- Void Dungeon NPC kills arm a transient traversal opportunity. Because NPC AI runs before player packet queues, ordinary auto-aggro waits through the next NPC scan; a stationary player becomes eligible again on the following scan, preserving AFK training. If the player submits an accepted ground/minimap `WALK_TO_POINT` during the five-tick opportunity, ordinary auto-aggro is suppressed for five ticks. Manual melee, ranged, throwing, or offensive magic clears the state, as do new combat and leaving the generated underground footprint; force-chase NPCs bypass it. The state is separate from `ranAwayTimer`, so PvP and retreat rules are unchanged.

**F2P Wilderness member loot**:
- Ground-loot availability and item-use eligibility are separate policies in `WildernessRules`.
- On a members-enabled world, members-only NPC drops, manual drops, and player death-pile items can exist, become public under the normal ownership timer, and be picked up in F2P Wilderness. This preserves the configured drop-table roll instead of silently replacing or discarding it.
- Equipping, consuming, identifying, using, trading through restricted actions, using members ammunition, and casting members spells remain blocked in F2P Wilderness by the existing `canUseItem`, `canUseItemAt`, and `canUseSpell` paths. Carrying the loot is allowed so it can be taken out of the Wilderness.
- On a true non-members world (`member_world: false`), members-only ground loot remains suppressed. Non-members items and existing Voidscape F2P-Wilderness use exceptions are unchanged.

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
