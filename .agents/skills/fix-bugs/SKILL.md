---
name: fix-bugs
description: Work the bug ledger autonomously — triage intake, then reproduce, fix, verify, and document bugs one at a time. Use when asked to fix bugs, work the bug backlog, or continue bug-fixing after a context clear.
argument-hint: [VS-NNN | <count> | triage | sweep]
---

Execute the bug-fix loop defined in `docs/BUGFIX-LOOP.md` against the ledger
`docs/BUGS.md`. Read both files first — the ledger's **Loop state** section, not your
memory of any previous session, is the ground truth for where work stands.

Argument: **$ARGUMENTS**

- *empty* — run the loop: resume **Loop state → Active bug** if set, then follow
  **Next action** if set, then triage Intake, then fix `confirmed` bugs in pick order
  until none are workable or you are blocked.
- `VS-NNN` — work exactly that bug.
- a number — run that many iterations, then summarize. One iteration = one bug driven
  to `verified`, `blocked`, or a timeboxed stop (triage passes don't count).
- `triage` — only convert Intake items into ledger entries (with repro attempts).
- `sweep` — run the discovery sweep (BUGFIX-LOOP.md §6) to find new bugs; no fixing.

Rules that override everything else: update `docs/BUGS.md` **as you work** (it is the
state that survives context clears); one bug per commit, message referencing the VS ID;
`scripts/build.sh` plus the bug's Verify recipe before claiming a fix; a
`docs/DIVERGENCE.md` paragraph per fix; never push.
