# Overmatch — opposed-margin combat formula (design + simulator spec)

Status: **simulator + default-off server implementation.** The `overmatch` ruleset
lives in `tools/combat-sim/combat_sim.py` and is runnable via
`scripts/combat-sim.sh --rules overmatch`. The Java port is isolated behind
`overmatch_combat: true`, remains disabled in the launch contract, and requires no
client or packet change.

The server preserves three live-content extensions that the generic simulator
scenarios do not model: the Void Scimitar/Bow/Sceptre 1.15 target bonuses, custom
NPC player-attack damage floors, and the Ranged cape's second arrow (resolved as a
second Overmatch contest). These are deliberate integration rules, not changes to
the opposed-margin constants below. Magic remains provisional pending the planned
dedicated tuning pass.

Date: 2026-07-17. Provenance: five independent design candidates were generated and
scored by a three-lens judge panel (game feel / systems balance / implementation).
The opposed-margin design won; the shipped spec grafts the judges' required fixes
(fixed-point thresholds, the PvP crit cap for pures, split NPC floors as knobs, a
reserve anti-stall lever) and two fixes found during Monte Carlo tuning
(fractional band quantization, self-anchored threshold floors).

---

## Why (owner's brief)

The three existing options — Voidscape classic, OpenRSC baseline, OSRS melee — all
share one resolution model: an independent accuracy check, then a uniform 0..max
damage roll. Verdict: "all samey." The chosen identity for the replacement:

- **High-stakes KO PvP.** Rare, dramatic big hits with genuine KO potential. Misses
  are fine; *landed* hits must matter. Under live rules, ~40% of successful max-2h
  hits deal 9 or less — that wet-noodle mass is the enemy.
- **Unified PvP + PvM.** One formula for both, with PvM danger and kill speeds kept
  within spitting distance of live's tuned values.
- Fights keep an arc: KOs come from earned windows, not a constant one-shot lottery.

## The model in one paragraph

Every swing is a contested clash. The attacker draws a die over their **Attack
Score**, the defender draws a die over their **Guard Score**, and the **margin**
(attacker minus defender) alone decides the outcome: lose it and you miss, win it
barely and you **glance**, win it cleanly and you land a **solid**, overwhelm it and
you land a **critical** that can exceed the classic max hit. Damage is drawn from
the tier's disjoint band, so the hit-splat alone tells both players what happened —
no UI changes. A critical (or a heroic defensive win, a **riposte**) earns one
**Edge** stack: the next swing against that player adds a large bonus to the attack
die. Crit into edged crit is the earned two-swing KO in a melee lock where food is
illegal. There is no independent accuracy check and no uniform 0..max roll anywhere.

The hit probability P(A > D) of two opposed uniform dice reproduces the live
piecewise hit-chance curve almost exactly, so **tuned hit rates carry over
essentially unchanged** (verified across all 15 built-in melee scenarios:
attacker-side hit% matches live within 0.1 points; NPC counter-swing hit% within
0.3 points — the tiny drift comes from the om_* scores flooring the 1.10 PvE
multipliers to integers). Only the damage story changes.

---

## Exact math

All arithmetic is integer. Fixed-point fractions are numerators over **1024**
(`OM_DENOM`) so a Java port and the duel-proof replayer are bit-exact. `U[a, b]` is
an inclusive uniform integer draw; `U[0, N)` excludes N.

### Effective levels (unchanged plumbing)

```
atk_eff = floor(attack   * attack_prayer)   + style_atk + B
def_eff = floor(defence  * defence_prayer)  + style_def + B
str_eff = floor(strength * strength_prayer) + style_str + B
```

- `B = 8` for players, `0` for NPCs.
- Styles: accurate +3 atk, aggressive +3 str, defensive +3 def, controlled +1 all.
- NPC-vs-player offence shaping (live carry-over, now with **split floors**): when
  an NPC attacks a player its Attack level is floored at `om_npc_acc_floor` (15)
  and its Strength level at `om_npc_power_floor` (15); NPCs whose best offence
  stat is ≥ 40 add `((level - 40) // 8) + 1` capped at `npc_vs_player_bonus_cap`
  (12) to the floored level.

### Scores

```
AS (Attack Score) = atk_eff * (weapon_aim + 64)
GS (Guard Score)  = def_eff * (64 + floor(armour_points * 0.60))
Ranged AS         = (ranged + B) * (bow_aim + 1 + 64)
Melee max hit     MH = max(1, str_eff * (weapon_power + 64) // 640)
Ranged max hit    MH = max(1, (ranged + B) * (ammo_power + 1 + 64) // 640)
```

PvE score multipliers (live carry-over): NPC attacking player `AS = floor(AS *
1.10)`; player attacking NPC `GS = floor(GS * 1.10)`. **Every score is floored at
1 after all multipliers** — shipped NpcDefs contain attackable NPCs with zero
Attack/Defense (charlie, Battle mage), and `U[0, 0)` is undefined; a floored-to-1
score behaves correctly (an AS-1 attacker always misses, a GS-1 defender is
always outmargined).

### Universal tier thresholds

```
T1 = max( om_t_glance_num  * GS // 1024,   om_t1_as_floor_num * AS // 1024 )
T2 =      crit_num         * GS // 1024        (crit_num: melee 614, ranged 717)
     ... if both combatants are players: T2 = min(T2, om_t_crit_as_cap_num * AS // 1024)
T2 = max( T2,  om_t2_as_floor_num * AS // 1024 )
T2 = max(T2, T1)
```

One rule for every attacker. The **guard terms** make armour raise the bar for
being outclassed (a praying rune tank is effectively crit-immune to a lesser
demon — structural, not a resist stat). The **self-anchored floors** mean a clean
strike always requires rolling near your *own* ceiling — this is what keeps crit
rates difficulty-monotone instead of exploding against low-guard targets (trash
NPCs, naked pures, newbies). The **PvP cap** guarantees any player attacker keeps
a rare crit path against any player defender (a 40-att pure crits a plated main
~0.6%/swing instead of never).

### One melee swing — the draw tape

```
draw 1: A = U[0, AS)          (+ floor(om_edge_frac_num * GS / 1024) if an Edge
                               stack is held against this target; consumed now,
                               whatever the outcome)
draw 2: D = U[0, GS)
margin M = A - D  ->  tier:  M <= 0 miss | M < T1 glance | M < T2 solid | else crit
draw 2b (only if the tier is miss and the attacker wears the Attack cape):
        the existing 35% cape proc draw; if it passes, redraw A once (Edge bonus
        reapplied), keep the SAME guard die D, recompute the tier — exactly one
        redraw, so the tape stays bounded
draw 3 (only if not miss): x = U[band_lo_num * MH, band_hi_num * MH]
        damage = (x + 512) >> 10       (round-half-up quantizer)
[Strength cape: existing 35% proc; +20% damage on solid/crit tiers]
[Defence cape: existing 35% proc; halves damage taken]
```

Bands (numerators over 1024 of MH): glance 51–307, solid 410–768, crit 870–1178.

Two properties worth naming:

- **Disjoint bands.** At MH 28: glances splat 1–8, solids 11–21, crits 24–32.
  Nothing ever lands in the gaps, so tiers are readable from splats alone, and an
  over-classic-max splat happens **only** via a critical (invariant — future
  tuning must preserve it).
- **Fractional quantization.** Damage is drawn in 1024ths and rounded to nearest
  (`(x + 512) >> 10`). At MH 1 a "solid" therefore deals 0 about 28% of the time
  and glances deal 0 — which is exactly how the classic formula's fractional
  buckets behave, so trash-tier pacing and early XP rates stay at live values
  instead of tripling. The model becomes visible as damage scales, by design.

### Edge — the KO window

Transient per-fight state, one slot per combatant, same lifecycle as the live
momentum attribute (cleared on target swap / fight end; nothing persists).

- **Earn (opening):** your swing lands a **critical** against a player target.
- **Earn (riposte):** while defending against a player attacker, you win the
  exchange overwhelmingly: `D - A >= floor(om_riposte_frac_num * AS / 1024)`,
  where A is the final attack die (post-Edge) and AS is the *attacker's* score.
  A weak defender can never satisfy it (needs GS > 0.90 * AS). Frequency scales
  with the defence advantage: ~4%/swing in the max-2h mirror (~3.5% in-fight),
  ~20%/swing in the defensive mid-main mirror, and up to ~47%/swing for a maxed
  turtle being poked by weak weapons. That gradient is deliberate and is the
  model's anti-stall mechanism — the harder someone turtles against inadequate
  offence, the more Edge windows they hand themselves, so defensive fights
  resolve instead of stalling (the mid-main mirror resolves in ~85 ticks vs
  live's ~103, with the separate stall-decay lever left off).
- **Spend:** your next swing against that target adds `floor(0.30 * GS_target)`
  to the attack die. Consumed unconditionally. Max 1 stack — cannot be banked.
- **Edge exists only against player targets.** Crits against NPCs stay dramatic
  but do not chain into windows (this kept PvM kill speeds at parity), while a
  black dragon that ripostes an under-levelled attacker does get a live window
  against them.

Measured in the max-2h mirror: a spent Edge raises the next swing's
solid-or-crit chance from 23.2% to **50.4%**. Crit → edged-crit is a genuine
two-swing 45–60 damage sequence into 99 HP — the earned KO.

### Ranged

Same tape (no capes, no Edge earn/spend), `crit_num = 717` (0.70): crits from
eat-legal distance are rarer than in the melee death race.

### Magic

Cast success is the unchanged `Formulae.castSpell()` gate (draw 0). A successful
cast resolves through the same machinery with magic scores, and the miss tier is
remapped so magic never double-whiffs:

```
MS   = (magic + B) * (64 + magic_bonus * 8)             # floored at 1, like all scores
GS_m = def_eff * (64 + armour_points * 123 // 1024)     # armour weight 0.12; floored at 1
T1 = 205 * GS_m // 1024,  T2 = om_t_crit_magic_num * GS_m // 1024
M < max(T1, 1):           graze  x = U[0, 358 * SP]; damage = (x + 512) >> 10
                                 (round-half-up, same quantizer as physical;
                                  0 shows as the familiar splash)
max(T1,1) <= M < max(T2, T1, 1):  solid  same solid band over SP, quantized
M >= max(T2, T1, 1):              crit   same crit band over SP, quantized
```

(The `max(.., 1)` clamps are load-bearing for a bit-exact port: a solid requires
M >= 1 even if T1 computes to 0.)

Player targets keep the live 0.92 scale (floor, non-zero preserved at 1). Magic
neither earns nor spends Edge. The low armour weight is the niche: mages crack
plate that melee cannot. **Open item:** the two magic scenarios pull opposite
directions at final knobs (god spell vs tank 13% stronger than live, mid-level
fire wave vs armour ~75% weaker) — magic needs its own tuning pass before any
server port; levers are `om_magic_bonus_weight`, `om_magic_armour_frac_num`,
and the graze band.

### Reserve lever (not active)

`om_stall_decay_*`: deterministic tick-based threshold decay for turtle stalls,
zero extra draws. Tuning never needed it (the defensive mid-main mirror resolves
in ~85 ticks vs live's ~103), so it ships disabled. Enable only if playtests
find a stall the sim missed.

---

## Knobs (final values)

| Knob | Value | Meaning |
|---|---|---|
| `om_t_glance_num` | 205 (0.20) | glance/solid threshold, fraction of defender GS |
| `om_t1_as_floor_num` | 256 (0.25) | glance/solid floor, fraction of own AS |
| `om_t_crit_num` | 614 (0.60) | melee crit threshold, fraction of defender GS |
| `om_t_crit_ranged_num` | 717 (0.70) | ranged crit threshold |
| `om_t2_as_floor_num` | 614 (0.60) | crit floor, fraction of own AS |
| `om_t_crit_as_cap_num` | 870 (0.85) | PvP-only crit cap vs own AS (pure escape hatch) |
| `om_glance/solid/crit bands` | 51–307 / 410–768 / 870–1178 | damage bands, 1024ths of MH |
| `om_edge_frac_num` | 307 (0.30) | Edge attack-die bonus, fraction of target GS |
| `om_riposte_frac_num` | 922 (0.90) | defensive-win margin that earns Edge |
| `om_npc_acc_floor` / `om_npc_power_floor` | 15 / 15 | split NPC offence floors (= live single floor) |
| `om_magic_armour_frac_num` | 123 (0.12) | armour weight in magic guard |
| `om_magic_bonus_weight` | 8 | magic equipment bonus weight |
| `om_magic_graze_hi_num` | 358 (0.35) | top of magic graze band |
| `om_t_crit_magic_num` | 717 (0.70) | magic crit threshold |
| `om_stall_decay_*` | disabled | reserve anti-stall threshold decay |

Chosen by grid sweep (162 combos × 6k trials) against parity targets derived from
the live baseline; the winning family passed 30/32 weighted target-points.

---

## Simulator results (20k trials, seed 20260716, live = `voidscape` ruleset)

Melee (defender win% / avg kill tick / chip taken per kill):

| Scenario | Live | Overmatch | Note |
|---|---|---|---|
| newbie vs rat | 0.1% / 15.3 / 1.85 | 0.0% / 12.4 / 1.24 | rat still chips, never kills |
| newbie vs goblin | 22.9% / 55.5 / 6.73 | 16.4% / 59.2 / 6.08 | goblin still a real threat |
| lvl-10 iron vs cow | 0% / 31.9 / 1.71 | 0% / 38.3 / 1.07 | +20%, edge of band |
| steel vs skeleton | 0% / 40.8 / 1.93 | 0% / 42.3 / 1.21 | |
| mith vs giant | 0% / 59.1 / 4.69 | 0% / 65.3 / 4.43 | |
| rune vs moss giant | 0% / 50.4 / 5.64 | 0% / 49.6 / 3.52 | |
| 60 rune vs lesser | 0% / 76.2 / 13.68 | 0% / 79.5 / 11.23 | kill speed +4% |
| 80 rune vs greater | 0% / 60.0 / 10.39 | 0% / 62.4 / 7.69 | |
| 80 rune vs blue dragon | 0% / 80.6 / 21.13 | 0% / 88.1 / 18.69 | |
| max vs red dragon | 0% / 77.6 / 26.53 | 0% / 79.8 / 21.63 | |
| max vs black dragon | 49.3% / 116.4 / 85.3 | **56.4%** / 121.3 / 87.6 | dragon ~7pts scarier — flag |
| pure vs 60-def main | 96.8% / 41.6 / 69.5 | 99.0% / 38.0 / 69.9 | pure slightly worse off — flag |
| 70 mirror (defensive) | 48.6% / 102.6 | 55.0% / 84.6 | defensive mirrors resolve faster |
| max 2h mirror 3-1 | 48.3% / 66.5 | 48.9% / 61.4 | |
| max 2h mirror 2-2 | 48.1% / 66.1 | 48.5% / 61.3 | in the 50–90 band |

The max-2h mirror identity readout: hit 41.2% (== live); per swing 18.0% glance
(1–8), 20.1% solid (11–21), **3.0% crit (24–32)**; landed-hit median 13 = **46% of
the classic max 28** (live median ≈ 12 with ~40% of hits ≤ 9); Edge windows as
above. Damage story changed, pacing preserved.

Ranged/magic (avg attempts to kill):

| Scenario | Live | Overmatch |
|---|---|---|
| rune arrows vs lesser | 25.0 | 26.4 |
| max ranged vs praying rune player | 66.0 | 83.8 — ranged-vs-tank weaker, lever `om_t_crit_ranged_num` |
| fire wave vs 60-def rune player | 18.7 | 32.7 — see magic open item |
| charged god spell vs praying tank | 9.6 | 8.4 — anti-tank niche working |

Known deltas to decide on (each has a named lever): black dragon +7pts; naked
pures die ~9% faster to maxed mains; cow +20% kill time; ranged-vs-tank ~21%
less pressure; mid-tier magic vs armour weak; level-3 bronze PvP duels run ~30%
slower than live (MH-1 glances quantize to 0 — newbie PvM is at parity, newbie
PvP is not). Two structural notes from adversarial review, both bounded: crit
rate against very low-guard targets tops out near ~40%/swing (the 0.60·AS floor
is the ceiling — a maxed player critting a naked level-3 is loud but TTK matches
live), and at the opposite extreme an MH-1 attacker literally cannot damage a
maxed praying defender (glance band quantizes to 0 and higher tiers are
unreachable), where live allowed ~1%/swing chip 1s.

---

## Landability (when/if this goes to the server)

- **CombatFormula.java:** melee accuracy+damage collapse into one
  `resolveContest(attacker, defender, channel)` returning tier + damage.
  `MeleeHitResult` gains a `tier` field; a tier is still just an int splat on the
  wire — **zero client/packet changes**. Ranged/magic handlers call the same
  resolver. Live momentum code is replaced by the Edge slot (same per-fight
  attribute pattern, same clear-on-swap/end lifecycle). NPC floors and the 1.10
  PvE multipliers move into score computation verbatim.
- **Config:** server-only flag (`overmatch_combat: true`) exactly like
  `openrsc_classic_combat_baseline`, default off, enabled via ignored
  `server/local.conf` for playtests.
- **Duel-proof:** every draw has a bound known before it is drawn; the tape is
  *simpler* than live's unbounded Attack-cape reroll loop (one bounded redraw).
  Edge/riposte are derived state, no extra tape entries. But context v3 binds a
  formula version and the cohort-10136 client verifier replays the *classic*
  formula: **Overmatch worlds are proof-ineligible (gray) until a client cohort
  ships the new replayer.** Sequence that verifier update with the 10139
  flag-day cutover, and exclude Overmatch worlds from the no-magic-stake
  contract until then — add both to the release checklist if this ships.
- **Untouched:** cadence, retreat rules, the 3-hit rule, aggro, combat scripts
  (dragonfire, poison, drains), Void gear multipliers, Protect from Magic, XP
  policy, prayer drain.

## How to reproduce

```bash
scripts/combat-sim.sh --rules overmatch --scenario all --trials 20000 --seed 20260716
scripts/combat-sim.sh --rules voidscape --scenario all --trials 20000 --seed 20260716   # live baseline
```
