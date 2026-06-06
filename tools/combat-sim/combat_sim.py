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


@dataclass(frozen=True)
class Ruleset:
    name: str
    armour_accuracy_scale: float = 1.0
    physical_mitigation_divisor: Optional[float] = None
    physical_mitigation_cap: float = 0.0
    magic_player_damage_scale: float = 1.0


RULESETS: Dict[str, Ruleset] = {
    "openrsc": Ruleset(name="openrsc"),
    "voidscape": Ruleset(
        name="voidscape",
        armour_accuracy_scale=0.60,
        physical_mitigation_divisor=1200.0,
        physical_mitigation_cap=0.24,
        magic_player_damage_scale=0.92,
    ),
}


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


def armour_for_accuracy(defender: Combatant, rules: Ruleset) -> float:
    return 64.0 + (defender.armour * rules.armour_accuracy_scale)


def physical_damage_reduction(defender: Combatant, rules: Ruleset) -> float:
    if rules.physical_mitigation_divisor is None or defender.armour <= 1:
        return 0.0
    return min(rules.physical_mitigation_cap, defender.armour / rules.physical_mitigation_divisor)


def mitigate_physical_damage(damage: int, defender: Combatant, rules: Ruleset) -> int:
    if damage <= 0:
        return 0
    reduction = physical_damage_reduction(defender, rules)
    if reduction <= 0:
        return damage
    return max(1, math.floor(damage * (1.0 - reduction)))


def mitigate_magic_damage(damage: int, defender: Combatant, rules: Ruleset) -> int:
    if damage <= 0 or not defender.is_player or rules.magic_player_damage_scale >= 1.0:
        return damage
    return max(1, math.floor(damage * rules.magic_player_damage_scale))


def melee_defence(defender: Combatant, rules: Ruleset) -> float:
    effective = (
        math.floor(defender.defense * defender.defense_prayer)
        + defender.player_bonus
        + style_bonus(defender, SKILL_DEFENSE)
    )
    return effective * armour_for_accuracy(defender, rules)


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


def reduced_distribution(max_roll: int, defender: Combatant, rules: Ruleset) -> Dict[int, int]:
    distribution: Dict[int, int] = {}
    for damage, count in damage_distribution(max_roll).items():
        reduced_damage = mitigate_physical_damage(damage, defender, rules)
        distribution[reduced_damage] = distribution.get(reduced_damage, 0) + count
    return distribution


def expected_damage(max_roll: int) -> Tuple[int, float]:
    distribution = damage_distribution(max_roll)
    total = sum(distribution.values())
    avg = sum(damage * count for damage, count in distribution.items()) / total
    return max(distribution.keys()), avg


def summarize_melee(attacker: Combatant, defender: Combatant, rules: Ruleset) -> RollSummary:
    attack_roll = melee_accuracy(attacker)
    defence_roll = melee_defence(defender, rules)
    chance = hit_chance(attack_roll, defence_roll)
    max_hit, avg_on_hit = expected_damage(melee_max_roll(attacker))
    reduction = physical_damage_reduction(defender, rules)
    if reduction > 0:
        distribution = reduced_distribution(melee_max_roll(attacker), defender, rules)
        total = sum(distribution.values())
        max_hit = max(distribution.keys())
        avg_on_hit = sum(damage * count for damage, count in distribution.items()) / total
    return RollSummary(
        attack_roll=attack_roll,
        defence_roll=defence_roll,
        hit_chance=chance,
        max_hit=max_hit,
        average_on_hit=avg_on_hit,
        average_per_attempt=chance * avg_on_hit,
    )


def summarize_ranged(attacker: Combatant, defender: Combatant, setup: RangedSetup, rules: Ruleset) -> RollSummary:
    attack_roll = ranged_accuracy(attacker, setup)
    defence_roll = melee_defence(defender, rules)
    chance = hit_chance(attack_roll, defence_roll)
    max_hit, avg_on_hit = expected_damage(ranged_max_roll(attacker, setup))
    reduction = physical_damage_reduction(defender, rules)
    if reduction > 0:
        distribution = reduced_distribution(ranged_max_roll(attacker, setup), defender, rules)
        total = sum(distribution.values())
        max_hit = max(distribution.keys())
        avg_on_hit = sum(damage * count for damage, count in distribution.items()) / total
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


def roll_melee_damage(attacker: Combatant, defender: Combatant, rng: random.Random, rules: Ruleset) -> int:
    chance = hit_chance(melee_accuracy(attacker), melee_defence(defender, rules))
    is_hit = rng.random() <= chance

    while attacker.attack_cape and not is_hit and rng.randint(1, 99) <= 35:
        is_hit = rng.random() <= chance

    damage = mitigate_physical_damage(roll_bucket_damage(melee_max_roll(attacker), rng), defender, rules)
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
    rules: Ruleset,
) -> int:
    chance = hit_chance(ranged_accuracy(attacker, setup), melee_defence(defender, rules))
    if rng.random() > chance:
        return 0
    return mitigate_physical_damage(roll_bucket_damage(ranged_max_roll(attacker, setup), rng), defender, rules)


def roll_magic_damage(caster: Combatant, defender: Combatant, spell: MagicSetup, rng: random.Random, rules: Ruleset) -> int:
    if rng.random() > spell_success_chance(caster, spell):
        return 0
    return mitigate_magic_damage(rng.randrange(math.floor(spell.spell_power) + 1), defender, rules)


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
    rules: Ruleset,
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

            damage = roll_melee_damage(source, target, rng, rules)
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
    rules: Ruleset,
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
                damage = roll_ranged_damage(scenario.attacker, scenario.defender, scenario.ranged, rng, rules)
            elif scenario.mode == "magic":
                assert scenario.magic is not None
                damage = roll_magic_damage(scenario.attacker, scenario.defender, scenario.magic, rng, rules)
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
    armour = {
        "none": 1,
        "bronze": 32,
        "iron": 46,
        "steel": 71,
        "black": 91,
        "mithril": 100,
        "adamantite": 143,
        "rune": 206,
        "rune_no_shield": 160,
    }

    weapons = {
        "none": (1, 1),
        "bronze_short": (7, 7),
        "iron_scimitar": (10, 10),
        "steel_scimitar": (15, 15),
        "mithril_scimitar": (21, 21),
        "adamantite_scimitar": (29, 29),
        "rune_scimitar": (45, 45),
        "rune_scimitar_strength": (45, 55),
        "rune_2h_strength": (71, 81),
        "rune_battleaxe_strength": (48, 75),
    }

    npcs = {
        "rat": Combatant("rat", False, attack=3, strength=2, defense=4, hits=2, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "goblin": Combatant("goblin", False, attack=8, strength=9, defense=9, hits=5, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "cow": Combatant("cow", False, attack=9, strength=9, defense=8, hits=8, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "skeleton": Combatant("skeleton", False, attack=24, strength=23, defense=20, hits=17, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "zombie": Combatant("zombie", False, attack=23, strength=23, defense=28, hits=24, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "giant": Combatant("giant", False, attack=37, strength=40, defense=36, hits=35, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "moss_giant": Combatant("moss giant", False, attack=62, strength=65, defense=61, hits=60, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "lesser": Combatant("lesser demon", False, attack=78, strength=80, defense=79, hits=79, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "greater": Combatant("greater demon", False, attack=86, strength=88, defense=87, hits=87, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "blue_dragon": Combatant("blue dragon", False, attack=105, strength=105, defense=105, hits=105, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "red_dragon": Combatant("red dragon", False, attack=140, strength=140, defense=140, hits=140, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
        "black_dragon": Combatant("black dragon", False, attack=210, strength=210, defense=190, hits=190, weapon_aim=0, weapon_power=0, armour=0, combat_style=STYLE_NONE),
    }

    def melee_player(
        name: str,
        level: int,
        weapon: str,
        armour_tier: str,
        style: str = STYLE_AGGRESSIVE,
        attack_prayer: float = 1.0,
        strength_prayer: float = 1.0,
        defense_prayer: float = 1.0,
    ) -> Combatant:
        weapon_aim, weapon_power = weapons[weapon]
        return Combatant(
            name=name,
            is_player=True,
            attack=level,
            defense=level,
            strength=level,
            hits=level,
            weapon_aim=weapon_aim,
            weapon_power=weapon_power,
            armour=armour[armour_tier],
            combat_style=style,
            attack_prayer=attack_prayer,
            strength_prayer=strength_prayer,
            defense_prayer=defense_prayer,
        )

    def ranged_player(name: str, level: int, armour_tier: str = "none") -> Combatant:
        return Combatant(
            name=name,
            is_player=True,
            defense=level,
            hits=level,
            ranged=level,
            armour=armour[armour_tier],
            combat_style=STYLE_ACCURATE,
        )

    def mage_player(name: str, magic: int, hits: int, defense: int, armour_tier: str = "none", magic_bonus: int = 1) -> Combatant:
        return Combatant(
            name=name,
            is_player=True,
            defense=defense,
            hits=hits,
            magic=magic,
            magic_bonus=magic_bonus,
            armour=armour[armour_tier],
        )

    newbie = melee_player("newbie bronze short", 1, "bronze_short", "none", STYLE_ACCURATE)
    early = melee_player("10 melee iron scim", 10, "iron_scimitar", "iron")
    steel = melee_player("25 melee steel scim", 25, "steel_scimitar", "steel")
    mid_mith = melee_player("40 melee mithril scim", 40, "mithril_scimitar", "mithril")
    mid_rune = melee_player("60 melee rune scim", 60, "rune_scimitar_strength", "rune")
    end_rune = melee_player("80 melee rune scim", 80, "rune_scimitar_strength", "rune")
    max_rune = melee_player("99 melee rune scim", 99, "rune_scimitar_strength", "rune", attack_prayer=1.15, strength_prayer=1.15, defense_prayer=1.15)
    max_2h = melee_player("max rune 2h", 99, "rune_2h_strength", "rune_no_shield", attack_prayer=1.15, strength_prayer=1.15, defense_prayer=1.15)
    max_2h_def = melee_player("max rune 2h defender", 99, "rune_2h_strength", "rune_no_shield", STYLE_DEFENSIVE, attack_prayer=1.15, strength_prayer=1.15, defense_prayer=1.15)
    low_pure = Combatant(
        name="40 attack 70 strength pure rune 2h",
        is_player=True,
        attack=40,
        defense=1,
        strength=70,
        hits=70,
        weapon_aim=weapons["rune_2h_strength"][0],
        weapon_power=weapons["rune_2h_strength"][1],
        armour=armour["none"],
        combat_style=STYLE_AGGRESSIVE,
        attack_prayer=1.15,
        strength_prayer=1.15,
    )
    mid_main = melee_player("70 melee rune battleaxe", 70, "rune_battleaxe_strength", "rune", attack_prayer=1.15, strength_prayer=1.15, defense_prayer=1.15)
    ranger = ranged_player("60 ranged magic shortbow", 60, "steel")
    max_ranger = ranged_player("99 ranged magic shortbow", 99, "rune")
    mage = mage_player("75 magic fire wave", 75, 75, 75, magic_bonus=1)
    max_mage = mage_player("99 magic god spell", 99, 99, 70, magic_bonus=16)
    pvp_rune_60 = melee_player("60 defense rune target", 60, "rune_scimitar_strength", "rune", STYLE_DEFENSIVE)
    pvp_rune_99 = melee_player("99 defense rune target", 99, "rune_scimitar_strength", "rune", STYLE_DEFENSIVE, defense_prayer=1.15)

    scenarios = [
        Scenario(
            name="pvm-newbie-rat",
            mode="melee",
            attacker=newbie,
            defender=npcs["rat"],
            cadence="pve-player-started",
            notes=["Melee PvE sanity check against low-level NPC stats from NpcDefs.json."],
        ),
        Scenario(
            name="pvm-newbie-goblin",
            mode="melee",
            attacker=newbie,
            defender=npcs["goblin"],
            cadence="pve-player-started",
            notes=["Brand-new account with a bronze short sword against the weaker Goblin definition."],
        ),
        Scenario(
            name="pvm-early-cow",
            mode="melee",
            attacker=early,
            defender=npcs["cow"],
            cadence="pve-player-started",
            notes=["Early training check: level 10 iron player against a cow."],
        ),
        Scenario(
            name="pvm-early-skeleton",
            mode="melee",
            attacker=steel,
            defender=npcs["skeleton"],
            cadence="pve-player-started",
            notes=["Early dungeon pressure with steel gear against a skeleton."],
        ),
        Scenario(
            name="pvm-mid-giant",
            mode="melee",
            attacker=mid_mith,
            defender=npcs["giant"],
            cadence="pve-player-started",
            notes=["Mid training check against the Hill Giant-style Giant definition."],
        ),
        Scenario(
            name="pvm-mid-moss-giant",
            mode="melee",
            attacker=mid_rune,
            defender=npcs["moss_giant"],
            cadence="pve-player-started",
            notes=["Rune midgame check against Moss Giant stats."],
        ),
        Scenario(
            name="pvm-rune-lesser",
            mode="melee",
            attacker=mid_rune,
            defender=npcs["lesser"],
            cadence="pve-player-started",
            notes=["Rune scim + strength amulet + full rune style stats, no cape procs."],
        ),
        Scenario(
            name="pvm-rune-greater",
            mode="melee",
            attacker=end_rune,
            defender=npcs["greater"],
            cadence="pve-player-started",
            notes=["Late rune melee check against Greater Demon stats."],
        ),
        Scenario(
            name="pvm-rune-blue-dragon",
            mode="melee",
            attacker=end_rune,
            defender=npcs["blue_dragon"],
            cadence="pve-player-started",
            notes=["Dragon baseline without dragonfire scripting; formula-only melee exchange."],
        ),
        Scenario(
            name="pvm-max-red-dragon",
            mode="melee",
            attacker=max_rune,
            defender=npcs["red_dragon"],
            cadence="pve-player-started",
            notes=["Endgame formula pressure against Red Dragon stats, dragonfire excluded."],
        ),
        Scenario(
            name="pvm-max-black-dragon",
            mode="melee",
            attacker=max_rune,
            defender=npcs["black_dragon"],
            cadence="pve-player-started",
            notes=["Endgame formula pressure against Black Dragon stats, dragonfire excluded."],
        ),
        Scenario(
            name="pvp-low-pure-vs-main",
            mode="melee",
            attacker=low_pure,
            defender=pvp_rune_60,
            cadence="pvp-2-2",
            notes=["Low-defence rune 2h pure pressuring a rune-armoured mid main."],
        ),
        Scenario(
            name="pvp-mid-main-mirror",
            mode="melee",
            attacker=mid_main,
            defender=melee_player("70 melee rune defender", 70, "rune_battleaxe_strength", "rune", STYLE_DEFENSIVE, attack_prayer=1.15, strength_prayer=1.15, defense_prayer=1.15),
            cadence="pvp-2-2",
            notes=["Mid-level rune main mirror with battleaxes and prayers."],
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
            defender=npcs["lesser"],
            ranged=RangedSetup(
                bow_name="magic shortbow",
                ammo_name="rune arrows",
                bow_aim=35,
                ammo_power=40,
            ),
            notes=["Classic ranged formula: magic shortbow aim 35, rune-arrow power 40."],
        ),
        Scenario(
            name="ranged-max-vs-rune-player",
            mode="ranged",
            attacker=max_ranger,
            defender=pvp_rune_99,
            ranged=RangedSetup(
                bow_name="magic shortbow",
                ammo_name="rune arrows",
                bow_aim=35,
                ammo_power=40,
            ),
            notes=["Max ranged pressure into a high-defence rune-armoured player."],
        ),
        Scenario(
            name="magic-fire-wave-pvp",
            mode="magic",
            attacker=mage,
            defender=pvp_rune_60,
            magic=MagicSetup(
                spell_name="Fire Wave",
                required_level=75,
                spell_power=10.0,
            ),
            notes=["Modern magic table maxes Fire Wave at 10 against players and NPCs."],
        ),
        Scenario(
            name="magic-charged-godspell-pvp",
            mode="magic",
            attacker=max_mage,
            defender=pvp_rune_99,
            magic=MagicSetup(
                spell_name="Charged god spell",
                required_level=60,
                spell_power=25.0,
            ),
            notes=["Charged god spell pressure against a high-defence rune-armoured player."],
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


def scenario_result(scenario: Scenario, trials: int, seed: int, max_ticks: int, rules: Ruleset) -> Dict:
    result = {
        "name": scenario.name,
        "mode": scenario.mode,
        "ruleset": rules.name,
        "notes": scenario.notes,
        "attacker": asdict(scenario.attacker),
        "defender": asdict(scenario.defender),
    }
    if scenario.mode == "melee":
        result["cadence"] = scenario.cadence
        result["attacker_roll"] = asdict(summarize_melee(scenario.attacker, scenario.defender, rules))
        result["defender_roll"] = asdict(summarize_melee(scenario.defender, scenario.attacker, rules))
        result["simulation"] = simulate_melee_fight(scenario, trials, seed, max_ticks, rules)
    elif scenario.mode == "ranged":
        if scenario.ranged is None:
            raise ValueError(f"Scenario {scenario.name} needs a ranged setup")
        summary = summarize_ranged(scenario.attacker, scenario.defender, scenario.ranged, rules)
        result["ranged"] = asdict(scenario.ranged)
        result["attacker_roll"] = asdict(summary)
        result["projectile_kill"] = simulate_projectile_to_kill(scenario, trials, seed, 10000, rules)
    elif scenario.mode == "magic":
        if scenario.magic is None:
            raise ValueError(f"Scenario {scenario.name} needs a magic setup")
        success = spell_success_chance(scenario.attacker, scenario.magic)
        max_hit = math.floor(scenario.magic.spell_power)
        damage_values = [
            mitigate_magic_damage(damage, scenario.defender, rules)
            for damage in range(max_hit + 1)
        ]
        max_hit = max(damage_values)
        avg_on_success = statistics.fmean(damage_values)
        result["magic"] = asdict(scenario.magic)
        result["attacker_roll"] = {
            "spell_success_chance": success,
            "max_hit": max_hit,
            "average_on_success": avg_on_success,
            "average_per_attempt": success * avg_on_success,
        }
        result["projectile_kill"] = simulate_projectile_to_kill(scenario, trials, seed, 10000, rules)
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
    print(f"ruleset: {result['ruleset']}")
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
    parser.add_argument("--rules", choices=sorted(RULESETS), default="voidscape", help="Formula ruleset to simulate.")
    parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    parser.add_argument("--trials", type=int, default=20000, help="Monte Carlo trial count.")
    parser.add_argument("--seed", type=int, default=1337, help="Base random seed.")
    parser.add_argument("--max-ticks", type=int, default=5000, help="Max ticks before a melee fight times out.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    rules = RULESETS[args.rules]
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
                rules=rules,
            )
        )

    if args.format == "json":
        print(json.dumps(results, indent=2, sort_keys=True))
    else:
        print(f"trials: {args.trials}")
        print(f"seed: {args.seed}")
        print(f"ruleset: {rules.name}")
        for result in results:
            print_text_result(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
