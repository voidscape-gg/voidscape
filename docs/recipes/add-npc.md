# Recipe: add an NPC

Adds a new NPC. Authentic in `NpcDefs.json`; voidscape-original in `NpcDefsCustom.json`.

## Files you'll touch

| File | Why |
|---|---|
| `server/conf/server/defs/NpcDefsCustom.json` | NPC stats, name, combat level, skills, bonuses |
| `server/conf/server/defs/locs/NpcLocs.json` | Spawn location + patrol bounds |
| `server/src/com/openrsc/server/constants/NpcDrops.java` | (optional) drop table — recompile |
| `server/plugins/.../npcs/<Yourself>.java` | (optional) custom dialogue / behavior — recompile plugins |

## Steps

1. **Pick an NPC ID** above the highest in `NpcDefsCustom.json`. Voidscape originals: ≥ 10000 to avoid collision with future authentic NPCs.
2. **Append the def** to `NpcDefsCustom.json`. Required fields: `id`, `name`, `description`, `examine`, `maxHealth`, `combat` (combat level), `skills` (array — atk/str/def/etc.), `bonuses`, `attackable`, `aggressive`, optional `spells`, optional `drops` reference.
3. **Spawn the NPC** by adding to `server/conf/server/defs/locs/NpcLocs.json`:
   ```json
   { "id": <npcId>, "startX": <x>, "minX": <x>, "maxX": <x+5>, "startY": <y>, "minY": <y>, "maxY": <y+5> }
   ```
   `(min/max)X/Y` define a patrol box; equal to `start*` for a static NPC.
4. **Drops (optional)**: in `NpcDrops.java`, add a `DropTable` for the new ID inside `createMobDrops()` (or create a new factory method). Java — recompile required.
5. **Custom dialogue/behavior (optional)**:
   - New plugin in `server/plugins/com/openrsc/server/plugins/{authentic|custom}/npcs/<YourNpc>.java`.
   - Implement `TalkNpcTrigger`, `OpNpcTrigger`, etc.
   - In each, narrow to your NPC by ID: `npc.getID() == <npcId>` then guard.
   - Recompile plugins (`ant compile_plugins`).
6. **Restart the server**.
7. **Test**: walk to coordinates, NPC should be present. Talk, attack (if attackable), check combat level + drops on kill.

## Verification checklist

- [ ] NPC appears at coords on server boot.
- [ ] Examine + name display correctly.
- [ ] Combat level matches `combat` field.
- [ ] Patrol bounds work — NPC walks within (minX, minY) → (maxX, maxY).
- [ ] Aggressive flag respected (or not, in safe zones).
- [ ] Drops table fires on death (kill 50+ to validate).
- [ ] Voidscape-original NPCs have IDs ≥ 10000.

## Common pitfalls

- **Drops are hardcoded** in `NpcDrops.java`. Forgetting to recompile = NPC drops nothing.
- **Don't reuse `OpNpcTrigger.onOpNpc` without ID-guarding.** It fires for every NPC interaction; check `npc.getID() == ...` first.
- **Patrol bounds must contain the spawn point.** If `(minX, minY) > (startX, startY)`, the NPC may glitch.
- **Aggression radius is partly hardcoded.** If you want non-default ranges, that's a code change in `NpcBehavior`. Document in `DIVERGENCE.md`.
