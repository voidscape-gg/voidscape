#!/usr/bin/env bash
# fetch-upstream-snapshot.sh — recreate upstream/openrsc-snapshot/ from the
# vendored SHA. Idempotent. Used to restore a clean reference copy locally.
#
# The snapshot directory is gitignored (saves ~268MB from the repo). This
# script lets anyone rebuild it on demand.
#
# Source of truth for the SHA: docs/DIVERGENCE.md.

set -euo pipefail

UPSTREAM_REPO="https://github.com/Open-RSC/Core-Framework.git"
UPSTREAM_SHA="fc74d38e2ead0a5864b48ae191b7184a391777cf"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SNAPSHOT_DIR="$REPO_ROOT/upstream/openrsc-snapshot"
TMP_CLONE="$(mktemp -d)/openrsc-clone"

trap 'rm -rf "$(dirname "$TMP_CLONE")"' EXIT

echo "Fetching $UPSTREAM_REPO @ $UPSTREAM_SHA"
git clone --filter=blob:none --no-checkout "$UPSTREAM_REPO" "$TMP_CLONE"
git -C "$TMP_CLONE" checkout "$UPSTREAM_SHA"

echo "Refreshing $SNAPSHOT_DIR"
rm -rf "$SNAPSHOT_DIR"
mkdir -p "$SNAPSHOT_DIR"
rsync -a --exclude='.git/' "$TMP_CLONE"/ "$SNAPSHOT_DIR"/

echo "Done. Snapshot at: $SNAPSHOT_DIR"
echo "Remember: never edit files in upstream/openrsc-snapshot/."
