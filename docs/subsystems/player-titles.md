# Player Titles

Voidscape player titles are cosmetic suffix-style labels such as `Crownless Conqueror`. A player unlocks titles by meeting server-side requirements, picks an active title with `::titles`, and the custom client renders the active title beside the normal player name, such as `void Crownless Conqueror`.

## Server model

- `server/src/com/openrsc/server/content/PlayerTitle.java` is the title registry. It contains the title catalog, requirement metadata, cache keys, lookup helpers, and automatic unlock checks.
- Unlock state is stored in the existing player cache using keys prefixed with `pt_u_`. The shorter prefix keeps every title key under the current `player_cache.key` schema limit; legacy `player_title_unlocked_` keys are still read for older local data.
- Active title state is stored in `player_title_active`.
- Titles have a scope: `reusable` titles can be unlocked by every qualifying player, while `unique` titles are first-claim exclusives. Each account can hold only one unique title total.
- Unique title claims are stored in `pt_unique_claim`; owner display resolves that claim from online player caches first, then from the existing `player_cache` table joined to `players`. No new schema is required.
- Automatic unlocks currently evaluate stable, already-persisted player state: total level, combat level, quest points, wilderness player kills, total NPC kills, single-NPC kill streaks, and individual real RSC skill levels.
- Manual titles are catalog entries for systems that need bespoke hooks, such as event rewards, auction-house milestones, rare-drop milestones, or Voidscape encounters.

## Commands

- `::titles` refreshes automatic unlocks, then opens a dialogue-style title hub.
- `::titles list [page]` opens the all-title catalog.
- `::titles unlocked|unique|common|rarest [page]` opens a filtered 10-title catalog page. Rows stay compact; selecting a title opens a detail pop-up with its requirement, current progress, scope, rarity, and lock/owner state. Unlocked titles can be equipped from that pop-up.
- `::title <id or name>` equips an unlocked title.
- `::title clear` removes the active title.
- `::titles count` shows the player's unlocked count out of the full title catalog.

The custom PC client recognizes these title menu payloads and renders them as centered black modals with category tabs, page buttons, and title-detail pop-ups instead of the stock cyan top-left options menu. The modals still send ordinary menu replies, so the server remains authoritative and no title-catalog opcode is required.

## Unlock hooks

Automatic titles refresh in these existing progression paths:

- `Player.incQuestPoints(...)`
- `Player.addNpcKill(...)`
- wilderness player kills inside `Player.killedBy(...)`
- level-ups inside `Skills.addExperience(...)`
- opening `::titles`, which also retroactively grants titles for old accounts

For a future custom title, prefer adding a direct `PlayerTitle.unlock(player, PlayerTitle.X)` call at the authoritative completion point of the feature. For example, an auction-house title should unlock where the successful sale is committed, not from a UI command.

`DM Kingslayer` is a reusable manual title unlocked by the authoritative DM King victory resolver in `VoidArena`.

## Client display

The custom appearance update sends the active title as an extra string for custom clients at version `10052` or newer. Authentic and retro clients are not sent this field. The client stores it on `ORSCharacter.title` and composes a one-line overhead label locally: the staff-colored username first, then the red active title. `displayName` and `accountName` stay untouched so menus, social features, and player identity keep using the real username.
