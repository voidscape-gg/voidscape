#!/usr/bin/env bash
# tests/smoke.sh — end-to-end voidbot smoke test, zero screenshots.
#
# Drives a real logged-in session entirely through voidbot commands:
#   session -> walk -> npc dialog -> kill an npc -> loot -> bank
# Each step is asserted with `voidbot wait` / `voidbot state`. Admin commands are
# used ONLY to set up deterministic conditions; the actions themselves go through
# real game packets (WORLD_WALK_REQUEST, NPC_TALK_TO, NPC_ATTACK, GROUND_ITEM_TAKE,
# BANK_DEPOSIT, ...).
#
# Requires: a running voidscape server and an admin account.
#   VOIDBOT_USER / VOIDBOT_PASS  (default wbtest / voidtest123)
#   VOIDBOT_GAME_PORT            (default 43596)
#   VOIDBOT_CTRL_PORT            (default 18900; set a unique port per parallel run)
#   VOIDBOT_SMOKE_X/_Y           (default 126/650; walk/kill/loot arena anchor — give
#                                 each parallel run its own open-ground tile so ground
#                                 items and NPC spawns don't cross-contaminate; the
#                                 vendor-dialog stage stays at its fixed NPC location)
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
VB="$REPO/tools/voidbot/voidbot"
USER="${VOIDBOT_USER:-wbtest}"
PASS="${VOIDBOT_PASS:-voidtest123}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
SPOT_X="${VOIDBOT_SMOKE_X:-126}"
SPOT_Y="${VOIDBOT_SMOKE_Y:-650}"
if [ -n "${VOIDBOT_PYTHON:-}" ]; then
  PY="$VOIDBOT_PYTHON"
elif command -v python3 >/dev/null 2>&1; then
  PY="python3"
else
  PY="python"
fi

PASS_N=0; FAIL_N=0
DAEMON_PID=""

cleanup() {
  [ -n "$DAEMON_PID" ] && "$VB" admin "::quit" >/dev/null 2>&1 || true
  "$VB" stop >/dev/null 2>&1 || true
  [ -n "$DAEMON_PID" ] && kill "$DAEMON_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ok <label> <json>   -> pass if json has "ok": true
ok() {
  local label="$1" json="$2"
  if echo "$json" | grep -q '"ok": *true'; then
    echo "  PASS  $label"; PASS_N=$((PASS_N+1))
  else
    echo "  FAIL  $label"; echo "        $json"; FAIL_N=$((FAIL_N+1))
  fi
}
# matched <label> <json> -> pass if json has "matched": true
matched() {
  local label="$1" json="$2"
  if echo "$json" | grep -q '"matched": *true'; then
    echo "  PASS  $label"; PASS_N=$((PASS_N+1))
  else
    echo "  FAIL  $label"; echo "        $json"; FAIL_N=$((FAIL_N+1))
  fi
}

echo "== voidbot smoke: $USER @ 127.0.0.1:$GAME_PORT =="

# ---- 1. session ----
echo "[1] session"
START=$(VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" start --user "$USER" --pass "$PASS")
DAEMON_PID=$(echo "$START" | sed -n 's/.*"pid":\([0-9]*\).*/\1/p')
ok "daemon started" "$START"
matched "logged in" "$("$VB" wait logged-in --timeout 20)"
ok "have position" "$("$VB" state position)"

# ---- 2. walk (real WORLD_WALK_REQUEST) ----
echo "[2] walk"
ok    "teleport setup"  "$("$VB" admin "::teleport $SPOT_X $SPOT_Y")"
sleep 1
ok    "goto issued"     "$("$VB" goto $((SPOT_X-4)) $((SPOT_Y-2)))"
matched "arrived near target" "$("$VB" wait near --x $((SPOT_X-4)) --y $((SPOT_Y-2)) --radius 2 --timeout 20)"

# ---- 3. npc dialog (real NPC_TALK_TO) ----
echo "[3] npc dialog"
ok    "teleport next to vendor" "$("$VB" admin "::teleport 127 649")"
matched "vendor (npc 848) in view" "$("$VB" wait npc-present --id 848 --timeout 15)"
ok    "talk to vendor (npc 848)" "$("$VB" npc-talk --id 848)"
# vendor with no linked account opens a signup-code input box (the dialogue fired)
matched "vendor opened input box" "$("$VB" wait input-open --timeout 15)"
ok    "dismiss input box (soft-lock-fix path)" "$("$VB" input-reply --text '')"

# ---- 4. kill an npc (real NPC_ATTACK), tracked by the specific instance ----
echo "[4] kill"
"$VB" admin "::teleport $SPOT_X $SPOT_Y" >/dev/null   # back to this run's own arena
sleep 1.2
# ::setstat takes LEVEL then STAT (VS-047); pace admin commands >=1.2s or they drop (VS-027)
"$VB" admin "::setstat 90 attack"   >/dev/null; sleep 1.2
"$VB" admin "::setstat 90 strength" >/dev/null; sleep 1.2
"$VB" admin "::setstat 90 hits"     >/dev/null; sleep 1.2
"$VB" admin "::heal"                >/dev/null
# spawn + detect in a short retry loop (the bit-packed NPC stream can drop a frame)
RAT_SI=""; SPAWN_OK="no"
for attempt in 1 2 3; do
  "$VB" admin "::spawnnpc 3 30 2 $((SPOT_X+1)) $((SPOT_Y+1))" >/dev/null
  PR=$("$VB" wait npc-present --id 3 --timeout 12)
  if echo "$PR" | grep -q '"matched": *true'; then
    RAT_SI=$("$VB" state npcs | "$PY" -c 'import sys,json
n=[x for x in json.load(sys.stdin)["state"]["npcs"] if x["id"]==3]
print(n[0]["server_index"] if n else "")')
    [ -n "$RAT_SI" ] && { SPAWN_OK="yes"; break; }
  fi
  echo "        (retry $attempt: chicken not yet visible)"
done
if [ "$SPAWN_OK" = "yes" ]; then echo "  PASS  chicken present (server_index=$RAT_SI)"; PASS_N=$((PASS_N+1)); else echo "  FAIL  chicken present"; FAIL_N=$((FAIL_N+1)); fi
if [ -n "$RAT_SI" ]; then
  ATK=$("$VB" attack-npc --server-index "$RAT_SI")
  ok    "attack chicken" "$ATK"
  # Kill THIS instance. Re-issue the attack a few times in case combat dropped or the
  # player was briefly busy; death = the specific server_index leaving the world.
  DEAD="no"
  for round in 1 2 3 4 5 6; do
    D=$("$VB" wait npc-dead --server_index "$RAT_SI" --timeout 8)
    if echo "$D" | grep -q '"matched": *true'; then DEAD="yes"; break; fi
    "$VB" attack-npc --server-index "$RAT_SI" >/dev/null 2>&1 || true
  done
  if [ "$DEAD" = "yes" ]; then echo "  PASS  chicken died"; PASS_N=$((PASS_N+1)); else echo "  FAIL  chicken died"; FAIL_N=$((FAIL_N+1)); fi
else
  echo "  FAIL  attack chicken (no target)"; FAIL_N=$((FAIL_N+1))
fi

# ---- 5. loot: drop then take an item (real ITEM_DROP + GROUND_ITEM_TAKE) ----
echo "[5] loot"
ok    "get bones (item 20)" "$("$VB" admin "::item 20 1")"
matched "bones in inventory" "$("$VB" wait inventory-contains --id 20 --amount 1 --timeout 10)"
SLOT=$("$VB" state inventory | "$PY" -c 'import sys,json
inv=json.load(sys.stdin)["state"]["inventory"]
print(next((i["slot"] for i in inv if i["id"]==20), 0))')
ok    "drop bones" "$("$VB" drop "$SLOT")"
# bones now on the ground at our tile; find and take them back (proves drop+take packets)
matched "bones on ground" "$("$VB" wait ground-item --id 20 --timeout 12 2>/dev/null || echo '{"matched":false}')"
BONE_XY=$("$VB" state ground-items | "$PY" -c 'import sys,json
gs=json.load(sys.stdin)["state"]["ground_items"]
b=[g for g in gs if g["id"]==20]
print(f"{b[0][\"x\"]} {b[0][\"y\"]}" if b else "")' 2>/dev/null || true)
POS=$("$VB" state position | "$PY" -c 'import sys,json;p=json.load(sys.stdin)["state"]["position"];print(p["x"],p["y"])')
[ -z "$BONE_XY" ] && BONE_XY="$POS"
ok    "take bones (GROUND_ITEM_TAKE)" "$("$VB" take-item $BONE_XY 20)"
matched "bones back in inventory" "$("$VB" wait inventory-contains --id 20 --amount 1 --timeout 15)"

# ---- 6. bank (real BANK_DEPOSIT), asserted by bank delta ----
echo "[6] bank"
ok    "spawn coins" "$("$VB" admin "::item 10 500")"
matched "coins in inventory" "$("$VB" wait inventory-contains --id 10 --amount 500 --timeout 10)"
sleep 1.2   # pace admin commands >=1.2s or the second drops (VS-027)
ok    "open bank (::quickbank)" "$("$VB" admin "::quickbank")"
matched "bank open" "$("$VB" wait bank-open --timeout 10)"
BEFORE=$("$VB" state bank | "$PY" -c 'import sys,json
b=json.load(sys.stdin)["state"]["bank"]["items"]
print(sum(i["amount"] for i in b if i["id"]==10))')
ok    "deposit coins" "$("$VB" bank-deposit --id 10 --amount 500)"
sleep 1
AFTER=$("$VB" state bank | "$PY" -c 'import sys,json
b=json.load(sys.stdin)["state"]["bank"]["items"]
print(sum(i["amount"] for i in b if i["id"]==10))')
if [ "$((AFTER - BEFORE))" -ge 500 ]; then
  echo "  PASS  bank coins increased by >=500 ($BEFORE -> $AFTER)"; PASS_N=$((PASS_N+1))
else
  echo "  FAIL  bank coins delta ($BEFORE -> $AFTER, expected +500)"; FAIL_N=$((FAIL_N+1))
fi
"$VB" bank-close >/dev/null

# ---- summary ----
echo "== smoke: $PASS_N passed, $FAIL_N failed =="
[ "$FAIL_N" -eq 0 ]
