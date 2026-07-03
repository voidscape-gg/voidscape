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

- **Active bug:** none
- **Session preflight 2026-07-03:** branch `codex/ui-loot-checkpoint-20260613` (scratch
  checkpoint branch — standing Question for Ryan: where should bug fixes land?); dirty
  files all match the §7 Collisions list; pre-change `scripts/build.sh` green.
- **Last session:** 2026-07-02 — big run, 13 items committed. Verified fixes: wave-2
  tooling gate (VS-020/021/022/023/024, f89e872), VS-033 (bfab2e9), **VS-026 (P1,
  f29c301)**, VS-037 (eefd499), **VS-028 (P2, ca1f7fb)**, **VS-030 (P2, e6145c2c)**,
  VS-029 (P3, 8d433060). Plus F0.8 non-priv account (2f167e18) and **VS-003 fixed but
  runtime-display verification pending (b6c8841f)**. Re-verified VS-027 (decoder artifact,
  P2→P3) and VS-002 (latent MySQL-only; naive fix breaks SQLite — reverted, P2→P3). Filed
  VS-038, VS-040. Server on latest build.
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
- **Next action (top open confirmed, launch surfaces first):** VS-041 + VS-047 DONE
  2026-07-03 (voidbot decoder trust + 26/26 smoke gate restored) → next is VS-008
  (P3 server one-byte bank slot; qabot04's bank is still filled for the retest), then
  VS-042/043, P3 tail. VS-002 deferred (needs MySQL env). Also: E2 (doors) to unblock
  a quests wave. VS-003: await Ryan's ruling.

---

## Intake — dump raw bug reports here

Anyone (Ryan or an agent) can append raw, unstructured reports below, one bullet each.
The loop's triage step converts each into a numbered entry and removes it from this list.

_(Ryan: paste anything from "chat sometimes overlaps" to "X quest broken";
half-remembered is fine, triage will chase it down.)_

**Wave-1 oddity tail (un-triaged; each cites its report):**
- goto to unreachable tile stalls silently mid-route; reissue no-ops while walk-step
  crosses fine — no failure feedback for WORLD_WALK (tmp/qa/S-A §2)
- `::spawnnpc 19` (Rat) fails silently — no NPC, no message (tmp/qa/S-B F4). Wave-2
  corroboration: `::spawnnpc <id> 1 1 X Y` spawns relative to the SENDER'S tile, not the
  passed X/Y — remote-coord spawns silently do nothing (tmp/qa/S-DEATH). Likely one bug.
- ~~`item-command` acts on the LAST stack~~ → **wave-2 RESOLVED: was a decoder artifact**
  (now honors the requested slot; tmp/qa/S-C2). Removed.
- Equipping a non-wieldable item silently ignored — wave-2: REAL but EXPECTED (authentic
  client never offers wield on non-wieldable; not a defect). Closed.
- `::item` into a full inventory misleading message → promoted to **VS-043**.
- Over-deposit of an unheld item is a silent no-op — wave-2 could NOT re-verify (bank
  wouldn't open via banker dialogue in the daemon = VS-046 below); still open.
- NEW wave-2 lower-confidence items: `::quickbank` admin command is a no-op (inventory
  unchanged; tmp/qa/S-F, S-C2); banker-dialogue bank won't open in the voidbot daemon
  (npc-talk→menu-reply closes without opening bank — blocks daemon bank tests; call it
  VS-046, tmp/qa/S-C2 F3); equipping from inventory while banking closes the bank
  (server hideBank — possibly intended; tmp/qa/S-D2); `state skills` omits custom skills
  (runecraft/harvesting) so their xp can't be asserted (voidbot gap; tmp/qa/S-F);
  runecraft disabled in preset (`want_runecraft:false`) — spec question, is that intended
  for launch? (tmp/qa/S-F); artisan XP-per-action ratios looked off vs def exp but the
  VoidPath 2x/rested 1.5x boosts confound it — needs isolation (tmp/qa/S-F).
- Server-side bank adds while the bank UI is open push no updates (`Bank.add(item,
  false)`) — view stale until reopen (tmp/qa/S-D F5)
- `::teleport` silently swallowed while a skilling batch is active, reproduced 2× (S-E F3)
- `wait xp-gained` missed one in-window thieving gain once — flaky (tmp/qa/S-E F4)
- `::queststage` usage string says stage optional; handler requires all 3 args (S-G §2)
- `::setstat` arg order is LEVEL STAT with a misleading error on the natural order —
  fix docs/briefs that say SKILL LVL (tmp/qa/S-I, S-B, S-E)
- `wait xp-gained` without --skill crashes with raw KeyError (tmp/qa/S-C F8)
- Rested XP wording: docs say per-second, in-game message says per-minute (S-H F4)
- Undead Siege mid-run logout gives no payout/forfeit feedback (tmp/qa/S-I)
- Death items-kept untestable by fleet: admin accounts skip dropOnDeath()
  (`Player.java` ~2770) — campaign needs a non-privileged bot account (tmp/qa/S-A §4)
- S-V needs-eyes minors: Options panel bleeds through Advanced Settings modal;
  amount labels collide with panel border at slot 0 (bank "500", loot "3"); bestiary
  level column mixes "lv N" and "#id" for same-named NPCs; world-map window overlaps
  minimap + search box covers area label; `/dev/ui-panel "account"` returns ok but
  renders nothing (tmp/qa/S-V findings 3,5,6,7,8)
- S-W minors: dry-run email backfill preview hardcodes existing:0/queued:0; Google
  OAuth garbage credential → misleading `invalid_username`; no login throttle after
  12 failed passwords (tmp/qa/S-W/probes/30,36)
- S-W integrity export flags 7 high-severity rows (missing_skill_row ×6,
  missing_table ×1) on the dev DB — verify these are fixture artifacts, not real
  (tmp/qa/S-W/integrity-findings.json)
- Housekeeping: qabot04's bank left at ~1608 stacks for VS-008 retests; two Blessed
  ashes on the ground near (137,644) (tmp/qa/S-D end state)

---

## Open bugs

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

### VS-003 — "Items kept on death" screen ignores Protect Item and misreports stackables
- Status: **fixed-but-partial / reopened** (fix removed the old bugs but diverges from server on noted items; interface is DORMANT) · Severity: **P4** (dead code under every voidscape preset) · Area: client-ui · commit b6c8841f
- **Wave-2 findings (2026-07-02) that change the picture:**
  1. The interface is UNREACHABLE in all voidscape presets: `want_equipment_tab: false`
     (local.conf:239) + `items_on_death_menu: false` (local.conf:121); the equipment-tab
     button (mudclient.java:15195) is the only opener and isn't drawn. So the whole bug
     is dead code for players unless those flags are enabled (they're true only in
     rsccabbage/rsccoleslaw). → downgraded P2→P4.
  2. My fix treats noted as always-lost, but the SERVER keep-loop
     (`Inventory.java:477-490`) only `break`s on `isStackable()`, NOT noted — so the
     server CAN keep a noted non-stackable item when the player has <3 more-valuable
     non-noted items. S-DEATH confirmed live: qanpc1 (skull off, 2 gear + noted x3, no
     coins) KEPT the noted stack. So my client fix now DIVERGES from the server in that
     edge case (over-corrected).
- Net: the fix DID remove the two clear original bugs (Protect Item ignored, stackable
  split as "1 kept") — a real improvement — but to truly mirror the server it should
  break only on `isStackable()` (keep noted if reached), not treat noted as always-lost.
- **Question for Ryan:** (a) should the items-kept-on-death interface be enabled at all
  in voidscape (it's off)? (b) intended: are noted items kept or always lost on death?
  The server currently *can* keep them — that itself may be a server bug. Answer decides
  whether to refine the client (break only on stackable) or fix the server (break on
  noted too).
- Root cause (diagnosed 2026-07-02, agent a385dba): the interface is a client-LOCAL
  computation (equipment-tab button, no packet). It sorted stackables by price (so a big
  stack could take a keep slot) and split off 1 unit as "kept", and never added the
  Protect Item +1. Server truth `Inventory.dropOnDeath` (`server/.../container/Inventory.java:424-490`):
  keep 3 most valuable NON-stackable/non-noted (0 if skulled), +1 if Protect Items
  (prayer id 8) active; stackable+noted ALWAYS fully lost. Note the interface's `lost`
  field is inverted (`lost==true` means kept).
- Fix: `LostOnDeathInterface.java` — demote stackable/noted below all keepable items in
  the sort, keep `(skulled?0:3)+(protect?1:0)` via `mc.checkPrayerOn(8)` (server-synced,
  opcode 206), stop at the first always-lost item; deleted the buggy split block.
- Fix status: removed the two original bugs (Protect Item, stackable-split) — verified by
  code review; but see the noted-item divergence above. Runtime display matrix could NOT
  be run (interface dormant — S-VD). Server death rule itself verified live via S-DEATH
  (skull off → keep 3 non-stackables; skull on → keep 0; and the noted edge case above).
- Log: 2026-07-02 seeded; diagnosed + fixed (b6c8841f); **wave-2 (S-DEATH + S-VD) found
  the interface is dormant and the fix diverges from the server on noted items** →
  reopened, downgraded P2→P4, spec question raised for Ryan. Don't archive until the
  noted behavior is decided.

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
- Status: reported · Severity: P2 · Area: server-content
- Evidence: `server/src/com/openrsc/server/constants/NpcDrops.java:1777` and `:1805` —
  "// TODO: Fix up drop table (especially with double-drops)".
- Repro: unknown — needs reading the drop-table code to pin what "fix up" means, then a
  statistical kill loop via voidbot (or direct unit-style invocation) on affected NPCs.
- Verify: depends on finding; likely distribution check over N kills vs intended table.
- Log: 2026-07-02 seeded from survey.

### VS-008 — SEND_BANK_UPDATE writes bank slot as one byte; slots ≥256 wrap mod 256
- Status: confirmed · Severity: P3 · Area: server-net
- Evidence: `server/src/com/openrsc/server/net/rsc/ActionSender.java:2282-2289` — TODO
  admits the per-slot bank update is broken for >256-item banks and adds a whole-bank
  `showBank` resend as a quickfix, but the quickfix is **gated to client versions
  10009/10010** (with custom banks). Voidscape's client is 10120
  (`Client_Base/src/orsc/Config.java:23`; voidbot logs in as 10087 per docs/bot-api.md),
  so current clients take the raw per-slot path — unestablished whether that desyncs
  (stale bank screen past slot 256) or whether the newer client handles it fine.
- Repro: build a >256-distinct-item bank, then deposit/withdraw an item past slot 256
  and check what the real client shows / voidbot's decoded bank state.
- Verify: per-slot updates correct past slot 256 on client 10120 and via voidbot; if it
  turns out fine, `wontfix(inapplicable to voidscape client)`. **Packet contract
  warning:** any wire change must follow `docs/subsystems/networking-protocol.md`.
- Log: 2026-07-02 seeded from survey. 2026-07-02 verification pass: quickfix is
  version-gated and inapplicable to voidscape — reframed from "wasteful resend" to
  "possible stale bank screen"; confirmed→reported pending live repro.
  2026-07-02 wave-1 S-D: CONFIRMED + root-caused —
  `PayloadCustomGenerator.java:667` `builder.writeByte((byte) bu.slot)` truncates the
  slot; five wrap datapoints on a 1608-stack `::fillbank` bank (300→44, 1000→232,
  1544→8, 1607→71 ×2). Client view corrupts; DB stays correct; bank close/reopen
  fully resyncs (hence P3). Repro: `::fillbank` → reopen bank →
  `bank-withdraw --id 1000 --amount 3 --noted` → events show `bank_update slot 232`.
  Sibling compaction desync split to VS-031. qabot04's bank is left filled for retest.
  2026-07-02 wave-2 S-D2 (fixed decoder): full-list bank display on open is now
  correct (no wrap) — but the live single-slot SEND_BANK_UPDATE STILL truncates slot to
  one byte, corrupting any op on slots >=256. Server bug CONFIRMED on the fixed decoder
  (not a decoder artifact). Withdraw of a slot-262 item updated slot 6.

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

### VS-025 — voidbot CLI/doc drift: unknown commands exit 0; bot-api.md lists unimplemented commands
- Status: confirmed · Severity: P3 · Area: tooling (voidbot) / docs
- Evidence: unknown command prints ok:false but exits 0 (docs promise exit 2);
  bot-api.md's implemented list includes ~7 commands the binary lacks (combat-style,
  prayer-on/off, cast-npc/self, use-on-item, examine, boundary-action) (S-A/B/C).
- Repro: `voidbot combat-style aggressive; echo $?`
- Verify: docs match binary; unknown command exits 2.
- Log: 2026-07-02 wave-1 (S-A/B/C).

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

### VS-031 — Full-stack bank withdrawal: server compacts slots but sends no shift updates
- Status: reported · Severity: P3 · Area: server-net / tooling (voidbot)
- Evidence: S-D — withdrawing an entire stack sends one `bank_update slot N amount 0`;
  server compacts (proven by later appends/updates) but no shift updates follow, so a
  slot-model client desyncs below slot 256 too. UNKNOWN whether the real 10120 client
  compacts on amount-0 (then this is a voidbot model bug) — check that first.
  `tmp/qa/S-D/25-events-full.json` seq 908-930.
- Repro: after `::fillbank`: `bank-withdraw --id 1 --amount 51 --noted` → hole in view.
- Verify: workbench bank state after a full-stack withdraw settles the contract; fix
  whichever side is wrong.
- Log: 2026-07-02 wave-1 (S-D): interacts with VS-008.

### VS-032 — Items above client render cap: bank shows "Unobtanium" placeholder; withdraw force-drops the real item
- Status: confirmed · Severity: P3 · Area: server-core / content-pipeline
- Evidence: S-D — banked ids 1604-1607 render as id 1544 "Unobtanium" ×50; withdrawing
  → "Your client could not receive Blessed ashes, it drops to the ground!" (real item
  force-dropped; DB held correct ids). Loss risk for players. Two Blessed ashes remain
  on the ground near (137,644). `tmp/qa/S-D/16-events-ashes-unobtanium.txt`.
- Repro: `::fillbank` → withdraw id 1607.
- Verify: either the client renders these ids, or the server blocks banking/spawning
  them with an honest message — never placeholder + silent floor drop.
- Log: 2026-07-02 wave-1 (S-D): VS-004-adjacent; the cap guard's failure mode is the bug.

### VS-034 — Abandoned/cancelled NPC dialog leaves the NPC silently un-talkable ~30-60s
- Status: confirmed · Severity: P3 · Area: server-core (dialog busy-state)
- Evidence: S-G — walk away from Fred mid-menu → two 12s-timeout re-talks; Cook after
  menu-cancel → 20s+ timeouts, recovered ~50s. No "X is busy" feedback. Contrast:
  attack-while-menu-open closes cleanly and NPC is immediately re-talkable.
- Repro: talk to Fred (77) → walk away with menu open → re-talk → timeout.
- Verify: NPC promptly re-talkable after abandon/cancel, or an explicit busy message.
- Log: 2026-07-02 wave-1 (S-G): players will read this as "NPC broken".

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

## Design rulings (Ryan, 2026-07-02) — intended behavior, do NOT re-file

- Harvesting skill disabled on the launch preset (`want_harvesting: false`) — intended.
- Bone-bury prayer XP rides `combat_exp_rate` (10×, i.e. 37.5/bone) — intended.
- `bank-deposit-all` deposits wielded equipment too — intended.
- Void Rush one-entry-per-IP — intended anti-abuse (QA consequence: this minigame is
  permanently untestable from a single-host fleet; needs manual/multi-host QA).
- Combat XP per NPC capped at max-HP-worth (0 XP killing blow after retreat/regen) —
  intended/authentic.
- Portal `launch-live` endpoint has no date gate — discretionary by design (admin-gated).

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

### VS-042 — Lumbridge Cook's Range blocks cooking for players who haven't done Cook's Assistant
- Status: confirmed · Severity: P3 · Area: server-plugin (cooking)
- Evidence: S-F — cooking raw shrimp on the Lumbridge Cook's Range (obj 119 @131,660) is
  a silent no-op (no cook, no product, no message) for a fresh player. `ObjectCooking`
  gates COOKS_RANGE behind `getQuestStage(COOKS_ASSISTANT) > -1`, but an un-started quest
  is stage 0 (>-1 == true) and a COMPLETED quest is stage -1 — so the range only works
  BEFORE... actually only fails while the completed sentinel isn't set; the gate is
  inverted for the intended "members can use after quest" logic, and gives no feedback.
  Fires only when no cooking-assistant NPC is in range. Cooking on a fire works fine.
- Repro: `::teleport 131 661` → `::item 349 5` → `use-item-on-object 131 660 <slot>` →
  no xp, no message (tmp/qa/S-F/skills_final.json).
- Verify: a fresh player can cook on the range (or gets a clear message); check the
  intended authentic rule for this range.
- Log: 2026-07-02 wave-2 S-F.

### VS-043 — Admin ::item into a full inventory drops to the ground but reports "Something went wrong spawning"
- Status: confirmed · Severity: P4 · Area: server-plugin (admin command)
- Evidence: S-C2/S-F — with a 30/30 inventory, `::item 546 1` prints "Something went wrong
  spawning 1 Shark" yet the Shark appears on the ground at the player's feet. Misleading
  admin feedback (the spawn succeeded to ground). Bit QA provisioning twice.
- Repro: fill inventory to 30, `::item 546 1`, `state messages` (error) + `state
  ground-items` (item present).
- Verify: message should say the item dropped to the ground / inventory full.
- Log: 2026-07-02 wave-2 S-C2/S-F. QA-ergonomics but also player-facing for admins.

## Watch list — not open bugs, but recurrence risks and burned areas

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
