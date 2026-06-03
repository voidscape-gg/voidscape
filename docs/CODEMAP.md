# Code map

Directory-by-directory index. Use this when looking for **where** something lives — for **how** something works, see `docs/ARCHITECTURE.md` and the relevant `docs/subsystems/*.md`.

## Top-level layout

```
voidscape/
├── CLAUDE.md                       # Root brief — always loaded
├── README.md                       # OpenRSC's README (will be replaced as voidscape diverges)
├── LICENSE                         # AGPLv3 (inherited from OpenRSC)
├── CONTRIBUTING.md                 # Upstream contributor notes (still applies for plugin patterns)
├── SECURITY.md                     # Security disclosure guidance from upstream
├── Commands.md                     # Reference for in-game admin/player commands
│
├── docs/                           # Voidscape architecture / recipes / glossary
├── scripts/                        # Voidscape canonical entry points (build, run, reset)
├── memory/                         # Voidscape persistent memory (loaded each session)
├── .agents/                        # Codex skills / workflow scaffolding
├── .claude/                        # Claude Code config + custom subagents
├── upstream/                       # Frozen reference copy of OpenRSC at vendor SHA
│   └── openrsc-snapshot/           #   gitignored — recreate via scripts/fetch-upstream-snapshot.sh
│
├── server/                         # OpenRSC game server (Java, Ant)
├── PC_Client/                      # Desktop client (Java, Ant)
├── Client_Base/                    # Shared client core (PC + Android use this)
├── Android_Client/                 # Android client (Java, Gradle)
├── PC_Launcher/                    # Desktop launcher / auto-updater (Java, Ant)
├── Deployment_Scripts/             # Ops scripts
├── Backups/                        # Empty placeholder for SQL dumps
├── Portable_Windows/               # Windows portable build assets (HeidiSQL, etc.)
│
├── Makefile                        # Top-level orchestration (calls Ant targets)
├── docker-compose.yml              # MariaDB-only docker stack (server + clients run on host)
├── .env                            # Default scaffold creds (root/root) for docker-compose
├── .editorconfig                   # Style: 4-space spaces, tabs for Java/PHP
├── .gitignore                      # Upstream rules + voidscape additions at the bottom
├── .gitlab-ci.yml                  # Upstream CI (we don't use GitLab; ignore)
│
├── Start-Linux.sh                  # Upstream Linux quickstart (alternative to scripts/run-server.sh)
├── Start-Windows.cmd               # Upstream Windows quickstart
├── *.md                            # Per-OS getting-started guides (Linux, MacOS, Windows, Pi)
```

## `server/` — game server

```
server/
├── build.xml                       # Authoritative Ant build (compile_core, compile_plugins, runserver)
├── build.gradle                    # IDE-only Gradle layer; imports build.xml
├── gradlew, gradlew.bat            # Gradle wrapper (rarely used)
│
├── src/com/openrsc/server/         # Server core
│   ├── Server.java                 # Entry point — main(); boot sequence
│   ├── ServerConfiguration.java    # Loads .conf, exposes config keys
│   │
│   ├── model/world/                # World, Region, RegionManager, WorldLoader, WorldPopulator
│   │   ├── World.java              # Top-level singleton: players, npcs, regions, …
│   │   └── region/                 # 8×8 tile regions for AOI/visibility
│   │
│   ├── model/entity/               # Entity hierarchy
│   │   ├── Entity.java             # base
│   │   ├── Mob.java                # combat/movement state
│   │   ├── player/Player.java      # player + processTick, processLogout, sendUpdates
│   │   └── npc/                    # Npc.java + NpcBehavior.java (state machine)
│   │
│   ├── event/                      # Scheduler + event system
│   │   ├── DelayedEvent.java       # ms-delay convenience wrapper
│   │   └── rsc/
│   │       ├── GameTickEvent.java  # base class (also FinitePeriodicEvent, SingleTickEvent, …)
│   │       ├── handler/GameEventHandler.java  # event registry + dispatch
│   │       └── impl/
│   │           ├── PoisonEvent.java
│   │           ├── PrayerDrainEvent.java
│   │           └── combat/
│   │               ├── CombatEvent.java        # turn-based combat tick
│   │               ├── CombatFormula.java      # damage / accuracy math
│   │               └── scripts/all/            # NPC special-attack scripts (loaded by reflection)
│   │
│   ├── net/                        # Netty pipeline + packet handling
│   │   ├── Connection*Handler.java # channel lifecycle
│   │   ├── PacketBuilder.java      # outbound packet construction
│   │   ├── ActionSender.java       # high-level outbound dispatch
│   │   └── rsc/
│   │       ├── enums/OpcodeIn.java, OpcodeOut.java   # opcode enums (server side)
│   │       ├── handlers/*.java     # per-opcode inbound handlers (AttackHandler, ChatHandler, …)
│   │       ├── generators/impl/Payload*Generator.java   # version-specific outbound encoders
│   │       └── parsers/Payload*Parser.java     # version-specific inbound parsers
│   │
│   ├── login/                      # LoginExecutor, LoginRequest, PlayerSaveRequest
│   ├── plugins/                    # Plugin runtime infra (NOT plugin content — that's server/plugins/)
│   │   ├── handler/PluginHandler.java   # load + dispatch
│   │   ├── io/PluginJarLoader.java
│   │   └── triggers/*.java          # 24+ trigger interfaces (TalkNpcTrigger, OpLocTrigger, …)
│   │
│   ├── service/PlayerService.java  # save / load orchestration
│   │
│   ├── database/                   # JDBC layer
│   │   ├── GameDatabase.java       # abstract base
│   │   ├── JDBCDatabase.java       # JDBC wrapper
│   │   ├── JDBCDatabaseConnection.java   # connection abstraction
│   │   ├── MySQLDatabaseConnection.java  # MySQL impl
│   │   ├── SqliteGameDatabaseConnection.java  # SQLite impl
│   │   ├── MySqlGameDatabase.java
│   │   ├── SqliteGameDatabase.java
│   │   ├── DatabaseType.java       # SQLite vs MySQL selector
│   │   └── JDBCPatchApplier.java   # migration runner
│   │
│   ├── constants/
│   │   ├── Skills.java             # skill IDs, mode constants
│   │   ├── NpcDrops.java           # NPC drop tables — HARDCODED here, no JSON
│   │   ├── Constants.java          # REGION_SIZE, etc.
│   │   └── …
│   │
│   ├── external/EntityHandler.java # singleton catalog of all defs (items, NPCs, skills, …)
│   │
│   ├── content/                    # shared content systems such as LootBeamSettings
│   │   ├── PlayerTitle.java        # Voidscape player-title catalog, unlock checks, active title cache
│   │
│   └── …                           # (login, util, services, …)
│
├── plugins/com/openrsc/server/plugins/   # Content code (compiled separately into plugins.jar)
│   ├── authentic/                  # vanilla-RSC content
│   │   ├── npcs/                   # NPC dialogue / shop definitions
│   │   ├── skills/                 # per-skill action handlers (cooking/, fishing/, mining/, …)
│   │   ├── quests/                 # quest implementations (free/, members/)
│   │   └── minigames/
│   ├── custom/                     # OpenRSC additions / non-authentic content
│   ├── RuneScript.java             # static dialogue / delay helpers (legacy name)
│   ├── Functions.java              # quest / NPC / item utilities
│   └── QuestInterface.java         # quest contract
│
├── conf/                           # Static data + non-preset config
│   └── server/
│       ├── defs/                   # game data (loaded by EntityHandler at boot)
│       │   ├── ItemDefs.json, ItemDefsCustom.json, ItemDefsPatch18.json
│       │   ├── NpcDefs.json, NpcDefsCustom.json, NpcDefsPatch18.json
│       │   ├── SpellDef.xml, PrayerDef.xml, TileDef.xml, DoorDef.xml
│       │   ├── GameObjectDef.xml
│       │   ├── extras/             # per-skill defs (ItemCookingDef.xml, ObjectFishing.xml, …)
│       │   └── locs/               # spawn locations
│       │       ├── SceneryLocs.json (+ map variants + custom variants)
│       │       ├── NpcLocs.json    (+ variants)
│       │       └── GroundItems.json (+ variants)
│       └── data/maps/              # binary terrain (.orsc, .osar)
│
├── database/                       # SQL schema + migrations
│   ├── mysql/
│   │   ├── core.sql                # initial schema
│   │   ├── patches/YYYY_MM_DD_*.sql   # cumulative patches
│   │   ├── upgrades/N_*_VERSION.sql   # versioned schema upgrades
│   │   └── queries/*.xml           # parameterized SQL templates
│   └── sqlite/
│       ├── core.sqlite             # SQLite-format schema
│       └── patches/, queries/      # SQLite equivalents
│
├── *.conf                          # Server presets (preservation, default, 2001scape, rsccabbage, openpk, uranium, rsccoleslaw, connections, default)
├── globalrules.txt
│
├── inc/                            # Runtime helper files (sqlite/ DB files, my.cnf, ant resources)
│   ├── sqlite/                     # SQLite DBs land here (gitignored runtime files)
│   ├── ant/                        # Ant resources
│   └── databases/                  # Docker MariaDB volume target (gitignored)
│
├── lib/                            # Bundled JARs (Netty, JDBC drivers, Discord SDK, log4j, …)
│
├── avatars/                        # Per-player appearance data — currently unused; .gitsave only
├── logs/                           # Server logs (gitignored)
├── compile_core.cmd, compile_core.sh
├── compile_plugins.cmd, compile_plugins.sh
├── ant_launcher.sh
└── run_server.sh                   # Upstream's run script (we use scripts/run-server.sh)
```

## `Client_Base/` — shared client core

```
Client_Base/
├── build.xml                       # Ant build → Open_RSC_Client.jar
└── src/
    ├── orsc/
    │   ├── Config.java             # CLIENT_VERSION, SERVER_IP/PORT, feature flags
    │   ├── mudclient.java          # main game loop (rendering, networking, entities)
    │   ├── net/
    │   │   ├── Network_Base.java, Network_Socket.java
    │   │   └── Opcodes.java        # CLIENT-SIDE opcode enum — must stay in sync with server
    │   ├── graphics/
    │   │   ├── three/              # 3D rendering
    │   │   ├── two/SpriteArchive/  # sprite handling
    │   │   └── gui/Panel.java, Menu.java
    │   ├── enumerations/           # game data enums
    │   ├── buffers/RSBuffer*.java  # packet serialization
    │   └── multiclient/ClientPort.java   # platform abstraction
    ├── com/openrsc/                # entity defs (ItemDef, NPCDef, SpellDef, AnimationDef)
    └── res/                        # icon resources
```

## `PC_Client/`, `Android_Client/`, `PC_Launcher/`

```
PC_Client/
├── build.xml                       # Ant; Java 1.8 target
├── src/orsc/
│   ├── OpenRSC.java                # main()
│   ├── WorkbenchServer.java        # Dev-only loopback screenshot/state API
│   └── ScaledWindow.java, Discord.java   # PC-specific UI
└── lib/discord-rpc.jar

Android_Client/
├── Open RSC Android Client/build.gradle  # AGP 7.3.0, minSDK 23, target/compile SDK 31
└── src/main/java/com/openrsc/client/android/
    ├── GameActivity.java           # ClientPort impl; main entry
    ├── render/RSCBitmapSurfaceView.java, InputImpl.java
    └── updater/CacheUpdater.java

PC_Launcher/
├── build.xml                       # Ant
└── src/main/java/launcher/
    ├── Main.java
    ├── Fancy/MainWindow.java       # themed launcher UI
    ├── Gameupdater/                # auto-updater + MD5 verification
    └── Utils/                      # WorldPopulations, Defaults (version stamp)
```

## `Deployment_Scripts/`, `Backups/`, `Portable_Windows/`

```
Deployment_Scripts/
├── (operational scripts; consult before relying)
└── unmaintained/Makefile           # OLD prod scripts — ignore

Backups/                            # Empty placeholder; manual SQL dumps go here

Portable_Windows/                   # Windows portable bundle (HeidiSQL, scripts)
```

## `docs/`, `scripts/`, `memory/`, `.agents/`, `.claude/` — voidscape additions

```
docs/
├── ARCHITECTURE.md                 # System-level synthesis
├── CODEMAP.md                      # This file
├── DEVELOPMENT.md                  # Build / run / reset on macOS
├── DIVERGENCE.md                   # Vendor SHA + voidscape change log
├── CONFIG-MATRIX.md                # Intended dev/staging/prod config values
├── GLOSSARY.md                     # RSC + OpenRSC terminology
├── OPERATIONS.md                   # Runbook: deploy, backups, logs, rollback
├── RELEASE-CHECKLIST.md            # Pre-release verification checklist
├── SERVER-PRESETS.md               # Each .conf's role + voidscape recommendation
├── recipes/                        # Step-by-step playbooks
│   ├── add-item.md
│   ├── add-npc.md
│   ├── add-quest.md
│   ├── add-skill-action.md
│   ├── tweak-combat-formula.md
│   └── add-admin-command.md
└── subsystems/                     # Deep dives, one per major subsystem
    ├── ai-workbench.md
    ├── client-cache.md
    ├── combat-system.md
    ├── dynamic-wilderness-spawns.md
    ├── networking-protocol.md
    ├── persistence-db.md
    ├── player-titles.md
    ├── rare-drop-beams.md
    ├── scripting-plugins.md
    ├── world-announcements.md
    └── world-tick-loop.md

scripts/
├── build.sh                        # Compile server + plugins + client
├── run-server.sh                   # Run server with voidscape's local.conf
├── run-client.sh                   # Run PC client
├── run-workbench-client.sh         # Run PC client with local AI workbench endpoints
├── reset-db.sh                     # Wipe + reseed dev DB
└── fetch-upstream-snapshot.sh      # Recreate upstream/openrsc-snapshot/

memory/
├── MEMORY.md                       # Index — always loaded
├── project_voidscape.md
├── reference_openrsc_upstream.md
├── reference_layout.md
└── feedback_preferences.md

.agents/
└── skills/
    └── feature/SKILL.md            # Discovery-first feature workflow

.claude/
├── settings.json                   # Project-level Claude Code config (tracked)
├── settings.local.json             # Per-user overrides (gitignored)
└── agents/                         # Custom subagent definitions (if any)
```

## Quick "where do I edit X" cheat sheet

| Want to change … | Edit … |
|---|---|
| Item stats / name / price | `server/conf/server/defs/ItemDefs.json` (or Custom/Patch18) |
| NPC stats / combat / skills | `server/conf/server/defs/NpcDefs.json` |
| Where an NPC spawns | `server/conf/server/defs/locs/NpcLocs.json` |
| Where scenery sits | `server/conf/server/defs/locs/SceneryLocs.json` |
| What an NPC drops | `server/src/com/openrsc/server/constants/NpcDrops.java` (Java!) |
| What a shop sells | `server/plugins/.../npcs/<Shop>.java` (Java!) |
| Quest logic | `server/plugins/authentic/quests/<group>/*.java` |
| Skill action logic | `server/plugins/authentic/skills/<skill>/*.java` |
| Damage formula | `server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java` |
| Game tick / exp rate / world params | `server/local.conf` |
| DB engine + creds | `server/connections.conf` |
| Server boot order | `server/src/com/openrsc/server/Server.java` |
| Add an admin command | (see `docs/recipes/add-admin-command.md`) |
| Add an opcode | `server/src/com/openrsc/server/net/rsc/enums/Opcode{In,Out}.java` **and** `Client_Base/src/orsc/net/Opcodes.java` (append only — never insert mid-list) |
