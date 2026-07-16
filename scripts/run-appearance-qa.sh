#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKBENCH_URL="${VOIDSCAPE_WORKBENCH_URL:-http://127.0.0.1:18787}"
APPEARANCE_ID="${1:-245}"
OUT="${2:-$ROOT/tmp/workbench/appearance-qa/appearance-$APPEARANCE_ID}"
REPORT="$OUT/workbench-report.json"
BEFORE="$OUT/cowboy-assets-before.json"
AFTER="$OUT/cowboy-assets-after.json"
IMMUTABILITY="$OUT/cowboy-immutability.json"

mkdir -p "$OUT"
"$ROOT/scripts/content.sh" appearance cowboy-asset-snapshot --out "$BEFORE"

status=0
set +e
curl -fsS -X POST -H 'Content-Type: application/json' \
  --data "{\"appearanceId\":$APPEARANCE_ID}" \
  "$WORKBENCH_URL/scenario/appearance-frames" > "$REPORT"
status=$?

if [[ $status -eq 0 ]]; then
  "$ROOT/scripts/content.sh" appearance verify-workbench --report "$REPORT" --out "$OUT"
  status=$?
fi
if [[ $status -eq 0 ]]; then
  "$ROOT/scripts/content.sh" appearance compare-workbench \
    --report "$REPORT" \
    --fixture "$OUT/composite-fixture.json" \
    --out "$OUT/client-preview"
  status=$?
fi
set -e

"$ROOT/scripts/content.sh" appearance cowboy-asset-snapshot --out "$AFTER"
"$ROOT/scripts/content.sh" appearance verify-cowboy-immutability \
  --before "$BEFORE" --after "$AFTER" --out "$IMMUTABILITY"
exit "$status"
