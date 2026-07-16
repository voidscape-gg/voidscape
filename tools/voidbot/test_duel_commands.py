"""Focused wire-contract tests for voidbot's player-duel command surface."""
import hashlib
import json
import os
import struct
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import protocol as P
from voidbotd import Daemon, GameState, UsageError, parse_duel_offer_specs


def canonical_duel_context(proof_id):
    """Build the same compact VSDPCTX3 shape used by the server encoder."""
    context = bytearray(P.DUEL_PROOF_CONTEXT_MAGIC)
    context.extend(struct.pack(">IIII", P.DUEL_PROOF_CONTEXT_VERSION,
                               P.DUEL_PROOF_PROTOCOL_VERSION,
                               P.DUEL_PROOF_RNG_VERSION,
                               P.DUEL_PROOF_FORMULA_VERSION))
    context.extend(proof_id)
    context.append(0x02)  # No Magic
    context.extend(struct.pack(">i", 40))
    for ordinal, player_id, username in ((0, 7, b"Alpha"), (1, 42, b"Beta")):
        context.append(ordinal)
        context.extend(struct.pack(">i", player_id))
        context.append(len(username))
        context.extend(username)
        context.extend(struct.pack(">i", 50 + ordinal))
        context.extend(struct.pack(">iiiiiiiiii",
                                   50, 60, 40, 50, 55, 60, 45, 50, 30, 40))
        context.append(ordinal)  # valid combat style 0/1
        context.extend(struct.pack(">iii", 12, 13, 14))
        context.extend(b"\x00\x00\x00")  # cape eligibility
        context.extend(struct.pack(">i", 0))  # prayer mask
        context.append(14)
        for _ in range(14):
            context.extend(struct.pack(">iiB", -1, 0, 0))
        context.append(0)  # no recoil ring
        context.extend(struct.pack(">i", 0))
        context.append(1)  # one stake row
        context.extend(struct.pack(">BiiB", 0, 10 + ordinal, 100 + ordinal, 0))
    return bytes(context)


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.want_bank_notes = True
        self.names = {10: "Coins", 20: "Sword"}
        self.sent = []
        self._duel_proof = None

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


class DuelCommandWireTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_opcode_contract(self):
        self.assertEqual(103, P.OUT["PLAYER_DUEL"])
        self.assertEqual(8, P.OUT["DUEL_FIRST_SETTINGS_CHANGED"])
        self.assertEqual(33, P.OUT["DUEL_OFFER_ITEM"])
        self.assertEqual(176, P.OUT["DUEL_FIRST_ACCEPTED"])
        self.assertEqual(77, P.OUT["DUEL_SECOND_ACCEPTED"])
        self.assertEqual(197, P.OUT["DUEL_DECLINED"])
        self.assertEqual(29, P.OUT["COMBAT_STYLE_CHANGED"])

    def test_request_and_full_offer_replacement_payloads(self):
        result = self.bot.handle({"cmd": "duel-request", "args": {"server_index": "321"}})
        self.assertTrue(result["ok"])
        self.assertEqual(("PLAYER_DUEL", bytes.fromhex("0141")), self.bot.sent[-1])

        result = self.bot.handle({
            "cmd": "duel-offer",
            "args": {"items": ["10:3", "20:2:noted"]},
        })
        self.assertTrue(result["ok"])
        self.assertEqual(
            ("DUEL_OFFER_ITEM", bytes.fromhex("02 000a 00000003 0000 0014 00000002 0001")),
            self.bot.sent[-1],
        )
        self.assertEqual(2, len(self.bot.st.duel["own_offer"]))

        self.bot.handle({"cmd": "duel-offer", "args": {"items": []}})
        self.assertEqual(("DUEL_OFFER_ITEM", b"\x00"), self.bot.sent[-1])

    def test_rules_accept_confirm_decline_and_style_payloads(self):
        result = self.bot.handle({
            "cmd": "duel-settings",
            "args": {"no_retreat": True, "no_prayer": "true"},
        })
        self.assertTrue(result["ok"])
        self.assertEqual(("DUEL_FIRST_SETTINGS_CHANGED", b"\x01\x00\x01\x00"),
                         self.bot.sent[-1])

        for command, opcode in (
                ("duel-accept", "DUEL_FIRST_ACCEPTED"),
                ("duel-confirm", "DUEL_SECOND_ACCEPTED"),
                ("duel-decline", "DUEL_DECLINED")):
            self.bot.handle({"cmd": command, "args": {}})
            self.assertEqual((opcode, b""), self.bot.sent[-1])

        result = self.bot.handle({"cmd": "combat-style", "args": {"style": "aggressive"}})
        self.assertEqual({"ok": True, "style": 1, "name": "aggressive"}, result)
        self.assertEqual(("COMBAT_STYLE_CHANGED", b"\x01"), self.bot.sent[-1])
        self.assertEqual(1, self.bot.st.combat_style)

    def test_invalid_offer_and_style_fail_before_sending(self):
        with self.assertRaises(UsageError):
            parse_duel_offer_specs(["10:0"])
        result = self.bot.handle({"cmd": "combat-style", "args": {"style": "reckless"}})
        self.assertFalse(result["ok"])
        self.assertTrue(result["error"].startswith("usage:"))
        self.assertEqual([], self.bot.sent)


class DuelStateDecodeTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_server_index_and_duel_lifecycle_decode(self):
        self.bot.decode(25, bytes.fromhex("0141 0900 0900 0000 03b0"))
        self.assertEqual(321, self.bot.st.server_index)
        self.assertEqual(321, self.bot.snapshot("position")["position"]["server_index"])

        self.bot.decode(176, bytes.fromhex("007b"))
        self.assertEqual("offer", self.bot.st.duel["phase"])
        self.assertEqual(123, self.bot.st.duel["opponent_server_index"])

        # Server DUEL_ITEMS uses a one-byte noted flag, unlike the client's
        # two-byte noted field in DUEL_OFFER_ITEM.
        self.bot.decode(6, bytes.fromhex("01 000a 01 00000003"))
        self.assertEqual(
            [{"slot": 0, "id": 10, "name": "Coins", "amount": 3, "noted": True}],
            self.bot.st.duel["opponent_offer"],
        )

        self.bot.decode(30, b"\x01\x00\x01\x00")
        self.assertTrue(self.bot.st.duel["settings"]["no_retreat"])
        self.assertTrue(self.bot.st.duel["settings"]["no_prayer"])
        self.bot.decode(210, b"\x01")
        self.bot.decode(253, b"\x01")
        self.assertTrue(self.bot.st.duel["own_accepted"])
        self.assertTrue(self.bot.st.duel["opponent_accepted"])

        self.bot.decode(172, b"Opponent\n")
        self.assertEqual("confirm", self.bot.st.duel["phase"])
        self.bot.decode(225, b"")
        self.assertEqual("closed", self.bot.st.duel["phase"])

        # SEND_MESSAGE: icon:i32, type:u8, info:u8, message:LF string.
        self.bot.decode(131, b"\x00\x00\x00\x00\x00\x00Commencing Duel!\n")
        self.assertEqual("combat", self.bot.st.duel["phase"])
        self.bot.decode(83, b"")
        self.assertEqual("complete", self.bot.st.duel["phase"])
        self.assertEqual("lost", self.bot.st.duel["outcome"])
        self.bot.decode(225, b"")
        self.assertEqual("complete", self.bot.st.duel["phase"])

    def test_winner_message_completes_duel(self):
        self.bot.decode(176, bytes.fromhex("007b"))
        self.bot.decode(131, b"\x00\x00\x00\x00\x00\x00Commencing Duel!\n")
        self.bot.decode(131, b"\x00\x00\x00\x00\x00\x00You have defeated Rival!\n")
        self.assertEqual("complete", self.bot.st.duel["phase"])
        self.assertEqual("won", self.bot.st.duel["outcome"])

    def test_hit_feedback_identifies_the_first_duel_attacker(self):
        # count:u16, target:u16, type 10, damage/cur/max:u8,
        # attackerType:u8, attackerIndex/maxHit:u16.
        self.bot.decode(234, bytes.fromhex(
            "0001 0005 0a 07 2b 32 01 0004 000c"
        ))
        damage_events = [event for event in self.bot.st.events
                         if event["kind"] == "player_damage"]
        self.assertEqual(1, len(damage_events))
        self.assertEqual(5, damage_events[0]["target_server_index"])
        self.assertEqual(4, damage_events[0]["attacker_server_index"])
        self.assertEqual(7, damage_events[0]["damage"])
        self.assertEqual(43, damage_events[0]["cur_hits"])
        self.assertEqual(12, damage_events[0]["attacker_max_hit"])


class DuelProofHandshakeTests(unittest.TestCase):
    PROOF_ID = bytes(range(16))
    OTHER_PROOF_ID = bytes.fromhex("cd" * 16)
    CONTEXT = canonical_duel_context(PROOF_ID)
    OTHER_CONTEXT = canonical_duel_context(OTHER_PROOF_ID)
    CONTEXT_HASH = P.duel_proof_context_hash(CONTEXT)
    OTHER_CONTEXT_HASH = P.duel_proof_context_hash(OTHER_CONTEXT)
    SERVER_COMMIT = bytes(range(64, 96))
    CLIENT_SEED = bytes(range(96, 128))
    OTHER_COMMIT = bytes.fromhex("ab" * 32)

    def setUp(self):
        self.bot = FakeDaemon()

    @staticmethod
    def _message(control, message_type=3, info=0):
        text = P.DUEL_PROOF_PREFIX + control
        return b"\x00\x00\x00\x00" + bytes([message_type, info]) + text.encode("ascii") + b"\n"

    def _decode_control(self, control):
        self.bot.decode(131, self._message(control))

    def _last_response(self):
        name, payload = self.bot.sent[-1]
        self.assertEqual("INTERFACE_OPTIONS", name)
        self.assertEqual(P.DUEL_PROOF_OPTION, payload[0])
        self.assertEqual(b"\n", payload[-1:])
        return payload[1:-1].decode("ascii")

    @staticmethod
    def _context_controls(proof_id, context):
        size = P.DUEL_PROOF_CONTEXT_CHUNK_BYTES
        total = (len(context) + size - 1) // size
        return ["v1|context|%s|%d|%d|%s" % (
            proof_id.hex(), chunk, total,
            context[chunk * size:min(len(context), (chunk + 1) * size)].hex())
                for chunk in range(total)]

    def _send_context(self, proof_id=None, context=None):
        proof_id = self.PROOF_ID if proof_id is None else proof_id
        context = self.CONTEXT if context is None else context
        for control in self._context_controls(proof_id, context):
            self._decode_control(control)

    def _commit_control(self, ordinal=0, proof_id=None, context_hash=None,
                        server_commit=None):
        proof_id = self.PROOF_ID if proof_id is None else proof_id
        context_hash = self.CONTEXT_HASH if context_hash is None else context_hash
        server_commit = self.SERVER_COMMIT if server_commit is None else server_commit
        return "v1|commit|%s|%s|%s|%d" % (
            proof_id.hex(), context_hash.hex(), server_commit.hex(), ordinal)

    def _begin_commit(self, ordinal=0):
        self._send_context()
        self._decode_control(self._commit_control(ordinal))

    def test_commit_requires_complete_context_before_entropy(self):
        controls = self._context_controls(self.PROOF_ID, self.CONTEXT)
        self._decode_control(controls[0])
        retained = self.bot._duel_proof["context_chunks"][0]
        with mock.patch("voidbotd.secrets.token_bytes",
                        return_value=self.CLIENT_SEED) as entropy:
            self._decode_control(self._commit_control(0))
        self.assertEqual("v1|fail|%s|state" % self.PROOF_ID.hex(),
                         self._last_response())
        entropy.assert_not_called()
        self.assertEqual(bytearray(len(retained)), retained)

    def test_context_chunks_are_sequential_canonical_and_bounded(self):
        controls = self._context_controls(self.PROOF_ID, self.CONTEXT)
        self._decode_control(controls[0])
        sent_before = len(self.bot.sent)
        self._decode_control(controls[0])  # exact retransmit is harmless
        self.assertEqual(sent_before, len(self.bot.sent))

        conflicting = controls[0][:-2] + ("00" if controls[0][-2:] != "00" else "01")
        retained = self.bot._duel_proof["context_chunks"][0]
        self._decode_control(conflicting)
        self.assertEqual("v1|fail|%s|state" % self.PROOF_ID.hex(),
                         self._last_response())
        self.assertEqual(bytearray(len(retained)), retained)

        self.bot._clear_duel_proof()
        self._decode_control(controls[1])
        self.assertEqual("v1|fail|%s|state" % self.PROOF_ID.hex(),
                         self._last_response())

        self.bot._clear_duel_proof()
        oversized_total = "v1|context|%s|0|513|00" % self.PROOF_ID.hex()
        self._decode_control(oversized_total)
        self.assertEqual("v1|fail|%s|malformed" % self.PROOF_ID.hex(),
                         self._last_response())

        self.bot._clear_duel_proof()
        uppercase = "v1|context|%s|0|1|AA" % self.PROOF_ID.hex()
        self._decode_control(uppercase)
        self.assertEqual("v1|fail|%s|malformed" % self.PROOF_ID.hex(),
                         self._last_response())

        self.bot._clear_duel_proof()
        self._decode_control(controls[0])
        replaced = self.bot._duel_proof["context_chunks"][0]
        other_first = self._context_controls(
            self.OTHER_PROOF_ID, self.OTHER_CONTEXT)[0]
        self._decode_control(other_first)
        self.assertEqual(bytearray(len(replaced)), replaced)
        self.assertEqual(self.OTHER_PROOF_ID.hex(),
                         self.bot._duel_proof["proof_id_text"])

    def test_context_hash_and_canonical_structure_are_checked(self):
        tampered = bytearray(self.CONTEXT)
        tampered[-1] ^= 1
        self._send_context(context=bytes(tampered))
        with mock.patch("voidbotd.secrets.token_bytes",
                        return_value=self.CLIENT_SEED) as entropy:
            self._decode_control(self._commit_control(0))
        self.assertEqual("v1|fail|%s|state" % self.PROOF_ID.hex(),
                         self._last_response())
        entropy.assert_not_called()

        self.bot._clear_duel_proof()
        malformed = bytearray(self.CONTEXT)
        malformed[0] ^= 1
        malformed_hash = P.duel_proof_context_hash(malformed)
        self._send_context(context=bytes(malformed))
        with mock.patch("voidbotd.secrets.token_bytes",
                        return_value=self.CLIENT_SEED) as entropy:
            self._decode_control(self._commit_control(0, context_hash=malformed_hash))
        self.assertEqual("v1|fail|%s|malformed" % self.PROOF_ID.hex(),
                         self._last_response())
        entropy.assert_not_called()

    def test_abort_during_context_wipes_pending_chunks(self):
        first = self._context_controls(self.PROOF_ID, self.CONTEXT)[0]
        self._decode_control(first)
        retained = self.bot._duel_proof["context_chunks"][0]
        self._decode_control("v1|abort|%s|timeout" % self.PROOF_ID.hex())
        self.assertIsNone(self.bot._duel_proof)
        self.assertEqual(bytearray(len(retained)), retained)

    def test_valid_commit_reveal_lock_flow_is_hidden_and_retains_private_witness(self):
        self._send_context()
        context_chunks = list(self.bot._duel_proof["context_chunks"])
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._decode_control(self._commit_control(0))
        self.assertTrue(all(chunk == bytearray(len(chunk)) for chunk in context_chunks))

        expected_client_commit = hashlib.sha256(
            b"Voidscape/DuelProof/v1/client-commitment"
            + self.CONTEXT_HASH + self.SERVER_COMMIT + b"\x00" + self.CLIENT_SEED
        ).digest()
        self.assertEqual(
            "v1|commit|%s|%s" % (self.PROOF_ID.hex(), expected_client_commit.hex()),
            self._last_response(),
        )

        reveal = "v1|reveal|%s|7|%s|42|%s" % (
            self.PROOF_ID.hex(), expected_client_commit.hex(), self.OTHER_COMMIT.hex())
        self._decode_control(reveal)
        self.assertEqual(
            "v1|reveal|%s|%s" % (self.PROOF_ID.hex(), self.CLIENT_SEED.hex()),
            self._last_response(),
        )

        expected_lock = hashlib.sha256(
            b"Voidscape/DuelProof/v1/final-lock"
            + struct.pack(">III", 1, 1, 1)
            + self.PROOF_ID + self.CONTEXT_HASH + self.SERVER_COMMIT
            + struct.pack(">I", 7) + expected_client_commit
            + struct.pack(">I", 42) + self.OTHER_COMMIT
        ).digest()
        lock = "v1|lock|%s|%s|%s|7|%s|42|%s|%s" % (
            self.PROOF_ID.hex(), self.CONTEXT_HASH.hex(), self.SERVER_COMMIT.hex(),
            expected_client_commit.hex(), self.OTHER_COMMIT.hex(), expected_lock.hex())
        self._decode_control(lock)
        expected_ack = "v1|ack|%s|%s" % (self.PROOF_ID.hex(), expected_lock.hex())
        self.assertEqual(expected_ack, self._last_response())
        self.assertEqual(bytearray(self.CLIENT_SEED), self.bot._duel_proof["client_seed"])

        # A repeated identical lock gets the exact same acknowledgement and leaves
        # the private witness unchanged for later receipt validation.
        self._decode_control(lock)
        self.assertEqual(expected_ack, self._last_response())
        self.assertEqual(bytearray(self.CLIENT_SEED), self.bot._duel_proof["client_seed"])

        # Closing the confirmation window to commence combat must not look like an
        # abort and erase the finalized witness.
        self.bot.decode(225, b"")
        self.assertEqual(bytearray(self.CLIENT_SEED), self.bot._duel_proof["client_seed"])

        self.assertEqual([], self.bot.st.messages)
        self.assertFalse(any(event["kind"] == "message" for event in self.bot.st.events))
        public_data = json.dumps({
            "state": self.bot.snapshot("all"),
            "events": self.bot.st.events,
        })
        self.assertNotIn(self.CLIENT_SEED.hex(), public_data)
        self.assertNotIn(P.DUEL_PROOF_PREFIX, public_data)

    def test_duplicate_commit_reuses_one_seed_and_response(self):
        control = self._commit_control(1)
        self._send_context()
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED) as entropy:
            self._decode_control(control)
            first_response = self._last_response()
            first_seed = self.bot._duel_proof["client_seed"]
            self._decode_control(control)
            second_response = self._last_response()

        self.assertEqual(first_response, second_response)
        self.assertIs(first_seed, self.bot._duel_proof["client_seed"])
        entropy.assert_called_once_with(32)
        self.assertEqual([], self.bot.st.messages)

    def test_new_proof_replaces_and_zeroes_the_previous_witness(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        previous_seed = self.bot._duel_proof["client_seed"]
        replacement_seed = bytes.fromhex("ef" * 32)
        self._send_context(self.OTHER_PROOF_ID, self.OTHER_CONTEXT)
        self.assertEqual(bytearray(32), previous_seed)
        replacement = self._commit_control(
            1, self.OTHER_PROOF_ID, self.OTHER_CONTEXT_HASH, self.SERVER_COMMIT)

        with mock.patch("voidbotd.secrets.token_bytes", return_value=replacement_seed):
            self._decode_control(replacement)

        self.assertEqual(bytearray(32), previous_seed)
        self.assertEqual(self.OTHER_PROOF_ID.hex(), self.bot._duel_proof["proof_id_text"])
        self.assertEqual(bytearray(replacement_seed), self.bot._duel_proof["client_seed"])

    def test_entropy_exception_fails_closed_and_is_sticky(self):
        control = self._commit_control(0)
        self._send_context()
        with mock.patch("voidbotd.secrets.token_bytes", side_effect=OSError("no entropy")):
            self._decode_control(control)
        self.assertEqual(
            "v1|fail|%s|entropy" % self.PROOF_ID.hex(), self._last_response())
        self.assertEqual("rejected", self.bot._duel_proof["phase"])

        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED) as entropy:
            self._decode_control(control)
        self.assertEqual(
            "v1|fail|%s|entropy" % self.PROOF_ID.hex(), self._last_response())
        entropy.assert_not_called()

    def test_malformed_control_is_hidden_and_fails_when_id_is_valid(self):
        malformed = "v1|commit|%s|%s|%s|0" % (
            self.PROOF_ID.hex(), "zz" * 32, self.SERVER_COMMIT.hex())
        self._decode_control(malformed)
        self.assertEqual(
            "v1|fail|%s|malformed" % self.PROOF_ID.hex(),
            self._last_response(),
        )
        self.assertEqual("rejected", self.bot._duel_proof["phase"])
        self.assertEqual("malformed", self.bot._duel_proof["rejected_reason"])
        self.assertEqual([], self.bot.st.messages)
        self.assertEqual([], self.bot.st.events)

    def test_abort_hides_control_and_zeroes_pending_seed(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        seed_reference = self.bot._duel_proof["client_seed"]

        self._decode_control("v1|abort|%s|timeout" % self.PROOF_ID.hex())

        self.assertIsNone(self.bot._duel_proof)
        self.assertEqual(bytearray(32), seed_reference)
        self.assertEqual([], self.bot.st.messages)
        self.assertEqual([], self.bot.st.events)

    def test_retreat_abort_is_accepted_and_clears_active_witness(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        seed_reference = self.bot._duel_proof["client_seed"]
        responses_before_abort = len(self.bot.sent)

        self._decode_control("v1|abort|%s|retreat" % self.PROOF_ID.hex())

        self.assertIsNone(self.bot._duel_proof)
        self.assertEqual(bytearray(32), seed_reference)
        self.assertEqual(responses_before_abort, len(self.bot.sent))

    def test_foreign_abort_cannot_clear_active_witness(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        seed_reference = self.bot._duel_proof["client_seed"]

        self._decode_control("v1|abort|%s|timeout" % self.OTHER_PROOF_ID.hex())

        self.assertIs(seed_reference, self.bot._duel_proof["client_seed"])
        self.assertEqual(bytearray(self.CLIENT_SEED), seed_reference)

    def test_abort_requires_exact_shape_and_allowlisted_reason(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        seed_reference = self.bot._duel_proof["client_seed"]

        self._decode_control("v1|abort|%s|anything" % self.PROOF_ID.hex())

        self.assertEqual(
            "v1|fail|%s|malformed" % self.PROOF_ID.hex(), self._last_response())
        self.assertEqual(bytearray(32), seed_reference)
        self.assertEqual("rejected", self.bot._duel_proof["phase"])

    def test_reveal_rejects_noncanonical_player_id_and_commit_mismatch(self):
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        client_commit = P.duel_proof_client_commitment(
            self.CONTEXT_HASH, self.SERVER_COMMIT, 0, self.CLIENT_SEED)
        reveal = "v1|reveal|%s|07|%s|42|%s" % (
            self.PROOF_ID.hex(), client_commit.hex(), self.OTHER_COMMIT.hex())
        self._decode_control(reveal)
        self.assertEqual(
            "v1|fail|%s|malformed" % self.PROOF_ID.hex(), self._last_response())

        self.bot._clear_duel_proof()
        with mock.patch("voidbotd.secrets.token_bytes", return_value=self.CLIENT_SEED):
            self._begin_commit(0)
        reveal = "v1|reveal|%s|7|%s|42|%s" % (
            self.PROOF_ID.hex(), self.OTHER_COMMIT.hex(), client_commit.hex())
        self._decode_control(reveal)
        self.assertEqual(
            "v1|fail|%s|state" % self.PROOF_ID.hex(), self._last_response())

    def test_only_senderless_quest_controls_are_intercepted(self):
        control = self._commit_control(0)
        self.bot.decode(131, self._message(control, message_type=0, info=0))
        self.bot.decode(131, self._message(control, message_type=3, info=1))

        self.assertEqual(2, len(self.bot.st.messages))
        self.assertEqual(2, len([event for event in self.bot.st.events
                                if event["kind"] == "message"]))
        self.assertEqual([], self.bot.sent)


if __name__ == "__main__":
    unittest.main()
