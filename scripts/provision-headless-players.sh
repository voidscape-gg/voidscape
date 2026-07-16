#!/usr/bin/env bash
# Create the ten normal player accounts from the tracked, non-secret roster.
# Passwords and provisioning receipts live outside the repository.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HELPER="$SCRIPT_DIR/headless-player-helper.py"
VOIDBOT="${HEADLESS_VOIDBOT:-$REPO_ROOT/tools/voidbot/voidbot}"
PYTHON="${HEADLESS_PYTHON:-${VOIDBOT_PYTHON:-python3}}"

ROSTER="${HEADLESS_PLAYERS_ROSTER:-$REPO_ROOT/tools/headless_players/roster.json}"
CREDENTIAL_DIR="${HEADLESS_CREDENTIAL_DIR:-/etc/voidscape/headless-players}"
SERVER_CONFIG="${HEADLESS_SERVER_CONFIG:-$REPO_ROOT/server/local.conf}"
CONNECTIONS_CONFIG="${HEADLESS_CONNECTIONS_CONFIG:-$REPO_ROOT/server/connections.conf}"
DATABASE="${HEADLESS_DATABASE:-}"
PASSWORD_HELPER_CLASSPATH="${HEADLESS_PASSWORD_HELPER_CLASSPATH:-$REPO_ROOT/server/core.jar}"
JAVA="${HEADLESS_JAVA:-java}"
GAME_HOST="${HEADLESS_GAME_HOST:-127.0.0.1}"
GAME_PORT="${HEADLESS_GAME_PORT:-43596}"
STAGGER_SECONDS="${HEADLESS_PROVISION_STAGGER_SECONDS:-1.25}"
EMAIL_DOMAIN="${HEADLESS_REGISTRATION_EMAIL_DOMAIN:-}"

usage() {
	cat <<'EOF'
Usage: scripts/provision-headless-players.sh [options]

Options:
  --roster FILE             Tracked non-secret roster JSON.
  --credentials-dir DIR     Absolute directory outside the repository.
  --config FILE             Active server config (default server/local.conf).
  --connections-config FILE Active DB config (default server/connections.conf).
  --database FILE           Explicit active SQLite database path.
  --password-helper FILE    core.jar containing PortalPasswordHasher.
  --host HOST               Registration server; loopback only (default 127.0.0.1).
  --port PORT               Registration server port (default 43596).
  --stagger SECONDS         Delay between registrations (default 1.25).
  --email-domain DOMAIN     Register <profile-id>@DOMAIN when the world requires email.
  -h, --help                Show this help.

Maintenance window: set want_packet_register: true and, when both
want_registration_limit and is_localhost_restricted are true, temporarily set
want_registration_limit: false (preferred). Keep the endpoint loopback-only,
restart the server, provision, then restore the original settings and restart.
This script checks but never edits the config. Existing names are accepted only
after the exact file credential is verified offline against the active SQLite
account row. Provisioning never logs an account in or consumes first-login state.
The active world must also set right_click_bank: true, want_fatigue: false, and
want_bank_notes: true for the fleet's banking, skilling, and packet-decoding contract.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--roster) ROSTER="$2"; shift 2 ;;
		--credentials-dir) CREDENTIAL_DIR="$2"; shift 2 ;;
		--config) SERVER_CONFIG="$2"; shift 2 ;;
		--connections-config) CONNECTIONS_CONFIG="$2"; shift 2 ;;
		--database) DATABASE="$2"; shift 2 ;;
		--password-helper) PASSWORD_HELPER_CLASSPATH="$2"; shift 2 ;;
		--host) GAME_HOST="$2"; shift 2 ;;
		--port) GAME_PORT="$2"; shift 2 ;;
		--stagger) STAGGER_SECONDS="$2"; shift 2 ;;
		--email-domain) EMAIL_DOMAIN="$2"; shift 2 ;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ "$GAME_HOST" != "127.0.0.1" ]]; then
	echo "ERROR: provisioning requires the literal 127.0.0.1 game endpoint" >&2
	exit 2
fi

if [[ ! "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "ERROR: --port must be from 1 to 65535" >&2
	exit 2
fi
if ! "$PYTHON" -c 'import math,sys; value=float(sys.argv[1]); assert math.isfinite(value) and 0.5 <= value <= 60' "$STAGGER_SECONDS" 2>/dev/null; then
	echo "ERROR: --stagger must be from 0.5 to 60 seconds" >&2
	exit 2
fi
"$PYTHON" "$HELPER" check-registration-config --path "$SERVER_CONFIG" >/dev/null
"$PYTHON" "$HELPER" check-fleet-runtime-config --path "$SERVER_CONFIG" >/dev/null
credential_store_args=(check-sqlite-credential-store \
	--connections-config "$CONNECTIONS_CONFIG" --server-config "$SERVER_CONFIG" \
	--classpath "$PASSWORD_HELPER_CLASSPATH" --java "$JAVA")
[[ -n "$DATABASE" ]] && credential_store_args+=(--database "$DATABASE")
"$PYTHON" "$HELPER" "${credential_store_args[@]}" >/dev/null
CREDENTIAL_DIR="$("$PYTHON" "$HELPER" check-credential-dir --path "$CREDENTIAL_DIR")"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-headless-provision.XXXXXX")"
chmod 700 "$WORK_DIR"
ROSTER_LIST="$WORK_DIR/roster.tsv"
trap 'rm -rf "$WORK_DIR"' EXIT
"$PYTHON" "$HELPER" roster-list --roster "$ROSTER" >"$ROSTER_LIST"
export VOIDBOT_PYTHON="$PYTHON"

OFFLINE_STATUS=""
check_offline_credential() {
	local username="$1" password_file="$2" output status_rc
	local args=(sqlite-credential-status --connections-config "$CONNECTIONS_CONFIG" \
		--server-config "$SERVER_CONFIG" --username "$username" \
		--password-file "$password_file" --classpath "$PASSWORD_HELPER_CLASSPATH" \
		--java "$JAVA")
	[[ -n "$DATABASE" ]] && args+=(--database "$DATABASE")
	set +e
	output="$("$PYTHON" "$HELPER" "${args[@]}" 2>&1)"
	status_rc=$?
	set -e
	case "$status_rc" in
		0) OFFLINE_STATUS="verified" ;;
		3) OFFLINE_STATUS="missing" ;;
		4) OFFLINE_STATUS="online" ;;
		5) OFFLINE_STATUS="mismatch" ;;
		*) [[ -n "$output" ]] && echo "$output" >&2; return 2 ;;
	esac
	return 0
}

count=0
while IFS=$'\t' read -r slot profile_id username; do
	[[ -n "$profile_id" ]] || continue
	count=$((count + 1))
	password_file="$CREDENTIAL_DIR/$profile_id.password"
	receipt_file="$CREDENTIAL_DIR/$profile_id.provisioned"
	receipt_exists=0

	if [[ -e "$receipt_file" ]]; then
		if ! "$PYTHON" "$HELPER" receipt-check --path "$receipt_file" --id "$profile_id" \
			--username "$username" --password-file "$password_file"; then
			echo "ERROR: refusing incomplete, invalid, or mismatched credentials for $username" >&2
			exit 2
		fi
		receipt_exists=1
		if ! check_offline_credential "$username" "$password_file"; then
			echo "ERROR: could not verify the active SQLite account for $username" >&2
			exit 2
		fi
		case "$OFFLINE_STATUS" in
			verified)
				echo "==> slot $slot ($username): receipt and active SQLite credential verified"
				continue
				;;
			missing)
				echo "==> slot $slot ($username): receipt is valid but the active DB row is missing; re-registering"
				;;
			online)
				echo "ERROR: $username is marked online; stop the account before provisioning" >&2
				exit 1
				;;
			mismatch)
				echo "ERROR: active DB credential for $username does not match its receipt-bound password; refusing adoption" >&2
				exit 1
				;;
		esac
	fi

	"$PYTHON" "$HELPER" generate-password --path "$password_file" >/dev/null

	echo "==> slot $slot ($username): registering normal player account"
	register_args=(register --host "$GAME_HOST" --game-port "$GAME_PORT" \
		--user "$username" --password-file "$password_file")
	if [[ -n "$EMAIL_DOMAIN" ]]; then
		register_args+=(--email "$profile_id@$EMAIL_DOMAIN")
	else
		register_args+=(--no-email)
	fi
	set +e
	registration_output="$("$VOIDBOT" "${register_args[@]}" 2>&1)"
	registration_rc=$?
	set -e
	if (( registration_rc != 0 )); then
		registration_response="$(printf '%s\n' "$registration_output" | "$PYTHON" -c \
			'import json,sys; value=json.load(sys.stdin); print(value.get("response", ""))' 2>/dev/null || true)"
		if [[ "$registration_response" == "2" ]]; then
			echo "==> slot $slot ($username): name exists; verifying the preserved file credential offline"
		else
			[[ -n "$registration_output" ]] && echo "$registration_output" >&2
			echo "ERROR: registration failed for $username; no ownership receipt was written" >&2
			exit 1
		fi
	else
		[[ -n "$registration_output" ]] && echo "$registration_output"
	fi
	if ! check_offline_credential "$username" "$password_file"; then
		echo "ERROR: could not verify the active SQLite account for $username after registration" >&2
		exit 2
	fi
	case "$OFFLINE_STATUS" in
		verified) ;;
		online)
			echo "ERROR: $username became online during provisioning; refusing receipt recovery" >&2
			exit 1
			;;
		missing)
			echo "ERROR: registration did not produce an active SQLite row for $username" >&2
			exit 1
			;;
		mismatch)
			echo "ERROR: active DB credential for $username does not match the preserved password; refusing adoption" >&2
			exit 1
			;;
	esac
	if (( receipt_exists == 0 )); then
		"$PYTHON" "$HELPER" receipt-write --path "$receipt_file" --id "$profile_id" \
			--username "$username" --password-file "$password_file"
	fi

	if (( count < 10 )); then
		sleep "$STAGGER_SECONDS"
	fi
done <"$ROSTER_LIST"

if (( count != 10 )); then
	echo "ERROR: expected ten roster entries, found $count" >&2
	exit 2
fi

echo "==> Provisioning state is valid for ten normal player accounts"
echo "==> Credentials remain in $CREDENTIAL_DIR; do not copy them into the repository"
