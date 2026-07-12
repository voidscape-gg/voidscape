#!/usr/bin/env bash
# Fail closed if deferred launch content regains a normal-player entry route.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

python3 - "$REPO" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])

def read(relative):
    return (root / relative).read_text()

def config_values(text):
    values = {}
    for line in text.splitlines():
        match = re.match(r"^\s*([A-Za-z0-9_]+)\s*:\s*([^#]*?)(?:\s+#.*)?$", line)
        if match:
            values[match.group(1)] = match.group(2).strip().strip('"\'')
    return values

config = config_values(read("server/voidscape-launch.conf"))
expected = {
    "production_command_lockdown": "true",
    "want_beta_onboarding_guide": "false",
    "want_cracker_campaign": "true",
    "cracker_campaign_npc_kill_denominator": "500",
    "cracker_campaign_skilling_denominator": "1000",
    "want_void_colossus": "false",
    "want_void_dungeon": "true",
}
for key, value in expected.items():
    assert config.get(key) == value, (key, config.get(key), value)

floor = json.loads(read("server/conf/server/data/void_dungeon_floor.json"))
assert {stage["id"] for stage in floor["stages"]} == {1, 2, 3}
assert {stage["floor"] for stage in floor["stages"]} == {1, 2, 3}
assert all(t["sourceStage"] != "void_wyrm" and t["targetStage"] != "void_wyrm"
           for t in floor["transitions"])

npc_defs = read("server/conf/server/defs/NpcDefsCustom.json")
npc_locs = "\n".join(path.read_text() for path in
                       (root / "server/conf/server/defs/locs").glob("NpcLocs*.json"))
npc_drops = read("server/src/com/openrsc/server/constants/NpcDrops.java")
assert not re.search(r'"id"\s*:\s*869(?:\D|$)', npc_defs)
assert not re.search(r'"id"\s*:\s*869(?:\D|$)', npc_locs)
assert "VOID_WYRM" not in npc_drops
assert not list((root / "server").rglob("*VoidWyrm*.java"))

void_test = read("server/plugins/com/openrsc/server/plugins/custom/commands/VoidTestCommands.java")
assert "return player.isDev();" in void_test
assert "VoidColossusArena.devEnter(player)" in void_test
assert "UndeadSiegeMinigame.startSolo(player)" in void_test

colossus = read("server/plugins/com/openrsc/server/plugins/custom/minigames/voidcolossus/VoidColossusArena.java")
assert colossus.count("enterInstance(player)") == 1
assert "public static boolean devEnter(Player player)" in colossus

void_rush = read("server/plugins/com/openrsc/server/plugins/custom/minigames/voidrush/VoidRushMinigame.java")
assert "public static boolean joinQueue(Player player)" in void_rush
all_java = list((root / "server/plugins").rglob("*.java"))
dialogue_callers = [p for p in all_java if "VoidRushNpcDialogue.handle(" in p.read_text()]
assert not dialogue_callers, dialogue_callers
join_callers = [p for p in all_java if "VoidRushMinigame.joinQueue(" in p.read_text()]
allowed_join_callers = {
    root / "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java",
    root / "server/plugins/com/openrsc/server/plugins/custom/minigames/voidrush/VoidRushNpcDialogue.java",
}
assert set(join_callers) == allowed_join_callers, join_callers

siege_dialogue_callers = [p for p in all_java if "UndeadSiegeNpcDialogue.handle(" in p.read_text()]
assert not siege_dialogue_callers, siege_dialogue_callers
start_callers = [p for p in all_java if re.search(r"UndeadSiegeMinigame\.start(?:Solo|Party)\(", p.read_text())]
allowed_start_callers = {
    root / "server/plugins/com/openrsc/server/plugins/custom/commands/VoidTestCommands.java",
    root / "server/plugins/com/openrsc/server/plugins/custom/minigames/undeadsiege/UndeadSiegeNpcDialogue.java",
}
assert set(start_callers) == allowed_start_callers, start_callers

print("Launch reachability passed: deferred Wyrm/fourth floor/Rush/Siege/Colossus fail closed.")
PY
