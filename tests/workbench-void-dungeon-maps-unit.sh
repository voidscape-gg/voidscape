#!/usr/bin/env bash
# Executable client contract for Void Dungeon Workbench map QA.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
SOURCE="$REPO/tests/java/orsc/graphics/gui/WorldMapPanelVoidDungeonTest.java"
CLIENT_JAR="$REPO/Client_Base/Open_RSC_Client.jar"

python3 - "$REPO" <<'PY'
import hashlib
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1])
digests = set()
for floor in range(4):
    image = root / "Client_Base/Cache/worldmap" / f"plane-{floor}.png"
    data = image.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", image
    width, height = struct.unpack(">II", data[16:24])
    assert (width, height) == (2448, 2736), (image, width, height)
    if floor > 0:
        assert len(data) > 20_000, (image, len(data))
    digests.add(hashlib.sha256(data).digest())
assert len(digests) == 4, "surface and dungeon plane images must be distinct"

workbench = (root / "PC_Client/src/orsc/WorkbenchServer.java").read_text()
for fragment in (
    'createContext("/scenario/void-dungeon-maps"',
    'sendCommand("tele " + worldX + " " + worldY)',
    'openWorldMapFromMinimapButton()',
    'getFloorTabCenterX(floor)',
    'closeWorldMapByUiClick()',
    'cleanupVoidDungeonMapsScenario(originalLocation)',
    'if (!client.workbenchAppearancePromptVisible()) return false;',
    'client.workbenchAcceptCurrentAppearance()',
):
    assert fragment in workbench, fragment

mudclient = (root / "Client_Base/src/orsc/mudclient.java").read_text()
assert "public boolean workbenchAcceptCurrentAppearance()" in mudclient
assert "if (!this.showAppearanceChange) return false;\n\t\tsubmitVoidscapeAppearance();" in mudclient
assert mudclient.count("workbenchAcceptCurrentAppearance()") == 1
for source in root.glob("**/*.java"):
    if source.name in {"WorkbenchServer.java", "mudclient.java"}:
        continue
    assert "workbenchAcceptCurrentAppearance" not in source.read_text(errors="ignore"), source

world_map = (root / "Client_Base/src/orsc/graphics/gui/WorldMapPanel.java").read_text()
loader_start = world_map.index("private Sprite getOrLoadFloor(int floor)")
loader_end = world_map.index("private static Sprite makePoiCircle()", loader_start)
loader = world_map[loader_start:loader_end]
decode = 'Sprite s = readPngAsSprite("plane-" + floor + ".png");'
assert "private synchronized Sprite getOrLoadFloor" not in world_map
assert loader.count("synchronized (floorLoadLock)") == 2
assert "\t\t}\n\n\t\t" + decode in loader, "decode must occur after the claim lock"
assert decode + "\n\t\t// Workbench reads" in loader
assert loader.index(decode) < loader.rindex("synchronized (floorLoadLock)"), \
    "decode must occur before the publication lock"
print("Void Dungeon Workbench source/assets contract passed.")
PY

if [ ! -f "$CLIENT_JAR" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/void-dungeon-world-map.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac -source 8 -target 8 -cp "$CLIENT_JAR" -d "$TMP" "$SOURCE"
java -cp "$TMP:$CLIENT_JAR" orsc.graphics.gui.WorldMapPanelVoidDungeonTest
