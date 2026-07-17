#!/usr/bin/env python3
"""Small source contract for the launch-day ordinary-duel and XP controls."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


player = read("server/src/com/openrsc/server/model/entity/player/Player.java")
skills = read("server/src/com/openrsc/server/model/Skills.java")
handler = read("server/src/com/openrsc/server/net/rsc/handlers/PlayerDuelHandler.java")
control = read("server/src/com/openrsc/server/content/OrdinaryDuelControl.java")
world = read("server/src/com/openrsc/server/model/world/World.java")
commands = read("server/src/com/openrsc/server/net/rsc/handlers/CommandHandler.java")
regular = read("server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java")
admins = read("server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java")
void_arena = read("server/src/com/openrsc/server/content/voidarena/VoidArena.java")

assert "shouldBlockExperienceGain(skill, true)" in player
assert "getDuel().hasDuelCombatStarted()" in player
assert "shouldBlockExperienceGain(skill, true)" in skills
assert 'message("Duels do not award experience.")' in handler
assert handler.count("rejectDisabledDuel(") >= 5
assert "getOrdinaryDuelControl().isEnabled()" in handler

assert 'CACHE_KEY = "void_dueling_enabled"' in control
assert ".atomically(() ->" in control
assert "enabled = nextEnabled;" in control
assert "hasDuelCombatStarted()" in control
assert "getOrdinaryDuelControl().load();" in world

assert "isPlayerExperienceFreezeCommand(player, cmd, args)" in commands
assert "handleExperienceFreeze(player, command, args)" in regular
assert "isSelfExperienceFreezeSyntax(args)" in admins
assert "controlDueling(player, command, args)" in admins
assert "OrdinaryDuelControl" not in void_arena

print("Ordinary duel and XP control contract tests passed")
