# Server presets

OpenRSC ships seven `.conf` presets in `server/`. Each represents an era and play style. Voidscape's working config lives at `server/local.conf` (gitignored), copied from one of these templates and edited.

## Preset inventory

### `preservation.conf` — authentic RSC with minimal QoL
- DB: `preservation`. Port: 43594. Era: ~RSC c.2010.
- `member_world: true`, `want_fatigue: true`, `game_tick: 640`, `exp_rate: 1.0`, `auto_save: 120s`
- `location_data: 1` (some custom content), `want_fixed_broken_mechanics: true`
- Style: faithful to original. Fatigue is real. Slow grind.

### `default.conf` — generic hybrid
- DB: `preservation`. Port: 43594.
- Identical to preservation in practice; `location_data: 0` (strictly authentic).
- Style: safe baseline if you don't pick a preset.

### `2001scape.conf` — early-2001 retro
- DB: `2001scape`. Port: 43593.
- `member_world: false`, `want_fatigue: false`, `game_tick: 640`, `exp_rate: 1.0`
- `client_version: 20010000` (custom retro version, enforced)
- `rapid_cast_spells: true`
- `based_map_data: 14`, `based_config_data: 18`, `restrict_item_id: 306`, `restrict_scenery_id: 179`
- Style: bare-bones, minimal quests, pure nostalgia. F2P-only, RPK-style world.

### `rsccabbage.conf` — fast hybrid with addons
- DB: `cabbage`. Port: 43595.
- `member_world: true`, `want_fatigue: false`, `game_tick: 430` (faster), `exp_rate: 5.0`
- `auto_save: 30s`, `want_experience_cap: true`
- `location_data: 2` (custom content)
- `character_creation_mode: 1` (ironman + 1x modes)
- `want_decorated_mod_room: true`
- Style: fast-paced, QoL-focused. No fatigue. 5× exp rate.

### `openpk.conf` — open-world PK
- DB: `openpk`. Port: 43597.
- `exp_rate: 15.0`, `wilderness_boost: 40`, `skull_boost: 50`
- `npc_respawn_multiplier: 0.5` (NPCs respawn 2× faster)
- `player_level_limit: 120`, `location_data: 4`
- `max_connections_per_ip: 500` (allows multi-logging)
- Style: chaotic, action-heavy, designed for PK clans.

### `uranium.conf` — bot-friendly authentic
- DB: `uranium`. Port: 43235.
- `exp_rate: 1.0`, `want_fatigue: false`, `auto_save: 300s`
- `max_connections_per_ip: 100`, `max_logins_per_second: 40`
- `want_pcap_logging: false`
- Style: anything goes. Pure grind for automation.

### `rsccoleslaw.conf` — fast bot-friendly
- DB: `coleslaw`. Port: 43599.
- Like `rsccabbage` but `exp_rate: 2.0`, `max_connections_per_ip: 45`, `max_logins_per_second: 40`.
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

# Keep authentic combat speed and progression
# (preservation defaults are good — don't override game_tick or exp_rate)

# Optional: enable some custom-but-authentic-feeling content
# location_data: 1   # already preservation default; bump to 2 if you want OpenRSC additions

# Voidscape identity
server_name: Voidscape
server_name_welcome: Voidscape
```

If you find authentic-rate progression too slow during early development, consider:
- `exp_rate: 2.0` — modest 2× boost (still feels authentic-ish)
- Keep `game_tick: 640` — never lower this; it changes combat feel fundamentally

If `rsccabbage` ends up feeling closer to the actual goal after testing, we can pivot — but document the rationale in `docs/DIVERGENCE.md`.

**Avoid as base**: `2001scape` (too retro), `openpk` / `uranium` / `rsccoleslaw` (botting permitted, off-vision).

## Active config selection

`ServerConfiguration.java` resolution order:

1. **CLI arg** — `ant runserver -DconfFile=mypreset` loads `mypreset.conf`.
2. **`local.conf`** — if it exists *and* you pass `-DconfFile=local`, it overrides.
3. Otherwise, error: server tells you to provide `-DconfFile`.

Voidscape convention: **always run via `local.conf`**. The `scripts/run-server.sh` wrapper enforces this.

`server/connections.conf` is loaded first regardless, for DB connection settings.

## Critical config keys

The ~20 most important — see comments in each `.conf` for the full set.

| Key | Example | Purpose |
|---|---|---|
| `db_name` | `preservation` | SQLite/MySQL database name |
| `db_type` | `sqlite` or `mysql` | DB backend (in `connections.conf`) |
| `server_port` | `43594` | TCP port for game clients |
| `ws_server_port` | `43494` | WebSocket port (web client) |
| `max_players` | `2000` | Concurrent player cap |
| `server_name` | `Voidscape` | Shown in login screen + friends list |
| `server_name_welcome` | `Voidscape` | Welcome screen header |
| `member_world` | `true` | P2P content enabled |
| `want_fatigue` | `false` | Stamina drain during skilling |
| `game_tick` | `640` | Tick speed in ms (authentic = 640) |
| `exp_rate` | `1.0` | Multiplier for all exp gains |
| `wilderness_boost` | `0` | Additive exp bonus in wilderness |
| `max_connections_per_ip` | `20` | Concurrent connections per IP |
| `max_logins_per_second` | `2` | Per-IP login attempt rate |
| `auto_save` | `30000` | Player save interval (ms) |
| `client_version` | `10010` | Protocol version (must match client) |
| `enforce_custom_client_version` | `true` | Reject mismatched clients |
| `location_data` | `1` | Item/NPC data set (0=strict authentic, 1=preservation, 2=custom, 4=openpk) |
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
