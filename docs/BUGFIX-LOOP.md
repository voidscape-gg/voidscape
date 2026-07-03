# Bug-fix loop

Protocol for autonomously working the bug ledger, `docs/BUGS.md`. Built to survive
context clears and session restarts: **all state lives in BUGS.md — this file is pure
procedure and must stay stateless.** A fresh session knowing only CLAUDE.md (or
AGENTS.md) plus these two files must be able to continue the work.

Entry point: `/fix-bugs` (skill) — or follow this file directly.

---

## 0. Session start / recovery

1. Read `docs/BUGS.md` top to bottom.
2. If **Loop state → Active bug** is set, resume that bug from its Log line. Trust the
   Log over any memory of a previous session — the Log is ground truth.
3. Else if **Loop state → Next action** is set, follow it. It outranks the default pick
   order in §3 (it exists to carry cross-session intent); if it conflicts with reality
   (e.g. names a bug that's now closed), fix the field, then fall through.
4. Else if **Intake** has items, triage them (§2). Else pick per §3.
5. Preflight, once per session and **before any step that mutates the repo or runs the
   game** (including triage repro attempts):
   - `git status --short`; compare against the Collisions list (§7). A dirty file **not**
     on the list: log it in Loop state, don't touch its existing hunks, and never
     `git add` a whole file that carries pre-existing diffs — stage your own hunks only
     (`git add -p`).
   - Note the current branch in Loop state. **Commit on the current branch; never switch
     or create branches autonomously.** If the branch looks disposable (e.g. a
     `codex/*-checkpoint-*` scratch branch), add a "Question for Ryan: where should bug
     fixes land?" line — but keep working; commits are recoverable by VS-ID.
   - Confirm `scripts/build.sh` passes **before changing anything**, so a pre-existing
     breakage is never blamed on your fix. If it's red: that's the new top bug — file it
     in Intake, triage as P1, and fix it first; if the breakage looks like someone's
     deliberate WIP (see §7), escalate per §8 and stop.

## 1. Ground rules (non-negotiable)

- All CLAUDE.md/AGENTS.md hard rules apply. In particular: never touch
  `upstream/openrsc-snapshot/`; build/run only via `scripts/*.sh`; packet/opcode changes
  are a cross-client contract (`docs/subsystems/networking-protocol.md`); never commit
  runtime artifacts; no scope creep — fix the bug, nothing else.
- One bug per commit (mechanics in §5). **Never push** unless Ryan asks.
- Update `docs/BUGS.md` **as you work** (status flips, one-line dated Log entries), not
  at the end. A context clear can land mid-task; the ledger is what survives.
- One bug at a time. Never batch fixes into one commit.
- Timebox: ~45 minutes without progress on a repro or fix → write findings to the Log,
  set `blocked(<reason>)` or leave as `reported`, move to the next bug.
- Game interaction is voidbot-only (see the Game interaction section of
  CLAUDE.md/AGENTS.md); client-UI state and screenshots go through the AI workbench
  (§4) — never ad-hoc screen-looking or synthetic mouse input outside those two channels.

## 2. Triage (Intake → ledger entries)

For each raw report in the Intake section:

1. Assign the next unused `VS-NNN`, write an entry under Open bugs, Status `reported`,
   best-guess Severity/Area, Evidence = the raw report verbatim plus anything found.
2. Attempt reproduction using the matrix in §4. Reproduced (or conclusive code evidence
   found) → `confirmed`, with exact Repro steps and a Verify recipe. Not reproducible →
   keep `reported`, Log what was tried, add a **"Question for Ryan:"** line to the entry.
   Do not block on the answer; continue the loop.
3. Remove the raw bullet from Intake — the entry replaces it.

## 3. Pick order

Work `confirmed` bugs by severity P1 → P4; skip `blocked`. Within a severity, two
groups: launch-surface bugs first (web portal, desktop client, TeaVM web client —
launch is 2026-07-11, per the portal's `PORTAL_LAUNCH_AT` default), then everything
else; within each group, oldest ID first.

If no workable `confirmed` bugs remain: attempt repro on `reported` entries (same
ordering). If those are exhausted too, run a discovery sweep (§6), or stop and summarize.

## 4. Verify — how to prove a repro and a fix

Always: `scripts/build.sh` must pass. Then by area:

| Area | How |
|---|---|
| server gameplay / plugins / content | voidbot against a local server. `scripts/run-server.sh` blocks — run it in the background; readiness signal = `voidbot start` succeeding (retry loop; `tests/smoke.sh` is the canonical startup + assertion pattern). Leave the server running across bugs; `scripts/reset-db.sh` when a repro needs a clean dev DB. Assert with `voidbot wait ...` / `voidbot state ...` — never by eye. |
| client-ui (Client_Base / PC_Client) | `scripts/run-workbench-client.sh` → workbench HTTP on 127.0.0.1:18787. **The spec is `docs/subsystems/ai-workbench.md` — read it before your first call.** `GET /screenshot` for exact frames; `POST /dev/ui-panel` to open named panels; `GET /state` reports interface state for the auction house, world map, shop, bank, and bestiary only — for other interfaces, extend `PC_Client/src/orsc/WorkbenchServer.java` first (in-scope, own commit), same as the voidbot rule below. voidbot drives the game interactions themselves. For pixel-level judgment, save before/after screenshots under `tmp/` and read the images; if correctness is still ambiguous, Log `needs-eyes` (a Log tag, not a Status) with the screenshot paths — don't guess. |
| web portal | `scripts/test-portal-api.sh` + `scripts/test-portal-schema.sh` (hermetic); visual gate `scripts/smoke-portal-prelaunch-visual.sh`. |
| combat math | `scripts/combat-sim.sh` (offline, fully autonomous). |
| physical Android / iPhone behavior | not autonomously verifiable → `blocked(needs-device)` after doing everything simulator-side. |

If voidbot lacks a command or can't observe the needed state, **extend voidbot first**
(`docs/bot-api.md` is the spec — one if-branch in `Daemon.handle()` in
`tools/voidbot/voidbotd.py`, opcodes in `protocol.py`, payload layouts in the bot-api
handler table). The extension is in-scope and gets its own commit.

voidbot account: `wbtest` / `voidtest123` (CLAUDE.md/AGENTS.md Game interaction
section). Known state 2026-07-02: **the dev DB has zero accounts** — reset-db.sh wipes
players and nothing reseeds them. Provisioning is `docs/QA-CAMPAIGN.md` Phase 0 (F0.1
voidbot `register` + F0.2 `scripts/qa-provision-accounts.sh`); until those land,
register through the real client's flow and grant admin rank via an offline sqlite
UPDATE with the server stopped (details in `memory/qa_campaign.md`).

Save evidence (bot transcripts, screenshots, script output) under `tmp/` and cite the
paths in the Log and the DIVERGENCE entry — that's the repo's validation convention.
`tmp/` is ephemeral and must never be committed; cited paths serve the verifying
session and Ryan's immediate review, not permanent history.

## 5. Fix procedure (per bug)

1. Flip Status → `in-progress`; set **Loop state → Active bug**; Log the one-line plan.
2. **Reproduce first.** No fix without a failing observation. For latent code bugs where
   live repro is impractical (e.g. flag-gated paths), Log why, and verify by targeted
   state inspection instead.
3. Minimal fix, matching surrounding style. Check the Collisions list (§7) before
   editing hot files.
4. Verify per §4: flip → `fixed` when the code is done and the build is green;
   → `verified` when the bug's Verify recipe passes.
5. Document: one-paragraph `docs/DIVERGENCE.md` entry in the house format (dated `###`
   heading, what/why, files touched, `tmp/` evidence paths, blast-radius sentence).
6. Move the ledger entry to **Fixed archive**. Commit the bug: code + your own
   DIVERGENCE.md hunk + the BUGS.md update, in one commit whose subject mentions the
   `VS-NNN` (that ID is how fixes are found later — `git log --grep VS-NNN`; don't try
   to embed a commit's own hash in itself). DIVERGENCE.md and BUGS.md may carry other
   uncommitted hunks — stage selectively (`git add -p`), never the whole file blindly.
7. Clear Active bug; update Last session and Next action.

## 6. Discovery sweep (ledger dry, or on request)

- **Client UI:** workbench panel sweep — `scripts/run-workbench-client.sh`, then per
  `docs/subsystems/ai-workbench.md` open every `/dev/ui-panel` panel (hud,
  options-profile, options-settings, friends, ignore, magic, prayers, skills, quests,
  loot, bestiary, minimap, inventory, account) and capture `GET /state` +
  `GET /screenshot` for each (`/state` only carries data for the five §4 interfaces —
  the screenshot is the primary capture for the rest; record the gap rather than
  extending WorkbenchServer mid-sweep). Sweep the default desktop layout; the mobile panel shell
  (`voidscapeUseMobilePanelShell` in mudclient.java) has no documented programmatic
  toggle — if you can't find a scriptable way to flip it, cover desktop only and record
  the coverage gap in the sweep log. Findings → Intake.
- **Server:** run `tests/smoke.sh`; then targeted voidbot scenarios around the areas
  recent DIVERGENCE entries touched.
- **Portal:** full battery — `scripts/test-portal-api.sh`, `scripts/test-portal-schema.sh`,
  `scripts/smoke-portal-prelaunch-visual.sh`.
- Log the sweep (what was covered, what wasn't) in Loop state; raw findings go to
  Intake, then triage them.

## 7. Collisions — uncommitted WIP (as of 2026-07-02; delete lines once committed)

Deliberate, finished, uncommitted workstreams sit in the working tree. Working these
files is allowed, but stage your own hunks only and never fold this WIP into a bug commit:

- `Client_Base/src/orsc/mudclient.java` — camera-interpolation toggle.
- `server/plugins/.../commands/Admins.java` + `server/src/.../util/SyntheticLoadService.java`
  — `::cinematic` promo scenes.
- `web/portal/dev-server.mjs` + `scripts/test-portal-api.sh` +
  `scripts/package-launch-staging.sh` + `web/portal/README.md` — Resend email queue.
- `docs/DIVERGENCE.md` — carries drafted entries for the three workstreams above; every
  bug fix also appends here, so **always `git add -p` this file**.
- `server/inc/sqlite/preservation.db` — runtime artifact delta; **never commit**
  (hard rule 4). Running the local server mutates it again — that's fine and expected;
  the rule is only about committing.
- The bug system's own files (docs/BUGS.md, docs/BUGFIX-LOOP.md, skills, memory,
  CLAUDE.md/AGENTS.md lines) are also uncommitted until Ryan commits them.

## 8. Escalation — collect for Ryan, don't block

Queue these in the relevant entry's Log and surface the list in every session summary:
un-reproducible reports ("Question for Ryan:"), visual judgment calls (`needs-eyes` Log
tag + screenshot paths), physical-device gates (`blocked(needs-device)`), and anything
destructive. Destructive boundary: the **local dev DB is expendable** —
`scripts/reset-db.sh` is always allowed; shared/staging/prod databases, `Backups/`, and
anything on a remote host are off-limits without Ryan.
