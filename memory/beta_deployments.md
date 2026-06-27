---
name: Beta deployments
description: VPS beta deploy gotchas and verification points
type: reference
---

For the friend beta VPS, deploy server runtime and migrations together: `server/core.jar`, `server/plugins.jar`, `server/conf/`, and `server/database/`. Preserve live state (`server/inc/sqlite/*.db`, `server/logs/`, `server/avatars/`, `server/local.conf`), back up the DB first, and keep deployed `server/local.conf` `client_version` matched to `Client_Base/src/orsc/Config.java`.

Client-only updates (HUD/render/window, no protocol change): just rebuild + `scripts/package-friend-beta.sh --skip-build --host 5.161.114.251 --port 43596 --base-url <update base>`, then `rsync -az` the generated `dist/friend-beta/update/` to `…:/var/www/html/voidscape/update/` (no `--delete`; upload `manifest.properties` last). No `CLIENT_VERSION` bump or server redeploy. Cross-check the served jar SHA-256 == local as the authoritative confirmation. Do NOT use `Deployment_Scripts/deploy-openrsc-client.sh` (legacy, wrong target + MD5).

The voidscape HUD only renders when the server tells the client custom UI is ON, which needs TWO things on the server (config flip alone is NOT enough):
1. `server/local.conf` `want_custom_ui: true` (and `want_custom_banks: true`). Config isn't hot-reloaded; restart after changing.
2. The `core.jar` must include the `Player.getCustomUI()` default-true fix (commit e2af6a8). The pre-e2af6a8 jar returns FALSE for accounts with no `custom_ui` cache even when `want_custom_ui` is true, so every tester gets the CLASSIC UI. A client-only deploy does NOT fix this — you must rebuild + deploy `server/core.jar` (+`plugins.jar`) and restart. Verify the deployed jar: `unzip -p core.jar .../Player.class | javap -c` and check the getCustomUI no-cache branch is `iconst_1`, not `iconst_0`. (The beta shipped a 2026-06-07 19:06 jar built before the 21:29 fix — that was the real "HUD not showing" cause.)
Server jar/DB deploy: `systemctl stop voidscape`; back up `inc/sqlite/voidscape.db` and the old jars; `rsync -az server/core.jar server/plugins.jar root@…:/opt/voidscape/server/` (do NOT overwrite `conf/` — it holds the VPS `want_custom_ui` + endpoints — or `database/`); `systemctl start voidscape`; watch journald for "Database patches" + "Voidscape started".

Known beta VPS:
- SSH: `root@5.161.114.251` with `~/.ssh/voidscape_hetzner`.
- Web root: `/var/www/html/voidscape`.
- Game root: `/opt/voidscape`.
- Server runs as systemd `voidscape.service` (SQLite at `/opt/voidscape/server/inc/sqlite/voidscape.db`). Restart: `systemctl restart voidscape`; boot logs go to journald (`journalctl -u voidscape`), not the (stale) runner `.log` files. Boot is ~3s; look for `Voidscape started`.
- Public update base: `https://voidscape.5.161.114.251.sslip.io/voidscape/update/`.
- Public game port: `43596`; websocket: `43496`.

Current public deploy map as of 2026-06-27:
- Host: `voidscape.gg` / `5.161.114.251` (`voidscape-beta-ash-1`), SSH `root@voidscape.gg -i ~/.ssh/voidscape_hetzner`.
- Game/source root: `/opt/voidscape`; active config: `/opt/voidscape/server/local.conf`; jars: `/opt/voidscape/server/core.jar` and `/opt/voidscape/server/plugins.jar`; DB: `/opt/voidscape/server/inc/sqlite/voidscape.db`.
- Portal: `/opt/voidscape/web/portal`; env: `/etc/voidscape/portal.env`; data: `/var/lib/voidscape-portal`; service: `voidscape-portal.service`; backend listens on `127.0.0.1:8788`.
- Prelaunch promise env: keep `PORTAL_SIGNUP_IP_DAILY_LIMIT` and `PORTAL_STARTER_IP_DAILY_LIMIT` aligned (live set to `10` on 2026-06-27) so every accepted prelaunch account gets the advertised starter subscription card unless public copy is changed to mention review holds.
- Nginx root: `/var/www/html`; web client: `/var/www/html/play`; downloads: `/var/www/html/voidscape`; launcher: `/var/www/html/voidscape/VoidscapeLauncher.jar`; Android APK: `/var/www/html/voidscape/Voidscape-Android-Beta.apk`; launcher update root: `/var/www/html/voidscape/update`.
- Services/ports: `voidscape.service` on TCP `43596`, WebSocket `43496` proxied at `wss://voidscape.gg/play/ws/`, `nginx.service` on `80/443`, integrity timer/service `voidscape-integrity-export`.
- Before launch deploys, back up active jars, `server/local.conf`, SQLite DB, `/etc/voidscape/portal.env`, portal root, `/var/www/html/play`, and `/var/www/html/voidscape`. Preserve live DB/env secrets and patch config keys instead of wholesale replacing `local.conf` unless deliberate.

Verify after every beta deploy:
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/update/manifest.properties`
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/VoidscapeLauncher.jar`
- `nc -vz 5.161.114.251 43596`
- Tail the newest `server/logs/voidscape-runner-*.log` until DB patches finish and `Voidscape started` appears.
