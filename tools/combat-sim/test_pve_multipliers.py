#!/usr/bin/env python3
"""Deterministic contract checks for Voidscape's PvE physical roll multipliers."""

from dataclasses import replace

from combat_sim import (
    Combatant,
    RangedSetup,
    RULESETS,
    melee_accuracy,
    melee_defence,
    melee_max_roll,
    physical_defence,
    ranged_accuracy,
    ranged_max_roll,
)


def require_close(actual: float, expected: float, label: str) -> None:
    if abs(actual - expected) > 1e-9:
        raise AssertionError(f"{label}: expected {expected}, got {actual}")


def main() -> None:
    player = Combatant(
        name="player", is_player=True, attack=60, defense=55, strength=60,
        ranged=60, weapon_aim=45, weapon_power=45, armour=120,
    )
    npc = Combatant(
        name="npc", is_player=False, attack=50, defense=45, strength=50,
        ranged=50, weapon_aim=20, weapon_power=20, armour=80,
        combat_style="none",
    )
    other_npc = replace(npc, name="other npc", defense=40, armour=60)
    ranged = RangedSetup("test bow", "test ammo", bow_aim=30, ammo_power=30)
    boosted = RULESETS["voidscape"]
    neutral = replace(
        boosted,
        npc_vs_player_physical_accuracy_multiplier=1.0,
        npc_physical_defence_against_players_multiplier=1.0,
    )

    require_close(
        melee_accuracy(npc, player, boosted),
        melee_accuracy(npc, player, neutral) * 1.10,
        "npc-to-player melee accuracy",
    )
    require_close(
        ranged_accuracy(npc, player, ranged, boosted),
        ranged_accuracy(npc, player, ranged, neutral) * 1.10,
        "npc-to-player ranged accuracy",
    )
    require_close(
        physical_defence(player, npc, boosted),
        melee_defence(npc, boosted) * 1.10,
        "npc defence against player melee/ranged",
    )

    require_close(
        melee_accuracy(player, npc, boosted),
        melee_accuracy(player, npc, neutral),
        "player attack roll",
    )
    require_close(
        ranged_accuracy(player, npc, ranged, boosted),
        ranged_accuracy(player, npc, ranged, neutral),
        "player ranged attack roll",
    )
    require_close(
        physical_defence(npc, player, boosted),
        physical_defence(npc, player, neutral),
        "player defence against NPC",
    )
    require_close(
        melee_accuracy(npc, other_npc, boosted),
        melee_accuracy(npc, other_npc, neutral),
        "NPC-to-NPC accuracy",
    )
    require_close(
        physical_defence(npc, other_npc, boosted),
        physical_defence(npc, other_npc, neutral),
        "NPC-to-NPC defence",
    )

    if melee_max_roll(npc, player, boosted) != melee_max_roll(npc, player, neutral):
        raise AssertionError("NPC melee max roll changed")
    if ranged_max_roll(npc, ranged) != 4750:
        raise AssertionError("NPC ranged max roll changed")
    if RULESETS["openrsc"].npc_vs_player_physical_accuracy_multiplier != 1.0:
        raise AssertionError("OpenRSC NPC accuracy multiplier must remain neutral")
    if RULESETS["openrsc"].npc_physical_defence_against_players_multiplier != 1.0:
        raise AssertionError("OpenRSC NPC defence multiplier must remain neutral")

    print("PvE physical roll multiplier contract passed")


if __name__ == "__main__":
    main()
