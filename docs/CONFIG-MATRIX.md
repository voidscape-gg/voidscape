# Config matrix

This file records the intended shape of Voidscape configs so `server/local.conf` does not become tribal knowledge. `server/local.conf` is ignored, but its important values should not be mysterious.

## Current decision points

| Concern | Current local value | Release target | Notes |
|---|---:|---:|---|
| Client version | `10132` | `10132` | Must match `Client_Base/src/orsc/Config.java`; recreate any older gitignored `server/local.conf` from the tracked launch preset before QA. |
| Member world | `true` | Hybrid, P2P-enabled | Launch decision: keep `member_world: true`, but make the early game feel F2P/classic and gate higher-value content through requirements, risk, cost, or location. |
| Server port | `43596` | `43596` | `scripts/run-client.sh` reads local server port automatically. |
| Android public host | `voidscape.gg` | `voidscape.gg:43596` | Release `9 / 1.0.8` defaults to DNS and migrates only the previous public pair `5.161.114.251:43596`; unrelated custom endpoints remain unchanged. |
| WebSocket port | `43496` | `43496` | Proxied publicly at `wss://voidscape.gg/play/ws/` only when launch ingress is open. |
| DB backend | Usually SQLite locally | Single-host SQLite + portal JSON for launch | Owner accepted the short launch runway over a last-week MariaDB migration. A quiesced, consistent game-DB + portal-data backup/restore rehearsal remains mandatory before cutover. |
| Custom content gate | `want_void_enclave: true` | likely `true` | Many Voidscape systems are loaded under this gate. |
| Launch Void gates | `want_void_colossus: false`, `want_void_dungeon: true` | Colossus `false`; Dungeon `true` | The approved launch dungeon is the three-floor, boss-free build; Colossus remains unreachable. |
| Custom landscape | expected `true` | likely `true` | Required for Void Island, Enclave, arenas, and map edits. |
| Item cap | `restrict_item_id: 9999` | `9999` | Required for custom item ids in the 1500+ range. |
| Packet capture | `want_pcap_logging: false` | `false` | Enable only for targeted networking debug. |
| Production command lockdown | `true` for prelaunch/public rehearsal | `true` for public launch | Makes high-risk staff/dev commands owner-only without tying launch safety to the beta guide flag. |
| Launch cracker campaign | enabled, pool `0` until QA | enabled, pool set explicitly by owner | `::cracker N` sets the exact finite supply; approved odds are `1/500` NPC kills and `1/1000` eligible skilling ticks. |
| Launch world achievements | enabled, season `launch-2026` | enabled, season `launch-2026` | No backfill. First skills are `80/90/99`, first item is campaign cracker `575`, and qualified PK loot floor is `5000` default-price gp. |

## Core identity

| Key | Dev/local | Staging | Production | Source |
|---|---|---|---|---|
| `server_name` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `server_name_welcome` | `Voidscape` | `Voidscape` | `Voidscape` | `server/local.conf` |
| `welcome_text` | Voidscape-specific | Voidscape-specific | Launch copy | `server/local.conf` |
| `client_version` | `10132` | `10132` | `10132` | Server conf + `Config.java` |
| `enforce_custom_client_version` | `true` | `true` | `true` | Server conf |
| `want_email` | `false` for the launch preset | `false` | `false` | Desktop and Android in-client character creation asks only for username/password. The web portal may still collect email for web accounts. |
| `want_packet_register` | `true` for the launch preset, `false` when omitted | `true` | `true` | Enables desktop and native Android in-client character creation through the existing register packet. `/play` web signup remains portal-first. |

## Portal account API

| Key | Dev/local | Staging | Production | Notes |
|---|---|---|---|---|
| `PORTAL_OPENRSC_DB` | `server/inc/sqlite/voidscape.db` when testing | staging DB bridge path or service DSN | production account/game write path | Local scaffold uses a SQLite file path; production should use the real portal/game persistence boundary. |
| `PORTAL_LEGENDS_SEASON_ID` | `launch-2026` | `launch-2026` | `launch-2026` | Server-selected season for the read-only public Legends projection. Callers cannot select another season; invalid config prevents portal startup. |
| `PORTAL_ADMIN_TOKEN` | explicit local secret | temporary loopback-only secret | unset during normal public operation | Public nginx must return `404` for `/api/admin/*` before requests reach the portal. Full staff identity/RBAC is deferred; bounded maintenance may set a temporary token only on the loopback backend, then remove it and restart. |
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
| `PORTAL_STARTER_IP_DAILY_LIMIT` | `0` disabled | keep disabled unless copy changes | keep disabled unless copy changes | Accepted promo accounts receive starter cards by default; abuse should be blocked before signup/character creation. Positive values intentionally re-enable card-review mode. |
| `PORTAL_ABUSE_HASH_SALT` | dev-only value | stable private secret | stable private secret | Rotating it loses the ability to compare old abuse-signal hashes. |
| Google OAuth/provider config | hidden unless configured | optional later | optional later | Leave `PORTAL_GOOGLE_CLIENT_ID` unset for launch unless intentionally enabling Google Identity Services; redirect-style `/api/oauth/google/*` remains a placeholder. |
| `PORTAL_TEBEX_PUBLIC_TOKEN` / `PORTAL_TEBEX_PRIVATE_KEY` | unset | Tebex sandbox/test-store secrets | live secret manager | Headless API Basic authentication. The private key never reaches the browser or health response. |
| `PORTAL_TEBEX_WEBHOOK_SECRET` | unset | sandbox endpoint secret | live endpoint secret | Verifies `X-Signature` over the exact raw body; this is distinct from the Headless private key. |
| `PORTAL_TEBEX_SUBSCRIPTION_CARD_PACKAGE_ID` | unset | allowlisted single-payment test package | allowlisted live single-payment package | Checkout always adds exactly this package at quantity one. |
| `PORTAL_TEBEX_EXPECTED_CURRENCY` / `PORTAL_TEBEX_EXPECTED_PRICE_MINOR` | unset | exact test package catalog values | exact live package catalog values | Package/basket/webhook base-price and currency mismatches fail closed. Provider-authorized discounts are recorded separately as product-paid/transaction-paid/adjustments; browser-supplied prices are never accepted. |
| Tebex package delivery policy | no live package | locked test package, zero deliverables | locked live package, zero deliverables | `single`, quantity disabled, gifting disabled, and no commands/RCON, gift-card, Discord, or other provider delivery. A controlled real purchase must prove package custom survives, no command queue side effect occurs, and exactly one pending entitlement is created before live enablement. |
| Tebex commerce schema | local patch when testing | apply and back up | apply before credentials | `2026_07_11_add_portal_commerce.sql`; contains no customer PII or raw webhook bodies. |
| `PORTAL_PUBLIC_MODE` | optional | `1` for public rehearsal | `1` | Locks the portal to public-safe landing/account surfaces. Backend admin handlers remain for bounded loopback maintenance, but every public reverse-proxy origin must return `404` for `/api/admin/*`. |
| `PORTAL_LAUNCH_SIGNUP_MODE` | optional | `1` for ad-flow rehearsal | `1` for ads | Turns public mode into account-first signup. With email verification enabled, creation is deferred until the verification link is consumed; otherwise it creates the web account, first linked OpenRSC character, one used roster slot, and starter-card reservation in one flow. Requires `PORTAL_OPENRSC_DB` for real launch use. |
| `PORTAL_EMAIL_VERIFICATION_REQUIRED` | unset unless testing | `1` for public rehearsal | `1` | Verified email remains required for public password signup; the account, first character, and starter card are created only after explicit email verification. Requires configured email delivery. |
| `PORTAL_EMAIL_VERIFICATION_TTL_HOURS` | `48` | `48` | `48` | Pending signup verification-link lifetime. |
| `PORTAL_EMAIL_VERIFICATION_IP_LIMIT` / `PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT` / `PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES` | `3` / `3` / `60` | `3` / `3` / `60` | `3` / `3` / `60` | Independently caps initial and resend verification requests per source IP and per canonical email. `POST /api/accounts/verify-email/resend` returns the same accepted response whether a live pending signup exists, apart from a generic rate-limit response. These sends do not count as completed signups. |
| `PORTAL_PUBLIC_ORIGIN` | optional local URL | staging HTTPS origin | `https://voidscape.gg` | Canonical origin for emails, public download metadata, and launcher manifests; required when public email verification is enabled. |
| `PORTAL_LAUNCH_FREE_CARD_HOURS` | `0` | `0` | `0` | Portal starter promises close at launch. The separate one-shot cutover seeds one `launch_subcard_2026:<playerId>` marker per reviewed final-roster character; later characters receive none. |
| `PORTAL_WEB_CLIENT_URL` | `https://voidscape.gg/play/` | release URL | release URL | Browser-client URL used by release account surfaces/API metadata; the prelaunch landing mentions platform support but keeps the main CTA on reservation. |

## World rules

| Key | Dev/local | Staging | Production | Release note |
|---|---:|---:|---:|---|
| `game_tick` | `640` | `640` | `640` | Authentic RSC combat/movement feel. |
| `combat_exp_rate` / `skilling_exp_rate` | `10` / `1.5` | `10` / `1.5` | `10` / `1.5` | Subscription adds +1x to each, normally 11x combat / 2.5x skills while active. |
| `melee_gives_xp_hit` | `true` locally per divergence | `true` | `true` | Launch keeps the per-hit melee XP divergence from authentic death-time melee XP. |
| `ranged_gives_xp_hit` | `true` locally per divergence | `true` | `true` | Launch keeps ranged paired with the melee decision. |
| `launch_subscription_card_until` | unset | unset | unset | Disabled. Native and portal characters receive launch cards only through the reviewed, sealed final-roster cutover, never a runtime or post-launch creation window. |
| `idle_timer` | `600000` | `600000` | `600000` | Regular players get a 10-minute movement-idle warning window; authentic presets keep their own/default value. |
| `idle_timer_subscriber` | `900000` | `900000` | `900000` | Active Void subscribers get a 15-minute movement-idle warning window; omitted configs fall back to `idle_timer`. |
| `aggro_range` | `4` | `4` | `4` | Voidscape default aggressive-NPC scan radius; authentic presets remain at the Java/default 1-tile behavior unless configured otherwise. |
| `wilderness_npc_blocking` | `0` | `0` | `0` | Wilderness override for `npc_blocking`; aggressive NPCs still aggro/fight, but do not body-block movement on Wilderness tiles. |
| `wilderness_spawn_multiplier` | `1.5` | `1.5` | `1.5` | Boot-time multiplier for attackable wilderness NPC spawn locs; decimal values add a deterministic proportional subset; default `1` disables it for authentic presets. |
| `want_fatigue` | `false` | `false` | `false` | Foundational QoL divergence. |
| `member_world` | `true` currently | `true` | `true` | Hybrid launch: P2P-enabled world with F2P-feeling early progression and controlled access to stronger content. This is a global server rule shared by launcher, Android, and web clients, not a per-player subscription flag. |
| `is_localhost_restricted` | `false` | `true` or IP-gated | `true` or IP-gated | Local-only convenience should not leak accidentally. |
| `production_command_lockdown` | `true` for prelaunch/public rehearsal | `true` | `true` | Non-owner staff keep moderation/read-only support commands, but economy/account/world/server-runtime/debug commands are owner-only. |
| `want_cracker_campaign` | `true` locally; pool defaults to `0` | `true` | `true` | Enables the finite campaign service. It awards nothing until the owner explicitly sets a positive durable pool. |
| `cracker_campaign_npc_kill_denominator` | `500` | `500` | `500` | Slice 4B policy: one candidate roll per legitimate rewarded NPC kill. |
| `cracker_campaign_skilling_denominator` | `1000` | `1000` | `1000` | Slice 4B policy: one candidate roll per eligible player/tick of positive noncombat, nonquest skilling XP. |
| `want_email` | `false` for launch preset and staging bundle | `false` | `false` | Keeps desktop/native Android character creation email-free; portal web accounts remain separate. |
| `want_packet_register` | `true` for launch preset and staging bundle; Java default remains `false` when omitted | `true` | `true` | Enables desktop/native Android in-client character creation while web `/play` continues to use portal signup. |

## Content gates

| Key | Expected value | Why it matters |
|---|---:|---|
| `want_void_enclave` | `true` | Loads the Enclave, several custom loc files, and related content. |
| `want_void_colossus` | `false` for launch | Keeps Void Colossus rifts/arena content unloaded; stale entry handlers also deny access while the feature is off. |
| `want_void_dungeon` | `true` for launch | Loads only the approved three-floor, boss-free dungeon. Feature-off recovery remains a tested rollback path. |
| `want_beta_onboarding_guide` | `false` for launch | Beta-only tester toolkit for teleports, stat presets, item kits, and FarmSim shortcuts. Keep enabled only during trusted beta windows. |
| `production_command_lockdown` | `true` for launch | Owner-only guard for item/NPC spawning, stat/account mutation, forced teleport/movement, server lifecycle, bot/load-test, event/world reset, and QA fixture commands. |
| `custom_landscape` | `true` | Enables custom patched terrain used by multiple systems. |
| `spawn_auction_npcs` | `true` if Auction House is live | Spawns the Void Auctioneer and gates marketplace access. |
| `want_world_announcements` | `true` for launch | Master presentation switch. Durable achievement claims remain controlled separately. |
| `want_world_milestone_announcements` | `true` for launch | Announces ordinary skill/total milestones plus committed first-skill and first-campaign-cracker records. |
| `want_world_skulled_pk_announcements` | `true` for launch | With achievements enabled, announces only committed qualified Wilderness kills and streaks `3/5/10`; the simpler legacy skulled-death message remains the disabled-achievements fallback. |
| `want_world_achievements` | `true` for launch | Enables durable first records and qualified-PK event/streak settlement. `false` preserves existing rows but creates no new season progression. |
| `world_achievement_season_id` | `launch-2026` | Stable lowercase season namespace. Changing it starts a separate empty season; launch has no backfill. |
| `world_pk_loot_minimum` | `5000` | Minimum exact killer-owned tradeable post-death loot value for qualified PK credit, using definition default prices. |
| `want_cracker_campaign` | `true` for launch | Loads the durable owner-controlled pool. Zero remains inactive; `::cracker N` is owner-only and audited. |
| `want_global_chat_country_flags` | `true` if global chat is live | Shows player-chosen country flags in global chat and lets players hide their own flag in settings. |
| `more_shafts_per_better_log` | `true` | Lets higher-tier logs feed the player-made arrow economy instead of every log producing the same 10 shafts. |
| Subscription cards | Always available in Voidscape | Tradable cards add 7 days and the small XP bump; linked characters share the time account-wide, while unlinked characters use character-local time. Lumbridge vendor claims paid cards, one reviewed launch card per cutover character, and older portal starter promises. |
| `want_custom_banks` | `false` for Void Glass launch on desktop, `/play`, and Android | Leave off for the shipped Void Glass bank. `true` is only for older legacy custom-bank presets when custom UI is off; loadouts do not require it. |
| `want_bank_presets` | `true` if shipping loadouts | Enables loadouts/presets for Void Glass and requires `bankpresets` schema. |
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
| Custom item ids | `1608` | Cowboy item `1609` is deferred; Void ashes remain the launch high-water mark. |
| Custom NPC ids | `868` | Void Archivist is current high-water mark. |
| Custom scenery ids | `1313` | Void market shelter is the launch high-water mark. |
| Custom client version | `10132` | Current coordinated release-source value. |

## Pre-release config sign-off

- [x] F2P vs members default decided: hybrid/P2P-enabled world with F2P-feeling early progression.
- [x] XP rates decided: 10x combat / 1.5x skills, subscription adds +1x to each.
- [ ] PvP/safe-zone policy decided.
- [ ] Economy-affecting QoL toggles decided: notes, auction house, bank presets, batch skilling.
- [x] Rift travel policy decided: ordinary Void Rifts form a five-hub network (Void Enclave, Edgeville, Varrock, Falador, Ardougne), with Lumbridge left to Home teleport and world-map autowalk/saved walks as the broad traversal QoL.
- [x] Launch command policy decided: enable `production_command_lockdown: true`; keep moderation/read-only staff support available and make high-risk staff/dev commands owner-only.
- [x] Production database selected: single-host SQLite + portal JSON for launch, contingent on a quiesced consistency backup/restore rehearsal.
- [x] Public host/ports selected: `voidscape.gg`, game TCP `43596`, WebSocket `43496` behind `wss://voidscape.gg/play/ws/`.
- [x] Client update/cache distribution path selected: desktop launcher manifest, hosted TeaVM package, Google Play production with signed direct-APK fallback.
- [ ] Discord/community links selected.
- [ ] AGPL source-disclosure path selected.
