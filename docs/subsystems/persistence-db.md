# Persistence and Data Model

DB engines, schema, connection layer, save model, and **where every category of static game data lives**.

## DB engines

Both **MySQL/MariaDB** and **SQLite** are first-class. SQLite is the default (`DatabaseType.DEFAULT = SQLITE`).

Selection — `server/src/com/openrsc/server/database/DatabaseType.java` (`resolveType()`):
- Configured in `server/connections.conf` (or whichever conf file you start with) via `db_type`.
- SQLite: file-based, opens `inc/sqlite/<db_name>.db` via `DriverManager.getConnection("jdbc:sqlite:")` (`SqliteGameDatabaseConnection.java`).
- MySQL: standard JDBC connection (`MySQLDatabaseConnection.java`), credentials in `connections.conf`.

For dev: **SQLite is recommended** (zero setup). MariaDB via Docker is for collaborative testing or production simulation.

## Schema and migrations

Core schema files:
- MySQL: `server/database/mysql/core.sql` (~660 lines)
- SQLite: `server/database/sqlite/core.sqlite` (text-based SQL)

Migrations (cumulative patches applied chronologically):
- `server/database/mysql/patches/` and `server/database/sqlite/patches/`
- Format: `YYYY_MM_DD_description.sql` (e.g. `2023_12_23_change_itemid_to_bigint.sql`)
- Tracking table: `db_patches`
- Applied automatically at boot via `JDBCPatchApplier`

Versioned schema upgrades:
- `server/database/mysql/upgrades/` (~11 files)
- Format: `N_description_VERSION.sql` (e.g. `2_alter_redundant_columns_5.0.0.sql`)
- Includes large conversions like `convert_core_4.3.0.sql` (228 KB)

~46 tables (core + patches): players, skills, inventory, equipment, social (friends/ignores), logging, security.

## Connection layer

JDBC + prepared statements, **no ORM**.

- Abstract base: `GameDatabase.java`
- JDBC wrapper: `JDBCDatabase.java` extends `GameDatabase`
- Connection abstractions:
  - `JDBCDatabaseConnection.java` (abstract; synchronizes all DB calls)
  - `MySQLDatabaseConnection.java`
  - `SqliteGameDatabaseConnection.java`

Helper pattern:
```java
withPreparedStatement(query, (CheckedFunction or CheckedConsumer) -> { ... })
```
Setters: `setInt`, `setString`, etc. Batch execution for bulk inserts (bank items, inventory).

**No connection pool** — single synchronized connection per database. Bottleneck under high concurrency.

Queries are split between embedded SQL in service code and external XML query files:
- `server/database/mysql/queries/` and `server/database/sqlite/queries/`
- Files: `player.xml`, `patches.xml`, `bank_presets.xml` (SQLite-only), `item.xml` (SQLite-only)
- Loaded by `QueriesManager` at startup, parameterized via `{username}`, `{playerId}`, `{items}`, etc.

## Service / repository pattern

Service-based, not classic DAOs.

- **`PlayerService.java`** (`server/src/com/openrsc/server/service/`): central player persistence orchestrator. `loadPlayer(LoginRequest)`, `savePlayer(Player)`. Wraps multi-step saves in `database.atomically(...)`.
- Direct queries embedded in `MySqlGameDatabase.java` and `SqliteGameDatabase.java`.
- Logging uses dedicated query classes: `LoginLog.java`, `ChatLog.java`, `TradeLog.java`, `StaffLog.java`.

Save chain example (`PlayerService.savePlayer()`):
```
database.atomically(() -> {
  savePlayerBankPresets()
  savePlayerInventory()
  savePlayerEquipment()
  savePlayerBank()
  savePlayerQuests()
  savePlayerData()
  savePlayerSkills()
  savePlayerSocial()
})
```

## Player save: fields, schedule, size

**`players` table** — core columns (from `core.sql`):
- Identity: `id`, `username`, `group_id`, `creation_date`, `creation_ip`, `login_ip`, `login_date`
- Auth: `pass` (hashed), `salt`
- Position: `x`, `y`
- Combat: `combat`, `combatstyle`
- Skill total: `skill_total`
- Appearance: `male`, `haircolour`, `topcolour`, `trousercolour`, `skincolour`, `headsprite`, `bodysprite`
- Settings: `cameraauto`, `onemouse`, `soundoff`, `block_chat`, `block_private`, `block_trade`, `block_duel`
- Progress: `quest_points`, `kills`, `deaths`, `npc_kills`, `offences`
- State: `fatigue`, `bank_size`, `online`, `banned`, `muted`, `hideOnline`

Related tables (linked by `playerID`):
- `experience`, `curstats`, `maxstats`, `capped_experience` — 19 skills × N values
- `invitems` — inventory (slot-indexed)
- `bank` — bank items
- `equipped` — equipment
- `itemstatuses` — per-instance metadata (catalogID, amount, noted, wielded, durability)
- `friends`, `ignores`, `private_message_logs`
- `quests` — per-player quest progress
- `ironman` — ironman flags + HC death
- `player_cache` — arbitrary key-value (type 0 int, 1 string, 2 long)
- `npckills` — kill counts per NPC

Triggers:
- **Autosave**: every 30s (`AUTO_SAVE` in `ServerConfiguration`, default 30000ms). Driven from `GameStateUpdater.java:105–108`:
  ```java
  if (curTime - player.getLastSaveTime() >= autoSave && player.loggedIn()) {
      player.timeIncrementActivity();
      player.save();
      player.setLastSaveTime(curTime);
  }
  ```
- **Logout**: explicit `new PlayerSaveRequest(server, player, true)` from `Player.logout()`.
- Async: queued to `LoginExecutor`. Save is wrapped in `database.atomically(...)`.

Typical save size: ~2–3 KB per player (player row ~500B, inventory ~224B, bank ~1.5KB, equipment ~104B, skills ~171B).

## Static game data — where to edit what

**Items** — `server/conf/server/defs/`:
- `ItemDefs.json` (~891 KB) — authentic items
- `ItemDefsCustom.json` (~212 KB) — custom additions
- `ItemDefsPatch18.json` (~50 KB) — patch updates
- Loaded by `EntityHandler.loadItems()` into `ArrayList<ItemDefinition>`.
- Fields: id, name, description, commands, isFemaleOnly, isMembersOnly, isStackable, isUntradable, isWearable, appearanceID, wearSlot, requiredLevel, requiredSkillID, prices, bonuses.

**NPCs** — `server/conf/server/defs/`:
- `NpcDefs.json` (~772 KB)
- `NpcDefsCustom.json` (~35 KB)
- `NpcDefsPatch18.json` (~40 KB)
- Loaded by `EntityHandler.loadNpcs()`.
- Fields: id, name, description, examine, maxHealth, combat, skills, bonuses, spells, drops.

**Scenery / objects (walls, boundaries, ground objects)**:
- Per-instance locations: `server/conf/server/defs/locs/SceneryLocs.json` (~2.2 MB main world)
- Map-version variants: `SceneryLocs14.json`, `SceneryLocs27.json`
- Custom/expansion: `SceneryLocsCustomQuest.json`, `SceneryLocsExpansion.json`, `SceneryLocsHarvesting.json`, `SceneryLocsModRoom.json`, `SceneryLocsOpenPk.json`, `SceneryLocsOther.json`, `SceneryLocsRunecraft.json`, `SceneryLocsWoodcuttingGuild.json`
- Format: JSON array `{id, x, y, direction, type}`.
- Object property defs: `server/conf/server/defs/GameObjectDef.xml` (~426 KB).
- Boundary objects: `server/conf/server/defs/locs/BoundaryLocs.json` (~81 KB).
- Doors: `server/conf/server/defs/DoorDef.xml` (~67 KB).
- Loaded by `WorldPopulator.loadGameObjLocs()`.

**Ground items (respawning loot)** — `server/conf/server/defs/locs/`:
- `GroundItems.json` (~101 KB) main + map variants `GroundItems14.json`, `GroundItems27.json`
- Custom: `GroundItemsCustomQuest.json`, `GroundItemsHarvesting.json`, `GroundItemsMiceToMeetYou.json`
- Format: `{id, x, y, amount, respawn}` (respawn in seconds).
- Loaded by `WorldPopulator.loadItemLocs()`.

**NPC spawns and patrol bounds** — `server/conf/server/defs/locs/`:
- `NpcLocs.json` (~517 KB) main + variants `NpcLocs14.json`, `NpcLocs27.json`
- Specialized: `NpcLocsAuction.json`, `NpcLocsCustomQuest.json`, `NpcLocsHarvesting.json`, `NpcLocsIronman.json`, `NpcLocsModRoom.json`, `NpcLocsOpenPk.json`, `NpcLocsOther.json`, `NpcLocsPkBots.json`, `NpcLocsRunecraft.json`
- Format: `{id, startX, minX, maxX, startY, minY, maxY}`.
- Loaded by `WorldPopulator.loadNpcLocs()`.

**NPC drops (loot tables)** — **HARDCODED IN JAVA** — `server/src/com/openrsc/server/constants/NpcDrops.java`.
- Methods: `createMobDrops()`, `createHerbDropTable()`, `createRareDropTable()`, etc.
- `HashMap<Integer, DropTable>` keyed by NPC ID.
- `DropTable` holds `ArrayList<Drop>` (item ID, amount, weight, noted).
- Special tables: herbs, rare/mega-rare/ultra-rare, bones, demon ashes.
- Bad-luck mitigation tracker: `BadLuckMitigation` class.
- **Editing requires a recompile and server restart** — there is no JSON for drops.

**Shops** — **DEFINED IN PLUGIN CODE** — `server/plugins/com/openrsc/server/plugins/{authentic,custom}/npcs/`.
- Examples: `GeneralStore.java`, `CraftingEquipmentShops.java`, etc.
- Construction: `new Shop(general, respawnRate, buyModifier, sellModifier, priceModifier, items...)`.
- Base price: `ItemDefinition.defaultPrice` × shop modifiers.
- Editing requires plugin recompile.

**Skill data** — `server/conf/server/defs/extras/`:
- `ItemCookingDef.xml`, `ItemCraftingDef.xml`, `ItemSmithingDef.xml`
- `ObjectMining.xml`, `ObjectFishing.xml`, `ObjectWoodcutting.xml`
- `FiremakingDef.xml`, `ItemHerbDef.xml`, `ItemHerbSecond.xml`, `ItemUnIdentHerbDef.xml`
- `ObjectRunecraft.xml`
- Loaded by `EntityHandler` into `HashMap`s.

**Spells** — `server/conf/server/defs/SpellDef.xml` (~26 KB), retro variant `SpellDefRetro.xml`. Loaded as `SpellDef[]`.

**Prayers** — `server/conf/server/defs/PrayerDef.xml`. Loaded as `PrayerDef[]`.

**Tiles / terrain definitions** — `server/conf/server/defs/TileDef.xml`.

**Quest data**:
- Per-player state in `quests` table.
- Quest definitions are not in a JSON — they are coded as plugin classes implementing `QuestInterface` (see `scripting-plugins.md`).

**Teleport points** — `server/conf/server/defs/extras/ObjectTelePoints.xml`.

**Map data (terrain, collision)** — `server/conf/server/data/maps/` (30 subdirectories):
- Authentic: `Authentic_Landscape.orsc` (~945 KB), `Authentic_Sprites.orsc` (~1.3 MB)
- Custom: `Custom_Landscape.orsc` (~973 KB), `Custom_Sprites.osar` (~924 KB)
- `.orsc` / `.osar` are OpenRSC binary cache formats. Loaded by `WorldLoader`.

## Definition loading at startup

`EntityHandler.java` (`server/src/com/openrsc/server/external/`) — singleton accessed via `server.getEntityHandler()`.

Sequence:
1. `loadNpcs(NpcDefs.json)` + `loadNpcs(NpcDefsCustom.json)` → populates `npcs`, `npcs.patch`, `HashSet<String> npcNames`.
2. `loadItems(ItemDefs.json)` + `loadItems(ItemDefsCustom.json)` → populates `items`.
3. Skill-specific data (cooking, smithing, fishing, mining, woodcutting, …) into per-skill `HashMap`s.
4. Spells, prayers, doors, tiles → arrays/maps.
5. Definitions queryable globally: `getItemDef(id)`, `getNpcDef(id)`, etc.

Spawning: `WorldPopulator.populateWorld()` reads location JSONs, instantiates `GameObject` and `Npc` at coords; defs fetched on-demand from the cache.

## `server/avatars/`

Stores optional per-player avatar PNGs generated by the OpenRSC server when `avatar_generator: true`. Voidscape's local portal reads `server/avatars/<db_name>+<playerId>.png` through its `/openrsc/avatar/<playerId>.png` route so the account dashboard can show the real saved character render after logout. Appearance still lives in the `players` table columns (`male`, `haircolour`, `topcolour`, etc.); custom appearance overrides could land in `player_cache`.

## `Backups/`

Empty (only `.gitsave`). Intended for manual SQL dumps. No automated backup pipeline in code — that's left to `Deployment_Scripts/` or operator workflow.

## Seasonal world-achievement ledger

Migration `2026_07_11_add_world_achievements.sql` exists for both MySQL and SQLite and creates three prefix-safe tables:

- `world_achievement_records` — immutable first-achievement records keyed by season and stable record key, with a second source-event uniqueness contract.
- `world_pk_events` — immutable, UUID-keyed qualified/rejected PvP-death audit events.
- `world_pk_streaks` — mutable per-season/per-character current/best streak and qualified-kill projection.

`WorldAchievementLedger` owns the shared SQL and validation contract; `GameDatabase` exposes engine-neutral load/insert/update methods. Higher-level policy belongs to `WorldAchievementService`, `WorldPkEvaluation`, `WorldPkSettlementService`, and the cracker award transaction. Those coordinators use settled transactions and exact after-state verification so a lost COMMIT acknowledgement cannot create a guessed winner or streak.

Machine identifiers are case-sensitive canonical strings (`ascii_bin` on MySQL). Public-facing character names remain separate. The private event ledger may retain linked account IDs and stable rejection reasons for anti-abuse review, but it stores no raw IP address. See `docs/subsystems/world-achievements.md` for the complete policy and the later public-API privacy boundary.

## Pitfalls / non-obvious

1. **No ORM = SQL changes are fragile.** Schema migrations require coordinated edits across `core.sql`, query XML files, and `MySqlGameDatabase`/`SqliteGameDatabase`.
2. **Single-connection synchronization.** `JDBCDatabaseConnection` serializes all DB calls. High-concurrency saves (30+ concurrent) queue up.
3. **Item ID was widened to BIGINT.** Migration `2023_12_23_change_itemid_to_bigint.sql`. Code using `int itemId` risks overflow — use `long`.
4. **SQLite vs MySQL behavioral drift.** Some queries are duplicated per-engine. Transaction syntax differs (`BEGIN/END TRANSACTION` vs `START TRANSACTION/COMMIT`). Bank presets queries are SQLite-only.
5. **Static defs require server restart.** Editing `ItemDefs.json` or any def XML requires a restart. Items already in player inventories use cached metadata until re-load.
6. **Shop inventory is runtime-only.** Reset on server crash. Plan shop persistence yourself if needed.
7. **NPC drops are hardcoded.** No in-game admin tool. Changes require plugin/server recompile.
8. **No FK constraints.** Schema is largely FK-less; orphaned inventory items possible if a player row is deleted manually.
9. **Bank presets feature gap.** SQLite has the queries; MySQL may not. Verify before relying.
10. **Ironman state is split.** `players` row + dedicated `ironman` table. Atomic updates required for consistency.
11. **`fatigue` column is legacy when fatigue is disabled.** Field still persists; setting `want_fatigue: false` in conf doesn't drop the column.
12. **`player_cache` is untyped K/V.** Convenient for quest scratch state but slow on large reads — use sparingly.

## Glossary candidates

- **Atomically** — wraps multiple DB ops in a transaction (`database.atomically(Runnable)`).
- **PreparedStatement** — parameterized SQL; prevents injection.
- **WorldPopulator** — loads location JSONs and instantiates entities into the world.
- **EntityHandler** — singleton registry of all definitions loaded at startup.
- **DropTable** — weighted loot table; rolled by random weight.
- **Shop** — runtime inventory with buy/sell modifiers and respawn timer.
- **ItemStatus** — per-instance metadata (catalogID, amount, noted, durability).
- **PlayerSaveRequest** — async request queued to `LoginExecutor`.
- **LoginExecutor** — separate thread for save/logout; ensures non-blocking persistence.
- **Ironman mode** — self-sufficiency restriction; tracked in dedicated table.
- **Bank preset** — named bank layout (partial implementation).
- **Bad-luck mitigation** — tracks consecutive non-rare rolls to enforce eventual rare drops.
- **`.orsc` / `.osar`** — OpenRSC binary cache formats for terrain/sprites.
