#!/usr/bin/env python3
"""Source/config contract for the launch Overmatch combat ruleset."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


config = read("server/src/com/openrsc/server/ServerConfiguration.java")
formula = read("server/src/com/openrsc/server/event/rsc/impl/combat/OvermatchCombatFormula.java")
classic = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java")
combat_event = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java")
range_utils = read("server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java")
npc_behavior = read("server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java")
proof = read("server/src/com/openrsc/server/content/duelproof/DuelProofService.java")
launch_contract = json.loads(read("scripts/launch-config-contract.json"))

assert "public boolean OVERMATCH_COMBAT;" in config
assert 'OVERMATCH_COMBAT = tryReadBool("overmatch_combat").orElse(false);' in config
assert "overmatch_combat cannot be combined" in config
assert launch_contract.get("overmatch_combat") == "true"

for token in (
    "DENOMINATOR = 1024",
    "GLANCE_THRESHOLD = 205",
    "MELEE_CRIT_THRESHOLD = 614",
    "RANGED_CRIT_THRESHOLD = 717",
    "GLANCE_LOW = 51",
    "CRIT_HIGH = 1178",
    "EDGE_BONUS = 307",
    "RIPOSTE_THRESHOLD = 922",
    "voidMeleeMultiplier(source, victim)",
    "voidRangedMultiplier(source, bowId, victim)",
    "voidSceptreMagicMultiplier(source, victim)",
    "clearPvpMeleeMomentum(source)",
):
    assert token in formula, f"Overmatch port is missing {token}"

assert "OvermatchCombatFormula.calculateMagicDamage" in classic
assert "OvermatchCombatFormula.doMeleeHit" in combat_event
assert "OvermatchCombatFormula.clearEdge" in combat_event
assert "OvermatchCombatFormula.doRangedDamage" in range_utils
assert "OvermatchCombatFormula.doMeleeHit" in npc_behavior
assert "!first.getWorld().getServer().getConfig().OVERMATCH_COMBAT" in proof

print("Overmatch combat source/config contract passed")
