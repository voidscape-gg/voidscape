#!/usr/bin/env bash
# build.sh — voidscape canonical build
#
# Compiles server core, plugins, and PC client. Wraps OpenRSC's Makefile
# `compile` target with sanity checks. Use this instead of raw ant/make
# invocations so we don't drift across sessions.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Sanity checks
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. See docs/DEVELOPMENT.md for JDK 11 install." >&2
    exit 1
fi

if ! command -v ant >/dev/null 2>&1; then
    echo "ERROR: ant not found. Install with: brew install ant" >&2
    exit 1
fi

JAVA_MAJOR=$(java -version 2>&1 | awk -F '"' '/version/ {split($2, v, "."); print v[1]}')
if [[ "$JAVA_MAJOR" -lt 11 ]]; then
    echo "ERROR: JDK 11+ required (detected $JAVA_MAJOR). See docs/DEVELOPMENT.md." >&2
    exit 1
fi
if [[ "$JAVA_MAJOR" -gt 21 ]]; then
    echo "WARNING: JDK $JAVA_MAJOR detected. OpenRSC targets JDK 8/11; expect compile warnings." >&2
fi

echo "==> Building voidscape (server + plugins + client)"
make compile
echo "==> Build complete"
echo "    server/core.jar"
echo "    server/plugins.jar"
echo "    Client_Base/Open_RSC_Client.jar"
