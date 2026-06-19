#!/usr/bin/env bash
# run-client.sh — launch the PC client against a local server
#
# Ensures Client_Base/Cache/{ip,port}.txt point at localhost on the
# voidscape server's port, then runs ant compile-and-run.
#
# Dev shortcut:
#   scripts/run-client.sh --login StepAlt:stepalt
#   scripts/run-client.sh --user StepAlt --pass stepalt

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

AUTO_LOGIN_USER="${VOIDSCAPE_AUTO_LOGIN_USER:-}"
AUTO_LOGIN_PASS="${VOIDSCAPE_AUTO_LOGIN_PASS:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --login)
            [[ $# -ge 2 ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
            AUTO_LOGIN_USER="${2%%:*}"
            AUTO_LOGIN_PASS="${2#*:}"
            [[ "$AUTO_LOGIN_USER" != "$2" ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
            shift 2
            ;;
        --user)
            [[ $# -ge 2 ]] || { echo "error: --user needs a value" >&2; exit 2; }
            AUTO_LOGIN_USER="$2"
            shift 2
            ;;
        --pass|--password)
            [[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
            AUTO_LOGIN_PASS="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '1,18p' "$0"
            exit 0
            ;;
        *)
            echo "error: unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

# Default port: extract from local.conf if present, otherwise 43594.
# local.conf is YAML-ish with tab-indented keys: `\tserver_port: 43596 # ...`.
# Grep allows leading whitespace; awk picks the value field (not the comment).
DEFAULT_PORT=43594
if [[ -f server/local.conf ]]; then
    PORT_FROM_CONF=$(grep -E '^[[:space:]]*server_port:' server/local.conf | head -1 | awk '{print $2}' || true)
    DEFAULT_PORT="${PORT_FROM_CONF:-43594}"
fi

mkdir -p Client_Base/Cache
echo "127.0.0.1" > Client_Base/Cache/ip.txt
echo "$DEFAULT_PORT" > Client_Base/Cache/port.txt

echo "==> Client will connect to 127.0.0.1:$DEFAULT_PORT"
if [[ -n "$AUTO_LOGIN_USER" || -n "$AUTO_LOGIN_PASS" ]]; then
    [[ -n "$AUTO_LOGIN_USER" && -n "$AUTO_LOGIN_PASS" ]] || {
        echo "error: auto-login needs both username and password" >&2
        exit 2
    }
    export VOIDSCAPE_AUTO_LOGIN_USER="$AUTO_LOGIN_USER"
    export VOIDSCAPE_AUTO_LOGIN_PASS="$AUTO_LOGIN_PASS"
    echo "==> Dev auto-login enabled for user: $AUTO_LOGIN_USER"
fi
echo "==> Building + launching PC client"
cd Client_Base
ant compile-and-run
