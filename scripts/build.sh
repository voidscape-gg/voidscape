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

# Prefer the repo-local Windows portable Ant when a system Ant is not on PATH.
PORTABLE_ANT_BIN="$REPO_ROOT/Portable_Windows/apache-ant-1.10.5/bin"
if ! command -v ant >/dev/null 2>&1 && [[ -x "$PORTABLE_ANT_BIN/ant" ]]; then
    export PATH="$PORTABLE_ANT_BIN:$PATH"
fi

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
if command -v make >/dev/null 2>&1; then
    make compile
else
    echo "==> make not found; using Ant compile targets directly"
    ant -f server/build.xml compile_core
    ant -f server/build.xml compile_plugins
    ant -f Client_Base/build.xml compile
    ant -f PC_Launcher/build.xml compile
fi
echo "==> Build complete"
echo "    server/core.jar"
echo "    server/plugins.jar"
echo "    Client_Base/Open_RSC_Client.jar"
