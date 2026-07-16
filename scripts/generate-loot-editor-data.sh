#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PYTHONDONTWRITEBYTECODE=1 exec python3 tools/generate-loot-editor-data.py "$@"
