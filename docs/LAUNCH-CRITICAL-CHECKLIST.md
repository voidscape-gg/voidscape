# July 18 launch critical checklist

This is the short operator queue for the July 18, 2026 launch. Use
`docs/RELEASE-CHECKLIST.md` as the full audit inventory, but do not spend the
remaining prelaunch budget on unchecked items that are explicitly deferred
below.

Launch time: **2026-07-18 18:00 UTC / 11:00 AM Pacific**.

## Locked scope

- [x] Keep the current post-reset production SQLite world and growing portal roster. Do not reset it again.
- [x] Keep the production game stopped until launch; the portal countdown is presentation only and does not gate server login.
- [x] Give each character in the frozen launch roster one launch Subscription card through the reviewed one-shot cutover. Existing account-level starter promises remain separate.
- [x] Ship Android through Google Play `9 / 1.0.8` with the checked release APK as fallback. Physical Android QA is owner-waived/not passed.
- [x] Defer iPhone from launch and do not advertise it as launch-ready.
- [x] Do not publish or push the new clean source during this preparation slice. Preserve the clean snapshot and hashes locally; any later source request or disclosure decision is a separate task requiring owner approval. The older GitHub mirror is already public external state, but this slice must not update or advertise it as the current release. This is a priority decision, not a license-compliance determination.
- [x] Defer Tebex/in-client purchasing, Appearance Studio/Paperdoll V2, new cosmetics, and nonessential website expansion.

## P0 — prepare production while the world is closed

- [x] Google Play has the exact signed AAB, Internal Testing is available, and the 100% Production rollout is in Google review with automatic publishing after approval.
- [x] Production discovery confirms `voidscape.service` is inactive and disabled, public TCP `43596` is closed, SQLite integrity is `ok`, daily backups succeed, and portal signup remains live.
- [ ] Make only the two required predeploy portal/package corrections:
  - stop presenting obsolete source commit `97484977...` as the current build; report publication as pending without pushing source;
  - support a validated Google Play listing as the primary Android choice after approval, with the signed APK clearly labeled as fallback.
- [ ] Run focused portal/package tests, `scripts/build.sh`, and `git diff --check`; commit the minimal correction.
- [ ] Build one fresh promotable production bundle from the resulting clean commit:

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

- [ ] Require `promotable=true`, fresh server/client/web builds, protocol `10132`, a checker-passed release APK, production URLs, and no promotion blockers in `MANIFEST.txt`.
- [ ] Sync the bundle only to a versioned remote release directory such as `/opt/voidscape/releases/<commit>-10132/`; never use the bundle's `rsync --delete` directly on `/opt/voidscape`.
- [ ] Take a live SQLite `.backup` rehearsal snapshot and record its SHA-256, player count, patch ledger, `PRAGMA integrity_check`, and `PRAGMA foreign_key_check`.
- [ ] Boot the exact bundle against that copied database on high ports that are not allowed by UFW. Provision a rehearsal-only QA identity in the clone, then use voidbot for login, state, logout, restart, and relogin proof. Never expose production `43596` or `43496` during rehearsal.
- [ ] Verify the clone receives the commerce and world-achievement patches, keeps all original player/cache rows, remains integrity-clean, and can be restored from the rehearsal backup. Archive the journal and voidbot JSON.

## P0 — brief production promotion

- [ ] Confirm again that `voidscape.service` is inactive and disabled. Stop the portal briefly so the portal JSON and game DB have one coordinated backup point.
- [ ] Create `/opt/voidscape/backups/prep-10132-<UTC stamp>/` containing:
  - game SQLite backup;
  - portal app, portal JSON data, and `portal.env`;
  - server jars, helper jar, `local.conf`, `connections.conf`, `conf/`, and `database/`;
  - client/cache, launcher, `/play`, public downloads, nginx configuration, and relevant systemd units.
- [ ] Verify the backup database and a restored copy before overwriting anything.
- [ ] Atomically promote the checked server files, portal, TeaVM `/play`, client/cache, launcher channel, and signed `10132` APK fallback. Preserve the live DB, secrets, logs, avatars, and private `connections.conf`.
- [ ] Merge production configuration instead of replacing secrets. Require:
  - protocol `10132`, command lockdown, launch content gates, and packet registration;
  - `PORTAL_LAUNCH_AT=2026-07-18T18:00:00Z`;
  - `PORTAL_LAUNCH_FREE_CARD_HOURS=0`;
  - durable portal/game DB paths and working Resend/helper configuration;
  - no steady-state `PORTAL_ADMIN_TOKEN`;
  - no Play URL until Google approves it.
- [ ] Disable the obsolete repeating native-backfill timer and stale beta-open/announcement timers. The final backfill is a reviewed one-shot launch operation.
- [ ] Install the nginx `/api/admin/*` block for `voidscape.gg`, `www.voidscape.gg`, and the legacy sslip origin; require `404` with and without a token header.
- [ ] Run `nginx -t`, restart only `voidscape-portal`, and reload nginx. Leave the game inactive and disabled.
- [ ] Verify production while closed:
  - `/api/health` is public-ready with no issues and no admin token;
  - `/api/public` advertises Web/Desktop/Android only and the card cutoff equals launch time;
  - `/legends` works;
  - launcher manifest, client/cache, TeaVM build, and APK all report protocol `10132` and expected hashes;
  - TCP remains closed and WSS remains unavailable because the game service is stopped;
  - portal signup resumes successfully.

## P0 — final launch-day gate

- [ ] Confirm Play `9 / 1.0.8` is publicly receivable. If Google review is still pending, confirm the signed `10132` APK fallback is public and tested before opening Android access.
- [ ] Confirm a real break-glass OWNER account exists and can authenticate. Do not infer this from public aggregate counts.
- [ ] Pause portal signup, verification completion, and character creation; keep the game stopped.
- [ ] Take a new coordinated game DB + portal-data backup and verify its restore.
- [ ] Run the final native-account/card cutover dry-run against the complete roster. Explicitly include or exclude every staff, banned, test, or ambiguous character.
- [ ] Present the exact roster counts, exception decisions, `reviewToken`, and frozen `asOfMs` for owner approval. Do not apply before that approval.
- [ ] Apply once; verify one `launch_subcard_2026:<playerId>` marker per included character, no excluded markers, exactly one `launch_subcard_2026:done` seal, and a zero-pending post-apply dry-run.
- [ ] Archive an empty `launch-2026` achievement baseline and confirm the cracker pool is absent or zero.
- [ ] Close public TCP/WSS ingress before the first production boot, start protocol `10132`, and use loopback voidbot to verify login, save, logout, and relogin.
- [ ] At the announced time, open TCP/WSS and require external TCP reachability, WSS `101`, launcher/web/Android compatibility, portal launch-open state, and a normal voidbot login.
- [ ] Watch server, portal, nginx, database, disk, and tick telemetry continuously through the initial launch window.
- [ ] Activate crackers only after the owner chooses the finite supply: run `::cracker N` once and verify exactly one canonical pool row plus the global announcement.

## Hard stops

Do not open the world if any of these are true:

- A coordinated backup or restore verification failed.
- The production-database clone migration, boot, or relogin rehearsal failed.
- Any client/public channel still reports a protocol other than `10132`.
- Portal health has issues, public admin routes are not `404`, or a steady-state admin token remains configured.
- No verified OWNER account is available.
- Neither Play code `9` nor the checked signed APK can deliver Android `10132`.
- The final card cohort has unresolved conflicts or unreviewed exceptions.
- The game is reachable before the approved opening time.

## Explicitly deferred

- Public-source push, public-branch cleanup, and source-disclosure hosting. Keep the clean snapshot private and reproducible; do not alter remote refs without a separate owner request.
- Physical Android QA beyond the recorded owner waiver; perform it as soon as real hardware is available.
- iPhone Safari/Home Screen launch support.
- Native Tebex/in-game checkout and shop refinements.
- Appearance Studio, Paperdoll V2, new hairstyles, and cosmetic sales.
- MariaDB migration, advanced monitoring expansion, and non-launch-critical portal/Legends enhancements.

## Existing evidence to preserve

- Play submission: `tmp/play-android-8e/summary.json`
- Hosted launch gate: `tmp/launch-staging-hosted-c519a-final-signup-chrome-pass/summary.json`
- Hosted TeaVM deep verification: `tmp/launch-staging-hosted-c519a-final-signup-chrome-pass/web-deployment/summary.json`
- Android emulator campaign proof: `tmp/android-cracker-campaign-8b-accepted5`
- New production bundle/evidence: `tmp/launch-production-10132/`
- Remote coordinated backup: `/opt/voidscape/backups/prep-10132-<UTC stamp>/`
