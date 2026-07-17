#!/usr/bin/env python3
"""Source contract for production-only regular-player command restrictions."""

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/CommandHandler.java"
REGULAR = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java"
COMMANDS_DOC = ROOT / "Commands.md"

EXPECTED = {
    "uniqueonline", "onlinelist", "onlinelistlocs",
    "time", "date", "datetime", "coords", "groups", "ranks", "gang",
    "kills", "kc", "minigamelog", "clientlimitations",
    "maxplayersperip", "mppi", "getpidlesscatching", "tellpidlesscatching", "pidless",
    "arena", "voidarena", "setglobalmessagecolor", "titles", "title", "b",
    "qoloptout", "qoloptoutconfirm", "certoptout", "certoptoutconfirm",
    "togglereceipts", "togglenpckcmessages", "oldtrade", "notradeconfirm",
    "language", "setlanguage",
    "bankpinoptin", "bankpin_optin", "bank_pin_opt_in",
    "bankpinoptout", "bankpin_optout", "bank_pin_opt_out",
    "skiptutorial", "getholidaydrop", "checkholidaydrop", "checkholidayevent", "drop",
}

source = HANDLER.read_text(encoding="utf-8")
match = re.search(
    r"PRODUCTION_REGULAR_PLAYER_BLOCKED_COMMANDS\s*=\s*new HashSet<String>\(Arrays\.asList\((.*?)\)\);",
    source,
    re.DOTALL,
)
assert match is not None, "regular-player production deny set is missing"
actual = set(re.findall(r'"([a-z0-9_]+)"', match.group(1)))
assert actual == EXPECTED, (
    f"regular-player deny set mismatch; missing={sorted(EXPECTED - actual)} "
    f"unexpected={sorted(actual - EXPECTED)}"
)

assert "if (!player.getWorld().getServer().getConfig().PRODUCTION_COMMAND_LOCKDOWN) return false;" in source
assert "player.getGroupID() == Group.USER && PRODUCTION_REGULAR_PLAYER_BLOCKED_COMMANDS.contains(cmd)" in source
assert source.index("player.getGroupID() == Group.USER") < source.index("if (player.isOwner()) return false;")

regular_source = REGULAR.read_text(encoding="utf-8")
help_source = regular_source[regular_source.index("private static final String[] pageZeroCommands") :]
help_commands = set(re.findall(r"::([a-z0-9_]+)", help_source.lower()))
assert help_commands.isdisjoint(EXPECTED), (
    f"restricted commands still appear in ::commands help: {sorted(help_commands & EXPECTED)}"
)

commands_doc = COMMANDS_DOC.read_text(encoding="utf-8")
regular_doc = commands_doc.split("Regular Player Commands", 1)[1].split("## Voidscape dev/testing commands", 1)[0]
documented_commands = set(re.findall(r"::([a-z0-9_]+)", regular_doc.lower()))
assert documented_commands.isdisjoint(EXPECTED), (
    f"restricted commands still appear in regular-player documentation: {sorted(documented_commands & EXPECTED)}"
)

for relative in (
    "server/src/com/openrsc/server/content/VoidVeteranTour.java",
    "server/src/com/openrsc/server/net/rsc/ActionSender.java",
    "server/src/com/openrsc/server/net/rsc/handlers/PlayerTradeHandler.java",
    "web/portal/features.html",
):
    player_surface = (ROOT / relative).read_text(encoding="utf-8")
    advertised = set(re.findall(r"::([a-z0-9_]+)", player_surface.lower()))
    assert advertised.isdisjoint(EXPECTED), (
        f"restricted commands still advertised by {relative}: {sorted(advertised & EXPECTED)}"
    )

print(f"Production regular-player command lockdown contract passed ({len(EXPECTED)} command tokens)")
