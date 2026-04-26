# Recipe: add an NPC with a custom right-click action

This is the playbook for adding an NPC whose **right-click menu has a custom option** (e.g. "Teleport to Edgeville", "Heal me", "Donate", "Withdraw") that runs server-side logic. It complements `add-npc.md` and corrects two gaps found in that recipe (see the bottom of this file).

Reference implementations in the tree:
- `server/plugins/com/openrsc/server/plugins/custom/npcs/EdgevilleTeleportWizard.java` — Edgar (Lumbridge → Edgeville teleport). Minimal, ~25 lines.
- `server/plugins/com/openrsc/server/plugins/custom/npcs/Gardener.java` — adds a "Trade" right-click that opens a shop. Has `OpNpcTrigger` + `TalkNpcTrigger` together.

---

## Mental model

RSC's NPC right-click menu is fixed: **Talk-to**, optional **command1**, optional **command2**, **Examine**. The two command slots are arbitrary strings stored on the NPC def. When the player clicks one, the client sends opcode 202 (`NPC_COMMAND`) or 203 (`NPC_COMMAND2`); the server resolves the string from the def and dispatches to `OpNpcTrigger.onOpNpc(Player, Npc, String command)`.

Wire format does **not** change. You only change data (NPC defs) and write a new plugin.

---

## Files you'll touch

| File | Why |
|---|---|
| `server/conf/server/defs/NpcDefsCustom.json` | New NPC entry. Set `command` to the menu label. |
| `server/conf/server/defs/locs/NpcLocs.json` | World spawn (coords + patrol bounds). |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | **Append** an `npcs.add(new NPCDef(...))` at the end of `loadNpcDefinitions4`. Required — the client doesn't fetch defs from the server; the def list is hardcoded and ID-aligned by `i++` insertion order. |
| `server/plugins/com/openrsc/server/plugins/custom/npcs/<YourNpc>.java` | New plugin implementing `OpNpcTrigger`. |
| `docs/DIVERGENCE.md` | One paragraph per CLAUDE.md rule 6. |

---

## Steps

### 1. Pick an NPC ID

`grep -E '"id"' server/conf/server/defs/NpcDefsCustom.json | tail -1` — your new ID is **(highest + 1)**. As of 2026-04-25, the highest custom ID is 835 ("Ash"), so the next is 836.

The "≥ 10000" advice in `add-npc.md` does not match practice. The client list uses sequential `i++`-assigned IDs and would need ~9000 placeholder rows to support a 10000-jump. Stick to next sequential.

### 2. Append the server NPC def

In `server/conf/server/defs/NpcDefsCustom.json`, append before the closing `]\n}`. **Set `"command"` to the right-click label** the player will see; leave `"command2"` empty unless you want a second option.

```json
,
{
  "id": 836,
  "name": "Edgar",
  "description": "An old wizard",
  "command": "Teleport to Edgeville",
  "command2": "",
  "attack": 0, "strength": 0, "hits": 5, "defense": 0,
  "ranged": false, "combatlvl": 0,
  "isMembers": 0, "attackable": 0, "aggressive": 0,
  "respawnTime": 30,
  "sprites1": 0, "sprites2": 1, "sprites3": 2,
  "sprites4": -1, "sprites5": -1,
  "sprites6": 77, "sprites7": 76, "sprites8": 81,
  "sprites9": -1, "sprites10": -1, "sprites11": -1, "sprites12": -1,
  "hairColour": 16777215, "topColour": 255, "bottomColour": 255, "skinColour": 15523536,
  "camera1": 145, "camera2": 220,
  "walkModel": 6, "combatModel": 6, "combatSprite": 5,
  "canEdit": 0, "roundMode": 0
}
```

Sprite array (12 slots): `head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet, cape`. The values above are the classic blue-robed wizard. Mirror an existing NPC's sprites/colors when in doubt — find one in `loadNpcDefinitions{1,2,3,4}` of `EntityHandler.java` and copy the integers.

### 3. Append the spawn

In `server/conf/server/defs/locs/NpcLocs.json` (uses tab indentation):

```json
, {
    "id": 836,
    "start": { "X": 124, "Y": 640 },
    "min":   { "X": 124, "Y": 640 },
    "max":   { "X": 124, "Y": 640 }
}
```

For a static NPC, `min == max == start`. Lumbridge bounds: X 108-147, Y 620-670 (`Point.java:292-294`). Region helpers for other cities live in the same file.

### 4. Append the client def — **don't skip this**

In `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`, find the **end** of `loadNpcDefinitions4` (last `npcs.add` call before the `if (Config.S_WANT_CUSTOM_SPRITES) {` block). Append:

```java
sprites = new int[]{0, 1, 2, -1, -1, 77, 76, 81, -1, -1, -1, -1};
npcs.add(new NPCDef("Edgar", "An old wizard", "Teleport to Edgeville", 0, 0, 5, 0, false, sprites, 16777215, 255, 255, 15523536, 145, 220, 6, 6, 5, i++));
```

The `i++` produces the next sequential ID (836 here). The third constructor arg is `command1` — same string you put in `"command"` above. **The integers must match the server JSON exactly** for sprites, colours, and stats.

Constructor signature: `NPCDef(name, description, command1, attack, strength, hits, defense, attackable, sprites, hairColour, topColour, bottomColour, skinColour, camera1, camera2, walkModel, combatModel, combatSprite, id)`.

### 5. Write the plugin

In `server/plugins/com/openrsc/server/plugins/custom/npcs/<YourNpc>.java`:

```java
package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;

public final class EdgevilleTeleportWizard implements OpNpcTrigger {

    private static final int EDGAR_NPC_ID = 836;
    private static final String COMMAND = "Teleport to Edgeville";

    @Override
    public void onOpNpc(Player player, Npc npc, String command) {
        if (npc.getID() != EDGAR_NPC_ID) return;
        if (!command.equalsIgnoreCase(COMMAND)) return;
        player.teleport(217, 449, true);
    }

    @Override
    public boolean blockOpNpc(Player player, Npc npc, String command) {
        return npc.getID() == EDGAR_NPC_ID && command.equalsIgnoreCase(COMMAND);
    }
}
```

Key points:
- `OpNpcTrigger.onOpNpc` fires for **every NPC right-click command** in the world. **Always ID-guard first.**
- `blockOpNpc` returning `true` claims the action so other plugins don't double-fire. Recommended for any custom command.
- The `command` arg arrives lowercased by the server (`NpcCommand.java:33` calls `.toLowerCase()`); compare with `equalsIgnoreCase`.
- `player.teleport(x, y, true)` clears combat automatically and shows the standard bubble GFX. Returns void — fire-and-forget.

For non-teleport actions, swap the body: open a shop (`ActionSender.showShop`), grant XP (`incExp`), give an item (`give`), etc. See `Functions.java` for the helper surface.

### 6. Build + restart

```bash
scripts/build.sh        # compiles core, plugins, Client_Base
scripts/run-server.sh   # in one terminal
scripts/run-client.sh   # in another
```

No hot-reload — every change to JSON, plugins, or client EntityHandler requires a server restart and a client relaunch.

### 7. Verify in-game

- NPC appears at the spawn coords.
- Right-click menu includes the custom option.
- Clicking the option fires the plugin (server log: `Tick N : <PluginName>.onOpNpc : [Player, Npc, command]`).
- The action runs (teleport lands, shop opens, etc.).

### 8. Document in `docs/DIVERGENCE.md`

One paragraph per CLAUDE.md rule 6. Cover what was added, files touched, reversibility.

---

## Common pitfalls

- **Skipping the client def** → NPC ID 836 sent over the wire resolves to nothing on the client; the NPC renders as a blank tile or doesn't render at all.
- **Off-by-one between server JSON `"id"` and client `i++`** → wrong sprites/name appear, or worse, the def goes to a different NPC ID. Always append at the *current end* of both lists in the same commit; never insert in the middle.
- **Forgetting the ID guard** in `onOpNpc` → your handler runs for every right-click on every NPC, breaking other content.
- **Mistaking `block*` semantics** → `blockOpNpc` returning `true` *claims* (cancels further chain). Returning `false` lets other plugins handle. Easy to invert.
- **Static def changes need restart** → `local.conf` is gitignored; JSON is loaded at boot. No hot-reload.
- **`docs/recipes/add-npc.md` is incomplete** as of 2026-04-25:
  - It doesn't mention the `Client_Base/EntityHandler.java` step.
  - Its "voidscape originals: ≥ 10000" advice doesn't match the working convention (sequential next-id, due to client `i++` alignment).

---

## Where the wire flow lives, for reference

- `Client_Base/src/orsc/mudclient.java:7981-7989` — client menu generation; reads `NPCDef.getCommand1()` and `getCommand2()`.
- `Client_Base/src/orsc/mudclient.java:13372` — sends opcode 202 (`NPC_COMMAND`) on click.
- `server/src/com/openrsc/server/net/rsc/handlers/NpcCommand.java` — server-side opcode handler; resolves `def.getCommand1()`, lowercases it, dispatches to `OpNpcTrigger`.
- `server/src/com/openrsc/server/plugins/triggers/OpNpcTrigger.java` — the trigger interface (two methods: `onOpNpc`, `blockOpNpc`).
- `server/src/com/openrsc/server/plugins/Functions.java:537` — `teleport(Player, int, int)` static helper, if you prefer it over `player.teleport(...)`.
