---
name: project_bank_ui_rebuild
description: Void Glass bank rebuild — SHIPPED 2026-07-02 (all 7 slices); what remains open
metadata:
  type: project
---

**Void Glass bank — DONE (2026-07-02), commits 68ee7588..9d3bbc01.** Ryan's rebuild of
the bank UI: translucent purple "clear glass" aesthetic, built as a new render+input path
`renderVoidGlassBank` in **BankInterface.java** (search "Void Glass" / `voidGlassBank()` =
`Config.C_CUSTOM_UI && !isAndroid()`), NOT CustomBankInterface (dormant;
`want_custom_banks: false` in local.conf). Preservation/skin-off keeps the authentic
classic bank — verified visually. Native 48×32 icons, never shrunk.

**Shipped features (all verified live via workbench screenshots + state):** centered
HUD-safe panel (tiers: <700px 8 cols, <940 10 cols, else 12); adaptive bank/inventory
split at small viewports (bank ≥3 rows, tray takes the remainder with its own gutter
scrollbar — this fixed the Classic 512×334 squeeze); live search (title-strip box,
keys via `vgHandleKey` hook in mudclient's dispatcher, Esc clears then closes); page
tabs (All + up to six 48-item pages, last tab absorbs overflow, search forces All);
loadouts L1-L3 (context card: Load/Save/Clear, packets 28/27/199-14); deposit-all
(packet 24); Note: On/Off toggle (`swapNoteMode` byte on withdraw packet 22);
right-click quantity menu (1/5/10/X/All both grids); noted two-layer paper icons;
stack colors green/gold≥100k/cyan≥10M; glass sheen; scrollbar hover. 6-viewport
regression + voidbot bank round-trip passed.

**Server changes that were required (documented in DIVERGENCE):** BankHandler +
InterfaceOptionHandler gates for deposit-all/preset opcodes now accept
`player.getCustomUI()` (VS-045 — they previously silently rejected AND set the
suspicious-player flag). SQLite bank-preset persistence was broken for everyone
(VS-046): `JDBCDatabase.executeQuery` consumed a closed ResultSet (reads empty on
sqlite-jdbc — fixed for ALL callers) and `bank_presets.xml` double-quoted hex params
(stored literal quotes). Presets now survive relogin.

**2026-07-03 (Ryan's follow-ups, shipped commit 9e1e8c2f):** drag-to-reorder + Arrange
chip (Off/Swap/Insert; sub-ops 2/3, gates accept custom_ui; drag disabled while
searching; client resets bank view state only on FRESH opens since insert triggers a
showBank resend). Small screen: top-tab row hides while banking at the small HUD,
topSafe→8, small tier 9 cols → Classic = 3 bank + 3 inv rows (3+2 with page tabs),
near-full-height panel. All verified live (swap/insert round-trips, withdraw with
arrange off, MID regression).

**Open / for Ryan:** (1) cert-mode deposit skipped (`want_cert_deposit: false`).
(2) The old phase-1 loop questions still pending: VS-003 noted-items-on-death ruling,
runecraft on/off.

**Verify loop (still valid):** `scripts/dev/bank-verify.sh <viewport 0-5>` (5=Classic
512, 2=800×600, 4=1024×768) builds + fresh-relaunches the workbench skin-on + opens the
bank + screenshots. READ THE PNG — the ui-geometry-lint /state reflects the dormant
CustomBankInterface, not the Void Glass render. Clicks/typing:
`POST 127.0.0.1:18787/input/click|type|key|command`. Fresh login needs SIGTERM (+~12s);
pkill -9 leaves a resumed session that renders skin-OFF. wbtest has `custom_ui=true` in
player_cache (flip to 'false' string to test the classic bank). `::fillbank`/
`::unfillbank` = 192-item test data (unfill sweeps ALL ids ≤191 incl. originals).
CLOSE the workbench when done (Ryan asked).
