# Task: replace IP-based global chat flags with a player-chosen country flag

Self-contained implementation prompt. All context needed is in this file plus the referenced
code. Read `AGENTS.md` (repo root), `server/AGENTS.md` for server work, and
`Client_Base/CLAUDE.md` for shared-client rules first — their hard rules apply (use
`scripts/*.sh`, never touch `upstream/openrsc-snapshot/`, document divergence, protocol changes
follow the cross-client sync rule in `docs/subsystems/networking-protocol.md`).

## Problem

Global chat lines are prefixed with a country flag derived from the sender's **IP address**
(async HTTP geolocation against `api.country.is`). Instead, players should **pick their country
flag on the character-design ("new character") screen**, and that choice should drive the flag.
The IP-geolocation path goes away entirely.

## Implementation status

Implemented on 2026-07-06 as protocol/client version `10125`.

- Server-side IP geolocation was replaced by `GlobalChatCountryFlags`, which reads/writes
  `global_chat_country_code`, removes the legacy `global_chat_country_ip` cache value when a
  player submits an explicit choice, and keeps the existing show/hide toggle.
- `PLAYER_APPEARANCE_CHANGE` now carries two country bytes before the optional referral string
  for clients `>= 10125`; `SEND_UNLOCKED_APPEARANCES` appends two bytes so the design screen can
  preselect the saved value.
- `Client_Base` now has the country selector, a shared curated list, flag rendering in the picker,
  and bundled `lipis/flag-icons` assets for the selected list.
- Voidbot supports `design-character --country <cc|none>`.

## Pre-implementation trace (historical)

Line numbers are anchors as of this writing; they drift — search by symbol name.

### Server: IP → country code → in-band token

| What | Where | ~Line |
|---|---|---|
| Whole feature | `server/src/com/openrsc/server/content/GlobalChatIpFlags.java` | all |
| Token builder `flagTokenFor(player)` → `"@flg@CC "` or `""` | same | 52–62 |
| Visibility: config gate + per-player cache toggle `shouldShow` / `setShow` | same | 42–50, 105 |
| Async IP lookup: `resolvePlayerAsync`, `lookupCountryCode`, single-thread executor, in-memory IP cache | same | 23–37, 64–103, 144–184 |
| Resolved code persisted in **player cache**: keys `global_chat_country_ip` / `global_chat_country_code` | same | 29–30, 119–122, 140–141 |
| Client version gate constant (v10069) | same | ~24 |
| Config keys: `WANT_GLOBAL_CHAT_COUNTRY_FLAGS` (default true), `GLOBAL_CHAT_COUNTRY_LOOKUP_URL`, `GLOBAL_CHAT_COUNTRY_LOOKUP_TIMEOUT_MS`, `GLOBAL_CHAT_LOCAL_COUNTRY_CODE` | `server/src/com/openrsc/server/ServerConfiguration.java` | 134–137, ~628–631 |

The flag is **in-band text**, not packet metadata: the server prepends `@flg@CC ` to the chat
line string before it's serialized. Exactly **two** call sites:

- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java:1133` —
  `formatGlobalChatLine`: `GlobalChatIpFlags.flagTokenFor(player) + "@gre@" + name + ": @whi@" + msg`
- `server/src/com/openrsc/server/model/world/World.java:1179` — global quest messages.

(Nothing else uses `flagTokenFor` — private messages are NOT flagged.)

The message travels via the normal `SEND_SERVER_MESSAGE` path (`ActionSender.sendMessage`,
`PayloadCustomGenerator` case ~768–782); nothing flag-specific in the packet itself.

### Client: `@flg@CC` token → 13×10 flag sprite

All in `Client_Base/src/orsc/graphics/two/GraphicsController.java`:

| What | ~Line |
|---|---|
| Token detect `isCountryFlagToken` (`@flg@` + 2 ASCII letters) | 2701–2708 |
| Draw in text-render loop, advance 15px, skip 7 chars | 1970–1975 |
| `drawCountryFlagToken` — 13×10 px alpha blend | 2718–2741 |
| `loadCountryFlagAsset` — loads `Cache/voidscape/flags/{cc}.png` | 2751–2766 |
| `buildCountryFlagFallback` — procedural letterbox if PNG missing | 2795–2812 |

**Asset reality check:** `Client_Base/Cache/voidscape/flags/` currently contains only `ca.png`
(+ `ca.svg`, `LICENSE.flag-icons.txt` — lipis/flag-icons, MIT). Every other country renders the
procedural fallback box today.

Per-player show/hide toggle rides on `SEND_GAME_SETTINGS` (extra byte, gated
`Config.CLIENT_VERSION >= GLOBAL_CHAT_COUNTRY_FLAGS_CLIENT_VERSION` = 10069) —
`Client_Base/src/orsc/PacketHandler.java:2270–2273`. Keep this toggle working.

### Character-design screen (where the picker goes)

Client — `Client_Base/src/orsc/mudclient.java`:

| What | Symbol | ~Line |
|---|---|---|
| Appearance state vars (`appearanceHeadType`, `appearanceHairColour`, `appearanceSkinColour`, …) | — | 885–1063 |
| Classic panel build (2-col grid, arrow pairs at ±40) | — | 2895–2966 |
| Voidscape panel build (rows +62 apart, arrows ±42; referral text field precedent at bottom) | `createVoidscapeAppearancePanel` | 3002–3044 |
| Live 3D preview render | — | 3969–4023 |
| Arrow click handling | `handleAppearancePanelControls` | 23747+ |
| Submission → opcode 235 | `submitVoidscapeAppearance` | 23726–23745 |

Wire — outbound opcode **235 `PLAYER_APPEARANCE_CHANGE`** (custom protocol), current payload:
gender, headType, bodyType, mustEqual2, hairColour, topColour, trouserColour, skinColour,
ironmanMode, isOneXp, [hairStyle byte], [referralName RSC-string if client ≥ v10111].
This packet has been extended three times already (ironman/xp bytes, hairStyle v10062,
referralName v10111) — follow the same version-gated append pattern.

Server:

| What | Where |
|---|---|
| Parser (byte-by-byte read + `isPossiblyValid` length windows 10 / 11 / 12–24) | `server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java`, `PLAYER_APPEARANCE_CHANGE` case (~530–545) |
| Struct | `server/src/com/openrsc/server/net/rsc/struct/incoming/PlayerAppearanceStruct.java` |
| Handler: gates on `isChangingAppearance`, validates `PlayerAppearance.isValid`, first-login-only block (`getLastLogin() == 0L`) for ironman/XP/starter items | `server/src/com/openrsc/server/net/rsc/handlers/PlayerAppearanceUpdater.java:22–145` |

**Screen re-entry** (`ActionSender.sendAppearanceScreen`, opcode 59, currently **no payload**):
`MakeOverMage.java:57` (Falador), `Moderator.java:1329` (mod command), `PeelingTheOnion.java:796`
(quest), and re-sent on invalid submission (`PlayerAppearanceUpdater.java:37`). So the picker
automatically becomes the "change your flag later" path via the Makeover Mage.

Precedent for syncing state to the client *before* the screen opens:
`PlayerService.updateUnlockedPlayerSkins` (`PlayerService.java:504`) sends
`SEND_UNLOCKED_APPEARANCES` (custom opcode, `OpcodeOut.java:105`) from inside
`sendAppearanceScreen` (`ActionSender.java:215–221`).

Voidbot: `tools/voidbot/protocol.py` has `PLAYER_APPEARANCE_CHANGE: 235`; `voidbotd.py`
`design-character` command builds the packet (~680–700).

## Implemented design

1. **Canonical country list, shared client+server.** One ordered list of ISO 3166-1 alpha-2
   codes + display names, entry 0 = "None". Duplicate as a client array and a server whitelist
   set; document in `networking-protocol.md` that they must stay in sync. **On the wire, send
   the two ASCII chars, not a list index** (`0x00 0x00` = none) — list edits then can't corrupt
   stored choices.

2. **Client picker row.** Add a "Country" option to BOTH panel variants (classic build
   ~2895 and `createVoidscapeAppearancePanel`), same arrow-pair pattern as existing rows. Render
   the actual 13×10 flag beside the country name using the existing `loadCountryFlagAsset` /
   fallback path. UX note: arrow-cycling ~250 countries is painful — recommend a **curated list
   (~40–60 common countries) plus None** to start; owner can expand later. (Alternative: full
   list with hold-to-fast-cycle.)

3. **Wire change.** Append the 2 country bytes to opcode 235, gated on a **new client version**
   (bump `Config.CLIENT_VERSION`; add constant like `COUNTRY_PICKER_CLIENT_VERSION`). Update the
   `PayloadCustomParser` read order carefully — `referralName` at the tail is variable-length,
   so place the fixed 2 bytes before the referral string read (mirroring how hairStyle slotted
   in) and widen `isPossiblyValid` windows. Old clients keep sending the old shape and must
   still work (country simply stays unset).

4. **Server handling.** `PlayerAppearanceUpdater` reads the code, validates against the
   whitelist (reject → treat as unset, don't kick), and stores it in the player cache under the
   **existing key `global_chat_country_code`** (reuse it). Stop writing `global_chat_country_ip`.
   Apply on **every** submission, not just first login — that's what makes the Makeover Mage the
   change-flag path. "None" clears the key.

5. **Gut the IP path.** `GlobalChatCountryFlags.flagTokenFor` reads the chosen code from player
   cache, else returns empty. `resolvePlayerAsync`, `lookupCountryCode`, the executor/caches, and
   the three lookup config keys (`GLOBAL_CHAT_COUNTRY_LOOKUP_URL`, `_TIMEOUT_MS`,
   `GLOBAL_CHAT_LOCAL_COUNTRY_CODE`) are gone. `WANT_GLOBAL_CHAT_COUNTRY_FLAGS`, the per-player
   show/hide toggle, the `@flg@` token format, and both call sites remain.

6. **Existing players.** They already have IP-derived codes stored under
   `global_chat_country_code`. Reusing the key **grandfathers them in** — current flags persist
   until they visit the Makeover Mage. (Recommended. Alternative: one-time cache wipe of that
   key to reset everyone to None — owner's call, see Decisions.)

7. **Pre-select on re-entry.** The client must show the player's current choice when the design
   screen reopens (otherwise a makeover visit that doesn't touch the selector would silently
   reset the flag to None). Send the current code to the client before/with the screen —
   cleanest is a small version-gated addition alongside `SEND_UNLOCKED_APPEARANCES` in
   `sendAppearanceScreen` (either extend that packet's payload behind the version gate, or add a
   sibling custom packet). Client initializes the selector from it.

8. **Assets.** Bundle 13×10 PNGs for every code in the curated list into
   `Client_Base/Cache/voidscape/flags/` (source: lipis/flag-icons, license file already in
   tree). The fallback letterbox works but looks rough inside a picker.

9. **Voidbot.** Extend `design-character` in `voidbotd.py` with `--country cc` (and bump its
   packet build). Required for fresh-account E2E testing.

10. **Docs.** `docs/subsystems/networking-protocol.md`: new version-gate entry for the 235
    payload change + the pre-select packet. `docs/DIVERGENCE.md`: one paragraph. Update the
    `docs/CONFIG-MATRIX.md` rows for the removed lookup keys. Update any player-facing copy that
    still describes network-derived country selection.

No DB schema change — the player cache (`Cache.java`, K/V persisted per player) already backs
this; the code was already being stored there by the IP path.

## Gotchas

- Opcode 235 and the appearance screen are a **shared client/server contract** —
  `Client_Base` is the shared base (PC + Android), so edit there, and version-gate everything;
  there's no Android toolchain on this machine, PC client is the verification target.
- `PlayerAppearanceUpdater` sets `setSuspiciousPlayer` on unexpected packets — make sure the new
  bytes parse cleanly for both old and new client versions.
- The handler's first-login block also fires teleport/onboarding — don't move the country logic
  inside it.
- Line numbers above will drift; search by symbol.

## Decisions to confirm with owner (recommendations inline)

1. Curated country list (~40–60 + None) vs full ISO list — **recommend curated** given arrow UX.
2. Grandfather existing IP-derived flags vs reset all to None — **recommend grandfather** (free,
   via key reuse).
3. Should "None" be the default for new characters (no flag until picked)? — **recommend yes**.

## Acceptance criteria

- Fresh account (voidbot register → design-character → workbench): Country row visible on the
  design screen; pick a country; global chat message shows that flag. No HTTP geolocation call
  occurs anywhere (grep server logs / code for the lookup URL).
- Pick None → no `@flg@` token in the line.
- Makeover Mage reopens the screen with the current country pre-selected; changing only the
  country updates the flag and leaves appearance intact.
- Previous-version client can still complete character creation (old packet shape accepted).
- Per-player flag show/hide toggle and `WANT_GLOBAL_CHAT_COUNTRY_FLAGS=false` still suppress flags.
- `scripts/build.sh` passes; `tools/voidbot design-character --country ca` works;
  `tests/smoke.sh` still green.
- `DIVERGENCE.md`, `networking-protocol.md`, `CONFIG-MATRIX.md` updated.
