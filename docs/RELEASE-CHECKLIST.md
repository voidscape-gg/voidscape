# Release checklist

Use this before any player-facing test, public weekend, or real launch. The goal is not ceremony; it is catching avoidable drift before players do.

## Release target

- [ ] Release name / tag:
- [ ] Target audience: private test / friends test / public beta / public launch
- [ ] Server preset: local / staging / production
- [ ] Database backend: SQLite / MariaDB
- [ ] Client version:
- [ ] Build commit:
- [ ] Operator:

## Git and workspace

- [ ] `git fetch --prune origin`
- [ ] `git status -sb` shows no accidental runtime files.
- [ ] `git rev-list --left-right --count HEAD...origin/main` is `0 0`, unless intentionally releasing from a branch.
- [ ] All release changes are committed.
- [ ] `docs/DIVERGENCE.md` has entries for non-trivial Voidscape behavior changes.
- [ ] New player commands are reflected in `Commands.md`.
- [ ] New content recipes or subsystem lessons were added to `docs/recipes/` or `docs/subsystems/` when reusable.
- [ ] No secrets are in the tree: `git grep -n "sk-|DISCORD_BOT_TOKEN|password|webhook"` reviewed.

## Build

- [ ] `scripts/build.sh` passes.
- [ ] Android, if shipping APK: `scripts/build-android.sh` passes on JDK 17 with a configured Android SDK.
- [ ] Generated client/server cache assets are intentionally committed when they are part of the release.
- [ ] Build artifacts remain untracked: `server/*.jar`, `Client_Base/Open_RSC_Client.jar`, `PC_Launcher/OpenRSC.jar`.

## Config

- [ ] `server/local.conf` or production config matches `docs/CONFIG-MATRIX.md`.
- [ ] `Client_Base/src/orsc/Config.java` `CLIENT_VERSION` matches server `client_version`.
- [ ] `enforce_custom_client_version: true` is enabled for any non-local release.
- [ ] `server_port` / `ws_server_port` are the intended public ports.
- [ ] `server_name`, welcome text, and Discord/community URLs are correct.
- [ ] `member_world` is intentional and documented.
- [ ] XP rates and hit-XP timing flags are intentional and documented.
- [ ] `restrict_item_id` allows the current custom item range.
- [ ] `want_pcap_logging` is off unless actively debugging packet capture.
- [ ] Any local-only convenience flags are removed or documented as deliberate launch behavior.

## Database

- [ ] Fresh DB import path is tested: `scripts/reset-db.sh` for dev, or the production import procedure in `docs/OPERATIONS.md`.
- [ ] Required addon tables exist: bank presets, auction house, item status fields, and any active feature tables.
- [ ] Migrations are idempotent or safe to apply once.
- [ ] Backup taken before migration or public test.
- [ ] Account creation and login tested on a clean player.
- [ ] Existing-account login tested after migration.
- [ ] Player save/logout/relogin tested.

## Server smoke test

- [ ] `scripts/run-server.sh` boots without fatal errors.
- [ ] Server reports the expected TCP and WebSocket ports.
- [ ] Entity definitions load without JSON/XML errors.
- [ ] Plugin handler loads without missing critical content.
- [ ] PERF telemetry, if enabled, shows zero skipped ticks at idle.
- [ ] `scripts/perf-smoke.sh` passes when a logged-in local client is available.

## Client smoke test

- [ ] `scripts/run-client.sh` connects to the intended host/port.
- [ ] Login screen renders Voidscape branding correctly.
- [ ] New-account appearance flow works.
- [ ] Existing account login works.
- [ ] Basic movement, chat, inventory, bank, shop, combat, and logout work.
- [ ] Wrench/profile/advanced settings open and persist.
- [ ] World map opens, pans/zooms, and autowalk route returns.
- [ ] Rare-drop beams and ground-item labels respect settings.
- [ ] Custom bank opens, searches, deposits, withdraws, and loadouts save/load.

## Content smoke test

- [ ] Void Island starter path can be chosen once, grants the matching starter kit once, and shows the post-choice welcome box.
- [ ] Home teleport works and respects wilderness restrictions.
- [ ] Edgeville Void Rift teleports to the Void Enclave.
- [ ] Void Enclave safe zone blocks PvP and amenities work: bank, altar, healing pool, waystones, store.
- [ ] Void chest requires a Void Key and rolls rewards.
- [ ] Auction House opens, lists, buys, cancels, collects, and handles expiry.
- [ ] Death Match Arena starts, completes, rewards, and cleans up after logout/death.
- [ ] Player titles command opens and active title appears in chat.
- [ ] Karamja Fishmonger cooks and notes supported fish.
- [ ] Lumbridge Subscription Vendor grants one reserved starter subscription card per linked account, does not open a shop, and redeemed cards apply the intended account-wide XP rates.
- [ ] PK Catching Simulator starts, scores, `::leave` exits, and highscores persist.
- [ ] Void Rush starts with bots, eliminates players, rewards one winner, and cleans up.
- [ ] Dragon sword components drop/source/assemble as intended.
- [ ] Custom items render in inventory and when wielded.

## Performance

- [ ] `scripts/perf-watch.sh` shows clean telemetry during local smoke.
- [ ] Synthetic load test run at the expected scale: `scripts/perf-load.sh <count> <duration> <radius> <intervalTicks>`.
- [ ] No repeatable skipped ticks or sustained tick `p95` over the threshold chosen for the test.
- [ ] Logs do not show save queue, SQL queue, or packet queue pressure under expected load.

## Community and launch surface

- [ ] Discord access gate listener is running if using the announcement gate.
- [ ] `docs/community/discord-server-setup.md` permissions still match the live server.
- [ ] Prelaunch portal signup is backed by production persistence, creates linked game characters, and disables/redirects client packet registration.
- [ ] Public links, invite URLs, support instructions, and rules are current.
- [ ] AGPL source-disclosure plan is ready before any public distribution.

## Rollback

- [ ] Previous known-good commit/tag identified.
- [ ] Database backup path verified.
- [ ] Cache/client rollback path verified.
- [ ] Discord/community announcement draft ready if a public test is interrupted.

## Sign-off

- [ ] Build verified.
- [ ] Server verified.
- [ ] Client verified.
- [ ] Content smoke verified.
- [ ] Backup verified.
- [ ] Release notes posted or queued.
