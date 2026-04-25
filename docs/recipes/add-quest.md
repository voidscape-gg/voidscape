# Recipe: add a quest

Quests are plugin classes implementing `QuestInterface` plus the trigger interfaces for steps. State is tracked via `player.getQuestStage(this)` (-1 = complete, 0 = not started, 1+ = in progress) and `player.getCache()` for branching state.

Reference implementation: `server/plugins/authentic/quests/free/PiratesTreasure.java`.

## Files you'll touch

| File | Why |
|---|---|
| `server/plugins/.../quests/{free,members,custom}/<YourQuest>.java` | Quest class |
| `server/conf/server/defs/NpcDefs.json` (or Custom) | Any new NPCs the quest needs |
| `server/conf/server/defs/locs/NpcLocs.json` | Spawn locations for quest NPCs |
| `server/conf/server/defs/locs/SceneryLocs.json` | (optional) quest objects (chests, doors) |
| `server/conf/server/defs/ItemDefs.json` (or Custom) | (optional) quest items |
| **No client changes** unless adding new sprites |

## Steps

1. **Pick a quest ID**. Authentic IDs are taken (see existing files); voidscape-original quests get IDs ≥ 100.
2. **Create the quest class** at `server/plugins/.../quests/custom/<YourQuest>.java`:
   ```java
   public class YourQuest implements QuestInterface, TalkNpcTrigger, OpLocTrigger, UseLocTrigger {
       @Override public int getQuestId() { return 100; }
       @Override public String getQuestName() { return "Your Quest"; }
       @Override public int getQuestPoints() { return 1; }
       @Override public boolean isMembers() { return false; }

       @Override
       public boolean blockTalkNpc(Player p, Npc npc) {
           return npc.getID() == NpcId.YOUR_QUEST_NPC.id();
       }
       @Override
       public void onTalkNpc(Player p, Npc npc) { /* dialogue tree */ }

       // ... other triggers as needed
       @Override
       public void handleReward(Player p) {
           p.getCache().remove("yourquest_progress");
           incQP(p, getQuestPoints());
           give(p, ItemId.REWARD.id(), 1);
           incExp(p, Skills.ATTACK, 100);
       }
   }
   ```
3. **State machine**: use `player.getQuestStage(this)` and `setQuestStage(player, this, n)`. Branching state in `player.getCache()`. **Namespace cache keys** with quest name: `"yourquest_step1_done"`.
4. **Triggers**: implement only what you need. Each trigger usually has a `block*` variant — return `true` to gate the action to your quest only.
5. **Quest NPCs / objects / items**: add to the appropriate JSON files (see `add-npc.md`, `add-item.md`).
6. **Compile + restart**:
   ```bash
   cd server && ant compile_plugins
   scripts/run-server.sh
   ```
7. **Test the full path**: start, mid-steps, completion. Use admin commands to set quest stages directly during iteration.

## Quest stage convention

- `-1` — complete (rewards already given)
- `0` — not started
- `1, 2, 3, …` — in progress, monotonically increasing

`handleReward()` is called when you transition to `-1`. Set the stage to `-1` from inside the final trigger, then `handleReward` is invoked by the framework.

## Dialogue patterns

Use `RuneScript` helpers (auto-imported in plugins):
```java
npcsay(player, npc, "What can I do for you?");
delay(2);
mes("You explain you're looking for the chest.");
delay(3);
say(player, "Where could it be?");
```

For multiple-choice menus, see existing quests for the pattern (`Functions.multi(...)`-style).

## Verification checklist

- [ ] Quest appears in player's quest list (correct name, members flag).
- [ ] Each step triggers the correct dialogue / interaction.
- [ ] Quest stage advances correctly through the full flow.
- [ ] Cannot start the next step out of order.
- [ ] Completion rewards: items, XP, quest points.
- [ ] After completion, talking to the NPC says "thanks again" (no re-reward loop).
- [ ] Cache keys are quest-namespaced — no collision with other quests.
- [ ] Login mid-quest preserves state (test by logging out + back in).

## Common pitfalls

- **Cache key collisions.** Always namespace: `"yourquest_x"`, never `"x"`.
- **Forgetting to set stage to `-1`** on final step → reward fires repeatedly.
- **Returning `true` from `blockTalkNpc`** when you don't want to gate — it cancels the entire NPC interaction.
- **Long `delay()` chains** can backpressure the tick loop. Keep dialogues tight.
- **Plugin recompile required** — `ant compile_plugins` after every edit.
- **Quest NPCs with combat level**: if attackable, drops fire too; intentional or not?
