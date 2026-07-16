#!/usr/bin/env bash
# Deterministic end-to-end coverage for the self-opened Christmas cracker.
# Requires a running local server and an admin account. The script records the
# account's initial inventory and removes only the item deltas it creates.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
VB="$REPO/tools/voidbot/voidbot"
USER="${VOIDBOT_USER:-wbtest}"
PASS="${VOIDBOT_PASS:-voidtest123}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
CTRL_PORT="${VOIDBOT_CTRL_PORT:-18900}"
if [ -n "${VOIDBOT_PYTHON:-}" ]; then
	PY="$VOIDBOT_PYTHON"
elif command -v python3 >/dev/null 2>&1; then
	PY=python3
else
	PY=python
fi

BASELINE_JSON=""
MANAGED_IDS=(1 422 575 577 580 677 828 971)

vb() {
	VOIDBOT_CTRL_PORT="$CTRL_PORT" "$VB" "$@"
}

inventory() {
	vb state inventory
}

count_id() {
	local id="$1" json="$2"
	printf '%s' "$json" | "$PY" -c \
		'import json,sys; d=json.load(sys.stdin); wanted=int(sys.argv[1]); print(sum(int(i.get("amount",1)) for i in d["state"]["inventory"] if int(i["id"]) == wanted))' "$id"
}

inventory_size() {
	local json="$1"
	printf '%s' "$json" | "$PY" -c \
		'import json,sys; print(len(json.load(sys.stdin)["state"]["inventory"]))'
}

slot_for() {
	local id="$1" json="$2"
	printf '%s' "$json" | "$PY" -c \
		'import json,sys; d=json.load(sys.stdin); wanted=int(sys.argv[1]); print(next(i["slot"] for i in d["state"]["inventory"] if int(i["id"]) == wanted))' "$id"
}

noted_slot_for() {
	local id="$1" json="$2"
	printf '%s' "$json" | "$PY" -c \
		'import json,sys; d=json.load(sys.stdin); wanted=int(sys.argv[1]); print(next(i["slot"] for i in d["state"]["inventory"] if int(i["id"]) == wanted and i.get("noted")))' "$id"
}

messages_contain() {
	local needle="$1"
	vb state messages | "$PY" -c \
		'import json,sys; needle=sys.argv[1].lower(); d=json.load(sys.stdin); raise SystemExit(0 if any(needle in m.get("text","").lower() for m in d["state"]["messages"]) else 1)' "$needle"
}

expect_eq() {
	local label="$1" actual="$2" expected="$3"
	if [ "$actual" != "$expected" ]; then
		echo "FAIL $label: expected $expected, got $actual" >&2
		exit 1
	fi
	echo "PASS $label"
}

admin() {
	vb admin "$1" >/dev/null
	sleep 1.2
}

queue_fixture() {
	local category_roll="$1" reward_roll="$2"
	admin "::workbenchcracker $category_roll $reward_roll"
	if ! messages_contain "Queued Christmas cracker fixture: category=$category_roll, reward=$reward_roll"; then
		echo "FAIL fixture was not acknowledged: category=$category_roll reward=$reward_roll" >&2
		exit 1
	fi
}

open_and_expect() {
	local label="$1" category_roll="$2" reward_roll="$3" reward_id="$4" message="$5"
	local before after slot before_crackers after_crackers before_reward after_reward
	before="$(inventory)"
	before_crackers="$(count_id 575 "$before")"
	before_reward="$(count_id "$reward_id" "$before")"
	slot="$(slot_for 575 "$before")"
	queue_fixture "$category_roll" "$reward_roll"
	vb item-command "$slot" >/dev/null
	sleep 1
	after="$(inventory)"
	after_crackers="$(count_id 575 "$after")"
	after_reward="$(count_id "$reward_id" "$after")"
	expect_eq "$label consumed one cracker" "$after_crackers" "$((before_crackers - 1))"
	expect_eq "$label awarded item $reward_id" "$after_reward" "$((before_reward + 1))"
	if ! messages_contain "$message"; then
		echo "FAIL $label result message missing: $message" >&2
		exit 1
	fi
	echo "PASS $label result message"
}

open_and_expect_nothing() {
	local before after slot before_crackers after_crackers
	before="$(inventory)"
	before_crackers="$(count_id 575 "$before")"
	slot="$(slot_for 575 "$before")"
	queue_fixture 59 0
	vb item-command "$slot" >/dev/null
	sleep 1
	after="$(inventory)"
	after_crackers="$(count_id 575 "$after")"
	expect_eq "59 stays in the nothing bucket" "$after_crackers" "$((before_crackers - 1))"
	if ! messages_contain "nothing inside"; then
		echo "FAIL nothing result message missing" >&2
		exit 1
	fi
	echo "PASS nothing result message"
}

cleanup() {
	set +e
	if [ -n "$BASELINE_JSON" ]; then
		local current id baseline_count current_count delta
		current="$(inventory 2>/dev/null)"
		if [ -n "$current" ]; then
			for id in "${MANAGED_IDS[@]}"; do
				baseline_count="$(count_id "$id" "$BASELINE_JSON" 2>/dev/null)"
				current_count="$(count_id "$id" "$current" 2>/dev/null)"
				delta=$((current_count - baseline_count))
				if [ "$delta" -gt 0 ]; then
					vb admin "::ritem $id $delta $USER false" >/dev/null 2>&1
					sleep 1.2
					current="$(inventory 2>/dev/null)"
				fi
			done
		fi
	fi
	vb admin "::quit" >/dev/null 2>&1
	vb stop >/dev/null 2>&1
}
trap cleanup EXIT

echo "== Christmas cracker QA: $USER @ 127.0.0.1:$GAME_PORT =="
VOIDBOT_CTRL_PORT="$CTRL_PORT" VOIDBOT_GAME_PORT="$GAME_PORT" \
	"$VB" start --user "$USER" --pass "$PASS" >/dev/null
vb wait logged-in --timeout 20 >/dev/null
BASELINE_JSON="$(inventory)"

# Seven crackers cover five boundary probes, a rapid duplicate attempt, and a
# final full-inventory delivery check.
admin "::item 575 7"
expect_eq "fixture setup" "$(count_id 575 "$(inventory)")" "$(( $(count_id 575 "$BASELINE_JSON") + 7 ))"

open_and_expect_nothing
sleep 6.1
open_and_expect "60 enters the party-hat bucket" 60 0 580 "pink party hat"
sleep 6.1
open_and_expect "79 remains in the party-hat bucket" 79 127 577 "yellow party hat"
sleep 6.1
open_and_expect "80 enters the holiday-rare bucket" 80 0 422 "pumpkin"
sleep 6.1
open_and_expect "99 remains in the holiday-rare bucket" 99 7 971 "Santa's hat"

# Two crackers remain. Repeating the same slot immediately must consume only one
# even though the second cracker shifts into that slot after the first removal.
sleep 6.1
before="$(inventory)"
before_crackers="$(count_id 575 "$before")"
before_mask="$(count_id 828 "$before")"
slot="$(slot_for 575 "$before")"
queue_fixture 80 4
vb item-command "$slot" >/dev/null
vb item-command "$slot" >/dev/null
sleep 1
after="$(inventory)"
expect_eq "rapid duplicate consumes one cracker" "$(count_id 575 "$after")" "$((before_crackers - 1))"
expect_eq "rapid duplicate awards once" "$(count_id 828 "$after")" "$((before_mask + 1))"

# A noted cracker must remain untouched.
admin "::noteditem 575 1"
noted_before="$(inventory)"
noted_slot="$(noted_slot_for 575 "$noted_before")"
noted_count_before="$(count_id 575 "$noted_before")"
vb item-command "$noted_slot" >/dev/null
sleep 1
noted_after="$(inventory)"
expect_eq "noted cracker is rejected" "$(count_id 575 "$noted_after")" "$noted_count_before"
if ! messages_contain "unnote the Christmas cracker"; then
	echo "FAIL noted-cracker guidance missing" >&2
	exit 1
fi
echo "PASS noted-cracker guidance"
# Remove the noted probe specifically; the final unnoted cracker must remain.
admin "::ritem 575 1 $USER true"

# Fill every remaining slot. Opening the last unnoted cracker frees one slot,
# so the Easter egg must still be delivered into a 30-slot inventory.
current="$(inventory)"
filler_count="$((30 - $(inventory_size "$current")))"
if [ "$filler_count" -gt 0 ]; then
	admin "::item 1 $filler_count"
fi
expect_eq "inventory filled" "$(inventory_size "$(inventory)")" 30
sleep 6.1
before="$(inventory)"
before_egg="$(count_id 677 "$before")"
slot="$(slot_for 575 "$before")"
queue_fixture 80 2
vb item-command "$slot" >/dev/null
sleep 1
after="$(inventory)"
expect_eq "full inventory remains at capacity" "$(inventory_size "$after")" 30
expect_eq "full inventory receives reward" "$(count_id 677 "$after")" "$((before_egg + 1))"
expect_eq "all test crackers consumed" "$(count_id 575 "$after")" "$(count_id 575 "$BASELINE_JSON")"

echo "Christmas cracker QA passed."
