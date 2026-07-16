"""Focused packet/state tests for voidbot nearby-player observation."""
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from voidbotd import Daemon, GameState


def packed_bits(*fields):
    """Pack (value, width) pairs MSB-first like PacketBuilder.writeBits."""
    bit_count = sum(width for _, width in fields)
    payload = bytearray((bit_count + 7) // 8)
    bit_position = 0
    for value, width in fields:
        value &= (1 << width) - 1
        for shift in range(width - 1, -1, -1):
            if value & (1 << shift):
                payload[bit_position >> 3] |= 1 << (7 - (bit_position & 7))
            bit_position += 1
    return bytes(payload)


def appearance_update(server_index, name="Quiet Oak"):
    """Build one current-custom-client SEND_UPDATE_PLAYERS type-5 entry."""
    payload = bytearray(struct.pack(">H", 1))
    payload.extend(struct.pack(">HB", server_index, 5))
    payload.extend(name.encode("latin1") + b"\n")
    payload.append(3)
    payload.extend(struct.pack(">HHH", 1, 2, 3))
    payload.extend(bytes((10, 8, 14, 0)))  # hair/top/trousers/skin
    payload.extend(bytes((23, 0)))         # combat level/overhead type
    payload.append(0)                      # no clan
    payload.extend(bytes((0, 0, 0)))       # visible/vulnerable/no group
    payload.extend(struct.pack(">i", 0))   # icon
    payload.extend(b"\n")                 # no title
    payload.extend(bytes((0, 0)))          # title tier/hair style
    payload.extend(b"\n")                 # no honorific
    payload.append(0)                      # honorific tier
    return bytes(payload)


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()


class PlayerCoordinateDecodeTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_add_identify_move_and_remove_nearby_player(self):
        # Header: bot at (100, 200), no cached players. New player 77 is at
        # offset (+3, -2) with sprite 4.
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (2, 4), (0, 8),
            (77, 11), (3, 6), (-2, 6), (4, 4),
        ))
        player = self.bot.snapshot("players")["players"][0]
        self.assertEqual(
            {"server_index": 77, "name": None, "x": 103, "y": 198,
             "sprite": 4, "appearance": None},
            player,
        )

        self.bot.decode(234, appearance_update(77))
        player = self.bot.snapshot("players")["players"][0]
        self.assertEqual("Quiet Oak", player["name"])
        self.assertEqual(23, player["appearance"]["combat_level"])
        self.assertEqual([1, 2, 3], player["appearance"]["worn_items"])

        # Cached update: movement direction 3 increments both coordinates.
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (2, 4), (1, 8),
            (1, 1), (0, 1), (3, 3),
        ))
        player = self.bot.snapshot("players")["players"][0]
        self.assertEqual((104, 199, 3),
                         (player["x"], player["y"], player["sprite"]))
        self.assertEqual("Quiet Oak", player["name"])

        # Cached update type 1 with high sprite bits 3 is the AOI removal marker.
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (2, 4), (1, 8),
            (1, 1), (1, 1), (3, 2),
        ))
        self.assertEqual([], self.bot.snapshot("players")["players"])
        self.assertNotIn(77, self.bot.st._player_appearances)

    def test_appearance_before_coordinates_is_applied_when_player_arrives(self):
        self.bot.decode(234, appearance_update(88, "River Ash"))
        self.bot.decode(191, packed_bits(
            (50, 11), (60, 13), (0, 4), (0, 8),
            (88, 11), (-1, 6), (1, 6), (6, 4),
        ))
        player = self.bot.snapshot("players")["players"][0]
        self.assertEqual("River Ash", player["name"])
        self.assertEqual((49, 61), (player["x"], player["y"]))

    def test_remove_then_readd_same_frame_retains_identity(self):
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (0, 4), (0, 8),
            (77, 11), (1, 6), (0, 6), (0, 4),
        ))
        self.bot.decode(234, appearance_update(77))

        # A moving player can fail the retained-player predicate and be encoded
        # as both removed and newly visible in one server tick.
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (0, 4), (1, 8),
            (1, 1), (1, 1), (3, 2),
            (77, 11), (4, 6), (0, 6), (2, 4),
        ))
        players = self.bot.snapshot("players")["players"]
        self.assertEqual(1, len(players))
        self.assertEqual("Quiet Oak", players[0]["name"])
        self.assertEqual((104, 200), (players[0]["x"], players[0]["y"]))
        self.assertFalse(any(event["kind"] == "player_removed"
                             for event in self.bot.st.events))

    def test_appearance_entry_does_not_hide_following_combat_update(self):
        appearance = bytearray(appearance_update(77))
        appearance[0:2] = struct.pack(">H", 2)
        appearance.extend(bytes.fromhex("0005 0a 07 2b 32 01 0004 000c"))
        self.bot.decode(234, bytes(appearance))
        damage = [event for event in self.bot.st.events
                  if event["kind"] == "player_damage"]
        self.assertEqual(1, len(damage))
        self.assertEqual(4, damage[0]["attacker_server_index"])

    def test_local_damage_and_hp_sync_keep_hits_snapshot_authoritative(self):
        self.bot.st.server_index = 5
        self.bot.st.skills["hits"] = {"cur": 10, "max": 10, "xp": 1000}

        # One hit-feedback damage update for the local player.
        self.bot.decode(234, bytes.fromhex("0001 0005 0a 03 06 0a 02 0289 0003"))
        self.assertEqual({"cur": 6, "max": 10, "xp": 1000}, self.bot.st.skills["hits"])

        # Custom type 9 is an authoritative HP synchronization without damage.
        self.bot.decode(234, bytes.fromhex("0001 0005 09 09 0a"))
        self.assertEqual({"cur": 9, "max": 10, "xp": 1000}, self.bot.st.skills["hits"])

        # A nearby player's updates must not overwrite the local snapshot.
        self.bot.decode(234, bytes.fromhex("0001 0006 02 01 02 14"))
        self.assertEqual({"cur": 9, "max": 10, "xp": 1000}, self.bot.st.skills["hits"])

    def test_cached_count_mismatch_invalidates_aoi_for_absence_waits(self):
        self.bot.st.players = [{
            "server_index": 77, "name": "Quiet Oak", "x": 1, "y": 1,
            "sprite": 0, "appearance": None,
        }]
        self.bot.st._players_initialized = True
        self.bot.decode(191, packed_bits(
            (100, 11), (200, 13), (0, 4), (0, 8),
        ))
        self.assertFalse(self.bot.st._players_initialized)
        errors = [event for event in self.bot.st.events
                  if event["kind"] == "decode_error"]
        self.assertIn("does not match", errors[-1]["error"])


class PlayerWaitTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()
        self.bot.st.x = 100
        self.bot.st.y = 100
        self.bot.st._players_initialized = True
        self.bot.st.players = [{
            "server_index": 77,
            "name": "Quiet Oak",
            "x": 102,
            "y": 101,
            "sprite": 0,
            "appearance": None,
        }]

    def test_presence_absence_and_near_waits(self):
        present = self.bot.wait({
            "condition": "player-present", "name": "quiet oak", "timeout": 0.1,
        })
        self.assertTrue(present["matched"])
        self.assertEqual(77, present["players"][0]["server_index"])

        near = self.bot.wait({
            "condition": "player-near", "server_index": 77,
            "radius": 3, "timeout": 0.1,
        })
        self.assertTrue(near["matched"])
        self.assertEqual(3, near["distance"])

        absent = self.bot.wait({
            "condition": "player-absent", "name": "Somebody Else", "timeout": 0.1,
        })
        self.assertTrue(absent["matched"])

    def test_player_wait_requires_name_or_server_index(self):
        result = self.bot.wait({"condition": "player-present", "timeout": 0.1})
        self.assertFalse(result["ok"])
        self.assertTrue(result["error"].startswith("usage:"))

    def test_name_absence_waits_for_initialized_named_aoi(self):
        self.bot.st._players_initialized = False
        result = self.bot.wait({
            "condition": "player-absent", "name": "Missing", "timeout": 0.01,
        })
        self.assertFalse(result["matched"])

        self.bot.st._players_initialized = True
        self.bot.st.players[0]["name"] = None
        result = self.bot.wait({
            "condition": "player-absent", "name": "Missing", "timeout": 0.01,
        })
        self.assertFalse(result["matched"])

        self.bot.st.players[0]["name"] = "Known Player"
        result = self.bot.wait({
            "condition": "player-absent", "name": "Missing", "timeout": 0.1,
        })
        self.assertTrue(result["matched"])


if __name__ == "__main__":
    unittest.main()
