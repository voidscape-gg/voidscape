# Launch cracker campaign

Voidscape's launch cracker campaign is a finite, owner-controlled supply of Christmas crackers earned through ordinary play. Slice 4A provides the durable control plane, Slice 4B provides exact gameplay delivery, and Slice 4C implements the shared remaining-count HUD behind a reserved custom-client capability.

## Slice 4A contract

- The authoritative remaining count is the global `player_cache` integer `void_cracker_pool_remaining` (`playerID=0`). A missing row and `0` both mean inactive.
- `CrackerCampaignService` is owned by `World`. It loads the row lazily because `World` is constructed before the game database is opened.
- A failed database read is an unknown state, never an assumed zero. The next status request retries the durable read.
- `::cracker` and `::cracker status` show the current state. `::cracker N`, `::cracker set N`, and `::cracker 0` replace the exact remaining count.
- The command accepts `0..1,000,000`, is owner-only even outside production lockdown, and adds an operation-specific staff log with intended, previous, resulting, and success/failure state.
- Pool replacement reads and writes within `GameDatabase.atomicallySettled(...)`. A lost COMMIT acknowledgement is verified against durable state; an unresolved result is reported as uncertain and is not published to the service cache.

The legacy `player_cache` table has no uniqueness constraint on `(playerID,key)`. Voidscape runs one authoritative game-server writer for this pool. Every non-idempotent owner set uses the existing global-cache save operation, which deletes all matching rows before inserting one canonical row. Release QA must verify exactly one row after the initial owner set. Running multiple game-server processes against the same game database is outside this contract.

## Configuration

| Key | Launch value | Meaning |
|---|---:|---|
| `want_cracker_campaign` | `true` | Enables the service; a zero pool still keeps awards inactive. |
| `cracker_campaign_npc_kill_denominator` | `500` | One roll in 500 eligible, normally rewarded NPC kills. |
| `cracker_campaign_skilling_denominator` | `1000` | One roll in 1,000 eligible skilling ticks. |

Invalid non-positive denominators fall back to those safe defaults rather than becoming a guaranteed drop. Production uses the repository's inclusive random helper; deterministic tests inject a chance roller and prove the exact configured denominators reach it without off-by-one math.

## Slice 4B gameplay eligibility

- NPCs produce one candidate only on the normal, unsuppressed death-reward path, after the final credited player (including the owner of a related player-controlled NPC) is resolved and their NPC kill count is incremented. Plugin-suppressed and plugin-handled deaths, unowned NPC-versus-NPC deaths, PvP deaths, offline-owner drop resolution, and kills while the credited player is on Classic Tutorial Island or Void Tutorial Isle do not roll.
- Skilling produces a candidate only after the main player actually gains positive XP from an action with `useFatigue=true` and `fromQuest=false`. OpenPK point conversion, capped/blocked zero-XP attempts, quest XP, both tutorial regions, Attack, Defence, Strength, Hits, Ranged, and every Magic alias are excluded. Prayer remains eligible by design.
- A transient player attribute limits skilling to one candidate per server tick, preventing a single action with several XP updates from receiving several rolls. NPC kills retain one roll per eligible completed kill.
- Eligibility has no special staff-rank filter. Existing command lockdown remains the control for staff-only fixtures and pool administration.
- A missing, disabled, failed-to-load, or zero pool stops before the random roll. Unsuccessful rolls use the world service's cached count and open no player-save or award transaction.

## Exact delivery and settlement

After a winning candidate, `CrackerCampaignTransactions` reserves the player's save lifecycle and owns item `575`, the pool decrement, and provenance as one settled operation:

1. Read and validate the authoritative remaining count inside the database transaction.
2. Under the inventory's brief local lock, reject an unsupported client or full inventory, add one exact unnoted Christmas cracker without ground spill, and capture the exact inventory snapshot.
3. Persist that snapshot, replace the remaining count with `before - 1`, and synchronously record `launch_cracker_campaign` provenance with trigger (`npc_kill` or `skilling`), before/after counts, and exact item-instance id.
4. Publish the new process-local count only after a confirmed commit.

Pool-empty, inventory-full, client-unsupported, busy-save, and interrupted-lifecycle outcomes mint nothing and do not decrement. A confirmed rollback removes only the exact tentative item. A lost COMMIT acknowledgement checks both the durable pool and exact persisted inventory item. An unresolved or mixed settlement never guesses or compensates: it returns `UNCERTAIN`, retains the save fence, and quarantines the session for restart/operator reconciliation.

Only a confirmed award refreshes the client inventory, sends the private success message, and asks `WorldAnnouncementService` to broadcast:

`@mag@[Cracker Hunt] @whi@<name> has got a @yel@cracker drop@whi@!`

The broadcast follows the normal `want_world_announcements` policy, which launch configuration requires. A winning roll with a full inventory instead sends a private clear-space warning; there is no decrement, broadcast, or ground item.

Crackers minted by pre-existing systems such as presents or Void Rush are outside this campaign pool.

## Slice 4B verification

`tests/cracker-campaign-unit.sh` deterministically covers exact award/pool/provenance commit, empty/full/unsupported no-mutation paths, injected failures at every durable write, lifecycle interruption and exact compensation, lost-COMMIT reconciliation, unresolved-outcome quarantine, concurrent winners against one remaining cracker, configured denominator delivery, production hook ordering, actual-XP eligibility, tutorial/combat exclusions, Prayer eligibility, post-commit messages, and the no-ground-spill contract. The canonical build and deterministic suite are green.

Disposable live voidbot QA also passed against the isolated launch database with temporary `1/1` odds: a real chicken kill awarded and announced, a real woodcutting action produced actual XP and campaign awards, a full inventory rejected two eligible kills without decrement/provenance/ground spill, freeing one slot allowed the final award, and another kill after pool exhaustion changed nothing. The four committed provenance transitions were `npc_kill 3→2`, `skilling 2→1`, `skilling 1→0`, and a separately reset `npc_kill 1→0`. The database, account, inventory, campaign rows, and local `1/500`/`1/1000` odds were restored; SQLite integrity was `ok` and no QA process remained. Evidence: `tmp/cracker-campaign-4b-qa/summary.json`.

## Slice 4C shared HUD

The server reuses senderless `SEND_SERVER_MESSAGE` / `MessageType.QUEST` metadata rather than adding an opcode or packet field:

`@vscrackercampaign@v1|remaining`

- State is sent after game settings on both a normal login and a resumed/reconnected session. Owner pool changes and confirmed award settlement publish the new count to compatible online players. A confirmed award publishes only after item, pool, and provenance commit together; inventory-full/client-unsupported/busy/interrupted outcomes leave the prior HUD state alone because the pool did not change.
- `0` means hidden. Disabled, unknown, malformed, unsupported-version, negative, and over-`1,000,000` states fail closed to hidden. Failed or uncertain award settlement and an uncertain owner set publish hidden immediately; a confirmed owner-set rollback republishes its known previous count. After an unknown result, the next authoritative load republishes a valid repaired count.
- The shared client consumes recognized campaign envelopes before chat display. It clears stale campaign state on fresh game entry, logout, and return to the login screen.
- Capability is active for custom clients `>= 10132`. The coordinated release source now sets the shared PC/native-Android/TeaVM client and enforced launch preset to `10132`; authentic clients and older custom clients receive no envelope. Production delivery still waits for the final platform builds, QA, and deployment gates.
- Positive counts render an item-`575` icon and gold plaque. Labels are exact and comma-formatted: `1,000 crackers available`, `999 crackers available`, and singular `1 cracker available`; zero renders neither plaque nor label.
- Desktop/web placement is top-right below the top tab strip, with a right-edge margin. The native-Android branch sits left of vitals and drops beneath the location plaque when narrow. Plaque height is `26` pixels normally and `24` in compact HUD mode; width is at least `132` pixels and otherwise follows label width. The kill-feed baseline moves to at least `13` pixels below a visible plaque. Blocking dialogs, open side panels/menus, sleep/cinematic locks, and other hidden-HUD states suppress it.

`tests/cracker-campaign-hud-unit.sh`, `tests/workbench-cracker-campaign-hud-unit.sh`, and `tests/web-teavm-cracker-campaign-hud-unit.sh` pass. The PC Workbench scenario captured `1,000`, `999`, `1`, and hidden `0` at real presets `4` (`1024x768`) and `5` (`512x346`), for eight PNG/state pairs. It passed exact grammar, visibility, in-frame geometry, top-tab/location non-overlap, kill-feed offset, capture-count, and finally-style original viewport/count restoration. Report: `tmp/workbench-cracker-campaign-4c/scenario-2.json`.

Slice 8B native-Android emulator proof passed at `tmp/android-cracker-campaign-8b-accepted5`. The real login path released the IME without a harness Back dismissal; positive portrait and landscape captures showed the item-`575` plaque in-frame without location/vitals/rail/chat overlap, while zero and malformed state stayed hidden and campaign metadata never appeared in chat history. The guarded fixture required explicit logout, restored the exact campaign and player-cache rows, removed its staff-log tail, restored both affected sequences and the player's group, delayed and repeated the comparison, and ended with SQLite integrity `ok`.

Slice 8B TeaVM proof passed at `tmp/web-teavm-cracker-campaign-10132/summary.json` over a real `ws://127.0.0.1:43496` session at client `10132`. Login delivered the authoritative `1000` snapshot; real owner commands broadcast `999`, `1`, and `0`; labels were exact, zero hid the plaque, and chat leak count stayed zero. A diagnostics-only compiled-parser phase after the network proof checked five malformed variants (wrong version, negative, over maximum, nonnumeric, and extra field), all hidden without chat leakage. The welcome-modal retry was a smoke timing race only.

These artifacts close the local TeaVM and Android-emulator HUD gate, not the release gate. Google Play still serves `8 / 1.0.7` and no AAB was uploaded in Slice 8B; a clean signed `9 / 1.0.8` preflight and Play update must precede production protocol `10132`. Physical Android/device and hosted web deployment verification remain pending.

Operational rollback is `::cracker 0`, followed by disabling `want_cracker_campaign` and restarting if necessary. This stops new campaign awards and hides the compatible-client HUD but does not reclaim already committed crackers. A zero cache row is harmless to older code.
