#!/usr/bin/env bash
# run-workbench-client.sh - launch the PC client with the local AI workbench.
#
# Dev shortcut:
#   scripts/run-workbench-client.sh --login StepAlt:stepalt
#   scripts/run-workbench-client.sh --user StepAlt --pass stepalt --workbench-port 18787

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

CLIENT_HOST="${VOIDSCAPE_CLIENT_HOST:-127.0.0.1}"
DEFAULT_PORT=43594
if [[ -f server/local.conf ]]; then
    PORT_FROM_CONF=$(grep -E '^[[:space:]]*server_port:' server/local.conf | head -1 | awk '{print $2}' || true)
    DEFAULT_PORT="${PORT_FROM_CONF:-43594}"
fi
CLIENT_PORT="${VOIDSCAPE_CLIENT_PORT:-$DEFAULT_PORT}"

WORKBENCH_PORT="${VOIDSCAPE_WORKBENCH_PORT:-18787}"
WORKBENCH_DIR="${VOIDSCAPE_WORKBENCH_DIR:-$REPO_ROOT/tmp/workbench}"
AUTO_LOGIN_USER="${VOIDSCAPE_AUTO_LOGIN_USER:-}"
AUTO_LOGIN_PASS="${VOIDSCAPE_AUTO_LOGIN_PASS:-}"

usage() {
    sed -n '1,8p' "$0"
    cat <<'EOF'

Options:
  --host HOST             Game server host written to Client_Base/Cache/ip.txt.
  --port PORT             Game server port written to Client_Base/Cache/port.txt.
  --workbench-port PORT   Loopback workbench HTTP port.
  --out DIR               Workbench screenshot/output directory.
  --login USER:PASS       Enable dev auto-login.
  --user USER             Enable dev auto-login username.
  --pass PASS             Enable dev auto-login password.
  -h, --help              Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host)
            [[ $# -ge 2 ]] || { echo "error: --host needs a value" >&2; exit 2; }
            CLIENT_HOST="$2"
            shift 2
            ;;
        --port)
            [[ $# -ge 2 ]] || { echo "error: --port needs a value" >&2; exit 2; }
            CLIENT_PORT="$2"
            shift 2
            ;;
        --workbench-port)
            [[ $# -ge 2 ]] || { echo "error: --workbench-port needs a value" >&2; exit 2; }
            WORKBENCH_PORT="$2"
            shift 2
            ;;
        --out|--workbench-dir)
            [[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
            WORKBENCH_DIR="$2"
            shift 2
            ;;
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
            usage
            exit 0
            ;;
        *)
            echo "error: unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -n "$AUTO_LOGIN_USER" || -n "$AUTO_LOGIN_PASS" ]]; then
    [[ -n "$AUTO_LOGIN_USER" && -n "$AUTO_LOGIN_PASS" ]] || {
        echo "error: auto-login needs both username and password" >&2
        exit 2
    }
    export VOIDSCAPE_AUTO_LOGIN_USER="$AUTO_LOGIN_USER"
    export VOIDSCAPE_AUTO_LOGIN_PASS="$AUTO_LOGIN_PASS"
fi

mkdir -p Client_Base/Cache "$WORKBENCH_DIR"
echo "$CLIENT_HOST" > Client_Base/Cache/ip.txt
echo "$CLIENT_PORT" > Client_Base/Cache/port.txt

echo "==> Client will connect to $CLIENT_HOST:$CLIENT_PORT"
echo "==> Workbench will listen on http://127.0.0.1:$WORKBENCH_PORT"
echo "==> Workbench captures will be saved under $WORKBENCH_DIR"
if [[ -n "$AUTO_LOGIN_USER" ]]; then
    echo "==> Dev auto-login enabled for user: $AUTO_LOGIN_USER"
fi
echo "==> Building + launching PC client"

cd Client_Base
ant \
    -Dworkbench.jvmargs="-Dvoidscape.workbench=true -Dvoidscape.workbench.port=$WORKBENCH_PORT -Dvoidscape.workbench.dir=$WORKBENCH_DIR ${VOIDSCAPE_WORKBENCH_JVMARGS:-}" \
    compile-and-run
