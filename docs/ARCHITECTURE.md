# Architecture

System-level view of voidscape (and OpenRSC underneath): processes, data flow, lifecycle. For per-subsystem deep dives, see `docs/subsystems/`. For directory layout, see `docs/CODEMAP.md`.

## The processes

```
┌──────────────────┐        ┌──────────────────┐
│   PC_Client      │        │   Android_Client │
│   (Java, Swing)  │        │   (Java, Android)│
│                  │        │                  │
│   uses Client_Base shared core               │
│   reads Cache/ip.txt + Cache/port.txt        │
└────────┬─────────┘        └────────┬─────────┘
         │                           │
         │  TCP (default 43594)      │  TCP / WebSocket
         │  RSCMultiPortDecoder auto-detects
         ▼                           ▼
┌─────────────────────────────────────────────┐
│              Game Server (JVM)               │
│                                              │
│  Netty event loop ──► Connection handler     │
│         │                  │                 │
│         │                  ▼                 │
│         │            Login/auth (RSA+ISAAC)  │
│         │                  │                 │
│         │                  ▼                 │
│         │            World registration      │
│         │                  │                 │
│         ▼                  ▼                 │
│  Per-player packet queue ──► Game tick (640ms)
│                                  │           │
│                                  ▼           │
│                         GameStateUpdater     │
│                                  │           │
│              ┌───────────────────┼─────────┐ │
│              ▼          ▼         ▼        ▼ │
│         Events     World update  NPCs   Players
│         (timers)                         │   │
│                                          ▼   │
│                                   Outbound packets
│                                          │   │
└──────────────────────────────────────────┼───┘
                                           │
                                           ▼
              ┌─────────────────┐    ┌──────────────┐
              │  GameDatabase    │    │   Plugins.jar │
              │  (SQLite/MySQL)  │    │ (content code)│
              │                  │    │               │
              │  via LoginExecutor    │  Loaded via   │
              │  async thread          │  PluginHandler│
              └─────────────────┘    └──────────────┘
```

## Boot sequence (server)

1. `Server.main()` — `server/src/com/openrsc/server/Server.java:193`
2. Load config from `local.conf` (or whichever `-DconfFile=` selects).
3. Construct singletons: `RSCPacketFilter`, `PluginHandler`, `CombatScriptLoader`, `Constants`, `GameDatabase`, `World`, `GameEventHandler`, `GameStateUpdater`, `LoginExecutor`. Spin up SQL/log/online-monitor thread pools.
4. Open DB. Run `JDBCPatchApplier` migrations.
5. Load assets:
   - Captchas
   - Item/NPC/scenery defs (`EntityHandler.load()`)
   - World (`World.load()`)
   - Plugins from `plugins.jar` (`PluginHandler.load()`)
   - Combat scripts (`CombatScriptLoader.load()`)
6. Start services: login executor, Discord bridge, game logger, pcap logger.
7. Bind Netty (TCP + optional WS).
8. Schedule the game thread — `ScheduledExecutorService` ticking every 10 ms.

## Connect → login → in-world

```
Client TCP connect
  │
  ▼
Netty channel accepted
  │
  ▼
RSCMultiPortDecoder ─ TCP or WebSocket detection
  │
  ▼ pipeline reconfigured for chosen protocol
RSCConnectionHandler.channelActive()
  │
  ▼ reads version + login bytes
LoginPacketHandler.processLogin()
  │  RSA decrypt login block (≥v205)
  │  XTEA decrypt username block
  │  ISAAC seed (both directions)
  │
  ▼
LoginExecutor (async thread)
  │  DB lookup, credential check, load skills/inv/quests
  │
  ▼
World.registerPlayer(player)
  │  PlayerList add, Region update, social alerts
  │
  ▼
ActionSender.sendWelcomeInfo() + initial state packets
  │
  ▼
Player in game tick rotation
```

Authoritative refs: `docs/subsystems/networking-protocol.md` (handshake, encryption), `docs/subsystems/world-tick-loop.md` (registration, lifecycle).

## The 640ms tick

Single-threaded scheduler fires every 10 ms; an actual tick fires when accumulated time ≥ `GAME_TICK` ns (default 640 ms).

```
Server.run() — per tick:

1. Process non-player events (timers, scheduled actions)
2. Update world state (GameStateUpdater.updateWorld)
3. Process NPCs (movement, AI, combat)
4. Per-player, in PID order:
     a. processTick()  — drain inbound packet queue, walk, events, message queue
     b. processLogout() — handle deferred logout flag
     c. sendUpdates()  — flush outbound state to client
5. executePidlessCatching (late processing)
6. Global message queue
7. Internet reachability heartbeat
8. Cleanup + housekeeping
9. Tick counter advances
```

Each phase is benchmarked. If the server falls behind, ticks may be skipped. PID order can be shuffled per-tick if `SHUFFLE_PID_ORDER` is on (causes subtle PvP timing variance).

## Player input → game state change

```
Client sends packet
  │
  ▼
Netty pipeline → RSCProtocolDecoder
  │  ISAAC-decrypts opcode (clients ≥183)
  │  Parses payload via version-specific Payload*Parser
  │
  ▼
Packet (opcode + payload)
  │
  ▼
RSCConnectionHandler.channelRead()
  │  Pre-login (opcode 0/2/4/8/19): LoginPacketHandler
  │  Post-login: player.addToPacketQueue(packet)
  │
  ▼ (next tick, in player's processTick)
GameStateUpdater dequeues
  │
  ▼
Handler dispatched (server/src/com/openrsc/server/net/rsc/handlers/*.java)
  │  Examples: AttackHandler, ChatHandler, BankHandler, NpcTalkTo, ItemDropHandler
  │
  ▼
Trigger plugin chain (PluginHandler.handlePlugin)
  │  Iterate Multimap<TriggerType, Plugin>
  │  Each plugin: blockX() then onX()
  │
  ▼
World state mutated, events enqueued, outbound packets prepared
```

Authoritative ref: `docs/subsystems/scripting-plugins.md`.

## Combat round flow

Combat is a `GameTickEvent` (`CombatEvent`) attached to both participants. Each tick advances the round by 1; alternating `roundNumber % 2` swaps attacker/defender. Tick cycle is 3-1 PvE / 2-2 duel / PID-determined for PvP.

```
CombatEvent.run() — per tick:
  ┌─ if attacker turn:
  │   - Apply combat scripts (CombatScript.executeScript)
  │   - Roll accuracy (CombatFormula)
  │   - On hit, compute damage, apply
  │   - Queue damage packets to both sides
  │
  └─ if defender turn:
      - swap role
```

Authoritative ref: `docs/subsystems/combat-system.md`.

## Persistence flow

Writes:
```
Trigger (autosave timer / logout)
  │
  ▼
new PlayerSaveRequest(server, player, blocking?) → LoginExecutor queue
  │
  ▼ (LoginExecutor thread, 50ms tick)
PlayerService.savePlayer(player)
  │
  ▼
database.atomically( () -> {
    savePlayerInventory()
    savePlayerEquipment()
    savePlayerBank()
    savePlayerSkills()
    savePlayerSocial()
    savePlayerData()
    savePlayerQuests()
    savePlayerBankPresets()
})
  │
  ▼
JDBC PreparedStatement → SQLite or MySQL
```

Reads (login):
```
LoginRequest → LoginExecutor → PlayerService.loadPlayer()
  → JDBC PreparedStatement reads players + skills + inv + bank + social + quests
  → Player constructed, returned to game thread for World.registerPlayer()
```

Authoritative ref: `docs/subsystems/persistence-db.md`.

## Disconnect

```
Player.unregister() (called on logout request, kick, or crash)
  │  sets UnregisterRequest flag (waits if in combat)
  │
  ▼ next tick, Player.processLogout()
World.unregisterPlayer(player)
  │  log offline, generate avatar (if enabled), social notifications
  │
  ▼
Channel close queued via DelayedEvent (2.5s)
  │  allows logout response packet to flush
  │
  ▼
Channel freed, player removed from PlayerList + Region
```

## Static data load

At boot, `EntityHandler.load()` reads:
- `server/conf/server/defs/ItemDefs.json` (+ Custom + Patch18 variants)
- `server/conf/server/defs/NpcDefs.json` (+ variants)
- `server/conf/server/defs/SpellDef.xml`, `PrayerDef.xml`, `TileDef.xml`, `DoorDef.xml`, `GameObjectDef.xml`
- Per-skill defs in `server/conf/server/defs/extras/` (cooking, smithing, fishing, mining, …)

`WorldPopulator.populateWorld()` then reads location JSONs (`server/conf/server/defs/locs/*.json`) and instantiates NPCs, scenery, ground items at coords.

NPC drop tables are **hardcoded** in `server/src/com/openrsc/server/constants/NpcDrops.java` — editing them requires recompile.

Shops are **defined in plugin code** (`server/plugins/.../npcs/*.java`) — editing requires plugin recompile.

Authoritative ref: `docs/subsystems/persistence-db.md` (definitive list of where to edit what).

## Plugins / content boot

```
Server boot → PluginHandler load
  │
  ▼
Read plugins.jar (compiled separately via compile_plugins target)
  │
  ▼ for each .class file:
   - Filter inner classes
   - Reflectively load
   - Discover trigger interfaces (TalkNpcTrigger, OpLocTrigger, …)
   - Instantiate via Guice
   - Register: triggerTypeToInstance.put(triggerType, instance)
  │
  ▼ at runtime, on a player action:
PluginHandler.handlePlugin(triggerType, ...)
  │
  ▼ iterate plugins for that trigger
For each: block? → on?
```

No hot-reload. Plugin changes require `compile_plugins` + server restart.

Authoritative ref: `docs/subsystems/scripting-plugins.md`.

## Threading summary

| Thread | Work |
|---|---|
| Game thread (single) | Tick loop — world updates, packet handling, sends |
| Event executor | Plugin/event runs (1 thread by default; multi if `WANT_THREADING__BREAK_PID_ORDER`) |
| LoginExecutor | Async login + save (50ms tick) |
| SQL pools | Logging (queries, chat, kills) |
| Netty I/O | Boss + worker; non-blocking channel I/O |

Game thread is the source of truth for world state. Everything else feeds into it via queues.

## Where things are configured

| Concern | File |
|---|---|
| Active preset (game tick, exp rate, fatigue, etc.) | `server/local.conf` |
| DB engine + creds | `server/connections.conf` |
| Item / NPC stats | `server/conf/server/defs/{ItemDefs,NpcDefs}.json` |
| Where stuff spawns | `server/conf/server/defs/locs/*.json` |
| Skill recipes (cookable items, smithable items, ore yields) | `server/conf/server/defs/extras/*.xml` |
| Spells | `server/conf/server/defs/SpellDef.xml` |
| Prayers | `server/conf/server/defs/PrayerDef.xml` |
| NPC drops | **`server/src/com/openrsc/server/constants/NpcDrops.java`** (Java, not data!) |
| Shops | **`server/plugins/.../npcs/*Shop*.java`** (Java, not data!) |
| Quest logic | **`server/plugins/.../quests/*.java`** |
| Skill action logic | **`server/plugins/.../skills/*/*.java`** |
| Map / terrain | `server/conf/server/data/maps/*.orsc` (binary) |

For a working change, you usually edit some combination of: a JSON def (item/npc), a spawn location JSON, and a plugin Java file. See `docs/recipes/` for step-by-step playbooks.
