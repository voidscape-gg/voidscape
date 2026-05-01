# Recipe: add an NPC

Adds a new NPC. Authentic in `NpcDefs.json`; voidscape-original in `NpcDefsCustom.json`.

## Files you'll touch

| File | Why |
|---|---|
| `server/conf/server/defs/NpcDefsCustom.json` | NPC stats, name, combat level, skills, bonuses |
| `server/conf/server/defs/locs/NpcLocs.json` | Spawn location + patrol bounds |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | Required for any client-visible NPC. The client NPC list is positional and hardcoded. |
| `Client_Base/src/orsc/Config.java` | Bump `CLIENT_VERSION` when adding a client-visible NPCDef. |
| `server/src/com/openrsc/server/constants/NpcDrops.java` | (optional) drop table — recompile |
| `server/plugins/.../npcs/<Yourself>.java` | (optional) custom dialogue / behavior — recompile plugins |

## Steps

1. **Pick the next sequential NPC ID**, not an arbitrary high number. Check the last custom `npcs.add(...)` in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` and the highest `id` in `NpcDefsCustom.json`; the new ID must be the next one in both places. Example: if the last custom NPC is `843`, the new NPC must be `844`.
2. **Append the def** to `NpcDefsCustom.json`. Required fields include `id`, `name`, `description`, `command`, `command2`, combat stats, `combatlvl`, membership/attack flags, `respawnTime`, twelve `spritesN` values, colours, camera values, walk/combat model values, `combatSprite`, `canEdit`, and `roundMode`.
3. **Spawn the NPC** by adding to `server/conf/server/defs/locs/NpcLocs.json`:
   ```json
   {
     "id": <npcId>,
     "start": { "X": <x>, "Y": <y> },
     "min": { "X": <x>, "Y": <y> },
     "max": { "X": <x>, "Y": <y> }
   }
   ```
   `(min/max)X/Y` define a patrol box; equal to `start*` for a static NPC.
4. **Append the matching client NPCDef** at the end of `loadNPCDefinitions4()` in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, before the `if (Config.S_WANT_CUSTOM_SPRITES)` block. This `npcs.add(...)` line must be in the same ordinal position as the server ID.
5. **Bump the client version** in `Client_Base/src/orsc/Config.java`. Update the local server `client_version` to match when testing.
6. **Drops (optional)**: in `NpcDrops.java`, add a `DropTable` for the new ID inside `createMobDrops()` (or create a new factory method). Java — recompile required.
7. **Custom dialogue/behavior (optional)**:
   - New plugin in `server/plugins/com/openrsc/server/plugins/{authentic|custom}/npcs/<YourNpc>.java`.
   - Implement `TalkNpcTrigger`, `OpNpcTrigger`, etc.
   - In each, narrow to your NPC by ID: `npc.getID() == <npcId>` then guard.
   - Recompile plugins (`ant compile_plugins`).
8. **Rebuild client and server artifacts**.
9. **Restart the server and client**.
10. **Test**: walk to coordinates, NPC should be present. Talk, attack (if attackable), check combat level + drops on kill.

## Verification checklist

- [ ] NPC appears at coords on server boot.
- [ ] Examine + name display correctly.
- [ ] Client does not show "Ana (not in a barrel)".
- [ ] Combat level matches `combat` field.
- [ ] Patrol bounds work — NPC walks within (minX, minY) → (maxX, maxY).
- [ ] Aggressive flag respected (or not, in safe zones).
- [ ] Drops table fires on death (kill 50+ to validate).
- [ ] Voidscape-original NPCs use the next sequential client/server ID.

## Common pitfalls

- **Drops are hardcoded** in `NpcDrops.java`. Forgetting to recompile = NPC drops nothing.
- **"Ana (not in a barrel)" means the client NPCDef list is missing or out of order.** Server-side JSON alone is not enough. The client does not look up NPCs by JSON id; it uses the hardcoded `npcs.add(...)` order and falls back to Ana for unknown/out-of-range IDs.
- **Do not skip IDs or pick IDs like `10000` for visible NPCs.** The server JSON has an `id` field, but the client assigns IDs with `i++` insertion order. Custom visible NPCs must be appended sequentially in both server and client definition lists.
- **Don't reuse `OpNpcTrigger.onOpNpc` without ID-guarding.** It fires for every NPC interaction; check `npc.getID() == ...` first.
- **Patrol bounds must contain the spawn point.** If `(minX, minY) > (startX, startY)`, the NPC may glitch.
- **Aggression radius is partly hardcoded.** If you want non-default ranges, that's a code change in `NpcBehavior`. Document in `DIVERGENCE.md`.
