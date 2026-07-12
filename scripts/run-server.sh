#!/usr/bin/env bash
# run-server.sh — start the voidscape game server
#
# Always uses server/local.conf. If missing, prints setup instructions.
# See docs/SERVER-PRESETS.md for choosing a base preset.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/server"

if [[ ! -f local.conf ]]; then
    cat >&2 <<'EOF'
ERROR: server/local.conf not found.

Voidscape's working preset lives in server/local.conf (gitignored). Bootstrap it:

    cp server/preservation.conf server/local.conf
    # then apply voidscape QoL overrides per docs/SERVER-PRESETS.md

EOF
    exit 1
fi

echo "==> Starting voidscape server (local.conf, ZGC)"
ant runserverzgc -DconfFile=local
