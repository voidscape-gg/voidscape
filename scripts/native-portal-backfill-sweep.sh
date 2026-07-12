#!/usr/bin/env bash
set -euo pipefail

url="${PORTAL_NATIVE_BACKFILL_URL:-http://127.0.0.1:8788/api/admin/accounts/backfill-native}"
token="${PORTAL_ADMIN_TOKEN:-}"
mode="${1:---dry-run}"
review_token="${PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN:-}"
approved_exceptions="${PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS:-}"
excluded_exceptions="${PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS:-}"
grant_launch_character_cards="${PORTAL_NATIVE_BACKFILL_GRANT_LAUNCH_CHARACTER_CARDS:-1}"
snapshot_as_of_ms="${PORTAL_NATIVE_BACKFILL_AS_OF_MS:-}"

case "$mode" in
	--dry-run|--apply)
		;;
	*)
		echo "usage: $0 [--dry-run|--apply]" >&2
		exit 2
		;;
esac

if [[ -z "$token" ]]; then
	echo "PORTAL_ADMIN_TOKEN is required" >&2
	exit 2
fi

if [[ "$grant_launch_character_cards" != "0" && "$grant_launch_character_cards" != "1" ]]; then
	echo "PORTAL_NATIVE_BACKFILL_GRANT_LAUNCH_CHARACTER_CARDS must be 0 or 1" >&2
	exit 2
fi

if [[ -n "$snapshot_as_of_ms" ]] && ! [[ "$snapshot_as_of_ms" =~ ^[1-9][0-9]*$ ]]; then
	echo "PORTAL_NATIVE_BACKFILL_AS_OF_MS must be a positive integer from the reviewed dry-run" >&2
	exit 2
fi
if [[ "$mode" == "--apply" && -z "$snapshot_as_of_ms" ]]; then
	echo "PORTAL_NATIVE_BACKFILL_AS_OF_MS is required for --apply; copy cohort.asOfMs from the reviewed dry-run" >&2
	exit 2
fi

approved_json="$({
	node -e '
const values = String(process.argv[1] || "").split(",").map((value) => value.trim()).filter(Boolean);
const ids = values.map(Number);
if (ids.some((id) => !Number.isInteger(id) || id <= 0)) {
	throw new Error("PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS must be comma-separated positive player IDs");
}
process.stdout.write(JSON.stringify([...new Set(ids)].sort((a, b) => a - b)));
' "$approved_exceptions"
} 2>&1)" || {
	echo "$approved_json" >&2
	exit 2
}
excluded_json="$({
	node -e '
const values = String(process.argv[1] || "").split(",").map((value) => value.trim()).filter(Boolean);
const ids = values.map(Number);
if (ids.some((id) => !Number.isInteger(id) || id <= 0)) {
	throw new Error("PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS must be comma-separated positive player IDs");
}
process.stdout.write(JSON.stringify([...new Set(ids)].sort((a, b) => a - b)));
' "$excluded_exceptions"
} 2>&1)" || {
	echo "$excluded_json" >&2
	exit 2
}
dry_payload="$(node -e '
process.stdout.write(JSON.stringify({
	approvedExceptionPlayerIds: JSON.parse(process.argv[1]),
	excludedExceptionPlayerIds: JSON.parse(process.argv[2]),
	grantLaunchCharacterCards: process.argv[3] !== "0",
	...(process.argv[4] ? { asOfMs: Number(process.argv[4]) } : {})
}));
' "$approved_json" "$excluded_json" "$grant_launch_character_cards" "$snapshot_as_of_ms")"

dry_run="$(
	curl -fsS -X POST "$url" \
		-H "x-portal-admin-token: $token" \
		-H "content-type: application/json" \
		-d "$dry_payload"
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
	+ Number(payload.launchCharacterCardsSeeded || 0)
	+ (payload.launchCharacterCardCutoverWillSeal ? 1 : 0)
	+ Number(payload.subscriptionsMigrated || 0);
const conflicts = Array.isArray(payload.conflicts) ? payload.conflicts.length : 0;
const cohort = payload.cohort || {};
console.log(JSON.stringify({
	players: payload.players,
	cohortPolicy: cohort.policy,
	reviewToken: cohort.reviewToken,
	asOfMs: cohort.asOfMs,
	asOf: cohort.asOf,
	cohortReady: cohort.ready === true,
	eligiblePlayers: cohort.eligiblePlayers,
	excludedPlayers: cohort.excludedPlayers,
	ambiguousPlayers: cohort.ambiguousPlayers,
	approvedExceptions: cohort.approvedExceptions,
	explicitlyExcludedExceptions: cohort.explicitlyExcludedExceptions,
	pendingExceptions: cohort.pendingExceptions,
	unexpectedApprovedPlayerIds: cohort.unexpectedApprovedPlayerIds || [],
	unexpectedExcludedPlayerIds: cohort.unexpectedExcludedPlayerIds || [],
	conflictingDecisionPlayerIds: cohort.conflictingDecisionPlayerIds || [],
	exceptions: cohort.exceptions || [],
	alreadyLinked: payload.alreadyLinked,
	pending,
	createdAccounts: payload.createdAccounts,
	createdCharacters: payload.createdCharacters,
	updatedCharacters: payload.updatedCharacters,
	linkedPlayers: payload.linkedPlayers,
	starterCardsGranted: payload.starterCardsGranted,
	starterCardsSkippedAfterLaunchSeal: payload.starterCardsSkippedAfterLaunchSeal,
	grantLaunchCharacterCards: payload.grantLaunchCharacterCards,
	launchCharacterCardsSeeded: payload.launchCharacterCardsSeeded,
	launchCharacterCardsAlreadyPresent: payload.launchCharacterCardsAlreadyPresent,
	launchCharacterCardsSkippedAfterSeal: payload.launchCharacterCardsSkippedAfterSeal,
	launchCharacterCardCutoverWillSeal: payload.launchCharacterCardCutoverWillSeal,
	launchCharacterCardCutoverSealed: payload.launchCharacterCardCutoverSealed,
	launchCharacterCardCutoverAlreadySealed: payload.launchCharacterCardCutoverAlreadySealed,
	launchCharacterCardCampaign: payload.launchCharacterCardCampaign,
	subscriptionsMigrated: payload.subscriptionsMigrated,
	conflicts
}));
if (conflicts || cohort.ready !== true) process.exit(2);
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
		if [[ "$mode" != "--apply" ]]; then
			echo "native portal backfill has pending changes; archive this report, then rerun --apply with PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN and PORTAL_NATIVE_BACKFILL_AS_OF_MS set from it"
			exit 0
		fi
		;;
	*)
		exit "$status"
		;;
esac

if [[ -z "$review_token" ]]; then
	echo "PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN is required for --apply; copy it from a separately reviewed dry-run" >&2
	exit 2
fi

current_review_token="$(node -e 'const p=JSON.parse(process.argv[1]); process.stdout.write(String(p.cohort && p.cohort.reviewToken || ""));' "$dry_run")"
if [[ "$review_token" != "$current_review_token" ]]; then
	echo "PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN does not match the current dry-run cohort; review the new report" >&2
	exit 2
fi

apply_payload="$(node -e '
process.stdout.write(JSON.stringify({
	apply: true,
	reviewToken: process.argv[1],
	approvedExceptionPlayerIds: JSON.parse(process.argv[2]),
	excludedExceptionPlayerIds: JSON.parse(process.argv[3]),
	asOfMs: Number(process.argv[4]),
	grantLaunchCharacterCards: process.argv[5] !== "0"
}));
' "$review_token" "$approved_json" "$excluded_json" \
	"$(node -e 'const p=JSON.parse(process.argv[1]); process.stdout.write(String(p.cohort.asOfMs));' "$dry_run")" \
	"$grant_launch_character_cards")"

applied="$(
	curl -fsS -X POST "$url" \
		-H "x-portal-admin-token: $token" \
		-H "content-type: application/json" \
		-d "$apply_payload"
)"

node -e '
const payload = JSON.parse(process.argv[1]);
const conflicts = Array.isArray(payload.conflicts) ? payload.conflicts.length : 0;
const cohort = payload.cohort || {};
console.log("native portal backfill applied: " + JSON.stringify({
	players: payload.players,
	cohortPolicy: cohort.policy,
	reviewToken: cohort.reviewToken,
	asOfMs: cohort.asOfMs,
	approvedExceptions: cohort.approvedExceptions,
	explicitlyExcludedExceptions: cohort.explicitlyExcludedExceptions,
	createdAccounts: payload.createdAccounts,
	createdCharacters: payload.createdCharacters,
	updatedCharacters: payload.updatedCharacters,
	linkedPlayers: payload.linkedPlayers,
	starterCardsGranted: payload.starterCardsGranted,
	starterCardsSkippedAfterLaunchSeal: payload.starterCardsSkippedAfterLaunchSeal,
	launchCharacterCardsSeeded: payload.launchCharacterCardsSeeded,
	launchCharacterCardsAlreadyPresent: payload.launchCharacterCardsAlreadyPresent,
	launchCharacterCardsSkippedAfterSeal: payload.launchCharacterCardsSkippedAfterSeal,
	launchCharacterCardCutoverWillSeal: payload.launchCharacterCardCutoverWillSeal,
	launchCharacterCardCutoverSealed: payload.launchCharacterCardCutoverSealed,
	launchCharacterCardCutoverAlreadySealed: payload.launchCharacterCardCutoverAlreadySealed,
	subscriptionsMigrated: payload.subscriptionsMigrated,
	conflicts
}));
if (conflicts) process.exit(2);
' "$applied"
