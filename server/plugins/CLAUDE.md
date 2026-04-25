# server/plugins/ — content code

Content lives here: skills, NPCs, dialogue, quests, minigames, shops. **Anything game-content goes in this tree, not in `server/src/`.**

This file auto-loads when editing plugins. Detail: `docs/subsystems/scripting-plugins.md`.

## How plugins are loaded

1. Compile via `ant compile_plugins` from `server/` (produces `server/plugins.jar`).
2. Server boot: `PluginHandler` reads `plugins.jar`, scans `.class` files, instantiates anything implementing a trigger interface.
3. **No hot-reload.** Plugin changes = recompile + restart.

## Trigger interfaces (most common)

From `server/src/com/openrsc/server/plugins/triggers/`:

| Action | Trigger interface |
|---|---|
| Right-click an object ("fish", "pray", "open") | `OpLocTrigger.onOpLoc(Player, GameObject, String)` |
| Use an item on an object | `UseLocTrigger.onUseLoc(Player, GameObject, Item)` |
| Talk to an NPC | `TalkNpcTrigger.onTalkNpc(Player, Npc)` |
| Right-click NPC option | `OpNpcTrigger.onOpNpc(Player, Npc, String)` |
| Use item on NPC | `UseNpcTrigger.onUseNpc(Player, Npc, Item)` |
| Use item on item (combine) | `UseInvTrigger.onUseInv(Player, Integer, Item)` |
| Inventory item action ("eat", "drink") | `OpInvTrigger.onOpInv(Player, Integer, Item, String)` |
| Cast spell on NPC / Player / Loc / Item | `Spell{Npc,Player,Loc,Inv}Trigger` |
| Player attacks NPC | `AttackNpcTrigger.onAttackNpc(Player, Npc)` |
| NPC death | `KillNpcTrigger.onKillNpc(Player, Npc)` |
| Player login / logout | `PlayerLoginTrigger`, `PlayerLogoutTrigger` |
| Quest contract | `QuestInterface` (in `plugins/QuestInterface.java`) |

Each trigger usually has a sibling `block*` method — return `true` to cancel the action, `false` to fall through.

## Layout

```
plugins/com/openrsc/server/plugins/
├── authentic/               # vanilla RSC content
│   ├── npcs/                # shop / dialogue plugins
│   ├── skills/<skill>/      # action handlers (cooking/, fishing/, mining/, …)
│   ├── quests/{free,members}/<Quest>.java
│   └── minigames/
├── custom/                  # OpenRSC custom additions
│   ├── npcs/, skills/, quests/, minigames/
├── RuneScript.java          # static dialogue/delay helpers (legacy name)
├── Functions.java           # quest/NPC/item utilities
└── QuestInterface.java      # quest contract
```

When in doubt: **mirror existing structure**. New skill action → put it under `authentic/skills/<skill>/`. New custom NPC → `custom/npcs/`.

## Common helpers (`RuneScript` / `Functions`)

- `mes("...")` — server-side line; blocks for `delay()`.
- `npcsay(player, npc, "...")` — NPC chat bubble.
- `say(player, "...")` — player chat bubble.
- `delay()` / `delay(ticks)` — pause script.
- `end("...")` — terminate via `ScriptEndedException`.
- `give(player, itemId, amount)` — inventory grant.
- `incExp(player, skill, amount)` — XP grant.
- `incQP(player, n)` — quest points.
- `setQuestStage(player, this, n)` / `getQuestStage(this)` — quest progression.
- `player.getCache().set("namespaced_key", value)` / `.hasKey(...)` / `.get*(...)` — quest scratch state.

## Hard rules

1. **No hot-reload.** Recompile + restart for every change. Don't try to be clever.
2. **Namespace cache keys.** `player.getCache()` is a flat global K/V; `"bananas"` collides across quests. Prefix: `"piratestreasure_bananas"`.
3. **`block*` returns `true` to cancel.** `false` lets the chain continue. Easy to invert.
4. **Don't `delay()` for too long.** Long pauses during peak load backpressure the tick loop.
5. **Trigger ordering is undefined.** If your plugin must fire before another, the chain isn't deterministic — design so order doesn't matter.
6. **No partial-state recovery.** If a quest mid-step crashes, the player can be stuck. Validate state on login if your quest needs to be resilient.
7. **Don't put core mechanics here.** Combat formulas, tick loop, packet protocol live in `server/src/`. Plugins consume those.

## Patterns to copy

- Quest example: `authentic/quests/free/PiratesTreasure.java`. Implements `QuestInterface` + several triggers; uses `getQuestStage()` + cache for state.
- Skill action example: `authentic/skills/fishing/Fishing.java` (`OpLocTrigger`).
- Shop example: `authentic/npcs/GeneralStore.java` (constructs a `Shop` with item list and modifiers).

## Recipes

If you're adding a new content piece, start with the matching playbook:
- `docs/recipes/add-item.md`
- `docs/recipes/add-npc.md`
- `docs/recipes/add-quest.md`
- `docs/recipes/add-skill-action.md`
- `docs/recipes/add-admin-command.md`
