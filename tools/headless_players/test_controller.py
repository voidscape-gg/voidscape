import copy
import json
import random
import socket
import tempfile
import threading
import time
import unittest
from pathlib import Path

from tools.headless_players.controller import (
    ControlError,
    ControlClient,
    FleetController,
    PlayerBrain,
    RosterError,
    load_roster,
    main,
)


class FakeClock:
    def __init__(self, now=10.0):
        self.now = float(now)

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += seconds


class FakeClient:
    def __init__(self, state=None):
        self.commands = []
        self.current_state = state

    def state(self):
        return self.current_state

    def command(self, cmd, args=None):
        self.commands.append((cmd, dict(args or {})))
        return {"ok": True}


class FailOnceClient(FakeClient):
    def __init__(self, state=None):
        super().__init__(state)
        self.fail_next = None

    def command(self, cmd, args=None):
        if cmd == self.fail_next:
            self.fail_next = None
            raise ControlError(f"injected {cmd} failure")
        return super().command(cmd, args)


def item(item_id, slot=0, amount=1, *, wielded=False):
    return {
        "id": item_id,
        "slot": slot,
        "amount": amount,
        "noted": False,
        "wielded": wielded,
    }


def state_at(
    x,
    y,
    *,
    inventory=None,
    bank_open=False,
    bank_items=None,
    shop_open=False,
    messages=None,
    ground_items=None,
    npcs=None,
    mining=1,
    hits=(10, 10),
):
    return {
        "logged_in": True,
        "position": {"x": x, "y": y},
        "appearance": {"open": False},
        "dialog": {"open": False, "options": []},
        "inventory": list(inventory or []),
        "bank": {"open": bank_open, "items": list(bank_items or [])},
        "shop": {"open": shop_open},
        "skills": {
            "mining": {"cur": mining, "max": mining, "xp": 0},
            "woodcutting": {"cur": 1, "max": 1, "xp": 0},
            "fishing": {"cur": 1, "max": 1, "xp": 0},
            "hits": {"cur": hits[0], "max": hits[1], "xp": 0},
        },
        "players": [],
        "npcs": list(npcs or []),
        "ground_items": list(ground_items or []),
        "messages": list(messages or []),
    }


class RosterTests(unittest.TestCase):
    def test_exact_approved_roster(self):
        profiles = load_roster()
        self.assertEqual(list(range(10)), [profile["slot"] for profile in profiles])
        self.assertEqual(
            ["fireee", "ch0p", "ultraz", "vinny", "six-seven", "college", "pknskate", "p-h-i-s-h", "fulani", "az"],
            [profile["id"] for profile in profiles],
        )
        self.assertEqual("Six Seven", profiles[4]["username"])
        self.assertEqual("P H I S H", profiles[7]["username"])

    def test_roster_rejects_partial_fleet(self):
        payload = json.loads(Path("tools/headless_players/roster.json").read_text())
        payload["players"].pop()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "roster.json")
            path.write_text(json.dumps(payload))
            with self.assertRaisesRegex(RosterError, "exactly 10"):
                load_roster(path)

    def test_gather_and_training_nodes_need_two_distinct_approaches(self):
        cases = ((9, "nodes"), (4, "training"))
        for player_index, node_group in cases:
            with self.subTest(player=player_index, node_group=node_group):
                payload = json.loads(
                    Path("tools/headless_players/roster.json").read_text()
                )
                activity = payload["players"][player_index]["activity"]
                nodes = (
                    activity["nodes"]
                    if node_group == "nodes"
                    else activity["training"]["nodes"]
                )
                approach = dict(nodes[0]["approach"])
                for node in nodes:
                    node["approach"] = dict(approach)
                with tempfile.TemporaryDirectory() as tmp:
                    path = Path(tmp, "roster.json")
                    path.write_text(json.dumps(payload))
                    with self.assertRaisesRegex(
                        RosterError, "two physically distinct approaches"
                    ):
                        load_roster(path)

    def test_every_route_connects_task_and_bank(self):
        for profile in load_roster():
            activity = profile["activity"]
            route = activity["bank"]["route"]
            nodes = list(activity["nodes"])
            if activity.get("training"):
                nodes += activity["training"]["nodes"]
            first = route[0]
            task_distance = min(
                abs(first["x"] - node["approach"]["x"]) + abs(first["y"] - node["approach"]["y"])
                for node in nodes
            )
            bank = activity["bank"]["approach"]
            bank_distance = abs(route[-1]["x"] - bank["x"]) + abs(route[-1]["y"] - bank["y"])
            self.assertLessEqual(task_distance, activity.get("taskRadius", 8), profile["id"])
            self.assertLessEqual(bank_distance, activity["bank"].get("searchRadius", 5), profile["id"])
            if profile.get("stagingBank"):
                staging = profile["stagingBank"]
                spawn = profile["travelRoute"][0]
                self.assertLessEqual(
                    abs(staging["route"][0]["x"] - spawn["x"]) + abs(staging["route"][0]["y"] - spawn["y"]),
                    4,
                    profile["id"],
                )
            if profile.get("journey"):
                journey = profile["journey"]
                for leg_name in ("funding", "port", "karamja"):
                    leg = journey[leg_name]
                    self.assertLessEqual(
                        abs(leg["route"][-1]["x"] - leg["approach"]["x"])
                        + abs(leg["route"][-1]["y"] - leg["approach"]["y"]),
                        leg.get("searchRadius", 5),
                        f"{profile['id']}:{leg_name}",
                    )
                journey_arrival = journey["ardougne"]["route"][-1]
                task_route_start = activity["bank"]["route"][0]
                self.assertLessEqual(
                    abs(journey_arrival["x"] - task_route_start["x"])
                    + abs(journey_arrival["y"] - task_route_start["y"]),
                    4,
                    f"{profile['id']}:ardougne-arrival",
                )

    def test_audited_full_block_targets_are_not_used_as_waypoints_or_approaches(self):
        blocked = {(200, 650), (240, 650), (365, 520), (395, 503), (136, 643)}
        for profile in load_roster():
            points = [(p["x"], p["y"]) for p in profile["travelRoute"]]
            if profile.get("stagingBank"):
                points += [(p["x"], p["y"]) for p in profile["stagingBank"]["route"]]
            if profile.get("journey"):
                for leg_name in ("funding", "port", "karamja"):
                    leg = profile["journey"][leg_name]
                    points += [(p["x"], p["y"]) for p in leg["route"]]
                    points.append((leg["approach"]["x"], leg["approach"]["y"]))
                points += [
                    (p["x"], p["y"]) for p in profile["journey"]["ardougne"]["route"]
                ]
            points += [(p["x"], p["y"]) for p in profile["activity"]["bank"]["route"]]
            nodes = list(profile["activity"]["nodes"])
            if profile["activity"].get("training"):
                nodes += profile["activity"]["training"]["nodes"]
            points += [(node["approach"]["x"], node["approach"]["y"]) for node in nodes]
            self.assertTrue(blocked.isdisjoint(points), profile["id"])

    def test_ch0p_falador_bank_route_uses_live_collision_gate(self):
        profile = load_roster()[1]
        inbound = [(327, 576), (326, 565), (327, 553), (328, 553)]
        outbound = list(reversed(inbound))
        for route in (
            profile["travelRoute"],
            profile["stagingBank"]["route"],
            profile["activity"]["bank"]["route"],
        ):
            points = [(point["x"], point["y"]) for point in route]
            self.assertTrue(
                any(points[index : index + len(inbound)] == inbound for index in range(len(points))),
                points,
            )
        travel = [(point["x"], point["y"]) for point in profile["travelRoute"]]
        self.assertTrue(
            any(travel[index : index + len(outbound)] == outbound for index in range(len(travel)))
        )
        return_corridor = [(327, 576), (316, 576), (305, 576), (294, 576)]
        self.assertTrue(
            any(
                travel[index : index + len(return_corridor)] == return_corridor
                for index in range(len(travel))
            )
        )
        bank_route = [
            (point["x"], point["y"])
            for point in reversed(profile["activity"]["bank"]["route"])
        ]
        self.assertTrue(
            any(
                bank_route[index : index + len(return_corridor)] == return_corridor
                for index in range(len(bank_route))
            )
        )

    def test_ultraz_two_boat_contract(self):
        profile = load_roster()[2]
        self.assertNotIn("travelSupply", profile)
        self.assertNotIn("stagingBank", profile)
        self.assertNotIn("hazard", profile)
        journey = profile["journey"]
        self.assertEqual((83, 70, 60), (
            journey["funding"]["npcId"],
            journey["funding"]["saleItemId"],
            journey["funding"]["minimumCoins"],
        ))
        self.assertEqual((166, 30), (journey["port"]["npcId"], journey["port"]["fare"]))
        self.assertEqual((317, 30), (
            journey["karamja"]["npcId"], journey["karamja"]["fare"]
        ))
        karamja = journey["karamja"]
        survival = karamja["survival"]
        self.assertEqual((324, 713), (
            karamja["route"][0]["x"], karamja["route"][0]["y"]
        ))
        self.assertEqual((467, 650), (
            karamja["route"][-1]["x"], karamja["route"][-1]["y"]
        ))
        self.assertEqual([249, 132], survival["foodItemIds"])
        self.assertEqual([70, 21, 262, 271, 421], survival["hostileNpcIds"])
        self.assertEqual((4, 10, 7, 0.9), (
            survival["shieldItemId"],
            survival["bananaTarget"],
            survival["npcAvoidRadius"],
            survival["emergencyHitsRatio"],
        ))
        self.assertTrue(all(tree["command"] == 2 for tree in survival["bananaTrees"]))
        invalid_coarse_points = {
            (340, 720), (380, 720), (420, 720), (453, 720), (453, 700), (468, 691)
        }
        self.assertTrue(invalid_coarse_points.isdisjoint(
            (point["x"], point["y"]) for point in karamja["route"]
        ))
        self.assertTrue(all(
            left["x"] == right["x"] or left["y"] == right["y"]
            for left, right in zip(karamja["route"], karamja["route"][1:])
        ))
        self.assertEqual({"x": 538, "y": 617}, journey["ardougne"]["route"][0])
        self.assertEqual({"x": 419, "y": 499}, journey["ardougne"]["route"][-1])

    def test_survival_route_markers_must_name_audited_safe_stages(self):
        payload = json.loads(Path("tools/headless_players/roster.json").read_text())
        survival = payload["players"][2]["journey"]["karamja"]["survival"]
        survival["safeStagingPoints"].remove(survival["hazardSafePost"])
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "roster.json")
            path.write_text(json.dumps(payload))
            with self.assertRaisesRegex(RosterError, "hazardSafePost is not a safe stage"):
                load_roster(path)

    def test_port_base_must_leave_ten_valid_ports(self):
        self.assertEqual(2, main(["--once", "--control-port-base", "65530", "--log-level", "ERROR"]))


class OnboardingTests(unittest.TestCase):
    def setUp(self):
        self.profile = load_roster()[0]
        self.clock = FakeClock()
        self.client = FakeClient()
        self.brain = PlayerBrain(self.profile, self.client, clock=self.clock, rng=random.Random(1))

    def test_designs_only_while_live_appearance_screen_is_open(self):
        state = state_at(24, 37)
        state["appearance"]["open"] = True
        self.assertEqual("submitted-appearance", self.brain.tick_state(state))
        cmd, args = self.client.commands[-1]
        self.assertEqual("design-character", cmd)
        self.assertEqual("female", args["gender"])
        self.assertEqual(0, args["head"])
        self.assertEqual(4, args["body"])
        self.assertEqual(14, args["hair_colour"])

    def test_does_not_blindly_resubmit_appearance_on_void_island(self):
        self.assertEqual("waiting-onboarding", self.brain.tick_state(state_at(24, 37)))
        self.assertEqual([], self.client.commands)

    def test_selects_skip_then_forager_by_exact_menu_contract(self):
        welcome = state_at(24, 37)
        welcome["dialog"] = {
            "open": True,
            "options": ["I've played Classic — what's new in Voidscape?", "Skip the intro"],
        }
        self.assertEqual("selected-welcome-skip", self.brain.tick_state(welcome))
        self.assertEqual(("menu-reply", {"option": 1}), self.client.commands[-1])

        path = state_at(24, 26)
        path["dialog"] = {
            "open": True,
            "options": [
                "Warrior's Path - 2x XP: Attack, Defense, Strength",
                "Forager's Path - 2x XP: Fishing, Cooking, Mining",
                "Arcanist's Path - 2x XP: Ranged, Magic",
            ],
        }
        self.assertEqual("selected-forager", self.brain.tick_state(path))
        self.assertEqual(("menu-reply", {"option": 1}), self.client.commands[-1])

    def test_never_answers_unrecognized_dialog(self):
        state = state_at(120, 648)
        state["dialog"] = {"open": True, "options": ["Yes", "No"]}
        self.assertEqual("waiting-unrecognized-dialog", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_does_not_repeat_same_dialog_reply_while_packet_settles(self):
        welcome = state_at(24, 37)
        welcome["dialog"] = {
            "open": True,
            "options": ["I've played Classic — what's new in Voidscape?", "Skip the intro"],
        }
        self.brain.tick_state(welcome)
        self.clock.advance(1)
        self.assertEqual("waiting-dialog-close", self.brain.tick_state(welcome))
        self.assertEqual(1, len(self.client.commands))


class ActivityTests(unittest.TestCase):
    def setUp(self):
        self.profiles = {profile["id"]: profile for profile in load_roster()}

    def test_six_seven_trains_copper_and_tin_until_iron_level(self):
        brain = PlayerBrain(self.profiles["six-seven"], FakeClient(), rng=random.Random(1))
        low = state_at(74, 543, inventory=[item(156)], mining=14)
        nodes, outputs = brain._active_nodes_and_outputs(low)
        self.assertEqual({100, 105}, {node["objectId"] for node in nodes})
        self.assertEqual([150, 202], outputs)

        ready = state_at(74, 543, inventory=[item(156)], mining=15)
        nodes, outputs = brain._active_nodes_and_outputs(ready)
        self.assertEqual({102, 103}, {node["objectId"] for node in nodes})
        self.assertIn(151, outputs)

    def test_crowded_node_is_skipped(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(self.profiles["fireee"], client, clock=clock, rng=random.Random(2))
        state = state_at(212, 636, inventory=[item(87)])
        state["players"] = [{"x": 211, "y": 634, "name": "Someone"}]
        brain.tick_state(state)
        self.assertEqual("object-action", client.commands[-1][0])
        self.assertNotEqual((212, 634), (client.commands[-1][1]["x"], client.commands[-1][1]["y"]))

    def test_success_interrupts_before_idle_then_moves_to_uncrowded_tree(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(self.profiles["az"], client, clock=clock, rng=random.Random(3))
        initial = state_at(139, 639, inventory=[item(87)])
        initial["world_walk_route"] = {
            "seq": 7,
            "ok": True,
            "reason": 0,
            "count": 1,
            "route": [{"x": 999, "y": 999}],
        }
        self.assertEqual("gathering", brain.tick_state(initial))
        first_key = brain.runtime.pending_gather.node_key
        first_action = client.commands[-1]

        clock.advance(1)
        success = copy.deepcopy(initial)
        success["inventory"].append(item(14, slot=1))
        # The nearest alternate is occupied, so the success rotation must both
        # move immediately and retain the ordinary crowding avoidance rule.
        success["players"] = [{"x": 140, "y": 641, "name": "Someone"}]
        self.assertEqual("gather-success", brain.tick_state(success))
        self.assertGreater(brain.runtime.node_cooldowns[first_key], clock())
        self.assertEqual(first_key, brain.runtime.last_successful_node_key)
        self.assertLessEqual(brain.runtime.idle_until, clock())
        self.assertEqual("walk-step", client.commands[-1][0])
        rotation = brain.runtime.pending_gather_rotation
        self.assertIsNotNone(rotation)
        self.assertNotEqual((140, 640), rotation.approach)
        self.assertNotEqual(
            (first_action[1]["walk_x"], first_action[1]["walk_y"]),
            rotation.approach,
        )

        # WalkRequest consumes the first WALK_TO_POINT only to interrupt the busy
        # plugin. A later tick requests the real route, still without opening an
        # idle-only window.
        clock.advance(1.1)
        commands_before_retry = len(client.commands)
        self.assertEqual(
            "requesting-gather-rotation-route", brain.tick_state(success)
        )
        self.assertEqual(commands_before_retry + 1, len(client.commands))
        self.assertEqual("goto", client.commands[-1][0])
        self.assertLessEqual(brain.runtime.idle_until, clock())
        self.assertEqual(7, brain.runtime.pending_gather_rotation.route_seq_baseline)

        # The cached response is not fresh acknowledgement for this request.
        commands_after_route = len(client.commands)
        clock.advance(1)
        self.assertEqual(
            "waiting-gather-rotation-route", brain.tick_state(success)
        )
        self.assertEqual(commands_after_route, len(client.commands))
        self.assertLessEqual(brain.runtime.idle_until, clock())

        accepted = copy.deepcopy(success)
        # A daemon restart can reset its local response sequence below the
        # captured baseline; a positive different value is still fresh.
        accepted["world_walk_route"] = {
            "seq": 1,
            "ok": True,
            "reason": 0,
            "count": 1,
            "route": [{
                "x": rotation.approach[0],
                "y": rotation.approach[1],
            }],
        }
        clock.advance(0.1)
        self.assertEqual(
            "waiting-gather-rotation-movement", brain.tick_state(accepted)
        )
        self.assertEqual(commands_after_route, len(client.commands))
        self.assertLessEqual(brain.runtime.idle_until, clock())

        # Even a fresh successful route is not movement proof and cannot release
        # the state machine to another object action.
        clock.advance(1)
        self.assertEqual(
            "waiting-gather-rotation-movement", brain.tick_state(accepted)
        )
        self.assertEqual(commands_after_route, len(client.commands))

        # A changed coordinate is the final proof that busy cleared and walking
        # landed. Human-like idle begins only after this point.
        moved = copy.deepcopy(accepted)
        moved["position"] = {
            "x": rotation.approach[0],
            "y": rotation.approach[1],
        }
        moved["players"] = []
        clock.advance(0.5)
        self.assertEqual("gather-rotation-moving", brain.tick_state(moved))
        self.assertIsNone(brain.runtime.pending_gather_rotation)
        self.assertGreater(brain.runtime.idle_until, clock())

        clock.now = max(
            brain.runtime.idle_until,
            brain.runtime.node_cooldowns[first_key],
        ) + 0.1
        self.assertEqual("gathering", brain.tick_state(moved))
        self.assertEqual("object-action", client.commands[-1][0])
        self.assertNotEqual(first_key, brain.runtime.pending_gather.node_key)

    def test_success_still_moves_when_every_alternate_is_crowded_and_cooling(self):
        clock = FakeClock()
        client = FakeClient()
        profile = self.profiles["az"]
        brain = PlayerBrain(profile, client, clock=clock, rng=random.Random(5))
        initial = state_at(139, 639, inventory=[item(87)])
        self.assertEqual("gathering", brain.tick_state(initial))
        completed_key = brain.runtime.pending_gather.node_key

        alternates = [
            node
            for node in profile["activity"]["nodes"]
            if brain._node_key(node) != completed_key
        ]
        for node in alternates:
            brain.runtime.node_cooldowns[brain._node_key(node)] = clock() + 100

        clock.advance(1)
        success = copy.deepcopy(initial)
        success["inventory"].append(item(14, slot=1))
        success["players"] = [
            {
                "x": node["location"]["x"],
                "y": node["location"]["y"],
                "name": f"Crowd {index}",
            }
            for index, node in enumerate(alternates)
        ]
        commands_before_success = len(client.commands)

        self.assertEqual("gather-success", brain.tick_state(success))
        self.assertEqual(commands_before_success + 1, len(client.commands))
        self.assertEqual("walk-step", client.commands[-1][0])
        self.assertLessEqual(brain.runtime.idle_until, clock())
        self.assertIsNotNone(brain.runtime.pending_gather_rotation)
        self.assertIn(
            brain.runtime.pending_gather_rotation.approach,
            {
                (node["approach"]["x"], node["approach"]["y"])
                for node in alternates
            },
        )

    def test_xp_only_success_starts_the_same_cancel_handshake(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(
            self.profiles["az"], client, clock=clock, rng=random.Random(6)
        )
        initial = state_at(139, 639, inventory=[item(87)])
        self.assertEqual("gathering", brain.tick_state(initial))

        clock.advance(1)
        success = copy.deepcopy(initial)
        success["skills"]["woodcutting"]["xp"] = 25
        self.assertEqual("gather-success", brain.tick_state(success))
        self.assertEqual("walk-step", client.commands[-1][0])
        self.assertIsNotNone(brain.runtime.pending_gather_rotation)
        self.assertLessEqual(brain.runtime.idle_until, clock())

    def test_busy_route_response_reinterrupts_before_retrying_goto(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(
            self.profiles["az"], client, clock=clock, rng=random.Random(7)
        )
        initial = state_at(139, 639, inventory=[item(87)])
        brain.tick_state(initial)
        clock.advance(1)
        success = copy.deepcopy(initial)
        success["inventory"].append(item(14, slot=1))
        brain.tick_state(success)

        clock.advance(1.1)
        self.assertEqual(
            "requesting-gather-rotation-route", brain.tick_state(success)
        )
        rotation = brain.runtime.pending_gather_rotation
        self.assertEqual(1, rotation.route_attempts)

        rejected = copy.deepcopy(success)
        rejected["world_walk_route"] = {
            "seq": 1,
            "ok": False,
            "reason": 6,
            "count": 0,
            "route": [],
        }
        self.assertEqual("retrying-gather-interrupt", brain.tick_state(rejected))
        self.assertEqual("walk-step", client.commands[-1][0])
        self.assertEqual("cancel-sent", rotation.phase)
        self.assertLessEqual(brain.runtime.idle_until, clock())

        clock.advance(1.1)
        self.assertEqual(
            "requesting-gather-rotation-route", brain.tick_state(rejected)
        )
        self.assertEqual("goto", client.commands[-1][0])
        self.assertEqual(2, rotation.route_attempts)
        self.assertEqual(1, rotation.route_seq_baseline)

    def test_missing_route_ack_retries_are_bounded_without_object_action(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(
            self.profiles["ch0p"], client, clock=clock, rng=random.Random(8)
        )
        # Ch0p has an alternate rock directly adjacent to this unchanged origin;
        # clearing the rotation gate would permit an immediate object-action.
        initial = state_at(309, 634, inventory=[item(156)])
        brain.tick_state(initial)
        clock.advance(1)
        success = copy.deepcopy(initial)
        success["inventory"].append(item(150, slot=1))
        brain.tick_state(success)

        for attempt in range(1, 5):
            clock.advance(1.1)
            self.assertEqual(
                "requesting-gather-rotation-route", brain.tick_state(success)
            )
            self.assertEqual(
                attempt, brain.runtime.pending_gather_rotation.route_attempts
            )
            clock.advance(2.6)
            if attempt < 4:
                self.assertEqual(
                    "retrying-gather-interrupt", brain.tick_state(success)
                )
                self.assertEqual("walk-step", client.commands[-1][0])
                self.assertLessEqual(brain.runtime.idle_until, clock())
            else:
                self.assertEqual(
                    "gather-rotation-backoff", brain.tick_state(success)
                )

        rotation = brain.runtime.pending_gather_rotation
        self.assertIsNotNone(rotation)
        self.assertEqual("backoff", rotation.phase)
        self.assertLessEqual(brain.runtime.idle_until, clock())
        self.assertEqual(
            1, sum(command == "object-action" for command, _ in client.commands)
        )

        commands_at_backoff = len(client.commands)
        clock.advance(2)
        self.assertEqual("gather-rotation-backoff", brain.tick_state(success))
        self.assertEqual(commands_at_backoff, len(client.commands))
        self.assertEqual(
            1, sum(command == "object-action" for command, _ in client.commands)
        )

        # Finite backoff reselects another physically distinct target and starts a
        # fresh interrupt/route handshake without ever opening the gather gate.
        previous_target = rotation.node_key
        clock.advance(3.1)
        self.assertEqual("retrying-gather-interrupt", brain.tick_state(success))
        self.assertEqual("walk-step", client.commands[-1][0])
        self.assertNotEqual(previous_target, rotation.node_key)
        self.assertEqual("cancel-sent", rotation.phase)
        self.assertLessEqual(brain.runtime.idle_until, clock())

        clock.advance(1.1)
        self.assertEqual(
            "requesting-gather-rotation-route", brain.tick_state(success)
        )
        target = rotation.approach
        accepted = copy.deepcopy(success)
        accepted["world_walk_route"] = {
            "seq": 1,
            "ok": True,
            "reason": 0,
            "count": 1,
            "route": [{"x": target[0], "y": target[1]}],
        }
        self.assertEqual(
            "waiting-gather-rotation-movement", brain.tick_state(accepted)
        )
        self.assertEqual(
            1, sum(command == "object-action" for command, _ in client.commands)
        )

        moved = copy.deepcopy(accepted)
        moved["position"] = {"x": target[0], "y": target[1]}
        clock.advance(0.5)
        self.assertEqual("gather-rotation-moving", brain.tick_state(moved))
        self.assertIsNone(brain.runtime.pending_gather_rotation)
        self.assertGreater(brain.runtime.idle_until, clock())

    def test_cancel_control_error_preserves_rotation_intent(self):
        clock = FakeClock()
        client = FailOnceClient()
        brain = PlayerBrain(
            self.profiles["az"], client, clock=clock, rng=random.Random(9)
        )
        initial = state_at(139, 639, inventory=[item(87)])
        brain.tick_state(initial)
        client.fail_next = "walk-step"

        clock.advance(1)
        success = copy.deepcopy(initial)
        success["inventory"].append(item(14, slot=1))
        with self.assertRaisesRegex(ControlError, "injected walk-step"):
            brain.tick_state(success)
        self.assertIsNone(brain.runtime.pending_gather)
        self.assertIsNotNone(brain.runtime.pending_gather_rotation)
        self.assertEqual("cancel", brain.runtime.pending_gather_rotation.phase)
        self.assertLessEqual(brain.runtime.idle_until, clock())

        clock.advance(1)
        self.assertEqual("canceling-gather-batch", brain.tick_state(success))
        self.assertEqual("walk-step", client.commands[-1][0])
        self.assertEqual("cancel-sent", brain.runtime.pending_gather_rotation.phase)

    def test_no_output_timeout_does_not_advance_rotation(self):
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(self.profiles["fireee"], client, clock=clock, rng=random.Random(4))
        state = state_at(211, 634, inventory=[item(87)])
        brain.tick_state(state)
        key = brain.runtime.pending_gather.node_key
        previous_success = brain._node_key(self.profiles["fireee"]["activity"]["nodes"][1])
        self.assertNotEqual(key, previous_success)
        brain.runtime.last_successful_node_key = previous_success
        commands_before_timeout = len(client.commands)
        clock.advance(20)
        self.assertEqual("gather-stalled", brain.tick_state(state))
        self.assertEqual(commands_before_timeout, len(client.commands))
        self.assertIsNone(brain.runtime.pending_gather)
        self.assertIsNone(brain.runtime.pending_gather_rotation)
        self.assertGreater(brain.runtime.node_cooldowns[key], clock())
        self.assertEqual(previous_success, brain.runtime.last_successful_node_key)

        clock.now = max(brain.runtime.node_cooldowns[key], brain.runtime.idle_until) + 0.1
        self.assertEqual("gathering", brain.tick_state(state))
        self.assertEqual(key, brain.runtime.pending_gather.node_key)


class BankTests(unittest.TestCase):
    def setUp(self):
        self.profile = load_roster()[0]
        self.clock = FakeClock()
        self.client = FakeClient()
        self.brain = PlayerBrain(self.profile, self.client, clock=self.clock, rng=random.Random(1))

    def test_deposit_withdraw_close_is_explicit_and_does_not_redeposit_tool(self):
        approach = self.profile["activity"]["bank"]["approach"]
        full = state_at(
            approach["x"], approach["y"], inventory=[item(87), item(14, 1)], bank_open=True, bank_items=[]
        )
        self.assertEqual("banking-inventory", self.brain.tick_state(full))

        self.clock.advance(2)
        empty = state_at(
            approach["x"], approach["y"], inventory=[], bank_open=True, bank_items=[{"id": 88, "amount": 1}]
        )
        self.assertEqual("withdrawing-tool", self.brain.tick_state(empty))

        self.clock.advance(2)
        tool_back = state_at(
            approach["x"], approach["y"], inventory=[item(88)], bank_open=True, bank_items=[]
        )
        self.assertEqual("bank-complete", self.brain.tick_state(tool_back))
        self.assertEqual(
            ["bank-deposit-all", "bank-withdraw", "bank-close"],
            [command for command, _ in self.client.commands],
        )

    def test_hazard_profile_keeps_food_after_tool(self):
        profile = load_roster()[1]
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(profile, client, clock=clock, rng=random.Random(1))
        approach = profile["activity"]["bank"]["approach"]
        state = state_at(
            approach["x"], approach["y"], inventory=[], bank_open=True,
            bank_items=[{"id": 156, "amount": 1}, {"id": 132, "amount": 4}],
        )
        self.assertEqual("withdrawing-tool", brain.tick_state(state))
        clock.advance(2)
        state["inventory"] = [item(156)]
        self.assertEqual("withdrawing-food", brain.tick_state(state))
        self.assertEqual({"id": 132, "amount": 2}, client.commands[-1][1])

    def test_bank_open_acknowledgement_retries_are_bounded(self):
        approach = self.profile["activity"]["bank"]["approach"]
        state = state_at(approach["x"], approach["y"], inventory=[])

        for _ in range(4):
            self.assertEqual("opening-bank", self.brain.tick_state(state))
            self.clock.advance(4)
        self.assertEqual(4, len(self.client.commands))

        self.assertEqual("bank-open-timeout", self.brain.tick_state(state))
        self.assertEqual(4, len(self.client.commands))
        self.assertGreater(self.brain.runtime.route_blocked_until, self.clock())

        self.clock.advance(1)
        self.assertEqual("route-cooldown", self.brain.tick_state(state))
        self.assertEqual(4, len(self.client.commands))

    def test_open_bank_acknowledgement_resets_attempt_counter(self):
        approach = self.profile["activity"]["bank"]["approach"]
        closed = state_at(approach["x"], approach["y"], inventory=[])
        self.assertEqual("opening-bank", self.brain.tick_state(closed))
        self.assertEqual(1, self.brain.runtime.bank_open_attempts)

        self.clock.advance(1)
        opened = state_at(
            approach["x"], approach["y"], inventory=[item(87), item(14)], bank_open=True
        )
        self.brain.tick_state(opened)
        self.assertEqual(0, self.brain.runtime.bank_open_attempts)

    def test_staging_with_only_one_starter_meat_withdraws_available_amount_then_closes(self):
        profile = load_roster()[1]
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(profile, client, clock=clock, rng=random.Random(1))
        approach = profile["stagingBank"]["approach"]

        state = state_at(
            approach["x"], approach["y"], inventory=[], bank_open=True,
            bank_items=[{"id": 156, "amount": 1}, {"id": 132, "amount": 1}],
        )
        self.assertEqual("withdrawing-tool", brain.tick_state(state))

        clock.advance(2)
        state["inventory"] = [item(156)]
        state["bank"]["items"] = [{"id": 132, "amount": 1}]
        self.assertEqual("withdrawing-food", brain.tick_state(state))
        self.assertEqual({"id": 132, "amount": 1}, client.commands[-1][1])

        clock.advance(2)
        state["inventory"] = [item(156), item(132, slot=1)]
        state["bank"]["items"] = []
        self.assertEqual("bank-complete", brain.tick_state(state))
        self.assertEqual(
            ["bank-withdraw", "bank-withdraw", "bank-close"],
            [command for command, _ in client.commands],
        )


class HazardTests(unittest.TestCase):
    def setUp(self):
        profile = load_roster()[1]
        self.clock = FakeClock()
        self.client = FakeClient()
        self.brain = PlayerBrain(profile, self.client, clock=self.clock, rng=random.Random(1))

    def test_first_three_combat_rounds_retry_walk_step_and_never_eat(self):
        hurt = state_at(365, 520, inventory=[item(376), item(132, 1)], hits=(7, 10))
        for _ in range(3):
            self.assertEqual("retreating-from-combat", self.brain.tick_state(hurt))
            self.clock.advance(1.1)
        self.assertEqual(["walk-step", "walk-step", "walk-step"], [cmd for cmd, _ in self.client.commands])
        for cmd, args in self.client.commands:
            self.assertLessEqual(abs(args["x"] - 365) + abs(args["y"] - 520), 1)

    def test_successful_retreat_movement_then_eats(self):
        hurt = state_at(365, 520, inventory=[item(376), item(132, 1)], hits=(7, 10))
        self.brain.tick_state(hurt)
        step = self.client.commands[-1][1]
        self.clock.advance(1.1)
        moved = state_at(step["x"], step["y"], inventory=[item(376), item(132, 1)], hits=(7, 10))
        self.assertEqual("eating-after-retreat", self.brain.tick_state(moved))
        self.assertEqual(["walk-step", "item-command"], [cmd for cmd, _ in self.client.commands])

    def test_retreat_uses_the_prior_route_waypoint_by_default(self):
        profile = copy.deepcopy(load_roster()[1])
        brain = PlayerBrain(profile, FakeClient(), clock=self.clock, rng=random.Random(1))
        self.assertEqual((200, 625), brain._retreat_point((240, 610)))

    def test_no_food_retreat_then_holds_recovery_cooldown(self):
        hurt = state_at(365, 520, inventory=[item(376)], hits=(7, 10))
        self.brain.tick_state(hurt)
        step = self.client.commands[-1][1]
        self.clock.advance(1.1)
        moved = state_at(step["x"], step["y"], inventory=[item(376)], hits=(7, 10))
        self.assertEqual("hazard-no-food-cooldown", self.brain.tick_state(moved))
        command_count = len(self.client.commands)
        self.assertNotIn("item-command", [cmd for cmd, _ in self.client.commands])
        self.assertGreater(self.brain.runtime.hazard_blocked_until, self.clock())

        self.clock.advance(1.0)
        self.assertEqual("hazard-cooldown", self.brain.tick_state(moved))
        self.assertEqual(command_count, len(self.client.commands))


class JourneyTests(unittest.TestCase):
    def setUp(self):
        self.profile = load_roster()[2]
        self.clock = FakeClock()
        self.client = FakeClient()
        self.brain = PlayerBrain(self.profile, self.client, clock=self.clock, rng=random.Random(1))

    @staticmethod
    def journey_inventory(coins, *, sword=True):
        inventory = [item(376, slot=0)]
        if coins:
            inventory.append(item(10, slot=1, amount=coins))
        if sword:
            inventory.append(item(70, slot=2))
        return inventory

    @staticmethod
    def karamja_inventory(coins=36, *, bananas=10, shield=True, wielded=True, meat=True):
        inventory = [item(376, slot=0), item(10, slot=1, amount=coins)]
        if shield:
            inventory.append(item(4, slot=2, wielded=wielded))
        if meat:
            inventory.append(item(132, slot=3))
        inventory.extend(item(249, slot=4 + index) for index in range(bananas))
        return inventory

    def test_fresh_staged_player_talks_to_lumbridge_shop_assistant(self):
        state = state_at(135, 640, inventory=self.journey_inventory(50))
        self.assertEqual("talking:lumbridge-funding", self.brain.tick_state(state))
        self.assertEqual(("npc-talk", {"id": 83}), self.client.commands[-1])

    def test_exact_shop_menu_opens_and_unrelated_menu_remains_untouched(self):
        shop_menu = state_at(135, 641, inventory=self.journey_inventory(50))
        shop_menu["dialog"] = {
            "open": True,
            "options": ["Yes please, what are you selling?", "No thanks"],
        }
        self.assertEqual("opening-funding-shop", self.brain.tick_state(shop_menu))
        self.assertEqual(("menu-reply", {"option": 0}), self.client.commands[-1])

        other_client = FakeClient()
        other = PlayerBrain(self.profile, other_client, clock=self.clock, rng=random.Random(1))
        unrelated = state_at(135, 641, inventory=self.journey_inventory(50))
        unrelated["dialog"] = {"open": True, "options": ["Ok", "No"]}
        self.assertEqual("waiting-unrecognized-dialog", other.tick_state(unrelated))
        self.assertEqual([], other_client.commands)

    def test_open_shop_sale_waits_for_inventory_and_coin_proof_then_closes(self):
        open_shop = state_at(
            135, 641, inventory=self.journey_inventory(50), shop_open=True
        )
        self.assertEqual("selling-funding-item", self.brain.tick_state(open_shop))
        self.assertEqual(
            ("shop-sell", {"id": 70, "stock": 0, "amount": 1}),
            self.client.commands[-1],
        )

        self.clock.advance(1)
        self.assertEqual("waiting-funding-sale", self.brain.tick_state(open_shop))
        self.assertEqual(1, len(self.client.commands))

        sold = state_at(
            135, 641, inventory=self.journey_inventory(66, sword=False), shop_open=True
        )
        self.clock.advance(1)
        self.assertEqual("funding-sale-confirmed", self.brain.tick_state(sold))
        self.clock.advance(2)
        self.assertEqual("closing-funding-shop", self.brain.tick_state(sold))
        self.assertEqual(("shop-close", {}), self.client.commands[-1])

    def test_unconfirmed_shop_sale_is_bounded_to_four_attempts(self):
        open_shop = state_at(
            135, 641, inventory=self.journey_inventory(50), shop_open=True
        )
        for attempt in range(4):
            self.assertEqual("selling-funding-item", self.brain.tick_state(open_shop))
            self.clock.advance(8.1)
            expected = "funding-sale-timeout" if attempt == 3 else "funding-sale-unconfirmed"
            self.assertEqual(expected, self.brain.tick_state(open_shop))
            if attempt < 3:
                self.clock.advance(1.6)
        self.assertEqual(
            4,
            sum(command == "shop-sell" for command, _ in self.client.commands),
        )
        self.assertGreater(self.brain.runtime.journey_blocked_until, self.clock())

    def test_controller_restart_with_open_shop_resumes_sale_from_live_state(self):
        restarted = PlayerBrain(self.profile, self.client, clock=self.clock, rng=random.Random(1))
        state = state_at(135, 641, inventory=self.journey_inventory(50), shop_open=True)
        self.assertEqual("selling-funding-item", restarted.tick_state(state))
        self.assertEqual("shop-sell", self.client.commands[-1][0])

    def test_sale_below_sixty_closes_then_fails_closed(self):
        sold = state_at(
            135, 641, inventory=self.journey_inventory(58, sword=False), shop_open=True
        )
        self.assertEqual("closing-funding-shop", self.brain.tick_state(sold))
        self.clock.advance(2)
        closed = state_at(135, 641, inventory=self.journey_inventory(58, sword=False))
        self.assertEqual("journey-funding-missing", self.brain.tick_state(closed))
        self.assertGreater(self.brain.runtime.journey_blocked_until, self.clock())

    def test_funded_player_leaves_shop_on_audited_port_route(self):
        state = state_at(135, 640, inventory=self.journey_inventory(66, sword=False))
        self.assertEqual("walking:journey:port-sarim", self.brain.tick_state(state))
        self.assertEqual(("goto", {"x": 135, "y": 635}), self.client.commands[-1])

    def test_port_dialogue_accepts_both_exact_server_contracts(self):
        for options, expected_reply in (
            (["I'd rather go to Crandor Isle", "Yes please", "No thankyou"], 1),
            (["Yes please", "No thankyou"], 0),
        ):
            client = FakeClient()
            brain = PlayerBrain(self.profile, client, clock=self.clock, rng=random.Random(1))
            state = state_at(269, 649, inventory=self.journey_inventory(66, sword=False))
            state["dialog"] = {"open": True, "options": options}
            self.assertEqual("paying-port-fare", brain.tick_state(state))
            self.assertEqual(("menu-reply", {"option": expected_reply}), client.commands[-1])

    def test_restart_after_first_fare_waits_for_coordinate_transition(self):
        recent = [{"text": "You pay 30 gold", "t": time.time()}]
        state = state_at(
            269,
            649,
            inventory=self.journey_inventory(36, sword=False),
            messages=recent,
        )
        self.assertEqual("waiting-port-transition", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

        inconsistent = PlayerBrain(self.profile, FakeClient(), clock=self.clock, rng=random.Random(1))
        no_evidence = state_at(269, 649, inventory=self.journey_inventory(36, sword=False))
        self.assertEqual("port-fare-state-invalid", inconsistent.tick_state(no_evidence))

    def test_mid_karamja_restart_resumes_collision_valid_waypoint(self):
        state = state_at(459, 691, inventory=self.karamja_inventory())
        self.assertEqual("walking:journey:karamja-customs", self.brain.tick_state(state))
        self.assertEqual(("goto", {"x": 461, "y": 690}), self.client.commands[-1])

    def test_karamja_landing_equips_shield_before_supply_route(self):
        landing = state_at(
            324,
            713,
            inventory=self.karamja_inventory(bananas=0, wielded=False),
        )
        self.assertEqual("equipping-karamja-shield", self.brain.tick_state(landing))
        self.assertEqual(("equip", {"slot": 2}), self.client.commands[-1])

        self.clock.advance(2.1)
        equipped = state_at(
            324,
            713,
            inventory=self.karamja_inventory(bananas=0, wielded=True),
        )
        self.assertEqual("walking:journey:karamja-bananas:0", self.brain.tick_state(equipped))
        self.assertEqual(("goto", {"x": 332, "y": 713}), self.client.commands[-1])

    def test_karamja_missing_guaranteed_starter_shield_fails_closed_at_safe_stage(self):
        state = state_at(
            324, 713, inventory=self.karamja_inventory(bananas=0, shield=False)
        )
        self.assertEqual("karamja-shield-missing", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)
        self.assertGreater(self.brain.runtime.journey_blocked_until, self.clock())

    def test_banana_pick_uses_second_object_action_and_waits_for_batch_proof(self):
        empty = state_at(364, 703, inventory=self.karamja_inventory(bananas=0))
        self.assertEqual("picking-karamja-bananas", self.brain.tick_state(empty))
        command, args = self.client.commands[-1]
        self.assertEqual("object-action", command)
        self.assertEqual(
            {"x": 363, "y": 703, "id": 183, "which": 2, "walk_x": 364, "walk_y": 703},
            args,
        )

        self.clock.advance(1)
        partial = state_at(364, 703, inventory=self.karamja_inventory(bananas=1))
        self.assertEqual("waiting-karamja-banana-batch", self.brain.tick_state(partial))
        self.assertEqual(1, len(self.client.commands))

        self.clock.advance(1)
        batch = state_at(364, 703, inventory=self.karamja_inventory(bananas=5))
        self.assertEqual("karamja-banana-confirmed", self.brain.tick_state(batch))
        self.assertEqual(1, self.brain.runtime.journey_banana_tree_index)

        # The next audited approach is two tiles away. It must be reached
        # exactly instead of being swallowed by the generic route tolerance.
        self.assertEqual(
            "walking:journey:karamja-bananas:1", self.brain.tick_state(batch)
        )
        self.assertEqual(("goto", {"x": 364, "y": 701}), self.client.commands[-1])

    def test_empty_banana_tree_rotates_only_after_settle_timeout(self):
        empty = state_at(364, 703, inventory=self.karamja_inventory(bananas=0))
        self.brain.tick_state(empty)
        self.clock.advance(4.6)
        self.assertEqual("rotating-karamja-banana-tree", self.brain.tick_state(empty))
        self.assertEqual(1, self.brain.runtime.journey_banana_tree_index)

    def test_pre_hazard_restart_without_full_supply_returns_to_tree(self):
        state = state_at(375, 694, inventory=self.karamja_inventory(bananas=4))
        self.assertEqual("walking:journey:karamja-bananas:0", self.brain.tick_state(state))
        self.assertEqual(("goto", {"x": 367, "y": 694}), self.client.commands[-1])

    def test_post_commit_restart_does_not_backtrack_for_consumed_food(self):
        state = state_at(386, 690, inventory=self.karamja_inventory(bananas=4))
        self.assertEqual("crossing-karamja-hazard", self.brain.tick_state(state))
        self.assertEqual(("walk-step", {"x": 386, "y": 689}), self.client.commands[-1])

    def test_safe_pre_stage_waits_when_next_segment_is_within_euclidean_gate(self):
        state = state_at(
            385,
            690,
            inventory=self.karamja_inventory(),
            npcs=[{"id": 70, "server_index": 1, "x": 392, "y": 690}],
        )
        self.assertEqual("waiting-safe-hazard-clear", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_safe_pre_stage_uses_recent_npc_view_across_one_raw_frame_drop(self):
        state = state_at(385, 690, inventory=self.karamja_inventory(), npcs=[])
        state["recent_npcs"] = [
            {"id": 70, "server_index": 1, "x": 392, "y": 690},
        ]
        self.assertEqual("waiting-safe-hazard-clear", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_euclidean_gate_includes_distance_seven_but_excludes_distance_eight(self):
        for npc_x, expected_status, expected_command in (
            (393, "waiting-safe-hazard-clear", None),
            (394, "crossing-karamja-hazard", ("walk-step", {"x": 386, "y": 690})),
        ):
            with self.subTest(npc_x=npc_x):
                client = FakeClient()
                brain = PlayerBrain(
                    self.profile, client, clock=self.clock, rng=random.Random(1)
                )
                state = state_at(
                    385,
                    690,
                    inventory=self.karamja_inventory(),
                    npcs=[{"id": 70, "server_index": 1, "x": npc_x, "y": 690}],
                )
                self.assertEqual(expected_status, brain.tick_state(state))
                self.assertEqual(
                    [] if expected_command is None else [expected_command],
                    client.commands,
                )

    def test_clear_hazard_corridor_advances_one_cardinal_step(self):
        state = state_at(405, 683, inventory=self.karamja_inventory())
        self.assertEqual("crossing-karamja-hazard", self.brain.tick_state(state))
        self.assertEqual(("walk-step", {"x": 406, "y": 683}), self.client.commands[-1])

    def test_blocked_hazard_step_keeps_spaced_bounded_retries_until_movement(self):
        profile = copy.deepcopy(self.profile)
        profile["journey"]["karamja"]["survival"]["stepMaxAttempts"] = 2
        client = FakeClient()
        brain = PlayerBrain(profile, client, clock=self.clock, rng=random.Random(1))
        stuck = state_at(405, 683, inventory=self.karamja_inventory())

        self.assertEqual("crossing-karamja-hazard", brain.tick_state(stuck))
        self.assertEqual(1, len(client.commands))
        self.clock.advance(0.5)
        self.assertEqual("crossing-karamja-hazard", brain.tick_state(stuck))
        self.assertEqual(1, len(client.commands))
        self.clock.advance(0.6)
        self.assertEqual("crossing-karamja-hazard", brain.tick_state(stuck))
        self.assertEqual(2, len(client.commands))

        for expected_commands in range(3, 7):
            self.clock.advance(1.1)
            self.assertEqual("crossing-karamja-hazard", brain.tick_state(stuck))
            self.assertEqual(expected_commands, len(client.commands))
            self.assertLessEqual(brain.runtime.journey_survival_step_attempts, 2)
            self.assertEqual(0.0, brain.runtime.journey_blocked_until)

        # A changed coordinate is the server's proof that one retreat request landed;
        # reset the bounded retry cycle and continue toward the audited safe stage.
        self.clock.advance(0.1)
        moved = state_at(406, 683, inventory=self.karamja_inventory())
        self.assertEqual("crossing-karamja-hazard", brain.tick_state(moved))
        self.assertEqual(("walk-step", {"x": 407, "y": 683}), client.commands[-1])
        self.assertEqual(1, brain.runtime.journey_survival_step_attempts)

        # Death/respawn recovery remains higher priority than any survival retry.
        brain.runtime.travel_started = True
        self.clock.advance(0.1)
        respawned = state_at(120, 648, inventory=self.karamja_inventory())
        self.assertEqual("travel-retry", brain.tick_state(respawned))
        self.assertEqual(7, len(client.commands))

    def test_hazard_before_gate_retreats_by_audited_route_not_direct_line(self):
        state = state_at(
            389,
            683,
            inventory=self.karamja_inventory(),
            npcs=[{"id": 70, "server_index": 1, "x": 395, "y": 683}],
        )
        self.assertEqual("moving-to-karamja-safe-stage", self.brain.tick_state(state))
        self.assertEqual(("walk-step", {"x": 389, "y": 684}), self.client.commands[-1])

    def test_hazard_at_gate_advances_by_audited_route_to_safe_post(self):
        state = state_at(
            435,
            683,
            inventory=self.karamja_inventory(),
            npcs=[{"id": 21, "server_index": 1, "x": 441, "y": 683}],
        )
        self.assertEqual("moving-to-karamja-safe-stage", self.brain.tick_state(state))
        self.assertEqual(("walk-step", {"x": 435, "y": 684}), self.client.commands[-1])

    def test_first_damage_outside_safe_stage_starts_route_retreat(self):
        state = state_at(405, 683, inventory=self.karamja_inventory(), hits=(9, 10))
        self.assertEqual("moving-to-karamja-safe-stage", self.brain.tick_state(state))
        self.assertEqual(("walk-step", {"x": 404, "y": 683}), self.client.commands[-1])

    def test_safe_stage_eats_banana_first_and_food_loss_proves_even_if_poison_hits(self):
        hurt = state_at(
            385, 690, inventory=self.karamja_inventory(bananas=1), hits=(9, 10)
        )
        self.assertEqual("eating-at-safe-stage", self.brain.tick_state(hurt))
        self.assertEqual(
            ("item-command", {"slot": 4, "amount": 1, "command": 0}),
            self.client.commands[-1],
        )

        self.clock.advance(1)
        healed = state_at(
            385, 690, inventory=self.karamja_inventory(bananas=0), hits=(8, 10)
        )
        self.assertEqual("safe-food-confirmed", self.brain.tick_state(healed))
        self.assertEqual(1, len(self.client.commands))

    def test_banana_supply_capacity_failure_is_bounded_at_safe_stage(self):
        inventory = self.karamja_inventory(bananas=0)
        inventory.extend(
            item(1000 + index, slot=len(inventory) + index) for index in range(17)
        )
        state = state_at(324, 713, inventory=inventory)
        self.assertEqual("karamja-banana-capacity-invalid", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)
        self.assertGreater(self.brain.runtime.journey_blocked_until, self.clock())

    def test_poison_scorpion_segment_gate_never_waits_off_stage(self):
        state = state_at(
            477,
            671,
            inventory=self.karamja_inventory(bananas=4),
            npcs=[{"id": 271, "server_index": 1, "x": 483, "y": 671}],
        )
        self.assertEqual("moving-to-karamja-safe-stage", self.brain.tick_state(state))
        command, args = self.client.commands[-1]
        self.assertEqual("walk-step", command)
        self.assertEqual(1, abs(args["x"] - 477) + abs(args["y"] - 671))

    def test_safe_post_gates_the_actual_goto_target_after_near_waypoints_are_skipped(self):
        state = state_at(
            452,
            690,
            inventory=self.karamja_inventory(),
            npcs=[{"id": 421, "server_index": 1, "x": 459, "y": 697}],
        )
        self.assertEqual("waiting-safe-hazard-clear", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_karamja_restart_with_unpaid_mainland_balance_fails_closed(self):
        state = state_at(453, 700, inventory=self.journey_inventory(66, sword=False))
        self.assertEqual("karamja-fare-state-invalid", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)
        self.assertGreater(self.brain.runtime.journey_blocked_until, self.clock())

    def test_karamja_menu_rejects_unpaid_mainland_balance(self):
        state = state_at(467, 650, inventory=self.karamja_inventory(coins=66))
        state["dialog"] = {
            "open": True,
            "options": ["Can I board this ship?", "Does Karamja have any unusual customs then?"],
        }
        self.assertEqual("waiting-unrecognized-dialog", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_customs_full_exact_menu_sequence_and_geography_gate(self):
        contracts = (
            (["Can I board this ship?", "Does Karamja have any unusual customs then?"], 0),
            (["Why?", "Search away I have nothing to hide", "You're not putting your hands on my things"], 1),
            (["Ok", "Oh, I'll not bother then"], 0),
        )
        for options, expected_reply in contracts:
            state = state_at(467, 650, inventory=self.karamja_inventory())
            state["dialog"] = {"open": True, "options": options}
            self.assertNotEqual("waiting-unrecognized-dialog", self.brain.tick_state(state))
            self.assertEqual(("menu-reply", {"option": expected_reply}), self.client.commands[-1])
            self.clock.advance(3.1)

        client = FakeClient()
        mainland = PlayerBrain(self.profile, client, clock=self.clock, rng=random.Random(1))
        generic_ok = state_at(135, 641, inventory=self.journey_inventory(66, sword=False))
        generic_ok["dialog"] = {"open": True, "options": ["Ok", "Oh, I'll not bother then"]}
        self.assertEqual("waiting-unrecognized-dialog", mainland.tick_state(generic_ok))
        self.assertEqual([], client.commands)

    def test_restart_after_customs_fare_waits_for_ardougne_transition(self):
        recent = [{"text": "You board the ship", "t": time.time()}]
        state = state_at(
            467,
            650,
            inventory=self.journey_inventory(6, sword=False),
            messages=recent,
        )
        self.assertEqual("waiting-customs-transition", self.brain.tick_state(state))
        self.assertEqual([], self.client.commands)

    def test_mid_ardougne_restart_resumes_hazard_filtered_land_route(self):
        state = state_at(500, 490, inventory=self.journey_inventory(6, sword=False))
        self.assertEqual("walking:journey:ardougne-to-catherby", self.brain.tick_state(state))
        self.assertEqual(("goto", {"x": 495, "y": 490}), self.client.commands[-1])

    def test_ardougne_restart_with_unpaid_customs_balance_fails_closed(self):
        for coins, position in ((30, (500, 490)), (66, (500, 490)), (66, (419, 499))):
            with self.subTest(coins=coins, position=position):
                client = FakeClient()
                brain = PlayerBrain(
                    self.profile, client, clock=self.clock, rng=random.Random(1)
                )
                state = state_at(
                    *position, inventory=self.journey_inventory(coins, sword=False)
                )
                self.assertEqual("ardougne-fare-state-invalid", brain.tick_state(state))
                self.assertEqual([], client.commands)
                self.assertGreater(brain.runtime.journey_blocked_until, self.clock())

    def test_every_audited_ardougne_waypoint_stays_in_land_route_phase(self):
        journey = self.profile["journey"]
        phases = {
            self.brain._journey_phase((point["x"], point["y"]), journey)
            for point in journey["ardougne"]["route"]
        }
        self.assertEqual({"ardougne"}, phases)

    def test_catherby_arrival_returns_to_normal_task_and_bank_semantics(self):
        inventory = [item(376)] + [item(349, slot=slot) for slot in range(1, 15)]
        state = state_at(419, 499, inventory=inventory)
        self.assertEqual("walking:to-bank", self.brain.tick_state(state))
        self.assertEqual("to-bank", self.brain.runtime.route_key)


class Ch0pStagingBankTests(unittest.TestCase):
    def setUp(self):
        self.profile = load_roster()[1]
        self.clock = FakeClock()
        self.client = FakeClient()
        self.brain = PlayerBrain(self.profile, self.client, clock=self.clock, rng=random.Random(1))

    def test_full_starter_inventory_stages_at_falador_before_rimmington(self):
        inventory = [item(156)] + [item(900 + slot, slot=slot) for slot in range(1, 15)]
        state = state_at(120, 648, inventory=inventory)
        self.assertEqual("walking:to-staging-bank", self.brain.tick_state(state))
        self.assertEqual("to-staging-bank", self.brain.runtime.route_key)
        self.assertEqual(("goto", {"x": 160, "y": 640}), self.client.commands[-1])

    def test_lean_inventory_leaves_falador_on_normal_rimmington_bank_route(self):
        state = state_at(328, 553, inventory=[item(156), item(132, slot=1)])
        self.assertEqual("walking:to-task", self.brain.tick_state(state))
        self.assertEqual("to-task", self.brain.runtime.route_key)
        # The first reverse-route point is already within the controller's
        # two-tile arrival tolerance, so it advances to the audited west-side
        # collision gate instead of attempting the blocked x=327 bank wall.
        self.assertEqual(("goto", {"x": 326, "y": 565}), self.client.commands[-1])

    def test_full_ore_load_does_not_switch_back_to_staging_mid_route(self):
        inventory = [item(156)] + [item(150, slot=slot) for slot in range(1, 15)]
        state = state_at(303, 590, inventory=inventory)

        self.assertEqual("walking:to-bank", self.brain.tick_state(state))
        self.assertEqual("to-bank", self.brain.runtime.route_key)
        self.assertEqual(("goto", {"x": 292, "y": 590}), self.client.commands[-1])


class IntegrationContractTests(unittest.TestCase):
    def test_control_client_sends_one_json_line(self):
        ready = threading.Event()
        received = []
        server = socket.socket()
        server.bind(("127.0.0.1", 0))
        server.listen(1)
        port = server.getsockname()[1]

        def serve():
            ready.set()
            conn, _ = server.accept()
            with conn:
                stream = conn.makefile("rwb")
                received.append(json.loads(stream.readline()))
                stream.write(b'{"ok":true,"state":{"logged_in":true}}\n')
                stream.flush()
            server.close()

        thread = threading.Thread(target=serve)
        thread.start()
        ready.wait(2)
        self.assertTrue(ControlClient("127.0.0.1", port).state()["logged_in"])
        thread.join(2)
        self.assertEqual({"cmd": "state", "args": {"section": "all"}}, received[0])

    def test_fleet_maps_slot_to_base_plus_slot(self):
        ports = []

        def factory(host, port):
            ports.append((host, port))
            return FakeClient(state_at(120, 648))

        FleetController(load_roster(), "127.0.0.1", 19020, client_factory=factory)
        self.assertEqual(list(range(19020, 19030)), [port for _, port in ports])

    def test_route_resume_uses_nearest_staged_waypoint(self):
        profile = load_roster()[4]
        clock = FakeClock()
        client = FakeClient()
        brain = PlayerBrain(profile, client, clock=clock, rng=random.Random(1))
        route = brain._travel_route()
        brain._drive_route("initial-travel", route, (96, 565), clock())
        self.assertEqual({"x": 90, "y": 560}, client.commands[-1][1])


if __name__ == "__main__":
    unittest.main()
