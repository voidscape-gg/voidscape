#!/usr/bin/env bash
# Live Void Dungeon acceptance suite. Every game action goes through voidbot.
#
# Phases: active (default), prepare-feature-off, feature-off, prepare-retired, retired.
# The death case follows real death/drop rules and requires VOID_DUNGEON_ALLOW_DEATH=1
# on a disposable account. Optional fixture gaps are explicit SKIPs; set
# VOID_DUNGEON_REQUIRE_FIXTURES=1 to make every skipped cohort release-blocking.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
VB="$REPO/tools/voidbot/voidbot"
PY="${VOIDBOT_PYTHON:-python3}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
CTRL_A="${VOIDBOT_CTRL_PORT:-18900}"
CTRL_B="${VOID_DUNGEON_CTRL_B:-18901}"
CTRL_C="${VOID_DUNGEON_CTRL_C:-18902}"
CTRL_FIXTURE="${VOID_DUNGEON_CTRL_FIXTURE:-18903}"
USER_A="${VOIDBOT_USER:-wbtest}"
PASS_A="${VOIDBOT_PASS:-voidtest123}"
USER_B="${VOID_DUNGEON_USER_B:-}"
PASS_B="${VOID_DUNGEON_PASS_B:-}"
USER_C="${VOID_DUNGEON_USER_C:-}"
PASS_C="${VOID_DUNGEON_PASS_C:-}"
RETIRED_USER="${VOID_DUNGEON_RETIRED_USER:-}"
RETIRED_PASS="${VOID_DUNGEON_RETIRED_PASS:-}"
FEATURE_OFF_USER="${VOID_DUNGEON_FEATURE_OFF_USER:-}"
FEATURE_OFF_PASS="${VOID_DUNGEON_FEATURE_OFF_PASS:-}"
PHASE="${VOID_DUNGEON_PHASE:-active}"
REQUIRE_FIXTURES="${VOID_DUNGEON_REQUIRE_FIXTURES:-0}"
DB_NAME="$(sed -nE 's/^[[:space:]]*db_name:[[:space:]]*([^#[:space:]]+).*/\1/p' "$REPO/server/local.conf" | head -1)"
DB_FILE="${VOID_DUNGEON_DB_FILE:-$REPO/server/inc/sqlite/${DB_NAME}.db}"

PASS_N=0
FAIL_N=0
SKIP_N=0
STARTED_PORTS=()
PRIMARY_MUTATED=0
ORIGINAL_X=""
ORIGINAL_Y=""
BASELINE_COINS=""
LAST_JSON=""

pass() { echo "  PASS  $1"; PASS_N=$((PASS_N + 1)); }
fail() { echo "  FAIL  $1${2:+: $2}"; FAIL_N=$((FAIL_N + 1)); }
skip() { echo "  SKIP  $1${2:+: $2}"; SKIP_N=$((SKIP_N + 1)); }
is_ok() { printf '%s' "$1" | grep -q '"ok": *true'; }
is_matched() { printf '%s' "$1" | grep -q '"matched": *true'; }
expect_ok() { if is_ok "$2"; then pass "$1"; else fail "$1" "$2"; fi; }
expect_matched() { if is_matched "$2"; then pass "$1"; else fail "$1" "$2"; fi; }

vb() {
	local port="$1"
	shift
	VOIDBOT_CTRL_PORT="$port" VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" "$@"
}

start_bot() {
	local label="$1" port="$2" user="$3" passcode="$4"
	LAST_JSON="$(VOIDBOT_CTRL_PORT="$port" VOIDBOT_GAME_PORT="$GAME_PORT" "$VB" start --user "$user" --pass "$passcode" 2>&1)"
	if ! is_ok "$LAST_JSON"; then fail "$label daemon started" "$LAST_JSON"; return 1; fi
	STARTED_PORTS+=("$port")
	pass "$label daemon started"
	LAST_JSON="$(vb "$port" wait logged-in --timeout 20 2>&1)"
	if ! is_matched "$LAST_JSON"; then fail "$label logged in" "$LAST_JSON"; return 1; fi
	pass "$label logged in"
	# LOGIN success arrives before the initial inventory/stats snapshots.
	sleep 2
}

restart_primary() {
	local label="$1"
	vb "$CTRL_A" stop >/dev/null 2>&1 || true
	sleep 1
	start_bot "$label" "$CTRL_A" "$USER_A" "$PASS_A"
}

admin_a() {
	LAST_JSON="$(vb "$CTRL_A" admin "$1" 2>&1)"
	sleep 1.2
}

coin_count() {
	vb "$1" state inventory | "$PY" -c 'import json,sys; inv=json.load(sys.stdin)["state"]["inventory"]; print(sum(int(i.get("amount",1)) for i in inv if int(i["id"])==10))'
}

position_field() {
	vb "$1" state position | "$PY" -c 'import json,sys; print(json.load(sys.stdin)["state"]["position"][sys.argv[1]])' "$2"
}

dialog_has() {
	vb "$CTRL_A" state dialog | "$PY" -c 'import json,sys; raise SystemExit(0 if sys.argv[1] in json.load(sys.stdin)["state"]["dialog"]["options"] else 1)' "$1"
}

cache_status() {
	local key="$1"
	admin_a "::getcache $USER_A $key"
	vb "$CTRL_A" state messages | "$PY" -c 'import json,sys; key=sys.argv[1].lower(); rows=[m.get("text","") for m in json.load(sys.stdin)["state"]["messages"] if key in m.get("text","").lower()]; print("unknown" if not rows else "absent" if "does not have" in rows[-1].lower() else "present" if "has value" in rows[-1].lower() else "unknown")' "$key"
}

database_dungeon_cache_count() {
	"$PY" - "$DB_FILE" "$USER_A" <<'PY'
import sqlite3
import sys

database, username = sys.argv[1:]
with sqlite3.connect("file:" + database + "?mode=ro", uri=True, timeout=5) as connection:
    row = connection.execute(
        """SELECT count(*)
           FROM player_cache cache
           JOIN players player ON player.id = cache.playerID
           WHERE lower(player.username) = lower(?)
             AND cache.key IN ('void_dungeon_admission', 'void_dungeon_depth')""",
        (username,),
    ).fetchone()
print(int(row[0]))
PY
}

clear_admission() {
	admin_a "::deletecache $USER_A void_dungeon_admission"
	admin_a "::deletecache $USER_A void_dungeon_depth"
}

restore_primary() {
	[ "$PRIMARY_MUTATED" -eq 1 ] || return 0
	vb "$CTRL_A" ping >/dev/null 2>&1 || return 0
	clear_admission
	if [ -n "$BASELINE_COINS" ]; then
		local current delta
		current="$(coin_count "$CTRL_A" 2>/dev/null || echo "$BASELINE_COINS")"
		delta=$((current - BASELINE_COINS))
		if [ "$delta" -gt 0 ]; then admin_a "::ritem 10 $delta $USER_A false"; fi
		if [ "$delta" -lt 0 ]; then admin_a "::item 10 $((-delta))"; fi
	fi
	if [ -n "$ORIGINAL_X" ] && [ -n "$ORIGINAL_Y" ]; then admin_a "::teleport $ORIGINAL_X $ORIGINAL_Y"; fi
}

cleanup() {
	set +e
	restore_primary
	local index port
	for ((index=${#STARTED_PORTS[@]}-1; index>=0; index--)); do
		port="${STARTED_PORTS[$index]}"
		vb "$port" stop >/dev/null 2>&1 || true
	done
}
trap cleanup EXIT

operate_and_choose() {
	local x="$1" y="$2" object_id="$3" option="$4" expected="$5" target_x="$6" target_y="$7" label="$8"
	LAST_JSON="$(vb "$CTRL_A" object-action "$x" "$y" --id "$object_id" 2>&1)"; expect_ok "$label action" "$LAST_JSON"
	LAST_JSON="$(vb "$CTRL_A" wait dialog-open --timeout 20 2>&1)"; expect_matched "$label menu opened" "$LAST_JSON"
	if dialog_has "$expected"; then pass "$label menu contract"; else fail "$label menu contract" "missing '$expected'"; fi
	LAST_JSON="$(vb "$CTRL_A" menu-reply "$option" 2>&1)"; expect_ok "$label choice sent" "$LAST_JSON"
	LAST_JSON="$(vb "$CTRL_A" wait position --x "$target_x" --y "$target_y" --timeout 25 2>&1)"; expect_matched "$label destination" "$LAST_JSON"
}

enter_surface() {
	admin_a "::teleport 111 296"
	operate_and_choose 112 296 1306 "$1" "$2" "$3" "$4" "$5"
}

exit_rift() { operate_and_choose "$1" "$2" 1306 0 "Leave the Void Dungeon" 111 297 "$3"; }

use_ladder() {
	LAST_JSON="$(vb "$CTRL_A" object-action "$1" "$2" --id "$3" 2>&1)"; expect_ok "$6 action" "$LAST_JSON"
	LAST_JSON="$(vb "$CTRL_A" wait position --x "$4" --y "$5" --timeout 20 2>&1)"; expect_matched "$6 destination" "$LAST_JSON"
}

player_index_at() {
	vb "$1" state players | "$PY" -c 'import json,sys; x,y=int(sys.argv[1]),int(sys.argv[2]); p=[p for p in json.load(sys.stdin)["state"]["players"] if int(p["x"])==x and int(p["y"])==y]; print(p[0]["server_index"] if p else "")' "$2" "$3"
}

run_npc_drop() {
	echo "[active] Void NPC and production drop path"
	admin_a "::setstat 90 attack"; admin_a "::setstat 90 strength"; admin_a "::setstat 90 hits"; admin_a "::heal"
	admin_a "::teleport 71 3240"
	LAST_JSON="$(vb "$CTRL_A" wait npc-present --id 854 --timeout 20 2>&1)"; expect_matched "Void spider roster is live" "$LAST_JSON"
	local attempt npc_index before after found=0
	for attempt in 1 2 3 4 5; do
		npc_index="$(vb "$CTRL_A" state npcs | "$PY" -c 'import json,sys; n=[x for x in json.load(sys.stdin)["state"]["npcs"] if int(x["id"])==854]; print(n[0]["server_index"] if n else "")')"
		[ -n "$npc_index" ] || { sleep 2; continue; }
		before="$(vb "$CTRL_A" state ground-items)"
		LAST_JSON="$(vb "$CTRL_A" attack-npc --server-index "$npc_index" 2>&1)"
		if is_ok "$LAST_JSON"; then pass "Void spider real attack packet (attempt $attempt)"; else fail "Void spider real attack packet" "$LAST_JSON"; break; fi
		sleep 2
		admin_a "::killnpc 854"
		vb "$CTRL_A" wait npc-dead --server_index "$npc_index" --timeout 12 >/dev/null 2>&1 || true
		after="$(vb "$CTRL_A" state ground-items)"
		if BEFORE_JSON="$before" AFTER_JSON="$after" "$PY" -c 'import json,os; allowed={10,38,40,41,159,160,219,220,1604}; rows=lambda raw:{(int(g["id"]),int(g["x"]),int(g["y"]),int(g.get("amount",1))) for g in json.loads(raw)["state"]["ground_items"]}; new=rows(os.environ["AFTER_JSON"])-rows(os.environ["BEFORE_JSON"]); raise SystemExit(0 if any(r[0] in allowed for r in new) else 1)'; then
			found=1; pass "Void spider produced a configured visible drop"; break
		fi
		admin_a "::teleport 71 3240"; sleep 1
	done
	[ "$found" -eq 1 ] || fail "Void spider produced a configured visible drop" "five rolls were empty or not visible"
}

run_one_on_one() {
	if [ -z "$USER_B" ] || [ -z "$PASS_B" ] || [ -z "$USER_C" ] || [ -z "$PASS_C" ]; then
		skip "three-player one-on-one live gate" "set VOID_DUNGEON_USER_B/PASS_B and USER_C/PASS_C"; return
	fi
	echo "[active] three-player one-on-one gate"
	start_bot "player B" "$CTRL_B" "$USER_B" "$PASS_B" || return
	sleep 0.7
	start_bot "player C" "$CTRL_C" "$USER_C" "$PASS_C" || return
	admin_a "::setcache $USER_B void_path 1"; admin_a "::setcache $USER_C void_path 1"
	admin_a "::setstat $USER_B 30"; admin_a "::setstat $USER_C 30"
	admin_a "::teleport $USER_B 69 3252"; admin_a "::teleport $USER_C 71 3252"; admin_a "::teleport 73 3252"
	sleep 3
	local c_for_b c_for_a
	c_for_b="$(player_index_at "$CTRL_B" 71 3252)"; c_for_a="$(player_index_at "$CTRL_A" 71 3252)"
	if [ -z "$c_for_b" ] || [ -z "$c_for_a" ]; then fail "voidbot resolved player server indexes" "B='$c_for_b' A='$c_for_a'"; return; fi
	pass "voidbot resolved player server indexes"
	LAST_JSON="$(vb "$CTRL_B" attack-player --server-index "$c_for_b" 2>&1)"; expect_ok "first player initiated dungeon PvP" "$LAST_JSON"
	sleep 3
	LAST_JSON="$(vb "$CTRL_A" attack-player --server-index "$c_for_a" 2>&1)"; expect_ok "third player attack packet sent" "$LAST_JSON"
	LAST_JSON="$(vb "$CTRL_A" wait message --regex 'only allows one-on-one player fights' --timeout 12 2>&1)"; expect_matched "third player was rejected by one-on-one gate" "$LAST_JSON"
	admin_a "::teleport $USER_B 111 297"; admin_a "::teleport $USER_C 111 297"
}

run_death_reset() {
	if [ "${VOID_DUNGEON_ALLOW_DEATH:-0}" != "1" ]; then
		skip "real dungeon-death reset and recharge" "set VOID_DUNGEON_ALLOW_DEATH=1 with a disposable account"; return
	fi
	echo "[active] real dungeon death, reset, and recharge"
	local other_items since coins cache_rows deadline
	other_items="$(vb "$CTRL_A" state inventory | "$PY" -c 'import json,sys; print(sum(1 for i in json.load(sys.stdin)["state"]["inventory"] if int(i["id"])!=10))')"
	[ "$other_items" -eq 0 ] || echo "        warning: $other_items non-coin slots follow normal death/drop rules"
	if [ "$BASELINE_COINS" -gt 0 ]; then admin_a "::ritem 10 $BASELINE_COINS $USER_A false"; fi
	admin_a "::teleport 72 3252"
	cache_rows="$(database_dungeon_cache_count 2>/dev/null || echo -1)"
	if [ "$cache_rows" -gt 0 ]; then pass "pre-death admission is durably present"; else fail "pre-death admission is durably present" "rows=$cache_rows db=$DB_FILE"; fi
	since="$(vb "$CTRL_A" events --since 0 | "$PY" -c 'import json,sys; print(json.load(sys.stdin).get("last",0))')"
	admin_a "::kill $USER_A"; sleep 4
	if vb "$CTRL_A" events --since "$since" | "$PY" -c 'import json,sys; raise SystemExit(0 if any(e.get("kind")=="death" for e in json.load(sys.stdin).get("events",[])) else 1)'; then pass "real dungeon death packet observed"; else fail "real dungeon death packet observed"; fi
	deadline=$((SECONDS + 20))
	cache_rows="$(database_dungeon_cache_count 2>/dev/null || echo -1)"
	while [ "$cache_rows" -ne 0 ] && [ "$SECONDS" -lt "$deadline" ]; do
		sleep 0.5
		cache_rows="$(database_dungeon_cache_count 2>/dev/null || echo -1)"
	done
	LAST_JSON="$(vb "$CTRL_A" wait logged-in --timeout 1 2>&1)"
	if [ "$cache_rows" -eq 0 ] && is_matched "$LAST_JSON"; then
		pass "death cache removal is durable before logout in the same session"
	else
		fail "death cache removal is durable before logout in the same session" "rows=$cache_rows session=$LAST_JSON db=$DB_FILE"
	fi
	restart_primary "post-death persisted session" || return
	admin_a "::item 10 100000"
	enter_surface 0 "Enter the Void Dungeon" 72 3252 "post-death paid re-entry"
	coins="$(coin_count "$CTRL_A")"
	if [ "$coins" -eq 0 ]; then pass "post-death admission charged a fresh 100,000"; else fail "post-death admission charged a fresh 100,000" "coins=$coins"; fi
	exit_rift 68 3252 "post-death exit"
}

run_active() {
	echo "== Void Dungeon live suite: active feature @ 127.0.0.1:$GAME_PORT =="
	start_bot "primary" "$CTRL_A" "$USER_A" "$PASS_A" || return
	ORIGINAL_X="$(position_field "$CTRL_A" x)"; ORIGINAL_Y="$(position_field "$CTRL_A" y)"
	if [ "${VOID_DUNGEON_DISPOSABLE:-0}" = "1" ]; then
		admin_a "::wipeinventory $USER_A"
		if [ "$(vb "$CTRL_A" state inventory | "$PY" -c 'import json,sys; print(len(json.load(sys.stdin)["state"]["inventory"]))')" -eq 0 ]; then pass "disposable inventory cleared"; else fail "disposable inventory cleared"; return; fi
	fi
	BASELINE_COINS="$(coin_count "$CTRL_A")"
	local admission_status depth_status coins
	admission_status="$(cache_status void_dungeon_admission)"; depth_status="$(cache_status void_dungeon_depth)"
	if [ "$admission_status" != "absent" ] || [ "$depth_status" != "absent" ]; then
		skip "active admission suite" "use a clean account (admission=$admission_status depth=$depth_status)"; return
	fi
	PRIMARY_MUTATED=1
	admin_a "::item 10 100000"
	LAST_JSON="$(vb "$CTRL_A" wait inventory-contains --id 10 --amount $((BASELINE_COINS + 100000)) --timeout 12 2>&1)"; expect_matched "100,000 coin entry fixture" "$LAST_JSON"
	enter_surface 0 "Enter the Void Dungeon" 72 3252 "paid first entry"
	coins="$(coin_count "$CTRL_A")"
	if [ "$coins" -eq "$BASELINE_COINS" ]; then pass "first entry charged exactly 100,000"; else fail "first entry charged exactly 100,000" "coins=$coins baseline=$BASELINE_COINS"; fi
	if [ "$(cache_status void_dungeon_admission)" = "present" ]; then pass "admission cache committed"; else fail "admission cache committed"; fi

	exit_rift 68 3252 "Riftworks exit"
	restart_primary "persisted-admission session" || return
	enter_surface 0 "Enter the Riftworks" 72 3252 "free re-entry"
	coins="$(coin_count "$CTRL_A")"
	if [ "$coins" -eq "$BASELINE_COINS" ]; then pass "free re-entry charged no coins"; else fail "free re-entry charged no coins" "coins=$coins"; fi

	admin_a "::teleport 72 3198"
	use_ladder 72 3197 5 72 2308 "Riftworks ladder up"
	use_ladder 72 2308 6 72 3197 "Menagerie ladder down"
	use_ladder 72 3197 5 72 2308 "Menagerie unlock climb"
	admin_a "::teleport 72 2254"
	use_ladder 72 2253 5 72 1364 "Menagerie ladder up"
	use_ladder 72 1364 6 72 2253 "Sanctum ladder down"

	admin_a "::teleport 72 2308"; exit_rift 76 2308 "Broken Menagerie exit"
	enter_surface 1 "Resume at the Broken Menagerie" 72 2308 "Menagerie shortcut"
	exit_rift 76 2308 "Menagerie shortcut exit"
	enter_surface 2 "Resume at the Null Sanctum" 72 1364 "Null Sanctum shortcut"
	exit_rift 76 1364 "Null Sanctum exit"
	coins="$(coin_count "$CTRL_A")"
	if [ "$coins" -eq "$BASELINE_COINS" ]; then pass "all shortcuts remained free"; else fail "all shortcuts remained free" "coins=$coins"; fi

	enter_surface 0 "Enter the Riftworks" 72 3252 "NPC test entry"
	run_npc_drop
	run_one_on_one
	run_death_reset
}

prepare_saved_location() {
	local fixture_user="$1" fixture_pass="$2" x="$3" y="$4" label="$5"
	if [ -z "$fixture_user" ]; then skip "$label" "fixture username is not set"; return; fi
	start_bot "preparer" "$CTRL_A" "$USER_A" "$PASS_A" || return
	# Newly registered accounts have lastLogin=0, which intentionally overrides any
	# pre-login DB location with the first-time spawn. Warm the fixture once first.
	if [ -n "$fixture_pass" ]; then
		start_bot "$label warm-up" "$CTRL_FIXTURE" "$fixture_user" "$fixture_pass" || return
		vb "$CTRL_FIXTURE" stop >/dev/null 2>&1 || true
		sleep 2
	fi
	local attempt
	for attempt in $(seq 1 15); do
		admin_a "::teleport $fixture_user $x $y"
		if vb "$CTRL_A" state messages | "$PY" -c 'import json,sys; needle=sys.argv[1].lower(); raise SystemExit(0 if any(needle in m.get("text","").lower() for m in json.load(sys.stdin)["state"]["messages"]) else 1)' "teleported $fixture_user"; then
			break
		fi
		sleep 1
	done
	if [ "$attempt" -le 15 ] && vb "$CTRL_A" state messages | "$PY" -c 'import json,sys; needle=sys.argv[1].lower(); raise SystemExit(0 if any(needle in m.get("text","").lower() for m in json.load(sys.stdin)["state"]["messages"]) else 1)' "teleported $fixture_user"; then
		pass "$label saved at ($x,$y)"
	else
		fail "$label saved at ($x,$y)" "account must exist and be offline"
	fi
}

run_login_recovery() {
	local user="$1" passcode="$2" expected_x="$3" expected_y="$4" regex="$5" label="$6"
	if [ -z "$user" ] || [ -z "$passcode" ]; then skip "$label" "fixture credentials are not set"; return; fi
	start_bot "$label" "$CTRL_FIXTURE" "$user" "$passcode" || return
	LAST_JSON="$(vb "$CTRL_FIXTURE" wait position --x "$expected_x" --y "$expected_y" --timeout 20 2>&1)"; expect_matched "$label destination" "$LAST_JSON"
	if vb "$CTRL_FIXTURE" state messages | "$PY" -c 'import json,re,sys; rx=re.compile(sys.argv[1]); raise SystemExit(0 if any(rx.search(m.get("text","")) for m in json.load(sys.stdin)["state"]["messages"]) else 1)' "$regex"; then pass "$label explanation"; else fail "$label explanation" "login message absent"; fi
}

run_feature_off() {
	echo "== Void Dungeon live suite: feature-off recovery =="
	run_login_recovery "$FEATURE_OFF_USER" "$FEATURE_OFF_PASS" 111 297 'dormant Void Dungeon releases you' "feature-off underground login"
	if [ -n "$FEATURE_OFF_USER" ] && [ -n "$FEATURE_OFF_PASS" ]; then
		if vb "$CTRL_FIXTURE" state npcs | "$PY" -c 'import json,sys; ids={int(n["id"]) for n in json.load(sys.stdin)["state"]["npcs"]}; raise SystemExit(0 if not ids.intersection(range(853,861)) else 1)'; then pass "feature-off world exposes no Void Dungeon NPC roster"; else fail "feature-off world exposes no Void Dungeon NPC roster"; fi
	fi
}

case "$PHASE" in
	active)
		run_active
		;;
	pvp)
		echo "== Void Dungeon live suite: isolated three-player gate =="
		if start_bot "primary" "$CTRL_A" "$USER_A" "$PASS_A"; then run_one_on_one; fi
		;;
	prepare-feature-off)
		echo "== preparing feature-off login fixture =="
		prepare_saved_location "$FEATURE_OFF_USER" "$FEATURE_OFF_PASS" 72 3252 "feature-off fixture"
		;;
	feature-off)
		run_feature_off
		;;
	prepare-retired)
		echo "== preparing retired-floor login fixture =="
		prepare_saved_location "$RETIRED_USER" "$RETIRED_PASS" 110 1360 "retired-floor fixture"
		;;
	retired)
		echo "== Void Dungeon live suite: retired-floor recovery =="
		run_login_recovery "$RETIRED_USER" "$RETIRED_PASS" 72 1364 'retired threshold folds back into the Null Sanctum' "retired-floor login"
		;;
	*)
		fail "known VOID_DUNGEON_PHASE" "$PHASE"
		;;
esac

echo "== Void Dungeon live suite: $PASS_N passed, $FAIL_N failed, $SKIP_N skipped =="
if [ "$SKIP_N" -gt 0 ]; then echo "Fixture gaps are explicit SKIPs. Set VOID_DUNGEON_REQUIRE_FIXTURES=1 to make them release-blocking."; fi
if [ "$REQUIRE_FIXTURES" = "1" ] && [ "$SKIP_N" -gt 0 ]; then FAIL_N=$((FAIL_N + SKIP_N)); fi
[ "$FAIL_N" -eq 0 ]
