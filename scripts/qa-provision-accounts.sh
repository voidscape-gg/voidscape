#!/usr/bin/env bash
# qa-provision-accounts.sh — create the QA bot account pool (docs/QA-CAMPAIGN.md).
#
# Creates wbtest + qabot01..qabotNN via `voidbot register`, then grants ADMIN
# (group_id=1) with a direct sqlite UPDATE. The UPDATE is only safe while the
# server is STOPPED (it holds players in memory and saves over DB edits), so this
# script owns the whole server lifecycle: it refuses to run if a server is already
# listening, starts its own, registers, stops it, then applies the grant.
# Idempotent: already-existing accounts (register response 2) count as success.
#
# Requires want_packet_register: true in server/local.conf (dev-only flag).
#
# Usage: scripts/qa-provision-accounts.sh [count]     # default 12 qabots
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

COUNT="${1:-12}"
NORMAL_COUNT="${VOIDBOT_QA_NORMAL_COUNT:-1}"
GAME_PORT="${VOIDBOT_GAME_PORT:-43596}"
DB_NAME="$(sed -nE 's/^[[:space:]]*db_name:[[:space:]]*([^#[:space:]]+).*/\1/p' server/local.conf | head -1)"
DB_FILE="${VOIDBOT_QA_DB_FILE:-server/inc/sqlite/${DB_NAME}.db}"
VOIDBOT="tools/voidbot/voidbot"
PASS_QABOT="voidqa123"
PASS_WBTEST="voidtest123"
SERVER_LOG="${TMPDIR:-/tmp}/qa-provision-server.log"

port_up() {
    python3 -c "import socket,sys; s=socket.socket(); s.settimeout(1); sys.exit(0 if s.connect_ex(('127.0.0.1',$GAME_PORT))==0 else 1)" 2>/dev/null
}

if port_up; then
    echo "ERROR: a server is already listening on port $GAME_PORT." >&2
    echo "Stop it first — this script must own the server so it can stop it before the admin-rank UPDATE." >&2
    exit 2
fi

if ! grep -Eq '^[[:space:]]*want_packet_register:[[:space:]]*true' server/local.conf; then
    echo "ERROR: want_packet_register is not true in server/local.conf — voidbot register would be rejected (response 4)." >&2
    exit 2
fi

if [ -z "$DB_NAME" ] || [ ! -f "$DB_FILE" ]; then
    echo "ERROR: active SQLite QA database does not exist: $DB_FILE" >&2
    exit 2
fi

if grep -Eq '^[[:space:]]*production_command_lockdown:[[:space:]]*true' server/local.conf; then
    echo "WARNING: production_command_lockdown is true in server/local.conf — the QA fleet's admin" >&2
    echo "         setup commands (::item, ::teleport, ::spawnnpc, ...) will be owner-only and fail." >&2
    echo "         Set it false for local QA (docs/QA-CAMPAIGN.md)." >&2
fi

echo "==> Starting server (log: $SERVER_LOG)"
"$SCRIPT_DIR/run-server.sh" >"$SERVER_LOG" 2>&1 &
WRAPPER_PID=$!
for _ in $(seq 1 240); do
    port_up && break
    if ! kill -0 "$WRAPPER_PID" 2>/dev/null; then
        echo "ERROR: server process exited before the game port came up (see $SERVER_LOG)" >&2
        exit 1
    fi
    sleep 1
done
port_up || { echo "ERROR: server did not come up within 240s (see $SERVER_LOG)" >&2; exit 1; }

register() { # <user> <pass>
    local user="$1" pass="$2" out rc attempt
    for attempt in 1 2 3; do
        set +e
        out="$("$VOIDBOT" register --user "$user" --pass "$pass")"
        rc=$?
        set -e
        echo "    $user: $out"
        [ $rc -eq 0 ] && return 0
        echo "$out" | grep -q '"response": 2' && return 0   # already exists — fine
        sleep 1.2   # 2-logins/sec throttle or transient failure — retry
    done
    return 1
}

echo "==> Registering accounts (staggered for the 2/s login throttle)"
FAILED=0
register wbtest "$PASS_WBTEST" || FAILED=1
for account_number in $(seq 1 "$COUNT"); do
    i="$(printf '%02d' "$account_number")"
    sleep 0.7
    register "qabot$i" "$PASS_QABOT" || FAILED=1
done
# Deliberately NON-privileged accounts (rank stays USER/group_id 10): admin accounts
# skip dropOnDeath() (Player.java hasElevatedPriveledges), so items-kept-on-death (VS-003)
# and Wilderness PvP are only testable with normal-rank accounts. Their names are outside
# the qabot% pattern so
# the admin grant below leaves it non-admin.
for account_number in $(seq 1 "$NORMAL_COUNT"); do
    sleep 0.7
    register "qanpc$account_number" "$PASS_QABOT" || FAILED=1
done

echo "==> Stopping server before the admin-rank UPDATE"
SRV_PID="$(lsof -ti tcp:"$GAME_PORT" || true)"
[ -n "$SRV_PID" ] && kill $SRV_PID 2>/dev/null || true
for _ in $(seq 1 30); do
    port_up || break
    sleep 1
done
if port_up; then
    echo "ERROR: server did not stop; NOT applying the admin grant (unsafe while running)." >&2
    exit 1
fi
kill "$WRAPPER_PID" 2>/dev/null || true

echo "==> Granting admin (group_id=1) to the QA pool"
sqlite3 "$DB_FILE" "UPDATE players SET group_id=1 WHERE username LIKE 'qabot%' OR username='wbtest';"

# New accounts are held on Void Island by the Void Council until they choose a
# path (VoidPath.CACHE_KEY) — even admin ::teleport gets redirected back. Grant
# the Warrior path (1) so the pool can leave; suites that need the real starter
# flow register a fresh account instead. Cache type 0 = Integer.
echo "==> Granting void_path so the pool can leave Void Island"
sqlite3 "$DB_FILE" "INSERT INTO player_cache (playerID, type, key, value)
    SELECT id, 0, 'void_path', '1' FROM players
    WHERE (username LIKE 'qabot%' OR username='wbtest' OR username LIKE 'qanpc%')
      AND id NOT IN (SELECT playerID FROM player_cache WHERE key='void_path');"

EXPECTED=$((COUNT + 1))
GRANTED="$(sqlite3 "$DB_FILE" "SELECT count(*) FROM players WHERE group_id=1 AND (username LIKE 'qabot%' OR username='wbtest');")"
NPC_GRANTED="$(sqlite3 "$DB_FILE" "SELECT count(*) FROM players WHERE group_id=10 AND username LIKE 'qanpc%';")"

echo "==> Provisioned $GRANTED/$EXPECTED admin accounts (wbtest + qabot01..qabot$(printf '%02d' "$COUNT")) + $NPC_GRANTED/$NORMAL_COUNT normal qanpc accounts"
if [ "$FAILED" -ne 0 ] || [ "$GRANTED" -lt "$EXPECTED" ]; then
    echo "ERROR: provisioning incomplete" >&2
    exit 1
fi
if [ "$NPC_GRANTED" -lt "$NORMAL_COUNT" ]; then
    echo "ERROR: expected $NORMAL_COUNT non-privileged qanpc USER fixtures but found $NPC_GRANTED" >&2
    exit 1
fi
echo "==> Done. Server left STOPPED — start it with scripts/run-server.sh"
