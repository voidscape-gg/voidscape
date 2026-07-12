# Recipe: tweak the combat formula

Combat math lives in `server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java`. **Touching this is high-risk** — small changes ripple across PvE, PvP, and balance for every NPC and player. Document any change in `docs/DIVERGENCE.md`.

For a primer on the formula, see `docs/subsystems/combat-system.md`.

## Files you'll touch

| File | Why |
|---|---|
| `server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java` | Damage / accuracy math |
| `server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java` | Tick cycle, attacker/defender alternation |
| `server/src/com/openrsc/server/model/entity/player/Prayers.java` | Prayer modifiers |
| `docs/DIVERGENCE.md` | **Required** — record the rationale and the actual change |

## Steps

1. **Define the goal precisely**. "Make max hit higher" is not enough. Specify: which combat type (melee/ranged/magic), which level range, which player vs NPC matchup.
2. **Read `CombatFormula.java` end-to-end before editing.** The functions are short but interlinked. The relevant entry points are usually `doMeleeDamage`, `doRangedDamage`, `calculateMagicDamage`, `getMeleeDamage`, `getRangedDamage`, and `calculateAccuracy`.
3. **Identify the constant or expression to change.** Common knobs:
   - `bonusConstant` (8 for player, 0 for NPC) — has outsized effect via `(power + 64)` multiplier.
   - `styleBonus` (+3 for matched style, +1 for controlled).
   - Prayer multipliers (1.05 / 1.10 / 1.15).
   - The `(weaponPower + 64)` and `(arrowPower + 1 + 64)` constants.
4. **Capture a simulator baseline** for the target scenario before editing:
   ```bash
   scripts/combat-sim.sh --scenario all --trials 50000
   scripts/combat-sim.sh --rules openrsc --scenario all --trials 50000
   ```
   The default `voidscape` ruleset should match live behavior; `openrsc` is the inherited formula baseline. For custom matchups, add a JSON scenario and run it with `--json`.
5. **Make the smallest change that achieves the goal.** Resist refactoring on the side.
6. **Re-run the simulator** and compare hit chance, max hit, expected damage, and simple timing before doing manual testing.
7. **Build through the canonical script**:
   ```bash
   scripts/build.sh
   scripts/run-server.sh
   ```
8. **Test**:
   - Baseline measurement before the change (kill X count of NPC Y, average hits).
   - Same scenario after — should match the predicted shift.
   - Don't forget the inverse: PvP, magic, ranged, prayer-on, prayer-off.

## Verification checklist

- [ ] Predicted change matches measured change (not larger, not smaller).
- [ ] `scripts/combat-sim.sh` baseline and after-change output are captured.
- [ ] PvP not unintentionally affected.
- [ ] Prayer-modified hits scale correctly.
- [ ] NPC vs Player vs NPC vs NPC all behave consistently.
- [ ] No NaN / negative damage edge cases.
- [ ] Defence cape (50% block) still applies correctly.
- [ ] Strength cape (+20% on big hits) still applies correctly.
- [ ] **Recorded in `docs/DIVERGENCE.md`** with date, rationale, and before/after metrics.

## Common pitfalls

- **`bonusConstant` change has multiplicative effect.** Going from 8 to 10 doesn't mean "+25% damage" — it propagates through `(constant + style + level) × (power + 64)`. Predict carefully.
- **NPC `bonusConstant: 0`** means buffing player only also (relatively) buffs NPC damage less. Consider both sides.
- **Prayer multipliers compound with style bonus.** Ultimate Strength + Aggressive style + max-level player can be far above a naive estimate.
- **PvP vs PvE differ.** Damage formula is shared, but tick cycling differs. Test both.
- **Strength cape +20%** only on hits ≥ 50% of max. Affects edge cases.
- **Armour is split in Voidscape.** It still helps avoidance, but only contributes 60% of its value to defence rolls and also reduces physical hit damage by `min(24%, armour / 1200)`.
- **PvP melee has momentum.** A 68%+ target-adjusted melee hit grants one next-hit roll-twice stack against the same player target. Simulator fight timing reflects this, but single-roll summaries do not.
- **Magic PvP has a small target scale.** Player targets take 92% of rolled magic damage; NPC targets keep the full OpenRSC damage roll.
- **`shouldExecute()` of combat scripts** can fire bonus damage on top of formula. Check if any combat scripts shadow your tweak.
- **Don't change `CombatEvent` tick cadence** without deep understanding — that breaks PvP/PvE assumptions globally.

## When NOT to edit `CombatFormula`

If you want NPC X to hit harder, **prefer adding a `CombatScript`** in `server/src/.../event/rsc/impl/combat/scripts/all/` that fires conditionally. That's localized, reversible, and won't impact the rest of the world. Reserve `CombatFormula` for genuinely global rebalances.
