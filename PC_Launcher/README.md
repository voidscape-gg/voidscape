Voidscape desktop launcher and updater shell, hard-forked from the original OpenRSC PC launcher that was initially developed by Jaekr.

## Voidscape layered skin

The launcher composes the generated skin from separate background animation, logo, button-state, and icon assets in `src/main/resources/images/voidscape/layered/`. The transparent hit zones over those assets handle utility-icon hover glow, generated Play-button states, pressed feedback, click pulses, and real launcher actions.

Animated backgrounds are supported as GIF files. For quick local iteration:

```bash
VOIDSCAPE_BACKGROUND_ANIMATION=/absolute/path/to/background.gif scripts/run-launcher.sh
```

or place the file at `~/.voidscape/client/launcher/background.gif`. A bundled `src/main/resources/images/voidscape/layered/background.gif` also works when we want to ship one in the jar. The static `background.png` remains the fallback.

## Friend beta packaging

For a hosted friend beta, package a launcher that already knows the game server endpoint and update manifest:

```bash
scripts/package-friend-beta.sh \
  --host play.example.com \
  --port 43596 \
  --base-url https://play.example.com/voidscape/update \
  --discord-url https://discord.gg/example \
  --discord-application-id 123456789012345678
```

The script emits `dist/friend-beta/VoidscapeLauncher.jar` for testers and `dist/friend-beta/update/` for static hosting. The launcher syncs `Open_RSC_Client.jar` plus cache files from the manifest on startup and before Play, and writes the configured host/port into the runtime cache. The update folder also carries the launcher jar itself at `launcher/VoidscapeLauncher.jar` (referenced by the manifest `launcher.*` keys), so handed-out jars self-update.

Configuration precedence is system properties, environment variables, sidecar `voidscape-launcher.properties`, then the bundled properties generated during packaging. If `voidscape.manifestUrl` is omitted but `voidscape.portalUrl` is set, the launcher uses `<portalUrl>/api/launcher/manifest.properties`. Useful keys:

```properties
voidscape.serverHost=play.example.com
voidscape.serverPort=43596
voidscape.manifestUrl=https://play.example.com/voidscape/update/manifest.properties
voidscape.websiteUrl=https://voidscape.gg
voidscape.portalUrl=https://voidscape.gg
voidscape.discordUrl=https://discord.gg/example
voidscape.discordApplicationId=123456789012345678
voidscape.discordLargeImageKey=voidscape_logo
voidscape.discordLargeImageText=Voidscape
```

Discord rich presence uses the Discord application name for the activity title.
Create a Discord developer application named `Voidscape`, upload a large image
asset matching `voidscape.discordLargeImageKey`, then set
`voidscape.discordApplicationId` so testers do not publish the inherited
OpenRSC application in their status.

The portal API can also host a live manifest at `/api/launcher/manifest.properties`. That manifest points at `/downloads/client-runtime`, `/downloads/cache/...`, and `/downloads/launcher` for self-update, hashes every file with SHA-256, and is checked automatically on startup and on Play. Static beta packaging still works for a tiny no-portal deployment; portal-hosted updates are the normal path once the account site is deployed.

## Auto-update

The launcher syncs from the update manifest on startup and again on Play; when the update server is unreachable, Play falls back to the already-verified local files. Downloads retry three times with SHA-256 verification and an atomic move, aggregate byte progress comes from `file.N.size`, files removed from the manifest are pruned, and runtime files (credentials, `ip.txt`/`port.txt`, settings) are never touched. Verified-file state is tracked in `<cache>/.voidscape-sync-state.properties`; delete it to force a full re-verify. Plain-http manifests/downloads are refused for non-loopback hosts unless `voidscape.allowInsecureHttp=true`.

Manifest keys consumed (v2 `.properties`; every v2 key is optional and v1 manifests still work): `version`, `baseUrl`, `file.N.path` / `file.N.sha256` / `file.N.url` / `file.N.size`, `clientVersion`, and the self-update entry `launcher.sha256` / `launcher.url` / `launcher.size` / `launcher.version`.

When `launcher.sha256` is present and differs from the running jar, the launcher downloads the new build to `<cache>/launcher/VoidscapeLauncher-<sha8>.jar`, verifies it, and re-execs into it with `--relaunched`, which suppresses further chaining so a bad manifest can never relaunch in a loop. The originally downloaded jar stays untouched and keeps working as a bootstrap: restarting the launcher delivers both new client files and new launcher builds. Play never restarts the window; a self-update found during Play is staged and applies on the next launcher start.

CLI flags:

- `--dir <path>`, `-d <path>` — cache directory (default `~/.voidscape/client`).
- `--portable` — use `./Cache` next to the working directory.
- `--no-update`, `-n` — disable all update checks (game files and launcher).
- `--sync-only` — headless prepare + manifest sync without a window, for smokes/CI. Prints `SYNC_STATUS` progress lines plus `SYNC_OUTCOME` (`updated` / `up-to-date` / `offline` / `no-manifest` / `failed`), `SYNC_VERSION`, `SYNC_CLIENT_VERSION`, `SYNC_DOWNLOADED`, `SYNC_LAUNCHER_STAGED` (when a self-update was staged), and a final `SYNC_RESULT ok|failed`; exits 0 on success, 1 on failure.
- `--relaunched` — internal; passed by the previous launcher when chaining into a self-updated jar.

End-to-end regression: `scripts/smoke-launcher-update.sh` drives `--sync-only` against a hermetic local channel through fresh install, idempotency, corruption repair, prune, self-update staging plus the loop guard, offline fallback, and the plain-http refusal.

The Account launcher icon opens the configured portal at `#account`, which is safe for the public beta landing mode and still lands on the account surface when private portal routes are enabled. The Play button still launches the classic client directly; browser-session-to-game-login handoff is intentionally deferred until the portal account model is production-ready.
