#!/usr/bin/env python3
"""Voidscape combat formula simulator.

This tool mirrors the active classic OpenRSC formula path in:
server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java

It is intentionally standalone so formula experiments can be measured before
touching live server code.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import statistics
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


GAME_TICK_SECONDS = 0.640

SKILL_ATTACK = "attack"
SKILL_DEFENSE = "defense"
SKILL_STRENGTH = "strength"

STYLE_ACCURATE = "accurate"
STYLE_AGGRESSIVE = "aggressive"
STYLE_DEFENSIVE = "defensive"
STYLE_CONTROLLED = "controlled"
STYLE_NONE = "none"


@dataclass
class Combatant:
    name: str
    is_player: bool
    attack: int = 1
    defense: int = 1
    strength: int = 1
    hits: int = 10
    ranged: int = 1
    magic: int = 1
    weapon_aim: int = 1
    weapon_power: int = 1
    armour: int = 1
    magic_bonus: int = 1
    combat_style: str = STYLE_ACCURATE
    attack_prayer: float = 1.0
    strength_prayer: float = 1.0
    defense_prayer: float = 1.0
    attack_cape: bool = False
    strength_cape: bool = False
    defense_cape: bool = False

    @staticmethod
    def from_dict(data: Dict) -> "Combatant":
        return Combatant(**data)

    @property
    def player_bonus(self) -> int:
        return 8 if self.is_player else 0


@dataclass
class RangedSetup:
    bow_name: str
    ammo_name: str
    bow_aim: int
    ammo_power: int
    interval_ticks: int = 3

    @staticmethod
    def from_dict(data: Dict) -> "RangedSetup":
        return RangedSetup(**data)


@dataclass
class MagicSetup:
    spell_name: str
    required_level: int
    spell_power: float
    interval_seconds: float = 1.900

    @staticmethod
    def from_dict(data: Dict) -> "MagicSetup":
        return MagicSetup(**data)


@dataclass
class Scenario:
    name: str
    mode: str
    attacker: Combatant
    defender: Combatant
    cadence: str = "pve-player-started"
    notes: List[str] = field(default_factory=list)
    ranged: Optional[RangedSetup] = None
    magic: Optional[MagicSetup] = None

    @staticmethod
    def from_dict(data: Dict) -> "Scenario":
        clone = dict(data)
        clone["attacker"] = Combatant.from_dict(clone["attacker"])
        clone["defender"] = Combatant.from_dict(clone["defender"])
        if clone.get("ranged"):
            clone["ranged"] = RangedSetup.from_dict(clone["ranged"])
        if clone.get("magic"):
            clone["magic"] = MagicSetup.from_dict(clone["magic"])
        return Scenario(**clone)


@dataclass
class RollSummary:
    attack_roll: float
    defence_roll: float
    hit_chance: float
    max_hit: int
    average_on_hit: float
    average_per_attempt: float


def style_bonus(mob: Combatant, skill: str) -> int:
    if not mob.is_player:
        return 0
    if mob.combat_style == STYLE_CONTROLLED:
        return 1
    if skill == SKILL_ATTACK and mob.combat_style == STYLE_ACCURATE:
        return 3
    if skill == SKILL_DEFENSE and mob.combat_style == STYLE_DEFENSIVE:
        return 3
    if skill == SKILL_STRENGTH and mob.combat_style == STYLE_AGGRESSIVE:
        return 3
    return 0


def melee_accuracy(attacker: Combatant) -> float:
    effective = (
        math.floor(attacker.attack * attacker.attack_prayer)
        + attacker.player_bonus
        + style_bonus(attacker, SKILL_ATTACK)
    )
    return effective * (attacker.weapon_aim + 64)


def melee_defence(defender: Combatant) -> float:
    effective = (
        math.floor(defender.defense * defender.defense_prayer)
        + defender.player_bonus
        + style_bonus(defender, SKILL_DEFENSE)
    )
    return effective * (defender.armour + 64)


def hit_chance(accuracy: float, defence: float) -> float:
    if accuracy > defence:
        return 1.0 - ((defence + 2.0) / (2.0 * (accuracy + 1.0)))
    return accuracy / (2.0 * (defence + 1.0))


def melee_max_roll(attacker: Combatant) -> int:
    effective = (
        math.floor(attacker.strength * attacker.strength_prayer)
        + attacker.player_bonus
        + style_bonus(attacker, SKILL_STRENGTH)
    )
    return int(effective * (attacker.weapon_power + 64))


def ranged_accuracy(attacker: Combatant, setup: RangedSetup) -> float:
    return (attacker.ranged + attacker.player_bonus) * (setup.bow_aim + 1 + 64)


def ranged_max_roll(attacker: Combatant, setup: RangedSetup) -> int:
    return int((attacker.ranged + attacker.player_bonus) * (setup.ammo_power + 1 + 64))


def damage_distribution(max_roll: int) -> Dict[int, int]:
    if max_roll <= 0:
        return {0: 1}
    distribution: Dict[int, int] = {}
    for roll in range(max_roll):
        damage = (roll + 320) // 640
        distribution[damage] = distribution.get(damage, 0) + 1
    return distribution


def expected_damage(max_roll: int) -> Tuple[int, float]:
    distribution = damage_distribution(max_roll)
    total = sum(distribution.values())
    avg = sum(damage * count for damage, count in distribution.items()) / total
    return max(distribution.keys()), avg


def summarize_melee(attacker: Combatant, defender: Combatant) -> RollSummary:
    attack_roll = melee_accuracy(attacker)
    defence_roll = melee_defence(defender)
    chance = hit_chance(attack_roll, defence_roll)
    max_hit, avg_on_hit = expected_damage(melee_max_roll(attacker))
    return RollSummary(
        attack_roll=attack_roll,
        defence_roll=defence_roll,
        hit_chance=chance,
        max_hit=max_hit,
        average_on_hit=avg_on_hit,
        average_per_attempt=chance * avg_on_hit,
    )


def summarize_ranged(attacker: Combatant, defender: Combatant, setup: RangedSetup) -> RollSummary:
    attack_roll = ranged_accuracy(attacker, setup)
    defence_roll = melee_defence(defender)
    chance = hit_chance(attack_roll, defence_roll)
    max_hit, avg_on_hit = expected_damage(ranged_max_roll(attacker, setup))
    return RollSummary(
        attack_roll=attack_roll,
        defence_roll=defence_roll,
        hit_chance=chance,
        max_hit=max_hit,
        average_on_hit=avg_on_hit,
        average_per_attempt=chance * avg_on_hit,
    )


def spell_success_chance(caster: Combatant, spell: MagicSetup) -> float:
    level_diff = caster.magic - spell.required_level
    magic_equip = caster.magic_bonus
    if magic_equip >= 30 and level_diff >= 5:
        return 1.0
    if magic_equip >= 25 and level_diff >= 6:
        return 1.0
    if magic_equip >= 20 and level_diff >= 7:
        return 1.0
    if magic_equip >= 15 and level_diff >= 8:
        return 1.0
    if magic_equip >= 10 and level_diff >= 9:
        return 1.0
    if level_diff < 0:
        return 0.0
    if level_diff >= 10:
        return 1.0
    high = (level_diff + 2) * 2
    return 1.0 - (1.0 / (high + 1.0))


def roll_bucket_damage(max_roll: int, rng: random.Random) -> int:
    if max_roll <= 0:
        return 0
    return (rng.randrange(max_roll) + 320) // 640


def roll_melee_damage(attacker: Combatant, defender: Combatant, rng: random.Random) -> int:
    chance = hit_chance(melee_accuracy(attacker), melee_defence(defender))
    is_hit = rng.random() <= chance

    while attacker.attack_cape and not is_hit and rng.randint(1, 99) <= 35:
        is_hit = rng.random() <= chance

    damage = roll_bucket_damage(melee_max_roll(attacker), rng)
    if not is_hit:
        return 0

    if defender.is_player and defender.defense_cape and damage > 0 and rng.randint(1, 99) <= 35:
        damage //= 2

    maximum = (melee_max_roll(attacker) + 320) / 640.0
    if attacker.strength_cape and damage >= maximum * 0.5 and rng.randint(1, 99) <= 35:
        damage = int(damage + maximum * 0.2)

    return damage


def roll_ranged_damage(
    attacker: Combatant,
    defender: Combatant,
    setup: RangedSetup,
    rng: random.Random,
) -> int:
    chance = hit_chance(ranged_accuracy(attacker, setup), melee_defence(defender))
    if rng.random() > chance:
        return 0
    return roll_bucket_damage(ranged_max_roll(attacker, setup), rng)


def roll_magic_damage(caster: Combatant, spell: MagicSetup, rng: random.Random) -> int:
    if rng.random() > spell_success_chance(caster, spell):
        return 0
    return rng.randrange(math.floor(spell.spell_power) + 1)


def cadence_delay(cadence: str, round_number: int) -> int:
    if cadence in {"pvp-2-2", "duel-2-2", "npc-attacks-player"}:
        return 2
    if cadence in {"pve-player-started", "pvp-3-1"}:
        return 3 if round_number % 2 == 0 else 1
    raise ValueError(f"Unknown melee cadence: {cadence}")


def simulate_melee_fight(
    scenario: Scenario,
    trials: int,
    seed: int,
    max_ticks: int,
) -> Dict:
    rng = random.Random(seed)
    wins = {scenario.attacker.name: 0, scenario.defender.name: 0, "timeout": 0}
    win_ticks: List[int] = []
    attacker_damage: List[int] = []
    defender_damage: List[int] = []
    attacker_swings: List[int] = []
    defender_swings: List[int] = []

    for _ in range(trials):
        hp = [scenario.attacker.hits, scenario.defender.hits]
        total_damage = [0, 0]
        swings = [0, 0]
        tick = 0
        round_number = 0

        while tick <= max_ticks:
            source_index = 0 if round_number % 2 == 0 else 1
            target_index = 1 - source_index
            source = scenario.attacker if source_index == 0 else scenario.defender
            target = scenario.defender if target_index == 1 else scenario.attacker

            damage = roll_melee_damage(source, target, rng)
            damage = min(damage, hp[target_index])
            hp[target_index] -= damage
            total_damage[source_index] += damage
            swings[source_index] += 1

            if hp[target_index] <= 0:
                winner = scenario.attacker.name if source_index == 0 else scenario.defender.name
                wins[winner] += 1
                win_ticks.append(tick)
                break

            tick += cadence_delay(scenario.cadence, round_number)
            round_number += 1
        else:
            wins["timeout"] += 1

        attacker_damage.append(total_damage[0])
        defender_damage.append(total_damage[1])
        attacker_swings.append(swings[0])
        defender_swings.append(swings[1])

    return {
        "trials": trials,
        "seed": seed,
        "wins": wins,
        "win_ticks": summarize_samples(win_ticks),
        "attacker_damage": summarize_samples(attacker_damage),
        "defender_damage": summarize_samples(defender_damage),
        "attacker_swings": summarize_samples(attacker_swings),
        "defender_swings": summarize_samples(defender_swings),
    }


def simulate_projectile_to_kill(
    scenario: Scenario,
    trials: int,
    seed: int,
    max_attempts: int,
) -> Dict:
    rng = random.Random(seed)
    attempts: List[int] = []
    damage_totals: List[int] = []
    timeouts = 0

    for _ in range(trials):
        hp = scenario.defender.hits
        total_damage = 0
        for attempt in range(1, max_attempts + 1):
            if scenario.mode == "ranged":
                assert scenario.ranged is not None
                damage = roll_ranged_damage(scenario.attacker, scenario.defender, scenario.ranged, rng)
            elif scenario.mode == "magic":
                assert scenario.magic is not None
                damage = roll_magic_damage(scenario.attacker, scenario.magic, rng)
            else:
                raise ValueError(f"Unsupported projectile mode: {scenario.mode}")
            damage = min(damage, hp)
            hp -= damage
            total_damage += damage
            if hp <= 0:
                attempts.append(attempt)
                damage_totals.append(total_damage)
                break
        else:
            timeouts += 1
            damage_totals.append(total_damage)

    return {
        "trials": trials,
        "seed": seed,
        "timeouts": timeouts,
        "attempts_to_kill": summarize_samples(attempts),
        "damage_totals": summarize_samples(damage_totals),
    }


def summarize_samples(samples: List[int]) -> Dict[str, Optional[float]]:
    if not samples:
        return {"count": 0, "avg": None, "median": None, "p05": None, "p95": None}
    ordered = sorted(samples)
    return {
        "count": len(samples),
        "avg": statistics.fmean(samples),
        "median": percentile(ordered, 0.50),
        "p05": percentile(ordered, 0.05),
        "p95": percentile(ordered, 0.95),
    }


def percentile(ordered: List[int], p: float) -> float:
    if len(ordered) == 1:
        return float(ordered[0])
    pos = (len(ordered) - 1) * p
    lower = math.floor(pos)
    upper = math.ceil(pos)
    if lower == upper:
        return float(ordered[lower])
    weight = pos - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def built_in_scenarios() -> Dict[str, Scenario]:
    newbie = Combatant(
        name="newbie",
        is_player=True,
        attack=1,
        defense=1,
        strength=1,
        hits=10,
        weapon_aim=1,
        weapon_power=1,
        armour=1,
        combat_style=STYLE_ACCURATE,
    )
    rat = Combatant(
        name="rat",
        is_player=False,
        attack=3,
        defense=4,
        strength=2,
        hits=2,
        weapon_aim=0,
        weapon_power=0,
        armour=0,
        combat_style=STYLE_NONE,
    )
    mid_rune = Combatant(
        name="60 melee rune scim",
        is_player=True,
        attack=60,
        defense=60,
        strength=60,
        hits=60,
        weapon_aim=45,
        weapon_power=55,
        armour=207,
        combat_style=STYLE_AGGRESSIVE,
    )
    lesser = Combatant(
        name="lesser demon",
        is_player=False,
        attack=78,
        defense=79,
        strength=80,
        hits=79,
        weapon_aim=0,
        weapon_power=0,
        armour=0,
        combat_style=STYLE_NONE,
    )
    max_2h = Combatant(
        name="max rune 2h",
        is_player=True,
        attack=99,
        defense=99,
        strength=99,
        hits=99,
        weapon_aim=71,
        weapon_power=81,
        armour=160,
        combat_style=STYLE_AGGRESSIVE,
        attack_prayer=1.15,
        strength_prayer=1.15,
        defense_prayer=1.15,
    )
    max_2h_def = Combatant(
        name="max rune 2h defender",
        is_player=True,
        attack=99,
        defense=99,
        strength=99,
        hits=99,
        weapon_aim=71,
        weapon_power=81,
        armour=160,
        combat_style=STYLE_DEFENSIVE,
        attack_prayer=1.15,
        strength_prayer=1.15,
        defense_prayer=1.15,
    )
    ranger = Combatant(
        name="60 ranged magic shortbow",
        is_player=True,
        defense=60,
        hits=60,
        ranged=60,
        armour=80,
        combat_style=STYLE_ACCURATE,
    )
    mage = Combatant(
        name="75 magic fire wave",
        is_player=True,
        defense=75,
        hits=75,
        magic=75,
        magic_bonus=1,
        armour=1,
    )
    pvp_target = Combatant(
        name="60 defense rune target",
        is_player=True,
        attack=60,
        defense=60,
        strength=60,
        hits=60,
        armour=207,
        combat_style=STYLE_DEFENSIVE,
    )

    scenarios = [
        Scenario(
            name="pvm-newbie-rat",
            mode="melee",
            attacker=newbie,
            defender=rat,
            cadence="pve-player-started",
            notes=["Melee PvE sanity check against low-level NPC stats from NpcDefs.json."],
        ),
        Scenario(
            name="pvm-rune-lesser",
            mode="melee",
            attacker=mid_rune,
            defender=lesser,
            cadence="pve-player-started",
            notes=["Rune scim + strength amulet + full rune style stats, no cape procs."],
        ),
        Scenario(
            name="pvp-max-rune-2h-3-1",
            mode="melee",
            attacker=max_2h,
            defender=max_2h_def,
            cadence="pvp-3-1",
            notes=["Maxed rune 2h mirror with prayers; attacker gets the 3-1 cadence branch."],
        ),
        Scenario(
            name="pvp-max-rune-2h-2-2",
            mode="melee",
            attacker=max_2h,
            defender=max_2h_def,
            cadence="pvp-2-2",
            notes=["Same maxed mirror under duel/lower-PID 2-2 cadence."],
        ),
        Scenario(
            name="ranged-rune-arrow-lesser",
            mode="ranged",
            attacker=ranger,
            defender=lesser,
            ranged=RangedSetup(
                bow_name="magic shortbow",
                ammo_name="rune arrows",
                bow_aim=35,
                ammo_power=40,
            ),
            notes=["Classic ranged formula: magic shortbow aim 35, rune-arrow power 40."],
        ),
        Scenario(
            name="magic-fire-wave-pvp",
            mode="magic",
            attacker=mage,
            defender=pvp_target,
            magic=MagicSetup(
                spell_name="Fire Wave",
                required_level=75,
                spell_power=10.0,
            ),
            notes=["Modern magic table maxes Fire Wave at 10 against players and NPCs."],
        ),
    ]
    return {scenario.name: scenario for scenario in scenarios}


def load_scenarios(path: Path) -> Dict[str, Scenario]:
    data = json.loads(path.read_text())
    if isinstance(data, dict) and "scenarios" in data:
        raw_scenarios = data["scenarios"]
    elif isinstance(data, list):
        raw_scenarios = data
    else:
        raw_scenarios = [data]
    scenarios = [Scenario.from_dict(raw) for raw in raw_scenarios]
    return {scenario.name: scenario for scenario in scenarios}


def scenario_result(scenario: Scenario, trials: int, seed: int, max_ticks: int) -> Dict:
    result = {
        "name": scenario.name,
        "mode": scenario.mode,
        "notes": scenario.notes,
        "attacker": asdict(scenario.attacker),
        "defender": asdict(scenario.defender),
    }
    if scenario.mode == "melee":
        result["cadence"] = scenario.cadence
        result["attacker_roll"] = asdict(summarize_melee(scenario.attacker, scenario.defender))
        result["defender_roll"] = asdict(summarize_melee(scenario.defender, scenario.attacker))
        result["simulation"] = simulate_melee_fight(scenario, trials, seed, max_ticks)
    elif scenario.mode == "ranged":
        if scenario.ranged is None:
            raise ValueError(f"Scenario {scenario.name} needs a ranged setup")
        summary = summarize_ranged(scenario.attacker, scenario.defender, scenario.ranged)
        result["ranged"] = asdict(scenario.ranged)
        result["attacker_roll"] = asdict(summary)
        result["projectile_kill"] = simulate_projectile_to_kill(scenario, trials, seed, 10000)
    elif scenario.mode == "magic":
        if scenario.magic is None:
            raise ValueError(f"Scenario {scenario.name} needs a magic setup")
        success = spell_success_chance(scenario.attacker, scenario.magic)
        max_hit = math.floor(scenario.magic.spell_power)
        avg_on_success = max_hit / 2.0
        result["magic"] = asdict(scenario.magic)
        result["attacker_roll"] = {
            "spell_success_chance": success,
            "max_hit": max_hit,
            "average_on_success": avg_on_success,
            "average_per_attempt": success * avg_on_success,
        }
        result["projectile_kill"] = simulate_projectile_to_kill(scenario, trials, seed, 10000)
    else:
        raise ValueError(f"Unknown scenario mode: {scenario.mode}")
    return result


def format_percent(value: float) -> str:
    return f"{value * 100.0:6.2f}%"


def format_number(value: Optional[float], suffix: str = "") -> str:
    if value is None:
        return "n/a"
    return f"{value:.2f}{suffix}"


def print_roll(label: str, roll: Dict) -> None:
    print(f"  {label}")
    print(f"    attack roll: {roll['attack_roll']:.0f}")
    print(f"    defence roll: {roll['defence_roll']:.0f}")
    print(f"    hit chance: {format_percent(roll['hit_chance'])}")
    print(f"    max hit: {roll['max_hit']}")
    print(f"    avg on hit: {roll['average_on_hit']:.3f}")
    print(f"    avg per attempt: {roll['average_per_attempt']:.3f}")


def print_sample(label: str, sample: Dict, tick_units: bool = False) -> None:
    if sample["count"] == 0:
        print(f"    {label}: n/a")
        return
    suffix = " ticks" if tick_units else ""
    print(
        f"    {label}: avg {format_number(sample['avg'], suffix)}, "
        f"median {format_number(sample['median'], suffix)}, "
        f"p05 {format_number(sample['p05'], suffix)}, "
        f"p95 {format_number(sample['p95'], suffix)}"
    )
    if tick_units and sample["avg"] is not None:
        print(f"      avg seconds: {sample['avg'] * GAME_TICK_SECONDS:.2f}s")


def print_text_result(result: Dict) -> None:
    print()
    print(f"== {result['name']} ==")
    if result["notes"]:
        for note in result["notes"]:
            print(f"note: {note}")
    print(f"mode: {result['mode']}")
    print(f"attacker: {result['attacker']['name']}")
    print(f"defender: {result['defender']['name']}")

    if result["mode"] == "melee":
        print(f"cadence: {result['cadence']}")
        print_roll("attacker melee", result["attacker_roll"])
        print_roll("defender melee", result["defender_roll"])
        sim = result["simulation"]
        print("  fight simulation")
        total = sim["trials"]
        for winner, count in sim["wins"].items():
            print(f"    {winner}: {count} wins ({(count / total) * 100.0:.2f}%)")
        print_sample("win tick", sim["win_ticks"], tick_units=True)
        print_sample("attacker damage", sim["attacker_damage"])
        print_sample("defender damage", sim["defender_damage"])
        print_sample("attacker swings", sim["attacker_swings"])
        print_sample("defender swings", sim["defender_swings"])
    elif result["mode"] == "ranged":
        setup = result["ranged"]
        print(f"ranged setup: {setup['bow_name']} + {setup['ammo_name']}")
        print_roll("attacker ranged", result["attacker_roll"])
        avg_attempt = result["attacker_roll"]["average_per_attempt"]
        interval = setup["interval_ticks"] * GAME_TICK_SECONDS
        print(f"    avg dps at {setup['interval_ticks']}-tick interval: {avg_attempt / interval:.3f}")
        projectile = result["projectile_kill"]
        print("  one-way kill simulation")
        print(f"    timeouts: {projectile['timeouts']} / {projectile['trials']}")
        print_sample("attempts to kill", projectile["attempts_to_kill"])
    elif result["mode"] == "magic":
        setup = result["magic"]
        roll = result["attacker_roll"]
        print(f"spell: {setup['spell_name']}")
        print(f"  success chance: {format_percent(roll['spell_success_chance'])}")
        print(f"  max hit: {roll['max_hit']}")
        print(f"  avg on successful cast: {roll['average_on_success']:.3f}")
        print(f"  avg per cast attempt: {roll['average_per_attempt']:.3f}")
        print(f"  avg dps at {setup['interval_seconds']:.3f}s interval: {roll['average_per_attempt'] / setup['interval_seconds']:.3f}")
        projectile = result["projectile_kill"]
        print("  one-way kill simulation")
        print(f"    timeouts: {projectile['timeouts']} / {projectile['trials']}")
        print_sample("casts to kill", projectile["attempts_to_kill"])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Voidscape combat formula simulations.")
    parser.add_argument("--scenario", action="append", help="Scenario name to run. Repeatable. Use 'all' for every scenario.")
    parser.add_argument("--list", action="store_true", help="List available scenarios and exit.")
    parser.add_argument("--json", type=Path, help="Load one or more custom scenarios from JSON.")
    parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    parser.add_argument("--trials", type=int, default=20000, help="Monte Carlo trial count.")
    parser.add_argument("--seed", type=int, default=1337, help="Base random seed.")
    parser.add_argument("--max-ticks", type=int, default=5000, help="Max ticks before a melee fight times out.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    scenarios = built_in_scenarios()
    if args.json:
        scenarios.update(load_scenarios(args.json))

    if args.list:
        for name in sorted(scenarios):
            print(name)
        return 0

    selected = args.scenario or ["all"]
    if "all" in selected:
        names = list(scenarios)
    else:
        names = selected

    missing = [name for name in names if name not in scenarios]
    if missing:
        print(f"Unknown scenario(s): {', '.join(missing)}")
        print("Use --list to see available scenarios.")
        return 2

    results = []
    for index, name in enumerate(names):
        results.append(
            scenario_result(
                scenarios[name],
                trials=args.trials,
                seed=args.seed + index,
                max_ticks=args.max_ticks,
            )
        )

    if args.format == "json":
        print(json.dumps(results, indent=2, sort_keys=True))
    else:
        print(f"trials: {args.trials}")
        print(f"seed: {args.seed}")
        for result in results:
            print_text_result(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
