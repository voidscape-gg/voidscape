# Player Titles

Voidscape player titles are cosmetic suffix-style labels such as `the Master-at-Arms`.
A player unlocks titles by meeting server-side requirements, picks an active title with
`::titles`, and the custom client renders the active title beside the normal player
name, such as `void the Master-at-Arms`.

## Server model

- `server/src/com/openrsc/server/content/PlayerTitle.java` is the title registry. It owns the 62-entry catalog, tier/lifecycle metadata, achievement-table metadata, cache keys, lookup helpers, and automatic unlock checks.
- Unlock state is stored in the existing player cache using `pt_u_<id>`. Active title state is stored in `player_title_active`.
- Titles have a tier: `RENOWN` (white), `FEAT` (purple), or `UNIQUE` (gold). Rarity scoring was removed.
- Unique titles have a lifecycle: `FIRST` (permanent first claim), `CONTESTED` (current leader), or `ITEM_BOUND` (follows a future relic holder). Accounts may hold any number of uniques, but only one title can be active.
- First-to record titles are `FIRST` uniques whose catalog rows read like achievements, such as `First 50,000 Swordfish Cooked` and `First Total Level 1700`. They keep short player-cache counter keys and stamp the first claimant/date through the existing unique-title global cache.
- Contested titles use a global token in `title_contested_token_<id>`; the current holder's `pt_u_<id>` value must match that token. Warlord and Magnate also store current-month leader score metadata under `title_contested_month_<id>` and `title_contested_score_<id>`.
- Login/use migration clears old `pt_u_*`, `player_title_unlocked_*`, `pt_b_*`, and `pt_unique_claim` title state for the prelaunch overhaul, preserving only `founder`.

## Commands

- `::titles` refreshes automatic unlocks, then opens the title hub.
- `::titles list|all [page]` opens the full catalog.
- `::titles unlocked|renown|feat|unique [page]` opens a filtered catalog page. Rows show achievement/title, tier, holder/player, and claim age; selecting a row opens requirement/progress/lifecycle/holder details.
- `::title <id or name>` equips an unlocked title.
- `::title clear` removes the active title.
- `::titles count` shows the player's unlocked count out of the catalog.
- Staff can use `::granttitle <player> <title_id>` and `::revoketitle <player> <title_id>` for manual/event titles.

The custom PC client recognizes structured `~vstitle~` row metadata embedded in normal
menu option strings and renders the catalog as an achievement-style table with
All/Unlocked/Renown/Feat/Unique tabs. Generic clients still see readable fallback option
text. The modals still send ordinary menu replies, so the server remains authoritative
and no title-catalog opcode is required.

## Unlock hooks

Automatic titles refresh in these existing progression paths:

- `Player.incQuestPoints(...)`
- `Player.addNpcKill(...)`
- wilderness player kills inside `Player.killedBy(...)`
- level-ups inside `Skills.addExperience(...)`
- opening `::titles`, which also retroactively grants titles for current state

Direct event hooks award the manual/feat titles at authoritative completion points:

- fishing/cooking/bone burying/mining/high-alchemy/gnomeball counters feed Lobster Baron, Unburnt, Bone Collector, Coal-Hearted, Goldspinner, Gnome-Baller, and first swordfish/coal record titles.
- woodcutting, herb pickup, gem cutting, and spell-cast hooks feed the first magic-log, herb, gem, and spell record titles.
- NPC death/drop hooks feed Giant-Killer and Dragon-Crowned.
- Smithing a rune plate mail body awards Runesmith.
- Wilderness PKs feed Edgelord, Bronze Reaper, Widowmaker, and monthly Warlord of the Wastes.
- Void content awards Voidbane, Gravewalker, Void Arena Champion, and Colossusbane from their owning systems.
- Committed auction-house sales feed monthly Magnate through the sale-history table.

`RIFTBOUND` remains manual until there is a real Void Rift win-state event; the current
`VoidRift` plugin is a travel network, not a contest.

## Client display

The custom appearance update sends the active title string for clients `>= 10052` and,
for clients `>= 10123`, a one-byte title tier immediately after it. Authentic and retro
clients are not sent the custom title fields.

The client stores the fields on `ORSCharacter.title` and `ORSCharacter.titleTier`, draws
the username in the normal name color, and draws the title suffix in the tier color. The
label is hidden while a player overhead chat message is active so chat text stays
readable. Public chat no longer receives title prefixes.
