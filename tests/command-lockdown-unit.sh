#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
SOURCE="$REPO/tests/java/com/openrsc/server/net/rsc/handlers/CommandLockdownTest.java"
HANDLER_SOURCE="$REPO/server/src/com/openrsc/server/net/rsc/handlers/CommandHandler.java"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/command-lockdown.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac -source 8 -target 8 -cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" "$HANDLER_SOURCE" "$SOURCE"
java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.net.rsc.handlers.CommandLockdownTest
