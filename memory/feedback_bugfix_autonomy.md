---
name: feedback_bugfix_autonomy
description: Ryan wants the bug-fix / QA loop run autonomously straight through, no per-fix pauses
metadata:
  type: feedback
---

Ryan wants the bug-fix loop and QA campaign run **straight through without pausing
for approval between fixes**. Confirmed 2026-07-02.

**Why:** he set up the whole autonomous system (docs/BUGS.md, docs/BUGFIX-LOOP.md,
docs/QA-CAMPAIGN.md, /fix-bugs, /qa-campaign) precisely so bugs get fixed without
hand-holding across context clears.

**How to apply:** work the ledger in pick order (P1→P4, launch surfaces first),
one bug per commit, verify each per its recipe, update the ledger as you go — but
don't stop to ask "should I continue?" after each fix. Only surface for: destructive
actions, genuine scope changes, spec questions only Ryan can answer (collect these in
Intake and keep going), or when the confirmed queue is exhausted. Reversible in-scope
fixes proceed automatically. See [[bug_loop]], [[qa_campaign]].
