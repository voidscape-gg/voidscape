#!/usr/bin/env bash
# Verifies style-complete, direct-only Player->Player retaliation tracking.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
TRACKING_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/PvpDamageTracking.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/model/entity/player/PvpDamageTrackingTest.java"
PLAYER_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
FORMULA_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
OSRS_FORMULA_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/combat/OSRSCombatFormula.java"
COMBAT_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PROJECTILE_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
RANGE_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java"
THROWING_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
SPELL_SOURCE="$REPO/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
POISON_SOURCE="$REPO/server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/pvp-direct-damage.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 -d "$TMP" "$TRACKING_SOURCE" "$TEST_SOURCE"
java -cp "$TMP" com.openrsc.server.model.entity.player.PvpDamageTrackingTest

python3 - "$PLAYER_SOURCE" "$FORMULA_SOURCE" "$OSRS_FORMULA_SOURCE" "$COMBAT_SOURCE" \
	"$PROJECTILE_SOURCE" "$RANGE_SOURCE" "$THROWING_SOURCE" "$SPELL_SOURCE" \
	"$POISON_SOURCE" <<'PY'
import sys
from pathlib import Path

player, formula, osrs_formula, combat, projectile, ranged, throwing, spell, poison = (
    Path(path).read_text() for path in sys.argv[1:]
)

# Classic melee keeps the exact inherited defense-cape reduction and blocked counter,
# but contributes zero direct damage so the common application hook cannot double count.
formula_method = formula[
    formula.index("public static int doMeleeDamage"):
    formula.index("private static int rollMeleeDamage")
]
assert "damage /= 2;" in formula_method and "blockedDamage = damage;" in formula_method, \
    "legacy defense-cape reduction/bookkeeping drifted"
assert "updateDamageAndBlockedDamageTracking(source, 0, blockedDamage)" in formula_method, \
    "legacy formula may retain blocked damage only"
assert "damageToPlayer" not in formula_method, \
    "legacy melee direct damage would double count with the common hook"

# Both classic and OSRS melee converge before the one ordinary Player->Player hook.
main_hit = combat[
    combat.index("if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE)"):
    combat.index("if (target.isPlayer())", combat.index("if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE)"))
]
for required in (
    "OSRSCombatFormula.Melee.doMeleeDamage(hitter, target)",
    "CombatFormula.doMeleeDamage(hitter, target)",
    "PvpDamageTracking.actualDirectDamage(",
    "hitter != target && hitter instanceof Player && target instanceof Player",
    "updateDamageAndBlockedDamageTracking(",
    "(Player) hitter, actualDirectDamage, 0",
    "inflictDamage(hitter, target, damage, attackerMaxHit);",
):
    assert required in main_hit, f"common melee tracking contract missing: {required}"
assert main_hit.index("updateDamageAndBlockedDamageTracking(") \
    < main_hit.index("inflictDamage(hitter, target, damage, attackerMaxHit);"), \
    "main melee hit must be snapshotted immediately before damage application"
assert main_hit.count("updateDamageAndBlockedDamageTracking(") == 1, \
    "main melee may contribute direct damage exactly once"

recoil_start = combat.index("if (target.isPlayer())", combat.index("inflictDamage(hitter, target, damage, attackerMaxHit);"))
recoil_end = combat.index("private boolean checkPoisonousWeapons", recoil_start)
recoil = combat[recoil_start:recoil_end]
assert "inflictDamage(target, hitter, reflectedDamage);" in recoil, \
    "melee recoil behavior must remain present"
assert "updateDamageAndBlockedDamageTracking" not in recoil, \
    "melee recoil must not count as active retaliation"

# Every normal player ranged/throwing or offensive-magic projectile converges on this
# direct application point. The Player->Player type gate excludes NPC projectiles.
projectile_method = projectile[
    projectile.index("private void projectileDamage()"):
    projectile.index("public void setCanceled", projectile.index("private void projectileDamage()"))
]
for required in (
    "PvpDamageTracking.actualDirectDamage(damage, lastHits)",
    "actualDirectDamage > 0 && caster != opponent",
    "caster instanceof Player && opponent instanceof Player",
    "updateDamageAndBlockedDamageTracking(",
    "(Player) caster, actualDirectDamage, 0",
    "opponent.getSkills().subtractLevel(Skill.HITS.id(), damage, false);",
):
    assert required in projectile_method, f"projectile tracking contract missing: {required}"
assert projectile_method.index("updateDamageAndBlockedDamageTracking(") \
    < projectile_method.index("opponent.getSkills().subtractLevel"), \
    "projectile evidence must snapshot immediately before hitpoint subtraction"
assert projectile_method.count("updateDamageAndBlockedDamageTracking(") == 1, \
    "one projectile may contribute direct damage exactly once"

recoil_method = projectile[
    projectile.index("private void recoilDamage"):
    projectile.index("private void projectileDamage")
]
assert "updateDamageAndBlockedDamageTracking" not in recoil_method, \
    "projectile recoil must not count as active retaliation"
assert "new ProjectileEvent(getWorld(), player, target, damage, 2," in ranged, \
    "normal ranged must still use the common projectile application"
assert "new ProjectileEvent(getWorld(), player, target, damage, 2," in throwing, \
    "normal throwing must still use the common projectile application"
assert spell.count("new ProjectileEvent(") >= 6, \
    "offensive magic must still use the common projectile application"
assert "updateDamageAndBlockedDamageTracking" not in ranged \
    and "updateDamageAndBlockedDamageTracking" not in throwing \
    and "updateDamageAndBlockedDamageTracking" not in spell, \
    "style producers must not duplicate common projectile tracking"
assert "updateDamageAndBlockedDamageTracking" not in poison, \
    "poison must not count as active retaliation"
assert "updateDamageAndBlockedDamageTracking" not in osrs_formula, \
    "OSRS melee must rely on the one common melee hook"

# The target-side accumulator ignores zero/negative contributions and saturates both
# counters instead of wrapping the nonnegative event-schema contract.
tracking_method = player[
    player.index("public void updateDamageAndBlockedDamageTracking"):
    player.index("public int getTrackedDamage", player.index("public void updateDamageAndBlockedDamageTracking"))
]
for required in (
    "Math.max(0, damage)",
    "Math.max(0, blockedDamage)",
    "if (safeDamage == 0 && safeBlockedDamage == 0)",
    "PvpDamageTracking.saturatedNonnegativeAdd(oldDamage, safeDamage)",
    "PvpDamageTracking.saturatedNonnegativeAdd(oldBlockedDamage, safeBlockedDamage)",
):
    assert required in tracking_method, f"safe accumulator contract missing: {required}"

print("PvP direct-damage source contracts passed.")
PY

echo "PvP direct-damage tracking tests passed."
