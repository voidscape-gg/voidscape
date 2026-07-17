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
- **Immediate portal exposure:** CONTAINED by VS-092; unrelated launch blockers below
  keep the overall verdict at NO-GO.
- **Private branch:** `codex/release-10139-integration`
- **Latest verified release fix:** `f14cf3c6` (show all three clients after account creation)
- **Sanitized public branch:** `codex/launch-rc-public-clean`
- **Current local public commit:** `271581884add7bb434b715c598de281c631dba62`
- **Current public `main`:** `57056c6147c105039f3e7b39c33f264cfd776247`
- **Protocol/client version:** `10139`
- **Play candidate:** versionCode `12`, versionName `1.0.11`, client `10139`, in
  Google review for a 100% Production rollout; Managed Publishing is off so approval
  publishes it automatically
- **Play candidate local SHA-256:**
  `ef48b10c28b25a3581aa8068d39c93d68196a10c38cb1592bc83614d925aef87`
- **Release authority:** the public client-channel deployment and Play publication were
  explicitly authorized and completed through Google's review boundary. Public-source
  publication and opening the game server remain separate actions.

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
| 2026-07-16 | Apply an emergency Nginx-only boundary to all three public origins without replacing the portal bundle or touching the game, database, clients, Play Store, or public source. | Existing pre-signup visitors should not be able to retrieve packaged runtime/schema/support files, while normal portal, web-play, and launcher-update routes must remain available. |
| 2026-07-16 | Preserve the complete eligible pre-signed-player cohort and grant exactly one free starter subscription card entitlement to every character under each qualifying account. | The owner reports 190 eligible pre-signups so far. The benefit is per character: a qualifying account with ten characters must receive ten cards, one on each character. |
| 2026-07-16 | Treat 190 as a baseline count, not the final freeze count. | More eligible signups may arrive before the cutover. The freeze-time snapshot is authoritative. |
| 2026-07-16 | Freeze the founder cohort and public reservations at launch, `2026-07-18T18:00:00Z`. | The founder promise is based on the launch-time snapshot. A password signup still awaiting email verification has not created an eligible linked founder character, while an already-completed code-only reservation keeps its controlled legacy route. |
| 2026-07-16 | Separately give every character created before `2026-07-19T18:00:00Z` one `launch_24h_card` promotion, deduplicated against any founder issuance. | New launch-day characters still receive a card without changing the immutable founder cohort or allowing two cards where both routes apply. |
| 2026-07-16 | Keep the optional ten-player headless fleet disabled and outside the launch critical path. | It adds accounts, credentials, persistence, and recovery complexity without being needed to open. |
| 2026-07-16 | Keep the already-reviewed Play v11 AAB if its uploaded bytes and Android-input provenance are proven; do not rebuild it merely for server/portal-only changes. | A needless rebuild consumes a new versionCode and review cycle without changing Android behavior. |
| 2026-07-16 | Supersede the held v11 Play candidate with v12 and discard the unpublished v11 release. | The v11 upload predated the final Android cache-packaging fix and was not byte-identical to the physically tested final Android candidate. |
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

1. A linked founder qualification freezes into one exact
   `starter:<webAccountId>:<playerId>` marker for each eligible character under that
   account. Each marker is independently available or claimed and cannot be recreated
   by deleting the character.
2. A legacy code-only founder reservation keeps one controlled base signup-code route.
   It grants one founder issuance, remains private, and cannot create an extra card when
   it is bound to a linked founder character.
3. A separate `launch_24h_card` promotion applies to every character created before
   `2026-07-19T18:00:00Z`. This is not part of the pre-signup cohort count and must be
   reported separately.

Every eligible founder must have exactly one qualification route: linked account or a
controlled legacy code-only claim, never neither and never both. Every eligible
character under a linked qualifying account must have exactly one founder composite
marker. Referral rewards remain independent bonuses. Launch-24h eligibility is counted
separately, but founder and launch routes on the same character yield at most one
physical card.

- [x] Name the implementation's authoritative source for cohort eligibility:
  production portal `founders[]`; have the owner approve its freeze-time contents.
- [ ] Name the authoritative account identifier used to join portal and game records.
- [ ] Reconcile the current baseline count of 190 without exposing identities.
- [x] Define the founder cutoff time and whether an incomplete email verification is
  eligible: cutoff `2026-07-18T18:00:00Z`; pending verification alone is not a linked
  founder-character qualification.
- [ ] Freeze a final cohort snapshot and record its count and SHA-256 below.
- [ ] Exclude QA, headless, disposable, banned, and test accounts unless the owner
  explicitly approves an exception.
- [ ] Preserve every eligible portal/game identity through the launch reset.
- [ ] Give every eligible founder exactly one account-qualification route: a linked
  account or a controlled private legacy claim.
- [ ] Give every eligible character under every qualifying linked account exactly one
  available founder composite marker.
- [ ] Give no character under an ineligible founder account a founder composite marker;
  launch-24h markers follow their separate cutoff instead.
- [ ] Prove there are no eligible founder characters with zero or multiple founder
  composite markers.
- [ ] Count referral and launch-24h rewards separately from the founder base reward, and
  prove founder/launch overlap yields only one physical card.
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
- [ ] Prove the agreed timing rule: the founder manifest freezes at launch; every
  character created before `2026-07-19T18:00:00Z` gets the separate launch promotion;
  characters created at or after that cutoff get no launch route and cannot expand the
  frozen founder manifest; overlap grants only one card.
- [ ] Stop portal/game writes, then back up and restore-test the production portal JSON
  store and game database as one coordinated, hash-matched set.
- [x] Make the final portal candidate fail closed if its protected store is missing,
  malformed, partial, unreadable, symlinked, non-`0700` directory, or non-`0600` file;
  runtime damage returns `503`, and only the explicit absent-only initializer can
  create a new empty store.

Freeze evidence:

| Field | Value |
|---|---|
| Founder cohort/reservation cutoff | `2026-07-18T18:00:00Z` (launch) |
| Separate `launch_24h_card` cutoff | `2026-07-19T18:00:00Z` (24 hours after launch) |
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
| Available + claimed founder composite markers | Must equal eligible founder-character count |
| Eligible founder characters with zero composite markers | Must be `0` |
| Eligible founder characters with multiple composite markers | Must be `0` |
| Ineligible founder composite count | Must be `0` |
| Approved referral reward codes | `PENDING` (reported separately) |
| Approved `launch_24h_card` rewards | `PENDING` (reported separately) |
| Founder/launch overlaps yielding more than one physical card | Must be `0` |
| Restore rehearsal evidence | `PENDING` |

## P0 — immediate stop conditions

| Gate | Owner | Status | Completion evidence |
|---|---|---|---|
| Contain public portal access to runtime/source/schema/support files on all three origins. | Production operator | PASS | VS-092: 69/69 apex, `www`, and sslip checks plus the canonical hosted verifier passed. Forbidden, encoded, traversal-like, hidden, schema, metadata, and admin paths return `404`; portal/play/assets/public APIs and the launcher manifest remain healthy. Nginx and portal services remain active. |
| Complete VS-089 as a committed, fail-closed application/package/Nginx boundary. | Codex | PASS | Commit `6d70d5ee`; `docs/BUGS.md` VS-089; packaged-runtime, exact-tree, all-origin, portal API/schema, launch-config, and full-build checks pass. |
| Prevent every existing pre-VS-089 portal bundle from being deployed. | Release owner | BLOCKED | The current legacy live tree is Nginx-contained, but superseded bundle paths and hashes still need a retire/quarantine record so they cannot be redeployed elsewhere without the boundary. |

## P1 — required before opening

| Workstream | Owner | Status | Completion evidence |
|---|---|---|---|
| Clean source and canonical build | Codex | IN PROGRESS | VS-090 is committed at `7ea4d4af`; the VS-091 candidate and canonical `scripts/build.sh` pass locally. Repeat against the final v12 bundle commit and sanitized public tree. |
| Final v12 server/client release bundle | Codex | PENDING | Manifest names exact final commit, `source_dirty=false`, exact hashes, protocol `10139`, corrected launch contract, and no blockers. |
| Bundle hygiene | Codex | PENDING | No personal absolute paths, credentials/keys, backups/archives, `.gitsave`, runtime state, private history, or unexpected files; populated `portal.env` install mode is `0600`. |
| Correct launch profile and custom content | Codex | PENDING | Complete launch-contract verification; archived boot log shows expected custom region/Enclave/Rift/Auction counts. |
| Public portal boundary | Codex + operator | PASS | VS-089 exact-tree/application/package checks and VS-092 live Nginx containment pass. The include is active on all three origins; hosted forbidden, allowed, range, API, play-page, and launcher-manifest checks pass. Clean bundle replacement remains covered by bundle hygiene and superseded-bundle quarantine. |
| Portal account flows | Codex + human | PENDING | Signup, rules acceptance, verification, login, recovery, character create/delete, logout, data-deletion link, and email delivery pass against final candidate. |
| Pre-signed-player cohort | Owner + Codex + operator | BLOCKED | Every protected-cohort checkbox and count/hash reconciliation above passes. |
| Reward/reset safety | Codex + owner | BLOCKED | VS-090 is locally verified: exact founder composites, a separate launch-24h route, one-card overlap handling, reset reruns, and focused/full-build checks pass. The final frozen roster plus real final-candidate vendor, full-inventory, and repeat-claim evidence are still required. |
| Portal roster durability | Codex | PASS | VS-091: strict canonical startup, exact private/writable directory and file modes, absent-only service-user initialization, runtime `503`, mutation refusal, atomic writes, and preservation fixtures pass. Validating a copy of the real store and the paired restore rehearsal remain separate operator gates below. |
| Database choice and permissions | Owner + operator | PENDING | Explicit SQLite/MariaDB decision; integrity/foreign keys, permissions, capacity, and exactly-one-writer proof. |
| Coordinated backup and rollback | Operator + owner | PENDING | Portal/game writes quiesced; paired hashed off-host backup restored successfully in isolation; objective rollback triggers recorded. |
| Desktop and launcher | Human + Codex | PENDING | Client `10139` launcher/runtime/cache are live and the 531-file hosted manifest gate passes. Exact-candidate fresh install, repair/offline behavior, and gameplay lifecycle remain the final hands-on gate. |
| Hosted web client | Codex + human | IN PROGRESS | Exact `10139` package is live; expected-manifest match, 538/538 deep hashes, and immutable cache policy pass with zero failures. Authenticated WSS/gameplay and physical iPhone reconnect remain pending while the game server is intentionally off. |
| Physical Android | Human | HUMAN | Validator-passing report names the exact public APK SHA-256 and covers signup, login, gameplay, background/resume, recovery, report/block, and data deletion. |
| Physical iPhone/Home Screen | Human | HUMAN | Report covers Safari/Home Screen, signup/login, keyboard, orientation, panels, chat, bank/map, WSS reconnect, and background/resume. |
| Play AAB identity | Codex | PASS | The exact verified AAB at SHA-256 `ef48b10c28b25a3581aa8068d39c93d68196a10c38cb1592bc83614d925aef87` passed package, upload-signer, version, client, server-contract, and SDK checks. Play accepted `12 (1.0.11)`, target SDK 35, for a 100% Production rollout. It is in Google review and will publish automatically on approval. |
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
| 2026-07-16 | VS-092 live portal containment | PASS | `e2b24ece`; snippet SHA-256 `a21ee841e920ba62ab15290d33e9861a903005a4bc9d44ba8e0622e40b3f7e5a` | Backup: `/opt/voidscape/backups/vs092-portal-containment-20260716T184849Z`. Nginx syntax/reload and 69/69 all-origin probes passed, followed by the canonical hosted verifier. Available logs show no successful body GET of backend `.mjs`, schema SQL, or build metadata; two README GETs returned bodies, with no established credential exposure. This configuration/log backup is not the required paired portal/game data backup. |
| 2026-07-16 | Canonical build after VS-092 | PASS | `e2b24ece` | `scripts/build.sh` exit `0`; focused launch-config, static-boundary, package-runtime, portal API, and portal schema checks pass; JDK 25 compatibility warnings only. |
| 2026-07-16 | Pre-containment held portal boundary | FAIL (historical; now contained) | held bundles from `57056c61` | Forbidden paths were packaged/served and status-only probes returned `200` on all three origins before VS-092. The live access path is now contained; bundle retirement remains open. |
| 2026-07-17 | Public client surfaces | PASS | `f14cf3c6`; client `10139`; APK v12 / `1.0.11` | Live launcher SHA-256 `daa81d8eb39694a19f27c51f6a0042d73ee2ebede266f679401cfa59663268aa`; client jar `58ca1dd61fd604bc04fd4890cf4e258f6190b810283a1f4eb79fae6af5970623`; signed APK `f9ef65086cfe3f5699e48c59fcb7f748d8241510cb379f70eb11d3ae0123f382`; portal and legacy update manifests both report `10139`. Account-success Web/Desktop/Android tiles bind to the live routes. |
| 2026-07-17 | Hosted web package | PASS (static/deep) | manifest SHA-256 `a55128655d55af97c63bfbe74cde79f4a94bba4d3ed936a1f48cd76e528620c8` | Expected manifest matches; 538/538 hosted files and immutable cache headers pass with zero failures at `tmp/web-teavm-deployment-verify-10139-live`. Authenticated WSS smoke remains deferred until the server opens. |
| 2026-07-17 | Client-surface deployment backup | PASS | `/opt/voidscape/backups/client-surfaces-pre-20260717T031024Z` | Portal and game writers were quiesced; paired portal-store and SQLite backups were hashed before the atomic swaps. Live store permissions are `0700`/`0600`; portal and nginx are active; game/headless remain inactive. |
| 2026-07-17 | Hosted launch/client gate | PASS | `tmp/launch-staging-live-client-10139-final` | Public portal, exact APK, Google Play URL, 531-file launcher channel, all three public origins, deployed config, and SQLite connection contract pass. Signup and web verification were intentionally skipped; the web package passed separately above. |
| 2026-07-17 | Play status | IN REVIEW, AUTO-PUBLISH ON APPROVAL | v12 / `1.0.11` / client `10139` | Submitted for a 100% Production rollout. Console says `Your changes are now in review`; Managed Publishing is off, so the rollout starts automatically after Google approval. The currently public release remains v9 / `1.0.8` until approval. |
| 2026-07-16 | Play AAB identity | PASS | SHA-256 `ef48b10c28b25a3581aa8068d39c93d68196a10c38cb1592bc83614d925aef87` | Canonical `--play-release` build and Play preflight pass; exact local artifact uploaded; Console accepted versionCode 12, versionName `1.0.11`, API 23+, and target SDK 35. |
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
  VS-090 replaces the account-global marker with exact per-character founder composites,
  pending final verification.
- 2026-07-16: owner set two distinct cutoffs: founder reservations freeze at launch,
  `2026-07-18T18:00:00Z`, while every character created before
  `2026-07-19T18:00:00Z` receives the separate launch promotion. Vendor deduplication
  prevents two cards when both routes apply.
- 2026-07-16: committed and fully verified the local VS-089 fail-closed portal
  application/package/Nginx boundary at `6d70d5ee`. At that point the live production
  origins were still exposed and every superseded bundle remained blocked pending
  separately authorized containment.
- 2026-07-16: triaged the per-character founder reward as VS-090 and fail-open portal
  roster loading as VS-091. Both are P1 launch blockers; the owner resolved VS-090's
  deletion/recreation, promotion-stacking, and legacy-code accounting policy. VS-090's
  local implementation and automated verification now pass; final frozen-data and
  final-candidate acceptance gates remain open.
- 2026-07-16: completed the explicitly authorized VS-092 Nginx-only containment at
  `e2b24ece`. Preflight caught an overbroad `.properties` denial before installation,
  preserving `/api/launcher/manifest.properties`; the corrected rule, 69/69 matrix,
  canonical hosted verifier, and full build pass. The game ingress stayed intentionally
  closed, and no portal bundle, database, client, Play change, or source publication
  was deployed.
- 2026-07-16: completed and locally verified VS-091. The final portal candidate now
  fails closed on invalid protected state, has an explicit service-user first-run path,
  reports runtime store damage as unready, and packages WAL-safe paired backup/restore
  instructions. No production portal state was read or changed; real-store copy
  validation and the coordinated restore rehearsal remain pending.
