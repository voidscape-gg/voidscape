---
name: Beta deployments
description: VPS beta deploy gotchas and verification points
type: reference
---

For the friend beta VPS, deploy server runtime and migrations together: `server/core.jar`, `server/plugins.jar`, `server/conf/`, and `server/database/`. Preserve live state (`server/inc/sqlite/*.db`, `server/logs/`, `server/avatars/`, `server/local.conf`), back up the DB first, and keep deployed `server/local.conf` `client_version` matched to `Client_Base/src/orsc/Config.java`.

Known beta VPS:
- SSH: `root@5.161.114.251` with `~/.ssh/voidscape_hetzner`.
- Web root: `/var/www/html/voidscape`.
- Game root: `/opt/voidscape`.
- Public update base: `https://voidscape.5.161.114.251.sslip.io/voidscape/update/`.
- Public game port: `43596`; websocket: `43496`.

Verify after every beta deploy:
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/update/manifest.properties`
- `curl -I https://voidscape.5.161.114.251.sslip.io/voidscape/VoidscapeLauncher.jar`
- `nc -vz 5.161.114.251 43596`
- Tail the newest `server/logs/voidscape-runner-*.log` until DB patches finish and `Voidscape started` appears.
