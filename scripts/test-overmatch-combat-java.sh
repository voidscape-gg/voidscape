#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-overmatch-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/com/openrsc/server/event/rsc/impl/combat"
cat > "$TMP/com/openrsc/server/event/rsc/impl/combat/OvermatchFormulaMathTest.java" <<'JAVA'
package com.openrsc.server.event.rsc.impl.combat;

public final class OvermatchFormulaMathTest {
	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(String[] args) {
		require(OvermatchCombatFormula.quantize(51 * 28) == 1, "MH28 glance low");
		require(OvermatchCombatFormula.quantize(307 * 28) == 8, "MH28 glance high");
		require(OvermatchCombatFormula.quantize(410 * 28) == 11, "MH28 solid low");
		require(OvermatchCombatFormula.quantize(768 * 28) == 21, "MH28 solid high");
		require(OvermatchCombatFormula.quantize(870 * 28) == 24, "MH28 crit low");
		require(OvermatchCombatFormula.quantize(1178 * 28) == 32, "MH28 crit high");

		require(OvermatchCombatFormula.tierForMargin(0, 10, 20)
			== OvermatchCombatFormula.Tier.MISS, "zero margin misses");
		require(OvermatchCombatFormula.tierForMargin(1, 10, 20)
			== OvermatchCombatFormula.Tier.GLANCE, "positive low margin glances");
		require(OvermatchCombatFormula.tierForMargin(10, 10, 20)
			== OvermatchCombatFormula.Tier.SOLID, "T1 is solid");
		require(OvermatchCombatFormula.tierForMargin(20, 10, 20)
			== OvermatchCombatFormula.Tier.CRITICAL, "T2 is critical");
		System.out.println("Overmatch fixed-point Java vectors passed");
	}
}
JAVA

javac -source 8 -target 8 -cp "$ROOT/server/core.jar" -d "$TMP" \
  "$TMP/com/openrsc/server/event/rsc/impl/combat/OvermatchFormulaMathTest.java"
java -cp "$ROOT/server/core.jar:$TMP" \
  com.openrsc.server.event.rsc.impl.combat.OvermatchFormulaMathTest
