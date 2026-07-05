# Server presets

OpenRSC ships seven `.conf` presets in `server/`. Each represents an era and play style. Voidscape's working config lives at `server/local.conf` (gitignored), copied from one of these templates and edited.

## Preset inventory

### `preservation.conf` — authentic RSC with minimal QoL
- DB: `preservation`. Port: 43596. Era: ~RSC c.2010.
- `member_world: true`, `want_fatigue: true`, `game_tick: 640`, `combat_exp_rate: 1`, `skilling_exp_rate: 1`, `auto_save: 120s`
- `location_data: 1` (preservation + discontinued content, e.g. the Black Hole), `want_fixed_broken_mechanics: true`
- Style: faithful to original. Fatigue is real. Slow grind.

### `default.conf` — generic hybrid
- DB: `preservation`. Port: 43594.
- Identical to preservation in practice; `location_data: 0` (strictly authentic).
- Style: safe baseline if you don't pick a preset.

### `2001scape.conf` — early-2001 retro
- DB: `2001scape`. Port: 43593.
- `member_world: false`, `want_fatigue: false`, `game_tick: 640`, `combat_exp_rate: 1`, `skilling_exp_rate: 1`
- `client_version: 20010000` (custom retro version, enforced)
- `rapid_cast_spells: true`
- `based_map_data: 14`, `based_config_data: 18`, `restrict_item_id: 306`, `restrict_scenery_id: 179`
- Style: bare-bones, minimal quests, pure nostalgia. F2P-only, RPK-style world.

### `rsccabbage.conf` — fast hybrid with addons
- DB: `cabbage`. Port: 43595.
- `member_world: true`, `want_fatigue: false`, `game_tick: 430` (faster), `combat_exp_rate: 5`, `skilling_exp_rate: 5`
- `auto_save: 30s`, `want_experience_cap: true`
- `location_data: 2` (custom content)
- `character_creation_mode: 1` (ironman + 1x modes)
- `want_decorated_mod_room: true`
- Style: fast-paced, QoL-focused. No fatigue. 5× exp rate.

### `openpk.conf` — open-world PK
- DB: `openpk`. Port: 43597.
- `combat_exp_rate: 15`, `skilling_exp_rate: 15`, `wilderness_boost: 40`, `skull_boost: 50`
- `npc_respawn_multiplier: 0.5` (NPCs respawn 2× faster)
- `player_level_limit: 120`, `location_data: 4`
- `max_connections_per_ip: 500` (allows multi-logging)
- Style: chaotic, action-heavy, designed for PK clans.

### `uranium.conf` — bot-friendly authentic
- DB: `uranium`. Port: 43235.
- `combat_exp_rate: 1`, `skilling_exp_rate: 1`, `want_fatigue: true`, `auto_save: 300s`
- `max_connections_per_ip: 100`, `max_logins_per_second: 40`
- `want_pcap_logging: false`
- Style: anything goes. Pure grind for automation.

### `rsccoleslaw.conf` — fast bot-friendly
- DB: `coleslaw`. Port: 43599.
- Like `rsccabbage` but `combat_exp_rate: 2`, `skilling_exp_rate: 2`, `max_connections_per_ip: 45`, `max_logins_per_second: 40`.
- Style: bot-tolerant variant of cabbage.

## Voidscape recommendation

Voidscape's stated goal is **mostly authentic RSC with QoL and small customization**. That puts us between `preservation` (too punishing — fatigue is the canonical QoL pain point) and `rsccabbage` (too fast — 5× exp + 430ms tick are fundamentally non-authentic).

**Recommended starting point**: `preservation.conf` with targeted QoL overrides.

```bash
cp server/preservation.conf server/local.conf
$EDITOR server/local.conf
```

Suggested overrides to apply in `local.conf`:
```
# QoL — remove the most universally disliked authentic mechanic
want_fatigue: false

# Keep authentic combat timing, with Voidscape's current progression rates
game_tick: 640
combat_exp_rate: 10
skilling_exp_rate: 2

# Optional: enable some custom-but-authentic-feeling content
# location_data: 1   # already preservation default; bump to 2 if you want OpenRSC additions

# Voidscape identity
server_name: Voidscape
server_name_welcome: Voidscape
```

If these rates feel too fast for a particular test, lower `combat_exp_rate` and
`skilling_exp_rate` in that preset rather than changing `game_tick`. Keep
`game_tick: 640` unless you deliberately want to change the feel of combat and
movement.

If `rsccabbage` ends up feeling closer to the actual goal after testing, we can pivot — but document the rationale in `docs/DIVERGENCE.md`.

**Avoid as base**: `2001scape` (too retro), `openpk` / `uranium` / `rsccoleslaw` (botting permitted, off-vision).

## Active config selection

`Server.java` resolution order:

1. **CLI arg** — `ant runserver -DconfFile=mypreset` passes `mypreset.conf` to the server on startup; `-DconfFile=local` loads `local.conf` the same way.
2. **No CLI arg** — falls back to `default.conf`; this is not an error.

Voidscape convention: **always run via `local.conf`**. The `scripts/run-server.sh` wrapper enforces this (via the `runserverzgc` ant target, `-DconfFile=local`).

`server/connections.conf` is loaded first regardless, for DB connection settings.

## Critical config keys

The ~20 most important — see comments in each `.conf` for the full set.

| Key | Example | Purpose |
|---|---|---|
| `db_name` | `preservation` | SQLite/MySQL database name |
| `db_type` | `sqlite` or `mysql` | DB backend (in `connections.conf`) |
| `server_port` | `43596` | TCP port for the local Voidscape game client preset |
| `ws_server_port` | `43496` | WebSocket port (web client) |
| `max_players` | `2000` | Concurrent player cap |
| `server_name` | `Voidscape` | Shown in login screen + friends list |
| `server_name_welcome` | `Voidscape` | Welcome screen header |
| `member_world` | `true` | P2P content enabled |
| `want_fatigue` | `false` | Stamina drain during skilling |
| `game_tick` | `640` | Tick speed in ms (authentic = 640) |
| `combat_exp_rate` | `10` | Combat XP multiplier |
| `skilling_exp_rate` | `2` | Non-combat skill XP multiplier |
| `wilderness_boost` | `0` | Additive exp bonus in wilderness |
| `max_connections_per_ip` | `20` | Concurrent connections per IP |
| `max_logins_per_second` | `2` | Per-IP login attempt rate |
| `auto_save` | `30000` | Player save interval (ms) |
| `client_version` | `10122` | Protocol version (must match client) |
| `enforce_custom_client_version` | `true` | Reject mismatched clients |
| `location_data` | `1` | Item/NPC/scenery data set (0=preservation baseline, 1=+discontinued, 2=+custom, 4=openpk) |
| `character_creation_mode` | `0` | 0=standard, 1=ironman+1x, 2=classes+globalPK |

DB credentials live in `server/connections.conf`:
```
db_host: localhost:3306
db_user: root
db_pass: root
```

## Quick-pick table

| Goal | Preset |
|---|---|
| Voidscape default | **preservation.conf** + QoL overrides → `local.conf` |
| Authentic purist | preservation.conf |
| Retro nostalgia | 2001scape.conf |
| Fast-paced QoL | rsccabbage.conf |
| PK clan world | openpk.conf |
| Botting allowed | uranium.conf or rsccoleslaw.conf — **off voidscape's vision** |
