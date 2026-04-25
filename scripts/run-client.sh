#!/usr/bin/env bash
# run-client.sh — launch the PC client against a local server
#
# Ensures Client_Base/Cache/{ip,port}.txt point at localhost on the
# voidscape server's port, then runs ant compile-and-run.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Default port: extract from local.conf if present, otherwise 43594
DEFAULT_PORT=43594
if [[ -f server/local.conf ]]; then
    PORT_FROM_CONF=$(grep -E '^server_port' server/local.conf | head -1 | awk '{print $NF}' || true)
    DEFAULT_PORT="${PORT_FROM_CONF:-43594}"
fi

mkdir -p Client_Base/Cache
echo "127.0.0.1" > Client_Base/Cache/ip.txt
echo "$DEFAULT_PORT" > Client_Base/Cache/port.txt

echo "==> Client will connect to 127.0.0.1:$DEFAULT_PORT"
echo "==> Building + launching PC client"
cd Client_Base
ant compile-and-run
