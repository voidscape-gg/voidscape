---
name: qa-campaign
description: Run the parallel playthrough-QA campaign — per-system bot suites that exercise the whole game and feed findings into docs/BUGS.md Intake. Use when asked to test the game end-to-end, run QA suites, launch the bot fleet, or continue the campaign.
argument-hint: [status | phase0 | S-<letter> | fleet]
---

Execute the QA campaign defined in `docs/QA-CAMPAIGN.md`. Read it first — its
**Campaign state** section, not your memory of any previous session, is ground truth.

Argument: **$ARGUMENTS**

- `status` — report campaign/suite/Phase-0 state; no execution.
- `phase0` — work the Phase 0 infrastructure items in order (each one commit, per
  `docs/BUGFIX-LOOP.md` ground rules and verification standards).
- `S-<letter>` — run exactly that suite, per its spec in the campaign doc.
- `fleet` — run all non-blocked suites in parallel per the Run protocol (one subagent
  per suite, each with its own VOIDBOT_CTRL_PORT/account/region).
- *empty* — resume: finish Phase 0 if incomplete, then run `fleet` in the same session.

Non-negotiable: parallel suite agents never edit `docs/QA-CAMPAIGN.md` or
`docs/BUGS.md` — they write `tmp/qa/<suite>/report.md` and the orchestrator merges
findings into the BUGS.md Intake and updates suite statuses. Suites find bugs; fixes go
through `/fix-bugs`, never mid-suite.
