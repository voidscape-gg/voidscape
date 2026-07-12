#!/usr/bin/env bash
# Deterministic source/data contract for the launch-approved, boss-free Void Dungeon.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

OUTPUTS=(
	"server/conf/server/defs/locs/BoundaryLocsVoidDungeon.json"
	"server/conf/server/defs/locs/SceneryLocsVoidDungeon.json"
	"server/conf/server/defs/locs/NpcLocsVoidDungeon.json"
	"server/conf/server/data/void_dungeon_floor.json"
	"server/src/com/openrsc/server/content/voiddungeon/VoidDungeonLayout.java"
)

hash_outputs() {
	(
		cd "$REPO"
		shasum -a 256 "${OUTPUTS[@]}"
	)
}

before="$(hash_outputs)"
python3 "$REPO/scripts/gen-void-dungeon.py" >/dev/null
after="$(hash_outputs)"
if [ "$before" != "$after" ]; then
	echo "Void Dungeon generator is not deterministic; generated outputs changed." >&2
	diff -u <(printf '%s\n' "$before") <(printf '%s\n' "$after") >&2 || true
	exit 1
fi

if ! cmp -s "$REPO/server/conf/server/data/Custom_Landscape.orsc" \
	"$REPO/Client_Base/Cache/video/Custom_Landscape.orsc"; then
	echo "Server and client Custom_Landscape archives differ." >&2
	exit 1
fi

python3 - "$REPO" <<'PY'
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
authentic = root / "server/conf/server/data/Authentic_Landscape.orsc"
custom = root / "server/conf/server/data/Custom_Landscape.orsc"
retired = ("h1x50y44", "h1x50y45")
with zipfile.ZipFile(authentic) as source, zipfile.ZipFile(custom) as generated:
    for member in retired:
        assert source.read(member) == generated.read(member), member
print("Landscape archive contract passed: client/server identity and authentic retired floor.")
PY

python3 - "$REPO" <<'PY'
import collections
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
data = root / "server/conf/server"
floor = json.loads((data / "data/void_dungeon_floor.json").read_text())
boundaries = json.loads((data / "defs/locs/BoundaryLocsVoidDungeon.json").read_text())["boundaries"]
scenery = json.loads((data / "defs/locs/SceneryLocsVoidDungeon.json").read_text())["sceneries"]
npcs = json.loads((data / "defs/locs/NpcLocsVoidDungeon.json").read_text())["npclocs"]

assert floor["schemaVersion"] == 2
assert floor["surfaceRift"] == {"x": 112, "y": 296}
stages = floor["stages"]
assert [(s["id"], s["key"], s["floor"], s["tileCount"]) for s in stages] == [
    (1, "riftworks", 3, 1382),
    (2, "broken_menagerie", 2, 1524),
    (3, "null_sanctum", 1, 1576),
]

tiles = {tuple(tile) for tile in floor["tiles"]}
assert len(tiles) == 4482
assert {y // 944 for _, y in tiles} == {1, 2, 3}
owners = {}
for stage in stages:
    stage_tiles = set()
    for rect in stage["rectangles"]:
        for x in range(rect["minX"], rect["maxX"] + 1):
            for y in range(rect["minY"], rect["maxY"] + 1):
                stage_tiles.add((x, y))
    assert len(stage_tiles) == stage["tileCount"]
    for tile in stage_tiles:
        assert tile not in owners, (tile, owners[tile], stage["key"])
        owners[tile] = stage["key"]
assert set(owners) == tiles

transitions = {
    (t["objectId"], t["sourceStage"], t["source"]["x"], t["source"]["y"],
     t["targetStage"], t["target"]["x"], t["target"]["y"])
    for t in floor["transitions"]
}
assert transitions == {
    (5, "riftworks", 72, 3197, "broken_menagerie", 72, 2308),
    (6, "broken_menagerie", 72, 2308, "riftworks", 72, 3197),
    (5, "broken_menagerie", 72, 2253, "null_sanctum", 72, 1364),
    (6, "null_sanctum", 72, 1364, "broken_menagerie", 72, 2253),
}

assert len(boundaries) == 1010
assert len({(b["pos"]["X"], b["pos"]["Y"], b["direction"]) for b in boundaries}) == 1010
assert len(scenery) == 46
assert collections.Counter(s["id"] for s in scenery) == {
    5: 2, 6: 2, 46: 8, 51: 26, 1300: 4, 1306: 4,
}
assert collections.Counter(n["id"] for n in npcs) == {
    853: 6, 854: 17, 855: 5, 856: 7, 857: 2, 858: 5, 859: 6, 860: 5,
}
assert len(npcs) == 53
assert all(n["id"] != 869 for n in npcs)

layout = (root / "server/src/com/openrsc/server/content/voiddungeon/VoidDungeonLayout.java").read_text()
assert "STAGE_NULL_SANCTUM = 3" in layout
assert not re.search(r"STAGE_[A-Z0-9_]+\s*=\s*4", layout)
assert "Wyrm" not in layout and "Boss" not in layout

drops = (root / "server/src/com/openrsc/server/constants/NpcDrops.java").read_text()
required_drop_fragments = (
    'new DropTable("Void Wizard unique")',
    "voidWizardGear.addEmptyDrop(4 - voidWizardGear.getTotalWeight())",
    "voidDrop.addTableDrop(voidWizardGear, 1)",
    'new DropTable("Void Ogre unique")',
    "voidOgreGear.addEmptyDrop(4 - voidOgreGear.getTotalWeight())",
    "voidDrop.addTableDrop(voidOgreGear, 1)",
    'new DropTable("Void Giant unique")',
    "voidGiantGear.addEmptyDrop(16 - voidGiantGear.getTotalWeight())",
    "voidDrop.addTableDrop(voidGiantGear, 2)",
    'new DropTable("Void Demon unique")',
    "voidDemonGear.addEmptyDrop(16 - voidDemonGear.getTotalWeight())",
    "voidDrop.addTableDrop(voidDemonGear, 4)",
)
for fragment in required_drop_fragments:
    assert fragment in drops, fragment

knight = drops[drops.index('new DropTable("Void Knight (853)")'):]
knight = knight[:knight.index("this.npcDrops.put(NpcId.VOID_KNIGHT_VOIDBORN.id(), voidDrop);")]
for item in ("VOID_SCIMITAR", "VOID_BOW", "VOID_AMULET", "VOID_MACE"):
    assert item not in knight, item

print("Void Dungeon static contract passed: deterministic three-floor layout, loot, and no boss.")
PY
