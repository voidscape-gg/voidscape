# Rare drop beams

Voidscape rare drop beams are a custom-client ground-item visual. The server still sends the same one-byte beam flag added in client version `10030`; this subsystem changes which items deserve that flag for each player.

## Entry points

- `server/src/com/openrsc/server/content/LootBeamSettings.java` owns the default beam item list and player cache preferences.
- `server/src/com/openrsc/server/constants/NpcDrops.java` exposes the default beam list to legacy callers through `isRareDropItem`.
- `server/src/com/openrsc/server/model/entity/npc/Npc.java` still tags NPC drop ground items that came from rare tables or default beam items.
- `server/plugins/com/openrsc/server/plugins/shared/DropObject.java` tags player-dropped unnoted, non-stackable default beam items, which keeps manual visual testing simple.
- `server/src/com/openrsc/server/GameStateUpdater.java` applies `LootBeamSettings.shouldShowBeam(player, groundItem)` when building each player's ground-item update.
- `Client_Base/src/orsc/PacketHandler.java` reads the rare-beam byte for client versions `10030+`.
- `Client_Base/src/orsc/mudclient.java` stores the flag per visible ground item and renders the procedural purple beam.

## Default policy

The old rule treated any item from a `DropTable` marked `rare` as beam-worthy. That made common rolls from rare tables, such as ordinary gems or bulk currency/runes/certs, glow too often.

The current rule is an explicit default list in `LootBeamSettings`. It includes chase drops and rare cosmetics such as dragon equipment/components, shield halves, high-signal rune drops, Dragonstone/key-half drops, Void Key, KBD uniques, crackers, and party hats.

If a future drop should beam by default, add the item ID to `buildDefaultBeamItems()` and document the reason in `docs/DIVERGENCE.md`.

## Player customization

Players can customize beams through regular commands:

- `::lootbeam list`
- `::lootbeam defaults`
- `::lootbeam add <item id/name>`
- `::lootbeam remove <item id/name>`
- `::lootbeam mode default|custom`
- `::lootbeam reset`

Cache keys:

- `lootbeam_mode` is `default` or `custom`.
- `lb_add_<itemId>` explicitly adds an item for that player.
- `lb_hide_<itemId>` suppresses a default-list item for that player.

Default mode shows the default list plus explicit adds minus hidden defaults. Custom mode shows only explicit adds.

Each mutating `::lootbeam` command flushes the player cache immediately through `PlayerService.savePlayerCache(...)`. Normal autosave/logout still saves the same keys, but the explicit flush keeps quick logout/reconnects from dropping recent beam preferences.

## Client toggle

The existing Advanced Settings `Loot beams` row remains the client-side master toggle. It stores `setting_rare_drop_beams` through game-setting byte `48`; the renderer checks that local flag before drawing. The per-item list is server-side so each player can receive a different beam byte for the same ground item without changing the packet shape.
