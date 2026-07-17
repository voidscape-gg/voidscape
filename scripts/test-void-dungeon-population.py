#!/usr/bin/env python3
"""Contract test for the authored and generated Void Dungeon NPC population."""

import ast
import json
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts/gen-void-dungeon.py"
LOCS = ROOT / "server/conf/server/defs/locs/NpcLocsVoidDungeon.json"
DEFS = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"

source = GENERATOR.read_text(encoding="utf-8")
module = ast.parse(source)
spawn_packs = None
for statement in module.body:
    if isinstance(statement, ast.Assign) and any(
        isinstance(target, ast.Name) and target.id == "SPAWN_PACKS"
        for target in statement.targets
    ):
        spawn_packs = ast.literal_eval(statement.value)
        break
assert spawn_packs is not None, "SPAWN_PACKS is missing from the generator"

authored_counts = Counter()
for _stage, _room, npc_name, count, _wander, _step in spawn_packs:
    authored_counts[npc_name] += count

expected_names = {
    "knight": 6,
    "spider": 7,
    "giant": 5,
    "wolf": 5,
    "demon": 2,
    "ogre": 5,
    "wizard": 6,
    "unicorn": 5,
}
assert authored_counts == expected_names, (
    f"unexpected authored Void Dungeon population: {dict(authored_counts)}"
)

loc_data = json.loads(LOCS.read_text(encoding="utf-8"))
generated_counts = Counter(record["id"] for record in loc_data["npclocs"])
expected_ids = {
    853: 6,
    854: 7,
    855: 5,
    856: 5,
    857: 2,
    858: 5,
    859: 6,
    860: 5,
}
assert generated_counts == expected_ids, (
    f"unexpected generated Void Dungeon population: {dict(generated_counts)}"
)
assert len(loc_data["npclocs"]) == 41

npc_defs = json.loads(DEFS.read_text(encoding="utf-8"))
defs_by_id = {definition["id"]: definition for definition in npc_defs["npcs"]}
for npc_id in (853, 860):
    assert defs_by_id[npc_id]["aggressive"] == 1, (
        f"Void Dungeon NPC {npc_id} must be aggressive"
    )

expected_server_profiles = {
    854: {"attack": 28, "strength": 28, "defense": 24, "hits": 16, "combatlvl": 12},
    857: {"attack": 140, "strength": 150, "defense": 130, "hits": 108, "combatlvl": 83},
    860: {"attack": 32, "strength": 32, "defense": 32, "hits": 32, "combatlvl": 25},
}
for npc_id, expected_profile in expected_server_profiles.items():
    actual_profile = {
        key: defs_by_id[npc_id][key]
        for key in ("attack", "strength", "defense", "hits", "combatlvl")
    }
    assert actual_profile == expected_profile, (
        f"unexpected server-only Void Dungeon profile for {npc_id}: {actual_profile}"
    )

drops_source = DROPS.read_text(encoding="utf-8")
for npc_name in ("VOID_GIANT", "VOID_OGRE"):
    assert f"this.bigBoneNpcs.add(NpcId.{npc_name}.id());" in drops_source, (
        f"{npc_name} must produce Big Bones"
    )

print("Void Dungeon population policy contract passed")
