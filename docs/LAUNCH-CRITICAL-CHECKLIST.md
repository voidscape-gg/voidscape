# July 18 launch critical checklist

This is the short operator queue for the July 18, 2026 launch. Use
`docs/RELEASE-CHECKLIST.md` as the full audit inventory, but do not spend the
remaining prelaunch budget on unchecked items that are explicitly deferred
below.

Launch time: **2026-07-18 18:00 UTC / 11:00 AM Pacific**.

## Locked scope

- [x] Keep the current post-reset production SQLite world and growing portal roster. Do not reset it again.
- [x] Keep the production game stopped throughout preparation, OWNER promotion, backup, and card cutover. Only after the card apply passes may it boot privately behind closed ingress for the final authentication/save gate.
- [x] Give each eligible character in the brief frozen launch roster one launch Subscription card through the reviewed one-shot cutover. Eligibility means the game character was fully created before the roster freeze began; requesting a verification email is not enough. Existing account-level starter promises remain separate.
- [x] Keep the landing page and signup flow live throughout preparation, then use only the tested approximately ten-minute roster-write freeze immediately around the final backup, review, and card cutover.
- [x] Offer Android through both Google Play `9 / 1.0.8` and the checked release-signed direct APK as first-class launch channels. Physical Android QA is owner-waived/not passed.
- [x] Support iPhone at launch through Safari at `https://voidscape.gg/play/`. This is an owner-accepted browser option, not a native iOS/App Store claim; physical iPhone Safari/Home Screen QA remains owner-waived/not passed.
- [x] Do not publish or push the new clean source during this preparation slice. Preserve the clean snapshot and hashes locally; any later source request or disclosure decision is a separate task requiring owner approval. The older GitHub mirror is already public external state, but this slice must not update or advertise it as the current release. This is a priority decision, not a license-compliance determination.
- [x] Defer Tebex/in-client purchasing, Appearance Studio/Paperdoll V2, new cosmetics, and nonessential website expansion.

## P0 — prepare production while the world is closed

- [x] Google Play has the exact signed AAB, Internal Testing is available, and the 100% Production rollout is in Google review with automatic publishing after approval.
- [x] Production discovery confirms `voidscape.service` is inactive and disabled, public TCP `43596` is closed, SQLite integrity is `ok`, daily backups succeed, and portal signup remains live.
- [x] Make only the two required predeploy portal/package corrections:
  - stop presenting obsolete source commit `97484977...` as the current build; report publication as pending without pushing source;
  - support a validated Google Play listing after approval while keeping the checked signed direct APK visible as an equally valid Android choice.
- [x] Run focused portal/package tests, `scripts/build.sh`, `git diff --check`, and a real launch-open desktop/mobile browser smoke; commit the minimal correction.
- [x] Build one fresh promotable production bundle from the resulting clean commit:

  ```bash
  scripts/package-launch-staging.sh \
    --host voidscape.gg --port 43596 --ws-port 43496 \
    --portal-url https://voidscape.gg/ \
    --web-url https://voidscape.gg/play/ \
    --ws-url wss://voidscape.gg/play/ws/ \
    --launch-at 2026-07-18T18:00:00Z \
    --android-release \
    --output-dir tmp/launch-production-10132
  ```

- [x] Require `promotable=true`, fresh server/client/web builds, protocol `10132`, a checker-passed release APK, production URLs, and no promotion blockers in `MANIFEST.txt`.
- [x] Sync the bundle only to a versioned remote release directory such as `/opt/voidscape/releases/<commit>-10132/`; never use the bundle's `rsync --delete` directly on `/opt/voidscape`.
- [x] Take a live SQLite `.backup` rehearsal snapshot and record its SHA-256, player count, patch ledger, `PRAGMA integrity_check`, and `PRAGMA foreign_key_check`.
- [x] Boot the exact bundle against that copied database on high ports that are not allowed by UFW. Provision a rehearsal-only QA identity in the clone, then use voidbot for login, state, logout, restart, and relogin proof. Never expose production `43596` or `43496` during rehearsal.
- [x] Verify the clone receives the commerce and world-achievement patches, keeps all original player/cache rows, remains integrity-clean, and can be restored from the rehearsal backup. Archive the journal and voidbot JSON.

## P0 — brief production promotion

- [x] Confirm again that `voidscape.service` is inactive and disabled. Stop the portal briefly so the portal JSON and game DB have one coordinated backup point.
- [x] Create `/opt/voidscape/backups/prep-10132-<UTC stamp>/` containing:
  - game SQLite backup;
  - portal app, portal JSON data, and `portal.env`;
  - server jars, helper jar, `local.conf`, `connections.conf`, `conf/`, and `database/`;
  - client/cache, launcher, `/play`, public downloads, nginx configuration, and relevant systemd units.
- [x] Verify the backup database and a restored copy before overwriting anything.
- [x] Promote the checked server files, portal, TeaVM `/play`, client/cache, launcher channel, and signed direct `10132` APK through validated same-filesystem staged swaps. Preserve the live DB, secrets, logs, avatars, and private `connections.conf`.
- [x] Merge production configuration instead of replacing secrets. Require:
  - protocol `10132`, command lockdown, launch content gates, and packet registration;
  - `PORTAL_LAUNCH_AT=2026-07-18T18:00:00Z`;
  - `PORTAL_LAUNCH_FREE_CARD_HOURS=0`;
  - durable portal/game DB paths and working Resend/helper configuration;
  - no steady-state `PORTAL_ADMIN_TOKEN`;
  - no Play URL until Google approves it.
- [x] Disable the obsolete repeating native-backfill timer and stale beta-open/announcement timers. The final backfill is a reviewed one-shot launch operation.
- [x] Install the nginx `/api/admin/*` block for `voidscape.gg`, `www.voidscape.gg`, and the legacy sslip origin; require `404` with and without a token header.
- [x] Run `nginx -t`, restart only `voidscape-portal`, and reload nginx. Leave the game inactive and disabled.
- [x] Verify production while closed:
  - `/api/health` is public-ready with no issues and no admin token;
  - `/api/public` advertises Web/Desktop/Android only and the card cutoff equals launch time;
  - `/legends` works;
  - launcher/API and legacy manifests plus TeaVM/APK metadata report protocol `10132`, and the served client/cache/launcher/APK hashes match the bundle;
  - TCP remains closed and WSS remains unavailable because the game service is stopped;
  - the public signup surface is available and the portal reports durable, readable storage.
- [x] Complete one real post-promotion production signup and email-verification pass with an owner-selected address. This deliberately remains separate because a synthetic signup would mutate the live production roster.

## P0 — final launch-day gate

- [x] Confirm Play `9 / 1.0.8` is publicly receivable and independently confirm the signed `10132` direct APK is public and tested. Both are advertised Android choices, not primary/fallback substitutes. The public Play listing returned an Install action, and live `/api/public` now exposes neutral `google-play` and `direct-apk` channels; the direct APK remains client `10132` with SHA-256 `33f2c22a6bfc72782c3c39d73d99dea0f44789eb230d1bc5c5b32f79e27c7324`.
- [x] With `voidscape.service` inactive and disabled, run the guarded OWNER helper first as a dry run and then with `--apply` for exact player `542`, portal account `225`, and username `name123`. Exactly one linked, offline, unbanned USER became the only OWNER; ownership/stat rows stayed singular, the pre-change backup and restored copy are byte-identical at SHA-256 `be0ea51bde8602e9fdc214c80ae12828c13bec1cd0fab04e8f3d612ddf16ef00`, database checks passed, and the guarded rollback dry-run passed. This proves the offline rank/link mutation only—the OWNER authentication gate remains open until the private boot below.
- [ ] About ten minutes before opening, run `scripts/manage-portal-roster-freeze.sh freeze`. The helper must fail closed unless `voidscape.service` is inactive and disabled/masked and TCP `43596`/`43496` have no listeners. Require `/api/public` and `/api/health` to report `rosterWritesFrozen=true`, roster-mutating requests to return `503 roster_writes_frozen`, and the landing, resend, login, and security surfaces to remain live.
- [ ] Take a new coordinated game DB + portal-data backup and verify its restore.
- [ ] Run the final native-account/card cutover dry-run against the frozen roster. Explicitly include or exclude every staff, banned, test, or ambiguous character; include the owner-approved exception player `542` / `name123`.
- [ ] Present the exact roster counts, exception decisions, `reviewToken`, and frozen `asOfMs` for owner approval. Do not apply before that approval.
- [ ] Apply once; verify one `launch_subcard_2026:<playerId>` marker per included character, no excluded markers, exactly one `launch_subcard_2026:done` seal, and a zero-pending post-apply dry-run.
- [ ] Archive an empty `launch-2026` achievement baseline and confirm the cracker pool is absent or zero.
- [ ] With the roster still frozen and public TCP/WSS still closed, start protocol `10132` for the first production boot. Use loopback voidbot to log in specifically as `name123`, prove the OWNER-only but read-only `::loadbots status` path returns normal status instead of an owner-only refusal (do not start bots), then verify save, logout, and relogin. This closes the real OWNER authentication gate. Do not run the game before or during the backup, dry-run, or card apply; start it only after the apply has passed.
- [ ] At the announced time, open TCP/WSS, then immediately run `scripts/manage-portal-roster-freeze.sh unfreeze`. Require external TCP reachability, WSS `101`, launcher/web/Android compatibility, iPhone Safari `/play` availability, portal launch-open state, `rosterWritesFrozen=false`, and a normal voidbot login. Characters created after unfreeze are post-cutover and receive no launch card.
- [ ] Watch server, portal, nginx, database, disk, and tick telemetry continuously through the initial launch window.
- [ ] Activate crackers only after the owner chooses the finite supply: run `::cracker N` once and verify exactly one canonical pool row plus the global announcement.

## Hard stops

Do not open the world if any of these are true:

- A coordinated backup or restore verification failed.
- The production-database clone migration, boot, or relogin rehearsal failed.
- Any client/public channel still reports a protocol other than `10132`.
- Portal health has issues, public admin routes are not `404`, or a steady-state admin token remains configured.
- No verified OWNER account is available.
- Either advertised Android channel—Play code `9` or the checked signed direct APK—cannot deliver Android `10132`.
- The final card cohort has unresolved conflicts or unreviewed exceptions.
- The owner-approved player `542` decision is absent from the frozen cohort report, or the freeze is lifted before ingress opens.
- The game is reachable before the approved opening time.

## Explicitly deferred

- Public-source push, public-branch cleanup, and source-disclosure hosting. Keep the clean snapshot private and reproducible; do not alter remote refs without a separate owner request.
- Physical Android QA beyond the recorded owner waiver; perform it as soon as real hardware is available.
- Physical iPhone Safari/Home Screen QA beyond the recorded owner waiver, plus any native iOS/App Store application. The supported July 18 iPhone path is the existing browser client at `/play` only.
- Native Tebex/in-game checkout and shop refinements.
- Appearance Studio, Paperdoll V2, new hairstyles, and cosmetic sales.
- MariaDB migration, advanced monitoring expansion, and non-launch-critical portal/Legends enhancements.

## Existing evidence to preserve

- Play submission: `tmp/play-android-8e/summary.json`
- Hosted launch gate: `tmp/launch-staging-hosted-c519a-final-signup-chrome-pass/summary.json`
- Hosted TeaVM deep verification: `tmp/launch-staging-hosted-c519a-final-signup-chrome-pass/web-deployment/summary.json`
- Android emulator campaign proof: `tmp/android-cracker-campaign-8b-accepted5`
- Play/fallback plus publication-pending browser proof: `tmp/portal-play-source-pending-20260712T180009Z`
- Production bundle: clean commit `6bcdf8607c40848479b9456c1528d88465c26a10`, local `tmp/launch-production-10132/`, remote `/opt/voidscape/releases/6bcdf8607c40848479b9456c1528d88465c26a10-10132/`; `MANIFEST.txt` SHA-256 `9d50747b78f3ee17e8c3e7b39a5bcf1ae74c21f99481b34068689e814832a8ee`.
- Production SQLite rehearsal: `/opt/voidscape/rehearsals/6bcdf860-10132-20260712T181144Z/`; input snapshot SHA-256 `580c484deb3671f2dbe60ed4f246dca7b0910aa4f0e9b9626da8797e1321e10a`; 48-file `REHEARSAL-SHA256SUMS` SHA-256 `75acf8ebde2c522f726d9da2ca0c8a08b1ff3b2918e4a28223033f02cc268853`.
- Remote coordinated backup: `/opt/voidscape/backups/prep-10132-20260712T184726Z/`; `BACKUP-SHA256SUMS` SHA-256 `ed7a8fc003f91004d649176e681f0215d62057a33ad8d93debbc041efe2983e5`; manifest verification and byte-identical SQLite restore passed.
- Closed production verification: `tmp/production-closed-verify-6bcdf860/`; deep TeaVM proof `tmp/web-teavm-deployment-verify/summary.json`; the deployed launch config is byte-identical to the bundle and passes all 50 policy checks.
- Promotion evidence: `/opt/voidscape/deployments/prep-10132-6bcdf860/promotion-evidence/`; 547-file `PROMOTION-SHA256SUMS` SHA-256 `394c3ae70eba7db6eae346424fc8614eb31e2f785b7b253429a42dd4694d75bc`. The legacy `10126` launcher bridge downloaded all 531 entries, selected client `10132`, and staged the exact `10132` launcher. Commerce and achievement schemas are present and empty while the patch ledger intentionally remains at 15 until the first canonical server boot replays and records patches 16-17.
- Real production signup: `tmp/production-signup-20260713T001102Z/summary.json`; final mode passed 606 checks with zero failures and proved the exact owner-selected email active/verified with one linked `openrsc-sqlite-created` character (player 542). The pass exposed and then verified the VS-074 SQLite runtime-directory permission repair (`root:voidscape` `0750` -> `0770`); a service-user write/rollback probe, post-signup integrity and foreign-key checks, exact player/stats/link row counts, public admin `404`, inactive/disabled game service, closed TCP `43596`/`43496`, and unavailable WSS all passed. No game boot occurred.
- Verification UX/recovery: local evidence `tmp/portal-email-verification-ux-20260713T004326Z/`, live evidence `/opt/voidscape/deployments/portal-email-ux-20260713T004326Z/`, and production browser proof `tmp/vs076-production-visual/`. VS-076 made the scanner-resistant final button explicit and separated retryable network/5xx failures from expired and invalid/used links. The reversible four-file portal swap passed coordinated SQLite restore, public-ready health, exact served/release hashes, public admin `404`, and closed-world checks. Exactly one audited resend was delivered to the uniquely affected pending signup; no bulk resend, game boot, ingress opening, source publication, or launch reward action occurred.
- SQLite error-log redaction: local/live evidence `tmp/portal-sqlite-log-redaction-20260713T010651Z/` and `/opt/voidscape/deployments/portal-sqlite-log-redaction-20260713T010651Z/`; production browser proof `tmp/vs075-production-visual/`. VS-075 forces every portal SQLite child failure through a three-field allowlist and a retryable generic response, with a deterministic regression proving no SQL, email, IP, token, password hash/salt, path, or raw child data reaches logs. The one-file portal swap passed backup/restore, health, hash, admin `404`, and closed-world checks. After a separate explicit owner authorization, the hash-pinned incident journal archive was deleted (521 incident plus 22,163 unrelated entries), exactly 521 vulnerable-PID lines were filtered from isolated syslog, the preserved journal archive stayed byte-identical, and zero incident indicators remain. No raw log copy was retained; portal/game/database state remained healthy and closed.
- Launch-channel/roster-freeze portal deployment: local commit `0f4d5d95`; production evidence `/opt/voidscape/deployments/portal-launch-policy-20260713T023607Z/`, rollback backup `/opt/voidscape/backups/portal-launch-policy-20260713T023607Z/`; deployment-summary SHA-256 `ac9dfdfcacd214feac65692f8fc9d099a56cb707ee96a06498602aeeda8bb366`, payload-manifest SHA-256 `8ac3c809754fedce7f8201ea8cf09b712acd762b149152882b40bad5f06469b0`, and verified backup-manifest SHA-256 `0753a95acd992b3d1ec9e0d60f17de98f56f489d9ace48dc731d43dfe7264a41`. The approximately 24-second drained portal swap returned the unchanged signup landing with roster writes open, public-ready health, no admin token, all public admin routes `404`, Play + Direct APK + iPhone Safari presentation, unchanged APK/TeaVM artifacts, player count `538`, and inactive/disabled/closed game ingress.
- Guarded OWNER promotion: `/opt/voidscape/backups/owner-promotion-20260713T023915Z/`; summary SHA-256 `89a7f60aefdbdb40a64c8f19f925154226ee5c0c33f179ade793e6802d87e9e7`; byte-identical pre-change/restored SQLite SHA-256 `be0ea51bde8602e9fdc214c80ae12828c13bec1cd0fab04e8f3d612ddf16ef00`. Player `542` / `name123` is the sole `group_id=0` row with exactly one `web_account_id=225` link; live integrity/foreign keys and the guarded rollback dry-run passed. This does not claim game authentication, which remains a launch-day private-boot gate.
