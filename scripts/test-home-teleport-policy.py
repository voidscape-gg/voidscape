#!/usr/bin/env python3
"""Source contract for the Home Teleport safety policy and voidbot probe."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
PROTOCOL = ROOT / "tools/voidbot/protocol.py"
CLI = ROOT / "tools/voidbot/cli.py"
DAEMON = ROOT / "tools/voidbot/voidbotd.py"

source = HANDLER.read_text(encoding="utf-8")
start = source.index("if (spellEnum == Spells.HOME_TELEPORT)")
end = source.index("\n\t\tboolean canTeleport = true;", start)
policy = source[start:end]

for required in (
    "player.getLocation().inWilderness()",
    "player.getDuel().isDuelActive()",
    "player.inCombat() && player.getOpponent() instanceof Player",
):
    assert required in policy, f"Home Teleport policy is missing: {required}"

assert "getCombatTimer" not in policy, "Home Teleport must not gain a post-combat timer"
assert source.index("if (spellEnum == Spells.HOME_TELEPORT)") < source.index(
    "if (player.getLocation().wildernessLevel() >= 20", start
), "Home Teleport Wilderness policy must run before the ordinary level-20 teleport rule"

protocol = PROTOCOL.read_text(encoding="utf-8")
cli = CLI.read_text(encoding="utf-8")
daemon = DAEMON.read_text(encoding="utf-8")
assert '"CAST_ON_SELF": 137' in protocol
assert 'cmd == "cast-self"' in cli
assert 'self.send("CAST_ON_SELF", P.BitWriter().u16(spell).b)' in daemon

print("Home Teleport policy contract passed")
