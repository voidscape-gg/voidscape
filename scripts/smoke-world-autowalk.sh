#!/usr/bin/env bash
# Headless world-map autowalk regression smoke.
#
# Requires a running local server and an admin QA account. Drives only voidbot
# commands; no screenshots, mouse, or keyboard input.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
VB="$REPO/tools/voidbot/voidbot"
USER="${VOIDBOT_USER:-qabot03}"
PASS="${VOIDBOT_PASS:-voidqa123}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
CTRL_PORT="${VOIDBOT_CTRL_PORT:-18930}"
OUT="${OUT:-$REPO/tmp/world-autowalk-smoke}"
PY="${VOIDBOT_PYTHON:-python3}"

PASS_N=0
FAIL_N=0

mkdir -p "$OUT"

run_vb() {
	VOIDBOT_CTRL_PORT="$CTRL_PORT" VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" "$@"
}

pass() {
	echo "  PASS  $1"
	PASS_N=$((PASS_N + 1))
}

fail() {
	echo "  FAIL  $1"
	[ "${2:-}" ] && echo "        $2"
	FAIL_N=$((FAIL_N + 1))
}

json_get() {
	"$PY" - "$1" "$2" <<'PY'
import json, sys
path, expr = sys.argv[1], sys.argv[2]
data = json.load(open(path, encoding="utf-8"))
for part in expr.split("."):
    if part:
        data = data[part]
print(data)
PY
}

assert_ok() {
	local label="$1" path="$2"
	if grep -q '"ok": *true' "$path"; then pass "$label"; else fail "$label" "$(cat "$path")"; fi
}

assert_matched() {
	local label="$1" path="$2"
	if grep -q '"matched": *true' "$path"; then pass "$label"; else fail "$label" "$(cat "$path")"; fi
}

cleanup() {
	run_vb stop >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "== world autowalk smoke: $USER @ 127.0.0.1:$GAME_PORT ctrl=$CTRL_PORT =="

START="$OUT/00-start.json"
VOIDBOT_CTRL_PORT="$CTRL_PORT" VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" start --user "$USER" --pass "$PASS" > "$START"
assert_ok "daemon started" "$START"
run_vb wait logged-in --timeout 20 > "$OUT/01-logged-in.json"
assert_matched "logged in" "$OUT/01-logged-in.json"

echo "[1] short route ack + arrival"
run_vb admin "::teleport 120 648" > "$OUT/10-teleport.json"
sleep 1.2
run_vb goto 126 648 > "$OUT/11-goto.json"
assert_ok "short goto issued" "$OUT/11-goto.json"
sleep 1
run_vb state world-walk-route > "$OUT/12-route.json"
ROUTE_OK="$(json_get "$OUT/12-route.json" "state.world_walk_route.ok" 2>/dev/null || echo false)"
ROUTE_COUNT="$(json_get "$OUT/12-route.json" "state.world_walk_route.count" 2>/dev/null || echo 0)"
if [ "$ROUTE_OK" = "True" ] && [ "$ROUTE_COUNT" -gt 0 ]; then
	pass "short route ack ok count=$ROUTE_COUNT"
else
	fail "short route ack" "$(cat "$OUT/12-route.json")"
fi
run_vb wait near --x 126 --y 648 --radius 0 --timeout 20 > "$OUT/13-arrival.json"
assert_matched "short route arrived" "$OUT/13-arrival.json"

echo "[2] unreachable target rejects without moving"
run_vb admin "::teleport 120 648" > "$OUT/20-teleport.json"
sleep 1.2
run_vb state position > "$OUT/21-start-pos.json"
run_vb goto 0 0 > "$OUT/22-goto.json"
sleep 1
run_vb state world-walk-route > "$OUT/23-route.json"
run_vb state position > "$OUT/24-final-pos.json"
UNREACH_OK="$(json_get "$OUT/23-route.json" "state.world_walk_route.ok" 2>/dev/null || echo true)"
UNREACH_REASON="$(json_get "$OUT/23-route.json" "state.world_walk_route.reason" 2>/dev/null || echo -1)"
START_POS="$(json_get "$OUT/21-start-pos.json" "state.position.x"),$(json_get "$OUT/21-start-pos.json" "state.position.y")"
FINAL_POS="$(json_get "$OUT/24-final-pos.json" "state.position.x"),$(json_get "$OUT/24-final-pos.json" "state.position.y")"
if [ "$UNREACH_OK" = "False" ] && [ "$UNREACH_REASON" -eq 1 ] && [ "$START_POS" = "$FINAL_POS" ]; then
	pass "unreachable returned reason=1 and did not move"
else
	fail "unreachable rejection" "route=$(cat "$OUT/23-route.json") start=$START_POS final=$FINAL_POS"
fi

echo "[3] new goto cancels stale normal walk"
run_vb admin "::teleport 120 648" > "$OUT/30-teleport.json"
sleep 1.2
run_vb walk-step 145 648 > "$OUT/31-old-walk.json"
sleep 0.2
run_vb goto 116 648 > "$OUT/32-new-goto.json"
assert_ok "replacement goto issued" "$OUT/32-new-goto.json"
sleep 1
run_vb state world-walk-route > "$OUT/33-route.json"
run_vb wait near --x 116 --y 648 --radius 0 --timeout 15 > "$OUT/34-arrival.json"
assert_matched "replacement goto arrived quickly" "$OUT/34-arrival.json"

if [ "${WORLD_AUTOWALK_LONG:-0}" = "1" ]; then
	echo "[4] medium route"
	run_vb admin "::teleport 120 648" > "$OUT/40-teleport.json"
	sleep 1.2
	run_vb goto 89 693 > "$OUT/41-goto.json"
	sleep 1
	run_vb state world-walk-route > "$OUT/42-route.json"
	LONG_COUNT="$(json_get "$OUT/42-route.json" "state.world_walk_route.count" 2>/dev/null || echo 0)"
	if [ "$LONG_COUNT" -gt 40 ]; then pass "medium route count=$LONG_COUNT"; else fail "medium route count" "$(cat "$OUT/42-route.json")"; fi
	run_vb wait near --x 89 --y 693 --radius 1 --timeout 90 > "$OUT/43-arrival.json"
	assert_matched "medium route arrived" "$OUT/43-arrival.json"
fi

echo "== world autowalk smoke: $PASS_N passed, $FAIL_N failed =="
[ "$FAIL_N" -eq 0 ]
