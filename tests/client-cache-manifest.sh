#!/usr/bin/env bash
# Require the legacy updater table to describe the same canonical cache we ship.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

python3 "$REPO/scripts/generate-client-cache-md5.py" --check
