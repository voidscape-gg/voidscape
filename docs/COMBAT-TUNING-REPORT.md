# Combat Tuning Report

Date: 2026-06-06

This pass replaces the inherited OpenRSC physical-combat feel with a Voidscape-tuned formula while keeping authentic RSC structure: the same level, style, prayer, weapon, arrow, spell, and cadence concepts still drive combat.

## Goals

- Keep brand-new registration and early training low-friction.
- Avoid nerfing normal PvM kill speed.
- Make armour feel protective without turning armoured fights into long miss streaks.
- Reduce top-end PvP burst while making successful pressure more consistent.
- Preserve RSC's memorable "three-hit" streak moments in PvP without making every big hit guaranteed to snowball.
- Keep magic useful, but trim player-target spell burst so charged god spells do not dominate the softened physical top-end.

## Changes

### 1. Armour avoidance now uses 60% of armour points

Old OpenRSC defence term:

```text
(defence * prayer + playerBonus + styleBonus) * (armourPoints + 64)
```

New Voidscape defence term:

```text
(defence * prayer + playerBonus + styleBonus) * (64 + armourPoints * 0.60)
```

Rationale: OpenRSC uses armour entirely as avoidance. That makes armoured PvP too miss-heavy, then too spiky when a hit finally lands. Using 60% keeps armour meaningful while letting fights produce more readable pressure.

### 2. Armour now mitigates physical hit damage

New physical mitigation:

```text
reduction = min(0.24, armourPoints / 1200)
damage = floor(damage * (1 - reduction))
```

Successful non-zero physical hits stay at least 1 damage.

Rationale: this gives the 40% armour value removed from avoidance a second job. Rune armour lowers incoming melee/ranged burst by about 17%, while very high armour cannot exceed 24% reduction. This keeps armour valuable without making it a pure dodge stat.

### 3. Ranged uses the same armour split

Ranged still uses ranged level plus bow/ammo values against the target's defence term, but that target defence now uses the same 60% armour avoidance value. Ranged damage then receives the same physical mitigation as melee.

Rationale: ranged should not remain on the old armour model after melee moves. This makes max ranged into rune armour slightly more reliable, but trims its max hit.

### 4. Magic player-target damage is scaled to 92%

Magic damage still rolls uniformly from 0 to `floor(spellPower)`, and spell success is still based on caster magic level, spell requirement, and magic equipment. Against player targets only, the rolled damage is scaled to 92%, with successful non-zero hits preserved at 1.

Rationale: magic bypasses the new physical armour mitigation. A small player-target reduction keeps Fire Wave, Iban Blast, and charged god spells strong without letting them become the obvious replacement for physical burst. NPC targets keep full magic damage so PvM magic is not nerfed.

### 5. PvP melee big-hit momentum

Player-vs-player melee hits at or above 75% of the target-adjusted max hit grant one momentum stack against that same target. The next successful melee hit against that target rolls damage twice and keeps the higher result, then consumes the stack. A miss or target swap clears it.

Rationale: RSC's most exciting melee moments come from chained large hits while both players are locked in combat. The armour split reduced top-end burst, so this restores some streak drama without adding flat damage or making follow-up big hits guaranteed. It is PvP melee only: ranged, magic, PvM, and team-pile behavior are left alone.

### 6. Simulator now has `openrsc` and `voidscape` rulesets

`scripts/combat-sim.sh` defaults to `voidscape`, matching live behavior. `--rules openrsc` replays the inherited baseline.

Rationale: every future combat edit can compare against both the original baseline and the current Voidscape formula without starting a server.

## Simulator Results

Command:

```bash
scripts/combat-sim.sh --rules openrsc --scenario all --format json --trials 20000 --seed 20260606
scripts/combat-sim.sh --rules voidscape --scenario all --format json --trials 20000 --seed 20260606
```

Key readouts:

| Scenario | OpenRSC | Voidscape | Result |
|---|---:|---:|---|
| New player vs rat, avg kill tick | 15.0 | 15.0 | unchanged early flow |
| Level 60 rune vs lesser, avg kill tick | 72.2 | 72.2 | PvM offence unchanged |
| Lesser vs rune player, hit chance | 13.6% | 19.6% | more chip through armour |
| Lesser vs rune player, max hit | 8 | 6 | less burst through armour |
| Max rune 2h PvP, hit chance | 29.4% | 41.2% | fewer dead swings |
| Max rune 2h PvP, max hit | 28 | 24 | lower burst into armour |
| Max rune 2h PvP, avg fight tick | 79.9 | 66.5 | faster no-food resolution with big-hit chains |
| Mid rune main mirror, avg fight tick | 118.1 | 103.9 | less stall in defensive mirrors |
| Max ranged vs rune player, hit chance | 16.0% | 23.0% | ranged pressure is more reliable |
| Max ranged vs rune player, max hit | 18 | 14 | ranged burst is trimmed |
| Fire Wave vs player, max hit | 10 | 9 | small PvP magic trim |
| Charged god spell vs player, max hit | 25 | 23 | high magic burst trimmed |

## Interpretation

PvM player damage is intentionally unchanged because NPCs currently have no armour points. This preserves the training and boss-kill curve. The defensive side changes mainly affect players wearing armour: NPCs connect a little more often, but their max hit is softened by mitigation.

PvP becomes less binary. Rune armour no longer turns max melee mirrors into 70% misses, but it also prevents the old top hits from landing at full size. This should make fights feel more responsive while leaving food, prayer, PID cadence, and player decision-making as the real fight drivers.

PvP melee momentum adds back the emotional upside of streaks after the armour split. It does not alter the displayed hit chance or max hit; it only changes the damage distribution after a big hit by making the next successful melee hit more likely to land high. Because misses clear the stack and the stack is tied to one target, it should create highlight moments without buffing team piles or target swapping.

Magic is changed conservatively. It still matters because it ignores physical armour, but the 92% player-target scale keeps charged spells from becoming the clear best burst after physical max hits were lowered.

## Follow-Up Checks

- Run in-game sparring for max rune 2h, mid rune main, and pure-vs-main matchups with food enabled.
- Manually test Fire Wave, Iban Blast, and charged god spells against player and NPC targets.
- Watch beta telemetry for dragon and demon deaths after the armour split, because NPC chip damage is slightly more frequent.
- Revisit only after player behavior data; avoid stacking further global formula changes without telemetry.
