# Glossary

RSC- and OpenRSC-specific terminology. Add a term whenever you find yourself thinking "wait, what does that mean here?". A future Claude session will thank you.

Terms are grouped roughly by domain. Keep entries one sentence; if more is needed, link to a subsystem doc.

## Game world / mechanics

- **RSC** — RuneScape Classic. The 2001-2018 era of RuneScape, original sprite-based 2.5D engine. Voidscape emulates this.
- **Tick** — the server's fixed-rate update unit. RSC ticks at 640ms (≈1.56 Hz). Most actions complete on tick boundaries; see `docs/subsystems/world-tick-loop.md`.
- **Combat triangle** — Melee > Ranged > Magic > Melee, baseline interaction model.
- **PvP / Wilderness** — the zone where players can fight each other; outside it, attacks against players are blocked.
- **Multi-combat zone** — area where multiple attackers can engage one target simultaneously. Outside, combat is 1v1 until disengage.
- **Prayer drain** — prayer points tick down while a prayer is active; rate depends on which prayer.
- **F2P / P2P** — Free-to-play / Pay-to-play. RSC had a member-only content tier; the conf flag toggles which content is enabled.
- **Aggro / Aggression** — NPC behavior of attacking players within range without being clicked.

## Server architecture

- **Game server** — the JVM process that runs the world, accepts player connections, and ticks the loop.
- **Plugin** — content code (skill actions, NPC dialogues, quests) loaded as separate compilation units from `server/plugins/`. See `docs/subsystems/scripting-plugins.md`.
- **Preset / config** — one of `preservation.conf`, `2001scape.conf`, `cabbage.conf`, `coleslaw.conf`, `openpk.conf`, `uranium.conf`, `default.conf`. Each picks an era and feature set. Voidscape's base is documented in `docs/SERVER-PRESETS.md`.
- **Definition / def** — the static data describing an item, NPC, scenery object, or shop. Loaded into in-memory catalogs at boot. Source files live in `server/conf/server/data/` (see `docs/subsystems/persistence-db.md`).

## Networking

- **Opcode** — single-byte (sometimes two-byte) ID identifying a packet type. The opcode space is shared between server and client; both must agree.
- **ISAAC** — stream cipher used to obfuscate opcodes packet-by-packet after login. Seeded during the login handshake.
- **RSA** — used to encrypt the login block (containing username/password and the ISAAC seed). Key configuration in `server/conf/`.
- **Packet builder** — fluent API for constructing outbound packets. See `docs/subsystems/networking-protocol.md`.
- **Protocol version / build number** — integer the client sends in the login block so the server can refuse mismatched clients.

## Persistence

- **Avatar** — the `.png` (or per-format) file under `server/avatars/` that records a character's appearance/state. Format details in `docs/subsystems/persistence-db.md`.
- **Save tick** — the periodic "flush this player's state to DB" event.

## Build / tooling

- **gradlew** — Gradle wrapper script in `server/`. Always prefer it over a system-installed `gradle`.
- **Plugins compile step** — separate from core compile; see `compile_plugins.cmd` / `compile_plugins.sh`.

---

_This file is seeded; subsystem-mapping agents add terms they encounter. If a term shows up in code or docs and isn't here, add it._
