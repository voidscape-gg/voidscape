# QA campaign

- 2026-07-02 (later): Phase 0 COMPLETE (6 commits, F0.1-F0.6) and **fleet wave 1
  COMPLETE** — 10/10 suites, ~900 game ops, zero failures. Ledger grew VS-020..037;
  headline finds: VS-026 (Void Knight Death Match soft-lock, P1), VS-020 (voidbot
  full-inventory decode misparse — WAVE-2 GATE with VS-021/VS-024), VS-027 (server
  drops >1 packet per tick silently), VS-008 root-caused (one-byte bank slot).
  Suite evidence: tmp/qa/*/report.md. Two local.conf QA flags now REQUIRED:
  want_packet_register: true + production_command_lockdown: false (fresh copies must
  re-apply; provisioning script warns).

- 2026-07-02: Built the parallel playthrough-QA system. `docs/QA-CAMPAIGN.md` is the
  living state (fleet runbook, 11 suites S-A..S-W, Phase-0 infrastructure F0.1-F0.7,
  voidbot extension backlog E1-E7); `/qa-campaign` skill mirrored in `.claude/skills/`
  and `.agents/skills/`. Findings flow into docs/BUGS.md Intake; fixes via /fix-bugs.
- Verified 2026-07-02 (4-agent recon): N voidbot daemons work TODAY via
  VOIDBOT_CTRL_PORT (per-port logs, no other shared state); the server exempts
  127.0.0.1 from per-IP player/connection caps; logins throttle at 2/s until one admin
  logs in (host whitelist, in-memory per server run). Workbench port/dir/account are
  per-instance; window sizes via POST /dev/viewport index 0-5.
- CRITICAL gotcha: **the dev DB has ZERO accounts** — wbtest doesn't exist despite
  CLAUDE.md documenting it; `scripts/reset-db.sh` wipes players and nothing reseeds.
  Registration is wire opcode 2 (voidbot can't send it yet = F0.1); admin rank on an
  empty DB only via offline sqlite UPDATE with the server STOPPED (in-game ::setgroup
  can't mint ADMINs without an OWNER).
- Mobile panel shell is hardcoded off on desktop (`mudclient.java:17051` returns false)
  — the desktop workbench cannot test mobile layout; that's TeaVM-client territory.
- Biggest coverage gaps needing voidbot extensions: E1 use-item family (unblocks all
  artisan skills + many quests), E6 other-player decoder (unblocks ALL multiplayer:
  PvP, trade, duel, bounty hunter, ranked Void Arena).
