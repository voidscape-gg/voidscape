#!/usr/bin/env bash
# Compiles and runs deterministic fault injection against the production admission transaction.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/plugins/custom/misc/VoidDungeonAdmissionTest.java"

if [ ! -f "$REPO/server/core.jar" ] || [ ! -f "$REPO/server/plugins.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/void-dungeon-admission.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac -source 8 -target 8 \
	-cp "$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	-d "$TMP" "$TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	com.openrsc.server.plugins.custom.misc.VoidDungeonAdmissionTest
