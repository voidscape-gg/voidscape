---
name: feature
description: Plan and implement a feature with discovery-first workflow
argument-hint: <feature description>
---

The user is requesting a feature: **$ARGUMENTS**

Do not start writing code. Follow this workflow. Stop at each **[GATE]** to confirm with the user.

## 1. Frame

Restate the feature in one sentence. If load-bearing details are missing (scope, success criteria, in-scope vs out-of-scope), ask now — but only the questions that change the design. Don't bikeshed details that can be decided later.

## 2. Discovery

Identify every subsystem this feature touches (movement, combat, inventory, networking, client UI, persistence, etc.). For each:

- If `docs/subsystems/<x>.md` exists and looks current, read it.
- Otherwise, spawn an **Explore** agent in research-only mode. Prompt template:
  > "Map how X works in this server. Cite specific files and line numbers. Identify entry points, key data structures, and constraints (caps, timeouts, packet limits, tick budgets). Do not propose changes."
- Run independent Explore agents in parallel.
- If discovery is substantial and reusable, write it to `docs/subsystems/<x>.md` per AGENTS.md.

## 3. Surface unknowns

List the questions whose answers shape the design. Tag each:
- **[answered by discovery]** — cite the file/section.
- **[needs user input]** — product/scope decision only the user can make.
- **[needs more research]** — resolve now before continuing.

**[GATE]** Ask the user any **[needs user input]** questions before proceeding.

## 4. Plan

Spawn a **Plan** agent with discovery + resolved unknowns as context. Require:
- Specific files and functions to touch — no abstractions like "the pathfinder" or "the UI."
- Packet/protocol changes called out explicitly. **AGENTS.md rule 3**: server/client opcode changes require cross-client sync — flag them.
- Slice breakdown — each slice runnable and testable in-game.
- Risks, edge cases, remaining unknowns.
- No code. Plan only.

## 5. Critique

Read the plan critically before showing the user.
- If sections reference abstractions instead of specific files, discovery wasn't deep enough → return to step 2.
- If risks aren't called out, push back.
- If a slice can't be tested standalone, re-slice.
- Iterate until concrete.

## 6. Present and confirm

Show the user, in order:
1. One-paragraph discovery summary per subsystem.
2. Unknowns and how they were resolved.
3. The plan with slice ordering.
4. Risks and what's deferred.

**[GATE]** Get explicit user agreement on the plan and slice ordering before any code is written.

## 7. Implement slice by slice

For each slice:
- Implement the minimum to make the slice testable.
- Run `scripts/build.sh` (per AGENTS.md — never raw `gradlew`).
- For UI/client changes: actually run the client (`scripts/run-client.sh`) and test in-game. Type-checking ≠ working.
- Confirm the slice works before moving on. Don't batch.

## 8. Document

After completion, add a one-paragraph entry to `docs/DIVERGENCE.md` per AGENTS.md rule 6. If new subsystem knowledge emerged during discovery, leave it in `docs/subsystems/<x>.md` for next time.

---

## Hard rules (from AGENTS.md — do not violate)

- Never edit `upstream/openrsc-snapshot/`.
- Use `scripts/*.sh`, never raw `gradlew`.
- Opcode/packet changes are a cross-client contract — flag in plan, sync per `docs/subsystems/networking-protocol.md`.
- Don't add features beyond what was requested. Don't refactor on the side. RSC servers attract scope creep.
