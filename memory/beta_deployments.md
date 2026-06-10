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

Verify after every beta deploy:
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/update/manifest.properties`
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/VoidscapeLauncher.jar`
- `nc -vz 5.161.114.251 43596`
- Tail the newest `server/logs/voidscape-runner-*.log` until DB patches finish and `Voidscape started` appears.
