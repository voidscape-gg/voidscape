#!/usr/bin/env bash
# Decoder regression for the nearby-player indexes used by live 1v1 QA.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

PYTHONPATH="$REPO/tools/voidbot" python3 - <<'PY'
from voidbotd import Daemon, GameState


def packet(fields):
    bits = "".join(f"{value & ((1 << width) - 1):0{width}b}" for value, width in fields)
    bits += "0" * ((8 - len(bits) % 8) % 8)
    return int(bits, 2).to_bytes(len(bits) // 8, "big")


daemon = Daemon.__new__(Daemon)
daemon.st = GameState()

# Local (100,2000), zero retained players, then new player index 7 at (+2,-1).
daemon._decode_players(packet([
    (100, 11), (2000, 13), (2, 4), (0, 8),
    (7, 11), (2, 6), (63, 6), (4, 4),
]))
assert daemon.st.x == 100 and daemon.st.y == 2000
assert daemon.st.players == [{"server_index": 7, "x": 102, "y": 1999}]

# Retain that player and move direction 3 (+1,+1).
daemon._decode_players(packet([
    (100, 11), (2000, 13), (2, 4), (1, 8),
    (1, 1), (0, 1), (3, 3),
]))
assert daemon.st.players == [{"server_index": 7, "x": 103, "y": 2000}]

# Retained player removal (animation/update discriminator 3).
daemon._decode_players(packet([
    (100, 11), (2000, 13), (2, 4), (1, 8),
    (1, 1), (1, 1), (3, 2),
]))
assert daemon.st.players == []
assert any(event["kind"] == "player_removed" and event["server_index"] == 7
           for event in daemon.st.events)
print("voidbot nearby-player coordinate decoder tests passed.")
PY
