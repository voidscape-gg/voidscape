---
name: feedback_ui_qa
description: Ryan wants UI overlap/sizing issues caught reliably every time, not by eyeballing; prefers original OpenRSC bank sizing
metadata:
  type: feedback
---

Ryan flagged (2026-07-02) that the custom bank at the Classic/small viewport
overlaps the top-left location plaque ("LUMBRIDGE ... Slots N") and is still too
big — and asked that **this class of UI bug (overlaps, oversizing) get caught
every time**, not depend on an agent eyeballing a screenshot.

**Why:** VS-030 fixed the vertical clipping but missed the plaque overlap + the
panel still being oversized — because the S-V visual sweep judged screenshots by
eye, which is unreliable for overlaps.

**How to apply:**
- Prefer **deterministic UI-geometry checks** over visual judgment: the workbench
  `/state` exposes panel bounding boxes (panelX/Y/Height/width) and HUD element
  positions. Assert programmatically that each panel (a) fits inside the viewport
  and (b) does not overlap other visible HUD elements (location plaque, chat tabs,
  top tabs, minimap). Add this as a repeatable check in the S-V sweep so overlaps
  are caught every run, at every viewport preset.
- For the bank specifically, **emulate the original OpenRSC bank sizing/proportions**
  at small viewports rather than the oversized custom panel.
See [[qa_campaign]], and VS-030 / VS-044 in docs/BUGS.md.
