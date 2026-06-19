# Recipe: add a skill action

A "skill action" is the player interacting with an object/item to gain XP and possibly produce a result (e.g. mining a rock, fletching a log, fishing a spot). Each skill has its own plugin folder under `server/plugins/authentic/skills/<skill>/`.

Reference implementations:
- Fishing — `server/plugins/authentic/skills/fishing/Fishing.java` (object-based, OpLocTrigger)
- Cooking — `server/plugins/authentic/skills/cooking/InvCooking.java` (use-item-on-item, UseInvTrigger)
- Smithing — `server/plugins/authentic/skills/smithing/Smithing.java` (more complex with menus)

## Files you'll touch

| File | Why |
|---|---|
| `server/plugins/.../skills/<skill>/<Action>.java` | The action handler |
| `server/conf/server/defs/extras/<Skill>Def.xml` (or `Object<Skill>.xml`) | (optional) data-driven recipe |
| `server/conf/server/defs/ItemDefs.json` / `NpcDefs.json` / `locs/*.json` | (optional) new items, NPCs, world objects |

## Steps

1. **Identify the trigger interface**:
   - Right-click an object → `OpLocTrigger`
   - Use item on object → `UseLocTrigger`
   - Use item on item → `UseInvTrigger`
   - Inventory option ("eat", "drink") → `OpInvTrigger`
2. **Create / edit the handler** in `server/plugins/.../skills/<skill>/`:
   ```java
   public class FishCustomSpot implements OpLocTrigger {
       @Override
       public boolean blockOpLoc(Player p, GameObject obj, String cmd) {
           return obj.getID() == YOUR_OBJECT_ID && "fish".equalsIgnoreCase(cmd);
       }

       @Override
       public void onOpLoc(Player p, GameObject obj, String cmd) {
           if (p.getSkills().getLevel(Skills.FISHING) < 99) {
               mes("You need level 99 fishing to use this spot.");
               return;
           }
           if (p.getCarriedItems().getInventory().countId(ItemId.NET.id()) < 1) {
               mes("You need a net.");
               return;
           }
           mes("You attempt to catch a void fish...");
           delay(3);
           if (Formulae.calcGatheringSuccess(...)) {
               give(p, ItemId.VOID_FISH.id(), 1);
               incExp(p, Skills.FISHING, 200);
               mes("You catch a void fish!");
           } else {
               mes("You fail.");
           }
       }
   }
   ```
3. **Data-driven (where applicable)**: many skills (cooking, smithing, fletching) use XML defs for recipes. Edit `server/conf/server/defs/extras/<Skill>Def.xml` instead of hardcoding.
4. **Compile plugins + restart**:
   ```bash
   cd server && ant compile_plugins
   scripts/run-server.sh
   ```
5. **Test**: low and high level, with/without required items, success and failure paths.

## Common patterns

- **Tick-based progress**: `delay(3)` = 3 server ticks (~2s). Loop for repeated attempts.
- **Inventory checks**: `p.getCarriedItems().getInventory().countId(ItemId.X.id())`.
- **Gain XP**: `incExp(player, skillId, amount)`. Skill IDs in `Skills.java`.
- **Required level**: check `p.getSkills().getLevel(skillId)`.
- **Random success**: use `Formulae.calcGatheringSuccess(...)` for skill-typical curves; or roll your own with `DataConversions.random(0, max)`.
- **Tool wear / consumption**: remove from inventory with `p.getCarriedItems().remove(new Item(...))`.

## Verification checklist

- [ ] Action gated by required level.
- [ ] Action gated by required tool / item.
- [ ] Success rate reasonable (test 50+ attempts).
- [ ] XP grant matches expected amount.
- [ ] Failure path doesn't grant XP or consume tool incorrectly.
- [ ] Cannot start the action while in combat / busy.
- [ ] Voidscape-tuned XP rates: keep authentic; document any QoL boost in `docs/DIVERGENCE.md`.

## Common pitfalls

- **`block*` returns `true` to gate.** Easy to invert.
- **`delay()` halts the script** — long delays during peak load backpressure the tick loop.
- **Forgetting to recompile plugins** after edit.
- **Hardcoding rates instead of using XML defs** — drifts from authentic, hard to tune.
- **Granting XP twice** when both branches of a conditional grant.
- **Not respecting `member_world: false`** for members-only content — guard with `if (p.getConfig().MEMBER_WORLD)`.
