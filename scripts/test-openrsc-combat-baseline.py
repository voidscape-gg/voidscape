#!/usr/bin/env python3
"""Source and simulator contract for the opt-in OpenRSC combat baseline."""

import json
import sys
from dataclasses import fields
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/combat-sim"))

from combat_sim import RULESETS, Ruleset  # noqa: E402


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


config = read("server/src/com/openrsc/server/ServerConfiguration.java")
formula = read(
    "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
)
combat_event = read(
    "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
)
proof = read("server/src/com/openrsc/server/content/duelproof/DuelProofService.java")
launch_contract = json.loads(read("scripts/launch-config-contract.json"))

assert "public boolean OPENRSC_CLASSIC_COMBAT_BASELINE;" in config
assert (
    'OPENRSC_CLASSIC_COMBAT_BASELINE = '
    'tryReadBool("openrsc_classic_combat_baseline").orElse(false);'
) in config

for required in (
    "usesOpenRscClassicBaseline(source, victim)",
    "!openRscBaseline && source instanceof Npc && victim instanceof Player",
    "!openRscBaseline && source instanceof Player && victim instanceof Npc",
    "!usesOpenRscClassicBaseline(source, victim)",
    "usesOpenRscClassicBaseline(victim, null)",
    "? 1.0D : VOIDSCAPE_ARMOUR_ACCURACY_SCALE",
    "? 1.0D : VOIDSCAPE_MAGIC_PLAYER_DAMAGE_SCALE",
    "usePvpMeleeMomentum && consumePvpMeleeMomentum(source, victim)",
):
    assert required in formula, f"combat baseline path is missing: {required}"

assert "OPENRSC_CLASSIC_COMBAT_BASELINE" in proof
assert "OPENRSC_CLASSIC_COMBAT_BASELINE" in combat_event
assert launch_contract.get("openrsc_classic_combat_baseline", "false") == "false", (
    "the local trial flag must not be enabled in the launch contract"
)

baseline = RULESETS["openrsc"]
neutral = Ruleset(name="openrsc")
for field in fields(Ruleset):
    assert getattr(baseline, field.name) == getattr(neutral, field.name), (
        f"OpenRSC simulator rule is not neutral: {field.name}"
    )

print("OpenRSC combat baseline policy contract passed")
