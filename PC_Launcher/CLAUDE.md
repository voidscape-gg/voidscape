# PC_Launcher/ — Voidscape desktop launcher / auto-updater

Voidscape-branded desktop launcher: cache bootstrap, manifest auto-update (game files and the launcher itself), local client launch.

- Entry: `src/main/java/launcher/Main.java`.
- Build: Ant (Java 1.8). Output: `OpenRSC.jar`.
- Cache dir: defaults to `~/.voidscape/client` (CLI: `--dir`, `-d`; `--portable` uses `./Cache`).
- Visible UI: `launcher/Voidscape/VoidscapeLauncherWindow.java`.
- Updater: `launcher/Voidscape/VoidscapeUpdater.java` — syncs from the manifest (v2 `.properties`) on startup and on Play; SHA-256 verify, 3x retries, atomic moves; prunes files removed from the manifest (state: `<cache>/.voidscape-sync-state.properties`); offline fallback to verified local files; https-only for non-loopback hosts (`voidscape.allowInsecureHttp=true` overrides); launcher self-update stages `<cache>/launcher/VoidscapeLauncher-<sha8>.jar` and re-execs with the `--relaunched` loop guard. Local dev seeding from `Client_Base/` still applies when no manifest is configured.
- CLI: `--sync-only` headless sync for smokes/CI (prints `SYNC_*` markers, exit 0/1); `--no-update` disables sync; `--relaunched` internal.
- Version stamping: `build.xml` writes `Implementation-Version` into the jar manifest; Settings shows it as "Launcher build".
- Legacy OpenRSC updater/classes remain for compatibility but are not the visible startup flow.
- E2E smoke: `scripts/smoke-launcher-update.sh` (hermetic local channel).

For client architecture, see `docs/subsystems/client-cache.md`.
