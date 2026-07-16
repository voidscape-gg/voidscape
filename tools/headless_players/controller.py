#!/usr/bin/env python3
"""Drive ordinary voidbot player sessions as quiet world-activity gatherers.

This process owns no game connections or credentials.  A supervisor starts one
``voidbotd`` per account; this controller talks to their loopback JSON-lines
control sockets.  Profile ``slot`` N maps to ``--control-port-base + N``.

The controller intentionally never issues chat, friend, trade, or duel commands.
Those requests are therefore handled by the real player session's ordinary server
path and ignored by the unattended account, just like an AFK player.
"""

from __future__ import annotations

import argparse
import json
import logging
import random
import re
import socket
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, MutableMapping, Sequence


LOG = logging.getLogger("headless_players")
DEFAULT_ROSTER = Path(__file__).with_name("roster.json")
COLOUR_TAG = re.compile(r"@[A-Za-z0-9]{3}@")
Point = tuple[int, int]


class RosterError(ValueError):
    """The public behavior roster is malformed."""


class ControlError(RuntimeError):
    """A voidbot control socket was unavailable or rejected a command."""


def _point(value: Mapping[str, Any], label: str) -> Point:
    try:
        return int(value["x"]), int(value["y"])
    except (KeyError, TypeError, ValueError) as exc:
        raise RosterError(f"{label} must contain integer x/y") from exc


def _points(values: Iterable[Mapping[str, Any]], label: str) -> list[Point]:
    points = [_point(value, f"{label}[{index}]") for index, value in enumerate(values)]
    if not points:
        raise RosterError(f"{label} must not be empty")
    return points


def load_roster(path: str | Path = DEFAULT_ROSTER) -> list[dict[str, Any]]:
    """Load and strictly validate the non-secret fleet roster."""
    roster_path = Path(path)
    try:
        payload = json.loads(roster_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RosterError(f"cannot load roster {roster_path}: {exc}") from exc
    if not isinstance(payload, dict) or payload.get("schema") != 1:
        raise RosterError("roster root must have schema 1")
    players = payload.get("players")
    if not isinstance(players, list) or not players:
        raise RosterError("roster players must be a non-empty array")

    seen_ids: set[str] = set()
    seen_names: set[str] = set()
    seen_slots: set[int] = set()
    validated: list[dict[str, Any]] = []
    if len(players) != 10:
        raise RosterError("headless roster must contain exactly 10 players")
    for index, raw in enumerate(players):
        if not isinstance(raw, dict):
            raise RosterError(f"players[{index}] must be an object")
        profile = dict(raw)
        profile_id = str(profile.get("id", "")).strip()
        username = str(profile.get("username", "")).strip()
        try:
            slot = int(profile["slot"])
        except (KeyError, TypeError, ValueError) as exc:
            raise RosterError(f"players[{index}].slot must be an integer") from exc
        if not profile_id or profile_id in seen_ids:
            raise RosterError(f"duplicate or empty player id: {profile_id!r}")
        canonical_name = " ".join(username.lower().split())
        if not canonical_name or canonical_name in seen_names:
            raise RosterError(f"duplicate or empty username: {username!r}")
        if not 1 <= len(username) <= 12:
            raise RosterError(f"{profile_id}.username must be 1..12 characters")
        if slot < 0 or slot in seen_slots:
            raise RosterError(f"duplicate or negative slot: {slot}")

        appearance = profile.get("appearance")
        if not isinstance(appearance, dict):
            raise RosterError(f"{profile_id}.appearance must be an object")
        if appearance.get("gender") not in ("male", "female"):
            raise RosterError(f"{profile_id}.appearance.gender must be male or female")
        for key in ("hairColour", "topColour", "trouserColour", "skinColour", "head", "body"):
            if not isinstance(appearance.get(key), int):
                raise RosterError(f"{profile_id}.appearance.{key} must be an integer")
        if appearance["head"] < 1 or appearance["body"] < 1:
            raise RosterError(f"{profile_id} appearance head/body use one-based ids")

        activity = profile.get("activity")
        if not isinstance(activity, dict) or activity.get("type") not in (
            "woodcutting", "mining", "net_fishing"
        ):
            raise RosterError(f"{profile_id}.activity has unsupported type")
        if not isinstance(activity.get("skill"), str):
            raise RosterError(f"{profile_id}.activity.skill must be a string")
        for key in ("toolItemIds", "outputItemIds"):
            values = activity.get(key)
            if not isinstance(values, list) or not values or not all(isinstance(v, int) for v in values):
                raise RosterError(f"{profile_id}.activity.{key} must contain item ids")
        if not isinstance(activity.get("inventoryHighWater"), int) or not 1 <= activity["inventoryHighWater"] <= 30:
            raise RosterError(f"{profile_id}.activity.inventoryHighWater must be 1..30")
        _validate_nodes(
            activity.get("nodes"),
            f"{profile_id}.activity.nodes",
            require_rotation=True,
        )

        training = activity.get("training")
        if training is not None:
            if not isinstance(training, dict) or not isinstance(training.get("untilLevel"), int):
                raise RosterError(f"{profile_id}.activity.training requires untilLevel")
            _validate_nodes(
                training.get("nodes"),
                f"{profile_id}.activity.training.nodes",
                require_rotation=True,
            )

        bank = activity.get("bank")
        if not isinstance(bank, dict) or not isinstance(bank.get("npcId"), int):
            raise RosterError(f"{profile_id}.activity.bank requires npcId")
        _point(bank.get("approach", {}), f"{profile_id}.activity.bank.approach")
        _points(bank.get("route", []), f"{profile_id}.activity.bank.route")
        staging_bank = profile.get("stagingBank")
        if staging_bank is not None:
            if not isinstance(staging_bank, dict) or not isinstance(staging_bank.get("npcId"), int):
                raise RosterError(f"{profile_id}.stagingBank requires npcId")
            _point(staging_bank.get("approach", {}), f"{profile_id}.stagingBank.approach")
            _points(staging_bank.get("route", []), f"{profile_id}.stagingBank.route")
        _points(profile.get("travelRoute", []), f"{profile_id}.travelRoute")
        journey = profile.get("journey")
        if journey is not None:
            if not isinstance(journey, dict) or not isinstance(journey.get("currencyItemId"), int):
                raise RosterError(f"{profile_id}.journey requires currencyItemId")
            for leg_name in ("funding", "port", "karamja"):
                leg = journey.get(leg_name)
                if not isinstance(leg, dict) or not isinstance(leg.get("npcId"), int):
                    raise RosterError(f"{profile_id}.journey.{leg_name} requires npcId")
                _point(leg.get("approach", {}), f"{profile_id}.journey.{leg_name}.approach")
                _points(leg.get("route", []), f"{profile_id}.journey.{leg_name}.route")
            funding = journey["funding"]
            if not isinstance(funding.get("saleItemId"), int) or not isinstance(
                funding.get("minimumCoins"), int
            ):
                raise RosterError(f"{profile_id}.journey.funding requires saleItemId/minimumCoins")
            for leg_name in ("port", "karamja"):
                if not isinstance(journey[leg_name].get("fare"), int):
                    raise RosterError(f"{profile_id}.journey.{leg_name} requires fare")
            ardougne = journey.get("ardougne")
            if not isinstance(ardougne, dict):
                raise RosterError(f"{profile_id}.journey requires ardougne")
            _points(ardougne.get("route", []), f"{profile_id}.journey.ardougne.route")
            for region_name in ("karamja", "ardougne"):
                bounds = journey[region_name].get("bounds")
                if not isinstance(bounds, dict) or not all(
                    isinstance(bounds.get(key), int)
                    for key in ("minX", "maxX", "minY", "maxY")
                ):
                    raise RosterError(f"{profile_id}.journey.{region_name}.bounds is invalid")
            survival = journey["karamja"].get("survival")
            if survival is not None:
                if not isinstance(survival, dict):
                    raise RosterError(f"{profile_id}.journey.karamja.survival must be an object")
                for key in (
                    "shieldItemId",
                    "bananaItemId",
                    "bananaTarget",
                    "npcAvoidRadius",
                    "bananaBatchSize",
                    "stepMaxAttempts",
                ):
                    if not isinstance(survival.get(key), int) or int(survival[key]) <= 0:
                        raise RosterError(
                            f"{profile_id}.journey.karamja.survival.{key} must be positive"
                        )
                for key in ("foodItemIds", "hostileNpcIds"):
                    values = survival.get(key)
                    if not isinstance(values, list) or not values or not all(
                        isinstance(value, int) for value in values
                    ):
                        raise RosterError(
                            f"{profile_id}.journey.karamja.survival.{key} requires ids"
                        )
                if survival["bananaItemId"] not in survival["foodItemIds"]:
                    raise RosterError(
                        f"{profile_id}.journey.karamja.survival.foodItemIds must include bananaItemId"
                    )
                for key in ("departureHitsRatio", "emergencyHitsRatio"):
                    value = survival.get(key)
                    if not isinstance(value, (int, float)) or not 0 < float(value) <= 1:
                        raise RosterError(
                            f"{profile_id}.journey.karamja.survival.{key} must be in (0,1]"
                        )
                if float(survival["emergencyHitsRatio"]) > float(
                    survival["departureHitsRatio"]
                ):
                    raise RosterError(
                        f"{profile_id}.journey.karamja.survival emergency ratio exceeds departure ratio"
                    )
                supply_bounds = survival.get("supplyBounds")
                if not isinstance(supply_bounds, dict) or not all(
                    isinstance(supply_bounds.get(key), int)
                    for key in ("minX", "maxX", "minY", "maxY")
                ):
                    raise RosterError(
                        f"{profile_id}.journey.karamja.survival.supplyBounds is invalid"
                    )
                _points(
                    survival.get("supplyRoute", []),
                    f"{profile_id}.journey.karamja.survival.supplyRoute",
                )
                safe_stages = _points(
                    survival.get("safeStagingPoints", []),
                    f"{profile_id}.journey.karamja.survival.safeStagingPoints",
                )
                _validate_nodes(
                    survival.get("bananaTrees"),
                    f"{profile_id}.journey.karamja.survival.bananaTrees",
                )
                safe_radius = int(survival.get("safeStageRadius", 2))
                if safe_radius < 0:
                    raise RosterError(
                        f"{profile_id}.journey.karamja.survival.safeStageRadius must not be negative"
                    )
                for index, tree in enumerate(survival["bananaTrees"]):
                    approach = _point(
                        tree["approach"],
                        f"{profile_id}.journey.karamja.survival.bananaTrees[{index}].approach",
                    )
                    if min(
                        abs(approach[0] - stage[0]) + abs(approach[1] - stage[1])
                        for stage in safe_stages
                    ) > safe_radius:
                        raise RosterError(
                            f"{profile_id}.journey.karamja banana tree approach is not a safe stage"
                        )
                karamja_route = _points(
                    journey["karamja"]["route"], f"{profile_id}.journey.karamja.route"
                )
                for start, end in zip(karamja_route, karamja_route[1:]):
                    if start[0] != end[0] and start[1] != end[1]:
                        raise RosterError(
                            f"{profile_id}.journey.karamja survival route must be cardinal"
                        )
                ordered_markers = []
                marker_points: dict[str, Point] = {}
                for key in (
                    "supplyCommitPoint",
                    "hazardSafePre",
                    "hazardGatePoint",
                    "hazardSafePost",
                ):
                    marker = _point(
                        survival.get(key, {}),
                        f"{profile_id}.journey.karamja.survival.{key}",
                    )
                    if marker not in karamja_route:
                        raise RosterError(
                            f"{profile_id}.journey.karamja.survival.{key} is not on route"
                        )
                    ordered_markers.append(karamja_route.index(marker))
                    marker_points[key] = marker
                if ordered_markers != sorted(ordered_markers):
                    raise RosterError(
                        f"{profile_id}.journey.karamja survival markers are out of order"
                    )
                for key in ("supplyCommitPoint", "hazardSafePre", "hazardSafePost"):
                    if marker_points[key] not in safe_stages:
                        raise RosterError(
                            f"{profile_id}.journey.karamja.survival.{key} is not a safe stage"
                        )

        profile["id"] = profile_id
        profile["username"] = username
        profile["slot"] = slot
        seen_ids.add(profile_id)
        seen_names.add(canonical_name)
        seen_slots.add(slot)
        validated.append(profile)

    if seen_slots != set(range(10)):
        raise RosterError("headless roster slots must be exactly 0..9")
    return sorted(validated, key=lambda profile: profile["slot"])


def _validate_nodes(
    value: Any, label: str, *, require_rotation: bool = False
) -> None:
    if not isinstance(value, list) or not value:
        raise RosterError(f"{label} must be a non-empty array")
    for index, node in enumerate(value):
        if not isinstance(node, dict):
            raise RosterError(f"{label}[{index}] must be an object")
        if not isinstance(node.get("objectId"), int) or int(node.get("command", 0)) not in (1, 2):
            raise RosterError(f"{label}[{index}] requires objectId and command 1/2")
        _point(node.get("location", {}), f"{label}[{index}].location")
        _point(node.get("approach", {}), f"{label}[{index}].approach")
    if require_rotation:
        approaches = {
            _point(node["approach"], f"{label}[{index}].approach")
            for index, node in enumerate(value)
        }
        if len(approaches) < 2:
            raise RosterError(
                f"{label} requires at least two physically distinct approaches"
            )


class ControlClient:
    """Small JSON-lines client for one voidbotd control socket."""

    def __init__(self, host: str, port: int, timeout: float = 5.0):
        self.host = host
        self.port = int(port)
        self.timeout = float(timeout)

    def request(self, cmd: str, args: Mapping[str, Any] | None = None) -> dict[str, Any]:
        request = {"cmd": cmd, "args": dict(args or {})}
        try:
            with socket.create_connection((self.host, self.port), timeout=self.timeout) as conn:
                conn.settimeout(self.timeout)
                stream = conn.makefile("rwb")
                stream.write((json.dumps(request, separators=(",", ":")) + "\n").encode("utf-8"))
                stream.flush()
                line = stream.readline()
        except OSError as exc:
            raise ControlError(f"cannot reach {self.host}:{self.port}: {exc}") from exc
        if not line:
            raise ControlError(f"{self.host}:{self.port} closed without a response")
        try:
            response = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ControlError(f"{self.host}:{self.port} returned invalid JSON") from exc
        if not response.get("ok"):
            raise ControlError(str(response.get("error", f"{cmd} rejected")))
        return response

    def state(self) -> dict[str, Any]:
        response = self.request("state", {"section": "all"})
        state = response.get("state")
        if not isinstance(state, dict):
            raise ControlError("state all did not return an object")
        return state

    def command(self, cmd: str, args: Mapping[str, Any] | None = None) -> dict[str, Any]:
        return self.request(cmd, args)


@dataclass
class PendingGather:
    node_key: str
    output_count: int
    xp: int
    deadline: float


@dataclass
class PendingGatherRotation:
    node_key: str
    approach: Point
    origin: Point
    phase: str
    command_at: float
    route_seq_baseline: int
    route_attempts: int
    cancel_attempts: int


@dataclass
class PendingJourneySale:
    item_count: int
    coin_count: int
    deadline: float


@dataclass
class PendingJourneyBanana:
    banana_count: int
    tree_index: int
    deadline: float


@dataclass
class PendingJourneyEat:
    food_count: int
    deadline: float


@dataclass
class Runtime:
    route_key: str | None = None
    route_index: int = 0
    walk_target: Point | None = None
    walk_position: Point | None = None
    walk_progress_at: float = 0.0
    walk_retries: int = 0
    route_blocked_until: float = 0.0
    pending_gather: PendingGather | None = None
    pending_gather_rotation: PendingGatherRotation | None = None
    pending_journey_sale: PendingJourneySale | None = None
    journey_phase: str | None = None
    journey_interaction_key: str | None = None
    journey_command_at: float = 0.0
    journey_command_attempts: int = 0
    journey_dialog_active_until: float = 0.0
    journey_shop_command_at: float = 0.0
    journey_shop_sell_attempts: int = 0
    journey_shop_close_attempts: int = 0
    journey_blocked_until: float = 0.0
    pending_journey_banana: PendingJourneyBanana | None = None
    journey_banana_tree_index: int = 0
    journey_banana_failures: int = 0
    journey_banana_command_at: float = 0.0
    pending_journey_eat: PendingJourneyEat | None = None
    journey_eat_attempts: int = 0
    journey_eat_command_at: float = 0.0
    journey_survival_recovery_until: float = 0.0
    journey_equip_attempts: int = 0
    journey_equip_command_at: float = 0.0
    journey_survival_step_at: float = 0.0
    journey_survival_step_attempts: int = 0
    node_cooldowns: MutableMapping[str, float] = field(default_factory=dict)
    last_successful_node_key: str | None = None
    idle_until: float = 0.0
    bank_sweep_sent: bool = False
    bank_phase: str = "deposit"
    bank_command_at: float = 0.0
    bank_retries: int = 0
    bank_open_attempts: int = 0
    bank_missing_tool_until: float = 0.0
    appearance_submitted_at: float = 0.0
    dialog_signature: tuple[str, ...] | None = None
    dialog_replied_at: float = 0.0
    hazard_retreat_origin: Point | None = None
    hazard_retreat_waypoint: Point | None = None
    hazard_step_at: float = 0.0
    hazard_step_attempts: int = 0
    hazard_recovery_until: float = 0.0
    last_position: Point | None = None
    travel_started: bool = False
    travel_failures: int = 0
    hazard_blocked_until: float = 0.0
    status: str = "starting"


class PlayerBrain:
    """One deterministic, retry-bounded gathering state machine."""

    def __init__(
        self,
        profile: Mapping[str, Any],
        client: Any,
        *,
        clock: Callable[[], float] = time.monotonic,
        rng: random.Random | None = None,
        logger: logging.Logger = LOG,
    ):
        self.profile = dict(profile)
        self.client = client
        self.clock = clock
        # System-seeded by default so pauses and node choices do not repeat after
        # every process restart. Tests inject a seeded Random for reproducibility.
        self.rng = rng or random.Random()
        self.log = logger
        self.runtime = Runtime()

    @property
    def name(self) -> str:
        return str(self.profile["username"])

    def tick(self) -> str:
        state = self.client.state()
        return self.tick_state(state)

    def tick_state(self, state: Mapping[str, Any]) -> str:
        now = self.clock()
        if not state.get("logged_in"):
            return self._status("waiting-login")
        pos = self._position(state)
        if pos is None:
            return self._status("waiting-position")

        if self._handle_onboarding(state, pos, now):
            self.runtime.last_position = pos
            return self.runtime.status
        if self._in_void_island(pos):
            self.runtime.last_position = pos
            return self._status("waiting-onboarding")

        if self._detect_travel_failure(pos, now):
            self.runtime.last_position = pos
            return self.runtime.status
        if self._health_guard(state, pos, now):
            self.runtime.last_position = pos
            return self.runtime.status
        if now < self.runtime.hazard_blocked_until:
            self.runtime.last_position = pos
            return self._status("hazard-cooldown")
        if now < self.runtime.route_blocked_until:
            self.runtime.last_position = pos
            return self._status("route-cooldown")

        bank = state.get("bank") or {}
        if bank.get("open"):
            self.runtime.bank_open_attempts = 0
            self.runtime.bank_retries = 0
            self._reset_route()
            result = self._handle_bank(state, now)
            self.runtime.last_position = pos
            return result
        self.runtime.bank_sweep_sent = False
        self.runtime.bank_phase = "deposit"

        shop = state.get("shop") or {}
        if shop.get("open"):
            self._reset_route()
            if self.profile.get("journey"):
                result = self._handle_journey_shop(state, pos, now)
            else:
                result = self._status("waiting-unmanaged-shop")
            self.runtime.last_position = pos
            return result
        self.runtime.journey_shop_close_attempts = 0

        if now < self.runtime.journey_blocked_until:
            self.runtime.last_position = pos
            return self._status("journey-cooldown")

        if self.runtime.pending_gather is not None:
            if self._poll_gather(state, now):
                self.runtime.last_position = pos
                return self.runtime.status
        if self.runtime.pending_gather_rotation is not None:
            if self._poll_gather_rotation(state, pos, now):
                self.runtime.last_position = pos
                return self.runtime.status
        if now < self.runtime.idle_until:
            self.runtime.last_position = pos
            return self._status("idle")

        activity = self._activity()
        nodes, _ = self._active_nodes_and_outputs(state)
        bank_route = self._bank_route()
        bank_approach = self._bank_approach()
        at_task = self._distance_to_nodes(pos, nodes) <= int(activity.get("taskRadius", 8))
        at_bank = self._distance(pos, bank_approach) <= int(activity["bank"].get("searchRadius", 5))
        near_bank_route = self._distance_to_route(pos, bank_route) <= 10
        inventory = list(state.get("inventory") or [])
        has_tool = self._has_any_item(inventory, activity["toolItemIds"])
        needs_bank = len(inventory) >= int(activity["inventoryHighWater"]) or not has_tool

        if self._handle_journey(state, pos, now, at_task, at_bank, near_bank_route):
            self.runtime.last_position = pos
            return self.runtime.status

        # An optional pre-task staging bank is derived entirely from live state.
        # A bulky starter inventory or missing role tool takes the staging detour;
        # after deposit/withdraw the lean inventory naturally resumes the ordinary
        # task route. At-task loads always use the role's normal bank.
        staging_bank = self.profile.get("stagingBank")
        # Being on the role bank route is live evidence that a completed gather
        # cycle is already heading to Catherby. Never redirect that load to the
        # one-time/recovery staging bank, including after a controller restart.
        should_stage = bool(staging_bank) and not at_task and not near_bank_route and needs_bank
        if should_stage:
            staging_approach = _point(staging_bank["approach"], f"{self.name}.stagingBank.approach")
            staging_radius = int(staging_bank.get("searchRadius", 5))
            if self._distance(pos, staging_approach) <= staging_radius:
                self._reset_route()
                self._open_bank(now, staging_bank)
            else:
                staging_route = _points(staging_bank["route"], f"{self.name}.stagingBank.route")
                self.runtime.travel_started = True
                self._drive_route("to-staging-bank", staging_route, pos, now)
            self.runtime.last_position = pos
            return self.runtime.status

        if not at_task and not at_bank and not near_bank_route:
            route = self._travel_route()
            self.runtime.travel_started = True
            self._drive_route("initial-travel", route, pos, now)
            self.runtime.last_position = pos
            return self.runtime.status

        if needs_bank:
            if at_bank:
                self._reset_route()
                self._open_bank(now)
            else:
                self._drive_route("to-bank", bank_route, pos, now)
            self.runtime.last_position = pos
            return self.runtime.status

        if not at_task:
            self._drive_route("to-task", list(reversed(bank_route)), pos, now)
            self.runtime.last_position = pos
            return self.runtime.status

        self._reset_route()
        result = self._gather(state, pos, nodes, now)
        self.runtime.last_position = pos
        return result

    def _handle_onboarding(self, state: Mapping[str, Any], pos: Point, now: float) -> bool:
        appearance = state.get("appearance") or {}
        if appearance.get("open"):
            if now - self.runtime.appearance_submitted_at >= 3.0:
                look = self.profile["appearance"]
                # The roster records canonical one-based sprite ids; voidbot's wire
                # command takes headType/bodyType, which are both zero-based.
                args = {
                    "gender": look["gender"],
                    "head": int(look["head"]) - 1,
                    "body": int(look["body"]) - 1,
                    "hair_colour": int(look["hairColour"]),
                    "top_colour": int(look["topColour"]),
                    "trouser_colour": int(look["trouserColour"]),
                    "skin_colour": int(look["skinColour"]),
                    "hair_style": 0,
                    "country": "none",
                    "ironman": 0,
                    "one_xp": 0,
                }
                self.client.command("design-character", args)
                self.runtime.appearance_submitted_at = now
                self._status("submitted-appearance")
            else:
                self._status("waiting-appearance-close")
            return True

        dialog = state.get("dialog") or {}
        if not dialog.get("open"):
            return False
        options = [COLOUR_TAG.sub("", str(option)).strip() for option in dialog.get("options", [])]
        signature = tuple(options)
        if self.runtime.dialog_signature == signature and now - self.runtime.dialog_replied_at < 3.0:
            self._status("waiting-dialog-close")
            return True
        if len(options) >= 2 and options[0].startswith("I've played Classic") and options[1].startswith("Skip"):
            self.client.command("menu-reply", {"option": 1})
            self.runtime.dialog_signature = signature
            self.runtime.dialog_replied_at = now
            self._status("selected-welcome-skip")
            return True
        if len(options) >= 3 and options[1].startswith("Forager's Path - 2x XP:"):
            self.client.command("menu-reply", {"option": 1})
            self.runtime.dialog_signature = signature
            self.runtime.dialog_replied_at = now
            self._status("selected-forager")
            return True
        journey_reply = self._journey_dialog_reply(state, pos, signature)
        if journey_reply is not None:
            option, status, hold_seconds = journey_reply
            self.client.command("menu-reply", {"option": option})
            self.runtime.dialog_signature = signature
            self.runtime.dialog_replied_at = now
            self.runtime.journey_dialog_active_until = now + hold_seconds
            self.runtime.journey_command_attempts = 0
            self._status(status)
            return True
        # Never answer arbitrary NPC, bank, trade, duel, or future content menus.
        self._status("waiting-unrecognized-dialog")
        return True

    def _journey_dialog_reply(
        self,
        state: Mapping[str, Any],
        pos: Point,
        signature: tuple[str, ...],
    ) -> tuple[int, str, float] | None:
        """Resolve only the exact menus for this profile's ordinary boat trip."""
        journey = self.profile.get("journey")
        if not journey:
            return None
        phase = self._journey_phase(pos, journey)
        inventory = list(state.get("inventory") or [])
        currency_id = int(journey["currencyItemId"])
        coins = self._item_count(inventory, [currency_id])

        if phase == "mainland":
            funding = journey["funding"]
            funding_approach = _point(
                funding["approach"], f"{self.name}.journey.funding.approach"
            )
            sale_item_held = self._has_any_item(inventory, [funding["saleItemId"]])
            if (
                sale_item_held
                and self._distance(pos, funding_approach)
                <= int(funding.get("searchRadius", 4))
                and signature == ("Yes please, what are you selling?", "No thanks")
            ):
                return 0, "opening-funding-shop", 20.0

            port = journey["port"]
            port_approach = _point(port["approach"], f"{self.name}.journey.port.approach")
            if (
                coins >= int(funding["minimumCoins"])
                and self._distance(pos, port_approach) <= int(port.get("searchRadius", 5))
            ):
                if signature == ("I'd rather go to Crandor Isle", "Yes please", "No thankyou"):
                    return 1, "paying-port-fare", 12.0
                if signature == ("Yes please", "No thankyou"):
                    return 0, "paying-port-fare", 12.0

        if phase == "karamja":
            karamja = journey["karamja"]
            approach = _point(karamja["approach"], f"{self.name}.journey.karamja.approach")
            survival = karamja.get("survival") or {}
            shield_ready = not survival or any(
                int(item.get("id", -1)) == int(survival["shieldItemId"])
                and bool(item.get("wielded"))
                for item in inventory
            )
            if (
                coins >= int(karamja["fare"])
                and coins < int(journey["funding"]["minimumCoins"])
                and shield_ready
                and self._distance(pos, approach) <= int(karamja.get("searchRadius", 5))
            ):
                contracts = {
                    ("Can I board this ship?", "Does Karamja have any unusual customs then?"): (
                        0,
                        "requesting-customs-search",
                        20.0,
                    ),
                    (
                        "Why?",
                        "Search away I have nothing to hide",
                        "You're not putting your hands on my things",
                    ): (1, "accepting-customs-search", 20.0),
                    ("Ok", "Oh, I'll not bother then"): (0, "paying-customs-fare", 12.0),
                }
                return contracts.get(signature)
        return None

    def _handle_journey_shop(
        self, state: Mapping[str, Any], pos: Point, now: float
    ) -> str:
        journey = self.profile["journey"]
        funding = journey["funding"]
        approach = _point(funding["approach"], f"{self.name}.journey.funding.approach")
        if (
            self._journey_phase(pos, journey) != "mainland"
            or self._distance(pos, approach) > int(funding.get("searchRadius", 4))
        ):
            return self._status("waiting-unexpected-shop")
        if now < self.runtime.journey_blocked_until:
            return self._status("journey-shop-cooldown")

        inventory = list(state.get("inventory") or [])
        sale_item_id = int(funding["saleItemId"])
        currency_id = int(journey["currencyItemId"])
        item_count = self._item_count(inventory, [sale_item_id])
        coin_count = self._item_count(inventory, [currency_id])
        pending = self.runtime.pending_journey_sale
        if pending is not None:
            if item_count < pending.item_count and coin_count > pending.coin_count:
                self.runtime.pending_journey_sale = None
                self.runtime.journey_shop_sell_attempts = 0
                return self._status("funding-sale-confirmed")
            if now < pending.deadline:
                return self._status("waiting-funding-sale")
            self.runtime.pending_journey_sale = None
            if self.runtime.journey_shop_sell_attempts >= 4:
                return self._block_journey(now, "funding-sale-timeout")
            return self._status("funding-sale-unconfirmed")

        if now - self.runtime.journey_shop_command_at < 1.5:
            return self._status("journey-shop-settling")
        if item_count > 0:
            if self.runtime.journey_shop_sell_attempts >= 4:
                return self._block_journey(now, "funding-sale-timeout")
            self.client.command(
                "shop-sell", {"id": sale_item_id, "stock": 0, "amount": 1}
            )
            self.runtime.pending_journey_sale = PendingJourneySale(
                item_count=item_count,
                coin_count=coin_count,
                deadline=now + float(funding.get("saleTimeoutSeconds", 8.0)),
            )
            self.runtime.journey_shop_sell_attempts += 1
            self.runtime.journey_shop_command_at = now
            return self._status("selling-funding-item")

        if self.runtime.journey_shop_close_attempts >= 4:
            return self._block_journey(now, "funding-shop-close-timeout")
        self.client.command("shop-close")
        self.runtime.journey_shop_close_attempts += 1
        self.runtime.journey_shop_command_at = now
        return self._status("closing-funding-shop")

    def _handle_journey(
        self,
        state: Mapping[str, Any],
        pos: Point,
        now: float,
        at_task: bool,
        at_bank: bool,
        near_bank_route: bool,
    ) -> bool:
        journey = self.profile.get("journey")
        if not journey:
            return False

        phase = self._journey_phase(pos, journey)
        if self.runtime.journey_phase != phase:
            self._reset_route()
            self.runtime.journey_phase = phase
            self.runtime.journey_interaction_key = None
            self.runtime.journey_command_attempts = 0
            self.runtime.journey_dialog_active_until = 0.0
            self.runtime.pending_journey_sale = None
            self.runtime.pending_journey_banana = None
            self.runtime.pending_journey_eat = None
            self.runtime.journey_banana_failures = 0
            self.runtime.journey_eat_attempts = 0
            self.runtime.journey_equip_attempts = 0
            self.runtime.journey_survival_step_attempts = 0

        inventory = list(state.get("inventory") or [])
        coins = self._item_count(inventory, [journey["currencyItemId"]])

        # Fare balances are durable live-state phase evidence. Validate them
        # before allowing task/bank semantics, since the Catherby destination is
        # itself inside the broad Ardougne phase bounds.
        if phase == "ardougne" and coins >= int(journey["karamja"]["fare"]):
            self._block_journey(now, "ardougne-fare-state-invalid")
            return True

        if at_task or at_bank or near_bank_route:
            return False

        if phase == "ardougne":
            route = _points(journey["ardougne"]["route"], f"{self.name}.journey.ardougne.route")
            self.runtime.travel_started = True
            self._drive_route("journey:ardougne-to-catherby", route, pos, now)
            return True

        if phase == "karamja":
            leg = journey["karamja"]
            approach = _point(leg["approach"], f"{self.name}.journey.karamja.approach")
            at_customs = self._distance(pos, approach) <= int(leg.get("searchRadius", 5))
            fare = int(leg["fare"])
            if coins >= int(journey["funding"]["minimumCoins"]):
                self._block_journey(now, "karamja-fare-state-invalid")
                return True
            if coins < fare:
                if at_customs and self._recent_journey_transition(state):
                    self._status("waiting-customs-transition")
                else:
                    self._block_journey(now, "karamja-fare-state-invalid")
                return True
            if self._handle_karamja_survival(state, pos, now, leg):
                return True
            if at_customs:
                self._journey_talk("karamja-customs", int(leg["npcId"]), now)
            else:
                route = _points(leg["route"], f"{self.name}.journey.karamja.route")
                self.runtime.travel_started = True
                self._drive_route("journey:karamja-customs", route, pos, now)
            return True

        funding = journey["funding"]
        port = journey["port"]
        port_approach = _point(port["approach"], f"{self.name}.journey.port.approach")
        at_port = self._distance(pos, port_approach) <= int(port.get("searchRadius", 5))
        minimum_coins = int(funding["minimumCoins"])
        if at_port and coins < minimum_coins:
            if self._recent_journey_transition(state):
                self._status("waiting-port-transition")
            else:
                self._block_journey(now, "port-fare-state-invalid")
            return True

        if coins < minimum_coins:
            if not self._has_any_item(inventory, [funding["saleItemId"]]):
                self._block_journey(now, "journey-funding-missing")
                return True
            funding_approach = _point(
                funding["approach"], f"{self.name}.journey.funding.approach"
            )
            if self._distance(pos, funding_approach) <= int(funding.get("searchRadius", 4)):
                self._journey_talk("lumbridge-funding", int(funding["npcId"]), now)
            else:
                route = _points(funding["route"], f"{self.name}.journey.funding.route")
                self.runtime.travel_started = True
                self._drive_route("journey:lumbridge-funding", route, pos, now)
            return True

        if at_port:
            self._journey_talk("port-sarim-boat", int(port["npcId"]), now)
        else:
            route = _points(port["route"], f"{self.name}.journey.port.route")
            self.runtime.travel_started = True
            self._drive_route("journey:port-sarim", route, pos, now)
        return True

    def _handle_karamja_survival(
        self,
        state: Mapping[str, Any],
        pos: Point,
        now: float,
        leg: Mapping[str, Any],
    ) -> bool:
        """Prepare and gate Ultraz's ordinary-player crossing of Karamja."""
        survival = leg.get("survival")
        if not survival:
            return False

        inventory = list(state.get("inventory") or [])
        route = _points(leg["route"], f"{self.name}.journey.karamja.route")
        expanded_route = self._expand_cardinal_route(route)
        route_index = min(
            range(len(expanded_route)),
            key=lambda index: self._distance(pos, expanded_route[index]),
        )
        safe_points = _points(
            survival["safeStagingPoints"],
            f"{self.name}.journey.karamja.survival.safeStagingPoints",
        )
        safe_radius = int(survival.get("safeStageRadius", 0))
        at_safe_stage = self._distance_to_route(pos, safe_points) <= safe_radius
        hits = (state.get("skills") or {}).get("hits") or {}
        current_hits = int(hits.get("cur", 0))
        maximum_hits = int(hits.get("max", 0))
        if current_hits <= 0 or maximum_hits <= 0:
            self._reset_route()
            self._status("waiting-karamja-hitpoints")
            return True

        if not at_safe_stage:
            # Inventory actions are issued only from audited safe stages. If an
            # external combat move displaced the player, abandon the pending
            # acknowledgement and actively recover a stage first.
            self.runtime.pending_journey_eat = None
            self.runtime.pending_journey_banana = None

        shield_id = int(survival["shieldItemId"])
        shield = next(
            (item for item in inventory if int(item.get("id", -1)) == shield_id),
            None,
        )
        shield_ready = shield is not None and bool(shield.get("wielded"))
        if shield_ready:
            self.runtime.journey_equip_attempts = 0

        banana_id = int(survival["bananaItemId"])
        banana_count = self._item_count(inventory, [banana_id])
        supply_commit_index = expanded_route.index(
            _point(
                survival["supplyCommitPoint"],
                f"{self.name}.journey.karamja.survival.supplyCommitPoint",
            )
        )
        needs_supply = (
            banana_count < int(survival["bananaTarget"])
            and route_index <= supply_commit_index
        )
        supply_capacity_invalid = needs_supply and (
            len(inventory) + int(survival["bananaTarget"]) - banana_count > 30
        )

        pre_index = expanded_route.index(
            _point(
                survival["hazardSafePre"],
                f"{self.name}.journey.karamja.survival.hazardSafePre",
            )
        )
        gate_index = expanded_route.index(
            _point(
                survival["hazardGatePoint"],
                f"{self.name}.journey.karamja.survival.hazardGatePoint",
            )
        )
        post_index = expanded_route.index(
            _point(
                survival["hazardSafePost"],
                f"{self.name}.journey.karamja.survival.hazardSafePost",
            )
        )
        in_forced_corridor = pre_index <= route_index < post_index
        next_gate_target = self._next_karamja_gate_target(
            pos, route, expanded_route, route_index, in_forced_corridor
        )
        hostile_near = self._karamja_segment_has_hostile(
            state,
            pos,
            next_gate_target,
            survival["hostileNpcIds"],
            int(survival["npcAvoidRadius"]),
        )
        hostile_current_near = self._karamja_segment_has_hostile(
            state,
            pos,
            pos,
            survival["hostileNpcIds"],
            int(survival.get("combatAvoidRadius", 4)),
        )
        emergency = current_hits / maximum_hits <= float(
            survival["emergencyHitsRatio"]
        )

        if not at_safe_stage and (
            not shield_ready or hostile_near or emergency or supply_capacity_invalid
        ):
            self._move_karamja_to_safe_stage(
                pos,
                safe_points,
                now,
                survival,
                expanded_route,
                route_index,
                pre_index,
                gate_index,
                post_index,
            )
            return True

        if not at_safe_stage:
            if needs_supply:
                self._drive_or_pick_karamja_bananas(
                    banana_count, pos, now, survival, leg
                )
                return True
            if in_forced_corridor:
                self._walk_karamja_route_step(
                    pos,
                    expanded_route,
                    route_index,
                    1,
                    now,
                    max_attempts=int(survival["stepMaxAttempts"]),
                )
                return True
            return False

        if self._poll_karamja_eat(inventory, now):
            return True
        if self._poll_karamja_banana(inventory, now, survival):
            return True

        if shield is None:
            self._block_journey(now, "karamja-shield-missing")
            return True
        if not shield_ready:
            if now - self.runtime.journey_equip_command_at < 2.0:
                self._status("waiting-karamja-shield")
                return True
            if self.runtime.journey_equip_attempts >= 4:
                self._block_journey(now, "karamja-shield-timeout")
                return True
            self.client.command("equip", {"slot": int(shield["slot"])})
            self.runtime.journey_equip_command_at = now
            self.runtime.journey_equip_attempts += 1
            self._status("equipping-karamja-shield")
            return True

        if now < self.runtime.journey_survival_recovery_until:
            self._status("waiting-safe-food-recovery")
            return True

        if hostile_current_near:
            self._reset_route()
            self._status("waiting-safe-hazard-clear")
            return True

        if current_hits / maximum_hits < float(survival["departureHitsRatio"]):
            food = self._first_inventory_item(inventory, survival["foodItemIds"])
            if food is None:
                # Natural hitpoint regeneration can still recover the account;
                # never leave the audited safe stage under-strength.
                self._status("waiting-safe-hitpoint-recovery")
                return True
            if now - self.runtime.journey_eat_command_at < 1.5:
                self._status("waiting-safe-eat-retry")
                return True
            if self.runtime.journey_eat_attempts >= 4:
                self._block_journey(now, "karamja-eat-timeout")
                return True
            self.client.command(
                "item-command",
                {"slot": int(food["slot"]), "amount": 1, "command": 0},
            )
            self.runtime.pending_journey_eat = PendingJourneyEat(
                food_count=self._item_count(inventory, survival["foodItemIds"]),
                deadline=now + float(survival.get("eatTimeoutSeconds", 4.0)),
            )
            self.runtime.journey_eat_attempts += 1
            self.runtime.journey_eat_command_at = now
            self._status("eating-at-safe-stage")
            return True

        if needs_supply:
            if supply_capacity_invalid:
                self._block_journey(now, "karamja-banana-capacity-invalid")
                return True
            self._drive_or_pick_karamja_bananas(
                banana_count, pos, now, survival, leg
            )
            return True

        if hostile_near:
            self._reset_route()
            self._status("waiting-safe-hazard-clear")
            return True

        self.runtime.journey_banana_failures = 0
        if in_forced_corridor:
            self._walk_karamja_route_step(
                pos,
                expanded_route,
                route_index,
                1,
                now,
                max_attempts=int(survival["stepMaxAttempts"]),
            )
            return True
        return False

    def _drive_or_pick_karamja_bananas(
        self,
        banana_count: int,
        pos: Point,
        now: float,
        survival: Mapping[str, Any],
        leg: Mapping[str, Any],
    ) -> None:
        trees = list(survival["bananaTrees"])
        tree_index = self.runtime.journey_banana_tree_index % len(trees)
        tree = trees[tree_index]
        approach = _point(
            tree["approach"],
            f"{self.name}.journey.karamja.survival.bananaTrees[{tree_index}].approach",
        )
        if pos != approach:
            if self._in_bounds(pos, survival["supplyBounds"]):
                supply_route = _points(
                    survival["supplyRoute"],
                    f"{self.name}.journey.karamja.survival.supplyRoute",
                )
            else:
                journey_route = _points(
                    leg["route"], f"{self.name}.journey.karamja.route"
                )
                anchor = _point(
                    survival["supplyRoute"][-1],
                    f"{self.name}.journey.karamja.survival.supplyRoute[-1]",
                )
                commit = _point(
                    survival["supplyCommitPoint"],
                    f"{self.name}.journey.karamja.survival.supplyCommitPoint",
                )
                anchor_index = journey_route.index(anchor)
                commit_index = journey_route.index(commit)
                supply_route = list(
                    reversed(journey_route[anchor_index : commit_index + 1])
                )
            if supply_route[-1] != approach:
                supply_route.append(approach)
            self.runtime.travel_started = True
            self._drive_route(
                f"journey:karamja-bananas:{tree_index}",
                supply_route,
                pos,
                now,
                arrival_radius=0,
            )
            return
        if now - self.runtime.journey_banana_command_at < 1.5:
            self._status("waiting-banana-pick-retry")
            return
        location = _point(
            tree["location"],
            f"{self.name}.journey.karamja.survival.bananaTrees[{tree_index}].location",
        )
        self.client.command(
            "object-action",
            {
                "x": location[0],
                "y": location[1],
                "id": int(tree["objectId"]),
                "which": int(tree.get("command", 1)),
                "walk_x": approach[0],
                "walk_y": approach[1],
            },
        )
        self.runtime.pending_journey_banana = PendingJourneyBanana(
            banana_count=banana_count,
            tree_index=tree_index,
            deadline=now + float(survival.get("bananaTimeoutSeconds", 8.0)),
        )
        self.runtime.journey_banana_command_at = now
        self._status("picking-karamja-bananas")

    def _poll_karamja_banana(
        self,
        inventory: Sequence[Mapping[str, Any]],
        now: float,
        survival: Mapping[str, Any],
    ) -> bool:
        pending = self.runtime.pending_journey_banana
        if pending is None:
            return False
        count = self._item_count(inventory, [survival["bananaItemId"]])
        gained = count - pending.banana_count
        batch_size = int(survival.get("bananaBatchSize", 5))
        target = int(survival["bananaTarget"])
        if count >= target or gained >= batch_size:
            self.runtime.pending_journey_banana = None
            self.runtime.journey_banana_failures = 0
            if gained >= batch_size:
                trees = list(survival["bananaTrees"])
                self.runtime.journey_banana_tree_index = (
                    pending.tree_index + 1
                ) % len(trees)
            self._status("karamja-banana-confirmed")
            return True
        if now < pending.deadline:
            self._status(
                "waiting-karamja-banana-batch"
                if gained > 0
                else "waiting-karamja-banana"
            )
            return True

        if gained > 0:
            # An interrupted/non-batch server action still proved ordinary item
            # acquisition. Keep this tree selected and issue another pick after
            # the settle window rather than treating a partial batch as empty.
            self.runtime.pending_journey_banana = None
            self.runtime.journey_banana_failures = 0
            self._status("karamja-banana-partial-confirmed")
            return True

        trees = list(survival["bananaTrees"])
        self.runtime.pending_journey_banana = None
        self.runtime.journey_banana_failures += 1
        self.runtime.journey_banana_tree_index = (pending.tree_index + 1) % len(trees)
        if self.runtime.journey_banana_failures >= len(trees):
            self.runtime.journey_banana_failures = 0
            self._block_journey(now, "karamja-banana-supply-unavailable")
        else:
            self._status("rotating-karamja-banana-tree")
        return True

    def _poll_karamja_eat(
        self,
        inventory: Sequence[Mapping[str, Any]],
        now: float,
    ) -> bool:
        pending = self.runtime.pending_journey_eat
        if pending is None:
            return False
        survival = self.profile["journey"]["karamja"]["survival"]
        food_count = self._item_count(inventory, survival["foodItemIds"])
        if food_count < pending.food_count:
            self.runtime.pending_journey_eat = None
            self.runtime.journey_eat_attempts = 0
            self.runtime.journey_survival_recovery_until = now + float(
                survival.get("eatRecoverySeconds", 1.5)
            )
            self._status("safe-food-confirmed")
            return True
        if now < pending.deadline:
            self._status("waiting-safe-food-proof")
            return True
        self.runtime.pending_journey_eat = None
        if self.runtime.journey_eat_attempts >= 4:
            self._block_journey(now, "karamja-eat-timeout")
        else:
            self._status("safe-food-unconfirmed")
        return True

    def _move_karamja_to_safe_stage(
        self,
        pos: Point,
        safe_points: Sequence[Point],
        now: float,
        survival: Mapping[str, Any],
        expanded_route: Sequence[Point],
        route_index: int,
        pre_index: int,
        gate_index: int,
        post_index: int,
    ) -> None:
        safe_indices = [
            expanded_route.index(point)
            for point in safe_points
            if point in expanded_route
        ]
        if pre_index < route_index < gate_index:
            target_index = pre_index
        elif gate_index <= route_index < post_index:
            target_index = post_index
        else:
            target_index = min(
                safe_indices, key=lambda index: abs(route_index - index)
            )
        direction = (target_index > route_index) - (target_index < route_index)
        self._walk_karamja_route_step(
            pos,
            expanded_route,
            route_index,
            direction,
            now,
            status="moving-to-karamja-safe-stage",
            interval=float(survival.get("retreatStepIntervalSeconds", 1.0)),
            max_attempts=int(survival["stepMaxAttempts"]),
        )

    def _walk_karamja_route_step(
        self,
        pos: Point,
        expanded_route: Sequence[Point],
        route_index: int,
        direction: int,
        now: float,
        *,
        status: str = "crossing-karamja-hazard",
        interval: float = 1.0,
        max_attempts: int = 4,
    ) -> None:
        self._reset_route()
        if self.runtime.last_position != pos:
            self.runtime.journey_survival_step_attempts = 0
        if (
            self.runtime.journey_survival_step_attempts > 0
            and now - self.runtime.journey_survival_step_at < interval
        ):
            self._status(status)
            return
        if self.runtime.journey_survival_step_attempts >= max_attempts:
            # `max_attempts` bounds the adjacent-tile candidate cycle; it is not a
            # deadline for reaching safety. Native combat rejects every walk during
            # its first three rounds, so a generic 30-second journey block here can
            # strand the account in combat just before retreat becomes legal. Keep
            # the retry index bounded while preserving the one-command-per-interval
            # throttle until a changed coordinate proves movement landed.
            self.runtime.journey_survival_step_attempts = 0
        route_tile = expanded_route[route_index]
        if pos != route_tile:
            target = route_tile
        else:
            target_index = max(
                0, min(len(expanded_route) - 1, route_index + direction)
            )
            target = expanded_route[target_index]
        step = self._one_tile_toward(
            pos, target, self.runtime.journey_survival_step_attempts
        )
        if step == pos:
            self._status(status)
            return
        self.client.command("walk-step", {"x": step[0], "y": step[1]})
        self.runtime.journey_survival_step_at = now
        self.runtime.journey_survival_step_attempts += 1
        self._status(status)

    def _next_karamja_gate_target(
        self,
        pos: Point,
        route: Sequence[Point],
        expanded_route: Sequence[Point],
        expanded_index: int,
        forced_corridor: bool,
    ) -> Point:
        if forced_corridor:
            if pos != expanded_route[expanded_index]:
                return expanded_route[expanded_index]
            return expanded_route[min(len(expanded_route) - 1, expanded_index + 1)]
        nearest = min(
            range(len(route)), key=lambda index: self._distance(pos, route[index])
        )
        target_index = nearest + 1 if self._distance(pos, route[nearest]) <= 4 else nearest
        while target_index < len(route) and self._distance(pos, route[target_index]) <= 2:
            target_index += 1
        return route[min(len(route) - 1, target_index)]

    def _karamja_segment_has_hostile(
        self,
        state: Mapping[str, Any],
        start: Point,
        end: Point,
        npc_ids: Iterable[int],
        radius: int,
    ) -> bool:
        configured = {int(npc_id) for npc_id in npc_ids}
        squared_limit = float((int(radius) + 1) ** 2)
        # `npcs` is the exact current AOI frame. Prefer voidbot's separately exposed
        # three-second view so a single decode drop cannot falsely clear a lethal
        # corridor; fall back to the raw field for older daemons and unit fixtures.
        recent_npcs = state.get("recent_npcs")
        for npc in (state.get("npcs") if recent_npcs is None else recent_npcs) or []:
            try:
                if int(npc["id"]) not in configured:
                    continue
                npc_pos = int(npc["x"]), int(npc["y"])
            except (KeyError, TypeError, ValueError):
                continue
            if self._point_segment_distance_squared(npc_pos, start, end) < squared_limit:
                return True
        return False

    @staticmethod
    def _point_segment_distance_squared(point: Point, start: Point, end: Point) -> float:
        dx = end[0] - start[0]
        dy = end[1] - start[1]
        if dx == 0 and dy == 0:
            return float((point[0] - start[0]) ** 2 + (point[1] - start[1]) ** 2)
        projection = (
            (point[0] - start[0]) * dx + (point[1] - start[1]) * dy
        ) / float(dx * dx + dy * dy)
        projection = max(0.0, min(1.0, projection))
        nearest_x = start[0] + projection * dx
        nearest_y = start[1] + projection * dy
        return (point[0] - nearest_x) ** 2 + (point[1] - nearest_y) ** 2

    @staticmethod
    def _expand_cardinal_route(route: Sequence[Point]) -> list[Point]:
        expanded = [route[0]]
        for start, end in zip(route, route[1:]):
            dx = end[0] - start[0]
            dy = end[1] - start[1]
            if dx and dy:
                raise RosterError(f"non-cardinal Karamja survival leg: {start}->{end}")
            step_x = (dx > 0) - (dx < 0)
            step_y = (dy > 0) - (dy < 0)
            point = start
            while point != end:
                point = point[0] + step_x, point[1] + step_y
                expanded.append(point)
        return expanded

    @staticmethod
    def _first_inventory_item(
        inventory: Sequence[Mapping[str, Any]], item_ids: Iterable[int]
    ) -> Mapping[str, Any] | None:
        for item_id in item_ids:
            for item in inventory:
                if int(item.get("id", -1)) == int(item_id):
                    return item
        return None

    def _journey_talk(self, key: str, npc_id: int, now: float) -> None:
        if self.runtime.journey_interaction_key != key:
            self.runtime.journey_interaction_key = key
            self.runtime.journey_command_attempts = 0
            self.runtime.journey_command_at = 0.0
        if now < self.runtime.journey_dialog_active_until:
            self._status(f"waiting-dialog:{key}")
            return
        if now - self.runtime.journey_command_at < 4.0:
            self._status(f"waiting-talk-retry:{key}")
            return
        if self.runtime.journey_command_attempts >= 4:
            self._block_journey(now, f"journey-talk-timeout:{key}")
            return
        self._reset_route()
        self.client.command("npc-talk", {"id": npc_id})
        self.runtime.travel_started = True
        self.runtime.journey_command_at = now
        self.runtime.journey_command_attempts += 1
        self.runtime.journey_dialog_active_until = now + 20.0
        self._status(f"talking:{key}")

    def _block_journey(self, now: float, status: str) -> str:
        self._reset_route()
        self.runtime.journey_blocked_until = now + 30.0
        self.runtime.journey_dialog_active_until = 0.0
        self.runtime.journey_command_attempts = 0
        self.runtime.journey_shop_sell_attempts = 0
        self.runtime.journey_shop_close_attempts = 0
        self.runtime.pending_journey_banana = None
        self.runtime.pending_journey_eat = None
        self.runtime.journey_banana_failures = 0
        self.runtime.journey_eat_attempts = 0
        self.runtime.journey_equip_attempts = 0
        self.runtime.journey_survival_step_attempts = 0
        return self._status(status)

    def _journey_phase(self, pos: Point, journey: Mapping[str, Any]) -> str:
        if self._in_bounds(pos, journey["karamja"]["bounds"]):
            return "karamja"
        if self._in_bounds(pos, journey["ardougne"]["bounds"]):
            return "ardougne"
        # The audited Ardougne-to-Catherby route briefly arcs east of the broad
        # landing bounds.  Treat its exact corridor as Ardougne too so a tick or
        # controller restart on those waypoints cannot fall back to mainland
        # funding logic.
        ardougne_route = _points(
            journey["ardougne"]["route"], f"{self.name}.journey.ardougne.route"
        )
        if self._distance_to_route(pos, ardougne_route) <= 10:
            return "ardougne"
        return "mainland"

    @staticmethod
    def _in_bounds(pos: Point, bounds: Mapping[str, Any]) -> bool:
        return (
            int(bounds["minX"]) <= pos[0] <= int(bounds["maxX"])
            and int(bounds["minY"]) <= pos[1] <= int(bounds["maxY"])
        )

    @staticmethod
    def _recent_journey_transition(state: Mapping[str, Any], max_age_seconds: float = 15.0) -> bool:
        wall_now = time.time()
        expected = {"You pay 30 gold", "You board the ship"}
        for message in list(state.get("messages") or [])[-30:]:
            text = COLOUR_TAG.sub("", str(message.get("text", ""))).strip()
            try:
                age = wall_now - float(message["t"])
            except (KeyError, TypeError, ValueError):
                continue
            if text in expected and 0.0 <= age <= max_age_seconds:
                return True
        return False

    def _handle_bank(self, state: Mapping[str, Any], now: float) -> str:
        activity = self._activity()
        inventory = list(state.get("inventory") or [])
        bank_items = list((state.get("bank") or {}).get("items") or [])
        if now - self.runtime.bank_command_at < 1.5:
            return self._status("bank-settling")

        if self.runtime.bank_phase == "close":
            self.client.command("bank-close")
            self.runtime.bank_command_at = now
            return self._status("waiting-bank-close")

        if self.runtime.bank_phase == "deposit":
            if inventory:
                # Packet loss or a temporarily busy player: bounded retries.
                if self.runtime.bank_sweep_sent and self.runtime.bank_retries >= 3:
                    self.client.command("bank-close")
                    self.runtime.bank_phase = "close"
                    self.runtime.bank_retries = 0
                    self.runtime.bank_command_at = now
                    return self._status("bank-deposit-stalled")
                self.client.command("bank-deposit-all")
                if self.runtime.bank_sweep_sent:
                    self.runtime.bank_retries += 1
                self.runtime.bank_sweep_sent = True
                self.runtime.bank_command_at = now
                return self._status("retrying-bank-deposit" if self.runtime.bank_retries else "banking-inventory")
            self.runtime.bank_phase = "withdraw"
            self.runtime.bank_retries = 0

        tool = self._first_available_bank_item(bank_items, activity["toolItemIds"])
        if not self._has_any_item(inventory, activity["toolItemIds"]):
            if tool is None:
                self.client.command("bank-close")
                self.runtime.bank_phase = "close"
                self.runtime.bank_missing_tool_until = now + 300.0
                self.runtime.bank_command_at = now
                return self._status("tool-missing-from-bank")
            self.client.command("bank-withdraw", {"id": tool, "amount": 1})
            self.runtime.bank_command_at = now
            return self._status("withdrawing-tool")

        hazard = self.profile.get("hazard") or {}
        desired_food = int(hazard.get("foodToKeep", 0))
        if desired_food > 0:
            food_ids = list(hazard.get("foodItemIds") or [])
            held_food = self._item_count(inventory, food_ids)
            food = self._first_available_bank_item(bank_items, food_ids)
            if held_food < desired_food and food is not None:
                amount = min(desired_food - held_food, self._bank_item_count(bank_items, food))
                if amount > 0:
                    self.client.command("bank-withdraw", {"id": food, "amount": amount})
                    self.runtime.bank_command_at = now
                    return self._status("withdrawing-food")

        self.client.command("bank-close")
        self.runtime.bank_phase = "close"
        self.runtime.bank_retries = 0
        self.runtime.bank_command_at = now
        return self._status("bank-complete")

    def _open_bank(self, now: float, bank_spec: Mapping[str, Any] | None = None) -> None:
        if now < self.runtime.bank_missing_tool_until:
            self._status("tool-missing-cooldown")
            return
        if now - self.runtime.bank_command_at < 4.0:
            self._status("waiting-bank-open")
            return
        if self.runtime.bank_open_attempts >= 4:
            # A successful socket write only proves the NPC command reached
            # voidbotd, not that the server answered with OPEN_BANK. Bound silent
            # acknowledgements just like rejected control requests so a busy,
            # unreachable, or configuration-disabled banker cannot create an
            # endless command loop.
            self.runtime.route_blocked_until = now + 30.0
            self.runtime.bank_open_attempts = 0
            self.runtime.bank_retries = 0
            self.runtime.bank_command_at = now
            self._status("bank-open-timeout")
            return
        bank = bank_spec or self._activity()["bank"]
        try:
            self.client.command(
                "npc-command", {"id": int(bank["npcId"]), "which": int(bank.get("command", 1))}
            )
        except ControlError:
            self.runtime.bank_retries += 1
            if self.runtime.bank_retries >= 4:
                self.runtime.route_blocked_until = now + 30.0
                self.runtime.bank_retries = 0
            raise
        self.runtime.bank_command_at = now
        self.runtime.bank_open_attempts += 1
        self._status("opening-bank")

    def _gather(self, state: Mapping[str, Any], pos: Point, nodes: Sequence[Mapping[str, Any]], now: float) -> str:
        candidates = [node for node in nodes if self.runtime.node_cooldowns.get(self._node_key(node), 0) <= now]
        players = list(state.get("players") or [])
        crowding_radius = int(self._activity().get("crowdingRadius", 1))
        candidates = [node for node in candidates if not self._node_crowded(node, players, crowding_radius)]
        last_successful = self.runtime.last_successful_node_key
        if last_successful and any(self._node_key(node) != last_successful for node in nodes):
            # A successful gather advances the rotation. If every other node is
            # temporarily unavailable, wait instead of visibly farming the same
            # scenery object again; single-node activities may still continue.
            candidates = [
                node for node in candidates if self._node_key(node) != last_successful
            ]
        self.rng.shuffle(candidates)
        if not candidates:
            self.runtime.idle_until = now + self.rng.uniform(4.0, 10.0)
            return self._status("nodes-crowded-or-depleted")

        node = min(candidates, key=lambda candidate: self._distance(pos, _point(candidate["approach"], "node")))
        approach = _point(node["approach"], "node.approach")
        if self._distance(pos, approach) > 1:
            self._drive_route(
                f"node:{self._node_key(node)}",
                [approach],
                pos,
                now,
                arrival_radius=1,
            )
            return self.runtime.status

        location = _point(node["location"], "node.location")
        args = {
            "x": location[0],
            "y": location[1],
            "id": int(node["objectId"]),
            "which": int(node.get("command", 1)),
            "walk_x": approach[0],
            "walk_y": approach[1],
        }
        self.client.command("object-action", args)
        _, outputs = self._active_nodes_and_outputs(state)
        skill = str(self._activity()["skill"]).lower()
        self.runtime.pending_gather = PendingGather(
            node_key=self._node_key(node),
            output_count=self._item_count(state.get("inventory") or [], outputs),
            xp=int(((state.get("skills") or {}).get(skill) or {}).get("xp", 0)),
            deadline=now + float(self._activity().get("gatherTimeoutSeconds", 14)),
        )
        return self._status("gathering")

    def _poll_gather(self, state: Mapping[str, Any], now: float) -> bool:
        pending = self.runtime.pending_gather
        assert pending is not None
        _, outputs = self._active_nodes_and_outputs(state)
        skill = str(self._activity()["skill"]).lower()
        count = self._item_count(state.get("inventory") or [], outputs)
        xp = int(((state.get("skills") or {}).get(skill) or {}).get("xp", 0))
        if count > pending.output_count or xp > pending.xp:
            # Keep the just-used node out of the next choice so a gatherer rotates
            # through nearby scenery even when one approach tile is closest.
            self.runtime.node_cooldowns[pending.node_key] = now + float(
                self._activity().get("successNodeCooldownSeconds", 12)
            )
            self.runtime.last_successful_node_key = pending.node_key
            self.runtime.pending_gather = None
            pos = self._position(state)
            rotation_started = pos is not None and self._start_gather_rotation(
                state, pos, pending.node_key, now
            )
            if not rotation_started:
                self._schedule_gather_idle(now)
            self._status("gather-success")
            return True
        if now < pending.deadline:
            self._status("waiting-gather-result")
            return True
        self.runtime.node_cooldowns[pending.node_key] = now + self.rng.uniform(15.0, 40.0)
        self.runtime.pending_gather = None
        self.runtime.idle_until = now + self.rng.uniform(2.0, 5.0)
        self._status("gather-stalled")
        return True

    def _start_gather_rotation(
        self,
        state: Mapping[str, Any],
        pos: Point,
        completed_node_key: str,
        now: float,
    ) -> bool:
        """Interrupt a repeating gather before attempting ordinary route movement."""
        node = self._select_gather_rotation_node(
            state, pos, completed_node_key, now
        )
        if node is None:
            return False
        approach = _point(node["approach"], "node.approach")
        self._reset_route()
        pending = PendingGatherRotation(
            node_key=self._node_key(node),
            approach=approach,
            origin=pos,
            phase="cancel",
            command_at=0.0,
            route_seq_baseline=self._world_walk_route_seq(state),
            route_attempts=0,
            cancel_attempts=0,
        )
        # Persist intent before the socket write. A transient ControlError must not
        # drop the only state that prevents another object-action on the old node.
        self.runtime.pending_gather_rotation = pending
        self._send_gather_cancel_step(pending, pos, now)
        return True

    def _select_gather_rotation_node(
        self,
        state: Mapping[str, Any],
        pos: Point,
        completed_node_key: str,
        now: float,
        *,
        avoid_node_key: str | None = None,
    ) -> Mapping[str, Any] | None:
        nodes, _ = self._active_nodes_and_outputs(state)
        players = list(state.get("players") or [])
        crowding_radius = int(self._activity().get("crowdingRadius", 1))
        alternates = [
            node
            for node in nodes
            if self._node_key(node) != completed_node_key
            and self._distance(pos, _point(node["approach"], "node.approach")) > 0
        ]
        uncrowded = [
            node
            for node in alternates
            if not self._node_crowded(node, players, crowding_radius)
        ]
        ready_uncrowded = [
            node
            for node in uncrowded
            if self.runtime.node_cooldowns.get(self._node_key(node), 0) <= now
        ]
        # Prefer a node that can actually be used next. Movement itself remains
        # mandatory even when every alternate is occupied or cooling down because
        # WALK_TO_POINT is what cancels the server's repeating gather action. The
        # later `_gather` call still enforces both filters before another action.
        candidates = ready_uncrowded or uncrowded or alternates
        if avoid_node_key:
            other_candidates = [
                node
                for node in candidates
                if self._node_key(node) != avoid_node_key
            ]
            if other_candidates:
                candidates = other_candidates
        self.rng.shuffle(candidates)
        if not candidates:
            return None

        return min(
            candidates,
            key=lambda candidate: self._distance(
                pos, _point(candidate["approach"], "node.approach")
            ),
        )

    def _poll_gather_rotation(
        self, state: Mapping[str, Any], pos: Point, now: float
    ) -> bool:
        pending = self.runtime.pending_gather_rotation
        assert pending is not None
        if pos != pending.origin:
            # A coordinate change is the final acceptance proof. Packet writes and
            # even a successful route acknowledgement alone cannot prove movement.
            self.runtime.pending_gather_rotation = None
            self._schedule_gather_idle(now)
            self._status("gather-rotation-moving")
            return True

        if pending.phase == "backoff":
            backoff = float(
                self._activity().get("gatherRotationBackoffSeconds", 5.0)
            )
            if now - pending.command_at < backoff:
                self._status("gather-rotation-backoff")
                return True
            completed_node_key = self.runtime.last_successful_node_key or ""
            node = self._select_gather_rotation_node(
                state,
                pos,
                completed_node_key,
                now,
                avoid_node_key=pending.node_key,
            )
            if node is None:
                # Roster validation makes this unreachable for configured gather
                # roles, but retain the gate and retry quietly if live state ever
                # cannot provide a physically distinct target.
                pending.command_at = now
                self._status("gather-rotation-backoff")
                return True
            pending.node_key = self._node_key(node)
            pending.approach = _point(node["approach"], "node.approach")
            pending.phase = "cancel"
            self._send_gather_cancel_step(pending, pos, now)
            self._status("retrying-gather-interrupt")
            return True

        if pending.phase == "cancel":
            self._send_gather_cancel_step(pending, pos, now)
            self._status("canceling-gather-batch")
            return True

        if pending.phase == "cancel-sent":
            interval = float(
                self._activity().get("gatherRotationStepIntervalSeconds", 1.0)
            )
            if now - pending.command_at < interval:
                self._status("waiting-gather-interrupt")
                return True
            if self._gather_rotation_exhausted(pending):
                return self._backoff_gather_rotation(pending, now)
            pending.route_seq_baseline = self._world_walk_route_seq(state)
            self.client.command(
                "goto", {"x": pending.approach[0], "y": pending.approach[1]}
            )
            pending.route_attempts += 1
            pending.command_at = now
            pending.phase = "await-route"
            self._status("requesting-gather-rotation-route")
            return True

        if pending.phase == "await-route":
            route = state.get("world_walk_route") or {}
            route_seq = self._world_walk_route_seq(state)
            # The daemon-local sequence resets after voidbotd restarts while this
            # controller process can retain its pending baseline. Any positive,
            # different value is fresh for the current daemon lifetime.
            if route_seq > 0 and route_seq != pending.route_seq_baseline:
                if self._world_walk_route_matches(route, pending.approach):
                    pending.phase = "await-movement"
                    pending.command_at = now
                    self._status("waiting-gather-rotation-movement")
                    return True
                return self._retry_gather_rotation(pending, pos, now)
            ack_timeout = float(
                self._activity().get("gatherRotationAckTimeoutSeconds", 2.5)
            )
            if now - pending.command_at >= ack_timeout:
                return self._retry_gather_rotation(pending, pos, now)
            self._status("waiting-gather-rotation-route")
            return True

        if pending.phase == "await-movement":
            movement_timeout = float(
                self._activity().get("gatherRotationMovementTimeoutSeconds", 12.0)
            )
            if now - pending.command_at >= movement_timeout:
                return self._retry_gather_rotation(pending, pos, now)
            self._status("waiting-gather-rotation-movement")
            return True

        pending.phase = "cancel"
        self._status("canceling-gather-batch")
        return True

    def _send_gather_cancel_step(
        self, pending: PendingGatherRotation, pos: Point, now: float
    ) -> None:
        step = self._one_tile_toward(pos, pending.approach, pending.cancel_attempts)
        if step == pos:
            raise ControlError("gather rotation has no distinct cancellation step")
        self.client.command("walk-step", {"x": step[0], "y": step[1]})
        pending.cancel_attempts += 1
        pending.command_at = now
        pending.phase = "cancel-sent"

    def _retry_gather_rotation(
        self, pending: PendingGatherRotation, pos: Point, now: float
    ) -> bool:
        if self._gather_rotation_exhausted(pending):
            return self._backoff_gather_rotation(pending, now)
        # A rejected or missing world-route response cannot cancel a busy batch.
        # Reassert WALK_TO_POINT first, then attempt another fresh route next tick.
        pending.phase = "cancel"
        self._send_gather_cancel_step(pending, pos, now)
        self._status("retrying-gather-interrupt")
        return True

    def _gather_rotation_exhausted(self, pending: PendingGatherRotation) -> bool:
        maximum = int(
            self._activity().get("gatherRotationRouteMaxAttempts", 4)
        )
        return pending.route_attempts >= maximum

    def _backoff_gather_rotation(
        self, pending: PendingGatherRotation, now: float
    ) -> bool:
        self.runtime.node_cooldowns[pending.node_key] = max(
            self.runtime.node_cooldowns.get(pending.node_key, 0), now + 15.0
        )
        pending.phase = "backoff"
        pending.command_at = now
        pending.route_attempts = 0
        self._status("gather-rotation-backoff")
        return True

    @staticmethod
    def _world_walk_route_seq(state: Mapping[str, Any]) -> int:
        route = state.get("world_walk_route") or {}
        try:
            return int(route.get("seq", 0))
        except (TypeError, ValueError):
            return 0

    @staticmethod
    def _world_walk_route_matches(route: Mapping[str, Any], target: Point) -> bool:
        if not route.get("ok"):
            return False
        points = list(route.get("route") or [])
        if not points:
            return False
        try:
            last = int(points[-1]["x"]), int(points[-1]["y"])
        except (KeyError, TypeError, ValueError):
            return False
        return last == target

    def _schedule_gather_idle(self, now: float) -> None:
        idle = self._activity().get("idleSeconds", {"min": 2, "max": 6})
        self.runtime.idle_until = now + self.rng.uniform(
            float(idle["min"]), float(idle["max"])
        )

    def _health_guard(self, state: Mapping[str, Any], pos: Point, now: float) -> bool:
        hazard = self.profile.get("hazard") or {}
        if not hazard.get("monitor"):
            return False
        if now < self.runtime.hazard_recovery_until:
            self._status("waiting-hazard-recovery")
            return True
        hits = (state.get("skills") or {}).get("hits") or {}
        current = int(hits.get("cur", 0))
        maximum = int(hits.get("max", 0))
        if current <= 0 or maximum <= 0:
            self._reset_hazard_retreat()
            return False
        ratio = current / maximum
        threshold = float(hazard.get("eatBelowRatio", 0.7))
        food_ids = list(hazard.get("foodItemIds") or [])
        food = next((item for item in state.get("inventory") or [] if int(item.get("id", -1)) in food_ids), None)
        retreating = self.runtime.hazard_retreat_origin is not None

        if not retreating and ratio > threshold:
            return False
        if not retreating and now < self.runtime.hazard_blocked_until:
            # The outer tick reports the cooldown and, critically, does not resume
            # the hazardous route after a successful no-food retreat.
            return False

        if not retreating:
            self.runtime.hazard_retreat_origin = pos
            self.runtime.hazard_retreat_waypoint = self._retreat_point(pos)
            self.runtime.hazard_step_at = 0.0
            self.runtime.hazard_step_attempts = 0
            self._reset_route()

        origin = self.runtime.hazard_retreat_origin
        assert origin is not None
        if pos != origin:
            # WALK_TO_POINT movement is the only live proof that the server's native
            # combat retreat gate has released the player. ItemActionHandler rejects
            # eating before this point, so never send item-command speculatively.
            self._reset_hazard_retreat()
            if food is not None:
                self.client.command("item-command", {"slot": int(food["slot"]), "amount": 1, "command": 0})
                self.runtime.hazard_recovery_until = now + float(hazard.get("eatRecoverySeconds", 3.0))
                self.runtime.idle_until = self.runtime.hazard_recovery_until
                self._status("eating-after-retreat")
            else:
                self.runtime.hazard_blocked_until = now + float(
                    hazard.get("noFoodRecoveryCooldownSeconds", 60.0)
                )
                self._status("hazard-no-food-cooldown")
            return True

        interval = float(hazard.get("retreatStepIntervalSeconds", 1.0))
        if self.runtime.hazard_step_attempts > 0 and now - self.runtime.hazard_step_at < interval:
            self._status("waiting-retreat-movement")
            return True
        waypoint = self.runtime.hazard_retreat_waypoint
        assert waypoint is not None
        step = self._one_tile_toward(pos, waypoint, self.runtime.hazard_step_attempts)
        if step == pos:
            # There is no earlier waypoint (normally only the safe route origin).
            # Hold rather than issuing an ineffective walk or attempting to eat in
            # combat without movement proof.
            self.runtime.hazard_blocked_until = now + float(
                hazard.get("noFoodRecoveryCooldownSeconds", 60.0)
            )
            self._reset_hazard_retreat()
            self._status("hazard-retreat-unavailable")
            return True
        self.client.command("walk-step", {"x": step[0], "y": step[1]})
        self.runtime.hazard_step_at = now
        self.runtime.hazard_step_attempts += 1
        self._status("retreating-from-combat")
        return True

    def _reset_hazard_retreat(self) -> None:
        self.runtime.hazard_retreat_origin = None
        self.runtime.hazard_retreat_waypoint = None
        self.runtime.hazard_step_at = 0.0
        self.runtime.hazard_step_attempts = 0

    @staticmethod
    def _one_tile_toward(pos: Point, waypoint: Point, attempt: int) -> Point:
        """Choose an adjacent orthogonal tile that reduces waypoint distance.

        Alternating axes on retries gives the native walk handler another legal
        candidate when one adjacent tile is obstructed, without pathfinding or ever
        sending a non-adjacent combat walk.
        """
        dx = waypoint[0] - pos[0]
        dy = waypoint[1] - pos[1]
        if dx == 0 and dy == 0:
            return pos
        axes: list[str] = []
        if dx:
            axes.append("x")
        if dy:
            axes.append("y")
        if len(axes) == 2:
            preferred = "x" if abs(dx) >= abs(dy) else "y"
            other = "y" if preferred == "x" else "x"
            axis = (preferred, other)[attempt % 2]
        else:
            axis = axes[0]
        if axis == "x":
            return pos[0] + (1 if dx > 0 else -1), pos[1]
        return pos[0], pos[1] + (1 if dy > 0 else -1)

    def _detect_travel_failure(self, pos: Point, now: float) -> bool:
        if not self.runtime.travel_started or self.runtime.last_position is None:
            return False
        spawn = self._travel_route()[0]
        last = self.runtime.last_position
        if self._distance(pos, spawn) <= 3 and self._distance(last, spawn) >= 25:
            self.runtime.travel_failures += 1
            self._reset_route()
            self.runtime.pending_gather = None
            self.runtime.pending_journey_sale = None
            if self.runtime.travel_failures >= int((self.profile.get("hazard") or {}).get("maxTravelFailures", 3)):
                self.runtime.hazard_blocked_until = now + float(
                    (self.profile.get("hazard") or {}).get("failureCooldownSeconds", 600)
                )
                self._status("travel-failure-cooldown")
            else:
                self._status("travel-retry")
            return True
        return False

    def _drive_route(
        self,
        key: str,
        points: Sequence[Point],
        pos: Point,
        now: float,
        *,
        arrival_radius: int = 2,
    ) -> bool:
        if not points:
            return True
        if self.runtime.route_key != key:
            self.runtime.route_key = key
            nearest = min(range(len(points)), key=lambda index: self._distance(pos, points[index]))
            # A controller restart can find a player between waypoints. Resume at
            # the nearest staged point instead of walking all the way back to route
            # origin; once near a waypoint, advance naturally to the following one.
            nearest_distance = self._distance(pos, points[nearest])
            can_advance = nearest_distance <= 4 and (
                nearest < len(points) - 1 or nearest_distance <= arrival_radius
            )
            self.runtime.route_index = nearest + 1 if can_advance else nearest
            self.runtime.walk_target = None
            self.runtime.walk_retries = 0
            self.runtime.walk_position = pos
            self.runtime.walk_progress_at = now

        while (
            self.runtime.route_index < len(points)
            and self._distance(pos, points[self.runtime.route_index]) <= arrival_radius
        ):
            self.runtime.route_index += 1
            self.runtime.walk_target = None
            self.runtime.walk_retries = 0
        if self.runtime.route_index >= len(points):
            self._reset_route()
            self._status(f"route-complete:{key}")
            return True

        target = points[self.runtime.route_index]
        if self.runtime.walk_position != pos:
            self.runtime.walk_position = pos
            self.runtime.walk_progress_at = now
            self.runtime.walk_retries = 0
        should_send = self.runtime.walk_target != target or now - self.runtime.walk_progress_at >= 12.0
        if should_send:
            if self.runtime.walk_target == target:
                self.runtime.walk_retries += 1
            if self.runtime.walk_retries >= 4:
                self.runtime.route_blocked_until = now + 30.0
                self._reset_route()
                self._status(f"route-stalled:{key}")
                return False
            self.client.command("goto", {"x": target[0], "y": target[1]})
            self.runtime.walk_target = target
            self.runtime.walk_position = pos
            self.runtime.walk_progress_at = now
        self._status(f"walking:{key}")
        return False

    def _reset_route(self) -> None:
        self.runtime.route_key = None
        self.runtime.route_index = 0
        self.runtime.walk_target = None
        self.runtime.walk_position = None
        self.runtime.walk_progress_at = 0.0
        self.runtime.walk_retries = 0

    def _activity(self) -> Mapping[str, Any]:
        return self.profile["activity"]

    def _active_nodes_and_outputs(self, state: Mapping[str, Any]) -> tuple[list[Mapping[str, Any]], list[int]]:
        activity = self._activity()
        training = activity.get("training")
        if training:
            skill = str(activity["skill"]).lower()
            level = int(((state.get("skills") or {}).get(skill) or {}).get("max", 1))
            if level < int(training["untilLevel"]):
                return list(training["nodes"]), list(training.get("outputItemIds") or activity["outputItemIds"])
        return list(activity["nodes"]), list(activity["outputItemIds"])

    def _travel_route(self) -> list[Point]:
        return _points(self.profile["travelRoute"], f"{self.name}.travelRoute")

    def _bank_route(self) -> list[Point]:
        return _points(self._activity()["bank"]["route"], f"{self.name}.bank.route")

    def _bank_approach(self) -> Point:
        return _point(self._activity()["bank"]["approach"], f"{self.name}.bank.approach")

    def _retreat_point(self, pos: Point) -> Point:
        route = self._travel_route()
        nearest = min(range(len(route)), key=lambda index: self._distance(pos, route[index]))
        hazard = self.profile.get("hazard") or {}
        if hazard.get("escapeForward"):
            # Some mandatory chokepoints (notably White Wolf Mountain) have no
            # collision-valid path outside the NPC aggro rectangles. In those
            # cases, escaping toward the next staged waypoint makes each native
            # one-tile combat retreat eventual progress instead of backtracking
            # into the sentry that just attacked.
            return route[min(len(route) - 1, nearest + 1)]
        return route[max(0, nearest - 1)]

    @staticmethod
    def _position(state: Mapping[str, Any]) -> Point | None:
        position = state.get("position") or {}
        try:
            x, y = position["x"], position["y"]
            return (int(x), int(y)) if x is not None and y is not None else None
        except (KeyError, TypeError, ValueError):
            return None

    @staticmethod
    def _in_void_island(pos: Point) -> bool:
        return (16 <= pos[0] <= 32 and 17 <= pos[1] <= 42) or (33 <= pos[0] <= 46 and 17 <= pos[1] <= 41)

    @staticmethod
    def _distance(left: Point, right: Point) -> int:
        return abs(left[0] - right[0]) + abs(left[1] - right[1])

    def _distance_to_nodes(self, pos: Point, nodes: Sequence[Mapping[str, Any]]) -> int:
        return min(self._distance(pos, _point(node["approach"], "node.approach")) for node in nodes)

    def _distance_to_route(self, pos: Point, route: Sequence[Point]) -> int:
        return min(self._distance(pos, waypoint) for waypoint in route)

    @staticmethod
    def _item_count(items: Iterable[Mapping[str, Any]], item_ids: Iterable[int]) -> int:
        wanted = {int(item_id) for item_id in item_ids}
        return sum(int(item.get("amount", 1)) for item in items if int(item.get("id", -1)) in wanted)

    @staticmethod
    def _has_any_item(items: Iterable[Mapping[str, Any]], item_ids: Iterable[int]) -> bool:
        wanted = {int(item_id) for item_id in item_ids}
        return any(int(item.get("id", -1)) in wanted for item in items)

    @staticmethod
    def _first_available_bank_item(items: Iterable[Mapping[str, Any]], preferences: Iterable[int]) -> int | None:
        available = {int(item.get("id", -1)) for item in items if int(item.get("amount", 0)) > 0}
        return next((int(item_id) for item_id in preferences if int(item_id) in available), None)

    @staticmethod
    def _bank_item_count(items: Iterable[Mapping[str, Any]], item_id: int) -> int:
        return sum(int(item.get("amount", 0)) for item in items if int(item.get("id", -1)) == int(item_id))

    @staticmethod
    def _node_key(node: Mapping[str, Any]) -> str:
        location = _point(node["location"], "node.location")
        return f"{int(node['objectId'])}@{location[0]},{location[1]}"

    def _node_crowded(self, node: Mapping[str, Any], players: Iterable[Mapping[str, Any]], radius: int) -> bool:
        location = _point(node["location"], "node.location")
        approach = _point(node["approach"], "node.approach")
        for player in players:
            try:
                pos = int(player["x"]), int(player["y"])
            except (KeyError, TypeError, ValueError):
                continue
            if self._distance(pos, location) <= radius or self._distance(pos, approach) <= radius:
                return True
        return False

    def _status(self, status: str) -> str:
        if status != self.runtime.status:
            self.log.debug("%s: %s", self.name, status)
        self.runtime.status = status
        return status


class FleetController:
    def __init__(
        self,
        profiles: Sequence[Mapping[str, Any]],
        host: str,
        port_base: int,
        *,
        client_factory: Callable[[str, int], Any] = ControlClient,
        clock: Callable[[], float] = time.monotonic,
    ):
        self.brains = [
            PlayerBrain(profile, client_factory(host, port_base + int(profile["slot"])), clock=clock)
            for profile in profiles
        ]

    def tick(self) -> bool:
        reached = False
        for brain in self.brains:
            try:
                brain.tick()
                reached = True
            except ControlError as exc:
                LOG.warning("%s: %s", brain.name, exc)
            except Exception:
                LOG.exception("%s: controller error", brain.name)
        return reached


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roster", type=Path, default=DEFAULT_ROSTER)
    parser.add_argument("--control-host", default="127.0.0.1")
    parser.add_argument("--control-port-base", type=int, default=19020)
    parser.add_argument("--interval", type=float, default=1.0)
    parser.add_argument("--player", action="append", default=[], help="only run this profile id (repeatable)")
    parser.add_argument("--once", action="store_true", help="perform one fleet tick and exit")
    parser.add_argument("--log-level", choices=("DEBUG", "INFO", "WARNING", "ERROR"), default="INFO")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    logging.basicConfig(level=getattr(logging, args.log_level), format="%(asctime)s %(levelname)s %(message)s")
    try:
        profiles = load_roster(args.roster)
    except RosterError as exc:
        LOG.error("%s", exc)
        return 2
    if args.control_port_base < 1024 or args.control_port_base + 9 > 65535:
        LOG.error("control port base must leave ten ports in range 1024..65535")
        return 2
    if args.player:
        selected = set(args.player)
        profiles = [profile for profile in profiles if profile["id"] in selected]
        missing = selected - {profile["id"] for profile in profiles}
        if missing:
            LOG.error("unknown profile id(s): %s", ", ".join(sorted(missing)))
            return 2
    fleet = FleetController(profiles, args.control_host, args.control_port_base)
    if args.once:
        return 0 if fleet.tick() else 1
    try:
        while True:
            started = time.monotonic()
            fleet.tick()
            time.sleep(max(0.05, args.interval - (time.monotonic() - started)))
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
