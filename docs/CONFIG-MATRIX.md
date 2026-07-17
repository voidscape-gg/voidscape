# Config matrix

This file records the intended shape of Voidscape configs so `server/local.conf` does not become tribal knowledge. `server/local.conf` is ignored, but its important values should not be mysterious.

The machine-checked launch profile lives in `scripts/launch-config-contract.json`.
`scripts/package-launch-staging.sh` applies it to a secret-free base preset, and
the hosted verifier rejects missing, changed, or duplicate contract keys. Every
intentional player-facing difference from the preservation base belongs in that
contract; it must not rely on an ignored `server/local.conf` value.

## Current decision points

| Concern | Current local value | Release target | Notes |
|---|---:|---:|---|
| Client version | `10139` | Match the released client | Must match `Client_Base/src/orsc/Config.java`. |
| Member world | `true` | Hybrid, P2P-enabled | Launch decision: keep `member_world: true`, but make the early game feel F2P/classic and gate higher-value content through requirements, risk, cost, or location. |
| Server port | `43596` | TBD | `scripts/run-client.sh` reads local server port automatically. |
| Android public host | `5.161.114.251` | Final DNS name before broad release | Current APK one-tap Play target; replace hardcoded IP when the stable domain is ready. |
| WebSocket port | `43496` | TBD | Needed only if serving a WS/web client path. |
| DB backend | Usually SQLite locally | MariaDB recommended for public | See `docs/OPERATIONS.md`. |
| Game DB writer topology | One local game-server JVM | Exactly one live game-server JVM per game DB | Ranked admission/settlement and startup reconciliation assume a single game-server writer. Never overlap old/new or blue/green game-server processes on the same DB. |
| Custom content gate | `want_void_enclave: true` | `true` | Many Voidscape systems are loaded under this gate. |
| Release-disabled Void boss/dungeon gates | `want_void_colossus: false`, `want_void_dungeon: false` | `false` until deliberately launched | Public traversal should not expose Colossus or Void Dungeon content before their release pass. |
| Custom landscape | expected `true` | `true` | Required for Void Island, Enclave, arenas, and map edits. |
| Item cap | `restrict_item_id: 9999` | `9999` | Required for custom item ids in the 1500+ range. |
| Packet capture | `want_pcap_logging: false` | `false` | Enable only for targeted networking debug. |
| Production command lockdown | `true` for prelaunch/public rehearsal | `true` for public launch | Makes high-risk staff/dev commands owner-only without tying launch safety to the beta guide flag. |

## Core identity

| Key | Dev/local | Staging | Production | Source |
|---|---|---|---|---|
| `server_name` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `server_name_welcome` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `welcome_text` | Voidscape-specific | Voidscape-specific | Launch copy | `server/local.conf` |
| `client_version` | `10139` | Match client | Match client | Server conf + `Config.java` |
| `enforce_custom_client_version` | `true` | `true` | `true` | Server conf |
| `want_email` | `false` for the launch preset | `false` | `false` | Desktop packet character creation asks only for username/password. Android and web use the portal account flow, which may collect email. |
| `want_packet_register` | `true` for the launch preset, `false` when omitted | `true` | `true` | Enables desktop in-client character creation through the existing register packet. Native Android and `/play` web signup remain portal-first. |

## Portal account API

| Key | Dev/local | Staging | Production | Notes |
|---|---|---|---|---|
| `PORTAL_OPENRSC_DB` | `server/inc/sqlite/voidscape.db` when testing | staging DB bridge path or service DSN | production account/game write path | Local scaffold uses a SQLite file path; production should use the real portal/game persistence boundary. |
| `PORTAL_ADMIN_TOKEN` | explicit local secret | secret manager | replace with staff identity/RBAC | Enables `/api/admin/*`; unset means admin endpoints return `admin_not_configured`. |
| `PORTAL_SIGNUP_IP_HOURLY_LIMIT` / `PORTAL_SIGNUP_IP_DAILY_LIMIT` | launch mode `3` / `5`; otherwise `10` / `10` | `3` / `5` | `3` / `5` | Caps completed account creation and code-only founder reservations per non-local IP. Pending verification sends use their own counters and do not consume completed-signup velocity. |
| `PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT` / `PORTAL_SIGNUP_SUBNET_DAILY_LIMIT` | launch mode `20` / `50`; otherwise `0` / `0` | `20` / `50` | `20` / `50` | IPv4 `/24` cap for completed launch signups and code-only founder reservations; `0` disables it outside launch mode. |
| Initial launch-signup character | bypass repeat-character velocity | same | same | The first character created as part of an accepted signup bypasses the account/IP/subnet and proxy character-velocity caps. It still enforces the targeted IP blocklist, reserved/duplicate name checks, configured game DB, and transactional max-10 roster control; an initial-signup marker excludes it from later repeat-character counts. |
| `PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT` / `PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT` | launch mode `5` / `10`; otherwise `10` / `10` | `5` / `10` | `5` / `10` | Caps additional real character creation per portal account before the separate 10-slot roster cap. |
| `PORTAL_CHARACTER_IP_HOURLY_LIMIT` / `PORTAL_CHARACTER_IP_DAILY_LIMIT` | launch mode `10` / `20`; otherwise `10` / `10` | `10` / `20` | `10` / `20` | Caps additional real OpenRSC character creation per non-local IP. |
| `PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT` / `PORTAL_CHARACTER_SUBNET_DAILY_LIMIT` | launch mode `50` / `100`; otherwise `0` / `0` | `50` / `100` | `50` / `100` | IPv4 `/24` cap for additional character creation; `0` disables it outside launch mode. |
| `PORTAL_BLOCKED_IP_CIDRS` | unset | targeted `/32` or CIDR list | targeted `/32` or CIDR list | Rejects configured non-local IPv4 ranges with `403 ip_blocked`; use `/32` entries for exact-IP blocks. |
| `PORTAL_PROXY_IP_CIDRS` / `PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT` / `PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT` | unset / `1` / `1` | targeted list / `1` / `1` | targeted list / `1` / `1` | Applies tighter proxy/VPN overrides to completed signup and additional-character IP checks. Each override is the upper bound for both the hourly and daily IP check; the initial signup character still bypasses repeat-character velocity. |
| `PORTAL_LOGIN_IP_FAILURE_LIMIT` / `PORTAL_LOGIN_EMAIL_FAILURE_LIMIT` | `10` / `20` | tune from beta traffic | tune from incident traffic | Throttles password-login failures by source IP and by target account email before password verification. |
| `PORTAL_RECOVERY_FAILURE_LIMIT` / `PORTAL_RECOVERY_FAILURE_WINDOW_MINUTES` | `10` / `15` | tune from beta traffic | tune from incident traffic | Throttles bad recovery-code attempts by source IP and by target account email. |
| `PORTAL_PASSWORD_RESET_TTL_MINUTES` | `30` | `30` | `30` | Lifetime of a hashed, one-use emailed website-password reset token; minimum accepted value is 5 minutes. |
| `PORTAL_PASSWORD_RESET_IP_LIMIT` / `PORTAL_PASSWORD_RESET_ACCOUNT_LIMIT` / `PORTAL_PASSWORD_RESET_WINDOW_MINUTES` | `5` / `3` / `60` | same, then tune from logs | same, then tune from logs | Throttles recovery-email requests while keeping known and unknown identifiers externally indistinguishable. |
| `PORTAL_LEGACY_CLAIM_TTL_MINUTES` | `30` | `30` | `30` | Lifetime of a hashed, one-use email token for claiming a passwordless native-account backfill; minimum accepted value is 5 minutes. |
| `PORTAL_LEGACY_CLAIM_IP_LIMIT` / `PORTAL_LEGACY_CLAIM_CHARACTER_LIMIT` / `PORTAL_LEGACY_CLAIM_WINDOW_MINUTES` | `10` / `5` / `60` | same, then tune from logs | same, then tune from logs | Caps current-game-password claim attempts globally per source IP and per source-IP/character pair, so one hostile IP cannot lock the rightful owner out. Failed proofs count; completion failures also use the general recovery-failure throttle. |
| `PORTAL_SENSITIVE_ACTION_WINDOW_MINUTES` | `10` | `10` | `10` | Maximum age of a passwordless federated login before recovery-code rotation or game-password reset needs fresh authentication. Password accounts always confirm the current website password. |
| `PORTAL_GAME_PASSWORD_RESET_LIMIT` / `PORTAL_GAME_PASSWORD_RESET_WINDOW_MINUTES` | `5` / `60` | same | same | Per-IP and per-account attempt cap for authenticated character game-password changes. Failed step-up authentication and offline checks count. |
| `PORTAL_GAME_PASSWORD_HELPER_CLASSPATH` / `PORTAL_JAVA_BIN` | `server/core.jar` / `java` | packaged helper jar / Java runtime | `/opt/voidscape/server/portal-password-helper.jar` / `java` | Runs only the canonical OpenRSC bcrypt-compatible password helper over stdin in production, without coupling portal deploys to the full game jar. Password changes and older-account claims fail closed if the helper or ownership guard is unavailable. |
| `PORTAL_CAPTCHA_REQUIRED` / `PORTAL_CAPTCHA_SIGNUP_REQUIRED` | unset | optional Turnstile | enable during abuse/ad bursts | Requires CAPTCHA for founder reservations plus password/Google signup when `PORTAL_CAPTCHA_SITE_KEY` and `PORTAL_CAPTCHA_SECRET` are set. |
| `PORTAL_CAPTCHA_CHARACTER_REQUIRED` | unset | optional | optional incident clamp | Extends CAPTCHA to authenticated real character creation. |
| `PORTAL_STARTER_IP_DAILY_LIMIT` | `0` disabled | keep disabled unless copy changes | keep disabled unless copy changes | Accepted prelaunch accounts receive founder-card campaign qualification by default; the founder freeze creates the exact per-character ledger. Positive values intentionally re-enable pre-freeze qualification review and do not suppress launch-24h markers. |
| `PORTAL_ABUSE_HASH_SALT` | dev-only value | stable private secret | stable private secret | Rotating it loses the ability to compare old abuse-signal hashes. |
| Google OAuth/provider config | hidden unless configured | optional later | optional later | Leave `PORTAL_GOOGLE_CLIENT_ID` unset for launch unless intentionally enabling Google Identity Services; redirect-style `/api/oauth/google/*` remains a placeholder. |
| Payment provider config | none | sandbox checkout/webhooks | live checkout/webhooks | Production subscription-card checkout currently returns `501` until a provider is wired. |
| `PORTAL_DATA_DIR` | optional temp-backed local default | explicit durable path | `/var/lib/voidscape-portal` | Required in public mode. The directory must be real/private/writable (`0700`), and `dev-store.json` must already be canonical, regular, private (`0600`), and readable; startup and health fail closed otherwise. Back it up and restore it with the matching game DB. |
| `PORTAL_ALLOW_TMPDIR` | optional non-public test override | unset | unset | Never enables temp storage in public mode. Keep unset outside isolated local development. |
| `node web/portal/dev-server.mjs --initialize-store` | optional fresh fixture setup | fresh host only | fresh host only | Absent-only initialization command. Root first prepares the explicit durable directory as `voidscape:voidscape 0700`, then runs the command once as `voidscape` with `PORTAL_DATA_DIR`; it creates `0600` state, logs the initialization, refuses any existing path, and exits. It is not part of the normal service command. |
| `PORTAL_PUBLIC_MODE` | optional | `1` for public rehearsal | `1` | Locks the portal to public-safe landing/account surfaces plus token-gated admin endpoints. |
| `PORTAL_LAUNCH_SIGNUP_MODE` | optional | `1` for ad-flow rehearsal | `1` for ads | Turns public mode into account-first signup. With email verification enabled, creation is deferred until the verification link is consumed; otherwise it creates the web account, first linked OpenRSC character, and one used roster slot. Completion before launch also records founder qualification; character creation before the launch-24h cutoff atomically records `launch_24h_card`. Requires `PORTAL_OPENRSC_DB` for real launch use. |
| `PORTAL_EMAIL_VERIFICATION_REQUIRED` | unset unless testing | `1` for public rehearsal | `1` | Verified email remains required for public password signup; the account and first character are created only after explicit verification. Founder qualification requires completion before launch, while the separate launch marker uses the character-creation cutoff. Requires configured email delivery. |
| `PORTAL_EMAIL_VERIFICATION_TTL_HOURS` | `48` | `48` | `48` | Pending signup verification-link lifetime. |
| `PORTAL_EMAIL_VERIFICATION_IP_LIMIT` / `PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT` / `PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES` | `3` / `3` / `60` | `3` / `3` / `60` | `3` / `3` / `60` | Independently caps initial and resend verification requests per source IP and per canonical email. `POST /api/accounts/verify-email/resend` returns the same accepted response whether a live pending signup exists, apart from a generic rate-limit response. These sends do not count as completed signups. |
| `PORTAL_PUBLIC_ORIGIN` | optional local URL | staging HTTPS origin | `https://voidscape.gg` | Canonical origin for emails, public download metadata, and launcher manifests; required when public email verification is enabled. |
| `PORTAL_LAUNCH_FREE_CARD_HOURS` | `24` | `24` | `24` | Keeps the separate launch-card promotion open until `2026-07-19T18:00:00Z` for this launch; characters created at or after that instant do not qualify. Founder reservations and the immutable `starter:<accountId>:<playerId>` founder manifest freeze at launch instead. |
| `PORTAL_WEB_CLIENT_URL` | `https://voidscape.gg/play/` | release URL | release URL | Browser-client URL used by release account surfaces/API metadata; the prelaunch landing mentions platform support but keeps the main CTA on reservation. |

## World rules

| Key | Dev/local | Staging | Production | Release note |
|---|---:|---:|---:|---|
| `game_tick` | `640` | `640` | `640` | Authentic RSC combat/movement feel. |
| `milliseconds_between_casts` | `1900` | `1900` | `1900` | Explicit three-tick spell cadence used by players and Sir Charles; older presets' `milliseconds_between_spells` spelling is not the runtime key. |
| `combat_exp_rate` / `skilling_exp_rate` | `10` / `1.5` | `10` / `1.5` | `10` / `1.5` | Subscription adds +1x to each, normally 11x combat / 2.5x skills while active. |
| `melee_gives_xp_hit` | `true` | `true` | `true` | Voidscape keeps the tested successful-hit XP timing instead of silently reverting to authentic death-time payout. |
| `ranged_gives_xp_hit` | `true` | `true` | `true` | Kept paired with melee hit XP for the launch profile. |
| `openrsc_classic_combat_baseline` | `true` only for the current local trial | `false` / omitted | `false` / omitted | Server-only comparison switch for the inherited OpenRSC classic formula. Keep `osrs_combat_melee: false`; launch remains on Voidscape's tuned classic rules until the trial is explicitly accepted. |
| `launch_subscription_card_until` | unset unless testing packet registration | launch + 24h UTC | `2026-07-19T18:00:00Z` | Required while release packet registration is enabled so desktop-created characters receive `launch_24h_card` before the cutoff; characters created at or after it do not. Env `VOIDSCAPE_LAUNCH_SUBSCRIPTION_CARD_UNTIL` overrides it. |
| `idle_timer` | `600000` | `600000` | `600000` | Regular players get a 10-minute movement-idle warning window; authentic presets keep their own/default value. |
| `idle_timer_subscriber` | `900000` | `900000` | `900000` | Active Void subscribers get a 15-minute movement-idle warning window; omitted configs fall back to `idle_timer`. |
| `aggro_range` | `4` | `4` | `4` | Voidscape default aggressive-NPC scan radius; authentic presets remain at the Java/default 1-tile behavior unless configured otherwise. |
| `wilderness_npc_blocking` | `0` | `0` | `0` | Wilderness override for `npc_blocking`; aggressive NPCs still aggro/fight, but do not body-block movement on Wilderness tiles. |
| `wilderness_spawn_multiplier` | `1.5` | `1.5` | `1.5` | Boot-time multiplier for attackable wilderness NPC spawn locs; decimal values add a deterministic proportional subset; default `1` disables it for authentic presets. |
| `want_fatigue` | `false` | `false` | `false` | Foundational QoL divergence. |
| `member_world` | `true` currently | `true` | `true` | Hybrid launch: P2P-enabled world with F2P-feeling early progression and controlled access to stronger content. This is a global server rule shared by launcher, Android, and web clients, not a per-player subscription flag. |
| `is_localhost_restricted` | `false` | `true` or IP-gated | `true` or IP-gated | Local-only convenience should not leak accidentally. |
| `production_command_lockdown` | `true` for prelaunch/public rehearsal | `true` | `true` | Non-owner staff keep moderation/read-only support commands, but economy/account/world/server-runtime/debug commands are owner-only. |
| `void_arena_allow_ambiguous_proxy_ranked` | `true` only for isolated local WebSocket QA | `false` | `false` | A non-public server-observed WebSocket peer is normally a reverse proxy, so ranked admission fails closed while unranked remains available. Keep this false outside isolated development; browser ranked requires a direct public peer or a future reviewed trusted-origin propagation path. |
| `want_email` | `false` for launch preset and staging bundle | `false` | `false` | Keeps desktop packet character creation email-free; native Android and web use portal accounts. |
| `want_packet_register` | `true` for launch preset and staging bundle; Java default remains `false` when omitted | `true` | `true` | Enables desktop in-client character creation while native Android and web `/play` use portal signup. |

## Player-facing Voidscape profile

These values freeze the game tested before launch. They are server settings sent to
all official clients where applicable, so changing them does not require rebuilding
desktop, Android, or TeaVM artifacts.

| Keys | Launch value | Player-facing effect |
|---|---:|---|
| `fog_toggle`, `experience_drops_toggle`, `show_roof_toggle`, `inventory_count_toggle` | `true` | Keeps the established graphics/XP/inventory controls and defaults available. |
| `batch_progression`, `want_drop_x` | `true` | Keeps repeat skilling/progress UI and Drop-X QoL. |
| `want_keyboard_shortcuts` | `2` | Keeps number-key menu selection with visible numeric labels. |
| `right_click_bank`, `right_click_trade` | `true` | Keeps direct Banker and Shopkeeper shortcuts. |
| `want_global_chat`, `want_global_friend` | `true`, `false` | Uses Voidscape's direct global channel rather than the legacy `Global$` pseudo-friend path. |
| `want_global_chat_country_flags` | `true` | Keeps the current country-flag privacy/display flow in global chat. |
| `spawn_auction_npcs` | `true` | Keeps the tested Auction House available. |
| `want_world_announcements`, `want_world_milestone_announcements`, `want_world_new_player_announcements`, `want_world_skulled_pk_announcements` | `true` | Keeps the established social broadcasts. |
| `more_shafts_per_better_log` | `true` | Keeps the tested player-made arrow economy. |
| `avatar_generator` | `true` | Keeps real saved-character avatar renders available to the account portal after logout. |

## Content gates

| Key | Expected value | Why it matters |
|---|---:|---|
| `want_void_enclave` | `true` | Loads the Enclave, several custom loc files, and related content. |
| `want_void_colossus` | `false` for launch | Keeps Void Colossus rifts/arena content unloaded; stale entry handlers also deny access while the feature is off. |
| `want_void_dungeon` | `false` for launch | Keeps Void Dungeon locs/NPCs unloaded; stale entry handlers deny access while preserving the dungeon exit path. |
| `want_beta_onboarding_guide` | `false` for launch | Beta-only tester toolkit for teleports, stat presets, item kits, and FarmSim shortcuts. Keep enabled only during trusted beta windows. |
| `production_command_lockdown` | `true` for launch | Owner-only guard for item/NPC spawning, stat/account mutation, forced teleport/movement, server lifecycle, bot/load-test, event/world reset, and QA fixture commands. |
| `custom_landscape` | `true` | Enables custom patched terrain used by multiple systems. |
| `spawn_auction_npcs` | `true` | Spawns the Void Auctioneer and gates marketplace access. |
| `want_world_announcements` | `true` | Master switch for milestone, new-player, and skulled-Wilderness PK world messages. |
| `want_world_milestone_announcements` | `true` | Announces selected skill and total-level milestones. |
| `want_world_new_player_announcements` | `true` | Announces when a brand-new player first joins. |
| `want_world_skulled_pk_announcements` | `true` | Announces PKs only when the defeated player was skulled. |
| `want_global_chat_country_flags` | `true` | Shows player-chosen country flags in global chat and lets players hide their own flag in settings. |
| `more_shafts_per_better_log` | `true` | Lets higher-tier logs feed the player-made arrow economy instead of every log producing the same 10 shafts. |
| Subscription cards | Always available in Voidscape | Tradable cards add 7 account-wide days and the small XP bump; they do not unlock P2P areas because `member_world` is already global. The Lumbridge vendor grants a card for an exact founder composite or a launch-only marker and folds same-character founder/launch overlap into one physical issuance. Referral codes remain independent. |
| `want_custom_banks` | `false` for Void Glass launch on desktop, `/play`, and Android | Leave off for the shipped Void Glass bank. `true` is only for older legacy custom-bank presets when custom UI is off; loadouts do not require it. |
| `want_bank_presets` | `true` if shipping loadouts | Enables loadouts/presets for Void Glass and requires `bankpresets` schema. |
| `want_bank_notes` / `want_cert_as_notes` | `true` if shipping notes | Controls note-style item handling and visuals. |
| `want_leftclick_webs` | `true` | Supports Void Enclave web gates and vanilla web QoL. |
| `perf_telemetry` | `true` in staging, optional production | Low-overhead visibility into tick health. |
| `want_pcap_logging` | `false` | Packet capture is a debug tool. |

## Client-visible changes that require version review

Current official custom-client cohort: `10139`.

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
| Custom item ids | `1609` | Cowboy hat is current high-water mark. |
| Custom NPC ids | `868` | Void Archivist is current high-water mark. |
| Custom scenery ids | `1313` | Void market shelter is current high-water mark. |
| Custom client version | `10139` | Current working tree value. |

## Pre-release config sign-off

- [x] F2P vs members default decided: hybrid/P2P-enabled world with F2P-feeling early progression.
- [x] XP rates decided: 10x combat / 1.5x skills, subscription adds +1x to each.
- [ ] PvP/safe-zone policy decided.
- [x] Economy-affecting QoL toggles decided: notes, Auction House, bank presets, and batch skilling stay enabled as tested.
- [x] Combat XP timing decided: melee and ranged successful-hit XP stay enabled as tested.
- [x] Social profile decided: direct global chat, country flags, and the four world-announcement gates stay enabled.
- [x] Rift travel policy decided: ordinary Void Rifts form a five-hub network (Void Enclave, Edgeville, Varrock, Falador, Ardougne), with Lumbridge left to Home teleport and world-map autowalk/saved walks as the broad traversal QoL.
- [x] Launch command policy decided: enable `production_command_lockdown: true`; keep moderation/read-only staff support available and make high-risk staff/dev commands owner-only.
- [ ] Production database selected.
- [ ] Deployment topology guarantees exactly one live game-server JVM per game DB, with no rolling or blue/green writer overlap.
- [ ] Public host/ports selected.
- [ ] Client update/cache distribution path selected.
- [ ] Discord/community links selected.
- [ ] AGPL source-disclosure path selected.
