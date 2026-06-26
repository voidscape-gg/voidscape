# Config matrix

This file records the intended shape of Voidscape configs so `server/local.conf` does not become tribal knowledge. `server/local.conf` is ignored, but its important values should not be mysterious.

## Current decision points

| Concern | Current local value | Release target | Notes |
|---|---:|---:|---|
| Client version | `10070` | `10070` until next client-visible change | Must match `Client_Base/src/orsc/Config.java`. |
| Member world | `true` | Hybrid, P2P-enabled | Launch decision: keep `member_world: true`, but make the early game feel F2P/classic and gate higher-value content through requirements, risk, cost, or location. |
| Server port | `43596` | TBD | `scripts/run-client.sh` reads local server port automatically. |
| Android public host | `5.161.114.251` | Final DNS name before broad release | Current APK one-tap Play target; replace hardcoded IP when the stable domain is ready. |
| WebSocket port | `43496` | TBD | Needed only if serving a WS/web client path. |
| DB backend | Usually SQLite locally | MariaDB recommended for public | See `docs/OPERATIONS.md`. |
| Custom content gate | `want_void_enclave: true` | likely `true` | Many Voidscape systems are loaded under this gate. |
| Custom landscape | expected `true` | likely `true` | Required for Void Island, Enclave, arenas, and map edits. |
| Item cap | `restrict_item_id: 9999` | `9999` | Required for custom item ids in the 1500+ range. |
| Packet capture | `want_pcap_logging: false` | `false` | Enable only for targeted networking debug. |
| Production command lockdown | absent/default `false` locally | `true` for public launch | Makes high-risk staff/dev commands owner-only without tying launch safety to the beta guide flag. |

## Core identity

| Key | Dev/local | Staging | Production | Source |
|---|---|---|---|---|
| `server_name` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `server_name_welcome` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `welcome_text` | Voidscape-specific | Voidscape-specific | Launch copy | `server/local.conf` |
| `client_version` | `10099` | Match client | Match client | Server conf + `Config.java` |
| `enforce_custom_client_version` | `true` | `true` | `true` | Server conf |
| `want_packet_register` | `true` | `true` for beta | Decide before public launch | Friend beta allows in-client account/character creation; the portal flow can still be used. |

## Portal account API

| Key | Dev/local | Staging | Production | Notes |
|---|---|---|---|---|
| `PORTAL_OPENRSC_DB` | `server/inc/sqlite/voidscape.db` when testing | staging DB bridge path or service DSN | production account/game write path | Local scaffold uses a SQLite file path; production should use the real portal/game persistence boundary. |
| `PORTAL_ADMIN_TOKEN` | explicit local secret | secret manager | replace with staff identity/RBAC | Enables `/api/admin/*`; unset means admin endpoints return `admin_not_configured`. |
| `PORTAL_STARTER_IP_DAILY_LIMIT` | `5` default, tests use `2` | tune from beta traffic | tune with logs and support policy | Limits only free starter-card grants from the same non-local IP bucket; accounts still register. |
| `PORTAL_ABUSE_HASH_SALT` | dev-only value | stable private secret | stable private secret | Rotating it loses the ability to compare old abuse-signal hashes. |
| Google OAuth/provider config | dev endpoint only | configured provider | configured provider | Production `/api/oauth/google/*` currently returns `501` until real OAuth is wired. |
| Payment provider config | none | sandbox checkout/webhooks | live checkout/webhooks | Production subscription-card checkout currently returns `501` until a provider is wired. |

## World rules

| Key | Dev/local | Staging | Production | Release note |
|---|---:|---:|---:|---|
| `game_tick` | `640` | `640` | `640` | Authentic RSC combat/movement feel. |
| `combat_exp_rate` / `skilling_exp_rate` | `10` / `2` | `10` / `2` | `10` / `2` | Subscription adds +1x to each, normally 11x combat / 3x skills while active. |
| `melee_gives_xp_hit` | `true` locally per divergence | Decide | Decide | This is a gameplay divergence from authentic death-time melee XP. |
| `ranged_gives_xp_hit` | `true` locally per divergence | Decide | Decide | Keep paired with melee decision if desired. |
| `want_fatigue` | `false` | `false` | `false` | Foundational QoL divergence. |
| `member_world` | `true` currently | `true` | `true` | Hybrid launch: P2P-enabled world with F2P-feeling early progression and controlled access to stronger content. |
| `is_localhost_restricted` | `false` | `true` or IP-gated | `true` or IP-gated | Local-only convenience should not leak accidentally. |
| `production_command_lockdown` | `false` locally unless testing launch policy | `true` | `true` | Non-owner staff keep moderation/read-only support commands, but economy/account/world/server-runtime/debug commands are owner-only. |

## Content gates

| Key | Expected value | Why it matters |
|---|---:|---|
| `want_void_enclave` | `true` | Loads the Enclave, several custom loc files, and related content. |
| `want_beta_onboarding_guide` | `false` for launch | Beta-only tester toolkit for teleports, stat presets, item kits, and FarmSim shortcuts. Keep enabled only during trusted beta windows. |
| `production_command_lockdown` | `true` for launch | Owner-only guard for item/NPC spawning, stat/account mutation, forced teleport/movement, server lifecycle, bot/load-test, event/world reset, and QA fixture commands. |
| `custom_landscape` | `true` | Enables custom patched terrain used by multiple systems. |
| `spawn_auction_npcs` | `true` if Auction House is live | Spawns the Void Auctioneer and gates marketplace access. |
| `want_world_announcements` | `true` if Void Herald social broadcasts are live | Master switch for milestone and skulled-Wilderness PK world messages. |
| `want_world_milestone_announcements` | `true` if milestones should be public | Announces selected skill and total-level milestones. |
| `want_world_skulled_pk_announcements` | `true` if Wilderness kills should be public | Announces PKs only when the defeated player is skulled. |
| `want_global_chat_country_flags` | `true` if global chat is live | Server resolves public player IPs to country codes and lets players hide their own flag in settings. |
| `global_chat_local_country_code` | empty in release, `CA` locally if desired | Dev-only localhost override for testing flag rendering without a public IP. |
| `more_shafts_per_better_log` | `true` | Lets higher-tier logs feed the player-made arrow economy instead of every log producing the same 10 shafts. |
| Subscription cards | Always available in Voidscape | Tradable cards add 7 account-wide days; Lumbridge vendor grants one starter card reserved by the portal account flow. |
| `want_custom_banks` | `true` if shipping V2 bank | Enables the custom bank UI and loadout workflow. |
| `want_bank_presets` | `true` if shipping loadouts | Requires `bankpresets` schema. |
| `want_bank_notes` / `want_cert_as_notes` | `true` if shipping notes | Controls note-style item handling and visuals. |
| `want_leftclick_webs` | `true` | Supports Void Enclave web gates and vanilla web QoL. |
| `perf_telemetry` | `true` in staging, optional production | Low-overhead visibility into tick health. |
| `want_pcap_logging` | `false` | Packet capture is a debug tool. |

## Client-visible changes that require version review

Bump `Client_Base/src/orsc/Config.java` `CLIENT_VERSION` and matching server `client_version` when changing:

- opcodes or payload shape
- client-visible item/NPC/object/door/tile/texture definitions
- spell list ordering or spell ids
- cache archives that a stale client could misread
- login/account creation expectations
- settings packet shape
- world-map assets when stale clients would route or display misleading data

Do not bump for server-only plugin logic when existing definitions, packets, and cache remain compatible.

## Current custom id ranges

These are positional in many loaders. Keep server and client append order aligned.

| Type | Current high-water mark | Notes |
|---|---:|---|
| Custom item ids | `1608` | Void ashes are current high-water mark. |
| Custom NPC ids | `860` | Void Unicorn is current high-water mark. |
| Custom scenery ids | `1309` | Colossus charge effect objects are current high-water mark. |
| Custom client version | `10120` | Current working tree value. |

## Pre-release config sign-off

- [x] F2P vs members default decided: hybrid/P2P-enabled world with F2P-feeling early progression.
- [x] XP rates decided: 10x combat / 2x skills, subscription adds +1x to each.
- [ ] PvP/safe-zone policy decided.
- [ ] Economy-affecting QoL toggles decided: notes, auction house, bank presets, batch skilling.
- [x] Rift travel policy decided: ordinary Void Rifts go only to the Void Enclave; world-map autowalk remains the broad traversal QoL.
- [x] Launch command policy decided: enable `production_command_lockdown: true`; keep moderation/read-only staff support available and make high-risk staff/dev commands owner-only.
- [ ] Production database selected.
- [ ] Public host/ports selected.
- [ ] Client update/cache distribution path selected.
- [ ] Discord/community links selected.
- [ ] AGPL source-disclosure path selected.
