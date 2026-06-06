# Bounty Hunter

Voidscape's bounty hunter system is a world-level Wilderness activity. It keeps at most one active mark per server world and uses the existing player appearance skull byte to show the target: skull type `2` already renders as a red skull in the client, so the feature does not add or change any packet opcodes.

## Lifecycle

- `World` owns one `BountyHunter` instance and exposes it through `World.getBountyHunter()`.
- `Player.processTick()` calls `BountyHunter.onPlayerTick(player)`. The controller records how long each online player has continuously been in a bounty-eligible Wilderness location and runs the mark lifecycle once per server tick.
- A target can be selected only after at least 120 seconds in Wilderness, combat level 10+, alive, non-staff, not dueling, and outside custom safe zones.
- When no mark is active, the controller checks every 30 seconds for eligible players and randomly marks one.
- Clearing a mark waits 30 seconds before the next selection attempt.

## Claiming and scoring

Claiming is triggered by normal validated PvP attack paths, not by proximity or raw click packets:

- Melee: `Mob.startCombat()` after player-vs-player skull assignment.
- Ranged: `RangeEvent.run()` after `PlayerRangePlayerTrigger` allows the shot.
- Throwing: `ThrowingEvent.run()` after `PlayerRangePlayerTrigger` allows the throw.
- Magic: `SpellHandler.checkCastOnPlayer()` after `SpellPlayerTrigger` allows the cast.

The first eligible attacker claims the mark. The system does not enforce 1v1 or modify multi-combat; it only tracks the claimant for scoring.

Outcomes:

- Claimant kills target: claimant gets 5 bounty points, 1 bounty kill, and a Void key.
- Target kills claimant: target gets 2 bounty points and 1 counter-kill.
- Target escapes Wilderness after a claimant has hunted them for at least 60 seconds: target gets 1 bounty point and 1 escape.
- Target leaves before a real claimed chase, dies to someone else, logs out, or otherwise becomes invalid: the mark clears without payout.

Stats persist in player cache keys: `bounty_points`, `bounty_kills`, `bounty_counter_kills`, and `bounty_escapes`. `::bounty` shows the active mark and the caller's own stats.

## Abuse controls

The feature is intentionally low-friction: it does not add account checks or block combat. It withholds bounty payout only when:

- the scorer and other player have the same current non-local IP, or
- the same account pair has already received a bounty payout inside the last 30 minutes.

Those checks affect only bounty rewards and points. The underlying PvP kill, death, drops, skull behavior, and Wilderness rules continue normally.
