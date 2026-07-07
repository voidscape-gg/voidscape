#!/usr/bin/env bash
# Headless world-map autowalk route-quality checks.
#
# Requires a running local server and an admin QA account. This verifies the
# vendored waypoint graph and inspects WORLD_WALK_ROUTE acks through voidbot;
# it does not depend on screenshots, mouse input, or exact route snapshots.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
VB="$REPO/tools/voidbot/voidbot"
WAYPOINT_FILE="$REPO/server/conf/server/data/waypoints.rev"
USER="${VOIDBOT_USER:-qabot03}"
PASS="${VOIDBOT_PASS:-voidqa123}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
CTRL_PORT="${VOIDBOT_CTRL_PORT:-18932}"
OUT="${OUT:-$REPO/tmp/world-autowalk-routes}"
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

capture_route_event() {
	local since="$1" out="$2"
	local events="$out.events"
	for _ in $(seq 1 30); do
		run_vb events --since "$since" > "$events"
		if "$PY" - "$events" "$out" <<'PY'
import json, sys
events_path, out_path = sys.argv[1], sys.argv[2]
data = json.load(open(events_path, encoding="utf-8"))
for event in data.get("events", []):
    if event.get("kind") != "world_walk_route":
        continue
    ack = {
        "ok": bool(event.get("ok")),
        "reason": event.get("reason"),
        "count": event.get("count"),
        "route": event.get("route") or [],
    }
    json.dump({"ok": True, "state": {"world_walk_route": ack}}, open(out_path, "w", encoding="utf-8"), sort_keys=True)
    raise SystemExit(0)
raise SystemExit(1)
PY
		then
			return 0
		fi
		sleep 0.2
	done
	echo '{"ok":false,"error":"no world_walk_route event after goto"}' > "$out"
	return 1
}

cleanup() {
	run_vb stop >/dev/null 2>&1 || true
}
trap cleanup EXIT

check_waypoints() {
	local summary="$OUT/00-waypoints.json"
	if "$PY" - "$WAYPOINT_FILE" > "$summary" <<'PY'
import json, struct, sys, zipfile

path = sys.argv[1]
with zipfile.ZipFile(path) as zf:
    names = set(zf.namelist())
    missing = {"nodes.dat", "edges.dat"} - names
    if missing:
        raise SystemExit(f"missing entries: {sorted(missing)}")
    nodes = zf.read("nodes.dat")
    edges = zf.read("edges.dat")

node_count = struct.unpack(">I", nodes[:4])[0]
edge_count = struct.unpack(">I", edges[:4])[0]
if node_count < 10000 or edge_count < 50000:
    raise SystemExit(f"unexpectedly small graph: nodes={node_count} edges={edge_count}")

print(json.dumps({
    "ok": True,
    "path": path,
    "nodes": node_count,
    "undirected_edges": edge_count,
    "directed_edges": edge_count * 2,
}, sort_keys=True))
PY
	then
		pass "vendored waypoint graph present ($(cat "$summary"))"
	else
		fail "vendored waypoint graph invalid" "$(cat "$summary" 2>/dev/null || true)"
	fi
}

check_route() {
	local name="$1" sx="$2" sy="$3" tx="$4" ty="$5" max_ratio="$6" radius="$7"
	local dir="$OUT/$name"
	mkdir -p "$dir"

	echo "[$name] $sx,$sy -> $tx,$ty ratio<=$max_ratio"
	run_vb admin "::teleport $sx $sy" > "$dir/01-teleport.json"
	run_vb wait near --x "$sx" --y "$sy" --radius 0 --timeout 10 > "$dir/02-start.json"
	run_vb events --since 0 > "$dir/03-before-events.json"
	local since
	since="$(json_get "$dir/03-before-events.json" "last" 2>/dev/null || echo 0)"
	run_vb goto "$tx" "$ty" > "$dir/04-goto.json"
	capture_route_event "$since" "$dir/05-route.json"

	if "$PY" - "$dir/05-route.json" "$sx" "$sy" "$tx" "$ty" "$max_ratio" "$radius" > "$dir/06-analysis.json" <<'PY'
import json, math, sys

route_path, sx, sy, tx, ty, max_ratio, radius = sys.argv[1:]
sx, sy, tx, ty, radius = map(int, (sx, sy, tx, ty, radius))
max_ratio = float(max_ratio)

data = json.load(open(route_path, encoding="utf-8"))
state = data.get("state", {})
ack = state.get("world_walk_route") or data.get("world_walk_route")
if not isinstance(ack, dict):
    raise SystemExit("missing world_walk_route state")
if not ack.get("ok"):
    raise SystemExit(f"route rejected: reason={ack.get('reason')}")

route = ack.get("route") or []
if not route:
    raise SystemExit("empty route")

def point_xy(p):
    return int(p["x"]), int(p["y"])

first = point_xy(route[0])
last = point_xy(route[-1])
if max(abs(first[0] - sx), abs(first[1] - sy)) > 1:
    raise SystemExit(f"first step {first} is not adjacent to start {(sx, sy)}")
if max(abs(last[0] - tx), abs(last[1] - ty)) > radius:
    raise SystemExit(f"last step {last} is not within radius {radius} of target {(tx, ty)}")

prev = (sx, sy)
seen = {prev}
loops = []
for idx, raw in enumerate(route, start=1):
    cur = point_xy(raw)
    dx = abs(cur[0] - prev[0])
    dy = abs(cur[1] - prev[1])
    if max(dx, dy) > 1:
        raise SystemExit(f"non-contiguous step {idx}: {prev} -> {cur}")
    if cur in seen:
        loops.append((idx, cur))
    seen.add(cur)
    prev = cur
if loops:
    raise SystemExit(f"route revisits tiles: {loops[:5]}")

lower_bound = max(abs(tx - sx), abs(ty - sy))
max_allowed = int(math.ceil(lower_bound * max_ratio + 16))
count = len(route)
if lower_bound > 0 and count > max_allowed:
    raise SystemExit(
        f"route too long: count={count} lower_bound={lower_bound} "
        f"max_allowed={max_allowed} ratio={count / lower_bound:.2f}"
    )

print(json.dumps({
    "ok": True,
    "count": count,
    "lower_bound": lower_bound,
    "ratio": None if lower_bound == 0 else round(count / lower_bound, 3),
    "max_allowed": max_allowed,
    "first": {"x": first[0], "y": first[1]},
    "last": {"x": last[0], "y": last[1]},
}, sort_keys=True))
PY
	then
		pass "$name route quality $(cat "$dir/06-analysis.json")"
	else
		fail "$name route quality" "$(cat "$dir/06-analysis.json" 2>/dev/null || cat "$dir/05-route.json")"
	fi
}

echo "== world autowalk route quality: $USER @ 127.0.0.1:$GAME_PORT ctrl=$CTRL_PORT =="

check_waypoints

START="$OUT/01-start.json"
VOIDBOT_CTRL_PORT="$CTRL_PORT" VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" start --user "$USER" --pass "$PASS" > "$START"
if grep -q '"ok": *true' "$START"; then pass "daemon started"; else fail "daemon started" "$(cat "$START")"; fi
run_vb wait logged-in --timeout 20 > "$OUT/02-logged-in.json"
if grep -q '"matched": *true' "$OUT/02-logged-in.json"; then pass "logged in"; else fail "logged in" "$(cat "$OUT/02-logged-in.json")"; fi

check_route "lumbridge_short" 120 648 126 648 3.00 0
check_route "lumbridge_draynor" 120 648 122 509 1.75 1
check_route "lumbridge_west_field" 120 648 89 693 2.30 1
check_route "draynor_falador" 122 509 304 542 2.00 1
check_route "edgeville_lumbridge" 217 449 120 648 2.20 1

echo "== world autowalk route quality: $PASS_N passed, $FAIL_N failed =="
[ "$FAIL_N" -eq 0 ]
