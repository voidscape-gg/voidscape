# Operations

This is the practical runbook for operating Voidscape outside a one-off coding session. Development details live in `docs/DEVELOPMENT.md`; this file focuses on keeping a running world healthy.

## Operating modes

| Mode | Intended use | Database | Notes |
|---|---|---|---|
| Local dev | Daily development and quick smoke tests | SQLite | Use `scripts/*.sh`; okay to reset often. |
| Staging | Friends-only validation before a player-facing test | SQLite or MariaDB | Should use release-like config and backups. |
| Production | Public or semi-public world | MariaDB recommended | Needs backup, monitoring, update, and rollback discipline. |

SQLite is fine for local work and small private testing. Move to MariaDB before real public load, multi-operator work, or anything where file-copy backups are too informal.

## Build and run

Canonical commands:

```bash
scripts/build.sh
scripts/run-server.sh
scripts/run-client.sh
```

`scripts/run-server.sh` always runs `server/local.conf`. For staging or production, keep the actual deployed config outside Git, but keep its intended values mirrored in `docs/CONFIG-MATRIX.md`.

Useful local stop pattern:

```bash
SERVER_PID=$(lsof -tiTCP:43596 -sTCP:LISTEN || true)
[ -n "$SERVER_PID" ] && kill "$SERVER_PID"
```

Avoid broad `pkill -f openrsc` patterns for the server. The running command line is usually generic Java/Ant text and may not contain `openrsc`.

## Friends-only hosted beta

For a private beta, the intended player flow is one hosted game server plus a preconfigured launcher. Friends should not need the website or any manual client configuration.

Server host:

- Run `scripts/build.sh`, then `scripts/run-server.sh` on the VPS or dedicated host.
- Open the configured TCP `server_port` in the host firewall and provider firewall. Voidscape local config currently uses `43596`.
- Keep `ws_server_port` closed unless a browser/web client is intentionally being tested.
- Back up the database before inviting testers and before every update.

Launcher/update packaging from a build machine:

```bash
scripts/package-friend-beta.sh \
  --host <server-host-or-ip> \
  --port 43596 \
  --base-url https://<server-host-or-ip>/voidscape/update \
  --discord-url <optional-discord-invite>
```

Upload or sync `dist/friend-beta/update/` to the URL used as `--base-url`, then share only `dist/friend-beta/VoidscapeLauncher.jar` with testers. The launcher embeds the game endpoint and manifest URL, downloads the PC client/cache on first Play, writes `ip.txt`/`port.txt` into its runtime cache, and launches the client.

External smoke checks:

```bash
curl -I https://<server-host-or-ip>/voidscape/update/manifest.properties
nc -vz <server-host-or-ip> 43596
```

During the friend beta, newly created characters are temporarily created as Admins automatically so testers can use the command checklist immediately. Existing characters can still be changed with `make rank-sqlite` / `make rank-mysql` or in-game `::setrank` from an owner/admin account. Revert the auto-admin default before any public release.

## Config files

| File | Purpose | Git status |
|---|---|---|
| `server/local.conf` | Active local server preset | Ignored |
| `server/connections.conf` | DB backend and credentials | Tracked scaffold; review before deploy |
| `Client_Base/src/orsc/Config.java` | Client compile-time flags and `CLIENT_VERSION` | Tracked |
| `Client_Base/Cache/ip.txt`, `port.txt` | Per-developer client target | Ignored |
| `Client_Base/Cache/hideIp.txt` | Per-developer client UI preference | Ignored |

When client-visible definitions, packets, cache files, or login expectations change, bump `CLIENT_VERSION` in `Client_Base/src/orsc/Config.java` and set the matching server `client_version`.

## Database operations

### Local SQLite reset

```bash
scripts/reset-db.sh
```

This is destructive and intended for development.

### SQLite backup

Stop the server first or use SQLite's backup command:

```bash
sqlite3 server/inc/sqlite/voidscape.db ".backup 'Backups/voidscape-$(date +%Y%m%d-%H%M%S).sqlite'"
```

Do not commit backup files.

### MariaDB backup

Use the real database name and credentials for the deployed environment:

```bash
mysqldump -h <host> -u <user> -p <database> > "Backups/voidscape-$(date +%Y%m%d-%H%M%S).sql"
```

Restore only after stopping the game server:

```bash
mysql -h <host> -u <user> -p <database> < Backups/<backup>.sql
```

### Migrations

Server boot runs `JDBCPatchApplier`. Before a player-facing update:

- Back up the DB.
- Boot staging once against a copy.
- Confirm login, save, logout, and relogin.
- Confirm feature tables that came from addons or patches exist.

## Logs and telemetry

Server logs live under `server/logs/` and should remain untracked.

Performance helpers:

```bash
scripts/perf-watch.sh
scripts/perf-smoke.sh
scripts/perf-load.sh 50 130 18 2
```

Watch first for:

- skipped ticks
- sustained high tick `p95`
- save or SQL queue pressure
- packet queue pressure
- repeated plugin or definition errors

Packet capture is expensive and noisy. Keep `want_pcap_logging: false` unless actively debugging networking.

## Client and cache updates

PC client:

```bash
scripts/build.sh
```

The build produces `Client_Base/Open_RSC_Client.jar`, which is a build artifact and should stay untracked.

Android client:

```bash
scripts/build-android.sh
```

Android requires a local SDK and JDK 17. See `docs/DEVELOPMENT.md`.

Cache-backed changes need extra care:

- Client and server `Custom_Landscape.orsc` copies must both be regenerated when the landscape patcher changes.
- World-map overlays should be re-baked after custom map changes.
- `Client_Base/Cache/MD5.SUM` should be updated only when intentionally shipping cache changes.
- Client-visible definition changes usually require `CLIENT_VERSION` bumps.

## Discord operations

Community setup docs:

- `docs/community/discord-server-setup.md`
- `docs/community/discord-setup-bot.md`

Access gate:

```bash
scripts/discord-access-gate.js --setup
scripts/discord-access-gate.js --serve
```

The live listener must be running for the `Enter Voidscape` button to grant the member role. Tokens must come from the environment or keychain, never from committed files.

## Release procedure

1. Finish and commit the code/content slice.
2. Run `docs/RELEASE-CHECKLIST.md`.
3. Build with `scripts/build.sh`.
4. Back up the database.
5. Deploy server/client/cache artifacts.
6. Boot server and watch logs.
7. Smoke-test with a real client.
8. Post release notes or a test announcement.

## Rollback procedure

1. Stop the server.
2. Restore the previous known-good code/cache/client artifacts.
3. Restore the DB backup if the failed release applied migrations or corrupted state.
4. Boot server.
5. Log in with a test account.
6. Announce status if players were affected.

Prefer a boring rollback over trying to patch live state while players are online.

## Pre-public readiness gaps

Before inviting real public traffic, settle these:

- Production config values in `docs/CONFIG-MATRIX.md`.
- MariaDB backup/restore rehearsal.
- AGPL source-disclosure plan.
- Account moderation/support workflow.
- Basic uptime monitoring.
- Crash/restart policy.
- Rules and appeals process.
- Abuse policy for automation, multi-logging, RWT, bug exploitation, and Discord conduct.
