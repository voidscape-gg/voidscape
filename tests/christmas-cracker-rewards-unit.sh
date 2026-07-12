#!/usr/bin/env bash
# Deterministic server/shared-client/Workbench reward-table contract.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
SERVER_SOURCE="$REPO/server/plugins/com/openrsc/server/plugins/authentic/misc/ChristmasCracker.java"
SERVER_TEST="$REPO/tests/java/com/openrsc/server/plugins/authentic/misc/ChristmasCrackerRewardsTest.java"
CLIENT_SOURCE="$REPO/Client_Base/src/orsc/mudclient.java"
CLIENT_TEST="$REPO/tests/java/orsc/ChristmasCrackerClientRewardsTest.java"

python3 - "$REPO" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
server = (root / "server/plugins/com/openrsc/server/plugins/authentic/misc/ChristmasCracker.java").read_text()
client = (root / "Client_Base/src/orsc/mudclient.java").read_text()
workbench = (root / "PC_Client/src/orsc/WorkbenchServer.java").read_text()
launch_config = (root / "server/voidscape-launch.conf").read_text()

def array_body(source, name):
    match = re.search(rf"\b{name}\s*=\s*\{{(.*?)\}};", source, re.S)
    assert match, name
    return match.group(1)

server_holiday = re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", array_body(server, "HOLIDAY_RARE_IDS"))
server_party = re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", array_body(server, "PARTY_HAT_IDS"))
server_custom_party = re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", array_body(server, "CUSTOM_PARTY_HAT_IDS"))
client_holiday = [int(value) for value in re.findall(r"\d+", array_body(client, "CHRISTMAS_CRACKER_HOLIDAY_RARES"))]
client_party = [int(value) for value in re.findall(r"\d+", array_body(client, "CHRISTMAS_CRACKER_PARTY_HATS"))]
client_custom_party = [int(value) for value in re.findall(r"\d+", array_body(client, "CHRISTMAS_CRACKER_CUSTOM_PARTY_HATS"))]

assert server_party == [
    "PINK_PARTY_HAT", "BLUE_PARTY_HAT", "GREEN_PARTY_HAT",
    "WHITE_PARTY_HAT", "RED_PARTY_HAT", "YELLOW_PARTY_HAT",
], server_party
assert client_party == [576, 577, 578, 579, 580, 581], client_party
assert server_custom_party == server_party + ["BLACK_PARTY_HAT"], server_custom_party
assert client_custom_party == client_party + [1582], client_custom_party
assert server_holiday == [
    "PUMPKIN", "EASTER_EGG", "GREEN_HALLOWEEN_MASK", "RED_HALLOWEEN_MASK",
    "BLUE_HALLOWEEN_MASK", "SANTAS_HAT", "BUNNY_EARS", "SCYTHE",
], server_holiday
assert client_holiday == [422, 677, 828, 831, 832, 971, 1156, 1289], client_holiday
assert 1582 not in client_party and 1582 not in client_holiday
assert "ItemId.BLACK_PARTY_HAT" not in array_body(server, "PARTY_HAT_IDS")
assert "ItemId.BLACK_PARTY_HAT" not in array_body(server, "HOLIDAY_RARE_IDS")
assert "Config.S_WANT_CUSTOM_SPRITES" in client[client.index("private static int randomChristmasCrackerPartyHat"):]
assert re.search(r"(?m)^\s*custom_sprites:\s*false\b", launch_config), \
    "launch must retain the standard black-free party-hat pool"
assert "markReplacementEligibility(player, result.itemId);" in server
assert "hasCatalogID" not in server and "countId(" not in server, "cracker rewards must not reroll owned holiday items"

validator_start = workbench.index("private static boolean validChristmasCrackerFixtureItem")
validator_end = workbench.index("private static String subscriptionVendorScenarioJson", validator_start)
validator = workbench[validator_start:validator_end]
assert validator.count("mudclient.isValidChristmasCrackerResult") == 3
for stale_id in (422, 677, 828, 831, 832, 971, 1156, 1289, 1582):
    assert str(stale_id) not in validator, f"Workbench duplicated reward id {stale_id}"
print("Christmas cracker server/client/Workbench roster sync passed.")
PY

if [ ! -f "$REPO/server/core.jar" ] || [ ! -f "$REPO/server/plugins.jar" ] \
	|| [ ! -f "$REPO/Client_Base/Open_RSC_Client.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/christmas-cracker-rewards.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/server" "$TMP/client"
javac -source 8 -target 8 \
	-cp "$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	-d "$TMP/server" "$SERVER_SOURCE" "$SERVER_TEST"
java -cp "$TMP/server:$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	com.openrsc.server.plugins.authentic.misc.ChristmasCrackerRewardsTest

javac -source 8 -target 8 -cp "$REPO/Client_Base/Open_RSC_Client.jar" \
	-d "$TMP/client" "$CLIENT_SOURCE" "$CLIENT_TEST"
java -cp "$TMP/client:$REPO/Client_Base/Open_RSC_Client.jar" \
	orsc.ChristmasCrackerClientRewardsTest
