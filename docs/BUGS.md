# Bug ledger

Living document. This file plus `docs/BUGFIX-LOOP.md` are the durable state for the
autonomous bug-fix loop: any fresh session (or post-context-clear session) must be able
to resume from these two files alone. Keep every entry self-contained.

**Rules**
- One entry per bug, ID `VS-NNN`, never reuse or renumber IDs.
- Update Status and Log lines **as you work**, not after — a context clear can happen at
  any moment, and this file is what survives it.
- Statuses: `reported` (claim or suspicion, not yet established) → `confirmed` (defect
  established by reproduction or conclusive code evidence) → `in-progress` → `fixed`
  (code done, build green) → `verified` (Verify recipe passed; move entry to Fixed
  archive). Also `blocked(<reason>)` and `wontfix(<reason>)`. A code TODO alone
  `confirms` only if it states the defect precisely enough to write a Verify recipe;
  otherwise seed as `reported`.
- Severity: **P1** breaks core play, corrupts data, or blocks the launch (2026-07-11
  per the portal's `PORTAL_LAUNCH_AT` default — Ryan to confirm) · **P2** a feature is
  broken · **P3** minor / workaround exists · **P4** cosmetic, seasonal, or speculative.
- Every verified fix: one commit whose subject references the `VS-NNN` + a
  `docs/DIVERGENCE.md` paragraph (CLAUDE.md hard rule 6). The ID in the subject is the
  ledger↔history link (`git log --grep VS-NNN`) — don't try to record hashes in the
  same commit that creates them.

---

## Loop state

- **Active bug:** none — VS-072 fixed and focused-verified; full portal API/visual gates
  are blocked by unrelated existing failures, so the entry remains Open as `fixed`
  until those gates can run cleanly.
- **Session preflight 2026-07-09 (VS-072):** branch `main`; pre-change
  `scripts/build.sh` green. Existing dirty files before this fix include Android APK
  docs/build/script files, `Client_Base/src/orsc/mudclient.java`,
  `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`,
  portal launch WIP (`web/portal/dev-server.mjs`, `web/portal/script.js`,
  `web/portal/api-smoke.mjs`, `scripts/test-portal-api.sh`, `web/portal/README.md`,
  untracked `web/portal/portal.html` and related assets/styles), subscription/content
  files, `server/src/com/openrsc/server/GameStateUpdater.java`,
  `server/src/com/openrsc/server/ServerConfiguration.java`, `DiscordService.java` WIP,
  runtime `server/inc/sqlite/preservation.db`, deleted Android legacy drawables, and
  untracked launch/portal scripts/assets. Stage only VS-072 hunks if committing.
  Result: added authenticated character delete route, selected-character UI action,
  OpenRSC SQLite cleanup for portal-created saves, unlink-only handling for externally
  linked saves, and regression assertions. `scripts/build.sh`,
  `scripts/test-portal-schema.sh`, syntax checks, `git diff --check`, and focused
  launch-mode delete probe passed. `scripts/test-portal-api.sh` still stops on the
  pre-existing `snapshot endpoint should resolve the active title` fixture drift before
  reaching VS-072 assertions; `scripts/smoke-portal-prelaunch-visual.sh` still stops on
  pre-existing landing selector/sign-up locators before reaching account UI.
- **Session preflight 2026-07-08 (VS-061 reopen):** branch `main`; pre-change
  `scripts/build.sh` green. Existing dirty files before this fix include Android APK
  docs/build/script files, `Client_Base/src/orsc/mudclient.java`,
  `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`, portal
  WIP, subscription/content files, `server/src/com/openrsc/server/GameStateUpdater.java`,
  `server/src/com/openrsc/server/ServerConfiguration.java`, `DiscordService.java` WIP,
  runtime `server/inc/sqlite/preservation.db`, deleted Android legacy drawables, and
  untracked launch/portal scripts/assets. Stage only VS-061 hunks if committing.
  Result: server-side Android reconnect takeover fixed, and the APK now pauses shared
  client socket polling while backgrounded and suppresses the short resume reconnect
  overlay. `scripts/build.sh` and `scripts/build-android.sh --debug` passed. Focused
  Android lifecycle smoke reached authenticated in-game state at
  `tmp/vs061-android-lifecycle-reopen` and server logs showed `Login details for
  wbtest: Android/`, but the smoke stopped before HOME/resume because Settings stayed
  on `settingTab=0` while the harness expected `1`. A focused manual emulator runner
  then verified a 30s switch to Android Settings and launcher return at
  `tmp/vs061-manual-switch-after-overlay-suppress`: `GameActivity` resumed, `wbtest`
  stayed online, and the final screenshot had no reconnect overlay. Ryan's physical
  Android retest also passed.
- **Session preflight 2026-07-07 (VS-071):** branch `main`; pre-change
  `scripts/build.sh` green. Existing dirty files before this fix include the verified
  VS-069/VS-070 hunks, `docs/DIVERGENCE.md`, `docs/subsystems/combat-system.md`,
  `server/inc/sqlite/preservation.db` runtime state, `DiscordService.java` WIP, and
  untracked brief/config files. Stage only VS-071 hunks if committing.
  Result: VS-071 fixed and verified by JSON validation, `scripts/build.sh`, and live
  voidbot traversal; evidence in `tmp/vs071-ardougne-rift.txt`.
- **Ryan's redirect (2026-07-07, mid-VS-069):** pause VS-069 rift work and fix the
  level-43 prayer behavior first.
- **Session preflight 2026-07-07 (VS-070):** branch `main`; pre-change
  `scripts/build.sh` green. Existing dirty files before this fix: `docs/DIVERGENCE.md`
  (known collision plus Discord redaction entry), `server/inc/sqlite/preservation.db`
  (runtime artifact), `server/src/com/openrsc/server/net/DiscordService.java`
  (unlisted WIP; do not touch), plus untracked brief/config files
  `.claude/launch.json`, `CODEX-*.md`, and `docs/TITLE-OVERHAUL-BRIEF.md`.
  Stage only VS-070 hunks if committing.
  Result: VS-070 fixed and verified by `scripts/build.sh` plus source call-site
  inspection; live voidbot verification deferred because prayer/cast commands are not
  implemented.
- **VS-069 completion 2026-07-07:** verified on the rebuilt local server with voidbot;
  evidence in `tmp/vs069-void-arena-rift.txt`. Active bug remains VS-070 per Ryan's
  later redirect.
- **Session preflight 2026-07-07 (VS-069):** branch `main`; pre-change
  `scripts/build.sh` green. Existing dirty files before this fix:
  `docs/DIVERGENCE.md` (known collision), `server/inc/sqlite/preservation.db`
  (runtime artifact), `server/src/com/openrsc/server/net/DiscordService.java`
  (unlisted WIP; do not touch), plus untracked brief/config files
  `.claude/launch.json`, `CODEX-*.md`, and `docs/TITLE-OVERHAUL-BRIEF.md`.
  Stage only VS-069 hunks if committing.
- **Ryan's redirect (2026-07-03, mid-loop):** work **from VS-038 down**, impact-first
  — skip the "meh" tail. In scope, in order: VS-038 (Death Match forfeit), then
  player-facing Intake items (Undead Siege logout feedback, bank-add-while-open stale
  view, goto silent stall). Explicitly deprioritized by this ruling: VS-034, VS-009,
  VS-036 (above VS-038 on the ready list), VS-006 (vague TODO), VS-013 (QA tooling),
  and the P4 tail.
- **Branch (ruled 2026-07-03):** work lands on **`main`** — Ryan approved
  fast-forwarding local main to the checkpoint branch head (origin/main was a strict
  ancestor, 132 ahead / 0 behind; `codex/ui-loot-checkpoint-20260613` left intact at
  the same commit). Commit future fixes on main; pushing origin/main is Ryan's call.
- **Session preflight 2026-07-03 (later session, /loop /fix-bugs):** dirty files all
  match the §7 Collisions list; branch main; pre-change `scripts/build.sh` green; dev
  server still up on 43596; voidbot daemon down (restart needed). Ryan invoked
  `/loop /fix-bugs` — the loop resumes per Next action (Intake triage first, canHold
  priority); the P3/P4-tail deprioritization ruling stands. This session so far:
  VS-048 triaged → fixed → verified → committed (canHold over-cap merge rejection;
  open cap-ruling question for Ryan). VS-049 (portal login throttle, P2 launch
  surface) triaged → fixed → verified → committed. VS-051 (google credential/username
  error ordering) fixed → verified → committed. VS-052 (voidbot wait usage errors +
  fail-fast on typo'd conditions) fixed → verified → committed. VS-053 (::spawnnpc
  coordinate support; wave-1 half settled as VS-027 pacing) fixed → verified →
  committed. Over-deposit bullet settled (safe hardening). VS-054 (::setstat natural
  order) + VS-055 (::queststage 2-arg reset) + VS-056 (::rested wording) fixed →
  verified → committed; server restarted on the VS-056 build. New Watch-list entry:
  post-restart smoke flake pattern (2 sightings).
  Intake triage pass: 3 bullets
  closed (integrity export = fixture artifacts; ::quickbank = VS-027 pacing;
  death-testability = resolved by qanpc1/F0.8), VS-050 filed
  blocked(WIP-collision). Server on the VS-048 build
  (port 43596); smoke 26/26; portal batteries green.
- **Last session:** 2026-07-03 — 10 commits, 8 bugs verified: VS-041 (dae8a471,
  voidbot Patch18 gate), VS-047 (546fa5cd, smoke gate 26/26 restored), **VS-008
  (32406401, P3→shipped: SEND_BANK_UPDATE short slot, CLIENT bumped 10120→10121 —
  packet-shape change, all presets + voidbot moved together)**, VS-042 (bb3ea249,
  Cook's Range mostly authentic; cook-absent feedback added), VS-031 (529ca8ab,
  voidbot bank compaction), VS-043 (821817e9, ::item full-inv message), VS-025
  (cc5f9489, exit 2 + bot-api roadmap markers), VS-032 (bc692ece, stale voidbot
  login trailer — real client was never at risk). Tooling: npc_say decoding
  (7ae57e62) — NPC overhead chat now observable. VS-034 diagnosed deeply, not fixed
  (see its Log). New Intake: Bank.canHold mis-rejections (2 datapoints). Also
  restored a WIP-deleted committed DIVERGENCE entry (arrange-mode) in the working
  tree. Server running latest build on port 43596; smoke 26/26.
- **Previous session (2026-07-02):** 13 items committed — wave-2 tooling gate
  (VS-020..024), VS-033, VS-026 (P1), VS-037, VS-028, VS-030, VS-029, F0.8, VS-003
  (partial, reopened). Details in Fixed archive.
- **Wave 2 DONE (2026-07-02):** 5 suites. E1/artisan skills verified (smelt/smith/cook/
  firemaking/fletch/gem-cut all work). Decoder artifacts settled (VS-027 deposits +
  item-command-slot = artifacts; VS-021 fixed; VS-008 server-half REAL). New: VS-041
  (voidbot 44/45 decoder desync, P2), VS-042 (Cook's Range gate), VS-043 (::item msg).
  VS-003 reopened (dormant interface + noted divergence, → P4, spec question for Ryan).
- **UI-QA (2026-07-02, Ryan-reported):** Ryan flagged the Classic bank overlapping the
  location plaque + oversized. Built `scripts/ui-geometry-lint.py` (deterministic overlap
  detector, 53fd6425) + wired into the S-V sweep so this class is caught every run.
  Root-caused as VS-044 (P2): slot-shrink breaks fixed 48×32 icons (reverted); real fix =
  hide plaque while banking OR emulate OpenRSC sizing — deferred (blocked on making the
  skin-on layout testable; `customUi` is write-only in the conf).
- **Bank rebuild DONE (2026-07-02):** the Void Glass bank shipped (commits
  68ee7588..9d3bbc01): centered HUD-safe glass panel, adaptive small-viewport split,
  live search, page tabs, loadouts, deposit-all, note mode, right-click quantities,
  noted two-layer icons. VS-044 + VS-040 resolved (Fixed archive). Two server bugs
  found+fixed along the way: VS-045 (custom_ui opcode gates) and VS-046 (SQLite preset
  persistence / executeQuery closed-ResultSet — general DB-layer fix). 2026-07-03:
  Ryan requested + shipped (9e1e8c2f): drag-to-reorder/Arrange mode (swap+insert,
  gates relaxed) and the bigger small-screen layout (top tabs hidden while banking,
  9 cols, 3 bank + 3 inv rows at Classic). Still skipped: cert-mode deposit
  (want_cert_deposit is false anyway).
- **Next action (top open confirmed, launch surfaces first):** VS-041 + VS-047 + VS-008
  + VS-042 + VS-031 + VS-043 + VS-025 + VS-032 + **VS-003 (closed by Ryan's rulings +
  server noted-on-death fix)** DONE 2026-07-03. **Ryan redirected 2026-07-03: the bug
  loop's P3/P4 tail is DEPRIORITIZED in favor of the launch-readiness sweep
  (docs/RELEASE-CHECKLIST.md, launch 2026-07-11)** — new findings still land in
  Intake. When the loop resumes: VS-034 (deep diagnosis in its Log), VS-009, P4 tail,
  Intake triage (canHold has two fresh datapoints). VS-002 deferred (needs MySQL env). Also: E2 (doors)
  to unblock a quests wave. VS-003: await Ryan's ruling. NOTE: client version bumped to
  10121 — a fielded 10120 client build will be version-rejected by the updated
  dev/staging server (expected; the launcher updates clients).

---

## Intake — dump raw bug reports here

- Duel-confirm outside-click sends packet 230 (trade decline) instead of 197 (duel decline) — mudclient.java ~5446; the decline button correctly sends 197. Looks like copy-paste from trade confirm. Found during UI slice 9.
- AuctionHouse.resetAllVariables() only runs from the private auctionClose(); the server-driven close (mudclient ~27846) and the new ESC close leave stale field state until next open. Found during UI slice 9.
- handleAndroidBackButton dereferences worldMapPanel without a null check (safe today only because the field is final-initialized inline; getWebOverlayDialogName null-checks it defensively). Found during UI slice 9.
- Android `--only-auth-lifecycle` smoke times out before HOME/resume because it waits
  for Settings `settingTab=1`, but the current authenticated APK reports
  `ANDROID_SMOKE_SETTINGS ... visible=true showUiTab=6 settingTab=0 ...` after the
  smoke opens Settings. Reproduced during VS-061 reopen verification at
  `tmp/vs061-android-lifecycle-reopen`.

Anyone (Ryan or an agent) can append raw, unstructured reports below, one bullet each.
The loop's triage step converts each into a numbered entry and removes it from this list.

_(Ryan: paste anything from "chat sometimes overlaps" to "X quest broken";
half-remembered is fine, triage will chase it down.)_

**Wave-1 oddity tail (un-triaged; each cites its report):**
- ~~`item-command` acts on the LAST stack~~ → **wave-2 RESOLVED: was a decoder artifact**
  (now honors the requested slot; tmp/qa/S-C2). Removed.
- Equipping a non-wieldable item silently ignored — wave-2: REAL but EXPECTED (authentic
  client never offers wield on non-wieldable; not a defect). Closed.
- `::item` into a full inventory misleading message → promoted to **VS-043**.
- ~~Over-deposit of an unheld item is a silent no-op~~ → **CLOSED 2026-07-03:
  confirmed live (bank open via npc-command, `bank-deposit --id 20` unheld → zero
  events) and by code — `depositItemFromInventory` clamps to `countId` (0) and
  returns. The authentic client can only offer deposits of HELD items, so this packet
  only arises from bots/hacked clients; a silent no-op is correct hardening, not a
  player-facing defect.
- ~~`::quickbank` admin command is a no-op~~ → **CLOSED 2026-07-03: was the VS-027
  rapid-admin drop** — VS-047's diagnosis pinned exactly this (quickbank fired ~0.3s
  after ::item → dropped); with 1.2s pacing the smoke gate's quickbank section passes
  26/26 (twice today). Not a bug of its own.
- NEW wave-2 lower-confidence items: banker-dialogue bank won't open in the voidbot
  daemon (npc-talk→menu-reply closes without opening bank — blocks daemon bank tests;
  workaround in use: `npc-command --id 95 --which 1` opens it fine; tmp/qa/S-C2 F3);
  equipping from inventory while banking closes the bank
  (server hideBank — possibly intended; tmp/qa/S-D2); `state skills` omits custom skills
  (runecraft/harvesting) so their xp can't be asserted (voidbot gap; tmp/qa/S-F);
  artisan XP-per-action ratios looked off vs def exp but the
  VoidPath 2x/rested 1.5x boosts confound it — needs isolation (tmp/qa/S-F).
- Server-side bank adds while the bank UI is open push no updates (`Bank.add(item,
  false)`) — view stale until reopen (tmp/qa/S-D F5)
- `::teleport` silently swallowed while a skilling batch is active, reproduced 2× (S-E F3)
- `wait xp-gained` missed one in-window thieving gain once — flaky (tmp/qa/S-E F4)
- Undead Siege mid-run logout gives no payout/forfeit feedback (tmp/qa/S-I)
- ~~Death items-kept untestable by fleet~~ → **CLOSED 2026-07-03: resolved by F0.8** —
  qanpc1 (non-privileged) is provisioned by `scripts/qa-provision-accounts.sh` and the
  VS-003 server fix was live-verified with a real qanpc1 death. Gap gone.
- S-V needs-eyes minors: Options panel bleeds through Advanced Settings modal;
  amount labels collide with panel border at slot 0 (bank "500", loot "3"); bestiary
  level column mixes "lv N" and "#id" for same-named NPCs; world-map window overlaps
  minimap + search box covers area label; `/dev/ui-panel "account"` returns ok but
  renders nothing (tmp/qa/S-V findings 3,5,6,7,8)
- ~~S-W integrity export 7 high-severity rows~~ → **CLOSED 2026-07-03: all fixture
  artifacts.** The S-W battery runs against a hermetic throwaway DB
  (`test-portal-api.sh` mktemp `openrsc-fixture.db`) that deliberately creates
  skeletal players 77 "SmokeHero" / 78 "TakenHero" without stats rows and omits the
  `bank` table; the integrity export scanned THAT. Live dev DB checked 2026-07-03:
  players 77/78 don't exist, `bank` table present, wbtest has all stats rows. The
  scanner behaved correctly on a skeleton.
- S-W minors triaged 2026-07-03 → **VS-049** (no login throttle, P2, fixing),
  **VS-050** (dry-run backfill preview counts, P4, WIP-blocked), **VS-051** (OAuth
  error ordering, P4). Bullets removed; see the entries.
- Housekeeping: qabot04's bank left at 1607 stacks for retests (over the live 192 cap
  — see VS-048 in the Fixed archive). 2026-07-03: its inventory carries the VS-008
  probe withdrawals (3× noted 1000, 2× noted 100/302/1546/2 — noted staffs now 50×);
  the VS-048 fix let the 100 loose coins deposit back (merge), but the noted probe
  items still can't re-deposit — they need NEW slots and the bank exceeds the 192 cap
  (awaiting Ryan's cap ruling in VS-048). Two Blessed ashes on the ground near
  (137,644) (tmp/qa/S-D end state). 2026-07-03 VS-042 work left qabot04 with Cook's
  Assistant COMPLETED (stage -1; was untracked before), ~9 raw + 1 burnt shrimp, and
  attack/strength max 90.

---

## Open bugs

### VS-072 — Website character manager cannot delete characters
- Status: fixed · Severity: P1 · Area: web-portal / launch surface
- Evidence: Ryan reported "big bug on the character manager on the website. you are
  not able to delete characters." Code evidence matched: the Characters view rendered
  selectable roster cards plus create controls only, frontend code only called
  `POST /api/characters`, and the portal API had no authenticated delete route or
  public launch-mode allowlist entry for character deletion.
- Repro: create/sign in to a portal account with at least two characters; the character
  manager exposes no delete control, and `DELETE /api/characters/:id` returns 404.
- Verify: focused launch-mode probe against a throwaway OpenRSC SQLite DB creates a
  first account character, creates a second character, deletes it through
  `DELETE /api/characters/:id`, confirms the roster returns to one character, and
  confirms no `players` row or orphaned `web_account_id` cache remains for the deleted
  character. Standard gates: `scripts/build.sh` pass, `scripts/test-portal-schema.sh`
  pass, JS/shell syntax pass, `git diff --check` pass. `scripts/test-portal-api.sh`
  remains blocked by unrelated existing title-fixture drift before reaching the new
  delete assertions; `scripts/smoke-portal-prelaunch-visual.sh` remains blocked by
  unrelated existing landing selector/sign-up locators before reaching account UI.
- Log: 2026-07-09 filed directly from Ryan's report; agents confirmed the root cause is
  missing UI/API/test wiring. Fix in progress: add authenticated
  `DELETE /api/characters/:id`, launch public-mode allowlist, selected-character UI
  button, and regression coverage. 2026-07-09 code fixed and focused-verified; leave
  Status `fixed` (not archived) until unrelated portal gates are green.

### VS-001 — Desktop client UI regressions after mobile-shell overhaul (umbrella)
- Status: reported · Severity: P2 · Area: client-ui
- Evidence: five consecutive restore/fix commits at HEAD (9bcc493 inventory clicks,
  15a79f7 classic chat layout, 9b2dcf2 classic camera/size preset, af9a749 launcher
  gating, d50eed9 wilderness indicator) — each repaired desktop behavior the
  mobile-panel-shell work broke (`voidscapeUseMobilePanelShell` and friends,
  `Client_Base/src/orsc/mudclient.java` ~L17051, ~L18337, ~L18554). Pattern says more
  regressions remain; Ryan reports "UI not working" issues.
- Repro: unknown — needs Ryan's specific symptoms via Intake, plus the workbench panel
  sweep = QA campaign suite S-V (`docs/QA-CAMPAIGN.md`; same sweep as BUGFIX-LOOP §6 —
  run it once, via the campaign).
- Verify: per finding — workbench `GET /state` + screenshots + voidbot interaction
  checks; split each concrete regression into its own VS entry when found.
- Log: 2026-07-02 seeded from survey. 2026-07-02 wave-1 S-V sweep (84 shots, 14
  panels × 6 viewports): rendering broadly OK incl. Classic preset; wilderness
  indicator verified; concrete regressions split to VS-029/VS-030 + Intake minors;
  severity P1→P2 — still needs Ryan's specific symptoms to close or refine.

### VS-002 — queryItemCreate binds 6 of 7 params (latent; impact far narrower than first stated)
- Status: confirmed · Severity: P3 · Area: server-db · **DO NOT "fix" by changing the shared query**
- Premise (true): `MySqlGameDatabase.queryItemCreate` (2588) binds 6 params against the
  7-placeholder `save_ItemCreate` (`MySqlQueries.java:114`), leaving param 7 unbound.
- **Corrected impact (investigated 2026-07-02):** the real item-persistence path is
  `savePlayerInventory` / `savePlayerBank` / `savePlayerEquipment`, and these are
  OVERRIDDEN on BOTH backends (`SqliteGameDatabase.java:105/72`,
  `MySqlGameDatabase.java:1962/2021`) and bind `save_ItemCreate` with all 7 params
  CORRECTLY (explicit itemId). So item saving is NOT broken on either backend, and the
  live `itemstatuses` table has ZERO corruption (checked: no oversized catalogID, clean
  durability). queryItemCreate is a secondary id-assignment path (assignItemID ←
  addItemToPlayer, `MySqlGameDatabase.java:3196/3304`) that is rarely/never the live
  save path — hence latent, not P2.
- **TRAP (learned the hard way):** the naive fix — dropping `itemId` from the shared
  `save_ItemCreate` (itemId is AUTOINCREMENT) — makes queryItemCreate's 6 binds line up,
  BUT breaks the 4 savePlayerInventory/Bank callers that correctly bind 7 →
  `SqliteGameDatabase.savePlayerInventory:106 "Index 6 out of bounds for length 6"`,
  and player inventories stop saving. Verified live and reverted. The shared query is
  correct for its main users.
- Correct fix (deferred): give queryItemCreate its OWN 6-column auto-increment query
  (e.g. `save_ItemCreateGenerated`, no itemId) and point only queryItemCreate at it.
- Blockers before shipping: (a) needs a MySQL/MariaDB env to verify the actually-affected
  path (dev is SQLite); (b) analyze the queryItemCreate ↔ savePlayerInventory ↔
  emptyInventory interaction to avoid PK conflicts on re-insert. Do not ship blind.
- Log: 2026-07-02 seeded from survey. 2026-07-02 investigated: premise true but impact
  is latent (save path is correct on both backends); naive shared-query fix breaks
  SQLite saves — reverted; downgraded P2→P3; correct fix needs an isolated query +
  MySQL verification. The verify step prevented a production-breaking change.

### VS-004 — Fresh-checkout config trap for custom item IDs (likely already fixed — re-verify)
- Status: reported · Severity: P3 · Area: content-pipeline / config
- Evidence: `docs/DIVERGENCE.md` ~L2138-2140 documents the failure mode — client
  reports `itemCount()-1` as max item id at login (`mudclient.java` tellLimitations,
  now ~L27279; the DIVERGENCE citation of L15111 is stale), and server items above the
  `restrict_item_id` cap fail `Inventory.add()` (`Inventory.java:107/:117`) with "Your
  client could not receive {item}". HOWEVER the tracked presets now ship the bump —
  `server/preservation.conf:79` and `server/default.conf:74` both set
  `restrict_item_id: 9999` — so the headline "fresh checkout rejects custom items"
  repro likely no longer fires.
- Repro: simulate a fresh checkout (preset conf → local.conf per `scripts/run-server.sh`),
  then `::item <custom id above 1289>` via voidbot. If it works, check which OTHER
  gitignored local.conf-only overrides a fresh checkout still loses.
- Verify: fresh-checkout flow receives custom items (voidbot `wait inventory-contains`);
  close as already-fixed if the presets cover it, or re-scope to whichever traps remain.
- Log: 2026-07-02 seeded from survey. 2026-07-02 verification pass found tracked confs
  already carry restrict_item_id 9999 — downgraded confirmed→reported, P2→P3.
  2026-07-02 wave-1 S-D: the client-cap half fired live on client 10120 (banked ids
  1604-1607 render as "Unobtanium" placeholder; withdraw force-drops the real item) —
  split to VS-032.

### VS-005 — Void Arena / Death Match lifecycle edge cases remain
- Status: blocked(E6 — PvP/ranked paths need two-player voidbot) · Severity: P3 · Area: server-plugin (minigame)
- Evidence: four fix/harden commits (88a42fb QA issues, e1ccaf9, 3abd940, e44a0c3);
  the QA fix list (repeated-click setup dialog, "(unranked)" leaking onto menu options,
  cage fighters missing Attack option, fights held open forever) shows the match
  lifecycle/cleanup code is fragile.
- Repro: unknown — needs a voidbot QA pass over the full flow: challenge → accept →
  ready → fight → death/win → cleanup → re-challenge; plus abandon/logout mid-match.
- Verify: scripted voidbot scenario for each lifecycle path; no stuck matches, correct
  menus/messages.
- Log: 2026-07-02 seeded from survey. 2026-07-02 wave-1 S-I/S-H: solo Sir Charles
  lifecycle verified CLEAN (loss path, mid-fight disconnect restores exact inventory
  DB-verified, immediate re-challenge, W/L counter). Residual scope: dmking W/L
  broadcast emitted twice per fight (S-I seq 159/161), in-fight logout silently
  swallowed (no denial message), "(unranked)" menu-leak recheck — all needing E6 or
  small solo fixes. NOTE: the Void Knight Death Match Arena is a different minigame
  and is broken outright — see VS-026 (P1).

### VS-006 — NPC drop tables flagged wrong around double-drops
- Status: fixed · Severity: P2 · Area: server-content
- Evidence: chaos druid, chaos druid warrior, and Salarin use zero-weight nested
  double-drop tables; `DropTable.rollItem` has an explicit only-tables branch for this
  shape, and `invariableItems` skips table drops because their item id is `NOTHING`.
- Repro: code audit found the intended behavior: the double-drop branch rolls one herb
  plus one normal-table drop when its weighted parent entry is selected.
- Verify: build green; no TODO remains; optional future statistical loop can confirm
  observed rates but is not required for the vague TODO cleanup.
- Log: 2026-07-02 seeded from survey. 2026-07-07 audit: documented the intended
  zero-weight nested-table behavior and removed the vague TODOs while adding the
  Wilderness loot layer; `scripts/build.sh` is green.

### VS-009 — Bank value-sorting deliberately broken; falls back to catalog-ID order
- Status: confirmed · Severity: P3 · Area: server-core
- Evidence: `server/src/com/openrsc/server/model/container/Item.java:187-189` — original
  price-based compareTo commented out ("had to be broken for now because the Item
  doesn't have a reference to the World"); compares catalog IDs instead.
- Repro: sort-by-value in bank; order is catalog-id, not value.
- Verify: sorted order matches item values (bank state via voidbot after sort).
- Log: 2026-07-02 seeded from survey.

### VS-010 — Ned-boat NPC visibility special case suspected inauthentic
- Status: reported · Severity: P3 · Area: server-core
- Evidence: `server/src/com/openrsc/server/GameStateUpdater.java:290` — "TODO: probably
  this is incorrect & should be removed. There are authentically 4 versions of the Lady
  Lumbridge interior..." guarding a continue that hides NED_BOAT without the ned_hired
  cache key.
- Repro: Dragon Slayer flow around Ned/Lady Lumbridge; compare against authentic RSC
  behavior (replay/wiki research needed).
- Verify: depends on authenticity ruling; voidbot npc-present checks at the boat.
- Log: 2026-07-02 seeded from survey.

### VS-011 — TeaVM web client: mobile taps mis-route through stale panel state
- Status: reported · Severity: P3 · Area: web-client
- Evidence: `memory/iphone_web_client.md` — "some mobile taps reach Java as
  mouseButtonClick after old panels miss lastMouseButtonDown; login/HUD fixes may need
  narrow web-mobile fallbacks". Physical-device QA is an unmet release gate
  (`docs/PRELAUNCH-QA-HANDOFF.md` Remaining Gates).
- Repro: iPhone-simulator scripts (`scripts/run-web-teavm-iphone-simulator.sh`,
  `scripts/smoke-web-teavm-*.sh`); exact failing taps need enumeration.
- Verify: smoke scripts green; final confirmation is physical-device →
  blocked(needs-device) for that last step.
- Log: 2026-07-02 seeded from survey.

### VS-012 — Threaded event mode breaks scenery when two players share it (flag-gated)
- Status: reported · Severity: P3 · Area: server-core
- Evidence: `server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java:37` —
  "currently also causes issues with scenery breaking from having two players accessing
  it"; only manifests with `WANT_THREADING__BREAK_PID_PRIORITY` enabled.
- Repro: first check whether any voidscape preset/config enables the flag. If nothing
  does and nothing will → `wontfix(flag unused)` with a warning comment at the flag.
- Verify: two concurrent voidbot sessions hitting the same scenery under the flag.
- Log: 2026-07-02 seeded from survey.

### VS-013 — voidbot can drop an NPC AoI frame under rapid region changes
- Status: confirmed · Severity: P3 · Area: tooling (voidbot)
- Evidence: `docs/bot-api.md` known limitation; mitigation = 3s recently-seen NPC cache
  (`tools/voidbot/voidbotd.py` ~L330-344) + caller-side retries. Matters because it makes
  the loop's own NPC-based verifications flaky.
- Repro: rapid region-crossing walks while tracking NPCs.
- Verify: stress walk shows no lost tracked NPCs (by server_index).
- Log: 2026-07-02 seeded from survey.

### VS-014 — Gnome ball tackle think-bubble missing (disabled by FIXME)
- Status: confirmed · Severity: P4 · Area: server-core
- Evidence: `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java:357-358` —
  thinkbubble call commented out: "//TODO: FIXME: solve the thinkbubble requiring context".
- Repro: gnome ball tackle; no bubble shown.
- Verify: bubble packet observed during tackle (voidbot events/messages; may need a small
  voidbot decoder addition to observe the bubble packet).
- Log: 2026-07-02 seeded from survey.

### VS-015 — Easter bunnies offer "come with me" when player already has the bunny foot
- Status: confirmed · Severity: P4 · Area: server-plugin (seasonal)
- Evidence: `server/plugins/com/openrsc/server/plugins/custom/minigames/estersbunnies/Bunny.java:54`
  — "TODO Fix this condition for next year. Bunnies should not give the comeWithMe
  option if the player already has the foot."
- Repro: have foot, talk to bunny → option still offered.
- Verify: voidbot dialog state with/without foot in inventory.
- Log: 2026-07-02 seeded from survey.

### VS-016 — TerritorySignupInterface handlers are TODO stubs
- Status: reported · Severity: P4 · Area: client-ui (inherited)
- Evidence: `Client_Base/src/com/openrsc/interfaces/misc/TerritorySignupInterface.java:69-91`
  — signup/drop/switch-team handlers all "// TODO".
- Repro: first determine whether any voidscape config can reach this interface. If
  unreachable → `wontfix(unreachable upstream feature)`.
- Verify: n/a until reachability decided.
- Log: 2026-07-02 seeded from survey.

### VS-017 — updateQuestRewards early-returns in OpenPK mode ("fix for now")
- Status: reported · Severity: P4 · Area: client-ui
- Evidence: `Client_Base/src/orsc/mudclient.java:31862` — "//TODO: fix for now";
  updateQuestRewards() returns immediately under S_WANT_OPENPK_POINTS, leaving
  questGuideRewards unset in that mode.
- Repro: only under OpenPK config — check if voidscape ever runs it; likely wontfix.
- Verify: n/a until mode relevance decided.
- Log: 2026-07-02 seeded from survey.

### VS-018 — Grog stat effects suspected wrong (regional-ale consistency)
- Status: reported · Severity: P4 · Area: server-plugin
- Evidence: `server/plugins/com/openrsc/server/plugins/authentic/itemactions/Drinkables.java:696`
  in handleGrog() — "// XXX: all needs checking - probably should be the same as other
  regional ales". Corroborating: at :698-699 the comment says "remove constant 6
  strength" but the code substats ATTACK by 6 — a comment/code mismatch to check first.
- Repro: drink Grog; compare stat deltas against other regional ales / authentic values.
- Verify: voidbot skills state before/after drinking.
- Log: 2026-07-02 seeded from survey; narrowed to Grog + the attack-vs-strength
  mismatch on verification pass.


### VS-027 — Rapid admin `::commands` drop under fast fire (re-scoped; deposits are fine)
- Status: confirmed · Severity: P3 · Area: server-core (admin-command path) / QA-ergonomics
- Evidence: quantified by S-D — 299-iteration spawn+deposit loop: spawn successes
  exactly one tick apart, **1 of 295 deposits landed**; ≥1.2s spacing → 100%. S-C:
  `::item` at 0.25s = 0/29, at 1.1s = 25/25. Admins are exempt from CommandHandler's
  1000ms limit (`CommandHandler.java:191`) → mechanism is per-tick packet processing,
  not the command limiter. Sighted independently by 7 suites (dropped ::setstat,
  ::teleport, ::rested, deposits). `tmp/qa/S-D/vs008-fill.sh`, `10/11-events-fill.json`.
- Repro: `::item 20 1` ×12 with no pacing → ~2 land; ×6 at 1.2s pacing → 6/6.
- Verify: rapid admin commands all take effect (or fail visibly), OR accept as
  admin-only with the documented pacing workaround.
- Log: 2026-07-02 wave-1 (7 suites). **2026-07-02 RE-VERIFIED with the fixed decoder
  (post VS-020): the wave's headline was wrong.** Rapid BANK DEPOSITS land 5/5 — the
  "deposits drop" claim was a downstream artifact (spawns dropped → nothing to deposit)
  amplified by the broken inventory decoder. Rapid admin `::item` genuinely drops
  (2/12 no-pacing, 6/6 at 1.2s) — real but ADMIN-COMMAND-SPECIFIC and rate-dependent.
  Root cause is NOT the packet queue (drains fully per tick) nor CommandHandler's 1s
  delay (admins are exempt, `CommandHandler.java:191`) — mechanism needs runtime
  instrumentation. **Downgraded P2→P3**: players can't spam admin commands and are
  subject to the intended 1s delay anyway; QA workaround (≥1.2s between admin commands)
  already documented in the campaign doc. Not worth a risky server change now. 2026-07-02
  wave-2 S-D2 CONFIRMED deposits are fine: 6 and 20 rapid deposits all landed exactly.


### VS-034 — Abandoned/cancelled NPC dialog leaves the NPC silently un-talkable ~30-60s
- Status: confirmed · Severity: P3 · Area: server-core (dialog busy-state)
- Evidence: S-G — walk away from Fred mid-menu → two 12s-timeout re-talks; Cook after
  menu-cancel → 20s+ timeouts, recovered ~50s. No "X is busy" feedback. Contrast:
  attack-while-menu-open closes cleanly and NPC is immediately re-talkable.
- Repro: talk to Fred (77) → walk away with menu open → re-talk → timeout.
- Verify: NPC promptly re-talkable after abandon/cancel, or an explicit busy message.
- Log: 2026-07-02 wave-1 (S-G): players will read this as "NPC broken".
  2026-07-03 reproduced live + machinery mapped (timeboxed stop, no code change):
  (1) First re-talk after a goto-abandon times out silently (no message, no npc_say).
  (2) BUT the event log shows the re-talk actually re-ran Fred's full dialogue and
  opened its menu — which something then CLOSED ~5s later (unexplained dialog_close;
  suspect the cancelled first multi()'s late cleanup/hideMenu stomping the new menu).
  (3) Machinery: every non-excluded inbound packet runs
  PayloadProcessorManager.checkIfShouldCancelMenu → player.cancelMenuHandler()
  (menuHandler=null, busy=false, canceledMenuHandler=true); PluginHandler:317 stops
  the old dialogue plugin only when the NEXT plugin launches AND
  `getScriptContext().getInteractingNpc() != null` — so menus from OBJECT scripts
  (cook's range batch menu, VS-042 observation) are NEVER stopped and block the
  player until script timeout. (4) multi() exits only on option/attack/5min/
  other-player-wants+20s/menuHandler==null (Functions.java:319-384). Next step: tick-
  trace the double-cancel race (old plugin's resetMenuHandler vs new menu), and extend
  the PluginHandler stop to non-NPC dialog scripts. Also note `::spawnnpc 77 1 1`
  spawns despawn quickly — spawn fresh per attempt when reproducing.

### VS-035 — Void Rush queue: no in-game cancel; one-entry-per-IP starves solo/single-IP play
- Status: confirmed · Severity: P3 · Area: server-plugin (minigame)
- Evidence: S-I — join queue via herald 839 → lobby (505,88), MIN_PLAYERS=2, no cancel
  option/exit object; logout-only escape (relog message confirms return);
  `hasSameIpQueuedOrActive()` (`VoidRushMinigame.java:146-172`) limits one entry per
  IP — also makes localhost fleet QA of this minigame impossible even after E6.
- Repro: `::teleport 113 318` → `npc-talk --id 839` → `menu-reply 0` → stuck in lobby.
- Verify: in-game cancel path + waiting feedback exist.
- Log: 2026-07-02 wave-1 (S-I). 2026-07-02 Ryan ruled per-IP restriction intended —
  scope narrowed to the missing cancel/feedback only; fleet QA of this minigame is
  permanently blocked (single host), needs manual verification.

### VS-036 — Gnome agility course rope swing unresponsive; course impassable
- Status: reported · Severity: P3 · Area: server-plugin
- Evidence: S-E — ropeswing (id 650 @ 689,2395, dir 6): object-action from 4 positions
  × both options → nothing (no message/movement/xp) while the other 6 obstacles all
  respond (+15 xp each); gap tiles unwalkable so the 150-xp completion bonus is
  unreachable. Could be a voidbot quirk with diagonal dir-6 scenery — no scenery state
  query exists to confirm object presence (gap). `tmp/qa/S-E/32..35-rope.json`.
- Repro: `::teleport 689 2394` → `object-action 689 2395` → silence.
- Verify: confirm the object exists (workbench eyes or new scenery query), then swing
  moves player to (685,2396) + xp; full course completable.
- Log: 2026-07-02 wave-1 (S-E).

## Decoder-artifact re-verification — DONE (wave 2, 2026-07-02)

Wave 2 re-ran S-C/S-D on the fixed decoders and settled the wave-1 artifacts:
- VS-027 "deposits drop" → **WAS ARTIFACT**. 6 and 20 rapid deposits all landed exactly.
- "item-command acts on last stack" → **WAS ARTIFACT**. It honors the requested slot now.
- VS-008 (>256 bank) → **decoder half fixed** (full-list display correct), **server half
  REAL** (single-slot SEND_BANK_UPDATE truncates slot to one byte) — see VS-008.
- VS-021 (un-noted withdraw) → **REAL, now fixed** (confirmed delivering).
- "over-deposit no-op" → still UNVERIFIED (bank wouldn't open via banker dialogue — VS-046).
- NEW: the fixed decoder still desyncs for item ids 44/45 (patch-def mismatch) → VS-041.

## Design rulings (Ryan) — intended behavior, do NOT re-file

- Runecrafting disabled (`want_runecraft: false`) — intended, including for launch
  (ruled 2026-07-03; removed from Intake).
- Noted items are ALWAYS lost on death, like stackables (ruled 2026-07-03; enforced
  server-side by the VS-003 fix). The items-kept-on-death preview interface stays
  disabled in voidscape presets.

- Harvesting skill disabled on the launch preset (`want_harvesting: false`) — intended.
- Bone-bury prayer XP rides `combat_exp_rate` (10×, i.e. 37.5/bone) — intended.
- `bank-deposit-all` deposits wielded equipment too — intended.
- Void Rush one-entry-per-IP — intended anti-abuse (QA consequence: this minigame is
  permanently untestable from a single-host fleet; needs manual/multi-host QA).
- Combat XP per NPC capped at max-HP-worth (0 XP killing blow after retreat/regen) —
  intended/authentic.
- Portal `launch-live` endpoint has no date gate — discretionary by design (admin-gated).

### VS-050 — Backfill dry-run preview reports queued:0/existing:0 instead of previewing
- Status: blocked(WIP-collision — handler lives only in the uncommitted Resend queue
  workstream) · Severity: P4 · Area: web-portal (admin API)
- Evidence: S-W probe 30 — `POST /api/admin/emails/signup-confirmations {dryRun:true}`
  returns `eligible:4, selected:4, queued:0, existing:0`. Code-confirmed in
  `queueAdminBulkEmail` (dev-server.mjs, WIP hunk ~1110-1182): the dry-run branch
  returns literal `queued: 0, existing: 0`, while the real branch walks `selected`
  counting created-vs-existing. The "preview" can't tell an admin how many emails a
  real run would actually send vs skip.
- Fix sketch (for whoever commits the Resend WIP): in the dry-run branch, compute the
  would-be created/existing split (peek `queueAccountEmail`'s dedup criteria) instead
  of hardcoding 0/0.
- Verify: dry-run against a store where some accounts already have events →
  queued+existing preview matches the subsequent real run's counts.
- Log: 2026-07-03 triaged from Intake (S-W). Blocked until the Resend workstream
  commits — my hunk can't be staged without folding in Ryan's WIP (§7).

### VS-038 — Death Match has no voluntary forfeit (logout blocked, ladder outside arena)
- Status: reported · Severity: P3 · Area: server-plugin (minigame)
- Evidence: from the VS-026 diagnosis — the forfeit ladder (object 5 at 984,668) is in
  the gatekeeper chamber OUTSIDE the sealed arena (unreachable mid-fight), and
  `blockPlayerLogout` returns true while a session exists
  (`DeathMatchArena.java:304-307`). With VS-026 fixed a fought match ends normally
  (win/loss), so this is no longer a hard trap — but a player who wants to bail mid-fight
  can only do so by dying. Defense-in-depth, deferred from VS-026 to keep that fix minimal.
- Repro: enter a fight, try to logout → blocked; no reachable exit inside the arena.
- Verify: either logout cleanly forfeits (run onPlayerLogout's session.cleanup and let
  logout proceed) or a reachable in-arena exit wired to `session.finishLoss` exists.
- Log: 2026-07-02 filed from VS-026 root-cause (agent af575887).

### VS-040 — Bank web-small panel still overlaps the chat strip by ~22px at Classic
- Status: RESOLVED 2026-07-02 by the Void Glass bank rebuild (see VS-044 in the Fixed
  archive) — the skin-on bank is now HUD-safe at every viewport; the CustomBankInterface
  layout this described is dormant (`want_custom_banks: false`). Kept for history.
- Severity: P4 · Area: client-ui
- Evidence: after the VS-030 y-clamp, the Classic 512x346 bank panel fits the viewport
  (panelY 8 + height 326 = 334) and no row is clipped, but the inventory grid bottom
  (~328) still sits below voidscapeChatTabTop (306), so ~22px overlaps the chat strip.
  Fully clearing it needs a web-small-specific slot height (< BANK_SLOT_HEIGHT_ANDROID,
  which is shared with Android) or dropping one inventory row — a larger change kept out
  of the minimal VS-030 fix.
- Repro: workbench at index 5 (Classic), open bank, observe the inventory row over the
  chat tabs.
- Verify: bank inventory grid bottom <= voidscapeChatTabTop; workbench screenshot.
- Log: 2026-07-02 split from VS-030 (residual third symptom).

### VS-061 — Android APK disconnects after minimizing/backgrounding
- Status: verified · Severity: P1 · Area: Android APK / server session
- Evidence: Discord report 2026-07-06: "Everytime I minimize" with the Android
  in-game screen showing "Connection lost! Please wait... Attempting to re-establish."
  Code evidence: the native Android APK identifies itself in the login limitations byte,
  and the server has a channel-close session-resume path, but
  `RSCConnectionHandler.CHANNEL_CLOSED_RECONNECT_GRACE_MS` is only 5s for every client.
  A normal phone app switch can freeze the client longer than that, causing the server
  to unregister the player before the retained Android client can reconnect. Android
  backgrounding can also freeze the socket without closing the channel, so the shared
  30s `GameStateUpdater` client-activity timeout can unregister the player before
  the APK resumes. Reopened 2026-07-08 from Ryan's Android APK report: "it still dcs
  when you switch apps", with an in-game screenshot again showing the reconnect overlay.
- Repro: Android authenticated lifecycle smoke or physical Android: log in, background
  the game for longer than 30s, resume, observe disconnect/login reset or failed
  session resume.
- Verify: `scripts/build.sh`; `scripts/build-android.sh --debug`; Android lifecycle
  smoke backgrounds/resumes and stays in game or sees "Your previous session has been
  resumed." Physical-device confirmation remains a release gate.
- Fix: native Android APK clients now get both a 120s channel-close reconnect grace and
  a 120s silent client-activity timeout while desktop/web clients keep the existing
  5s/30s behavior. The shared client now only sends the Android limitations bit for
  native Android, so Android-profile `/play` does not silently inherit APK reconnect
  grace. Reopened fix: custom login now preserves the reconnect byte on `LoginRequest`
  and tags the request as Android from either the trailing limitations byte or the
  existing encrypted `Android/` login-details fallback. A valid native Android reconnect
  can reclaim its own prior Android session across IP changes or while the old socket
  still appears active, and the server detaches/closes the stale channel while updating
  logged-in IP bookkeeping. The native APK also marks `GameActivity` background/resume
  state through `ClientPort`; while backgrounded, the shared client skips socket polling
  and resets its read-timeout counter, and for the short foreground-resume window it
  suppresses the reconnect text box because Android may close the old socket even though
  the server accepts an immediate session resume.
- Verified 2026-07-06: `scripts/build.sh` green before Android smoke; after restarting
  the local server on the current build, `scripts/android-smoke.sh --no-build
  --only-auth-lifecycle --out /tmp/voidscape_android_vs061_lifecycle_native_only_rerun`
  passed for `wbtest` against `10.0.2.2:43596`, including a 35s HOME background,
  resume, duplicate relaunch, logout, and post-logout keyboard entry.
  `scripts/build-android.sh --debug` passed again after the final Android input build.
  Physical-device confirmation remains a release gate.
- Log: 2026-07-06 triaged from Discord screenshots, fixed, and emulator-smoke verified.
  2026-07-08 reopened after real-device recurrence; inspect Android lifecycle handling,
  server Android grace detection, and whether the app is still closing/tearing down the
  shared client on `Activity` stop despite the server-side grace. 2026-07-08 code fixed
  server-side active/stale-channel and IP-change Android reconnect bypasses, plus
  login-details fallback for same-version APKs missing the trailing Android bit.
  `scripts/build.sh` and `scripts/build-android.sh --debug` passed. Focused lifecycle
  smoke installed the APK, logged in `wbtest`, and captured in-game HUD under
  `tmp/vs061-android-lifecycle-reopen`; server log showed `Login details for wbtest:
  Android/`. Smoke did not reach HOME/resume because it timed out waiting for Settings
  `settingTab=1` while the client reported `settingTab=0`. Manual emulator follow-up
  reproduced that the APK still painted the reconnect overlay after resume, then verified
  the final client-side fix at `tmp/vs061-manual-switch-after-overlay-suppress`: after
  login, switching to Android Settings for 30s, and relaunching Voidscape, `GameActivity`
  was foreground, `wbtest` remained online (`549 607 1`), and `04-after-resume.png`
  showed the game view with no "Connection lost" overlay. A hidden successful reconnect
  response (`86`) can still occur because Android may close the background socket;
  Ryan's physical Android retest also passed, confirming the real app-switch path.

### VS-062 — Android one-finger camera drags also zoom
- Status: fixed · Severity: P2 · Area: Android APK input
- Evidence: Discord report 2026-07-06: "2 finger zoom better cuz with 1 when you try to
  rotate camera it zooms too if bad angles ... If you only trying to rotate." Code
  evidence: `InputImpl.onScroll()` updates `C_LAST_ZOOM` and camera rotation from the
  same single-finger drag, so diagonal rotate gestures can change zoom.
- Repro: Android authenticated zoom/camera smoke or device: drag diagonally in the
  viewport with swipe zoom enabled; observe both camera rotation and `C_LAST_ZOOM`
  changing.
- Fix: native Android `InputImpl` now routes scale gestures through
  `ScaleGestureDetector` and reserves one-finger drags for rotate/pitch/scroll; the
  Android Options text says "Pinch to Zoom." Pinch zoom keeps the old scrollable
  interface and bank guards so it cannot zoom the world behind open menus.
- Verify: `scripts/build-android.sh --debug`; physical Android: one finger rotates/
  pitches without changing zoom, and a two-finger pinch changes `C_LAST_ZOOM`.
- Log: 2026-07-06 triaged from Discord screenshots; code fixed and APK build green.
  Focused zoom smoke `scripts/android-smoke.sh --no-build --only-auth-zoom --out
  /tmp/voidscape_android_vs062_zoom_no_fake_pinch` logs in, captures the in-game zoom
  baseline, proves a one-finger viewport drag does not emit `ANDROID_SMOKE_ZOOM`, and
  exits cleanly with the Android grace-aware offline timeout. ADB cannot synthesize a
  reliable real two-finger pinch here; strict pinch assertion is only enabled by
  `ANDROID_SMOKE_MANUAL_PINCH_SECONDS` plus `ANDROID_SMOKE_REQUIRE_PINCH=1` on a
  physical device. Slow pinch deltas also keep accumulating instead of being consumed
  at zero distance.

### VS-063 — Android portrait right-side panels were shifted by a ghost side rail
- Status: verified · Severity: P2 · Area: Android APK UI
- Evidence: Discord screenshot 2026-07-06: right-side Options side view is cut off in
  portrait. Code evidence: the prior fix reserved 76px for phone portrait side-rail
  panels, but the native Android APK never draws that rail because
  `voidscapeUseMobilePanelShell()` is false and `drawVoidscapeMobilePanelRail()` has no
  native caller. That ghost reserve shifted right-side panels left over empty space and
  made the screenshot look like the side view was cut off.
- Repro: Android portrait, open the Options side panel, observe the panel content or
  tab row shifted off its intended top-tab anchor.
- Verify: `scripts/build-android.sh --debug`; authenticated Android settings smoke
  screenshot/log shows Options anchored by the existing top-tab path with no empty
  side-rail strip.
- Fix: the phone portrait side-rail reserve now only applies when the mobile panel shell
  is actually active; native Android keeps the normal top-tab panel anchor. The
  side-panel connector/drawing path remains limited to real mobile-shell side-key tabs.
- Verified 2026-07-06: `scripts/android-smoke.sh --no-build --only-auth-settings --out
  /tmp/voidscape_android_vs063_settings_no_ghost_rail` passed, and
  `/tmp/voidscape_android_vs063_settings_no_ghost_rail/99-auth-settings-open.png` shows
  the Options panel fully inside the portrait viewport without a blank 76px rail strip.
- Log: 2026-07-06 triaged from Discord screenshot, fixed, and emulator-smoke verified.

### VS-064 — "Skilling 2x still" report needs a concrete symptom
- Status: reported · Severity: P3 · Area: progression / XP rates
- Evidence: Discord report 2026-07-06 includes "And the Skilling 2x still" without the
  rest of the symptom. Current in-tree docs/config now say normal Voidscape target
  rates are 10x combat / 1.5x base skilling, subscription adds +1x, and starter paths
  grant selected 2x boosts below level 50, so the phrase alone is ambiguous between a
  stale expectation, a starter-path boost, subscription math, or a broken
  multiplier/display.
- Repro: needs the affected account, skill/action, expected XP, observed XP/message,
  and whether the starter path or subscription bonus was active.
- Verify: isolate one skilling action with voidbot/server logs and compare base XP,
  configured `skilling_exp_rate`, subscription, rested XP, wilderness/skull, and
  starter path bonus.
- Log: 2026-07-06 logged from Discord screenshot; not changing XP logic without a
  specific failing observation.

### VS-065 — Android portrait exposes black scene clear above the horizon
- Status: verified · Severity: P2 · Area: Android APK UI
- Evidence: Ryan screenshot 2026-07-06 shows a large pure-black rectangle between the
  top menu tabs and the rendered world in the native Android emulator. Code evidence:
  `drawGame()` clears the whole game framebuffer with `blackScreen(true)` before
  `Scene.endScene()`. The old RSC camera/projection exposes empty headroom above the
  horizon on tall portrait framebuffers, so that cleared area stays visible as a giant
  black block.
- Repro: native Android portrait, log in near Void Island, observe the upper scene area
  above the water/terrain horizon is pure black.
- Verify: Android authenticated chat-send/settings screenshot in portrait shows the
  empty upper scene area painted as a dark Voidscape backdrop instead of a pure black
  rectangle; `scripts/build.sh`; `scripts/build-android.sh --debug`.
- Fix: native Android portrait now paints a lightweight dark Voidscape scene backdrop
  immediately after the game-scene clear and before `Scene.endScene()` draws terrain,
  sprites, and overlays. Projection, click targeting, desktop, web, Android landscape,
  packets, opcodes, and gameplay state are unchanged.
- Verified 2026-07-06: `scripts/build.sh` passed, `scripts/build-android.sh --debug`
  passed, and `scripts/android-smoke.sh --no-build --only-auth-chat-send --out
  /tmp/voidscape_android_vs065_backdrop_chat_send_v2` passed. Screenshot
  `/tmp/voidscape_android_vs065_backdrop_chat_send_v2/65-auth-before-chat-send.png`
  shows the old black area painted as a dark blue backdrop; a pixel sample of the old
  black-block rectangle reported `black_fraction: 0.0`.
- Log: 2026-07-06 triaged from Ryan emulator screenshot; fixing with a native-Android
  portrait scene backdrop rather than changing projection/clipping math. 2026-07-06
  fixed and smoke-verified.

### VS-067 — Aggressive NPCs can immediately re-aggro after player retreat
- Status: verified · Severity: P2 · Area: server-combat / NPC aggro
- Evidence: Ryan reported 2026-07-07 that Void Dungeon NPCs instantly attack again
  after retreating from combat, despite the expected retreat cooldown. Code evidence:
  `WalkRequest`/`AutoWalkEvent` stamp `Player.setRanAwayTimer()` when a player legally
  retreats from NPC combat, but shared `NpcBehavior.canAggro()` only checks
  `combatTimer` and never consults `Player.canBeReattacked()`. The local upstream
  snapshot at `fc74d38e2e` has the same omission in `NpcBehavior`, so this is inherited
  OpenRSC behavior exposed by Voidscape's dense Wilderness/Void Dungeon packs rather
  than a Void NPC definition-only defect.
- Repro: enter combat with any aggressive NPC, legally retreat after the three-round
  gate, remain inside another aggressive NPC's aggro radius, and observe NPC auto-aggro
  can resume before the player's `pvp_reattack_timer` window is respected. Ordinary
  combat completion should remain fast for AFK training.
- Verify: `scripts/build.sh`; with voidbot, retreat from an aggressive NPC and confirm
  no NPC auto-aggro starts until `ranAwayTimer + pvp_reattack_timer` has elapsed, while
  killing/finishing an NPC still allows normal aggressive NPC training flow.
- Log: 2026-07-07 triaged from Ryan report; agents assigned current-code, upstream, and
  scope checks. Fix plan: make shared NPC auto-aggro respect the target player's existing
  retreat reattack immunity unless the NPC is an explicit force-chase target. 2026-07-07
  Ryan clarified ordinary combat completion should stay quickly eligible for AFK XP flow;
  narrowed fix to `Player.canBeReattacked()`/`ranAwayTimer`, not a blanket post-kill
  cooldown. 2026-07-07 code updated and `scripts/build.sh` passed; Ryan live-tested the
  rebuilt server/client in the Void Dungeon and confirmed the retreat behavior works great.

### VS-068 — World-map autowalk could OK-then-stall and lacked route-level QA
- Status: verified · Severity: P2 · Area: server-pathfinding / voidbot QA
- Evidence: Ryan reported the world autowalker "sometimes works, sometimes doesnt",
  including random stops and route-efficiency concerns. Existing Intake had "goto to
  unreachable tile stalls silently mid-route; reissue no-ops while walk-step crosses
  fine". Code archaeology found two concrete risks: `WORLD_WALK_REQUEST` did not clear
  an existing normal walking queue or interface/action state like `WalkRequest` does,
  so an OK route could wait behind stale movement; and `AutoWalkEvent` mid-route
  cancellation paths cleared the event without sending a failure route ack. QA gap:
  voidbot sent real `WORLD_WALK_REQUEST` packets but did not decode
  `SEND_WORLD_WALK_ROUTE` opcode `100`, so automated tests could only infer route
  behavior from movement.
- Repro: pre-fix code evidence plus live probes. `tmp/autowalk/routes/short-route.json`
  proved route ack decoding after tooling was added; `tmp/autowalk/routes/unreachable-0-0-02-route.json`
  proved unreachable targets now return `ok:false reason=1` and do not move; the new
  smoke's stale-walk case issues `walk-step 145 648` then immediately `goto 116 648`
  and requires arrival within 15 seconds, catching the old delayed-queue behavior.
- Verify: `scripts/build.sh`; `python3 -m py_compile tools/voidbot/protocol.py
  tools/voidbot/voidbotd.py tools/voidbot/cli.py`; `VOIDBOT_CTRL_PORT=18931
  OUT=tmp/world-autowalk-smoke scripts/smoke-world-autowalk.sh` passed 8/8; and
  `VOIDBOT_CTRL_PORT=18933 VOIDBOT_USER=qabot04 OUT=tmp/world-autowalk-smoke-medium
  WORLD_AUTOWALK_LONG=1 scripts/smoke-world-autowalk.sh` passed 10/10 with a
  79-tile medium route. Follow-up verify after vendoring the route graph:
  `VOIDBOT_CTRL_PORT=18936 OUT=tmp/world-autowalk-routes
  scripts/check-world-autowalk-routes.sh` passed 8/8; it caught and then verified the
  fix for a Lumbridge -> Draynor repeated-tile loop, with the final route at 182 tiles
  / 1.309x Chebyshev lower bound.
- Fix: `WorldWalkRequest` now cancels prior autowalk, resets action/interface state,
  and clears the old walking queue before installing a fresh `AutoWalkEvent`. Running
  autowalks send `SEND_WORLD_WALK_ROUTE` failure acks for busy/no-path cancels before
  clearing themselves. voidbot now exposes `state world-walk-route` and
  `world_walk_route` events, `scripts/smoke-world-autowalk.sh` is the reusable
  walking regression harness, and `scripts/check-world-autowalk-routes.sh` is the
  route-quality harness. `/Users/s/RSCRevolution2/waypoints.rev` was vendored to
  `server/conf/server/data/waypoints.rev` so clean checkouts load the efficient-route
  guide; `WorldPathfinder` prunes repeated-tile cycles after waypoint stitching.
- Log: 2026-07-07 triaged from Ryan report and existing Intake; agents split code,
  waypoint-data, and QA-matrix archaeology. Local server confirmed `WaypointGraph`
  loads `/Users/s/RSCRevolution2/waypoints.rev` (16,038 nodes, 114,072 directed edges)
  when present; follow-up vendored the same graph into the repo and boot confirmed
  `Loaded waypoint graph from conf/server/data/waypoints.rev`. 2026-07-07 fixed, built,
  route-quality-verified, and smoke-verified; medium route count 79 and arrival passed
  without idle/stall.

## Watch list — not open bugs, but recurrence risks and burned areas

- **smoke.sh first run after a fresh server restart can fail 1-3 checks; rerun is
  clean.** Seen twice on 2026-07-03 (VS-053 verify: 1 fail; VS-056 verify: 3 fails;
  both 26/26 on immediate rerun, same build). Consistent with warm-up (NPC spawns
  settling / VS-013 AoI class). If a post-restart smoke fails, rerun before
  diagnosing; if a THIRD instance shows up, file it as a bug and capture the failing
  check names.

- **`server/inc/sqlite/preservation.db` is currently modified in the working tree**
  (+127KB, runtime side effect of running the server). Never commit it (hard rule 4).
  The dev DB itself is expendable: running the server mutates it, `scripts/reset-db.sh`
  and `git checkout` on it are always allowed (BUGFIX-LOOP §8) — just re-provision the
  wbtest account afterwards if needed (BUGFIX-LOOP §4).
- **Cert renewal breaks /play websocket**: if the service user loses read access to
  Let's Encrypt certs, the server throws `NullPointerException: sslContext` and /play
  dies (desktop TCP keeps working). Re-check ACLs after every renewal
  (`memory/iphone_web_client.md`).
- **Server-driven dialog dismissal**: dismissing a server input box without a reply
  soft-locked players for 60s (fixed in 2db5712). Any new server-driven dialog must send
  a reply on dismiss.
- **Movement feel is a burned area**: client walk prediction was added and fully removed
  (b16713e → 69b5683) because it felt unnatural. Don't reintroduce client-side movement
  prediction casually.
- **Physical-device QA gates unmet**: Android + iPhone physical passes are still open
  release gates (`docs/PRELAUNCH-QA-HANDOFF.md`). Simulator proof is not sufficient.

---

## Fixed archive

_(entries move here when `verified`; find each fix via its subject — `git log --grep VS-NNN`)_

### VS-071 — Ardougne Void Rift sits outside the useful market area (FIXED)
- Status: verified · Severity: P3 · Area: server-content / traversal
- Evidence: Ryan reported 2026-07-07 that the Ardougne Void Rift was in a random
  caged-off spot and should be in the middle of Ardougne market. Code/data evidence:
  `VoidRift` and `SceneryLocsVoidRift.json` put the Ardougne hub at `(591,621)` with
  arrivals at `(588,621)`, while the Ardougne market stall cluster is around
  `(544..566,583..599)`.
- Repro: source/data inspection confirmed the mismatch; local stall locs place the
  market ring around baker/spice/fur/gem/silver/silk stalls, not near `(591,621)`.
- Fix: moved the Ardougne Void Rift object to `(552,592)` and changed the Ardougne
  arrival tile to `(552,594)`, central to the market cluster without placing the portal
  directly on a stall or NPC start.
- Verified 2026-07-07: JSON validation passed for `SceneryLocsVoidRift.json` and
  `scripts/build.sh` passed. A rebuilt local server loaded 7 Void Rift scenery entries.
  voidbot opened the Falador rift `(316,552)`, selected `Ardougne`, arrived at
  `(552,594)`, then used the moved Ardougne rift `(552,592)` and saw the normal network
  menu `Void Enclave`, `Edgeville`, `Varrock`, `Falador`, `Stay here`. Evidence:
  `tmp/vs071-ardougne-rift.txt`.
- Log: 2026-07-07 filed from Ryan's report, fixed, build-verified, and
  voidbot-verified.

### VS-070 — Protect from Magic blocks player spells instead of NPC-only magic (FIXED)
- Status: verified · Severity: P2 · Area: server-combat / prayer
- Evidence: Ryan reported 2026-07-07 that the newest level-43 prayer should not stop
  spells from players, only NPC/boss magic. Current client definitions already exposed
  "Protect from magic" at prayer id 14, but the server-side prayer definition/state was
  still capped at Protect from Missiles id 13, so the client/server prayer contract was
  inconsistent. Historical implementation in `a1b96685` applied Protect from Magic in
  `CombatFormula.applyMagicDamageReduction(damage, victim)` with no source/caster
  context, so player-cast spells against protected players would roll to zero too.
- Repro: code evidence was conclusive for the broken contract: client prayer list had id
  14, server `PrayerHandler` rejected ids above 13, and the old damage helper could not
  distinguish player-cast PvP spells from NPC/boss magic.
- Fix: restored server prayer id 14, activation/drain support, missile/magic mutual
  exclusion, and custom-client overhead bit `8`; made magic damage source-aware so
  player-origin spell paths pass a `Player` source and keep normal damage while ordinary
  NPC/boss magic can be blocked. The Void Knight boss' direct Fire Blast script also
  checks Protect from Magic before applying damage. Sir Charles is an explicit exception:
  both his lobby `DM_KING` id and arena `DM_KING_ARENA` id bypass Protect from Magic, so
  his Fire Blast remains unblockable. Client display text now says "Blocks most NPC magic
  attacks."
- Verified 2026-07-07: `scripts/build.sh` passed; source inspection shows
  `SpellHandler` and Void Colossus player spell paths pass `Player` sources, while DM
  King passes the boss `Npc` source and `CombatFormula` excludes Sir Charles ids from
  the protection block; `VoidKnightBoss.castIfReady` gates Fire Blast damage on
  `PROTECT_FROM_MAGIC`. Live voidbot verification deferred because
  `prayer-on/off`, `cast-player`, and `cast-npc` are not implemented yet.
- Log: 2026-07-07 triaged directly from Ryan's report, fixed, build-verified, and
  documented in `docs/DIVERGENCE.md`.

### VS-069 — Void Enclave rift can route to closed Void Colossus and Void Arena lacks an exit rift (FIXED)
- Status: verified · Severity: P2 · Area: server-content / minigame traversal
- Evidence: Ryan reported 2026-07-07 that the Void Rift portal inside the Void Enclave
  should lead to the Void Arena deathmatch lobby where Sir Charles is, but instead led
  to Colossus, which should be closed off. Code evidence: `VoidRift` and
  `VoidColossusArena` both claimed object `1306` at `(113,321)`, and
  `PluginHandler.handlePlugin` invokes every blocking `OpLocTrigger`, so enabling
  `want_void_colossus` let the unreleased Colossus handler run on the approved Void
  Arena rift. `SceneryLocsVoidColossusArena.json` also duplicated that hub rift. The
  Void Arena lobby had a bank chest but no portal/rift object wired to `VoidArena.leave`.
- Repro: conclusive code evidence plus live report; local `want_void_colossus:false`
  masked the conflict, but the handler/data collision was deterministic when the
  Colossus gate was enabled or stale loc data existed.
- Fix: `VoidColossusArena` no longer claims the Enclave hub rift and
  `SceneryLocsVoidColossusArena.json` no longer duplicates `(113,321)`. `VoidRift`
  keeps `(113,321)` as the Void Arena entrance and now handles a new Void Arena lobby
  rift at `(600,2911)` by calling the existing `VoidArena.leave` validation. The beta
  guide labels `(113,321)` as the Void Arena rift instead of a Colossus rift.
- Verified 2026-07-07: post-fix `scripts/build.sh` passed; JSON validation passed for
  the touched loc files; rebuilt local server loaded `SceneryLocsVoidArena.json` with
  18 scenery locations. voidbot used object `1306` at `(113,321)`, accepted
  `Enter Void Arena`, arrived at `(600,2914)`, and observed Sir Charles id `862` in
  the lobby. voidbot then used the new object `1306` at `(600,2911)`, accepted
  `Return to Void Enclave`, and arrived at `(113,318)`. Evidence:
  `tmp/vs069-void-arena-rift.txt`.
- Log: 2026-07-07 filed from Ryan's report, code-confirmed, fixed, build-verified,
  and voidbot-verified. Active loop state remains VS-070 after Ryan's later redirect.

### VS-066 — Wilderness castle rats leaked outside their rooms (FIXED)
- Status: verified · Severity: P3 · Area: server-content / NPC locs
- Evidence: Ryan supplied live mobile screenshots on 2026-07-07 showing dungeon rats
  outside the Wilderness castle walls after the new AFK rat clusters shipped. Code/data
  evidence: the eight `id: 367` dungeon-rat locs centered at `(249,359)` and `(263,359)`
  had broad `11x11` roam boxes (`244..254/354..364` and `258..268/354..364`) that
  overlapped exterior tiles.
- Repro: inspect the rat loc records in `server/conf/server/defs/locs/NpcLocs.json`;
  the starts are in the intended rooms, but their min/max rectangles include exterior
  castle tiles visible in the screenshots.
- Fix: shrink the two rat-cluster roam boxes to tight room-sized bounds around their
  start tiles: `247..251/357..361` and `261..265/357..361`. NPC count, NPC ID, loot,
  Wilderness multiplier behavior, and client behavior are unchanged.
- Verified 2026-07-07: `scripts/build.sh` passed before the fix; JSON validation and
  cluster query confirmed all eight castle rats remain present with the tightened
  bounds. Live hotfix deploy copied the updated `NpcLocs.json`, restarted
  `voidscape.service`, and confirmed the service active with the expected Wilderness
  density boot log.
- Log: 2026-07-07 triaged directly from Ryan's screenshots as a loc-bound content bug,
  fixed by data-only roam-bound tightening, and deployed live after backup.

### VS-060 — Android `/play` could fall into non-phone mode with desktop-style browser UA (FIXED)
- Status: verified · Severity: P1 · Area: TeaVM web client / launch surface
- Evidence: Ryan reported that a friend on Android could reach `/play` after the APK
  link fix, but could not click anything and the client appeared to turn off. Live
  server logs around the report showed the account did enter the web game session and
  then reset the connection, pointing away from a download failure and toward the
  mobile web shell/input mode. Code inspection found the `/play/` runtime detector
  relied on phone UA or a small viewport; Android Chrome "Desktop site" and some
  desktop-style Android browser UAs can expose a large layout viewport while still
  reporting phone-sized touch hardware, causing the page to use tablet/desktop behavior
  instead of the Android-profile phone shell.
- Fix: `/play/` now falls back to physical `screen` dimensions when deciding whether a
  primary-touch device is phone-sized, so desktop-style UAs on phone hardware still get
  `mode: "phone"` and the Android-profile input path unless the URL explicitly asks for
  desktop/tablet mode. The portal also normalizes bare `/play/` mobile web-client URLs
  to `https://voidscape.gg/play/?phone=1` for `/api/public` and launch-live emails, so
  site links force the phone shell.
- Verified 2026-07-06: local targeted Playwright runtime probe passed desktop,
  iPhone, iPad, explicit override, and Android desktop-site cases; the Android
  desktop-site case resolved as `mode:"phone"` from `heuristic:phone-screen`. Focused
  portal probe with `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/` returned
  `https://voidscape.gg/play/?phone=1`. Deployed live after backup
  `/opt/voidscape/backups/play-android-phone-mode-20260706T134847Z`; live curl confirmed
  `/play/` contains the new detector and `/api/public` returns the forced phone URL;
  live Playwright probe against `https://voidscape.gg/play/` with a desktop-style Chrome
  UA and phone-sized touch screen resolved `mode:"phone"`, `touchProfile:true`, and no
  desktop launcher gate. `scripts/build.sh`, `scripts/test-portal-schema.sh`,
  `node --check`, `bash -n`, and `git diff --check` passed for the touched files.
  `scripts/test-portal-api.sh` still stops on the existing unrelated
  `snapshot endpoint should resolve the active title` failure before reaching this
  regression block.
- Log: 2026-07-06 triaged from Android `/play` report, hardened, deployed, verified.

### VS-059 — Public APK/launcher downloads could advertise inferred HTTP origins (FIXED)
- Status: verified · Severity: P2 · Area: web-portal / launch surface
- Evidence: user screenshot from Android Chrome on `voidscape.gg/#account` showed the
  APK download gate "File can't be downloaded securely" for the 17.30 MB Android APK.
  Live checks on 2026-07-06 showed the deployed `/downloads/android-apk` endpoint itself
  served over HTTPS and `http://` redirected to HTTPS, but code inspection found the
  launcher manifest generated absolute download URLs from inferred request origin and
  `/api/public` advertised launcher/APK downloads as relative paths. A reverse proxy
  missing `X-Forwarded-Proto`, or a visitor who initially lands on HTTP, could therefore
  hand Chrome an insecure-looking download target.
- Fix: when `PORTAL_PUBLIC_ORIGIN` is configured, the portal now treats it as the
  canonical origin for public download metadata and launcher-manifest URLs. Public rows
  now advertise `https://voidscape.gg/downloads/...`, and `publicOrigin()` no longer
  downgrades generated manifest/cache/self-update URLs to loopback HTTP when production
  has a configured public origin. The portal README documents this production
  requirement, and the portal smoke now asserts no available public download uses
  `http://`.
- Verified 2026-07-06: live curl confirmed current production APK and manifest are HTTPS;
  focused local public-mode probe with `PORTAL_PUBLIC_ORIGIN=https://voidscape.gg`
  returned HTTPS launcher/APK rows from `/api/public` and HTTPS `baseUrl`,
  `file.1.url`, cache-file URLs, and `launcher.url` from
  `/api/launcher/manifest.properties`; `scripts/build.sh` green. Full
  `scripts/test-portal-api.sh` still fails an unrelated existing snapshot-title
  assertion (`snapshot endpoint should resolve the active title`) before reaching the
  new HTTPS regression block.
- Log: 2026-07-06 triaged from user screenshot, hardened, focused-verified, documented.

### VS-058 — Mobile landscape stats menu inherited the desktop stats detail panel (FIXED)
- Status: verified · Severity: P2 · Area: client-ui / launch surface
- Evidence: Ryan reported the deployed mobile web client stats menu looked terrible in
  landscape, with APK likely affected too. Code inspection found the desktop stats
  layout gate only excluded `voidscapeClassicWebSmallHud()`, while Android-profile
  clients can use the Voidscape HUD skin without satisfying that small-web predicate.
- Fix: added a shared `voidscapeUseCompactStatsPanel()` gate and routed stats row
  height, desktop-detail eligibility, footer height, section sizing, lock placement, and
  skill-row rendering through it. Desktop remains on the full-height aligned detail
  layout; mobile web landscape and native Android stay on the compact skill list.
- Verified 2026-07-05: `scripts/build.sh` green; `scripts/package-web-teavm.sh
  --output-dir dist/web-teavm` green; `scripts/build-android.sh --debug` green; real
  TeaVM mobile-landscape Skills screenshot captured at
  `tmp/mobile-stats-menu-check-20260705-compact/mobile-landscape/04-skills.png`. The
  full menu sweep later failed on desktop canvas visibility after the mobile/tablet
  frames had already been captured, so the verified evidence is the targeted landscape
  frame plus shared Android-profile code path.
- Log: 2026-07-05 emergency launch-surface fix; documented in `docs/DIVERGENCE.md`.

### VS-057 — Live launcher client showed the legacy bank instead of Void Glass (FIXED)
- Status: verified · Severity: P1 · Area: client-ui / launch config
- Evidence: Ryan reported the freshly launched live desktop client still showing the old
  bank after the launcher downloaded `Open_RSC_Client.jar` client `10122` SHA-256
  `848ee7e7d86d1b55d74fada341aebb0032c413f6334bdf5b8490ab4b5b53115f`. Bytecode
  inspection confirmed that jar contained `BankInterface.renderVoidGlassBank()` gated on
  `C_CUSTOM_UI && !Config.isAndroid()`, live server bytecode sent `Player.getCustomUI()`
  in `SEND_GAME_SETTINGS`, and the live DB had no `custom_ui` overrides. The real fault
  was production config drift: `/opt/voidscape/server/local.conf` had
  `want_custom_banks: true`, so `CustomBankInterface.onRender()` used the legacy custom
  bank renderer before the superclass Void Glass path could run.
- Fix: `CustomBankInterface` now treats the legacy custom-bank renderer as active only
  when `S_WANT_CUSTOM_BANKS` is true and desktop `C_CUSTOM_UI` is false; its
  deposit/withdraw/hotkey paths use that same effective mode. Live config was reset to
  `want_custom_banks: false`; cache/update permissions were normalized so the portal
  manifest can read the full update channel.
- Verified 2026-07-05: `scripts/build.sh` green; deployed package
  `tmp/live-client-ui-configfix-20260705T044847Z`; hosted static manifest advertises
  client SHA-256 `978d487522f6e206f1a50d209ae744377f0dabf118ca1b92560f4584c2e4c9a7`;
  portal manifest sync and local launcher cache sync both pulled that jar; live server
  config reports `want_custom_ui: true` and `want_custom_banks: false`; Ryan opened the
  live bank after relaunch and confirmed "fixed." Production backup:
  `/opt/voidscape/backups/ui-configfix-20260705T044939Z`.
- Log: 2026-07-05 emergency launch-surface fix; documented in `docs/DIVERGENCE.md`.

### VS-056 — ::rested wording implied per-minute accrual; pool is per-second (FIXED)
- Status: verified · Severity: P4 · Area: server-core (message string)
- The status line said "one rested minute per offline minute" while accrual stores
  raw offline seconds (same 1:1 rate, wrong quantization implication); docs +
  original DIVERGENCE both say per-second (S-H F4). Message now matches.
- Verified 2026-07-03 live: `::rested` renders the per-second line
  (tmp/vs056/01-new-wording.txt); build green; smoke 26/26 (first post-restart run
  flaked 23/26, clean rerun — second sighting, Watch-listed).
- Log: 2026-07-03 triaged (S-H F4), fixed, verified, committed same day.

### VS-055 — ::queststage rejected its documented 2-arg reset form (FIXED)
- Status: verified · Severity: P4 · Area: server-plugin (admin command)
- Evidence: `::queststage qabot04 5` → usage error, though the usage string documents
  `(stage)` optional and the handler carried a dead `stage = 0` default branch behind
  an `args.length < 3` gate (tmp/vs055/01-usage-error.txt; S-G §2). resetquest/resetq
  aliases were equally dead.
- Fix: gate `< 3` → `< 2`; the reset-to-0 branch comes alive per the usage string;
  3-arg path unchanged.
- Verified 2026-07-03 live on quest 5: baseline 0 → 3-arg set 2 → query 2 → 2-arg
  reset → query 0 (tmp/vs055/02-verify.txt); build green; smoke 26/26. Fixture left
  at default.
- Log: 2026-07-03 triaged (S-G §2), repro'd, fixed, verified, committed same day.

### VS-054 — ::setstat natural order answered "Invalid name or player is not online" (FIXED)
- Status: verified · Severity: P3 · Area: server-plugin (admin command / QA tooling)
- Evidence: `::setstat attack 77` → misleading player-name error, stat unchanged
  (tmp/vs054/01-misleading-error.txt); non-numeric args[0] was only ever tried as a
  player name. Hit by S-I/S-B/S-E and the root of VS-047's smoke breakage.
- Fix: name-lookup catch falls back to the natural self-targeted "stat level" form
  when no online player matches args[0], it names a skill, and args[1] parses as a
  level; online players named like skills still win (existing presence heuristic).
  Documented forms unchanged; bad input keeps the old error.
- Verified 2026-07-03 live: natural order sets Attack 77 + success message;
  `::setstat 90 attack` and the player form unchanged (fixture restored to 90);
  bogus name keeps old error (tmp/vs054/02-verify-messages.txt); build green;
  smoke 26/26.
- Log: 2026-07-03 triaged (3 suite citations), repro'd, fixed, verified, committed
  same day. ::cinematic WIP untouched.

### VS-053 — ::spawnnpc silently ignored coordinate arguments (FIXED)
- Status: verified · Severity: P3 · Area: server-plugin (admin command / QA tooling)
- Evidence: `::spawnnpc 3 1 1 130 650` from (148,503) spawned the Chicken at the
  SENDER — the handler parsed only `[id] (radius) (time)`, dropping extra args and
  hardcoding the sender's tile (tmp/vs053/01-remote-spawn-ignored.txt). Bit QA twice
  (S-B F4, S-DEATH). Wave-1's "plain ::spawnnpc fails silently" settled as the
  VS-027 rapid-admin drop (works when paced, repro'd).
- Fix: optional trailing `x y` (both or usage message), `withinWorld`-validated
  ("Invalid coordinates", the ::teleport idiom), spawn + wander bounds centered
  there; success message reports the spawn point.
- Verified 2026-07-03 live: remote spawn present at (131,650) after teleporting
  there, none at the sender (tmp/vs053/02-*, 03-messages.txt); 4-arg → usage;
  (99999,99999) → Invalid coordinates; plain form unchanged; build green;
  smoke 26/26 twice (one unreproduced first-run flake, consistent with the
  documented VS-013 class).
- Log: 2026-07-03 triaged from Intake (2 datapoints), repro'd, fixed, verified,
  committed same day. Admins.java ::cinematic WIP untouched (own hunks staged).

### VS-052 — voidbot wait: raw KeyError on missing args; typo'd conditions burned the timeout (FIXED)
- Status: verified · Severity: P3 · Area: tooling (voidbot)
- Evidence: `wait xp-gained` without --skill → `KeyError: 'skill'` exit 1 (S-C F8;
  live repro tmp/vs052/01-prefix-keyerror.json); same for every wait's required args
  (`wait position` → `KeyError: 'x'`); typo'd condition (`wait bank-opne`) burned the
  full timeout before exiting 1.
- Fix: per-condition required-args table validated at the top of `Daemon.wait()`
  (plus npc-dead/npc-gone --id OR --server_index, and unknown-condition detection)
  returning `usage: ...` errors; cli.py maps `usage:` errors to exit 2 (extends the
  VS-025 "unknown command" mapping).
- Verified 2026-07-03 live on a restarted daemon: no-skill xp-gained → usage exit 2;
  bank-opne → usage exit 2 in 0.14s; missing-coords + neither-id branches exit 2;
  valid waits unchanged (match 0 / timeout 1); tests/smoke.sh 26/26; build green.
- Log: 2026-07-03 triaged from Intake (S-C F8), repro'd all three shapes, fixed,
  verified, committed same day.

### VS-051 — /api/accounts/google validated username before the credential (FIXED)
- Status: verified · Severity: P4 · Area: web-portal
- Evidence: S-W probe 36 — garbage Google credential → 400 `invalid_username`
  (misleading; the credential was the problem). `googleProfileFromCredential` called
  `requireReservationUsername` as its first statement, before any JWT check.
- Fix: credential validation (`invalid_google_token` / `verifyGoogleIdToken`) now runs
  first; username rules unchanged for authenticated requests. Regression probe added
  to test-portal-api.sh (garbage credential → 401; answered 400 pre-fix,
  tmp/vs051-prefix.log).
- Verified 2026-07-03: both hermetic portal batteries green post-fix
  (tmp/vs051-postfix.log, tmp/vs051-schema.log); build green.
- Log: 2026-07-03 triaged, repro'd via battery probe, fixed, verified, committed
  same day.

### VS-049 — Portal login had no throttle: unlimited password guessing per IP (FIXED)
- Status: verified · Severity: P2 · Area: web-portal (launch surface)
- Evidence: S-W probe — 12 straight failed passwords against
  `POST /api/accounts/login`, all 401, no slowdown/429. Code-confirmed: the login
  handler had no attempt-counting while the founder-reservation route above it used
  the full abuse-signal stack.
- Fix (dev-server.mjs): failed logins record a `login_failure_ip` abuse signal;
  non-local IPs exceeding `PORTAL_LOGIN_IP_FAILURE_LIMIT` (default 10) failures within
  `PORTAL_LOGIN_FAILURE_WINDOW_MINUTES` (default 15) get `429 rate_limited` — checked
  BEFORE password verification (no oracle: correct password also 429s while
  throttled). Loopback without XFF stays exempt (mirrors founder route; battery
  ergonomics). Gotcha handled: `updateStore` persists only on resolve, so the signal
  rides a returned marker and the 401 throws outside the transaction.
- Verified 2026-07-03: `scripts/test-portal-api.sh` green including new VS-049 probes
  (10×401 from synthetic XFF IP → 11th attempt 429 with the CORRECT password →
  different IP logs in fine → loopback exempt); `scripts/test-portal-schema.sh` green;
  `scripts/build.sh` green (no Java touched). Logs tmp/vs049-portal-api.log,
  tmp/vs049-portal-schema.log.
- Log: 2026-07-03 triaged from Intake (S-W), code-confirmed, fixed, verified,
  committed same day. Note: dev-server.mjs + test-portal-api.sh carry unrelated
  Resend-queue WIP — only the VS-049 hunks were staged.

### VS-048 — Bank over configured cap rejected ALL deposits, including zero-slot merges (FIXED)
- Status: verified · Severity: P3 · Area: server-core (bank container)
- Root cause: `World.java:162` hardcodes `maxBankSize = MEMBER_WORLD ?
  (WANT_CUSTOM_BANKS ? ItemId.maxCustom(=1608) : 192) : 48` (`bank_size` conf key is
  deprecated upstream); voidscape local.conf has `want_custom_banks: false` → live cap
  **192**. qabot04's 1607-stack QA bank (filled while a larger cap was in effect) made
  `canHold` compute `192 − 1607 ≥ required` → false for EVERY deposit — including
  merges into an existing stack, which need no new slot and which `Bank.add` itself
  accepts at any size. Intake's "noted confuses the math" guess was wrong (noted plays
  no role); datapoint (b) (new stack at 1607) is correct-by-config under cap 192 — the
  QA "/1608" denominator was an assumption.
- Fix: `canHold` returns true when `getRequiredSlots(item) == 0`; new-stack deposits
  keep the headroom check (still refused over cap). Aligns the predicate with add()'s
  actual acceptance; protects players stranded over-cap by any future cap reduction.
- Verified 2026-07-03 live on the fixture: pre-fix, 100-coin merge deposit refused
  (tmp/vs048/01-coin-deposit-events.json); post-fix it lands as slot-0 bank_update
  24050→24150, no new slot, inventory coins gone (tmp/vs048/03-*.json); 48× noted
  staff (needs new slot) still correctly refused (tmp/vs048/04-*.json);
  tests/smoke.sh 26/26.
- **Question for Ryan (open):** intended launch bank cap — authentic 192 (current,
  `want_custom_banks: false`) or 1608 (`ItemId.maxCustom`, what the QA fleet assumed
  and what Void Glass pages/search were exercised against)? If 192 stands, the
  1607-stack QA fixture can never re-deposit its withdrawn noted probe items.
- Log: 2026-07-03 triaged from Intake (2 datapoints), root-caused, repro'd, fixed,
  verified, committed same day.

### VS-003 — Items kept on death: noted always lost (CLOSED by ruling + server fix)
- Status: verified · Severity: P4→gameplay-rule fix · Area: client-ui + server-core · commits b6c8841f (client preview) + the VS-003 server commit
- History: the client preview's two original bugs (Protect Item ignored; stackable
  split as "1 kept") were fixed 2026-07-02 (b6c8841f). Wave-2 then found the preview
  is DORMANT in all voidscape presets and that the SERVER could keep a whole noted
  stack in a keep slot (S-DEATH live) — parked for Ryan's ruling.
- Ruling (Ryan, 2026-07-03): noted items are ALWAYS lost on death (like stackables);
  the items-kept preview interface stays disabled. Closes the noting-as-death-insurance
  loophole (whole noted stacks surviving death; PvP killers getting nothing).
- Server fix: `Inventory.dropOnDeath` keep-3 loop and Protect-Item fourth-slot block
  now refuse `getNoted()` items as well as `isStackable()` (the value map already keyed
  noted as always-lost -1; only the keep checks lagged). Matches the shipped client
  preview model exactly.
- Verified 2026-07-03 live on the S-DEATH fixture (qanpc1, non-priv, unskulled, 2 gear
  + noted Rune Plate Mail Legs ×3): after death the 2 gear are kept, the noted stack is
  gone and lies on the death tile (ground item, noted=True), hits restored;
  tests/smoke.sh 26/26.
- Log: 2026-07-02 client fix + reopen; 2026-07-03 ruled, server fixed, verified,
  committed. Preview interface remains dormant-but-correct by ruling.

### VS-032 — "Unobtanium"/force-drop was voidbot's stale login trailer, not a player loss risk (FIXED)
- Status: verified · Severity: P3 · Area: tooling (voidbot); server behavior confirmed correct
- Resolution: the shipped client HAS defs for 1604-1608 (EntityHandler, 1611 defs) and
  reports maxItemId 1610 at login (tellLimitations) — it renders the ashes fine.
  voidbot's constant LOGIN_TRAILER (captured at 10088) reported maxItemId **1603**, so
  the server correctly protected a "limited client": bank view swapped over-cap ids to
  the 1544 "Unobtanium" placeholder (`Item.getSafeItemId`) and withdraws force-dropped
  WITH a message (`Inventory.java:117`) — honest by design, only misled QA because the
  wave couldn't know the trailer was stale.
- Fix: `protocol.py login_trailer()` patches the trailer's maxItemId from the server
  defs (max id in `load_item_defs`); daemon passes it at login.
- Verified 2026-07-03: qabot04's banked 1604-1607 now decode with real ids/names;
  `bank-withdraw --id 1607` lands in inventory, no force-drop message; real client
  (workbench) renders spawned Blessed ashes at slot 0 (screenshot
  tmp/workbench/screenshots/20260703-085046-576-http.png); tests/smoke.sh 26/26 on the
  new login path.
- Log: 2026-07-02 wave-1 S-D filed as server loss risk. 2026-07-03 root-caused to the
  stale trailer, fixed, verified, committed. Stale REAL clients would legitimately hit
  the protection, but enforce_custom_client_version rejects them at login anyway.

### VS-025 — voidbot CLI/doc drift settled: unknown commands exit 2; roadmap rows marked (FIXED)
- Status: verified · Severity: P3 · Area: tooling (voidbot) / docs
- Two halves: (1) unknown commands exited 1 by session start (0 at wave-1) though the
  documented contract is 2 for usage errors — cli.py now exits 2 when the daemon
  reports "unknown command"; ok=0 and timeout=1 unchanged. (2) bot-api.md's handler
  table suggested `voidbot ...` syntax for six commands the binary lacks (combat-style,
  boundary-action, examine, prayer-on/off, cast-self, cast-npc) — those rows are now
  marked "NOT IMPLEMENTED (planned syntax)". use-on-item from the original list landed
  with E1. Implementing the six (esp. boundary-action = the E2 doors dependency)
  remains future tooling work, not doc drift.
- Verified 2026-07-03: `voidbot combat-style aggressive` exits 2; valid command 0;
  wait timeout 1; tests/smoke.sh 26/26 after the exit-code change.
- Log: 2026-07-02 wave-1 filed. 2026-07-03 fixed + verified + committed.

### VS-043 — ::item into a full inventory now reports the ground drop (FIXED)
- Status: verified · Severity: P4 · Area: server-plugin (admin command)
- `Inventory.add` drops the spawned item at the player's feet when the inventory is
  full and returns false; the ::item handler then claimed "Something went wrong
  spawning". The failure branch now checks `canHold(id, amount)` and reports
  "<player>'s inventory is full - the <item> dropped to the ground"; genuine failures
  (e.g. restrict_item_id) keep the old message.
- Verified 2026-07-03 live: 30/30 inventory + `::item 546 1` → new message + Shark on
  the ground at the player's tile (tmp/vs043-fullinv-message.json).
- Log: 2026-07-02 wave-2 S-C2/S-F filed. 2026-07-03 fixed + verified + committed.

### VS-031 — voidbot bank model didn't compact on amount-0 updates (FIXED)
- Status: verified · Severity: P3 · Area: tooling (voidbot)
- Wire contract settled during VS-008: the real client (`BankInterface.updateBank`
  :1249-1254) treats `bank_update amount 0` as remove + compact (shift every later slot
  down). voidbot left a hole. `_decode_bank_update` now mirrors the client.
- Verified 2026-07-03 on qabot04's 1608-stack bank: full-stack withdraw of slot 100 →
  model shows 1607 contiguous slots with the old slot-101 item at 100; close/reopen
  full resync matches the model EXACTLY (tmp/vs031-after-fullstack-withdraw.json,
  tmp/vs031-resync.json).
- Log: 2026-07-02 wave-1 S-D filed vs server. 2026-07-03 re-scoped to voidbot (client
  handler read), fixed, verified, committed. Deposit-back of the withdrawn noted stack
  was rejected by the over-cap canHold bug, since fixed as VS-048 (fixture note in
  Housekeeping; new-slot deposits still capped at 192 pending Ryan's cap ruling).

### VS-042 — Cook's Range "silent no-op" was mostly authentic + a voidbot blind spot (FIXED, narrow)
- Status: verified · Severity: P3→P4 (as-landed) · Area: server-plugin (cooking) + tooling
- Resolution: the quest gate is AUTHENTIC — Cook's Assistant's RSC reward is permission
  to use the cook's range; `getQuestStage > -1` correctly means "not yet completed" (the
  wave-2 "inverted gate" reading was wrong). The denial was also NOT silent: the cook
  says "Hey! Who said you could use that?" — wave-2 couldn't see it because voidbot
  didn't decode NPC overhead chat (fixed, own commit: npc_say events). The only real
  defect was the `cook == null` branch (cook dead/out of sight) denying with zero
  feedback — it now sends "You need to complete the cook's assistant quest to use this
  range".
- Verified 2026-07-03 live as qabot04: stage 0 + cook present → authentic npc_say
  denial, no double message; stage -1 → range cooks (batch menu → burnt shrimp at
  cooking 1, normal); cook-absent branch verified by inspection (cook wouldn't die in
  60s and scenery 119 exists only in the kitchen — impractical to force live).
  Evidence tmp/vs042-events-full.json.
- Log: 2026-07-02 wave-2 S-F filed. 2026-07-03 re-diagnosed with npc_say decoding,
  narrow fix, verified, committed.

### VS-008 — SEND_BANK_UPDATE wrote the bank slot as one byte; slots ≥256 wrapped (FIXED)
- Status: verified · Severity: P3 · Area: server-net · packet-shape change at client 10121
- Root cause: `PayloadCustomGenerator` `SEND_BANK_UPDATE` wrote `writeByte((byte) slot)`;
  every live per-slot update past slot 255 wrapped mod 256 (wave-1: 300→44, 1000→232,
  1544→8, 1607→71 on a 1608-stack bank). Upstream TODO's whole-bank resend quickfix
  only covered clients 10009/10010.
- Fix: slot widened to a short for custom clients >= 10121 (older clients keep the
  byte); shared client `PacketHandler.updateBank` reads the short; CLIENT_VERSION
  10120→10121 (Config.java + all tracked preset confs + local.conf + CONFIG-MATRIX);
  voidbot decodes per its negotiated VOIDBOT_CLIENT_VERSION (default 10121). Contract
  entry added to docs/subsystems/networking-protocol.md.
- Verified 2026-07-03 live on qabot04's 1608-stack bank: withdraws at slots
  100/300/998/1544/1607 all emit true-slot bank_update events
  (tmp/vs008-multi-slot-events.txt); real client decoded a live update on the new shape
  exactly (workbench, tmp/vs008-workbench-after-withdraw.txt); 10121 login passes the
  enforced version gate for both client and voidbot; tests/smoke.sh 26/26.
- Log: 2026-07-02 seeded, wave-1 root cause, wave-2 confirmed server-half real.
  2026-07-03 fixed + verified + committed. Side finds: VS-031 settled as a voidbot bug
  (client compacts on amount-0), full-bank canHold merge rejection → Intake.

### VS-047 — tests/smoke.sh: setstat arg order + admin pacing broke the gate (FIXED)
- Status: verified · Severity: P2 · Area: tooling (smoke gate) · found during VS-041 verify
- Two defects: (1) smoke's `::setstat attack 90` used STAT-first order but `changeMaxStat`
  (Admins.java:3746-3757) parses `[level] [stat]` → parseInt("attack") threw → all three
  setstats silently no-op'd → the account fought at attack 3, chicken kill luck-dependent,
  in-combat state cascading into the bank section. (2) `::item 10 500` → `::quickbank`
  fired ~0.3s apart when the account already held coins (instant wait) → VS-027 rapid-
  admin drop → bank never opened (consistent 4/4 runs; works standalone).
- Fix: LEVEL-first setstat order + `sleep 1.2` VS-027 pacing (after arena teleport,
  between setstats, before quickbank).
- Verified 2026-07-03: standalone tests/smoke.sh 26/26 (tmp/vs047-smoke-verify.log;
  failing runs tmp/vs047-smoke.log). Server-side UX half (misleading error on the natural
  arg order) remains an Intake bullet.
- Log: 2026-07-03 triaged from Intake, confirmed, fixed, verified, committed same day.

### VS-041 — voidbot inventory decoder desynced for item ids 44/45 (FIXED)
- Status: verified · Severity: P2 · Area: tooling (voidbot) · found wave-2 S-C2
- Root cause: voidbot `load_item_defs` unconditionally merged `ItemDefsPatch18.json`, but
  the server (`EntityHandler.patchItems`) only applies `ItemDefsPatch<N>.json` when
  `based_config_data` (N) < 85 — voidscape presets run 85, unpatched. Ids 44/45 are
  non-stackable in base defs but stackable "Soul-Rune"/"Reality-Rune" in Patch18, so the
  bot read 4 amount bytes the server never wrote: single-slot updates decoded amount 0;
  a full SET_INVENTORY (opcode 53) with an item after 44 desynced → IndexError →
  `state inventory` [].
- Fix: `load_item_defs` mirrors the server — base + custom always; the patch file only
  when N < 85, N read from `server/local.conf` (fallback 85, matching
  ServerConfiguration; `VOIDBOT_BASED_CONFIG_DATA` env overrides for probing).
- Verified live as wbtest 2026-07-03: exact repro (44 + item after it, relogin) went
  from [] + decode_error to full 12-slot decode, correct name/amount; both gate branches
  checked offline (85 → base names, non-stackable; 18 → Patch18 semantics);
  `wait inventory-contains --id 44` matches on the single-slot path. Evidence
  tmp/vs041/01-04*.json. tests/smoke.sh 23/26 — the 3 fails reproduce identically on
  pre-fix code (smoke's own ::setstat arg-order no-op; filed in Intake).
- Log: 2026-07-02 wave-2 S-C2. 2026-07-03 reproduced, fixed, verified, committed.

### VS-044 — Bank at Classic (skin-on) overlapped the plaque + oversized (FIXED by the Void Glass rebuild)
- Status: verified · Severity: P2 · Area: client-ui · commits 68ee7588..9d3bbc01
- Superseded by the full bank rebuild: the modern bank is now a new render+input path in
  `BankInterface.renderVoidGlassBank` (gated by `C_CUSTOM_UI`; classic bank untouched for
  preservation), centered inside the HUD-safe band (topSafe=56, bottom=chat-tab top) with
  an adaptive bank/inventory row split at small viewports (bank ≥3 rows; tray scrolls).
  Verified 2026-07-02 with workbench screenshots at all 6 viewport presets: no plaque,
  top-tab, or chat-strip overlap anywhere. Also resolves the VS-040 chat-strip residual.
  Features shipped on it: live search, page tabs, loadouts (save/load/clear), deposit-all,
  right-click withdraw/deposit 1/5/10/X/All, note mode, noted two-layer icons.

### VS-045 — Server rejected Void Glass deposit-all/preset opcodes and flagged players suspicious (FIXED)
- Status: verified · Severity: P2 · Area: server-net · commits edf3540d, 1866b644 (clear gate)
- With `want_custom_banks: false`, BankHandler/InterfaceOptionHandler rejected
  BANK_DEPOSIT_ALL_FROM_INVENTORY, BANK_LOAD/SAVE_PRESET, and BANK_CLEAR_PRESET and set
  the suspicious-player flag — so the Void Glass deposit-all button silently did nothing
  while tainting the account. Gates now also accept `player.getCustomUI()` (requires
  server `want_custom_ui` + player opt-in; presets still require `want_bank_presets`).
  Preservation (`want_custom_ui: false`) unchanged. Verified live via workbench clicks.

### VS-046 — Bank presets never survived relogin on SQLite (FIXED, general DB-layer bug)
- Status: verified · Severity: P2 · Area: server-db · commit 1866b644
- Two stacked bugs: (1) `JDBCDatabase.executeQuery` handed its consumer a ResultSet whose
  PreparedStatement try-with-resources had already closed — a closed sqlite-jdbc ResultSet
  reads as EMPTY rather than throwing, so every login loaded 0 presets and the logout save
  then wiped the stored rows; fix consumes the ResultSet inside the statement scope
  (fixes every executeQuery caller). (2) sqlite `bank.addBankPreset` double-quoted its hex
  params on top of NamedParameterQuery's single quotes, storing literal quotes in the data.
  Corrupted dev rows purged (all were empty presets). Verified: save → relogin → loadout
  intact and loadable. MySQL used prepared statements and was unaffected.

### VS-029 — Auction House empty via admin open path (FIXED, admin-only)
- Status: verified · Severity: P3 (admin/QA-harness; real players unaffected) · Area: server-plugin · commit 8d433060
- Root cause: ::quickauction/openAuctionHouse never set the 'auctionhouse' player
  attribute, so InterfaceOptionHandler:410 dropped every auction option packet (Refresh
  included) — the admin-opened AH could never fetch listings. Auctioneer NPC paths set
  it, so real players were fine. Fixture writer was never broken (6 auctions + 7 sales
  persisted; the "0 rows" QA clue was a stale pre-seed capture).
- Fix: set the attribute before sendOpenAuctionHouse, mirroring Auctioneers/VoidAuctioneer.
- Verify (live/workbench): seed fixture (::workbenchauctionfixture → 6+7 rows in sqlite),
  open via ::quickauction → Browse shows "6 matches" with the seeded listings (was 0).
- Log: 2026-07-02 diagnosed + fixed + verified same day.

### VS-030 — Bank panel clipped at Classic 512x346 (FIXED)
- Status: verified · Severity: P2 · Area: client-ui · commit e6145c2c
- Root cause: web-small bank layout anchored the panel at topSafe=52 with height 326
  (CustomBankInterface.java:125) without a bottom clamp → panelY+height=378 > gameHeight
  334 at Classic → bottom inventory row cut off, first item hidden behind the frame.
- Fix: clamp y to max(0, min(topSafe, gameHeight-height)) → panelY=8, sum=334, fits.
- Verify (workbench, index 5): panelY 52→8; screenshot shows the full inventory row +
  first item visible. Residual ~22px chat overlap split to VS-040; the plaque-overlap + oversizing
  that this vertical-fit clamp exposed is now VS-044 (superset).
- Log: 2026-07-02 diagnosed + fixed + verified same day.


### VS-028 — Void Colossus stale instance hides all NPCs after non-rift exit (FIXED)
- Status: verified · Severity: P2 · Area: server-plugin (instancing) · commit ca1f7fb
- Root cause: NPC visibility is filtered by instanceId (RegionManager.getLocalNpcs:78);
  the Colossus cleared the id only on rift/death/logout, so a teleport-spell/item/admin
  exit left it stale and every overworld NPC (id 0) was filtered out until relog.
- Fix: recurring stray-exit guard (mirrors Undead Siege) clears the instance whenever an
  owner is outside the arena, covering all exit paths by position; entry sets the id only
  after teleporting the player inside, so the guard can't cancel entry.
- Verify (live): 14 town NPCs → 1 (boss) inside → 13 town NPCs after a non-rift teleport
  with NO relog; rift exit still returns to the hub. 4/4 checks.
- Log: 2026-07-02 diagnosed (agent aaea9299), fixed + verified same day.


### VS-026 — Void Knight Death Match soft-lock (FIXED)
- Status: verified · Severity: P1 · Area: server-plugin · commit f29c301
- Root cause: full-collision Altar (scenery 19) at (984,647)/(985,647) blocked the
  x=984 fight lane between boss spawn (984,643) and player spawn (984,651); straight-line
  pathing (want_improved_pathfinding off) walled boss and player apart, so combat never
  started and the player was trapped (hard-disconnect-only escape). Fix: relocated the
  altar to (980,647) off the lane. Verified live: challenge starts, boss engages ("You
  are under attack!"), player damages boss (attack xp). Voluntary-forfeit gap split to
  VS-038.
- Log: 2026-07-02 diagnosed (agent af575887), fixed + verified same day.

### VS-037 — Quest-point award message typo (FIXED)
- Status: verified · Severity: P4 · Area: server-plugin · commit eefd499
- Fix: "You haved gained" → "You have gained" in the generic incQP message
  (Functions.java:1319) and Reldo's superchisel line; correct plural for 0. Verified by
  build (compiles the literal) + source inspection; in-game deferred (pure display
  string, exercised naturally by the wave-2 quest suite).
- Log: 2026-07-02 wave-1 S-G, fixed same day.


### VS-033 — Portal non-object JSON body → 500 (FIXED)
- Status: verified · Area: web-portal · commit bfab2e9
- Fix: readJson now rejects null/array/primitive bodies with 400 invalid_json (every
  handler reads named fields, so non-objects crashed on `.password`). Verify: null,
  [], 123, "x" all return 400; {} still reaches field validation; test-portal-api.sh green.
- Log: 2026-07-02 wave-1 S-W finding, fixed same day.


### VS-020 — voidbot full-inventory decode misaligned (FIXED)
- Status: verified · Area: tooling (voidbot) · commit f89e872
- Root cause + fix: `_decode_inventory_full` parsed the single-slot update layout
  against SEND_INVENTORY (raw id short + separate wielded byte + noted byte); it never
  consumed the wielded byte and desynced after item 1. Now reads the real layout.
- Verify: 10-check decode harness vs known inputs (coins amount, wielded flag, 3 bone
  slots, no garbage/phantom ids, starter-kit slots 0-2 now visible); tests/smoke.sh 26/26.
- Log: 2026-07-02 wave-2 gate; validated live as qabot12.

### VS-021 — voidbot bank-withdraw omitted the noted byte (FIXED)
- Status: verified · Area: tooling (voidbot) · commit f89e872
- Fix: send the noted byte unconditionally on want_bank_notes servers (0 unless
  --noted). Verify: un-noted withdraw delivered 40 coins in the harness.
- Log: 2026-07-02 wave-2 gate.

### VS-022 — voidbot single-slot stack update id<<8 (FIXED, same root cause as VS-020)
- Status: verified · Area: tooling (voidbot) · commit f89e872
- Resolution: the milestone-coin corruption S-H saw came through the full-inventory
  resync path; fixed by VS-020. Single-slot stackable path was already correct and
  now verified (coins +40 withdraw, +100 spawn both decode exactly).
- Log: 2026-07-02 wave-2 gate.

### VS-023 — voidbot inventory not compacted on removal (FIXED)
- Status: verified · Area: tooling (voidbot) · commit f89e872
- Fix: REMOVE_INVENTORY_SLOT now shifts higher slots down, matching the server/client
  arraycopy-on-removal. Verify: after burying a mid-inventory bone, slots stayed
  contiguous from 0 with no duplicates.
- Log: 2026-07-02 wave-2 gate.

### VS-024 — voidbot ground-item state broken (FIXED)
- Status: verified · Area: tooling (voidbot) · commit f89e872
- Fix: apply the incremental add/remove delta stream to persistent state (was rebuilt
  per packet), consume the per-entry noted + rare-drop-beam bytes, honor the 0x8000
  removal bit and the 255 region-clear. Verify: dropped bone appeared, no id-0
  phantoms, taken bone removed from ground.
- Log: 2026-07-02 wave-2 gate.


### VS-007 — Portal signup/recovery residual defects (verified clean, children split out)
- Status: verified · Area: web-portal
- Resolution: wave-1 S-W ran both hermetic batteries green (exit 0) plus edge probes:
  duplicate-email 409; 4-way parallel same-email register race → exactly one 201 and
  one player row; recovery one-time codes atomic (concurrent submit → single winner,
  replay 401); username validation rejects unicode/emoji/overlong/SQL-ish; 5MB → 413;
  malformed JSON/arrays → 400; all 8 admin routes constant-time 403 without token.
  Residual defects split out: VS-033 (JSON `null` → 500) + Intake minors (dry-run
  preview counts, launch-live date gate, OAuth error text, login throttle).
- Log: 2026-07-02 closed by QA wave 1 per the entry's Verify recipe; evidence
  tmp/qa/S-W/probes/*.

### VS-019 — voidbot client version trailing the real client (premise was stale docs)
- Status: verified (invalid premise; residual doc drift fixed) · Area: tooling (voidbot)
- Resolution: `tools/voidbot/protocol.py` already sends CLIENT_VERSION = 10120 (matches
  the real client); only `docs/bot-api.md:164` still said 10087 — doc corrected, and the
  version is now env-overridable (`VOIDBOT_CLIENT_VERSION`) for probing version-gated
  server paths (relevant to VS-008). Sir Charles's ≥10112 gate is already satisfied.
- Log: 2026-07-02 closed during QA campaign Phase 0 (F0.6); tests/smoke.sh 26/26 green
  logging in as 10120.
