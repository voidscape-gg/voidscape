#!/usr/bin/env bash
# Atomically freeze or unfreeze launch-roster mutations in the production portal.

set -euo pipefail

ENV_FILE="/etc/voidscape/portal.env"
PORTAL_SERVICE="voidscape-portal.service"
GAME_SERVICE="voidscape.service"
LOCAL_BASE_URL="http://127.0.0.1:8788"
PUBLIC_BASE_URL=""
SYSTEMCTL_BIN="systemctl"
CURL_BIN="curl"
SS_BIN="ss"
GAME_PORTS=(43596 43496)
CUSTOM_GAME_PORTS=0
SKIP_WRITE_PROBES=0
ROSTER_FREEZE_KEY="PORTAL_ROSTER_WRITES_FROZEN"

usage() {
	cat <<'EOF'
Usage: scripts/manage-portal-roster-freeze.sh ACTION [options]

Actions:
  freeze                  Set PORTAL_ROSTER_WRITES_FROZEN=1 atomically.
  unfreeze                Set PORTAL_ROSTER_WRITES_FROZEN=0 atomically.
  status                  Verify and report the current safe public state.

Options:
  --env-file PATH         Portal environment file. Default: /etc/voidscape/portal.env.
  --service NAME          Portal systemd service. Default: voidscape-portal.service.
  --game-service NAME     Game systemd service. Default: voidscape.service.
  --game-port PORT        Game listener port that must be closed while frozen.
                          Repeat to replace defaults 43596 and 43496.
  --local-url URL         Loopback portal base URL. Default: http://127.0.0.1:8788.
  --public-url URL        Optional public base URL for additional read-only checks.
  --systemctl PATH        systemctl-compatible executable (useful for hermetic tests).
  --curl PATH             curl-compatible executable (useful for hermetic tests).
  --ss PATH               ss-compatible executable used for listener checks.
  --skip-write-probes     Skip frozen POST probes; use only when a proxy prevents them.
  -h, --help              Show this help.

The helper never sources or prints the environment file. A failed restart or
verification restores the exact previous file atomically and restarts the portal
again. Frozen verification expects HTTP 503 with error roster_writes_frozen for
registration, verification completion, and character creation.
EOF
}

die() {
	echo "ERROR: $*" >&2
	exit 1
}

ACTION="${1:-}"
case "$ACTION" in
	freeze|unfreeze|status)
		shift
		;;
	-h|--help|"")
		usage
		exit 0
		;;
	*)
		die "action must be freeze, unfreeze, or status"
		;;
esac

while [[ $# -gt 0 ]]; do
	case "$1" in
		--env-file)
			ENV_FILE="${2:-}"
			shift 2
			;;
		--service)
			PORTAL_SERVICE="${2:-}"
			shift 2
			;;
		--game-service)
			GAME_SERVICE="${2:-}"
			shift 2
			;;
		--game-port)
			if [[ "$CUSTOM_GAME_PORTS" -eq 0 ]]; then
				GAME_PORTS=()
				CUSTOM_GAME_PORTS=1
			fi
			GAME_PORTS+=("${2:-}")
			shift 2
			;;
		--local-url)
			LOCAL_BASE_URL="${2:-}"
			shift 2
			;;
		--public-url)
			PUBLIC_BASE_URL="${2:-}"
			shift 2
			;;
		--systemctl)
			SYSTEMCTL_BIN="${2:-}"
			shift 2
			;;
		--curl)
			CURL_BIN="${2:-}"
			shift 2
			;;
		--ss)
			SS_BIN="${2:-}"
			shift 2
			;;
		--skip-write-probes)
			SKIP_WRITE_PROBES=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			die "unknown option: $1"
			;;
	esac
done

[[ -n "$ENV_FILE" && -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] \
	|| die "--env-file must be an existing regular, non-symlink file"
[[ -n "$PORTAL_SERVICE" && "$PORTAL_SERVICE" =~ ^[A-Za-z0-9_.@-]+$ ]] \
	|| die "--service contains invalid characters"
[[ -n "$GAME_SERVICE" && "$GAME_SERVICE" =~ ^[A-Za-z0-9_.@-]+$ ]] \
	|| die "--game-service contains invalid characters"
for game_port in "${GAME_PORTS[@]}"; do
	[[ "$game_port" =~ ^[0-9]+$ ]] && (( game_port >= 1 && game_port <= 65535 )) \
		|| die "--game-port must be a number from 1 to 65535"
done
[[ -x "$SYSTEMCTL_BIN" ]] || command -v "$SYSTEMCTL_BIN" >/dev/null 2>&1 \
	|| die "systemctl executable not found"
[[ -x "$CURL_BIN" ]] || command -v "$CURL_BIN" >/dev/null 2>&1 \
	|| die "curl executable not found"
command -v node >/dev/null 2>&1 || die "node is required for JSON verification"

LOCAL_BASE_URL="${LOCAL_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"
case "$LOCAL_BASE_URL" in
	http://127.0.0.1:*|https://127.0.0.1:*|http://localhost:*|https://localhost:*|http://\[::1\]:*|https://\[::1\]:*) ;;
	*) die "--local-url must use a loopback host" ;;
esac
if [[ -n "$PUBLIC_BASE_URL" && "$PUBLIC_BASE_URL" != http://* && "$PUBLIC_BASE_URL" != https://* ]]; then
	die "--public-url must be an HTTP(S) URL"
fi

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-roster-freeze.XXXXXX")"
ENV_TMP=""
ENV_BACKUP=""
ROLLBACK_ARMED=0
cleanup() {
	local original_status=$?
	trap - EXIT INT TERM HUP
	if [[ "$ROLLBACK_ARMED" -eq 1 && -n "$ENV_BACKUP" && -f "$ENV_BACKUP" ]]; then
		echo "WARN: interrupted roster change; restoring the previous portal environment." >&2
		mv -f "$ENV_BACKUP" "$ENV_FILE" || true
		ENV_BACKUP=""
		"$SYSTEMCTL_BIN" restart "$PORTAL_SERVICE" >/dev/null 2>&1 || \
			echo "ERROR: previous portal environment was restored, but the portal restart failed." >&2
	fi
	[[ -z "$ENV_TMP" ]] || rm -f "$ENV_TMP"
	[[ -z "$ENV_BACKUP" ]] || rm -f "$ENV_BACKUP"
	rm -rf "$WORK_DIR"
	return "$original_status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

read_env_state() {
	local count value
	count="$(awk -F= -v key="$ROSTER_FREEZE_KEY" '$1 == key { count++ } END { print count + 0 }' "$ENV_FILE")"
	(( count <= 1 )) || die "$ROSTER_FREEZE_KEY appears more than once"
	if (( count == 0 )); then
		printf '%s' "false"
		return
	fi
	value="$(awk -F= -v key="$ROSTER_FREEZE_KEY" '$1 == key { print substr($0, index($0, "=") + 1) }' "$ENV_FILE")"
	case "$value" in
		1) printf '%s' "true" ;;
		0) printf '%s' "false" ;;
		*) die "$ROSTER_FREEZE_KEY must be exactly 0 or 1" ;;
	esac
}

atomic_set_env_state() {
	local numeric="$1"
	local env_dir env_mode env_uid env_gid
	env_dir="$(dirname "$ENV_FILE")"
	ENV_TMP="$(mktemp "$env_dir/.portal.env.roster-freeze.XXXXXX")"
	awk -F= -v key="$ROSTER_FREEZE_KEY" '$1 != key { print }' "$ENV_FILE" > "$ENV_TMP"
	printf '%s=%s\n' "$ROSTER_FREEZE_KEY" "$numeric" >> "$ENV_TMP"
	if env_mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null)"; then
		env_uid="$(stat -c '%u' "$ENV_FILE")"
		env_gid="$(stat -c '%g' "$ENV_FILE")"
	else
		env_mode="$(stat -f '%Lp' "$ENV_FILE")"
		env_uid="$(stat -f '%u' "$ENV_FILE")"
		env_gid="$(stat -f '%g' "$ENV_FILE")"
	fi
	chmod "$env_mode" "$ENV_TMP"
	if [[ "$(id -u)" -eq 0 ]]; then
		chown "$env_uid:$env_gid" "$ENV_TMP" || die "could not preserve portal env ownership"
	fi
	mv -f "$ENV_TMP" "$ENV_FILE"
	ENV_TMP=""
}

portal_service_active() {
	local state
	state="$($SYSTEMCTL_BIN is-active "$PORTAL_SERVICE" 2>/dev/null || true)"
	[[ "$state" == "active" ]] || die "$PORTAL_SERVICE is not active after restart"
}

assert_game_closed() {
	local active_state enabled_state listeners local_address port
	active_state="$($SYSTEMCTL_BIN is-active "$GAME_SERVICE" 2>/dev/null || true)"
	enabled_state="$($SYSTEMCTL_BIN is-enabled "$GAME_SERVICE" 2>/dev/null || true)"
	[[ "$active_state" == "inactive" ]] \
		|| die "$GAME_SERVICE must be inactive while roster writes are frozen"
	case "$enabled_state" in
		disabled|masked) ;;
		*) die "$GAME_SERVICE must be disabled or masked while roster writes are frozen" ;;
	esac
	[[ -x "$SS_BIN" ]] || command -v "$SS_BIN" >/dev/null 2>&1 \
		|| die "ss is required to prove production game ports are closed"
	listeners="$($SS_BIN -H -ltn 2>/dev/null)" \
		|| die "could not inspect listening TCP ports with ss"
	while IFS= read -r local_address; do
		[[ -n "$local_address" ]] || continue
		port="${local_address##*:}"
		for game_port in "${GAME_PORTS[@]}"; do
			[[ "$port" != "$game_port" ]] \
				|| die "production game port $game_port still has a TCP listener"
		done
	done < <(awk '{ print $4 }' <<<"$listeners")
}

fetch_json() {
	local url="$1"
	local output="$2"
	local status
	status="$($CURL_BIN -sS --connect-timeout 5 --max-time 15 -o "$output" -w '%{http_code}' "$url")"
	[[ "$status" == "200" ]] || die "$url returned HTTP $status instead of 200"
}

assert_json_freeze_state() {
	local file="$1"
	local expected="$2"
	local label="$3"
	node - "$file" "$expected" "$label" <<'NODE'
const fs = require("fs");
const [file, expectedText, label] = process.argv.slice(2);
const payload = JSON.parse(fs.readFileSync(file, "utf8"));
const actual = payload.rosterWritesFrozen ?? payload.config?.rosterWritesFrozen;
const expected = expectedText === "true";
if (actual !== expected) {
	throw new Error(`${label} rosterWritesFrozen=${JSON.stringify(actual)}; expected ${expected}`);
}
NODE
}

probe_frozen_write() {
	local path="$1"
	local slug="$2"
	local body="$WORK_DIR/write-$slug.json"
	local status
	status="$($CURL_BIN -sS --connect-timeout 5 --max-time 15 \
		-X POST -H 'content-type: application/json' -d '{}' \
		-o "$body" -w '%{http_code}' "$LOCAL_BASE_URL$path")"
	[[ "$status" == "503" ]] || die "$path returned HTTP $status instead of frozen 503"
	node - "$body" "$path" <<'NODE'
const fs = require("fs");
const [file, path] = process.argv.slice(2);
const payload = JSON.parse(fs.readFileSync(file, "utf8"));
if (payload.error !== "roster_writes_frozen") {
	throw new Error(`${path} did not return roster_writes_frozen`);
}
NODE
}

verify_portal_state() {
	local expected="$1"
	local health="$WORK_DIR/health.json"
	local public="$WORK_DIR/public.json"
	if [[ "$expected" == "true" ]]; then
		assert_game_closed || return 1
	fi
	portal_service_active || return 1
	fetch_json "$LOCAL_BASE_URL/api/health" "$health" || return 1
	fetch_json "$LOCAL_BASE_URL/api/public" "$public" || return 1
	assert_json_freeze_state "$health" "$expected" "local health" || return 1
	assert_json_freeze_state "$public" "$expected" "local public" || return 1
	if [[ -n "$PUBLIC_BASE_URL" ]]; then
		local external_health="$WORK_DIR/external-health.json"
		local external_public="$WORK_DIR/external-public.json"
		fetch_json "$PUBLIC_BASE_URL/api/health" "$external_health" || return 1
		fetch_json "$PUBLIC_BASE_URL/api/public" "$external_public" || return 1
		assert_json_freeze_state "$external_health" "$expected" "external health" || return 1
		assert_json_freeze_state "$external_public" "$expected" "external public" || return 1
	fi
	if [[ "$expected" == "true" && "$SKIP_WRITE_PROBES" -ne 1 ]]; then
		probe_frozen_write "/api/accounts/register" "register" || return 1
		probe_frozen_write "/api/accounts/verify-email" "verify-email" || return 1
		probe_frozen_write "/api/characters" "characters" || return 1
	fi
}

current_state="$(read_env_state)"
if [[ "$ACTION" == "status" ]]; then
	verify_portal_state "$current_state"
	echo "Portal roster freeze status: frozen=$current_state; service=active; API state verified."
	exit 0
fi

target_state="false"
target_numeric=0
if [[ "$ACTION" == "freeze" ]]; then
	target_state="true"
	target_numeric=1
fi

if [[ "$current_state" == "$target_state" ]]; then
	verify_portal_state "$target_state"
	echo "Portal roster already in requested state: frozen=$target_state; API state verified."
	exit 0
fi

if [[ "$target_state" == "true" ]]; then
	assert_game_closed
fi

env_dir="$(dirname "$ENV_FILE")"
ENV_BACKUP="$(mktemp "$env_dir/.portal.env.roster-freeze-backup.XXXXXX")"
cp -p "$ENV_FILE" "$ENV_BACKUP"
ROLLBACK_ARMED=1
atomic_set_env_state "$target_numeric"

change_ok=0
# Run verification in a subshell so a fail-closed `die` returns control here and
# the exact previous environment can still be restored.
if "$SYSTEMCTL_BIN" restart "$PORTAL_SERVICE" && ( verify_portal_state "$target_state" ); then
	change_ok=1
fi

if [[ "$change_ok" -ne 1 ]]; then
	echo "ERROR: portal roster state verification failed; restoring the previous environment." >&2
	mv -f "$ENV_BACKUP" "$ENV_FILE"
	ENV_BACKUP=""
	ROLLBACK_ARMED=0
	if ! "$SYSTEMCTL_BIN" restart "$PORTAL_SERVICE"; then
		die "rollback restored the environment but could not restart $PORTAL_SERVICE"
	fi
	( verify_portal_state "$current_state" ) \
		|| die "rollback restored the environment but previous API state did not recover"
	die "requested roster state was rolled back safely"
fi

ROLLBACK_ARMED=0
rm -f "$ENV_BACKUP"
ENV_BACKUP=""
echo "Portal roster state changed: frozen=$target_state; service=active; API state and write guards verified."
