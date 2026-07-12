# Voidscape UI Style Guide

**Status:** authoritative. Any client UI change must conform to this document or amend it in the same PR.
**Scope:** all client-drawn chrome in `Client_Base` (and the PC bootstrap card). It does NOT cover world rendering, avatar recoloring, or cache sprite art content.
**Provenance:** produced 2026-07-03 from a 14-area code audit of the client UI, three competing design proposals judged by a 3-lens panel (authenticity / engineering / player-UX; unanimous winner "One Void — Authentic-Plus"), plus live workbench screenshots. Baseline captures: `tmp/workbench/baseline-ui/`.

## 0. The one decision everything follows from

The dark **void theme** already on screen (glass panel kit, Void Glass bank, farm-sim modal, skinned right-click menu, HUD icon strip, location plaque) **is the brand**. Standardization means conforming every remaining surface to it — not inventing a new aesthetic and not reverting themed surfaces to 2001 chrome.

Two skins ship forever:

1. **Authentic** (`Config.C_CUSTOM_UI == false`): classic RSC chrome, **byte-identical** to today. Preservation/2001scape presets depend on it (`want_custom_ui` is false in every preset except `server/local.conf`). The gate pattern to copy is `Menu.java`'s `useVoidscapeMenuStyle` dual-skin (Menu.java:397-417).
2. **Voidscape** (`C_CUSTOM_UI == true`, `useVoidscapeHudSkin()` at mudclient.java:17055-17057): everything in this guide.

Only two sanctioned exceptions change authentic-mode pixels: the `drawBoxAlpha` green-mask fix and the `drawShadowText` unification (§8). Both are logged in `docs/DIVERGENCE.md` with both-skin before/after screenshot pairs, per CLAUDE.md hard rule 6.

## 1. Design tokens — `UiSkin`

All tokens live in **one class**: `Client_Base/src/orsc/graphics/gui/UiSkin.java` (Client_Base-pure — no AWT, no PC classes; compiles into PC, Android, TeaVM). No new hex literal may be added to UI code; use a token or add one here with a source citation.

### 1.1 Palette

| Token | Hex | Role | Canonical source (verified) |
|---|---|---|---|
| `VOID_SCRIM` | `0x050308` | glass-kit outer wash @50 | mudclient.java:19198 |
| `VOID_HEADER` | `0x0D0914` | title-bar strip @218 | mudclient.java:19199 |
| `VOID_BODY` | `0x0B0810` | panel body (alpha ladder §1.2) | mudclient.java:19200 — replaces 0x101216, 0x111018, 0x08080A, 0x09040F, 0x07040b, 0x05030A |
| `VOID_BOX` | `0x130E1A` | inset box / sub-tab bg | vBoxBg locals, mudclient.java:15278 — replaces 0x101010, 0x090909, 0x14181e, 0x2a2532 |
| `VOID_LINE` | `0x2E2140` | dividers | vLine, mudclient.java:15278 — replaces 0x3B3148, 0x241A33, 0x2A173A |
| `BORDER_INNER` | `0x271F2D` | inner inset border | mudclient.java:19209 |
| `SHADOW_A` / `SHADOW_B` | `0x2F2434` / `0x18111E` | bevel dark pair | mudclient.java:19204/19208 |
| `GOLD_BEVEL` | `0x8C6F3D` | bevel light (top/left) | mudclient.java:19203 |
| `GOLD_LINE` | `0x6E5737` | hairline gold (== `Menu.VOIDSCAPE_UI_LINE`) | mudclient.java:19207, Menu.java:15 — replaces 0xb8914f, 0xd0a030, 0x9a7a31, 0xD8C27A |
| `PURPLE_SELECT` | `0x4B2472` | selected/hover fill (== `Menu.VOIDSCAPE_UI_PURPLE`) | Menu.java:16, vSel mudclient.java:15278 |
| `PURPLE_EDGE` | `0x6A4FA0` | separators / tooltip border | mudclient.java:20366 — replaces 0x6F3DC4, 0x7557b8, 0x5c3a93, 0x7d28c7 |
| `PURPLE_BRIGHT` / `PURPLE_BLOOM` | `0x8F2BFF` / `0xC35CFF` | VG hover bloom, boss-bar fill (hero surfaces only) | BankInterface.java:138-139 |
| `PURPLE_FOCUS` | `0xB68AFF` | text-field focus ring | mudclient.java:12509 — also replaces ScaledWindow accent 0x9b62ff (ScaledWindow.java:963) and 0xd9b6ff-as-focus |
| `GOLD_TITLE` | `0xF0DFA3` | font-4 panel titles | mudclient.java:19211 — replaces 0xf3d46b, 0xf1d56c, 0xF6DA7D-as-title |
| `GOLD_HEADER` | `0xE4D08D` | section headers | mudclient.java:20145 area |
| `GOLD_HOT` | `0xFFD968` | hover/active text + values | mudclient.java:20367 — replaces 0xffff00/0xffd66b/0xFFE9A8/0xEFE3B6 hovers |
| `GOLD_RING` | `0xF6DA7D` | selected-slot ring ONLY (== `Menu.VOIDSCAPE_UI_GOLD`, `VG_SEL_RING`) | Menu.java:17, BankInterface.java:140 |
| `TEXT_BODY` | `0xE7DEBC` | parchment body text (vText) | mudclient.java:15278 |
| `TEXT_LABEL` | `0xB7ABC8` | secondary labels | player-info stat labels |
| `TEXT_DIM` | `0x8E7EA7` | disabled/dim (vDim) | mudclient.java:20375 |
| `GOOD` / `BAD` / `FLASH` | `0x6FE38A` / `0xFF6B6B` / `0xFF4D4D` | boosted / drained / activity flash | skill rows, chat dock |
| `DANGER_HOVER` / `DANGER_GLYPH` | `0x6E1E1E` / `0xFF7070` | close-button hover fill / glyph | farm-sim X, mudclient.java:6426-6428 |
| `CLOSE_FILL` / `CLOSE_BORDER` | `0x221A24` / `0x7D6F8B` | close-button idle | mudclient.java:6426-6427 |
| `FIELD_BG` | `0x07090C` @218 | text-field fill | mudclient.java:12508 — kills all white input boxes |
| `FIELD_BORDER_IDLE` | `0x56606A` | text-field idle border | mudclient.java:12509 |
| `TINT_PARCHMENT` | `0x3C3125` | parchment fallback tint (right-panel-bg.png tone) | drawVoidscapePanelChrome body fallback, Menu.VOIDSCAPE_UI_TINT |
| `GLASS_BODY` | `0x160B2C` | **standard panel body** (translucent, see §1.3) | BankInterface VG_BODY |
| `GLASS_RIM` | `0x8A6BD8` | lavender inner rim on glass cards | BankInterface VG_FRAME_INNER |
| `GLASS_SHEEN` | `0xBFA8FF` | upper-glass sheen @12 | BankInterface glass sheen |

**World-color exemption (hard rule):** world/scene colors — walk-route green `0x40FF40`, pending-input mint `0x2AD28C`/`0xA8FFD6`, entity health-bar `0x00FF00`/`0xFF0000`, teleport-bubble ramps, level-diff `@gr1..3@` — get their own `WORLD_*` named constants and are **exempt** from any UI palette sweep. A UI token change must never alter world rendering.

### 1.2 Alpha ladder

`VOID_BODY` at: **218** modal, **170** chat frame, `voidscapeGlassPanelBodyAlpha()` for glass side panels. `VOID_SCRIM` @50. Full-screen dim stays black @118-138 (unchanged). `PURPLE_SELECT` at: **235** active tab, **200** strip/button, **116** hover row.

### 1.3 Chrome spec

**Glass mandate (owner direction, 2026-07-04):** the standard panel treatment is the translucent light-purple **glass card** — `UiSkin.glassPanel()`: `GLASS_BODY` at `A_GLASS` (112, hero/grid surfaces over the 3D world) or `A_GLASS_TEXT` (168, text-dense modals/cards), upper 2/5 `GLASS_SHEEN` @12, black outer border, `GLASS_RIM` inner rim. Panels stay see-through; **opaque backdrops are banned** on standardized surfaces. The ornate side-panel kit (nine-slice + SCRIM/HEADER underlay below) remains the treatment for the docked right-hand HUD panels.

- **Panel frame (docked HUD panels):** nine-slice `right-panel-frame-thin.png` (32/16/16) over SCRIM+HEADER+BODY underlay. Code-drawn fallback = the glass-kit recipe verbatim (top/left `GOLD_BEVEL`, bottom/right `SHADOW_A`, `GOLD_LINE`+`SHADOW_B` seam under header, `BORDER_INNER` inset). Missing cache assets **must** degrade to the fallback — never a pink placeholder (enforced structurally by the `SpriteHook` indirection, §5.1).
- **Title bar:** 24px (25/27 compact/normal on glass panels); ornate panels use the `right-panel-title.png` three-slice; title = font 4, `GOLD_TITLE`, centered.
- **Close:** 20x20 "X" top-right (farm-sim recipe: `CLOSE_FILL`/`CLOSE_BORDER`, hover `DANGER_HOVER`/`DANGER_GLYPH`) + ESC, on **every** window. One modality table (§4.5) drives ESC, Android back, and wheel routing.
- **Button:** fill `VOID_BOX` @200, 1px `GOLD_LINE` border; hover = `GOLD_HOT` border+label; active/selected = `PURPLE_SELECT` @200 + `GOLD_TITLE` underline; disabled label `TEXT_DIM`.
- **Scrollbar:** 12px (28px Android touch), track `VOID_BOX` @200, thumb `PURPLE_EDGE` with `GOLD_LINE` hairline.
- **Text field:** `FIELD_BG`, border `FIELD_BORDER_IDLE` → `PURPLE_FOCUS` on focus, text `TEXT_BODY`, existing `*` caret convention.
- **Tooltip:** the skill-tooltip spec verbatim (bg `VOID_BODY` @232, border `PURPLE_EDGE`, title `GOLD_HOT` font 1, labels `TEXT_DIM`, values `TEXT_BODY`, clamped to screen — mudclient.java:20354-20379), promoted to `UiSkin.tooltip()`.
- **Plaque / timer banner:** scout-timer recipe (measured width, dark backing @218, `PURPLE_EDGE` accent strip, `GOLD_TITLE` text flipping to `BAD` under threshold), stacked top-center.

### 1.4 Typography

Named constants replace magic font ints: `FONT_SMALL=0` (h11p, captions/values), `FONT_BODY=1` (h12b, rows/body/tooltips), `FONT_TITLE=4` (h14b, panel titles/strip labels), `FONT_DISPLAY=5` (h16b, pre-game hero titles only), `FONT_HUGE=7` (sleep screen only). One hierarchy: title = font 4 `GOLD_TITLE` / section = `GOLD_HEADER` / body = font 1 `TEXT_BODY` / caption = font 0. Shadows collapse to one convention: `drawShadowText` delegates to the auto-shadow offsets (§8 divergence entry).

### 1.5 Spacing

4px base unit (multiplied by `us()` on skinned surfaces); panel pad 8 (7 compact); list rows 15 dense / 18 standard / 28 touch; slot cells stay **49x34** with the VG tier ladder 49/54/60 × 34/36/40 (BankInterface.java:174-177); modal widths 400 (message) / 468 (grid) clamped to `gameWidth-16` and centered via `UiAnchor`.

## 2. Geometry — `UiAnchor`

Anchors live in a **separate** tiny class `Client_Base/src/orsc/graphics/gui/UiAnchor.java` (pure static int math over passed-in dimensions), so geometry and color are independently reviewable and the 512-era-literal sweep stays greppable: `centerX`, `rightEdge`, `clampTopSafe`, `clampBottomSafe`, `centeredDialogX/Y` (promoted from the void-arena helpers, mudclient.java:5480-5493).

**Rules:** no new literal `256`/`507`/`22,36` anchors — `grep 'drawColoredStringCentered(256'` must stay empty of new hits. Layout units on skinned surfaces are `uiScale()`/`us()`/`voidscapeFont()` (mudclient.java:30509-30522) and `voidscapePanelSizeClass()` (17416-17423). Safe areas via `getVoidscapeDesktopOverlayTopSafeY()`/`BottomSafeY()`. No reflow layer: fixed logical framebuffer + whole-frame scaling stays (bitmap fonts, 512px cache sprites, mouse inverse-mapping). Hit-tests must derive from the same geometry as the draw call, never parallel magic numbers.

## 3. Semantic `@col@` codes

Four additions, one switch line each in GraphicsController.java:1950-2015: `@hdr@`=`GOLD_TITLE`, `@acc@`=`GOLD_HOT`, `@dis@`=`TEXT_DIM`, `@bod@`=`TEXT_BODY`. Server-driven list text joins the theme without auditing every producer. Existing codes are wire-format — never remove or renumber.

## 4. How the three UI code families consume the standard

### 4.1 mudclient inline drawing (glass kit, dialogs, HUD)

Repoint every voidscape hex literal to `UiSkin` tokens, then collapse the three near-duplicate frame/button kits (title-menu 6105-6156, void-arena 5450-5468, login 12470-12511) into `UiSkin` helper calls. The debug hooks `voidscapeDebugColorProperty`/`IntProperty` (mudclient.java:17113-17142) feed `UiSkin` fields for live tuning. Unit of change: **one method per commit**, grep-verified (32k-line monolith; duplicated logic exists, e.g. the two NPC-overlay copies).

### 4.2 Panel.java (legacy widget fleet)

Replicate Menu.java's flag-gated dual-skin: `renderButtonBackground`, `renderScrollbar`, `renderDecoratedBox`, `renderTextEntry`, and list rendering consult `UiSkin` when `C_CUSTOM_UI`; the classic colorA-L path is byte-untouched otherwise. The five inline list state-color blocks dedupe into one `resolveListColor(hovered, selected, alt)`. While in the file: wire wheel-scroll (`scrollMethodList`, Panel.java:1348) into every scrolling-list variant and add the missing `addToggleButton()` for the orphaned checkbox renderer. Never change adder signatures or the `handleMouse → drawPanel → isClicked` ordering (drawPanel clears click state at Panel.java:483).

### 4.3 Interface classes (`com.openrsc.interfaces.misc`)

`InterfaceChrome` helper (title bar + X close + ESC + per-frame centering + safe-area clamp + `drawButton` + tooltip) consuming `UiSkin`. The 7 byte-identical grey `drawButton` copies are deleted. Interfaces keep their `isVisible/setVisible/onRender/reposition` shape and their inline packet writes untouched.

**NComponent freeze (review-rejection criterion):** NComponent is a documented legacy overlay host for its 5 existing consumers only. **No new consumers, no new primitives** — tokens-only recoloring. PRs violating this are rejected.

### 4.4 Overlays / world-anchored effects

HUD plaques (boss HUD, scout timer, FPS chip, XP badge, vitals) use `UiSkin.plaque()`/`timerBanner()`. World-tile highlights and beam/rift art keep their own `WORLD_*` constants (§1.1 exemption). No new per-frame alpha layers, gradients, or trig — the standardization must be draw-call-neutral (tick-cost push, commit b942cfbd).

### 4.5 Dialog modality

One ordered modality table (seeded from `getWebOverlayDialogName`, mudclient.java:28716-28736) drives ESC, the Android back button, **and mouse-wheel capture routing**. Every modal registers `{isOpen, close(), keySink}` there.

## 5. Do / Don't

**Do**
- Source every color from `UiSkin`; every anchor from `UiAnchor`/`us()`/size classes.
- Gate every skinned path on `C_CUSTOM_UI`; keep the authentic path byte-identical.
- Give every window a 20x20 X close + ESC via the modality table.
- Move paint coords and click rects **in lockstep** (draw methods are controllers; packet sends live inline — trade 104/230, shop 236/221, duel 8).
- Update `WorkbenchServer` layout accessors in the same commit as any geometry change they observe.
- One method/dialog per commit in mudclient.java; verify by workbench screenshot, not typecheck.
- Log any pixel-visible divergence in `docs/DIVERGENCE.md` (rule 6).

**Don't**
- Add hex literals, magic font ints, or literal-512-era anchors to UI code.
- Touch `upstream/openrsc-snapshot/`, opcode ordinals, packet-111 indexes, or `S_*`/`C_*` field names.
- Extend NComponent, merge Clan/Party, or delete the legacy login path inside theming slices (deferred backlog, §7).
- Use `GOLD_RING` for anything but selected-slot rings, or `PURPLE_BRIGHT/BLOOM` outside VG-bank hover + boss bar (hero-surface character stays).
- Reference AWT/PC classes from `UiSkin`/`UiAnchor` (Android/TeaVM compile Client_Base).
- Add per-frame trig/alpha layers to always-rendered chrome.

## 6. Verification standard

Every slice runs the standing matrix (`scripts/ui-regression-matrix.sh`, lands with Slice 2): `/dev/viewport` 0-5 × `/scenario/ui-panels` (+ bank/AH/dialog scenarios where relevant), captured **in both modes** — `C_CUSTOM_UI` on (visual review) and off (naive pixel-diff must be **byte-identical**, except slices explicitly containing the two sanctioned global fixes, which isolate their delta in dedicated commits with their own before/after galleries including a world scene). Workbench captures are unscaled native frames, so diffs are pixel-exact. Click paths changed by a slice are re-verified via `/input/click`, not screenshots alone.

## 7. Named deferred follow-ups (backlog, not scope)

- **Clan/Party parameterization** into one class (~900-line deletion; ClanInterface 911 vs PartyInterface 973 lines ~75% identical) — after the fleet migration.
- **Dead legacy login path deletion** (`useVoidscapeLogin()` constant-true at mudclient.java:11584; dead `loginScreenNumber==3` branch + never-instantiated `panelLoginOptions`) — logged in `docs/DIVERGENCE.md`, separate program.
- 16:9 viewport presets (append-only to `VIEWPORT_PRESETS`).

## 8. Sanctioned global pixel changes (DIVERGENCE.md entries required)

1. `drawBoxAlpha` green-channel mask `0xFFC4 → 0xFF00` (GraphicsController.java:1617, decompiler artifact `(color & 'ￄ') >> 8`). Affects every translucent box in both skins.
2. `drawShadowText` unified onto the auto-shadow convention (GraphicsController.java:2643-2660 vs 2107-2115).

Each ships as an isolated commit with both-skin before/after screenshot pairs (panels + a world scene) attached to its DIVERGENCE.md entry.

## 9. Adoption roadmap

**Status: ALL TEN SLICES COMPLETE (2026-07-04).** Every slice below shipped, built green, and was workbench-verified (trade/duel flows exercised live with two accounts; see DIVERGENCE.md for per-slice entries). Post-program additions on the same system: the left vitals stack (FPS/Hits/Prayer glass bars, RSCRevolution palette via VITAL_* tokens), the six-tab glass chat strip with the Global history tab, and the clan/party client-code removal. Remaining backlog: §7 items (dead legacy login path deletion, 16:9 viewport presets), mobile chat dock Global tab, wheel-capture routing in the modality table, and the reconnect-box software-renderer move.

| # | Slice | Core outcome |
|---|---|---|
| 1 | Token foundation | `UiSkin` + `UiAnchor`; every existing voidscape hex repointed to tokens (zero layout change); primitive fixes (§8) + `@col@` codes |
| 2 | Verification hardening | WorkbenchServer coords → layout accessors; `scripts/ui-regression-matrix.sh` standing harness |
| 3 | Panel.java dual-skin | Quest tab + all legacy lists/inputs join the theme; white search boxes die; wheel-scroll everywhere |
| 4 | Bank repair | 580px-on-512 overhang fixed; opaque backdrop behind grid; chat clear of preset buttons |
| 5 | Interface fleet | `InterfaceChrome` kit; 7 grey interfaces + item tooltip conformed; duplicate `drawButton`s deleted |
| 6 | Auction house | Navy/olive/tan → tokens; tab row below HUD safe-area; standard close |
| 7 | Modal kit + centering | `drawModal` for the 8 black-box dialogs; classic trade/duel centered via `UiAnchor` (no reskin) |
| 8 | Trade/duel/shop skin | Dual-skin chrome for the highest-risk dialogs (quarantined; one method per commit) |
| 9 | HUD unification | Chat tabs, banners, kill feed, XP counter onto the kit; the modality table lands |
| 10 | Responsive + pre-game | First-run window preset auto-pick; unified gesture zones; login card centering; display settings group |
