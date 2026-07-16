# July 18 launch readiness

Living execution record for the planned Voidscape public opening on
**2026-07-18 at 18:00 UTC**. The reusable requirements remain in
`docs/RELEASE-CHECKLIST.md`; this file records the status, decisions, owners, and
evidence for this specific launch.

Update this document whenever a gate changes. A checkbox is complete only when its
evidence is named here. Do not put player names, email addresses, credentials, secrets,
database contents, signing material, or private source/history in this file.

## Current decision

- **Verdict:** NO-GO as of 2026-07-16; re-audit after every P0 and P1 gate is closed.
- **Private branch:** `codex/release-10139-integration`
- **Last completed private commit:** `b8ea420f120a634aca06384a02420247fce0fda7`
- **Sanitized public branch:** `codex/launch-rc-public-clean`
- **Current local public commit:** `271581884add7bb434b715c598de281c631dba62`
- **Current public `main`:** `57056c6147c105039f3e7b39c33f264cfd776247`
- **Protocol/client version:** `10139`
- **Play candidate:** versionCode `11`, versionName `1.0.10`, held by Managed
  Publishing and not publicly rolled out
- **Play candidate local SHA-256:**
  `961b2148bb88300720084fb50c8a8ea3c10d5a739c75d4efc37edaa28cacb62b`
- **Release authority:** build/package work is authorized; production containment,
  deployment, public-source push, channel promotion, and Play publication each require
  separate explicit authorization.

## Status words

- `PASS` — requirement met and evidence recorded.
- `IN PROGRESS` — actively being worked; not releasable.
- `BLOCKED` — launch cannot proceed until closed.
- `HUMAN` — requires physical-device testing or owner/operator judgment.
- `PENDING` — not started or not yet proven against the final candidate.
- `N/A` — intentionally excluded, with the decision recorded.

## Launch decisions

| Date | Decision | Reason |
|---|---|---|
| 2026-07-16 | Preserve the complete eligible pre-signed-player cohort and grant exactly one free starter subscription card entitlement per eligible account. | The owner reports 190 eligible pre-signups so far; this is a protected launch obligation, not test data. |
| 2026-07-16 | Treat 190 as a baseline count, not the final freeze count. | More eligible signups may arrive before the cutover. The freeze-time snapshot is authoritative. |
| 2026-07-16 | Keep the optional ten-player headless fleet disabled and outside the launch critical path. | It adds accounts, credentials, persistence, and recovery complexity without being needed to open. |
| 2026-07-16 | Keep the already-reviewed Play v11 AAB if its uploaded bytes and Android-input provenance are proven; do not rebuild it merely for server/portal-only changes. | A needless rebuild consumes a new versionCode and review cycle without changing Android behavior. |
| 2026-07-16 | Verify the source corresponding to the already-live portal/downloads now, then publish the exact final sanitized Corresponding Source at or before the final public network/distribution cutover—not after declaring the launch successful. | The pre-signup portal already has remote users; an unopened game world is not a general AGPL grace period. Operational policy pending any contrary advice from qualified counsel. |
| 2026-07-16 | Never push public source with `--all` or `--mirror` from the shared private repository. | The current public worktree shares the private Git object database; an overly broad push could expose private refs/history. |

## Protected pre-signed-player cohort

Owner-provided baseline on 2026-07-16: **190 eligible pre-signed players**. This count
has not yet been independently reconciled against the production portal/game stores.

In the current implementation, the eligibility roster is the `founders[]` collection
in the production portal store (`$PORTAL_DATA_DIR/dev-store.json`). It is not the game
database and it must not be inferred from the broader `accounts[]` or game-player
tables. The game database separately stores whether a reward is available or claimed.
Those two data stores must therefore be frozen, backed up, restored, and reconciled as
one matched pair.

The final cohort may be larger. At the roster freeze, create one private operational
snapshot containing the owner-approved eligible `founders[]` records and the matching
portal-account/game-reward relationships. Store the protected snapshot outside Git and
record only aggregate counts, its timestamp, schema/version, and SHA-256 here.

There are currently three reward mechanisms and they must not be confused:

1. An account-bound starter-card marker for a founder already linked to a portal
   account. This is the preferred base-reward route.
2. A legacy one-time signup code for an eligible founder who is not account-linked at
   freeze time. This is a bearer credential and must remain private.
3. A separate native-launch reward. This is not part of the 190-player pre-signup
   promise and must be counted separately.

Every eligible founder must have exactly one base-reward route: account marker or
legacy code, never neither and never both. Referral and native-launch rewards are
separate bonuses and do not satisfy or duplicate that base promise.

- [x] Name the implementation's authoritative source for cohort eligibility:
  production portal `founders[]`; have the owner approve its freeze-time contents.
- [ ] Name the authoritative account identifier used to join portal and game records.
- [ ] Reconcile the current baseline count of 190 without exposing identities.
- [ ] Define the final eligibility cutoff time and whether an incomplete email
  verification is eligible.
- [ ] Freeze a final cohort snapshot and record its count and SHA-256 below.
- [ ] Exclude QA, headless, disposable, banned, and test accounts unless the owner
  explicitly approves an exception.
- [ ] Preserve every eligible portal/game identity through the launch reset.
- [ ] Give every eligible founder exactly one available base-reward route: an
  account-bound starter-card marker or a private legacy signup code.
- [ ] Give no ineligible account a starter-card marker.
- [ ] Prove there are no founders with zero or multiple base-reward routes.
- [ ] Count referral and native-launch rewards separately from the founder base reward.
- [ ] Prove every retained ordinary player has ordinary rank unless separately
  approved as staff.
- [ ] Prove the reset removes unintended progress/economy state without removing the
  eligible identities or their entitlement markers.
- [ ] Prove a successful vendor claim changes the marker from available to claimed and
  mints exactly one physical card.
- [ ] Prove a full inventory leaves the marker available and mints no card.
- [ ] Prove a second claim cannot mint a second free card.
- [ ] Prove another character linked to the same portal account sees the same
  account-wide entitlement/subscription state.
- [ ] Stop portal/game writes, then back up and restore-test the production portal JSON
  store and game database as one coordinated, hash-matched set.
- [ ] Make production portal startup fail closed if an existing store is missing or
  malformed; never silently replace a damaged production roster with an empty store.

Freeze evidence:

| Field | Value |
|---|---|
| Eligibility cutoff | `PENDING` |
| Baseline count | `190` (owner-provided, 2026-07-16) |
| Final frozen count | `PENDING` |
| Snapshot timestamp | `PENDING` |
| Snapshot schema/version | `PENDING` |
| Snapshot SHA-256 | `PENDING` |
| Eligible `founders[]` count | Must equal final frozen count (currently expected baseline `190`) |
| Unique canonical email count | Must equal eligible founder count |
| Unique normalized founder-name count | Must equal eligible founder count |
| Account-bound base rewards | `PENDING` |
| Legacy-code base rewards | `PENDING` |
| Account-bound + legacy-code base rewards | Must equal eligible founder count |
| Founders with zero base-reward routes | Must be `0` |
| Founders with multiple base-reward routes | Must be `0` |
| Ineligible entitlement count | Must be `0` |
| Approved referral reward codes | `PENDING` (reported separately) |
| Approved native-launch rewards | `PENDING` (reported separately) |
| Restore rehearsal evidence | `PENDING` |

## P0 — immediate stop conditions

| Gate | Owner | Status | Completion evidence |
|---|---|---|---|
| Contain public portal access to runtime/source/schema/support files on all three origins. | Production operator | BLOCKED | Status-only probes must show forbidden and encoded paths `404` while allowed public routes remain healthy. Preserve access logs; do not retrieve bodies unnecessarily. |
| Complete VS-089 as a committed, fail-closed application/package/Nginx boundary. | Codex | IN PROGRESS | `docs/BUGS.md` VS-089; focused static/API/schema tests currently pass, package test currently fails because the contract is untracked. |
| Prevent every existing pre-VS-089 portal bundle from being deployed. | Release owner | BLOCKED | Retire/quarantine record naming the superseded bundle paths and hashes. |

## P1 — required before opening

| Workstream | Owner | Status | Completion evidence |
|---|---|---|---|
| Clean source and canonical build | Codex | IN PROGRESS | Clean final commit; `scripts/build.sh` exit `0`; VS-089 focused tests and packaged-runtime test pass. |
| Final v12 server/client release bundle | Codex | PENDING | Manifest names exact final commit, `source_dirty=false`, exact hashes, protocol `10139`, corrected launch contract, and no blockers. |
| Bundle hygiene | Codex | PENDING | No personal absolute paths, credentials/keys, backups/archives, `.gitsave`, runtime state, private history, or unexpected files; populated `portal.env` install mode is `0600`. |
| Correct launch profile and custom content | Codex | PENDING | Complete launch-contract verification; archived boot log shows expected custom region/Enclave/Rift/Auction counts. |
| Public portal boundary | Codex + operator | BLOCKED | Exact packaged tree; Nginx include installed; all-origin forbidden-path probes `404`; hosted allowed paths and ranges pass. |
| Portal account flows | Codex + human | PENDING | Signup, rules acceptance, verification, login, recovery, character create/delete, logout, data-deletion link, and email delivery pass against final candidate. |
| Pre-signed-player cohort | Owner + Codex + operator | BLOCKED | Every protected-cohort checkbox and count/hash reconciliation above passes. |
| Reward/reset safety | Codex + owner | BLOCKED | Replace the current broad reset behavior with an owner-approved frozen roster; prove one base route per eligible founder, preserve claimed state where intended, and add focused automated tests. |
| Database choice and permissions | Owner + operator | PENDING | Explicit SQLite/MariaDB decision; integrity/foreign keys, permissions, capacity, and exactly-one-writer proof. |
| Coordinated backup and rollback | Operator + owner | PENDING | Portal/game writes quiesced; paired hashed off-host backup restored successfully in isolation; objective rollback triggers recorded. |
| Desktop and launcher | Human + Codex | PENDING | Exact candidate fresh install, update, repair, offline behavior, login, gameplay, save, logout, and relogin. |
| Hosted web client | Codex + human | PENDING | Exact `10139` manifest, HTTPS/WSS, cache policy, deep manifest, smoke, reconnect, and no stale `10132` assets. |
| Physical Android | Human | HUMAN | Validator-passing report names the exact public APK SHA-256 and covers signup, login, gameplay, background/resume, recovery, report/block, and data deletion. |
| Physical iPhone/Home Screen | Human | HUMAN | Report covers Safari/Home Screen, signup/login, keyboard, orientation, panels, chat, bank/map, WSS reconnect, and background/resume. |
| Play AAB identity | Human + Codex | BLOCKED | Google-provided original file SHA-256 exactly matches the held v11 candidate; version/signing/client/SDK metadata pass. Keep Managed Publishing on. |
| Current live-source correspondence | Codex + owner | BLOCKED | The public source link/revision is proven to correspond to the portal service and covered clients already live or downloadable; correct any mismatch without waiting for game-server opening. |
| Exact sanitized public source | Codex + owner | BLOCKED | Standalone sanitized clone; full reachable-history hygiene scan; exact final source commit publicly reachable and linked at/before opening. |
| Production services | Operator | PENDING | Actual Nginx/systemd units, service identities, permissions, restart/backoff, native backfill and integrity-export timers, and exactly one game JVM audited. |
| Monitoring and ownership | Owner + operator | PENDING | External portal/TCP/WSS/health checks, signup-email signal, crash/restart, disk, backup-age, integrity, latency alerts, named operator and rollback authority. |
| Final closed-ingress rehearsal | Operator + Codex | PENDING | Exact staged hashes; server/portal boot behind closed ingress; all verifiers, voidbot smoke, account flows, source link, logs, and latency pass before opening. |

## Explicitly outside the critical path

- Optional headless-player fleet startup/provisioning.
- A last-minute MariaDB migration if a rehearsed, explicitly accepted SQLite launch
  satisfies the initial load and safety gates.
- Rebuilding the Play AAB solely because final server/portal source changed.
- General P2 documentation or architecture cleanup that does not affect release safety.

## AGPL/source publication gate

Operational policy, not legal advice:

- The existing GitHub repository may remain the source host, but its linked revision
  must correspond to the covered portal service and clients that are already live or
  downloadable. The owner-reported pre-signup cohort means the portal already has
  remote users; verify present-day source correspondence now rather than treating the
  unopened game world as a grace period.
- Final release changes may remain private while they are only being built or staged
  behind closed ingress and are neither conveyed nor exposed to external users.
- The exact Corresponding Source for the final network service must be available when
  public users are allowed to interact with that modified service.
- The exact Corresponding Source for a distributed client must be available when that
  client is conveyed/distributed under the applicable source-offer method.
- Therefore, stage and verify the sanitized final source privately first, then publish
  it immediately before or as part of the explicitly authorized public cutover. Do not
  wait until after deciding the launch was successful.
- If the deployment rolls back, leaving the newer source public is acceptable from an
  operational secrecy perspective because it was already approved as sanitized; update
  the portal's linked deployed revision if necessary.

Counsel follow-up:

- [ ] Confirm the final sanitized tree contains complete Corresponding Source for the
  deployed service and every distributed client.
- [ ] Confirm the network-interaction source link/method satisfies AGPLv3 section 13.
- [ ] Confirm the chosen source-delivery method for Play and direct APK distribution.
- [ ] Confirm third-party assets/dependencies and build/install information are covered.

## Evidence ledger

Record durable summaries or paths only. Evidence under `tmp/` is local and ephemeral;
do not commit secrets or player data.

| Date | Gate | Result | Commit/artifact | Evidence |
|---|---|---|---|---|
| 2026-07-16 | VS-088 launch profile | PASS in isolated rehearsal | `b8ea420f` | `docs/BUGS.md` VS-088; final bundle regeneration still required. |
| 2026-07-16 | VS-089 focused static boundary | PASS | dirty working tree | `scripts/test-portal-static-boundary.sh` |
| 2026-07-16 | VS-089 portal API/schema | PASS | dirty working tree | `scripts/test-portal-api.sh`; `scripts/test-portal-schema.sh` |
| 2026-07-16 | VS-089 package test | FAIL | dirty working tree | Packager correctly refuses untracked `web/portal/public-static-contract.json`. |
| 2026-07-16 | Existing held portal boundary | FAIL | held bundles from `57056c61` | Forbidden paths are packaged/served; status-only production probes returned `200` on all three origins. |
| 2026-07-16 | Play status | HELD, NOT PUBLIC | v11 / `1.0.10` / client `10139` | Console: `Managed publishing on`, `Changes ready to publish`, `Publish 1 change`. |
| 2026-07-16 | Play AAB local identity | PARTIAL | SHA-256 `961b2148bb88300720084fb50c8a8ea3c10d5a739c75d4efc37edaa28cacb62b` | Local sidecar/signature/metadata pass; Play-hosted byte identity pending. |
| 2026-07-16 | Protected pre-signup cohort | OWNER BASELINE ONLY | count `190` | Production count/schema/hash reconciliation pending; identities must not be recorded here. |

## Go/no-go authorization

Do not complete this section until every P0 and P1 row is `PASS` or an explicitly
documented, owner-approved `N/A` that does not violate license, security, data safety,
or physical-device requirements.

| Approval | Name | UTC time | Decision/evidence |
|---|---|---|---|
| Release engineering | `PENDING` | `PENDING` | `PENDING` |
| Database/cohort owner | `PENDING` | `PENDING` | `PENDING` |
| Physical-device QA | `PENDING` | `PENDING` | `PENDING` |
| Production operator | `PENDING` | `PENDING` | `PENDING` |
| Public-source/legal owner | `PENDING` | `PENDING` | `PENDING` |
| Server-opening authorization | `PENDING` | `PENDING` | `PENDING` |
| Play-publication authorization | `PENDING` | `PENDING` | Separate decision after the server is verified live. |

## Change log

- 2026-07-16: created from the independent release-readiness audit; recorded the
  owner-provided 190-player protected-cohort baseline and the current VS-089, Play,
  source, device, database, rollback, and observability gates.
- 2026-07-16: identified production portal `founders[]` as the current eligibility
  roster, made portal JSON plus game DB a paired backup unit, separated the three
  reward routes, and added exact zero/double-reward reconciliation gates.
