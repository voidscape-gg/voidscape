# Title System Overhaul — Implementation Brief

Audience: an implementing agent (Codex) working in this repo. Read `CLAUDE.md`, `server/CLAUDE.md`,
`server/plugins/CLAUDE.md`, and `Client_Base/CLAUDE.md` before starting — their hard rules apply to
everything below. Build with `scripts/build.sh`, never raw gradle/ant improvisation.

**Goal:** replace the current 103-title spray with a small, prestigious, three-tier catalog; fix the
broken unique-claim mechanics; and fix the overhead-chat rendering collision that makes titled
players' chat look mangled.

**Status quo:** pre-launch. No real players. Wiping existing title unlock data is acceptable and
expected (one stable exception: `founder`, see Migration).

**Heads-up:** the working tree may contain unrelated uncommitted changes (hit-splat art, world map,
`PacketHandler.java`, `mudclient.java`). Build on top of them; do not revert anything you didn't touch.

---

## Part 0 — Current state (verified findings, don't re-discover)

| Where | What |
|---|---|
| `server/src/com/openrsc/server/content/PlayerTitle.java` | The entire catalog (103 enum entries: 64 reusable + 39 "unique"), unlock logic, rarity formula, active-title cache. |
| `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java:154-660` | `::titles` command: hub menu, paged catalog, detail view. |
| `server/src/com/openrsc/server/net/rsc/handlers/ChatHandler.java:72` | Prepends `@red@[Title]@whi@ ` **into the chat message text itself**. |
| `server/src/com/openrsc/server/GameStateUpdater.java:1079-1080` | Sends the active title string in the appearance packet, gated on `PlayerTitle.OVERHEAD_TITLE_CLIENT_VERSION` (10052). |
| `Client_Base/src/orsc/PacketHandler.java:2852,2893-2899` | Client reads the title from the appearance packet into `ORSCharacter.title`. |
| `Client_Base/src/orsc/mudclient.java:13974-14004` | Draws the overhead chat bubble queue entry and the floating `Name Title` label. |
| `Client_Base/src/orsc/mudclient.java:4445-4472` | `drawCharacterOverlay()` — chat bubble collision pass (bubbles only collide with other bubbles). |
| `Client_Base/src/orsc/mudclient.java:6187-6330` | Custom glass-UI panels for the title menus — **pattern-matches server menu option strings** (e.g. `"Common titles"`). |

### Bug 1 — chat "convergence" (the visual mess when a titled player talks)

The title renders twice in the same ~8 pixels:

1. `ChatHandler.java:72` injects `[Title]` into the message, so the overhead bubble says
   `[Grave-Reaper] Hello` (it also pollutes the chatbox line, `ChatLog`, and world snapshots).
2. The client independently draws an always-on floating `Name Title` label — even when the player
   has floating nametags disabled (`mudclient.java:13990-13991`: `labelOverlayEnabled || hasPlayerTitle`).
3. Two compounding client bugs: with a title the label drops to `y - 8` instead of the normal
   `y - 14` (`mudclient.java:13993`) — *closer* to the chat text; and the chat bubble draws its first
   line at `y`, wrapping downward, while the collision pass never considers the name label.

Result: red `[Title]` text in the bubble overlaps red title text in the label, 8px apart with a
~12px font.

### Bug 2 — unique titles are a footgun

- `refreshAutomaticUnlocks` fires on every level-up and login and **silently auto-claims** the first
  open unique the player qualifies for, in enum declaration order. The player never chooses.
- One unique per account, forever: no swap, no release, and a permanent `pt_b_` blocked flag.
- Ladder math makes flagship titles unclaimable: everyone crosses total 1750 ("Almost Maxed",
  unique) before 1782 ("Max-Caped", unique), so the level-up at 1750 burns the account's only
  unique slot and "Max-Caped" is then permanently blocked for that player. Same for the combat,
  PK, and quest ladders.

### Bug 3 — dead rarity math

`rarityScore()`/`rarityLabel()`: "rare" requires score ≥ 10000, but the maximum possible non-unique
score is 5000. The rare tier is unreachable — dead code. 50 of 64 reusable titles land in "common".

---

## Part 1 — Rendering fix (do this first; it stands alone)

### Server

1. **Delete the chat prefix.** Remove line 72 of `ChatHandler.java` (and `PlayerTitle.activePrefix()`
   once unreferenced). The title's only in-world channel is the appearance-packet label. Chat
   messages, chatbox lines, and chat logs go back to clean `name: message`.
2. **Send the tier color.** Extend the appearance payload in `GameStateUpdater.java` (~line 1080):
   after the title string, write one byte for the title tier (0 = renown, 1 = feat, 2 = unique;
   send 0 when no title). Gate it exactly like the existing pattern:
   - Add `PlayerTitle.OVERHEAD_TITLE_TIER_CLIENT_VERSION = 10123` (server) and
     `Config.PLAYER_TITLE_TIER_CLIENT_VERSION = 10123` (client).
   - Bump `Client_Base/src/orsc/Config.java` `CLIENT_VERSION` from 10122 → **10123** and
     `client_version` in `server/local.conf` to match (see `Client_Base/CLAUDE.md` rule 2; update
     `docs/CONFIG-MATRIX.md` if it pins the version).
   - No new opcode; this is a version-gated payload extension following the existing
     `PLAYER_TITLE_CLIENT_VERSION` pattern at `PacketHandler.java:2852/2893`. Do **not** touch
     opcode ordinals.

### Client (`mudclient.java`, drawPlayer ~13988-14004)

3. **Restore the label height.** Titled players' name line goes back to `y - 14` (same as untitled).
4. **Hide the label while speaking.** When `player.messageTimeout > 0`, skip the name+title label
   entirely — the chat bubble takes its place (this is also the authentic RSC feel, where speech
   replaces the nametag). This alone eliminates the overlap.
5. **Color the title by tier.** Read the tier byte in `PacketHandler.java` next to the existing
   title read (version-gated). In the label, keep the name yellow (`0xffff00`) and draw the title in
   the tier color:
   - Renown: silver `0xE8E8E8`
   - Feat: void purple `0xB980FF`  (matches the glass UI palette — check `UiSkin` for an existing constant first)
   - Unique: gold `0xFFD24A`
6. **Join rule.** Render `Name the Unburnt` when the title starts with `"the "`, otherwise
   `Name, Master of All` (comma + space). Server keeps sending the raw display name; the client
   applies the join.

### Verify (use the sanctioned channels — see `CLAUDE.md` Game interaction)

- `scripts/build.sh` passes; client compiles (`cd Client_Base && ant compile`).
- With voidbot: equip a title, `voidbot say "hello there"`, then use the AI workbench
  (`scripts/run-workbench-client.sh`, HTTP 127.0.0.1:18787) to screenshot: label hidden while the
  bubble is up, no overlapping text, chatbox line has no `[Title]`.
- Confirm a pre-10123 client still parses appearance packets (title string only, no tier byte).

---

## Part 2 — Mechanics redesign

All in `PlayerTitle.java` unless noted. Delete what's listed as deleted — don't leave dead paths.

### Data model

- Replace `TitleScope` with `Tier { RENOWN, FEAT, UNIQUE }`.
- Uniques get a `UniqueKind { FIRST, CONTESTED, ITEM_BOUND }`.
- **Delete** `rarityScore()`, `rarityLabel()`, the one-unique-per-account cap, and the entire
  `pt_b_` blocked-flag mechanism. An account may hold any number of uniques (only one title is
  active at a time anyway).
- Keep: `ACTIVE_TITLE_CACHE`, per-title unlock cache keys (`pt_u_<id>`), `byId()` display-name
  matching, `setActive`/`active` self-healing.

### Requirement types

Keep `SKILL_LEVEL`, `TOTAL_LEVEL`, `COMBAT_LEVEL`, `PLAYER_KILLS`, `NPC_KILLS_TOTAL`,
`NPC_KILLS_ANY`, `MANUAL`. Add:

- `QUEST_COMPLETE(questId)` — check `player.getQuestStage(questId) == -1` (completed convention).
- `MAX_QUEST_POINTS` — compute the max at runtime from the quest registry; never hardcode the number.
- `NPC_KILLS_SET(threshold, Set<npcId>)` — sum of `player.getKillCache()` over the id set. Resolve
  every NPC id from `server/conf/server/defs/` JSON or existing constants — **do not guess ids**.
- `COUNTER(cacheKey, threshold)` — a namespaced int cache counter (`title_<name>`), incremented by
  small hooks in the relevant plugins (Part 4).
- `ZERO_DEATH_COMBAT(level)` — combat ≥ level and deaths == 0 (verify the deaths stat getter exists;
  OpenRSC tracks deaths).

`refreshAutomaticUnlocks` keeps its existing call sites (level-up, login, `::titles`) and must also
run when quest points change (hook the QP increment path). Per-action counter hooks must be cheap:
increment the cache int, then check **only the titles bound to that counter** — no full refresh, no
DB queries unless a threshold was newly crossed this action.

### Unique lifecycle

**FIRST** (permanent, historical):
- Auto-granted at the *moment* the achievement happens (that is fair and race-correct — the problem
  before was the account cap and silent slot-burning, both now gone). Keep the synchronized unlock +
  `queryPlayerCacheOwner` server-wide ownership check.
- Stamp the claim date (`pt_first_date_<id>` = epoch seconds) for the detail view.
- World announcement via `WorldAnnouncementService`:
  `"<player> has claimed a unique title: the Paragon!"` (gold-toned message; match the service's
  existing formatting conventions).
- Never recycled. If it's claimed, it's history.

**CONTESTED** (held while leading; can change hands):
- Provide `PlayerTitle.grantContested(world, player, title)` — revokes from the current holder
  (with a polite message if online), grants to the new holder, announces the transfer.
- Wire `VOID_ARENA_CHAMPION` to it (existing seasonal re-award at
  `server/src/com/openrsc/server/content/voidarena/VoidArena.java:1849`).
- `WARLORD_WASTES`: keep a monthly PK counter (`title_pk_<yyyyMM>`) incremented on each wilderness
  player kill. On increment, if the killer isn't the holder, has ≥ 10 kills this month, and exceeds
  the holder's current-month count (read holder via `queryPlayerCacheOwner`, then their counter),
  transfer. PK kills are rare enough that one DB read per PK is acceptable.
- `MAGNATE` (auction-house volume leader): same pattern hooked into the AH transaction path. If the
  AH code has no clean hook, implement the title + grant API and leave the AH wiring as a clearly
  marked follow-up in `docs/BUGS.md` Intake — do not force it.

**ITEM_BOUND**:
- `RELIC_KEEPER` follows possession of the one-of-one relic. If no relic item exists yet, keep it
  MANUAL (admin-granted/revoked) and note that in its hint text.

### Admin tooling

Add `::granttitle <player> <title_id>` and `::revoketitle <player> <title_id>` to the appropriate
admin command plugin (grep the existing admin command handlers for the registration pattern).
Manual titles (`founder`, item-bound fallback) need this path, and it makes QA trivial.

### Migration (pre-launch, destructive is fine)

On login, lazily clean the player cache: remove `pt_u_*`, `player_title_unlocked_*`, `pt_b_*`, and
`pt_unique_claim` keys that don't correspond to a current title id; clear the active-title key if
its id no longer exists (already self-heals). Keep the id `founder` stable so existing founder
grants survive (see `docs/PRELAUNCH-FOUNDER-PASS.md`). Kill-based feats backfill automatically
because `killCache` persists; new counters start at zero — acceptable.

Old titles that were granted from event code and are being **cut** — remove their call sites:
- `VoidArena.java:1142` — `DM_KINGSLAYER` (cut).
- `DeathMatchArena.java:467` — `VOID_TOUCHED` (cut). Keep `VOIDBANE` at `:921`.
- Keep `GRAVEWALKER` at `undeadsiege/UndeadSiegeInstance.java:894`.

---

## Part 3 — The new catalog (51 titles, exact data)

Naming convention: titles starting with `the` render as `Name the X`; everything else renders as
`Name, X`. Hints shown in the catalog should be the "Requirement" text below, trimmed to fit.

### Tier 1 — RENOWN (silver) — reusable, endgame milestones. 25 titles.

Skill mastery, level 99 (`SKILL_LEVEL 99`):

| id | Display name | Skill |
|---|---|---|
| `master_at_arms` | the Master-at-Arms | Attack |
| `bulwark` | the Bulwark | Defense |
| `titan` | the Titan | Strength |
| `deathless` | the Deathless | Hits |
| `hawkeyed` | the Hawk-Eyed | Ranged |
| `hallowed` | the Hallowed | Prayer |
| `archmage` | the Archmage | Magic |
| `master_chef` | the Master Chef | Cooking |
| `treefeller` | the Treefeller | Woodcutting |
| `arrowsmith` | the Arrowsmith | Fletching |
| `old_salt` | the Old Salt | Fishing |
| `flamekeeper` | Keeper of the Flame | Firemaking |
| `artificer` | the Artificer | Crafting |
| `forgemaster` | the Forgemaster | Smithing |
| `deepdelver` | the Deepdelver | Mining |
| `apothecary` | the Apothecary | Herblaw |
| `fleet_footed` | the Fleet-Footed | Agility |
| `light_fingered` | the Light-Fingered | Thieving |

Account milestones:

| id | Display name | Requirement |
|---|---|---|
| `jack_of_trades` | Jack of All Trades | Total level 1,500 |
| `master_of_all` | Master of All | Total level 1,782 (max) |
| `apex` | the Apex | Combat level 123 |
| `storied` | the Storied | Maximum quest points (runtime-derived) |
| `ten_thousand_blades` | Ten-Thousand Blades | 10,000 NPC kills |
| `nemesis` | the Nemesis | 5,000 kills of a single NPC type |
| `widowmaker` | the Widowmaker | 100 wilderness player kills |

### Tier 2 — FEAT (void purple) — reusable, deed-based. 17 titles.

Quests and bosses (vanilla RSC):

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `dragonslayer` | the Dragonslayer | Complete Dragon Slayer (slay Elvarg) | `QUEST_COMPLETE` |
| `hero` | the Hero | Complete Hero's Quest | `QUEST_COMPLETE` |
| `legend` | the Legend | Complete Legends Quest | `QUEST_COMPLETE` |
| `black_kings_bane` | the Black King's Bane | Kill the King Black Dragon 100 times | `NPC_KILLS_SET` (KBD id) |
| `demonsbane` | the Demonsbane | Slay 666 demons | `NPC_KILLS_SET` (all NpcDefs whose name contains "demon": lesser/greater/black demons, Delrith, Chronozon, …) |
| `dragon_crowned` | Dragon-Crowned | Receive a dragon medium helmet as your own drop | hook the drop-award path (`NpcDrops` / rare-drop table); trades don't count |

Skilling feats (vanilla RSC):

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `runesmith` | the Runesmith | Smith a rune plate mail body | hook smithing success for that item id (the famous 99-smithing flex) |
| `lobster_baron` | the Lobster Baron | Catch 5,000 lobsters | `COUNTER` in the fishing plugin |
| `unburnt` | the Unburnt | Cook 1,000 food in a row without burning | streak `COUNTER` in the cooking plugin — increment on success, reset to 0 on burn |
| `bone_collector` | the Bone Collector | Bury 10,000 bones | `COUNTER` in the bone-bury handler |
| `coal_hearted` | the Coal-Hearted | Mine 10,000 coal | `COUNTER` in the mining plugin |
| `goldspinner` | the Goldspinner | Cast High Level Alchemy 10,000 times | `COUNTER` in the alch spell path |
| `gnome_baller` | the Gnome-Baller | Score 100 gnomeball goals | `COUNTER` in `authentic/minigames/gnomeball/` (a goal event already exists there) |

Combat feats (vanilla RSC):

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `giant_killer` | the Giant-Killer | Kill an NPC of combat level ≥ 2× yours (NPC must be level ≥ 50) | check on NPC death attribution |
| `untouched` | the Untouched | Reach combat level 90 without ever dying | `ZERO_DEATH_COMBAT(90)` |
| `edgelord` | the Edgelord | 50 player kills in wilderness levels 1–5 | `COUNTER` on wildy PK, gated by wilderness depth |
| `bronze_reaper` | the Bronze Reaper | Kill a player while wielding a bronze weapon | check killer's equipped weapon on PK |

Voidscape custom (kept deliberately few):

| id | Display name | Requirement | Tracking |
|---|---|---|---|
| `voidbane` | Voidbane | Defeat the Void Knight in the Death Match Arena | existing wiring (`DeathMatchArena.java:921`) |
| `gravewalker` | the Gravewalker | Clear wave 10 of Undead Siege | existing wiring (`UndeadSiegeInstance.java:894`) |
| `founder` | the Founder | Reserved for early Voidscape supporters | MANUAL — **keep this id stable** |

### Tier 3 — UNIQUE (gold) — one-of-one server-wide, announced. 9 titles.

FIRST (permanent, date-stamped):

| id | Display name | Requirement |
|---|---|---|
| `trailblazer` | the Trailblazer | First player on the server to reach level 99 in any skill |
| `paragon` | the Paragon | First player to reach max total level (1,782) |
| `lorekeeper` | the Lorekeeper | First player to complete every quest |
| `colossusbane` | Colossusbane | First kill of the Void Colossus |
| `riftbound` | Riftbound | First to win the Void Rift event (wire into `server/plugins/.../custom/misc/VoidRift.java`) |

CONTESTED (held while leading):

| id | Display name | Requirement |
|---|---|---|
| `warlord_wastes` | Warlord of the Wastes | Most wilderness kills this month (min 10; lazy re-eval per PK) |
| `void_arena_champion` | the Void Arena Champion | #1 in the current Void Arena ranked season (existing re-award) |
| `magnate` | the Magnate | Top auction-house trade volume this season (AH hook; follow-up if no clean hook) |

ITEM_BOUND:

| id | Display name | Requirement |
|---|---|---|
| `relic_keeper` | the Relic-Keeper | Hold the one-of-one Voidscape relic (MANUAL fallback if no relic item exists yet) |

Everything not listed above is **cut** — including all 8 total-level trickle titles below 1500, all
combat titles below 123, all quest-point tiers, all sub-100 PK tiers, all NPC-kill tiers below
10,000/5,000, all 18 level-50 skill titles, all 18 level-90 unique skill titles, and the old manual
grab-bag (`dm_kingslayer`, `voidwalker`, `void_touched`, `auctioneer`, `merchant`, `rarestruck`,
`beamcaller`, `old_guard`, `vanquisher`, `pathfinder`, `market_maker` as ids — `magnate` supersedes).

---

## Part 4 — Tracking hooks (where the counters live)

Content hooks go in `server/plugins/` next to the actions they observe (per `server/CLAUDE.md`
rule 6); the `PlayerTitle` model stays in `server/src/.../content/`. Namespace all cache keys with
`title_`. Locate the exact plugin files by grepping for the action (e.g. lobster fishing success is
in `authentic/skills/fishing/`); do not guess file paths or ids.

| Counter | Hook point |
|---|---|
| lobsters caught | fishing success where catch is a lobster |
| cook streak | cooking success (+1) / burn (reset 0) |
| bones buried | bone bury inventory action |
| coal mined | mining success where ore is coal |
| high alchs | High Level Alchemy cast success |
| gnomeball goals | gnomeball scoring event |
| monthly wildy PKs | player-kill attribution, wilderness only |
| Edgeville PKs (lvl 1–5) | same hook, depth-gated |
| bronze-weapon PK / giant-killer / dragon-med-helm / runesmith | event-checks (no counter), fired at the kill/drop/smith moment |

KBD, demons, `nemesis`, and `ten_thousand_blades` need **no new tracking** — they read the existing
persistent `killCache` / `getNpcKills()`.

---

## Part 5 — `::titles` UX updates

Server side (`RegularPlayer.java`):
- Hub menu options become: `Active: …` / `Unlocked titles (n/51)` / `Renown titles` / `Feat titles`
  / `Unique titles` / `All titles` / `Clear active title` / `Close`. Drop Common/Rarest views.
- Catalog rows: `<name> - <tier> - <state>` where tier is color-coded (`@whi@`/`@mag@`/`@yel@`) and
  state is `active` / `unlocked` / `locked` / for uniques `held by X` or `open`.
- Detail view keeps live progress lines; for FIRST uniques show
  `Claimed by X on <date>` or `Unclaimed - first to <deed> takes it forever.`; for CONTESTED show
  the current holder and their leading score.
- Unlock toast stays; color it by tier. Unique claims additionally world-announce (Part 2).

Client side — **required sync**: the glass-UI panels in `mudclient.java:6187-6330` detect the title
menus by matching the server's option strings (`"Common titles"`, `"Rarest titles"`,
`"View all titles"`, headers like `"All titles - page "`). Update every matcher and the tab row to
the new category names in the same change, or the custom UI silently degrades to the plain menu.
Test each screen (hub, catalog, detail) through the workbench after the change.

---

## Part 6 — Verification checklist

1. `scripts/build.sh` passes (server core + plugins); `cd Client_Base && ant compile` passes.
2. `tests/smoke.sh` still passes.
3. Fresh account via voidbot (see memory: fresh-account E2E flow): stat-grant with `voidbot admin`,
   confirm renown titles unlock on level-up with correct toasts, `::titles` menus navigate on the
   new categories via `voidbot menu-reply`.
4. `::granttitle`/`::revoketitle` round-trip on a feat title.
5. Equip a title, `voidbot say`, workbench screenshot: label hidden while bubble shows; label at
   `y-14`, tier-colored, correct join rule; chatbox line clean.
6. Unique flow: grant 99-in-a-skill to account A → world announcement fires, `trailblazer` shows
   `Claimed by A on <date>`; account B reaching 99 gets the renown title but not the unique, with
   no blocked flags written anywhere.
7. Contested flow: simulate two PKers across the monthly counter; title transfers with both players
   messaged.
8. Migration: a player cache seeded with old keys (`pt_u_first_step`, `pt_b_maxed`,
   `pt_unique_claim`) logs in clean; `founder` unlock survives.

## Part 7 — Bookkeeping (required by repo rules)

- Add a `docs/DIVERGENCE.md` entry (one paragraph: catalog replaced, tier system, unique lifecycle,
  chat prefix removed, appearance packet extended at 10123).
- Update `docs/CONFIG-MATRIX.md` if it records `client_version`.
- If `docs/GLOSSARY.md` or any subsystem doc references the old title tiers, update in passing.
- Commit style: imperative, why-focused. Keep the rendering fix and the catalog redesign as
  separate commits if practical.

## Out of scope — do not do

- No achievement-system integration, no title-related rewards/perks, no new UI panels beyond
  restyling the existing three title screens.
- No opcode insertions or reordering; the protocol change is exactly one version-gated byte.
- No touching `upstream/openrsc-snapshot/`.
- Don't backfill or fabricate historical "first" claims — all FIRST uniques start unclaimed.
