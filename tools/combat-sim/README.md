# Combat simulator

Local measurement harness for Voidscape's active classic OpenRSC combat formulas. It mirrors the formula path in `CombatFormula.java` without changing live game behavior.

Run:

```bash
scripts/combat-sim.sh --list
scripts/combat-sim.sh --scenario pvm-rune-lesser
scripts/combat-sim.sh --scenario all --trials 50000
scripts/combat-sim.sh --scenario pvp-max-rune-2h-3-1 --format json
```

The built-in scenarios cover:

- melee PvM sanity checks
- melee PvP `3-1` and `2-2` cadence comparison
- ranged accuracy/damage against melee defence
- magic spell success and flat spell-power damage

Important limits:

- The tool mirrors formulas, cadence, and melee cape procs, but it does not run the live server, plugins, pathing, eating, NPC scripts, dragonfire, or player decision-making.
- Ranged and magic kill simulations are one-way pressure models. Use them for formula pressure, not full PvP outcome prediction.
- If `CombatFormula.java` changes, update this tool in the same commit and re-run baseline scenarios.

Custom scenarios can be loaded with `--json path/to/scenarios.json`. The file may contain one scenario object, a list, or `{ "scenarios": [...] }`.

Minimal example:

```json
{
  "name": "custom-melee",
  "mode": "melee",
  "cadence": "pve-player-started",
  "attacker": {
    "name": "player",
    "is_player": true,
    "attack": 40,
    "defense": 40,
    "strength": 40,
    "hits": 40,
    "weapon_aim": 45,
    "weapon_power": 45,
    "armour": 80,
    "combat_style": "aggressive"
  },
  "defender": {
    "name": "npc",
    "is_player": false,
    "attack": 30,
    "defense": 30,
    "strength": 30,
    "hits": 30,
    "weapon_aim": 0,
    "weapon_power": 0,
    "armour": 0,
    "combat_style": "none"
  }
}
```
