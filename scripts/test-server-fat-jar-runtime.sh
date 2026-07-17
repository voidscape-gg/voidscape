#!/usr/bin/env bash
# Prove the standalone fat jar preserves Java 9+ multi-release dependencies.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="${1:-$REPO_ROOT/server/core.jar}"
PROBE="$SCRIPT_DIR/fixtures/Log4jFatJarProbe.java"
LOG_CONFIG="$REPO_ROOT/server/conf/server/log4j2.xml"
CONNECTIONS_CONFIG="$REPO_ROOT/server/connections.conf"

fail() {
    echo "server fat-jar runtime check failed: $*" >&2
    exit 1
}

[[ -f "$JAR" ]] || fail "missing jar: $JAR"
[[ -f "$PROBE" ]] || fail "missing probe: $PROBE"
[[ -f "$LOG_CONFIG" ]] || fail "missing log configuration: $LOG_CONFIG"
[[ -f "$CONNECTIONS_CONFIG" ]] || fail "missing connection configuration: $CONNECTIONS_CONFIG"
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

JAVA_BIN="${VOIDSCAPE_SERVER_RUNTIME_JAVA:-$(command -v java || true)}"
[[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || fail "Java runtime not found"
JAVAC_BIN="${VOIDSCAPE_SERVER_RUNTIME_JAVAC:-$(dirname "$JAVA_BIN")/javac}"
[[ -x "$JAVAC_BIN" ]] || fail "javac not found beside runtime: $JAVAC_BIN"

JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')"
JAVA_MAJOR="$(printf '%s\n' "$JAVA_VERSION" | awk -F. '{ print ($1 == "1" ? $2 : $1) }')"
[[ "$JAVA_MAJOR" =~ ^[0-9]+$ && "$JAVA_MAJOR" -ge 11 ]] ||
    fail "JDK 11+ required for the packaged-runtime probe (found $JAVA_VERSION)"

MANIFEST="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r')"
MULTI_RELEASE_COUNT="$(printf '%s\n' "$MANIFEST" |
    grep -Eic '^Multi-Release:[[:space:]]*true[[:space:]]*$' || true)"
[[ "$MULTI_RELEASE_COUNT" -eq 1 ]] ||
    fail "manifest must contain exactly one 'Multi-Release: true' field"

VERSIONED_STACK_LOCATOR="META-INF/versions/9/org/apache/logging/log4j/util/StackLocator.class"
STACK_LOCATOR_COUNT="$(unzip -Z1 "$JAR" |
    awk -v expected="$VERSIONED_STACK_LOCATOR" '$0 == expected { count++ } END { print count + 0 }')"
[[ "$STACK_LOCATOR_COUNT" -eq 1 ]] ||
    fail "jar must contain exactly one Log4j Java 9 StackLocator"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-fat-jar-runtime.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

"$JAVAC_BIN" --release 8 -cp "$JAR" -d "$TMP_ROOT" "$PROBE"
PROBE_OUTPUT="$("$JAVA_BIN" -cp "$TMP_ROOT:$JAR" Log4jFatJarProbe)"
[[ "$PROBE_OUTPUT" == "Log4jFatJarProbe" ]] ||
    fail "JDK $JAVA_MAJOR logger probe returned unexpected output: $PROBE_OUTPUT"

# Exercise the executable jar without opening a database or listener. Requiring an
# explicitly named missing config stops startup immediately after logging initializes.
DIRECT_ROOT="$TMP_ROOT/direct-jar"
mkdir -p "$DIRECT_ROOT/conf/server"
cp "$LOG_CONFIG" "$DIRECT_ROOT/conf/server/log4j2.xml"
cp "$CONNECTIONS_CONFIG" "$DIRECT_ROOT/connections.conf"
set +e
DIRECT_OUTPUT="$(
    cd "$DIRECT_ROOT" &&
        "$JAVA_BIN" -Dvoidscape.config.explicitOnly=true -jar "$JAR" __expected_missing__.conf 2>&1
)"
DIRECT_STATUS=$?
set -e
[[ "$DIRECT_STATUS" -eq 1 ]] ||
    fail "direct JDK $JAVA_MAJOR startup should stop at the expected missing config (exit $DIRECT_STATUS)"
[[ "$DIRECT_OUTPUT" == *"Launching Game Server..."* ]] ||
    fail "direct JDK $JAVA_MAJOR startup did not reach server initialization"
[[ "$DIRECT_OUTPUT" == *"Explicit server configuration file not found or unreadable: __expected_missing__.conf"* ]] ||
    fail "direct JDK $JAVA_MAJOR startup did not reach the expected config boundary"
[[ "$DIRECT_OUTPUT" != *"ExceptionInInitializerError"* &&
    "$DIRECT_OUTPUT" != *"UnsupportedOperationException"* &&
    "$DIRECT_OUTPUT" != *"callerClass"* ]] ||
    fail "direct JDK $JAVA_MAJOR startup hit the Log4j initialization regression"

JAVA8_BIN=""
JAVA8_CHECK="not-installed"
if [[ -n "${VOIDSCAPE_JAVA8_HOME:-}" ]]; then
    JAVA8_BIN="$VOIDSCAPE_JAVA8_HOME/bin/java"
elif [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
    JAVA8_HOME="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
    [[ -n "$JAVA8_HOME" ]] && JAVA8_BIN="$JAVA8_HOME/bin/java"
fi

if [[ -n "$JAVA8_BIN" && -x "$JAVA8_BIN" ]]; then
    JAVA8_OUTPUT="$("$JAVA8_BIN" -cp "$TMP_ROOT:$JAR" Log4jFatJarProbe)"
    [[ "$JAVA8_OUTPUT" == "Log4jFatJarProbe" ]] ||
        fail "Java 8 logger probe returned unexpected output: $JAVA8_OUTPUT"
    JAVA8_CHECK="passed"
fi

echo "Server fat-jar runtime check passed: java=$JAVA_VERSION java8=$JAVA8_CHECK multiRelease=true"
