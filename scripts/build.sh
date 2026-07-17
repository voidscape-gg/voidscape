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

echo "==> Testing duel-proof crypto vectors"
"$SCRIPT_DIR/test-duel-proof-java.sh"

echo "==> Testing duel-proof client handshake"
"$SCRIPT_DIR/test-duel-proof-client-java.sh"

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
echo "==> Testing standalone server fat-jar runtime"
"$SCRIPT_DIR/test-server-fat-jar-runtime.sh"
echo "==> Testing Void Arena ranked policy"
"$SCRIPT_DIR/test-void-arena-ranked-policy-java.sh"
echo "==> Testing Void Arena ranked persistence"
python3 "$SCRIPT_DIR/test-void-arena-ranked-persistence.py"
echo "==> Testing JDBC transaction exclusion"
"$SCRIPT_DIR/test-jdbc-transaction-lock-java.sh"
echo "==> Testing JDBC patch marker statement lifecycle"
"$SCRIPT_DIR/test-patch-applier-statement-close-java.sh"
echo "==> Testing Void Arena kit snapshot recovery"
"$SCRIPT_DIR/test-void-arena-kit-snapshot-java.sh"
echo "==> Testing Sir Charles finite-resource policy"
"$SCRIPT_DIR/test-void-arena-sir-policy-java.sh"
echo "==> Testing Void Dungeon traversal grace"
"$SCRIPT_DIR/test-void-dungeon-traversal-grace-java.sh"
echo "==> Testing earned skill batching limits"
"$SCRIPT_DIR/test-skill-batching-java.sh"

echo "==> Testing earned skill batching coverage"
python3 "$SCRIPT_DIR/test-skill-batching-coverage.py"

echo "==> Testing avatar runtime directory creation"
"$SCRIPT_DIR/test-avatar-file-store-java.sh"

echo "==> Testing portal public-static boundary"
"$SCRIPT_DIR/test-portal-static-boundary.sh"
python3 "$SCRIPT_DIR/test-portal-title-definitions.py"
python3 "$SCRIPT_DIR/test-package-portal-runtime.py"
"$SCRIPT_DIR/test-packaged-portal-character-flow.sh"
python3 "$SCRIPT_DIR/test-launch-staging-config.py"
echo "==> Testing launch subscription config parsing"
"$SCRIPT_DIR/test-launch-subscription-config-java.sh"
echo "==> Testing portal store fail-closed safety"
"$SCRIPT_DIR/test-portal-store-safety.sh"

echo "==> Testing founder subscription claim policy"
"$SCRIPT_DIR/test-void-subscription-claim-policy-java.sh"
echo "==> Testing repeatable launch reward reset"
"$SCRIPT_DIR/test-reset-launch-game-db.sh"

echo "==> Testing gathering input buffer policy"
"$SCRIPT_DIR/test-gather-repeat-buffer-java.sh"
python3 "$SCRIPT_DIR/test-gather-input-buffer-contract.py"
python3 "$REPO_ROOT/tools/voidbot/test_gather_input_packets.py"
echo "==> Build complete"
echo "    server/core.jar"
echo "    server/plugins.jar"
echo "    Client_Base/Open_RSC_Client.jar"
