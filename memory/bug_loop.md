# Bug-fix loop

- 2026-07-02: Built the autonomous bug-fix system. `docs/BUGS.md` is the single source
  of truth (Loop state, Intake, per-bug status + dated Log lines); `docs/BUGFIX-LOOP.md`
  is the stateless procedure; `/fix-bugs` (skill mirrored in `.claude/skills/` and
  `.agents/skills/`) is the entry point.
- Ledger seeded 2026-07-02 with VS-001..VS-018 from a full repo survey (code TODOs,
  DIVERGENCE admissions, git-history fragile clusters). Ryan's own reports go in the
  ledger's **Intake** section as raw bullets; triage converts them to entries.
- Why it's shaped this way: Ryan wants bugs fixed autonomously across context clears.
  Sessions must trust BUGS.md over their own memory and update it *while* working, not
  after — the ledger is the only thing guaranteed to survive.
- Verification split (from survey): server/gameplay → voidbot; client UI state and
  screenshots → AI workbench (127.0.0.1:18787 via `scripts/run-workbench-client.sh`);
  portal → `scripts/test-portal-*.sh`; pixel judgment and physical devices are the two
  things that may need Ryan (Log tag `needs-eyes` / status `blocked(needs-device)` —
  `needs-eyes` is a Log annotation, not a Status value).
- 2026-07-02, answer to BUGFIX-LOOP §4's account-provisioning question: there is NO
  provisioning today — the dev DB has zero players (wbtest included) and
  scripts/reset-db.sh wipes accounts on every reseed. Fix = QA campaign Phase 0
  (F0.1 voidbot `register`, F0.2 scripts/qa-provision-accounts.sh + reset-db hook).
  Until then: register via the real client, grant admin via offline sqlite UPDATE with
  the server stopped. See [[qa_campaign]].
