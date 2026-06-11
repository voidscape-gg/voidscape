#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON:-python3}"

export PYTHONPATH="$ROOT/tools/voidscape-content:$ROOT/tools/voidscim-art${PYTHONPATH:+:$PYTHONPATH}"
exec "$PYTHON_BIN" "$ROOT/tools/content-studio/server.py" "$@"
