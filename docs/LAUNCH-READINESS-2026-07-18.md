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
- **Latest verified release fix:** `6d70d5ee` (VS-089 portal static boundary)
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
| 2026-07-16 | Preserve the complete eligible pre-signed-player cohort and grant exactly one free starter subscription card entitlement to every character under each qualifying account. | The owner reports 190 eligible pre-signups so far. The benefit is per character: a qualifying account with ten characters must receive ten cards, one on each character. |
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

The roster decides which parent accounts qualify; it does **not** determine the final
number of cards. The card count is the number of eligible characters under those
qualifying accounts. A qualifying account with ten characters is owed ten cards, with
one independently claimable card marker on each character.

There are currently three reward mechanisms and they must not be confused:

1. An account-bound starter-card marker for a founder already linked to a portal
   account. The current implementation permits only one claim for the whole account,
   so it does not yet satisfy the per-character promise.
2. A legacy one-time signup code for an eligible founder who is not account-linked at
   freeze time. This is a bearer credential, currently grants only one card, and must
   remain private.
3. A separate native-launch reward. This is not part of the 190-player pre-signup
   promise and must be counted separately.

Every eligible founder must have exactly one qualification route: linked account or a
controlled legacy-account claim, never neither and never both. After qualification,
every eligible character under that account must have exactly one starter-card marker,
never zero and never more than one. Referral and native-launch rewards are separate
bonuses and do not satisfy or duplicate that per-character promise.

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
- [ ] Give every eligible founder exactly one account-qualification route: a linked
  account or a controlled private legacy claim.
- [ ] Give every eligible character under every qualifying account exactly one
  available starter-card marker.
- [ ] Give no character under an ineligible account a starter-card marker.
- [ ] Prove there are no eligible characters with zero or multiple starter-card
  markers.
- [ ] Count referral and native-launch rewards separately from the founder base reward.
- [ ] Prove every retained ordinary player has ordinary rank unless separately
  approved as staff.
- [ ] Prove the reset removes unintended progress/economy state without removing the
  eligible identities or their entitlement markers.
- [ ] Prove a successful vendor claim changes the marker from available to claimed and
  mints exactly one physical card.
- [ ] Prove a full inventory leaves the marker available and mints no card.
- [ ] Prove a second claim by the same character cannot mint a second free card.
- [ ] Prove every other character linked to the same qualifying account has its own
  independent card marker and can claim exactly once.
- [ ] Decide and test how characters created after the roster freeze qualify. If new
  characters qualify, prevent delete/recreate churn from becoming an unlimited-card
  generator.
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
| Linked-account qualification routes | `PENDING` |
| Controlled legacy qualification routes | `PENDING` |
| Linked + legacy qualification routes | Must equal eligible founder count |
| Eligible character count | `PENDING` (this, not `190`, determines cards owed) |
| Available + claimed per-character starter markers | Must equal eligible character count |
| Eligible characters with zero starter markers | Must be `0` |
| Eligible characters with multiple starter markers | Must be `0` |
| Ineligible entitlement count | Must be `0` |
| Approved referral reward codes | `PENDING` (reported separately) |
| Approved native-launch rewards | `PENDING` (reported separately) |
| Restore rehearsal evidence | `PENDING` |

## P0 — immediate stop conditions

| Gate | Owner | Status | Completion evidence |
|---|---|---|---|
| Contain public portal access to runtime/source/schema/support files on all three origins. | Production operator | BLOCKED | Status-only probes must show forbidden and encoded paths `404` while allowed public routes remain healthy. Preserve access logs; do not retrieve bodies unnecessarily. |
| Complete VS-089 as a committed, fail-closed application/package/Nginx boundary. | Codex | PASS | Commit `6d70d5ee`; `docs/BUGS.md` VS-089; packaged-runtime, exact-tree, all-origin, portal API/schema, launch-config, and full-build checks pass. |
| Prevent every existing pre-VS-089 portal bundle from being deployed. | Release owner | BLOCKED | Retire/quarantine record naming the superseded bundle paths and hashes. |

## P1 — required before opening

| Workstream | Owner | Status | Completion evidence |
|---|---|---|---|
| Clean source and canonical build | Codex | IN PROGRESS | Private source is clean at `6d70d5ee` and `scripts/build.sh` exits `0`; repeat against the final post-reward-fix commit and sanitized public tree. |
| Final v12 server/client release bundle | Codex | PENDING | Manifest names exact final commit, `source_dirty=false`, exact hashes, protocol `10139`, corrected launch contract, and no blockers. |
| Bundle hygiene | Codex | PENDING | No personal absolute paths, credentials/keys, backups/archives, `.gitsave`, runtime state, private history, or unexpected files; populated `portal.env` install mode is `0600`. |
| Correct launch profile and custom content | Codex | PENDING | Complete launch-contract verification; archived boot log shows expected custom region/Enclave/Rift/Auction counts. |
| Public portal boundary | Codex + operator | BLOCKED | Exact packaged tree; Nginx include installed; all-origin forbidden-path probes `404`; hosted allowed paths and ranges pass. |
| Portal account flows | Codex + human | PENDING | Signup, rules acceptance, verification, login, recovery, character create/delete, logout, data-deletion link, and email delivery pass against final candidate. |
| Pre-signed-player cohort | Owner + Codex + operator | BLOCKED | Every protected-cohort checkbox and count/hash reconciliation above passes. |
| Reward/reset safety | Codex + owner | BLOCKED | Replace the current account-wide/code-only grant behavior and broad reset with an owner-approved frozen roster and one marker per eligible character; prove ten characters receive ten independent cards, preserve claimed state where intended, and add focused automated tests. |
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
| 2026-07-16 | VS-089 focused static boundary | PASS | `6d70d5ee` | Minimized packaged runtime; allowed/forbidden/range matrix; three independent local origins. |
| 2026-07-16 | VS-089 portal API/schema | PASS | `6d70d5ee` | `scripts/test-portal-api.sh`; `scripts/test-portal-schema.sh` |
| 2026-07-16 | VS-089 package test | PASS | `6d70d5ee` | Whole packaged tree exactly equals the contract; no symlink or non-regular entry. |
| 2026-07-16 | Canonical build after VS-089 | PASS | `6d70d5ee` | `scripts/build.sh` exit `0`; JDK 25 compatibility warnings only. |
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
- 2026-07-16: owner clarified that the promised free card is per character, not per
  founder or parent account. A qualifying ten-character account is owed ten cards;
  the current account-global marker and single-use legacy code do not yet meet this
  contract.
- 2026-07-16: committed and fully verified the local VS-089 fail-closed portal
  application/package/Nginx boundary at `6d70d5ee`. The live production origins and
  every superseded bundle remain blocked until an operator performs an explicitly
  authorized containment/deployment and the hosted probes pass.
