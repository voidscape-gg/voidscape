#!/usr/bin/env bash
# run-npc-preview.sh — launch the Voidscape NPC sprite previewer.
#
# Renders any NPC exactly how the in-game client draws it (reuses the real
# MudClientGraphics rasterizer + EntityHandler defs + Sprite archive). Use it to
# iterate on NPC sprites/animations without the server+client+login loop:
#   - rotate the camera, scrub direction, play walk + combat animations live
#   - tune walkModel/combatModel (speed) and camera1/camera2 (size) on the fly
#   - inspect a raw 27-frame AnimationDef entity set with onion-skin previews
#   - re-pack candidate frames into Authentic_Sprites.orsc, hit "Reload sprites"
#
# Usage:  scripts/run-npc-preview.sh           # GUI
#         scripts/run-npc-preview.sh --dump <outDir> <npcId>   # headless frame dump
#         scripts/run-npc-preview.sh --dump-entity <outDir> <animationId>

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/Client_Base"

JAR="Open_RSC_Client.jar"
if [[ ! -f "$JAR" ]]; then
  echo "==> $JAR not found; building client first"
  ant compile >/dev/null
fi

OUT="$REPO_ROOT/tmp/npcpreview-classes"
mkdir -p "$OUT"
echo "==> Compiling NpcPreview"
javac -cp "$JAR" -d "$OUT" src/tools/NpcPreview.java

echo "==> Launching NPC previewer"
if [[ "${1:-}" == "--dump" ]]; then
  java -Djava.awt.headless=true -cp "$JAR:$OUT" tools.NpcPreview Cache --dump "${2:-/tmp/npcdump}" "${3:-852}"
elif [[ "${1:-}" == "--dump-entity" ]]; then
  java -Djava.awt.headless=true -cp "$JAR:$OUT" tools.NpcPreview Cache --dump-entity "${2:-/tmp/entitydump}" "${3:-0}"
else
  java -cp "$JAR:$OUT" tools.NpcPreview Cache
fi
