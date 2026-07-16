#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_JAR="$REPO_ROOT/server/core.jar"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/ServerConfigurationBindAddressTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-server-bind-address.XXXXXX")"
CLASSES="$TEST_ROOT/classes"
JAVA_BIN="${VOIDSCAPE_TEST_JAVA:-$(command -v java)}"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

if [[ ! -f "$SERVER_JAR" ]]; then
	echo "ERROR: missing server/core.jar; run scripts/build.sh first." >&2
	exit 1
fi

mkdir -p "$CLASSES"
javac --release 8 -cp "$SERVER_JAR" -d "$CLASSES" "$TEST_SOURCE"

# The bundled upstream Log4j caller lookup is incompatible with JDK 22+.
# Prefer an installed Java 8 runtime for this Java-8-targeted test when the
# ambient build JDK is newer; JDK 11/17/21 continue to run directly.
JAVA_MAJOR="$($JAVA_BIN -version 2>&1 | awk -F '"' '/version/ {split($2, v, "."); print (v[1] == "1" ? v[2] : v[1])}')"
if [[ -z "${VOIDSCAPE_TEST_JAVA:-}" && "$JAVA_MAJOR" -gt 21 && -x /usr/libexec/java_home ]]; then
	COMPAT_JAVA_HOME="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
	if [[ -x "$COMPAT_JAVA_HOME/bin/java" ]]; then
		JAVA_BIN="$COMPAT_JAVA_HOME/bin/java"
	fi
fi

"$JAVA_BIN" -cp "$SERVER_JAR:$CLASSES" com.openrsc.server.ServerConfigurationBindAddressTest
