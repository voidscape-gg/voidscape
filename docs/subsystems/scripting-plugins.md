# Plugins and Scripting

Plugin loader, trigger interfaces, per-skill organization, quest pattern, dialogue system, and tick-driven activities.

## Plugin system architecture

**Compilation**: `server/compile_plugins.cmd` (or `.sh`) invokes Ant target `compile_plugins` → outputs `server/plugins.jar`.

**Loading**: `server/src/com/openrsc/server/plugins/io/PluginJarLoader.java`.
- Loads `plugins.jar` via `URLClassLoader`.
- Scans `.class` files (excluding inner classes containing `$`) and instantiates them.
- Alternative `loadClasses()` reads from classpath (development mode).

**Registration**: `server/src/com/openrsc/server/plugins/handler/PluginHandler.java`.
- `initPlugins()` iterates loaded classes.
- For each implementing a trigger interface, instantiate via Guice and register.
- `Multimap<Class, Plugin> triggerTypeToInstance` indexes plugin instances by trigger type.
- Dispatch: `handlePlugin()` invokes matching trigger methods on registered instances.

**Boot flow**:
1. Server constructs `PluginHandler` → loads `plugins.jar`.
2. `initPlugins()` runs during server start.
3. Trigger types auto-discovered via classpath scan of `com.openrsc.server.plugins.triggers`.
4. **No hot reload** — plugin changes require recompile + restart.

## Trigger interfaces

All in `server/src/com/openrsc/server/plugins/triggers/`. The `block*` variant returns a boolean — if `true`, the action is intercepted/cancelled before the `on*` handler runs.

| Trigger | Method | Fires when |
|---|---|---|
| `AttackNpcTrigger` | `onAttackNpc(Player, Npc)` + `blockAttackNpc()` | Player attacks NPC |
| `AttackPlayerTrigger` | `onAttackPlayer(Player, Player)` + `blockAttackPlayer()` | Player attacks player |
| `TalkNpcTrigger` | `onTalkNpc(Player, Npc)` + `blockTalkNpc()` | Player talks to NPC |
| `OpNpcTrigger` | `onOpNpc(Player, Npc, String)` + `blockOpNpc()` | NPC right-click option |
| `UseNpcTrigger` | `onUseNpc(Player, Npc, Item)` + `blockUseNpc()` | Item used on NPC |
| `UseLocTrigger` | `onUseLoc(Player, GameObject, Item)` + `blockUseLoc()` | Item used on object |
| `OpLocTrigger` | `onOpLoc(Player, GameObject, String)` + `blockOpLoc()` | Object right-click (e.g. "fish") |
| `OpInvTrigger` | `onOpInv(Player, Integer, Item, String)` + `blockOpInv()` | Inventory item action |
| `SpellNpcTrigger` | `onSpellNpc(Player, Npc, int)` | Spell cast on NPC |
| `SpellPlayerTrigger` | `onSpellPlayer(Player, Player, int)` | Spell cast on player |
| `SpellLocTrigger` | `onSpellLoc(Player, GameObject, int)` | Spell cast on object |
| `SpellInvTrigger` | `onSpellInv(Player, Item, int)` | Spell cast on inventory item |
| `KillNpcTrigger` | `onKillNpc(Player, Npc)` | NPC death (before drop) |
| `PlayerDeathTrigger` | `onPlayerDeath(Player, Mob)` | Player death |
| `PlayerLoginTrigger` | `onPlayerLogin(Player)` | Login complete |
| `PlayerLogoutTrigger` | `onPlayerLogout(Player)` | Logout start |
| `DropObjTrigger` | `onDropObj(Player, Item)` + `blockDropObj()` | Item drop |
| `TakeObjTrigger` | `onTakeObj(Player, GroundItem)` + `blockTakeObj()` | Item pickup |
| `WearObjTrigger` | `onWearObj(Player, Item)` + `blockWearObj()` | Item equip |
| `UseInvTrigger` | `onUseInv(Player, Integer, Item)` + `blockUseInv()` | Inventory item used on item |
| `WineFermentTrigger` | wine fermentation timing | |
| `CatGrowthTrigger` | cat growth state changes | |
| `CommandTrigger` | server command execution | |
| `EscapeNpcTrigger` | NPC retreat mechanics | |

## Plugin discovery and registration

`PluginJarLoader.loadJar()`:
```java
for (JarEntry je : jarFile.entries()) {
    if (je.getName().endsWith(".class") && !je.getName().contains("$")) {
        String className = je.getName().substring(0, ...).replace('/', '.');
        Class<?> c = urlClassLoader.loadClass(className);
        loadedClasses.add(c);
    }
}
```

Trigger types are auto-loaded via `loader.loadTriggers("com.openrsc.server.plugins.triggers")`.

`PluginHandler.initPlugins()`:
1. Iterate `loadedClasses`.
2. Recognize plugin contracts: `DefaultHandler`, `MiniGameInterface`, `QuestInterface`, `AbstractRegistrar`.
3. For each plugin class, find implemented triggers via `ClassUtils.hierarchy(pluginType, Interfaces.INCLUDE)`.
4. Intersect with `triggerTypes`.
5. Instantiate via Guice, register: `triggerTypeToInstance.put(triggerType, instance)`.

Dispatch: `handlePlugin(triggerType, ...)` → fetch all instances for that trigger → invoke matching method on each.

## Per-skill organization

Most skills mix core in `server/src/` (constants, defs, base formulas) with plugins in `server/plugins/` (handlers, dialogue, state machines).

| Skill | Core | Plugin |
|---|---|---|
| Attack | `model/entity/Mob.java`, `CombatFormula.java` | combat scripts in `event/rsc/impl/combat/scripts/all/` |
| Defense | `CombatFormula.java` | combat scripts |
| Strength | `CombatFormula.java` | combat scripts |
| Hits | `model/Skills.java`, `Damage.java` | `PoisonEvent.java`, healing triggers |
| Ranged | `CombatFormula.java`, `RangeEvent.java` | (no dedicated plugin folder) |
| Prayer | `Prayers.java`, `PrayerDrainEvent.java`, handlers | `plugins/authentic/skills/prayer/Prayer.java` |
| Magic | `SpellHandler.java`, `CombatFormula.calculateMagicDamage()` | spell cast handlers per-spell |
| Cooking | `external/ItemCookingDef.java` | `plugins/authentic/skills/cooking/InvCooking.java`, `ObjectCooking.java` |
| Woodcutting | `external/ObjectWoodcuttingDef.java` | `plugins/authentic/skills/woodcutting/Woodcutting.java`, `WoodcutJungle.java` |
| Fletching | `external/ItemFletchingDef.java` | `plugins/authentic/skills/fletching/Fletching.java` |
| Fishing | `external/ObjectFishingDef.java`, `ObjectFishDef.java` | `plugins/authentic/skills/fishing/Fishing.java` |
| Firemaking | `external/ItemFiremakingDef.java` | `plugins/authentic/skills/firemaking/Firemaking.java` |
| Crafting | `external/ItemCraftingDef.java` | `plugins/authentic/skills/crafting/Crafting.java`, `Mould.java`, `BattlestaffCrafting.java` |
| Smithing | `external/ItemSmithingDef.java` | `plugins/authentic/skills/smithing/Smithing.java`, `Smelting.java` |
| Mining | `external/ObjectMiningDef.java`, `ObjectMineDef.java` | `plugins/authentic/skills/mining/Mining.java`, `GemMining.java` |
| Herblaw | `external/ItemHerblawDef.java` | `plugins/authentic/skills/herblaw/Herblaw.java` |
| Agility | `external/ObjectAgility*Def.java` | `plugins/authentic/skills/agility/GnomeAgilityCourse.java`, … |
| Thieving | `external/ObjectThievingDef.java` | `plugins/authentic/skills/thieving/Thieving.java`, `LootItem.java` |

Pattern: definitions/constants in core, action behavior + dialogue in plugin.

## Quest implementation pattern

Interface: `server/src/com/openrsc/server/plugins/QuestInterface.java`:
```java
int getQuestId();
String getQuestName();
int getQuestPoints();
boolean isMembers();
void handleReward(Player player);
```

Example: `server/plugins/authentic/quests/free/PiratesTreasure.java`.

Structure:
1. Implements `QuestInterface` + relevant trigger interfaces (`TalkNpcTrigger`, `OpLocTrigger`, `UseLocTrigger`).
2. Quest stage tracked via `player.getQuestStage(this)` — `0` not started, `-1` complete, `1+` in progress.
3. State machine via player cache: `player.getCache().set("bananas", count)`, `getCache().hasKey("rum_in_crate")`.
4. Triggers check stage + cache → unlock next step.
5. Completion: `handleReward()` grants items, XP, quest points (`incQP()`, `incStat()`, `give()`).

Flow:
- Quest start: NPC dialogue → `updateQuestStage(this, 1)`.
- Mid-quest: triggers gate on stage + cache + objectives.
- End: `handleReward()`, stage set to `-1`.

## Item / object interaction dispatch (UseLoc example)

1. Client sends use-item-on-object packet.
2. `UseLocHandler` extracts `(player, gameObject, item)`.
3. `PluginHandler` iterates `triggerTypeToInstance.get(UseLocTrigger.class)`.
4. For each plugin:
   - Call `blockUseLoc()` — if `true`, stop (e.g. "You can't use that here").
   - Call `onUseLoc()` to execute.
5. If no plugin blocks, default behavior runs.

Fishing example — `plugins/authentic/skills/fishing/Fishing.java`:
- `onOpLoc(player, object, "fish")` → checks level, tools, object type.
- `handleFishing()` → success roll → item added + XP granted.
- All static state (rod type, bait, location) lives in `ObjectFishingDef` from XML.

## NPC dialogue system

`server/src/com/openrsc/server/plugins/RuneScript.java` and `Functions.java`.

Linear, callback-based — **no AST or state machine**. Branching uses cache flags.

Key functions:
- `mes(String...)` — server-side message; blocks for `delay()`.
- `npcsay(Player, Npc, String)` — NPC bubble.
- `say(Player, String)` — player bubble.
- `delay()` / `delay(int)` — pause script (ticks).
- `end(String)` — `ScriptEndedException` to terminate.

Example:
```java
npcsay(player, npc, "What do you want?");
delay(2);
mes("You explain your quest...");
delay(3);
if (player.getQuestStage(this) == 0) {
    setQuestStage(player, this, 1);
    npcsay(player, npc, "Go find the chest!");
}
```

## Tick-driven activities

Common pattern: `delay()` + state check + reward.

1. **Cooking** — `ObjectCooking.java`:
   - `onUseLoc()` → set busy flag.
   - `delay(3)` → 3 tick pause.
   - Success check → consume raw, add cooked, `incExp()`.

2. **Fishing** — `Fishing.java`:
   - `onOpLoc()` → check level, tool, spot.
   - Loop: success → add catch + XP.
   - Terminate: inventory full or player moves.

3. **Prayer drain** — `PrayerDrainEvent.java`:
   - `GameTickEvent` with `delayTicks = 1`.
   - `pointDrain = ceil(totalDrainRate * 120 / (300 * (1 + (prayerPoints - 1) / 32)))`.
   - Level reduction: `level = ceil(points / 120)`.

4. **Poison** — `PoisonEvent.java`:
   - `delayTicks = 32` (32-tick interval, not ms).
   - Each tick: power -= 2, deal `floor(power/10)` damage.
   - Stop at power < 10.

5. **Smithing / smelting**:
   - Player picks quantity → loop.
   - Per item: `delay(3-5)` + success → add result.
   - Implicit progress via `delay()` cycles.

Backbone: `PluginTask` thread pool managed by `GameEventHandler`. All `delay()` calls wrap `PluginTask.delay(ticks)`. Ticks are server ticks (640ms default).

## Pitfalls / non-obvious

1. **No hot reload.** Every plugin change → recompile `plugins.jar` + server restart.
2. **Trigger ordering is undefined.** `Multimap` iteration order; multiple plugins for same trigger fire in unspecified order. Don't depend on order for stacking effects.
3. **Cache key collisions.** `player.getCache()` is a flat map. `"bananas"` from one quest can collide with another. Namespace your keys (e.g. `"piratestreasure_bananas"`).
4. **Synchronous dialogue blocking.** `delay()` halts the script. Long dialogues during peak load can backpressure the tick loop.
5. **No transaction/rollback.** Mid-quest crashes leave partial state. Check invariants on login if needed.
6. **Combat script non-determinism.** Reflection-loaded; if multiple `shouldExecute()` match, order undefined.
7. **Prayer drain formula is opaque.** Rates live in `PrayerDef.xml`; no in-code documentation.
8. **NPC aggression hardcoded in places.** `BLACK_KNIGHT`, `BANDIT_AGGRESSIVE` aggro radii baked into `NpcBehavior`, not data-driven.
9. **PID shuffling caveats.** `CombatEvent` tick cycling differs if `SHUFFLE_PID_ORDER` is on; subtle PvP desync risk under bad timing.
10. **`block*` semantics**: returning `true` cancels the action. Returning `false` lets the next handler in the chain proceed. Easy to invert by accident.

## Glossary candidates

- **GameTickEvent** — base class for periodic server events (combat, poison, prayer drain).
- **DuplicationStrategy** — controls whether multiple instances of the same event can run on the same mob (`ALLOW_MULTIPLE`, `ONE_PER_MOB`, …).
- **PluginTask** — thread-safe wrapper for plugin script execution; carries `ScriptContext`.
- **ScriptContext** — captures interaction state (player, NPC, item, object) during plugin trigger callback.
- **RuneScript** — static helper class with dialogue/delay/messaging functions (legacy RSC scripting name).
- **Functions** — utility layer over `RuneScript`; quest, NPC, item helpers.
- **CombatScript** — interface for NPC special attacks; loaded dynamically.
- **OnCombatStartScript** — fires once when combat begins.
- **CombatAggroScript** — fires when NPC aggros on player.
- **CombatSideEffectScript** — fires during damage infliction (e.g. poison application).
- **Trigger interface** — contract for plugin entry points.
- **PluginHandler.triggerTypeToInstance** — `Multimap<Trigger, Plugin>` registry mapping each trigger type to all implementing plugins.
- **Pot-poison** — poisoned weapons have 1/50 chance to apply poison on NPC hit.
- **PID (Process / Player ID)** — login order; determines PvP tick cycling.
