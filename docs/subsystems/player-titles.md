# Player Titles

Voidscape titles are cosmetic status labels earned through the main game loop. A player may
wear one prefix honorific and one suffix epithet at the same time, for example
`Saint Vex the Immortal`. Titles never grant perks and never prefix chat messages.

## Catalog and model

`server/src/com/openrsc/server/content/PlayerTitle.java` owns the 65-entry catalog:

| Tier | Count | Display |
|---|---:|---|
| Renown | 25 | White; long-form skill, quest, combat, and kill progression |
| Feat | 20 | Purple; specific accomplishments and substantial counters |
| Supreme | 7 | Crimson; deliberately rare, server-defining achievements |
| Unique | 13 | Gold; one permanent first claimant, current office holder, or relic holder |

Every row has `Position.SUFFIX` or `Position.PREFIX`. The three honorifics are `saint`
(`Saint`, Supreme), `knight` (`Knight`, Supreme), and `warlord_wastes` (`Warlord`, Unique
contested); all other catalog rows are suffix epithets. Unlocks use `pt_u_<id>`. The
independent worn slots use
`player_title_active` and `player_honorific_active`, so equipping one slot does not clear
the other. Revocation and contested transfer clear either slot if it contains the lost row.

Unique lifecycles remain `FIRST`, `CONTESTED`, and `ITEM_BOUND`. First claims store their
claimant/date globally. Warlord and Magnate use compact global token, month, score, and
period-crown cache keys. Their ownership expires at month rollover; ordinary mid-period
lead changes are quiet, while the first qualifying claim and the lazily observed closed
period may announce. Saint and Knight store per-player recognition dates, which The Court reads.

The full-V2 migration is `pt_migration_titles_full_v2`. It conservatively seeds the unsafe-
death counter from saved deaths, repairs prefix rows found in the legacy suffix slot, and
self-heals invalid honorific state. Earlier migrations still revoke culled/rescoped and
minigame-backed ids while preserving retained progress and the established Founder exception.
No schema was added; title state remains in `player_cache`.

## Main-loop additions

The V2 additions retained by the approved scope are:

- Feat: Fishmonger (25,000 swordfish cooked), Headhunter (250 Wilderness bounty claims),
  Uncaught (100 marked-bounty escapes), and Keywarden (500 Void Chests).
- Supreme suffixes: Immortal (maximum total without an unsafe death), Voidtouched (all four
  Void chase items received as the player's own NPC drops), Scourge (1,000 Wilderness PKs),
  Midas (25,000,000 gp lifetime Auction House sales), and Huntmaster (one kill for every NPC
  in the curated Preservation-14 main-world hunting roster).
- Supreme honorifics: Saint, earned by level 99 Prayer plus the runtime maximum quest points;
  and Knight, earned by level 99 Attack, Defence, Strength, Hits, Ranged, Prayer, and Magic,
  combat level 123, and the runtime maximum quest points.
- Existing `warlord_wastes` is now the prefix office `Warlord`; its stable id and monthly
  contested lifecycle are unchanged.

Committed Auction House sales update Midas progress for online and offline sellers. NPC death,
NPC-drop, Void Chest, bounty, cooking, skill, quest, total-level, and Wilderness-PK completion
paths feed the relevant checks at their authoritative server points. Opening `::titles` also
refreshes automatic requirements from durable current state.

## Ceremony and wearing

Unlocking never auto-equips. It stores a per-title `pt_q_<id>` marker and waits until combat,
duels, trades, bank/shop interfaces, other menus, and input dialogs are clear. Official custom
clients in cohort `10137` and newer render the ordinary two-option menu as a centered Void Glass ceremony.
In the current `10139` client, `Wear for 100,000 gp` charges inventory coins and equips the
correct slot; `Not now` leaves the title unlocked for later through the Void Herald. Generic or
older compatible clients receive the same choices as readable standard menu text. Dismissal,
interruption, timeout, and logout preserve/requeue pending awards rather than silently wearing or
losing them.

The ceremony uses `~vstitleaward~` metadata over the existing menu/reply transport; it adds no
title-specific opcode. The client workbench exposes ceremony visibility, metadata, geometry, and
button indices for deterministic UI verification.

## Commands and The Court

- `::titles` opens the hub and refreshes automatic unlocks.
- `::titles all|unlocked|renown|feat|supreme|unique|court [page]` opens a filtered board.
- `::title` remains a browse alias. Command arguments never wear or clear a row; mutation
  attempts direct the player to the Void Herald.
- The Void Herald is the registrar. Wearing or changing one honorific or epithet costs
  100,000 inventory coins after confirmation. Selecting the already-active row is a free no-op.
  Clearing the honorific, epithet, or both is free after a warning that re-wearing costs 100,000.
- `::titles count` reports progress out of 65.
- Staff retain `::granttitle <player> <title_id>` and `::revoketitle <player> <title_id>`.

The Court is the honorific roster: the reigning Warlord with monthly kills, plus every Saint and
Knight with their recognition date. It uses the same paged menu transport and readable fallback
rows as the main catalog. The custom board includes All/Unlocked/Renown/Feat/Supreme/Unique/Court
views; pages are sized so rows, navigation, and Close remain below the client's 20-option limit.
Title-detail cards render honorifics by their prefix form, wrap long requirements and progress at
compact viewports, and retain the server's original Back/wear/close option indices. Native Android
uses 48dp action targets around the same compact art; authentic mode retains the pre-overhaul card
geometry.

## Client display and protocol

Appearance updates retain the suffix string for clients `>= 10052`, its tier byte for clients
`>= 10123`, and the modern-hair byte for clients `>= 10057`. Cohort `10137` appends the honorific
string and honorific-tier byte after hair. The server, live and null-player client decoders, and
voidbot all use that exact order.

The client renders prefix color + yellow name + suffix color on one overhead line, three pixels
lower than the original title baseline, and hides the line while the player is speaking. Setting
index 35 is a `10138` three-state remote-player preference: Names + titles (default), Names only,
or Hide names + titles. It does not change the local player's visibility behavior. If the joined
label exceeds 40 characters, the client drops the suffix for that render rather than truncating a
word or hiding the honorific. Pre-10137 clients degrade to the suffix-only field. Catalog and Court
rows continue to use `~vstitle~` metadata over ordinary menus, so generic clients remain usable.

## Explicit exclusions

Titles do not come from minigames, arenas, or side events. Gnomeball, Death Match Arena, Undead
Siege, Void Colossus, Void Rift events, and Void Arena seasons keep their ordinary gameplay and
rewards but award no titles. Former ids `gnome_baller`, `voidbane`, `gravewalker`,
`colossusbane`, `riftbound`, and `void_arena_champion` are migration-only names.

The broader brief's arena-backed Grandmaster, Knight of the Void, Void Arena Champion, Duelist,
and Unmasker rows are not in the catalog. The retained `knight` honorific is the main-loop combat,
quest, and combat-level composite described above, not the rejected arena row. Founder-to-prefix
conversion, seat-capped offices/honor duels, King/Queen, and `self_made` remain explicitly deferred.
