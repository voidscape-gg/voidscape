# Recipe: add an item

Adds a new item to voidscape. Authentic items go in `ItemDefs.json`; voidscape-original items in `ItemDefsCustom.json`.

## Files you'll touch

| File | Why |
|---|---|
| `server/conf/server/defs/ItemDefsCustom.json` | New item def (id, name, stats, prices, sprite, etc.) |
| `server/src/com/openrsc/server/constants/NpcDrops.java` | (optional) add to NPC drop tables — recompile |
| `server/conf/server/defs/locs/GroundItems.json` | (optional) place as world spawn |
| `server/plugins/.../<Shop>.java` | (optional) add to shop inventory — recompile plugins |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | **Always**, for any new item id. Append `items.add(new ItemDef(...))` at the end of `loadItemDefinitions()` so the client-side list count stays aligned with the server's. The client sends `EntityHandler.itemCount() - 1` as `maxItemId` at login (`mudclient.java:15111`); if the server has an item id > that value, `Inventory.add()` rejects it with "Your client could not receive ..." (`Inventory.java:115`). Recompile `Client_Base` afterwards. |

## Steps

1. **Pick an ID** that is **next sequential after the highest existing item ID** across both `ItemDefs.json` (authentic, ends at 1289) and `ItemDefsCustom.json` (custom, ends wherever the last entry sits — currently 1602 after the Subscription card). Voidscape custom items continue the sequence; they are NOT placed at ≥ 10000. **Why**: `EntityHandler.getItemDef(int)` does `items.get(id)` against a positional `ArrayList` (`server/src/com/openrsc/server/external/EntityHandler.java:842-847`); the load order in JSON IS the position, so any gap means `::item <id>` returns null. Same constraint that the Edgar NPC entry in `docs/DIVERGENCE.md` flagged for NPCs.
2. **Append the def** to `ItemDefsCustom.json`. Required fields: `id`, `name`, `description`, `command`, `isFemaleOnly`, `isMembersOnly`, `isStackable`, `isUntradable`, `isWearable`, `appearanceID`, `wearSlot`, `requiredLevel`, `requiredSkillID`, `defaultPrice`, plus `bonuses` for wearable items. Mirror an existing similar item's shape.
3. **Sprite/appearance**: `appearanceID` references a sprite slot the client already knows. If you need a new sprite, that's a client cache change — see `docs/subsystems/client-cache.md`. (For authenticity, prefer reusing an existing sprite.)
4. **Spawn it (optional)**:
   - Drop on death from an NPC: edit `NpcDrops.java` (Java — recompile required).
   - Place on the ground in the world: add to `server/conf/server/defs/locs/GroundItems.json` (or a custom variant).
   - Sell from a shop: edit the relevant `server/plugins/.../npcs/<Shop>.java` (recompile plugins).
5. **Restart the server.** Static defs are not hot-reloaded.
6. **Test in client**: examine the item, check appearance, drop it, pick it back up, equip if wearable.

## Verification checklist

- [ ] Item def parses (no JSON errors at boot).
- [ ] Item appears with correct name/description in client examine.
- [ ] Wearable: equips correctly, bonuses match expectation.
- [ ] Stackable: stacks correctly in inventory and bank.
- [ ] Tradable/untradable: trade dialog respects the flag.
- [ ] Members-only: F2P worlds block the item if `member_world: false`.
- [ ] New custom item ID is the next sequential after the previous highest (no gaps — `EntityHandler` indexes `items.get(id)` positionally).

## Common pitfalls

- **Appending mid-array breaks JSON.** Watch your commas.
- **Bonuses field is required for wearables.** Defaults won't auto-fill.
- **Plugin compile is separate.** Editing a shop file requires `ant compile_plugins`, not just `ant compile_core`.
- **Forgot to restart** — static defs never hot-reload.
