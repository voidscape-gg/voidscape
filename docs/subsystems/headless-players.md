# Headless Players

Voidscape's world-activity fleet is ten ordinary registered player accounts. Each
account logs in through its own `voidbotd` process and is driven by the separate
`tools/headless_players/controller.py` process. There is no synthetic-player flag,
negative database ID, client marker, alternate movement packet, or server-side
social emulation.

## Roster

The tracked, non-secret roster is `tools/headless_players/roster.json`:

| Account | Activity | Bank |
|---|---|---|
| Fireee | Draynor woodcutting | Draynor |
| Ch0p | Rimmington copper mining | Falador |
| Ultraz | Catherby net fishing | Catherby |
| Vinny | East Varrock copper mining | Varrock |
| Six Seven | East Varrock mining; copper/tin until Mining 15, then iron | Varrock |
| College | East Varrock tin mining | Varrock |
| Pknskate | Draynor shrimp fishing | Draynor |
| P H I S H | Draynor shrimp fishing | Draynor |
| Fulani | Lumbridge woodcutting | Draynor |
| Az | Lumbridge woodcutting | Draynor |

The roster contains appearances, public task coordinates, routes, tools, and
activity settings only. Never add passwords, password hashes, emails, or recovery
data to it.

All profiles open banks with Banker command 1. The active world config must set
`right_click_bank: true`, `want_fatigue: false`, and `want_bank_notes: true`;
local and systemd supervisors fail before startup when that runtime/wire contract
is absent. Preservation/default presets differ, so do not start this fleet against
those presets without an explicit active-config override.

## Native lifecycle

Provisioning uses the server's ordinary packet-registration path and leaves every
account at the normal player group. The stock one-time new-player announcement is
part of that account's first real login session: server player registration sees
`lastLogin == 0` and announces the join before or around the appearance flow, not
only after appearance succeeds. In that same uninterrupted session, the controller
waits for the live appearance-screen packet, submits the configured appearance,
selects `Skip the intro`, selects `Forager's Path`, and begins walking from Lumbridge.
Provisioning therefore never performs a credential-proof login ahead of the fleet;
doing so would consume the announcement and split the approved onboarding flow.

That announcement and Void Island flow happen once per account, not on every server
restart. Later sessions load the account's saved position, skills, inventory, bank,
and social state like any other player.

### Disposable first-login demonstration

`scripts/headless-demo.sh` provides a one-shot local world for observing all ten
first logins without resetting or stopping another world. `prepare` verifies the
pinned pre-fleet SQLite snapshot, creates only `headless_demo.db`, provisions the
receipt-bound accounts without logging them in, disables packet registration and
all Discord integrations, and restarts the generated config on loopback TCP `43606`
and WebSocket `43506`. Fleet control ports are isolated at `19020..19029`; the
normal local world on `43596` and the Arena QA world on `43597` are treated as
protected listeners.

```bash
scripts/headless-demo.sh prepare

# Start a separate observer client against 127.0.0.1:43606 and log wbtest in.
scripts/headless-demo.sh fleet-start --stagger 3
```

`fleet-start` refuses unless `wbtest` is online and every fleet account is offline
with `login_date = 0` and neither form of the new-player announcement marker. It
then records a one-shot marker before launching slots `0..9`, so the default delay
produces arrivals at approximately `0, 3, ..., 27` seconds. Use
`scripts/headless-demo.sh reset` to stop the isolated processes and reconstruct a
fresh replay; reconnect the observer after that server restart. `stop` preserves
the disposable state, while `destroy` removes only the generated demo config,
database, logs, and PID state. Do not open or modify the pinned source snapshot:
the wrapper rejects a changed checksum or SQLite `-wal`/`-shm` sidecar.

Trade and duel requests, friend additions, following, private messages, and public
visibility all use the ordinary player handlers. The controller never sends chat,
never reciprocates a trade or duel request, and never changes the default privacy
settings. A requester therefore receives the normal request confirmation while the
unattended account simply does not answer.

## Credential and provisioning rules

Passwords live outside the repository in an owner-only directory. Each password is
a separate mode-0600 file and is passed to `voidbotd` by file path, never as a
process argument. A mode-0600 provisioning receipt binds the exact roster identity
to a fingerprint of that credential. An existing username without a matching
receipt is never adopted or reset.

For local development:

```bash
HEADLESS_CREDENTIAL_DIR="$HOME/.config/voidscape/headless-players" \
  scripts/provision-headless-players.sh
```

The active server must deliberately allow packet registration during this one-time,
loopback-only operation. For the temporary maintenance window, set
`want_packet_register: true`. If `want_registration_limit: true` and
`is_localhost_restricted: true`, temporarily set `want_registration_limit: false`
(preferred) while keeping the registration endpoint loopback-only. Restart the
server, run provisioning, restore the original settings, and restart again. The
provisioning script checks the selected active config but never edits it. Registration
is staggered to remain below the login filter. Provisioning is intentionally
SQLite-only: before it creates a password, adopts a receipt, or sends any register
packet, the script requires the active `db_type` to be `sqlite`, opens the selected
database read-only, validates the `players` credential columns, and checks the
canonical password helper. MariaDB/MySQL, an unavailable database, or an unavailable
helper aborts the entire run without a login fallback. Do not bypass this gate; a
non-SQLite deployment needs a separately reviewed account-migration plan.

If a registration succeeds but the process stops before writing its receipt, the
next run checks the preserved credential offline against the selected SQLite
database. Recovery requires exactly one canonical username row in `players`,
requires `online = 0`, and sends the file password plus that row's `salt` and `pass`
to `PortalPasswordHasher` over stdin. The raw password is never placed in argv. A
matching row proves an interrupted successful registration; a valid receipt whose
row is missing can re-register with its preserved credential, while an online,
duplicate, or password-mismatched row fails closed. Existing receipts are also
rechecked against the active database, so a stale receipt cannot silently adopt an
account after a database replacement.

Do not grant staff groups, write `void_path` cache values, or pre-complete appearance;
all ten accounts must take the real first-login path.

On a deployed host the credential directory is
`/etc/voidscape/headless-players`. The systemd template consumes each password with
`LoadCredential=`; the service user does not need access to the source directory.
The launch-staging bundle carries the non-secret scripts, voidbot runtime, roster,
server definitions, and three systemd units at their `/opt/voidscape`-relative
paths. Install the units into `/etc/systemd/system`, run `systemctl daemon-reload`,
and provision the external credential files before starting the target.

The ten `User=voidscape-headless-<profile>` identities in the template are static
system accounts, not `DynamicUser=` identities. Before enabling the target, create
the exact `fireee`, `ch0p`, `ultraz`, `vinny`, `six-seven`, `college`, `pknskate`,
`p-h-i-s-h`, `fulani`, and `az` users with primary group `voidscape`, no home, and
the nologin shell. Existing accounts are not accepted by name alone: verify each
passwd entry has the exact name, a UID greater than zero and below `/etc/login.defs`
`UID_MIN`, the `voidscape` primary GID, `/nonexistent` as its home, a nologin shell,
and no group membership except `voidscape`. The copy-ready creation and validation
loop is in `docs/OPERATIONS.md`. Keep the existing `voidscape` service account for
the controller. Both the per-player template and controller unit must retain
`LimitCORE=0`; a local override that permits core dumps is a failed secret-handling
prerequisite, because a daemon crash could otherwise persist credential-bearing
memory.

The fleet target deliberately does not start a game-server unit. Its defaults use
`/opt/voidscape/server/local.conf`, TCP `43596`, client version `10137`, and the
matching definitions tree. For an already-running isolated world, place only
non-secret overrides in `/etc/voidscape/headless.env`, for example:

```ini
HEADLESS_SERVER_CONFIG=/etc/voidscape/headless-runtime.conf
HEADLESS_GAME_PORT=43696
HEADLESS_DEFS_DIR=/opt/voidscape/headless-defs
VOIDBOT_BASED_CONFIG_DATA=85
VOIDBOT_CLIENT_VERSION=10132
```

Root must first run `check-fleet-runtime-config` against the real selected-world
config. It must then create `/etc/voidscape/headless-runtime.conf` as
root:`voidscape`, mode 0640, containing only `right_click_bank: true`,
`want_fatigue: false`, and `want_bank_notes: true`, and validate that minimal file
too. It is a least-privilege mirror of three already-verified values, not the config
used for provisioning. Point the systemd environment at this minimal file; never
grant static player users traversal through a private staging tree. Keep
the `/etc/voidscape` parent root:`voidscape` mode 0710 so the group can traverse the
known runtime path without listing the directory; keep `headless-players/` root:root
0700 and unrelated secret files root:root 0600. Keep `/etc/voidscape/headless.env`
root-owned mode 0644 or stricter. Start and health-check
the selected game-server service independently before enabling the fleet; the
version override must exactly match that active world's `client_version`. If the
alternate world's definitions tree is not traversable by group `voidscape`, copy only
`GameObjectDef.xml`, `ItemDefs.json`, and `ItemDefsCustom.json` into a root-owned,
group-`voidscape`, mode-0750 `HEADLESS_DEFS_DIR`; make the files mode 0640 and set
`VOIDBOT_BASED_CONFIG_DATA` to the selected world's exact value. Do not grant the
player service users access to a private server tree merely to read definitions.

Provisioning a deployed world must name every selected-world input explicitly. For
the staging example that means the real
`/opt/voidscape-staging/server/local.conf`, its `connections.conf`, its exact SQLite
database and password-helper JAR, credential directory, literal loopback host, TCP
43696, and client version 10132. Do not substitute the minimal runtime contract for
the real config here and do not rely on repository or production defaults. The full
copy-ready command is in `docs/OPERATIONS.md`.

Treat `/etc/voidscape/headless-players` and the active account database as one
recovery set. The directory must be root-owned mode 0700 and every password and
receipt must be mode 0600. Stream it directly into an approved encrypted off-host
backup; never create a plaintext tarball, add it to a launch bundle, or paste its
contents into logs or tickets. Restore only while the fleet target is stopped,
reapply owner/modes, restore the matching database snapshot, and rerun provisioning
preflight before starting any session. Every new snapshot requires
`set -euo pipefail`, a stopped fleet target, and a successful
`check-sqlite-roster-offline` against the explicitly selected SQLite database before
the SQLite backup is created. Decrypt and list the encrypted credential archive as a
verification step: it must contain exactly 20 members, one `.password` and one
`.provisioned` file for each of the ten roster IDs, with no extras. Never print file
contents. A legacy snapshot with stale `online = 1` rows may be reconciled only by
keeping the fleet disabled, starting the game server alone to perform its normal
flag cleanup, stopping it, and passing the offline gate before verification or fleet
startup; never patch those flags directly with SQL. Copy-ready backup and restore
steps are in `docs/OPERATIONS.md`.

## Runtime architecture

`scripts/run-headless-player.sh` runs one foreground game session. Roster slot `N`
uses loopback control port `19020 + N`. `scripts/run-headless-controller.sh` runs the
single controller against those ten ports. Production supervision is provided by:

- `voidscape-headless@.service` — one isolated real account session.
- `voidscape-headless-controller.service` — activity controller only; no secrets.
- `voidscape-headless.target` — starts and stops the complete fleet.

The controller is restart-safe because it derives work from live game state. It
recognizes only the exact appearance, Void welcome, and Forager menus; it never
answers an arbitrary menu. Gathering uses ordinary object actions and XP/inventory
updates. Banking uses the ordinary banker and bank packets, deposits carried items,
then withdraws the best available configured tool. After a success, the just-used node
is excluded from the next selection and a persistent gate first sends an ordinary
one-tile walk to cancel the repeating gather batch. The controller then requires a
fresh route response for a physically distinct approach and an actual coordinate
change before it can issue another object action. Busy, missing, or stalled routes
retry in bounded packet bursts; backoff never releases the gate. A crowded or cooling
alternate may be approached, but gathering waits until it is eligible. Woodcutting,
Mining, and Fishing recheck the same interruption immediately after their opening
delay and before awarding output or XP. Failed attempts retain bounded cooldowns
without advancing successful-node rotation.

Ch0p alone uses Falador bank as a live-state-derived staging stop when the fresh
inventory is bulky or a pickaxe is missing, then continues south to the Rimmington
copper rocks. Its ordinary gather-bank loop returns to Falador. Once Ch0p is at the
mine or already on that loop, a controller restart does not redirect a completed
load through onboarding travel again.

Ultraz does not use Falador or White Wolf Mountain. The fresh account sells its
starter bronze sword at the Lumbridge general store to secure both 30-coin fares,
walks to Port Sarim, and sails to Karamja. Before the crossing it equips the starter
wooden shield, retains the cooked meat, and gathers ten bananas with the ordinary
banana-tree action. It departs the safe stage only at full hits with the required
supplies, evaluates hostile NPC distance against each next segment, and traverses
the narrow hazard corridor one cardinal tile at a time. Pre- and post-corridor safe
stages allow bounded eating, proof of the banana batches, and retreat/retry without
invented items or teleportation. The Customs Officer route consumes the second fare
to Ardougne, after which Ultraz walks to Catherby and begins the normal fishing-bank
loop. This protected Karamja route is restart-derived from live position, inventory,
equipment, hits, and nearby NPCs.

Opening a bank is acknowledgement-bounded. The controller spaces Banker command-1
attempts by at least four seconds and allows at most four silent attempts without an
`OPEN_BANK` state update. It then resets the attempt counter and applies a 30-second
route cooldown instead of looping forever; an actual bank-open packet immediately
clears the counters. Rejected control requests use the same four-attempt/30-second
bound. Deposit sweeps are bounded separately before the controller closes a stalled
bank. Hazard profiles monitor hitpoints, eat ordinary carried food, retreat when
necessary, and retry from saved state after death. A missing tool routes back to the
staging or task bank instead of dropping or inventing an item.

## Operations and failure behavior

Expected properties:

- A failed game socket makes only that `voidbotd` exit nonzero; supervision restarts
  it with the normal slot delay.
- SIGTERM, SIGINT, and intentional shutdown request normal logout before closing.
- A stopped controller leaves real logged-in players idle; normal server idle rules
  eventually log them out if supervision is not restored.
- Accounts earn and save real XP and resources and count as real online players.
  They may therefore appear in database-backed rankings and activity exports.
- The accounts receive whatever normal first-account entitlements are active when
  they register. The controller never visits a reward vendor or transfers items.

Inspect service state and logs with `systemctl status` and `journalctl` rather than
reading credentials or attaching a graphical client to a fleet account.

## Acceptance checks

Use voidbot for all game interaction. With a separate observer account online:

1. Start one fresh fleet account and observe exactly one join announcement during
   that first login session, before or around its appearance flow.
2. In the same session, confirm appearance creation, Skip, Forager, Lumbridge arrival,
   and walking.
3. From nearby, send trade and duel requests and match the stock
   `Sending ... request` messages; the target must not open or complete either.
4. Add the account as a friend and wait for `friend-present`; follow it and match
   the stock `Following <name>` message while it moves.
5. Complete at least one gather-to-bank-to-task cycle for every profile.
6. Stop one daemon, the controller, and the game server independently and verify
   isolated recovery, persisted positions, and no repeated join announcement.
7. Confirm no credential appears in process arguments, logs, repository files, or
   controller state.

Command details and state/wait contracts are in `docs/bot-api.md`.
