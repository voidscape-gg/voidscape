# Title System Overhaul V2 — Prestige, Ceremony, Honorifics

> **Implementation scope/status — 2026-07-15 (authoritative):** the approved V2
> now includes the complete **main-game-loop-only** title pass: a 65-row catalog
> (25 Renown / 20 Feat / 7 Supreme / 13 Unique), no auto-equip, the Void Glass
> `Wear it` / `Not now` ceremony, one prefix honorific plus one suffix epithet,
> Saint, Knight, and Warlord honorifics, The Court, announcement discipline, and
> official client cohort `10138`. Titles still must not be earned from minigames, arenas,
> or side events. The six removed ids remain migration-only, and Grandmaster,
> Knight of the Void, Void Arena Champion, Duelist, and Unmasker are not catalog
> rows. The retained `knight` row is instead the approved all-combat-99, combat-123,
> maximum-quest-points main-loop honorific. Founder-prefix/throne/seat-cap/`self_made`
> ideas remain deferred. This
> block and `docs/subsystems/player-titles.md` override conflicting older slice
> language below; the older body is retained as the design and historical baseline.

Audience: an implementing agent working in this repo. Read `CLAUDE.md`, `server/CLAUDE.md`,
`server/plugins/CLAUDE.md`, and `Client_Base/CLAUDE.md` before starting — their hard rules apply to
everything below. Build with `scripts/build.sh`. This brief builds ON TOP of the shipped V1 system
(`docs/TITLE-OVERHAUL-BRIEF.md`, DIVERGENCE 2026-07-05 and 2026-07-07); nothing here re-litigates V1
decisions that stand. Every file:line below was verified against the tree on 2026-07-14.

**Goal:** make a displayed title feel *earned*. Kill the paths that hand out titles in the first
session, make wearing a title an explicit opt-in with a ceremony, add a SUPREME tier for
handful-of-players-ever feats, and introduce prefix **honorifics** — stations like `Sir Vex the
Immortal` — as a second, rarer axis of status.

**Current state:** the cull, retained-title rescope, Supreme tier, two-slot honorific model,
main-loop additions, Court, glass ceremony, and `10137` appearance tail are represented in the
current implementation. The official catalog and exclusions are summarized in
`docs/subsystems/player-titles.md`. Historical pre-implementation notes and rejected minigame rows
below are not authorization to restore them.

---

## Part 0 — Historical pre-V2 baseline (verified 2026-07-14)

| Where | What |
|---|---|
| `server/src/com/openrsc/server/content/PlayerTitle.java` | 62-title catalog (25 Renown / 20 Feat / 17 Unique), Tier + UniqueKind enums, requirement engine, unlock/grantContested/revoke, cache keys, migration `pt_migration_10123`. |
| `PlayerTitle.java:403-405` | **Auto-equip:** `unlock()` sets the title active when `active(player) == null`. This is the "applied by default" behavior V2 removes. Same branch in `grantContested` at `:438-442`. |
| `PlayerTitle.java:402` | Unlock feedback is one chat line (`@mag@Title unlocked: …`). No popup. |
| `PlayerTitle.java:766-785` | `updateMagnate` — **month-rollover degeneracy**: fresh month ⇒ `leaderScore = 0` (`:771`); any positive seller volume passes the `sellerVolume < leaderScore` guard (`:772`) and a non-owner reaches `grantContested` (`:782-784`) ⇒ gold unique + world announcement for the first sale of the month. An equal-volume tie-guard already exists at `:777` — ties do NOT steal today. Magnate ownership never month-expires (`contestedSeasonExpired` `:967-972` handles only `WARLORD_WASTES`). |
| `server/plugins/.../authentic/commands/RegularPlayer.java:153-660` | `::titles` hub / paged catalog / detail views over the standard options-menu transport; rows embed `~vstitle~` metadata for the custom client's achievement board. `TITLE_CATALOG_PAGE_SIZE = 10` (`:54`). |
| `server/src/com/openrsc/server/GameStateUpdater.java:1093-1101` | Appearance packet appends, in order: title string (clients ≥ 10052, `:1093-1095`), tier byte (≥ 10123, `:1096-1098`), **modern-hair byte (≥ 10057, `:1099-1101`) — the current FINAL field**. The 10123 tier byte was itself inserted *before* hair, so "after the tier byte" is NOT the end of the record. |
| `Client_Base/src/orsc/PacketHandler.java` | Client decode: title `:2996-3003`, tier `:3004-3008`, hair `:3009-3013`; byte-aligned null-player branch consumes the same fields at `:2953-2958`. voidbot mirrors the same order at `tools/voidbot/voidbotd.py:1131-1135`. |
| `Client_Base/src/orsc/mudclient.java:19370-19386, 19469-19485` | Overhead label: name yellow + title in tier color at `y-14`, hidden while speaking; **suffix-only join rule** (`Name the X` / `Name, X`). `titleTierColor`: renown `0xE8E8E8`, feat `0xB980FF`, unique `0xFFD24A`, unknown codes fall through to the renown default (graceful). |
| `Client_Base/src/orsc/mudclient.java:7469-7956` | Glass title board (hub/catalog/detail) — string-pattern-matched skins over the options menu; `~vstitle~` row cells parsed at `:7900-7907`. Client menu array is fixed at **20 options** (`mudclient.java:590`) with no bounds check in the decode — a menu with more than 20 options crashes custom clients. |
| `server/.../content/announcements/WorldAnnouncementService.java:101-149` | Unique-claim and contested-transfer world announcements. `grantContested` fires the transfer announcement for EVERY contested grant (`PlayerTitle.java:453`) — this one shared call is both the Champion's season announcement and the Warlord/Magnate churn. |
| `server/.../plugins/authentic/commands/Admins.java:1502-1555` | `::granttitle` / `::revoketitle`. |
| Persistence | All state in `player_cache`: unlocks `pt_u_<id>`, active `player_title_active`, first-claim dates `pt_first_date_<id>` (per-player AND a global copy via `stampFirstClaimDate` `:927-931`; global cache = `player_cache` rows with playerID 0), contested tokens `title_contested_token/month/score_<id>` (global), counters `title_*`. No title-specific tables. `player_cache.key` is unindexed (full scan per owner lookup — current scale is fine). |
| Hooks | Level-up (`Skills.java:324`), NPC kill (`Player.java:4514`), QP gain (`Player.java:2497`), wildy PK (`Player.java:2853-2855`, inside the `inWilderness()` branch at `:2828` — so `player.getKills()` counts wilderness PKs only), per-action counters in skill plugins, AH sale (`BuyMarketItemTask.java:135`), arena season (`VoidArena.java:1857`). **No login hook.** |
| Offline grants | `grantContested` has an offline branch that writes the player_cache row directly by playerId (`PlayerTitle.java:457-472`, via `querySavePlayerCacheValue`). `PlayerTitle.incrementCounter` (`:494-502`) requires an ONLINE player — it writes `player.getCache()`. |

### Why titles feel cheap (the offenders)

1. **Auto-equip** — first unlock silently appears overhead; nobody chose to wear anything.
2. **`magnate`** — first AH sale of each month claims a gold unique (see above).
3. **`first_agil30`** — permanent gold unique for ~1-2h of play (first to 30 Agility).
4. **`bronze_reaper`** — one PK while holding a bronze weapon; new players fight in bronze by default.
5. **`giant_killer`** — NPC ≥ 2× your combat: trivially safespotted at low level, and *harder* as you
   level. Perverse.
6. **`gravewalker`** — Undead Siege lends combat 40 / magic 70 + free supplies; 4-man carries make it
   a day-one purple. (Launch-locked content; fix before it unseals.)

Announcement churn (every contested transfer is world-announced) multiplies the effect.

---

## Part 1 — Doctrine

V1 principles that stand unchanged: small catalog, no trickle/participation titles,
overhead-label-only (no chat prefixes anywhere), no perks/rewards, FIRST uniques never backfilled
or recycled.

V2 **deliberately changes** one V1 rule: V1's "only one title is active at a time" becomes **one
active title per slot** — one suffix epithet + one prefix honorific (Part 4). This is an owned
design change, not drift.

V2 adds:

- **Nothing is worn by default.** Unlocking never equips. Wearing a title is always an explicit
  player action, prompted by a ceremony.
- **The prestige pyramid.** RENOWN (white) < FEAT (purple) < SUPREME (crimson, new) < UNIQUE (gold).
  Supreme = "a handful of players will ever have this." Unique = "exactly one player has this."
- **Epithets vs. stations.** Suffix titles describe what you *did* (`the Immortal`). Prefix
  honorifics describe what you *are* (`Sir`, `Saint`, `Champion`, `Warlord`) — conferred in
  ceremonies or held as offices, displayed before the name, stackable with one epithet.
- **Announcement discipline.** World announcements are reserved for history being made (Part 5).
  Contested offices announce at first claim of a period and at crowning — not per lead change.
- **Never let honorifics trickle.** Four at launch. Every future "wouldn't it be cute if X gave a
  prefix" is denied by default.

---

## Part 2 — Catalog changes (exact data)

### 2.1 Cull (delete the enum entries, their hooks, and their cache keys)

| id | Why |
|---|---|
| `first_agil30` | 1-2h gold unique. Cache cleanup: per-player `pt_u_first_agil30` and `pt_first_date_first_agil30` (migration, Part 7); the only global key it writes is the `pt_first_date_first_agil30` row at playerID 0 — there is no global owner key (ownership is derived by scanning per-player `pt_u_*`), and FIRST uniques never write contested keys. |
| `giant_killer` | Safespot-engineerable; anti-scaling. Remove the `checkGiantKiller` call at `Npc.java:420` and the method (`PlayerTitle.java:571-575`). |

### 2.2 Rescope (keep the id, change the requirement; migration revokes existing unlocks of `bronze_reaper`, `gnome_baller`, `gravewalker` — `magnate` is contested state, not an unlock, and lapses on its own via the `contestedSeasonExpired` addition below)

| id | New requirement | Notes |
|---|---|---|
| `bronze_reaper` | Kill a player of **equal or higher combat level** in the wilderness while wielding a bronze weapon | Was: any bronze PK. The existing hook (`Player.java:2854`, `checkBronzeWeaponPlayerKill`) already has killer and victim in scope — add the combat-level comparison there. |
| `gravewalker` | Clear wave 10 of Undead Siege **solo** | Solo = the instance was created with exactly one player AND no other player was ever active in it — track a max-concurrent-players field on `UndeadSiegeInstance` (constructor roster at `:71`, award at `:894` in `awardPayout`) and check it at award. Measuring only at award time would let a 4-man carry that empties out before wave 10 count as solo. Content is launch-locked; re-verify when it unseals. |
| `gnome_baller` | 500 gnomeball goals (was 100) | Counter `title_gnomeball_goals` persists; unlock revoked by migration, re-earned at 500. |
| `magnate` | Highest AH seller volume this month, **minimum 250,000 gp** | Add `MAGNATE_MIN_MONTHLY_VOLUME = 250_000` as an early-return floor in `updateMagnate`, mirroring the `WARLORD_MIN_MONTHLY_KILLS` guard (declared `PlayerTitle.java:111`, applied `:747`). The equal-volume tie-guard at `:777` already prevents tie-stealing — keep it. Also add `MAGNATE` to `contestedSeasonExpired` (`:967-972`) so the office lapses at month rollover like Warlord (today last month's magnate holds over). |

### 2.3 New tier: SUPREME

- `Tier.SUPREME`, wire code **3**, chat color `@red@`, overhead color **crimson `0xE04848`**
  (client `titleTierColor` gains `case 3`). Board tier pill: crimson fill, label `supreme`.
- Wire code 3 rides the existing tier byte — no packet shape change. Older clients fall through to
  the renown default color (verified graceful); version-gate the client color as usual.
- Ladder placement: above FEAT, below UNIQUE (one-of-one outranks handful-ever). **Note:** the wire
  code ordering (3 > 2) disagrees with the prestige ordering (SUPREME < UNIQUE) — any board sort or
  comparison keyed on the tier code must special-case this; sort on an explicit rank field, not the
  wire code.

### 2.4 New titles

**SUPREME (handful-of-players-ever; world-announced on unlock):**

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `immortal` | the Immortal | Total level 1,782 with 0 lifetime deaths | New check `ZERO_DEATH_TOTAL(1782)` beside `ZERO_DEATH_COMBAT` (`PlayerTitle.java:372-373`; `getTotalLevel()` and `getDeaths()` both exist and persist), evaluated in `refreshAutomaticUnlocks`. |
| `voidtouched` | the Voidtouched | Receive all four Void chase items as your **own** NPC drops | Canonical items: `ItemId.VOID_AMULET` (1595), `VOID_MACE` (1596 — display name "Void Sceptre", byName alias exists), `VOID_SCIMITAR` (1593), `VOID_BOW` (1594 — "Void Shortbow"). Per-item cache flags `title_vdrop_<item>` set at the NPC drop-award path (same pattern as `dragon_crowned` at `Npc.java:916`); unlock when all four flags present. Trades don't count, and the DeathMatchArena reward-roll copies of these items (`DeathMatchArena.java:124-127`) intentionally do NOT count — NPC drops only. |
| `grandmaster` | the Grandmaster | Win 3 Void Arena ranked seasons | Counter `title_arena_seasons_won`, incremented at the season-champion award (`VoidArena.java:1857` / `awardSeasonChampion :1850`). **The champion may be offline at season reset** — when not online, write the counter via the offline read-modify-write path (`querySavePlayerCacheValue`, mirroring `grantContested`'s offline branch at `PlayerTitle.java:457-472`); the unlock + prompt then arrive via the login catch-up. |
| `scourge` | the Scourge | 1,000 wilderness player kills | Existing `PLAYER_KILLS` requirement type at 1,000 (`player.getKills()` is wilderness-only by construction). |
| `midas` | the Midas | 25,000,000 gp lifetime auction-house sale volume | Counter `title_ah_volume` incremented per committed sale at `BuyMarketItemTask.java:135`. **The seller is routinely offline when their listing sells** — use the same offline cache write as `grandmaster`; never scan `auction_sales` per event (`getAuctionSellerVolumeSince` exists at `PlayerTitle.java:563-564` for the monthly magnate math only). |
| `huntmaster` | the Huntmaster | Kill every huntable NPC type | Breadth check over the persistent `killCache` (`Player.java:1512`, `Map<npcId,count>` — distinct ids = keySet). The huntable list is derived at boot from NPC defs, excluding unattackable/quest-locked/unreleased ids — **curate the exclusion list at implementation time; do not guess.** Check runs on NPC kill only when the killed id was previously absent from the cache. |

`self_made` (maxed ironman) is **deferred to the backlog** — verified 2026-07-14: ironman
infrastructure exists (`Player.isIronMan()` `:627`, `iron_man` column, live packet 113) but there is
**no player-facing path to become one** on voidscape (`character_creation_mode 0`, iron-man NPCs not
spawned, no command). Revisit only if the owner turns ironman on.

**FEAT additions:**

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `fishmonger` | the Fishmonger | Cook 25,000 swordfish | Existing counter `title_swfish` (incremented at `ObjectCooking.java:222`; shared with the `first_swfish` 50k record — intentional layering, same precedent as coal's 10k/25k split; `checkCounterTitles` already loops every title bound to a key). |
| `duelist` | the Duelist | Win 250 staked duels | Counter `title_duel_wins`. **There are TWO winner-completion paths:** `DuelJournalService.complete` (`:46`, called from `Duel.java:269`) and, when both players are on proof-capable clients, `DuelProofService.completeSettlement` (`:449`) — `Duel.java:264-270` routes to one OR the other. Increment at a single shared point in `Duel.dropOnDeath` after either completion succeeds, filtered to duels with non-empty stakes. |
| `headhunter` | the Headhunter | 250 bounty-mark kills | Reads existing cache key `bounty_kills` (`BountyHunter.java:26`) — generalize the COUNTER requirement to accept a non-`title_` key, or mirror into `title_bounty_kills` at the bounty hook; implementer's choice, keep it cheap. |
| `uncaught` | the Uncaught | Escape 100 bounties on your own head | Existing cache key `bounty_escapes` (`BountyHunter.java:28`), same mechanism as `headhunter`. |
| `keywarden` | the Keywarden | Open 500 Void Chests | New counter `title_void_chests` at the chest-open path in `VoidChest.java`. |

**UNIQUE addition:**

| id | Display name | Kind | Requirement |
|---|---|---|---|
| `unmasker` | the Unmasker | FIRST | First player to correctly unmask the Wanderer (AI player). MANUAL until the Wanderer ships (same precedent as `riftbound`); hint text: "Something walks among you. Unclaimed until someone proves it." The enum entry + teaser hint ship in slice 2 (it is MANUAL, so this is pure catalog data); there is no separate seeding work. |

Two further SUPREME entries — `knight_void` and `saint` — are honorifics and are specified in
Part 4.3; they ship with slice 3, not slice 2.

Id-collision check (verified): none of the 15 new ids collide with the 62 existing ids or display
names under `byId` normalization. Legacy pre-V1 `void_touched` cache keys are inert (`pt_u_void_touched`
≠ `pt_u_voidtouched`; the V1 migration wipes them anyway).

Everything else in the current catalog is **kept unchanged**.

---

## Part 3 — Award ceremony and equip flow

### 3.1 Remove auto-equip

Delete the `if (active(player) == null) setActive(...)` branch in `unlock()`
(`PlayerTitle.java:403-405`) and the equivalent in `grantContested` (`:438-442`, keep the
appearance-flag refresh). Unlocking never changes what is displayed.

### 3.2 The unlock prompt (V1: server-only, ships in slice 1)

**Transport constraint (verified):** `multi()` requires a plugin-script context
(`Functions.java:327-328` dereferences the context `PluginTask` and blocks its thread), but
`unlock()` fires from core game-thread paths (`Skills.java:324`, `Player.java:2497/2853/4514`).
Calling `multi()` from unlock would NPE. Additionally, `Player.setMenuHandler` silently replaces any
pending menu (`Player.java:1666-1670`) and an abandoned `multi()` keeps polling the shared
`questionOption` field — so prompts must never race an open menu.

Design:

- **Persistent queue:** unlock appends the title id to a player-cache key `title_prompt_queue`
  (ordered, `;`-separated). The chat line (`:402`) still prints immediately — it is the always-on
  fallback.
- **Flush point:** a per-player eligibility check (tick-driven or piggybacked on an existing
  per-tick player update) pops ONE queued id when the player is eligible and sends a two-option
  menu via `player.setMenuHandler(new MenuOptionListener(...))` + the menu send path used by
  `ActionSender` — a non-blocking callback, NOT `multi()` from core. Alternatively submit a one-shot
  plugin task and use `multi()` inside it; either way, document the choice in the code.

  > **You have earned the title of the Kingslayer.**
  > 1. Wear it
  > 2. Not now

  "Wear it" → `setActive` (or the honorific slot for PREFIX titles); "Not now" → dequeue, nothing
  else (title stays in `::titles`).
- **Eligibility (defer while ANY is true):** in combat; in a duel; in a trade; bank or shop
  interface open; `player.getMenuHandler() != null` or any dialog/menu pending; inside a Void Arena
  match or Sir Charles challenge; inside an Undead Siege or Death Match Arena instance. (There is no
  unified "in minigame" predicate — this is the enumerated list.)
- **Dismissal:** a `-1` reply (panel-miss, combat cancel, timeout) **re-queues** the prompt rather
  than dropping it.
- **Login:** the queue is a cache key, so it survives logout; the login flush (3.3) re-offers
  pending prompts. Players whose titles unlocked *before* V2 get no retroactive prompts — they
  re-equip manually via `::titles` (deliberate; do not synthesize prompts from `pt_u_*`).

### 3.3 Login catch-up

Add a `refreshAutomaticUnlocks` call on login — there is currently **no** login hook, so locate the
login-completion path (where the player is registered into the world and their cache is loaded) at
implementation time and hook there; then the prompt-queue flush fires once the player is eligible
(same eligibility check as 3.2 — the flush point and the login hook may share one per-tick check). Offline-granted titles (contested transfers, offline counter awards)
enqueue their prompt at grant time via the same cache key.

### 3.4 The glass ceremony modal (V2: client bump slice)

**Do not use a message-channel token to drive the modal.** (Verified: FarmSim's `@vsfarmsim@` modal
and the cracker reel are display-only — no existing feature marries a metadata modal to a pending
menu reply.) Use the **proven title-board pattern instead**: the prompt menu embeds a
`~vstitleaward~` metadata marker in its option/header strings (title id|display|tier|kind), exactly
like `~vstitle~` catalog rows; the custom client pattern-matches it (like `isVoidscapeTitleMenu`,
`mudclient.java:7618`) and renders an InterfaceChrome glass modal (per `docs/UI-STYLE-GUIDE.md`
§4.3) — title name large in tier color, [Wear it] / [Not now] buttons that reply with the ordinary
option indices via `sendOptionsMenuChoice`. The modal IS the menu skin: it opens with the menu,
auto-dismisses when the server closes it, and generic clients render the readable fallback text.
No new opcode, no new reply channel.

---

## Part 4 — Honorifics (prefix stations)

### 4.1 Model

- Titles gain a `Position { SUFFIX, PREFIX }` field; existing catalog is all SUFFIX. PREFIX titles
  ("honorifics") additionally carry a `prefixForm` render string (see 4.3).
- **Second active slot:** cache key `player_honorific_active`, parallel to `player_title_active`.
  A player may wear one honorific + one epithet simultaneously. **A title occupies at most one
  slot** — equipping it in one slot clears it from the other. `::titles clear` clears both; the
  detail view offers "Wear as honorific" for PREFIX titles. `revoke()` and contested transfers must
  clear the honorific slot when the revoked title occupied it (extend the existing active-slot
  self-heal at `PlayerTitle.java:625-629`).
- **PREFIX conversion of live ids:** when `void_arena_champion` / `warlord_wastes` become PREFIX,
  any `player_title_active` pointing at them migrates to the honorific slot on first touch
  (self-heal extension), so nobody keeps rendering an office as a suffix.
- **Pre-bump clients:** the legacy title-string field (`GameStateUpdater.java:1093-1095`, sent to
  ALL clients ≥ 10052) carries the **epithet only**. A player wearing only an honorific shows no
  overhead title on old clients — accepted degradation, checked in Part 9 item 10.
- Honorifics live in the same enum/catalog/board; the board shows a `prefix` marker on their rows.

### 4.2 Overhead render

`Sir Vex the Immortal` — prefix in its tier color, name yellow `0xffff00`, epithet in its tier
color, single line at `y-14`, hidden while speaking (unchanged). Join rules: prefix + space + name;
then the existing suffix join. **Length cap:** if the combined label exceeds 40 **characters**
(deterministic, not pixel-measured), drop the epithet from the render (the player can clear one;
never truncate mid-word).

### 4.3 The four launch honorifics

| id | Board name | prefixForm | Tier | Lifecycle | Requirement |
|---|---|---|---|---|---|
| `knight_void` | Knight of the Void | Sir / Dame / Knight (player-chosen, stored in `pt_hform_knight_void`) | SUPREME | permanent | Defeat Sir Charles in the Void Arena solo challenge (first victory). |
| `saint` | the Saint | Saint | SUPREME | permanent | Level 99 Prayer **and** maximum quest points (composite check in `refreshAutomaticUnlocks`; both predicates exist — `SKILL_LEVEL` + the runtime-derived `MAX_QUEST_POINTS`). |
| `void_arena_champion` | the Void Arena Champion | Champion | UNIQUE | contested (existing) | Existing seasonal wiring; entry gains `Position.PREFIX` + prefixForm. |
| `warlord_wastes` | Warlord of the Wastes | Warlord | UNIQUE | contested (existing) | Existing monthly-PK wiring; entry gains `Position.PREFIX` + prefixForm. |

Keep the ids `void_arena_champion` and `warlord_wastes` stable — they carry live contested state.
Their board names and detail views are unchanged; only their worn form moves in front of the name.

**Dub dates:** `unlock(knight_void)` and `unlock(saint)` stamp a **per-player** date key
(`pt_first_date_<id>`, epoch seconds) directly. Do NOT route through `stampFirstClaimDate`
(`PlayerTitle.java:927-931`) — it also writes a global single-owner key, which is wrong for
multi-holder honorifics. The Court reads the per-player key.

**Backlog (explicitly deferred, do not build now):** `founder` prefix form; seat-capped knighthood
with honor-duel challenges (the Kingsguard model — duel receipts already support it); `King/Queen`
apex office; `self_made` (Part 2.4).

### 4.4 The knighting ceremony

Hook: the Sir Charles victory path. Verified topology: `DmKing.onKillNpc` (plugin,
`plugins/.../custom/npcs/DmKing.java:118-122`) → `VoidArena.handleDmKingNpcKilled` →
`resolveDmKingVictory` (`VoidArena.java:1141-1161`), which already fires a one-shot first-victory
world broadcast gated on the per-player `void_arena_dmking_broadcast` cache flag (`:1156-1160`).

Per `server/CLAUDE.md` rule 6, **dialogue lives in the plugin layer**: have `VoidArena` expose a
first-victory signal (the existing one-shot gate is the anchor) and run the ceremony from
`DmKing.onKillNpc`:

1. Sir Charles dialog (`npcsay`/`multi` in the plugin): *"Three hundred bouts, and none had bested
   me. Kneel."* → chooser: **Rise as… Sir / Dame / Knight** (stores `pt_hform_knight_void`).
2. `unlock(knight_void)` → stamps the per-player dub date → world announcement (Part 5) → the
   standard wear-prompt (Part 3) offering the honorific slot.

**Replace, don't add:** the knighting announcement supersedes the existing
`void_arena_dmking_broadcast` first-win broadcast — one world line per first victory, form-aware.
Players who beat Sir Charles pre-V2 (flag already set) are knighted on their next victory.

Subsequent victories: no repeat ceremony. The dialog is skippable-safe: if the player declines or
disconnects mid-chooser, default the form to `Knight` (changeable later via a "Change form" option
in the title detail view, `knight_void` only).

### 4.5 The Court

New hub option on `::titles`: **The Court** — a roster of every current honorific holder, over the
same options-menu transport with `~vstitle~`-style rows (readable fallback text for generic
clients): Knights of the Void with dub dates (per-player `pt_first_date_knight_void`), reigning
Champion (+ rating), reigning Warlord (+ monthly kills), Saints. Custom client renders it as a board
tab beside All/Unlocked/Renown/Feat/Unique (client work rides the bump slice; server rows work for
everyone immediately). Adding the hub option is safe for the existing skin — `isVoidscapeTitleMenu`
matches on the options that are present, it does not require an exact option set.

Implementation notes:
- Knights/saints need an owner *list*: add `queryPlayerCacheOwners(key, limit)` beside
  `queryPlayerCacheOwner` (same JOIN, drop the LIMIT 1 — `MySqlQueries.java:107-110`; Sqlite
  inherits the MySql impl). `player_cache.key` is unindexed — same full-scan cost as the existing
  owner lookup, acceptable at current scale; add an index or cache the roster if the Court gets hot.
- Page size: `TITLE_CATALOG_PAGE_SIZE` (10). **Hard limit:** total menu options per page (rows +
  nav + Close) must stay ≤ 20 — the custom client's menu array is fixed at 20 and overflow crashes
  it.

---

## Part 5 — Announcement discipline

| Event | Announce? | Copy template |
|---|---|---|
| UNIQUE first-claim | Yes (gold, existing) | `Let it be known: <name>, <title>.` |
| SUPREME unlock | Yes (crimson, new) | `Let it be known: <name>, <title>.` |
| Knighting (replaces the existing Sir Charles first-win broadcast) | Yes | `Rise, Sir <name>, Knight of the Void.` (form-aware) |
| Sainthood | Yes | `Let it be known: Saint <name> walks among us.` |
| Contested office **first claim of a period** | Yes | `<name> has claimed <board name>.` (slice 3 upgrades to the form-aware `<prefixForm> <name> …` once prefixForm exists) |
| Contested office **crowning at period end** | Yes | `Warlord <old> has been overthrown. All hail Warlord <new>.` / `A new Champion is crowned: <name>.` |
| Contested **lead changes mid-period** | **No** (remove) | Holder + previous holder still get private messages (keep). |
| RENOWN / FEAT unlocks | No (chat line + prompt only) | — |
| Honorific holder dies in wilderness PvP | Yes (slice 4) | `<Killer> has slain <full styled name> in the Wilderness!` |

**Mechanics (verified constraints):**
- The transfer announcement currently lives INSIDE `grantContested` (`PlayerTitle.java:453`) — the
  same call is the Champion's season announcement and the Warlord/Magnate churn. **Hoist it out**:
  `grantContested` stops announcing; each call site decides. Season award (`VoidArena.java:1857`
  path) announces the crowning; `updateWarlord`/`updateMagnate` announce only a first-claim-of-period
  grant, and stay silent on mid-period steals.
- **There is no scheduler / month-rollover trigger.** Crowning is lazy: when
  `updateWarlord`/`updateMagnate` first runs in a new month (`recordedMonth != month`), emit the
  crowning for the CLOSED month's recorded leader (from `title_contested_month/score_<id>`
  metadata) before resetting. A month with zero activity delays the crowning until the next event —
  acceptable, matches the existing lazy-expiry precedent (`contestedSeasonExpired`).
- Magnate becomes a true monthly office via the `contestedSeasonExpired` addition (Part 2.2).
- Update `WorldAnnouncementService` templates to the "Let it be known" voice in the same pass.
- **Dispatch rule for SUPREME announcements:** `unlock()` announces SUPREME titles with the generic
  template by default; a title may carry a bespoke template (`saint` does), and ceremony-announced
  titles (`knight_void` — the plugin ceremony announces) set a flag so `unlock()` skips the generic
  line. One world line per unlock, never two.
- **Slice mapping:** slice 1 implements the hoist, the mid-period silence, the lazy crowning, and
  the first-claim line (name-only form). The SUPREME/Knighting/Sainthood rows activate with slices
  2-3 when those titles exist; the wildy-death row is slice 4.

---

## Part 6 — Protocol, client, voidbot

- **One client bump** covers all V2 client work (SUPREME color, honorific fields, ceremony modal
  skin, Court tab). It shipped as cohort `10137`, advancing the pre-V2 `10136` cohort; the
  matching server presets, voidbot default, config matrix, and networking history move together.
- **Appearance packet:** append the **honorific render string** (resolved prefixForm, empty if
  none) + **honorific tier byte** at the **TRUE END of the appearance record — after the
  modern-hair byte**, which is currently the final field. (Wire order today: title string [10052] →
  tier byte [10123] → modern-hair byte [10057]; the 10123 tier byte was itself a mid-record insert,
  so "after the tier byte" is NOT the end.) Gate on `PLAYER_HONORIFIC_CLIENT_VERSION`. Mirror the
  two new reads in all THREE decoders: server write after `GameStateUpdater.java:1099-1101`; client
  `PacketHandler.java` normal branch after `:3009-3013` AND the byte-aligned null-player branch
  after `:2957-2958`; voidbot after the hair read at `voidbotd.py:1131-1135`. No opcode changes, no
  ordinal moves (`Client_Base/CLAUDE.md` rule 1).
- **Client render:** `titleTierColor` gains `case 3 → 0xE04848`; prefix segment render per 4.2;
  board tier pill for `supreme` (sort by explicit rank, not wire code — Part 2.3); Court tab;
  ceremony modal as a menu skin (Part 3.4).
- **voidbot** (extend FIRST, per root `CLAUDE.md`): appearance decode gains
  `honorific`/`honorific_tier` in `state players`/`state all`; the unlock prompt is a standard
  options menu (already handled by `menu-reply`); add a `wait title-prompt` event if the smoke flow
  needs to block on it. Bump `VOIDBOT_CLIENT_VERSION` alongside.
- The V1 prompt (slice 1) needs **zero** client or voidbot work — it is a plain options menu.

---

## Part 7 — Persistence and migration

New cache keys (per-player unless noted): `player_honorific_active`, `pt_hform_knight_void`,
`title_prompt_queue`, per-player dub dates `pt_first_date_knight_void` / `pt_first_date_saint`,
counters `title_arena_seasons_won`, `title_ah_volume`, `title_duel_wins`, `title_void_chests`,
flags `title_vdrop_<item>` ×4. No schema changes; everything rides `player_cache`.

**Counter policy:** new counters start at zero — pre-V2 AH volume, past arena seasons, and past
duels do NOT count (deliberate; pre-launch history is test data). Do not backfill from
`auction_sales`, `voidarena_ranked_matches`, or duel receipts.

One-time lazy migration `pt_migration_titles_v2` (same pattern as `pt_migration_10123` at
`PlayerTitle.java:787-817`, guard-keyed, runs on first touch per player):

1. Clear `player_title_active` — **founder excepted**, matching the V1 precedent (`:809-815`
   preserves a founder active title). Nobody else wears anything until they opt in. Players with
   pre-V2 unlocks get no prompt; they re-equip via `::titles`.
2. Delete unlock keys for culled ids (`pt_u_first_agil30` + its per-player
   `pt_first_date_first_agil30`, `pt_u_giant_killer`) and rescoped ids (`pt_u_bronze_reaper`,
   `pt_u_gnome_baller`, `pt_u_gravewalker`) — counters persist, so rescoped titles re-earn honestly.
3. Preserve: all other unlocks, all `title_*` counters, `founder` (always), first-claim dates for
   kept titles, contested tokens.
4. Global cleanup: the orphaned global `pt_first_date_first_agil30` row (playerID 0). There is no
   delete API for global cache (only load/save) and no boot-cleanup precedent — either add a
   one-line `queryDeleteGlobalCache(key)` (the DELETE SQL already exists inline in
   `querySaveGlobalCache*`) called once at boot, **or accept the orphan row as dead data** (nothing
   reads it once the enum entry is gone). Recommended: accept the orphan; skip the new DB machinery.

---

## Part 8 — Historical implementation slices (superseded by the authoritative scope)

This was the original staged proposal. It is not the current work queue: the authoritative block
at the top records the combined main-loop-only result and the rejected rows.

**Slice 1 — the cull + ceremony core. Server-only. MUST land before 2026-07-18 launch.**
No client bump, no packet change, no voidbot change.
- Cull + rescopes + Magnate floor/expiry (Part 2.1, 2.2), auto-equip removal (3.1), unlock prompt
  queue + flush + login catch-up (3.2, 3.3), announcement hoist + discipline (Part 5, minus the
  slice-4 row), migration (Part 7).
- Verified protocol-free: the prompt rides the existing options-menu transport; "Wear it"/"Not now"
  strings collide with no client matcher; `tests/smoke.sh` has no title dependencies; the portal's
  title map is regex-scraped from `PlayerTitle.java` so culled ids drop out automatically (its one
  known failing title assertion predates V2 — `docs/BUGS.md`).
- This slice alone fixes everything the owner originally complained about, except the glass popup
  (plain menu until the bump — owner-visible tradeoff, see Status quo).

**Slice 2 — SUPREME tier + new titles. Server-mostly; the color rides the slice-3 bump.**
- `Tier.SUPREME` + all Part 2.4 titles (including the `unmasker` enum entry/teaser; excluding
  `self_made`, deferred), their counters/hooks (with the offline-write paths), `ZERO_DEATH_TOTAL`.
- Titles work immediately; old clients show supreme in the default white until the bump.

**Slice 3 — honorifics. One client bump (carries slice 2's color + ceremony modal skin + Court tab).**
- Position/prefixForm model, honorific slot + one-slot rule + PREFIX-conversion self-heal,
  knighting ceremony (plugin-layer dialog), `saint`, Champion/Warlord prefix conversion, the Court,
  appearance-packet extension (all three decoders), client render, voidbot decode.

**Slice 4 — drama polish (post-launch).**
- Honorific wildy-death announcements (PvP death attribution point verified available); "Change
  form" detail option hardening.
- Backlog (needs explicit owner sign-off to start): founder prefix, seat-capped knighthood +
  honor duels, King/Queen apex office, `self_made`.

Timing guidance: slices 2+3 ship together as one client bump when launch has stabilized — do not
rush a protocol change into the launch window. Slice 1 carries zero protocol risk by design.

---

## Part 9 — Historical verification matrix (voidbot + workbench)

Slice 1:
1. `scripts/build.sh` passes; `tests/smoke.sh` passes.
2. Fresh account (see memory: fresh-account E2E): grant stats via `voidbot admin`, confirm the
   unlock prompt arrives as an options menu **after** combat ends (queue rule), `menu-reply`
   "Wear it" equips, "Not now" leaves the overhead empty. Confirm NO auto-equip anywhere.
   Dismiss a prompt with `-1` → it re-queues and re-offers when next eligible.
3. Magnate: AH sale below 250k in a fresh month does **not** grant; above the floor does. (Equal
   volume already cannot steal — the `:777` tie-guard predates V2; keep a regression check on it.)
   Month rollover: exactly one crowning announcement for the closed month, office lapses.
4. Bronze reaper: PK a lower-level player in bronze → no title; equal/higher-level victim → title.
5. Migration: seed a cache with `pt_u_first_agil30` + an active non-founder title → login → active
   cleared, culled keys gone (`pt_u_` and per-player date), founder active survives on a founder
   account.
6. Announcements: contested mid-month lead change produces **no** world announcement; first claim
   of a fresh period produces one; Champion season award still announces exactly once.

Slices 2-3 add:
7. `immortal`/`scourge`/etc. unlock via admin-granted thresholds; supreme announce in crimson.
   Offline paths: resolve a season with the champion logged out → counter + unlock + prompt arrive
   at next login; AH sale committing while the seller is offline increments `title_ah_volume`.
8. Duelist counts wins on BOTH completion paths (plain journal and proof-settlement duels).
9. Knighting: beat Sir Charles (or admin-force the win branch on a test world), pick Dame → world
   announce uses Dame (and the old Sir Charles broadcast does NOT also fire), overhead reads
    `Dame <name>`, Court lists her with dub date.
10. Honorific + epithet stack: overhead `Sir X the Immortal`; 40-char cap drops the epithet
    (workbench screenshot); label still hides while speaking. **Pre-bump client check:** a 10136
    client against the bumped server parses appearance fine and shows the epithet only (honorific
    invisible — accepted); title board still renders.
11. One-slot rule: equipping a PREFIX title as honorific clears it from the epithet slot and vice
    versa; converting Champion/Warlord migrates suffix-wearers to the honorific slot on first touch.
12. voidbot `state players` exposes honorific fields; smoke extended for the prompt menu.

---

## Part 10 — Bookkeeping (required by repo rules)

- `docs/DIVERGENCE.md` entry per slice (what changed, reversibility).
- Update `docs/subsystems/player-titles.md` (tier table, honorifics section, Court, prompt flow,
  migration note) and `docs/subsystems/networking-protocol.md` (bump entry) in the bump slice.
- `docs/CONFIG-MATRIX.md` if it pins `client_version`.
- Update `docs/bot-api.md` for the voidbot decode extension.
- Commit per slice, imperative, why-focused.

## Out of scope — do not do

- No perks, buffs, or mechanical rewards for any title or honorific.
- No chat-text prefixes anywhere (chatbox/ChatLog/snapshots stay clean).
- No new opcodes; no opcode reordering. Payload extensions are version-gated appends at the true
  end of the record only.
- No seat caps / honor duels / King-Queen offices / ironman titles in this pass (backlog,
  owner-gated).
- No backfilled or fabricated first-claims; `unmasker` starts unclaimed like everything else. No
  counter backfills from historical tables (Part 7).
- Don't touch `upstream/openrsc-snapshot/`.
