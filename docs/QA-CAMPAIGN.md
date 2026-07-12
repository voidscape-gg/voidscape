# QA playthrough campaign

Systematically play through every game system with **parallel QA agents**, find unknown
bugs, and feed them into the bug ledger. This file is the campaign's living state —
coverage, fleet runbook, suite specs, infrastructure backlog. Same survival rule as
`docs/BUGS.md`: update it **as you work**; a fresh session must be able to resume from
this file alone.

Companions: `docs/BUGS.md` (findings land in its Intake), `docs/BUGFIX-LOOP.md` (fix
protocol), `docs/bot-api.md` (voidbot spec), `docs/subsystems/ai-workbench.md` (visual
channel). Entry point: `/qa-campaign`.

---

## Campaign state

- **Phase:** 0 COMPLETE (2026-07-02, commits 357b4cf, 22c5602, 12033a7, 1b75cd2,
  be61106, ab3211e — F0.7 optional, skipped). Account pool provisioned: wbtest +
  qabot01..12, all admin, all past the Void Council gate. tests/smoke.sh 26/26 green.
  Fleet wave 1 launched 2026-07-02.
- **Phase-0 discoveries (all handled):** (1) `want_packet_register` and
  (2) `production_command_lockdown` in the gitignored `server/local.conf` both had
  launch-hardened values that block QA — flipped locally with `# MODIFIED (QA)`
  comments; **fresh local.conf copies must re-apply both** (provisioning script checks
  and warns). (3) New accounts are held on Void Island by the Void Council until
  `void_path` is chosen — even admin ::teleport is redirected; provisioning grants the
  cache key offline. (4) VS-019's premise was stale docs — voidbot already sends client
  version 10120; closed.
- **Last session:** 2026-07-02 — **Wave 1 COMPLETE**: 10/10 suites ran (~56 min wall
  clock, ~900 game operations, zero agent failures). Results merged into docs/BUGS.md:
  18 new entries (VS-020..037), VS-008 confirmed+root-caused, VS-007 verified→archived,
  VS-005 narrowed to E6-blocked residuals, VS-001 P1→P2 with children split out, plus
  a 20-item Intake tail and 6 spec questions for Ryan. Full detail: tmp/qa/*/report.md.
- **Wave 2 DONE (2026-07-02):** S-F (artisan skills, E1) verified — smelt/smith/cook/
  firemaking/fletch/gem-cut all work; S-C2/S-D2 confirmed the decoder fixes + settled the
  wave-1 artifacts (VS-027 deposits + item-command-slot = artifacts); S-DEATH verified the
  server death rule (+ found the VS-003 noted divergence); S-VD found the death interface
  is dormant. New bugs → VS-041/042/043 + Intake.
- **Coverage:** 10 of 11 suites ran (wave 1); wave 2 re-ran S-C/S-D + added S-F/S-DEATH/S-VD. S-F now unblocked (E1 landed 2026-07-02).
  A wave-2 pass should run S-F + re-run S-C/S-D on the fixed decoders. Solid-green systems: session,
  walking, melee/ranged combat, XP stack (exact math), banking basics, gathering
  skills, 3 full quests, dialogs, rifts, titles, announcements, subscription cards,
  wilderness spawns, Undead Siege, Sir Charles solo, portal signup/recovery.
- **WAVE-2 GATE (do before any more game suites):** fix VS-020 + VS-021 + VS-024
  (voidbot decode bugs that corrupt QA ground truth — root causes in the ledger) and
  add F0.8 below. Command pacing workaround until VS-027 is fixed: ≥1s between bot
  commands.
- **Note on commits:** ledger/campaign docs stay uncommitted until Ryan commits the
  bug-system files; Phase-0 code commits reference their F-item and VS IDs in subjects.

---

## Fleet architecture (verified against code, 2026-07-02)

- **One local server** (`scripts/run-server.sh`, uses `server/local.conf`).
- **N voidbot daemons, one per agent.** Everything is already parameterized:
  `VOIDBOT_CTRL_PORT=1890X tools/voidbot/voidbot start --user qabotXX --pass voidqa123`.
  Logs land at `${TMPDIR}/voidbotd-<port>.log`. Every CLI call needs the same
  `VOIDBOT_CTRL_PORT` exported. **Never use the default port 18900** (solo runs
  included) — a port collision currently yields a silent false-success and an orphaned
  session (fix = F0.3).
- **Server accepts the fleet:** 127.0.0.1 is exempt from `max_players_per_ip` and the
  connection caps (`LoginRequest.java:275`, `RSCPacketFilter.java:277`) and can't be
  flood-banned. Two constraints remain: unique username per daemon (else
  ACCOUNT_LOGGEDIN), and `max_logins_per_second: 2` applies to localhost — **stagger
  daemon starts ≥600ms**; once one admin account logs in, the host is whitelisted and
  the throttle lifts for that server run.
- **Region isolation:** each suite teleports to its own town at start
  (`voidbot admin "::teleport <place|x y>"` — ~82 named locations across the two maps
  in `Event.java:~40-122`) and spawns its own NPCs/items (`::spawnnpc`, `::item`).
  Never scavenge another suite's ground items or kills.
- **Visual channel:** exactly **one** workbench client by default. Port/output/account
  are per-instance (`--workbench-port`, `--out`, `--login USER:PASS`), but multiple
  clients share `Client_Base/` CWD + Cache and `ant compile-and-run` rebuilds race —
  don't run two without F0.7. Window sizes via `POST /dev/viewport {"index":0..5}`
  (640×480 … 1024×768 + 512×346 Classic). The mobile panel shell is **unreachable on
  desktop** (`voidscapeUseMobilePanelShell()` hardcoded `return false;`,
  `mudclient.java:17051`) — mobile layout QA belongs to the TeaVM client, not this fleet.
- **Port map:** agent *i* ⇒ `VOIDBOT_CTRL_PORT=18900+i` (18901–18912 reserved below).
  Workbench: 18787 default (ephemeral free-port pattern in
  `scripts/smoke-pc-client-prelaunch.sh:132-141` if it's taken).

## Account pool

`qabot01`–`qabot12` / `voidqa123`, plus `wbtest`/`voidtest123` (workbench + smoke.sh
default) — all admin (group_id=1), all with `void_path=1` (Warrior) granted so they can
leave Void Island. **Provisioned 2026-07-02** via `scripts/qa-provision-accounts.sh`
(idempotent; re-run anytime; auto-runs after `scripts/reset-db.sh`). Accounts die on
every DB reset — that's expected; the reset hook re-mints them. Registration always
creates group_id=10; only an offline `UPDATE players SET group_id=1 ...` **with the
server stopped** can mint admins on an empty DB (an in-game ADMIN can't grant ADMIN;
there is no OWNER). Suites that need the true starter flow (unchosen path, starter
kit) register a throwaway account with `voidbot register` instead of using the pool.

---

## Phase 0 — infrastructure (in order; each item = one commit + DIVERGENCE entry)

- **F0.1 `voidbot register --user U --pass P`** — sender for login-phase wire opcode 2
  (handled at `LoginPacketHandler.java:599`; localhost is exempt from registration caps
  since local.conf has `is_localhost_restricted: false`; the 2/s login throttle applies —
  stagger). Verify: register on a fresh DB, then `start` + `state position`.
- **F0.2 `scripts/qa-provision-accounts.sh`** — two-phase by necessity: registration
  needs the server RUNNING; the rank grant needs it STOPPED. Sequence: with server up,
  register wbtest + qabot01..12 (staggered ≥600ms); stop the server; apply
  `sqlite3 server/inc/sqlite/preservation.db "UPDATE players SET group_id=1 WHERE
  username LIKE 'qabot%' OR username='wbtest'"`. **Invariant: the UPDATE must never run
  while the server is up** — it holds players in memory and saves over DB edits. The
  script should manage the server itself (start if down, stop before the UPDATE) or
  refuse with a clear message; when hooked into `scripts/reset-db.sh` (server already
  stopped there), the sequence is reset → start server → register → stop → UPDATE.
  Verify: 3 concurrent daemons log in; `::teleport` works from each.
- **F0.3 voidbot port-collision hardening** (~10-14 lines) — wrapper `start` pre-checks
  the port with a one-shot socket connect (not `cli.py ping`, which retries ~5s) and
  refuses with exit 2 if something listens; daemon exits if the control bind fails
  (`voidbotd.py:816` currently dies in a thread while `run()` logs in anyway → orphaned
  session). Verify: second start on a busy port exits 2; no orphan login.
- **F0.4 `tests/smoke.sh`** — it already inherits `VOIDBOT_CTRL_PORT` through the
  wrapper env; what needs parameterizing is the hardcoded teleport/walk coordinates
  (smoke.sh:71,78) so parallel runs use distinct regions.
- **F0.5 `docs/bot-api.md`** — add a "running multiple instances" paragraph
  (VOIDBOT_CTRL_PORT, unique `--user`, per-port logs, stagger rule).
- **F0.6 voidbot login client version configurable** — hardcoded 10087; real client is
  10120; Sir Charles / Void Arena challenge needs ≥10112. This is **VS-019** in BUGS.md.
- *(F0.7, only if a second visual client is ever needed: per-instance client CWD/cache +
  build-once launch instead of `ant compile-and-run`.)*
- **F0.8 (wave-2 gate)** — provision one **non-privileged** bot account (e.g. qabot13,
  group_id=10, void_path granted): admin accounts skip `dropOnDeath()`
  (`Player.java` ~2770), so death items-kept mechanics are untestable by the current
  all-admin pool. Add to qa-provision-accounts.sh.

## Voidbot extension backlog (ordered by what each unblocks)

Extensions are normal loop work: implement per `docs/bot-api.md` (payload tables are
already there), verify live, own commit + DIVERGENCE entry, update bot-api.md.

- **E1 use-item family — DONE (2026-07-02, commit ffb67211).** Added `use-on-item`
  (ITEM_USE_ITEM), `use-item-on-object` (USE_ITEM_ON_SCENERY), `use-item-on-ground`
  (GROUND_ITEM_USE_ITEM), `use-item-on-npc` (NPC_USE_ITEM). Verified live (firemaking
  +xp, smelting prompt). Unblocks cooking/smithing/crafting/firemaking/fletching/herblaw
  mixing/runecraft use-paths + many quests. (USE_WITH_BOUNDARY / doors is still E2.)
- **E2 `boundary-action`** (doors/gates, 5-byte payload) — unblocks most quests/interiors.
- **E3 `prayer-on/off` + `combat-style`** (1-byte payloads) — prayer effects/drain,
  per-style XP routing.
- **E4 `cast-self/cast-npc/cast-item/cast-land`** — magic combat, teleports, alch, enchant.
- **E5 `shop-buy/sell/close` + decode SEND_SHOP_OPEN stock** into `state shop.items`.
- **E6 other-player decoder** — full PLAYER_COORDS decode + appearance packet, new
  `state players` + `wait player-present`. **The multiplayer unlock**: PvP, trade, duel,
  bounty hunter, ranked Void Arena are all impossible without it, even with two bots.
  Largest item; schedule as its own slice.
- **E7 `trade-*` / `duel-*` senders** (after E6).

Wave-1 additions (consolidated from suite gap reports; details in tmp/qa/*/report.md):
- **E8 scenery/boundary state query** (`state objects`) — E2 companion; needed to
  diagnose path stalls and confirm object presence (blocked the VS-036 diagnosis).
- **E9 decode sendBox/message-box + npcsay overhead text** — currently blinds mod
  query replies, subscription-redeem confirmations, leaderboards, and makes menu-less
  dialogs (Gertrude, Kolodion) indistinguishable from dead NPCs.
- **E10 equipment/worn-slot state + SEND_GAME_SETTINGS decode** — wielded assertions
  currently need DB snapshots; effective XP rates (sub 11×/3×) unassertable.
- **E11 custom skills in `state skills` / `wait xp-gained`** — harvesting/runecraft XP
  can't be asserted even when enabled.
- **E12 QA ergonomics batch** — unknown-cmd exit 2 (with VS-025), `wait bank-closed`,
  admin-command ack or auto-spacing (VS-027 workaround), a state-resync command,
  ground-item amounts (with VS-024), wait-message registration race, `item-command`
  accepting command names.
- **E13 workbench batch** — /dev/ready must clear the character-design screen;
  programmatic world-map + shop open; /state chat-log + loot-panel data; deterministic
  pre-login capture.

---

## Suites

Each suite = one agent, one account, one control port, one region. Statuses: `todo` /
`running` / `pass` / `findings-filed` / `blocked(<what>)`. Per-system tags:
**READY** (runnable now) · **GAP(En/F0.n)** (needs that item) · **EYES** (workbench
screenshot judgment) · **PARTIAL** (part runnable).

### S-A Core loop & movement — qabot01, port 18901 — `findings-filed` (wave 1)
Login/session/logout READY · walking + pathing READY (`goto`, `wait near`) · doors/gates
GAP(E2) · death & items-kept PARTIAL (respawn position jump, inventory shrink, ground
items, message regex; client death-screen accuracy is VS-003 → EYES) · fatigue accrual
READY (sleep cycle deprioritized — `want_fatigue: false` in preset).

### S-B Combat — qabot02, port 18902 — `findings-filed` (wave 1; styles/magic still gap E3/E4)
Melee kill/loot READY (smoke.sh pattern) · ranged READY (equip bow+arrows → attack →
ranged xp + arrow drain) · combat styles GAP(E3) · magic combat GAP(E4) · prayers
GAP(E3) · PvP/wilderness/skulls BLOCKED(E6) · XP-multiplier stack READY (assert
`wait xp-gained` deltas vs `::rested`, progression-balance.md).

### S-C Inventory & equipment — qabot03, port 18903 — `findings-filed` (wave 1; root-caused VS-020/021)
Equip/unequip + wielded flag READY · drop/take READY · eat/drink/bury via `item-command`
READY · item-on-item combine GAP(E1) · noted items via bank READY · milestone coin
payouts READY (message regex + `inventory-contains` coins).

### S-D Banking & economy — qabot04, port 18904 — `findings-filed` (wave 1; VS-008 confirmed; bank left filled for retests)
Deposit/withdraw/deposit-all/noted READY · **bank >256 items = VS-008 repro lives here**
READY · shops GAP(E5) (workbench shows the native shop window → EYES fallback) ·
auction house: voidbot-blind (undecoded packet 132) — drive via workbench
`/fixture/auction-house` + `/scenario/auction-house-open`, assert against
`auctions`/`auction_sales` DB tables (EYES+DB).

### S-E Gathering skills — qabot05, port 18905 — `findings-filed` (wave 1; harvesting = spec question)
Mining, fishing, woodcutting, agility, thieving (pickpocket + stalls), harvesting — all
READY (`object-action`/`npc-command` + `wait xp-gained` + `inventory-contains`) ·
guaranteed-node pity streaks READY (`::gatherstreak` seeds deterministically).

### S-F Artisan skills — qabot06, port 18906 — `ready` (E1 landed 2026-07-02; run in wave 2)
Smithing, cooking, firemaking, crafting, fletching, herblaw mixing, runecraft use-paths —
all GAP(E1) · herb identify READY (run it while waiting). Starts properly once E1 lands.

### S-G Quests & dialogs — qabot07, port 18907 — `findings-filed` (wave 1; 3 quests completed end-to-end)
NPC dialogs/menus/input boxes READY · 52 quests (50 authentic + 2 custom:
PeelingTheOnion, RuneMysteries) — dialog/walk/kill steps READY, most full playthroughs
GAP(E1,E2); start with dialog-only quests; `::queststage` + `quest_points` assert stages.

### S-H Voidscape custom systems — qabot08, port 18908 — `findings-filed` (wave 1; all 8 systems pass; ranked blocked E6)
Void rifts READY (`object-action` id 1306 + `wait near`) · titles PARTIAL (menus + DB
`pt_u_*` cache keys READY; overhead render EYES) · world announcements READY
(`::announcepreview` + message regex) · subscription vendor/cards READY (smoke.sh stage
3 + card 1602 redeem + `acct_sub` cache asserts) · dynamic wilderness spawns PARTIAL
(`::wildhobdebug` forces tiers; true multi-IP tiers out of scope) · loot beams PARTIAL
(`::lootbeam` messages READY; beam flag byte undecoded; render EYES) · Sir Charles solo
arena BLOCKED(F0.6, needs client ≥10112) · ranked arena + bounty hunter BLOCKED(E6).

### S-I Minigames — qabot09, port 18909 — `findings-filed` (wave 1; VS-026 P1 found; VS-028, VS-035)
Authentic (barcrawl, fishing trawler, gnomeball, kitten care, mage arena, …) + custom
(DeathMatchArena→**VS-005**, voidcolossus, voidrush, undeadsiege, CombatOdyssey, pets,
DwarfRescue): dialog/walk/kill portions READY; gnomeball GAP(E1 use-on-player),
several GAP(E1/E2), PK sims BLOCKED(E6). Run the READY portions, tag the rest.

### S-V Visual UI (workbench) — wbtest, workbench :18787 — `findings-filed` (wave 1; 84-shot sweep; VS-029/030)
**This is VS-001's sweep.** All 14 panels (`/dev/ui-panel`: hud, options-profile,
options-settings, friends, ignore, magic, prayers, skills, quests, loot, bestiary,
minimap, inventory, account) × all 6 `/dev/viewport` presets, screenshot each
(PNG+state sidecar under the workbench out dir); plus login screens, chat layout,
wilderness indicator, hit splats, XP counter during a scripted fight (pair with S-B's
bot for on-screen action). Judgment: read the screenshots; ambiguous → `needs-eyes`
Log tag + paths. `/dev/ready` clears blocking dialogs; payloads are flat JSON.
**REQUIRED every run — deterministic overlap check:** after opening each panel at each
viewport, run `scripts/ui-geometry-lint.py`; it fails (exit 1) if a panel overflows the
viewport or overlaps a reserved HUD zone (plaque/top-tabs/chat). Don't rely on eyeballing
— the lint is what catches the VS-044-class overlap/oversizing every time (Ryan's ask).
NOTE: the voidscape HUD skin (`C_CUSTOM_UI`) drives the web-small layout at small presets
but can't currently be enabled via config (`customUi` is write-only in clientSettings.conf)
— find an in-client/workbench toggle so the skin-on layout (what players see) is testable.

### S-W Web portal — no bot — `findings-filed` (wave 1; batteries green; VS-007 closed, VS-033 split)
**= VS-007.** `scripts/test-portal-api.sh` + `test-portal-schema.sh` + edge probes
(double-submit, expired tokens, unicode, concurrent recovery) +
`smoke-portal-prelaunch-visual.sh`. Independent of the game fleet — can run anytime.

---

## Run protocol (orchestrator session)

1. **Preflight:** BUGFIX-LOOP §0 preflight (build green, git status vs Collisions).
   Phase 0 complete? If not, do Phase 0 items first — they're ordinary loop work.
   Start the server in the background; wait for a successful `voidbot start`.
2. **Provision:** run `scripts/qa-provision-accounts.sh` if the account pool is missing
   (check: `sqlite3 -readonly server/inc/sqlite/preservation.db "SELECT count(*) FROM
   players WHERE username LIKE 'qabot%'"`).
3. **Launch suites in parallel** — one subagent per non-blocked suite. Each agent's
   brief must include, verbatim: its suite spec from this file; its port/account/region;
   the assert-don't-look rules (voidbot waits/state; workbench for S-V); the findings
   format below; **and the fleet guardrails: never edit docs/QA-CAMPAIGN.md, docs/BUGS.md,
   or any shared doc; never fix a bug mid-suite (file it and move on); never run
   scripts/reset-db.sh or stop the server (other suites depend on both); never touch
   tools/voidbot code mid-fleet — a missing capability is a finding, not a cue to extend
   voidbot** (CLAUDE.md's "extend voidbot first" rule is suspended for suite agents;
   extensions are orchestrator/loop work between fleet runs).
4. **Each agent writes** `tmp/qa/<suite>/report.md`: systems exercised, per-system
   pass/fail/blocked, findings, evidence paths (bot JSON transcripts, screenshots).
   **Finding format:** expected vs observed + exact repro commands + evidence path.
   File even low-confidence oddities — triage will sort them.
5. **Orchestrator merges:** copy findings into `docs/BUGS.md` → Intake (one bullet
   each, citing the report path); update suite statuses + Campaign state here; then
   run `/fix-bugs triage`. Fixes happen via the normal bug loop, not inside suites.
6. **Re-run cadence:** after a batch of fixes, re-run affected suites (their reports
   list exact repro commands — re-verification is cheap). A suite is `pass` only when
   its READY systems all pass; GAP-tagged systems flip to runnable as E-items land.

## Standing rules

- Suites **find** bugs; the bug loop **fixes** them. Never fix mid-suite — file and move on.
- All BUGFIX-LOOP ground rules apply (voidbot-only interaction, never push, timeboxes,
  escalation) **except during a fleet run, when ledger/campaign-doc writes and DB
  resets are orchestrator-only** — a mid-fleet `reset-db.sh` would delete every suite's
  account and orphan all daemons.
- A suite that hits a missing voidbot capability notes it **in its report** (naming the
  E-item, or describing a new one); the orchestrator records it here. No screen-level
  workarounds, no mid-fleet voidbot edits.
- Update Campaign state + suite statuses in this file as work happens — context clears
  don't announce themselves.
