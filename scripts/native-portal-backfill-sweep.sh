#!/usr/bin/env bash
set -euo pipefail

url="${PORTAL_NATIVE_BACKFILL_URL:-http://127.0.0.1:8788/api/admin/accounts/backfill-native}"
token="${PORTAL_ADMIN_TOKEN:-}"

if [[ -z "$token" ]]; then
	echo "PORTAL_ADMIN_TOKEN is required" >&2
	exit 2
fi

dry_run="$(
	curl -fsS -X POST "$url" \
		-H "x-portal-admin-token: $token" \
		-H "content-type: application/json" \
		-d '{"dryRun":true}'
)"

set +e
summary="$(
	node -e '
const payload = JSON.parse(process.argv[1]);
const pending = Number(payload.createdAccounts || 0)
	+ Number(payload.createdCharacters || 0)
	+ Number(payload.updatedCharacters || 0)
	+ Number(payload.linkedPlayers || 0)
	+ Number(payload.starterCardsGranted || 0)
	+ Number(payload.subscriptionsMigrated || 0);
const conflicts = Array.isArray(payload.conflicts) ? payload.conflicts.length : 0;
console.log(JSON.stringify({
	players: payload.players,
	alreadyLinked: payload.alreadyLinked,
	pending,
	createdAccounts: payload.createdAccounts,
	createdCharacters: payload.createdCharacters,
	updatedCharacters: payload.updatedCharacters,
	linkedPlayers: payload.linkedPlayers,
	starterCardsGranted: payload.starterCardsGranted,
	subscriptionsMigrated: payload.subscriptionsMigrated,
	conflicts
}));
if (conflicts) process.exit(2);
process.exit(pending > 0 ? 3 : 0);
' "$dry_run"
)"
status=$?
set -e

echo "native portal backfill dry-run: $summary"

case "$status" in
	0)
		exit 0
		;;
	3)
		;;
	*)
		exit "$status"
		;;
esac

applied="$(
	curl -fsS -X POST "$url" \
		-H "x-portal-admin-token: $token" \
		-H "content-type: application/json" \
		-d '{}'
)"

node -e '
const payload = JSON.parse(process.argv[1]);
const conflicts = Array.isArray(payload.conflicts) ? payload.conflicts.length : 0;
console.log("native portal backfill applied: " + JSON.stringify({
	players: payload.players,
	createdAccounts: payload.createdAccounts,
	createdCharacters: payload.createdCharacters,
	updatedCharacters: payload.updatedCharacters,
	linkedPlayers: payload.linkedPlayers,
	starterCardsGranted: payload.starterCardsGranted,
	subscriptionsMigrated: payload.subscriptionsMigrated,
	conflicts
}));
if (conflicts) process.exit(2);
' "$applied"
