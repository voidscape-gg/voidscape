#!/usr/bin/env bash
# run-workbench-client.sh - launch the PC client with the local AI workbench.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

DEFAULT_PORT=43594
if [[ -f server/local.conf ]]; then
    PORT_FROM_CONF=$(grep -E '^[[:space:]]*server_port:' server/local.conf | head -1 | awk '{print $2}' || true)
    DEFAULT_PORT="${PORT_FROM_CONF:-43594}"
fi

WORKBENCH_PORT="${VOIDSCAPE_WORKBENCH_PORT:-18787}"
WORKBENCH_DIR="${VOIDSCAPE_WORKBENCH_DIR:-$REPO_ROOT/tmp/workbench}"

mkdir -p Client_Base/Cache "$WORKBENCH_DIR"
echo "127.0.0.1" > Client_Base/Cache/ip.txt
echo "$DEFAULT_PORT" > Client_Base/Cache/port.txt

echo "==> Client will connect to 127.0.0.1:$DEFAULT_PORT"
echo "==> Workbench will listen on http://127.0.0.1:$WORKBENCH_PORT"
echo "==> Workbench captures will be saved under $WORKBENCH_DIR"
echo "==> Building + launching PC client"

cd Client_Base
ant \
    -Dworkbench.jvmargs="-Dvoidscape.workbench=true -Dvoidscape.workbench.port=$WORKBENCH_PORT -Dvoidscape.workbench.dir=$WORKBENCH_DIR ${VOIDSCAPE_WORKBENCH_JVMARGS:-}" \
    compile-and-run
