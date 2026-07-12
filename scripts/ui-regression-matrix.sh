#!/usr/bin/env bash
# ui-regression-matrix.sh - capture the core Voidscape UI panels across every
# viewport preset so geometry changes can be diffed byte-for-byte.
#
# Requires the AI workbench client to already be running (see the launch line
# printed on failure). This script never launches or builds the client.
#
# What it does, for each viewport preset the client exposes:
#   1. POST /dev/viewport  (switch native resolution)
#   2. POST /scenario/ui-panels  (opens + screenshots the HUD/menu panels)
#   3. copies the produced PNGs into
#        tmp/workbench/matrix/<timestamp>/<label>/vp<N>/<panel>.png
#      with the capture timestamp stripped so runs are directly diffable.
# /dev/ready is POSTed once up front to reach an in-game state.
#
# The real /dev/viewport contract was read from
# PC_Client/src/orsc/WorkbenchServer.java::handleViewportPreset: it takes an
# "index" field, applies ScaledWindow.applyViewportPreset(index) (clamped to the
# valid range), and echoes back the applied {index,label}. The preset count is
# discovered dynamically by probing indices until the echoed index stops
# advancing (the clamp), so this keeps working if presets are added or removed.
#
# Usage:
#   scripts/ui-regression-matrix.sh [label]
#   BASELINE=<dir> scripts/ui-regression-matrix.sh [label]
#
#   label     Output-path segment for the skin under test (default "custom").
#   BASELINE  A previous run's <timestamp>/<label> directory. When set, every
#             produced PNG is byte-compared (cmp -s) against the same relative
#             path in BASELINE; a PASS/DIFF/MISSING table is printed and the
#             script exits 1 if anything differs or is missing, else 0.
#             Without BASELINE the capture directory is printed (use it as the
#             next baseline).
#
# Skin-mode note: the custom UI skin (C_CUSTOM_UI) is pushed by the server, not
# toggled by the client, so a both-skins comparison needs two runs with the
# server flipped between them (this script never flips the server itself):
#   1. Start the server in its custom-UI preset, run:
#        scripts/ui-regression-matrix.sh custom
#   2. Restart the server in its classic preset, run:
#        scripts/ui-regression-matrix.sh classic
#   Then diff a later run against each label's directory via BASELINE=...

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

WORKBENCH_HOST="${VOIDSCAPE_WORKBENCH_HOST:-127.0.0.1}"
WORKBENCH_PORT="${VOIDSCAPE_WORKBENCH_PORT:-18787}"
BASE_URL="http://${WORKBENCH_HOST}:${WORKBENCH_PORT}"
MAX_PRESET_PROBE=64

LABEL="${1:-custom}"
if [[ ! "$LABEL" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "error: label must match [A-Za-z0-9._-]+ (got: '$LABEL')" >&2
    exit 2
fi

require_tool() {
    command -v "$1" >/dev/null 2>&1 || { echo "error: '$1' is required but not found" >&2; exit 2; }
}
require_tool curl
require_tool python3

LAUNCH_HINT="scripts/run-workbench-client.sh --login wbtest:voidtest123"

if ! curl -fsS -m 5 "${BASE_URL}/health" >/dev/null 2>&1; then
    echo "error: workbench is not responding at ${BASE_URL}/health" >&2
    echo "       start it first, e.g.:" >&2
    echo "         ${LAUNCH_HINT}" >&2
    exit 1
fi

# POST helper: body is form-encoded ("k=v"); WorkbenchServer parses both form
# and JSON bodies, form is simplest. Fails (set -e) on any non-2xx response.
wb_post() {
    local path="$1"
    local data="${2:-}"
    curl -fsS -m 60 -X POST --data "$data" "${BASE_URL}${path}"
}

# Extract the applied preset index from a /dev/viewport response.
viewport_index() {
    python3 -c 'import sys,json; print(json.load(sys.stdin).get("index", -1))'
}

RUN_TS="$(date +%Y%m%d-%H%M%S)"
CAPTURE_ROOT="${REPO_ROOT}/tmp/workbench/matrix/${RUN_TS}/${LABEL}"
mkdir -p "$CAPTURE_ROOT"

restore_viewport() {
    # Best-effort: put the client back on preset 0 so it is not left on an odd
    # native resolution after the run (or an early failure).
    curl -fsS -m 30 -X POST --data "index=0" "${BASE_URL}/dev/viewport" >/dev/null 2>&1 || true
}
trap restore_viewport EXIT

echo "==> Workbench:   ${BASE_URL}"
echo "==> Label:       ${LABEL}"
echo "==> Capture dir: ${CAPTURE_ROOT}"
echo "==> Reaching an in-game state (POST /dev/ready)"
wb_post /dev/ready >/dev/null

# Iterate every preset the client exposes. Probe until the echoed index no
# longer matches the requested one (the ScaledWindow clamp), which marks the end.
preset=0
processed=0
while (( preset < MAX_PRESET_PROBE )); do
    applied="$(wb_post /dev/viewport "index=${preset}" | viewport_index)"
    if [[ "$applied" != "$preset" ]]; then
        break
    fi

    vp_dir="${CAPTURE_ROOT}/vp${preset}"
    mkdir -p "$vp_dir"
    echo "==> Preset ${preset}: capturing UI panels"

    # /scenario/ui-panels returns {captures:[{reason,pngPath,...},...]}. Emit one
    # "<sanitized-reason>\t<absolute-png-path>" line per capture so we can copy
    # each PNG to a stable, timestamp-free name.
    wb_post /scenario/ui-panels "" | python3 -c '
import sys, json, re
data = json.load(sys.stdin)
for cap in data.get("captures", []):
    reason = cap.get("reason", "") or "capture"
    safe = re.sub(r"[^A-Za-z0-9_-]+", "-", reason).strip("-") or "capture"
    print(safe + "\t" + cap.get("pngPath", ""))
' | while IFS=$'\t' read -r name png; do
        [[ -n "$png" && -f "$png" ]] || { echo "warn: capture PNG missing for '${name}'" >&2; continue; }
        cp "$png" "${vp_dir}/${name}.png"
    done

    processed=$((processed + 1))
    preset=$((preset + 1))
done

if (( processed == 0 )); then
    echo "error: no viewport presets were captured" >&2
    exit 1
fi

echo "==> Captured ${processed} preset(s) under ${CAPTURE_ROOT}"

if [[ -z "${BASELINE:-}" ]]; then
    echo ""
    echo "No BASELINE set. This run can serve as the baseline:"
    echo "  ${CAPTURE_ROOT}"
    echo "Compare a future run with:"
    echo "  BASELINE=${CAPTURE_ROOT} scripts/ui-regression-matrix.sh ${LABEL}"
    exit 0
fi

if [[ ! -d "$BASELINE" ]]; then
    echo "error: BASELINE directory does not exist: ${BASELINE}" >&2
    exit 2
fi

echo ""
echo "==> Comparing against baseline: ${BASELINE}"
printf '%-8s  %s\n' "RESULT" "PATH"
printf '%-8s  %s\n' "------" "----"

fail=0
# Walk exactly what this run produced; compare each to the same relative path.
while IFS= read -r produced; do
    rel="${produced#"$CAPTURE_ROOT"/}"
    base_file="${BASELINE}/${rel}"
    if [[ ! -f "$base_file" ]]; then
        printf '%-8s  %s\n' "MISSING" "$rel"
        fail=1
    elif cmp -s "$base_file" "$produced"; then
        printf '%-8s  %s\n' "PASS" "$rel"
    else
        printf '%-8s  %s\n' "DIFF" "$rel"
        fail=1
    fi
done < <(find "$CAPTURE_ROOT" -type f -name '*.png' | sort)

echo ""
if (( fail != 0 )); then
    echo "==> Regression detected (see DIFF/MISSING rows above)."
    exit 1
fi
echo "==> All captures match the baseline."
exit 0
