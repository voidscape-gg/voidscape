# PC_Launcher/ — Voidscape desktop launcher / auto-updater

Voidscape-branded desktop launcher, cache bootstrap, local client launch, and updater foundation.

- Entry: `src/main/java/launcher/Main.java`.
- Build: Ant (Java 1.8). Output: `OpenRSC.jar`.
- Cache dir: defaults to `~/.voidscape/client` (CLI: `--dir`, `-d`; `--portable` uses `./Cache`).
- Visible UI: `launcher/Voidscape/VoidscapeLauncherWindow.java`.
- Cache/updater: `launcher/Voidscape/VoidscapeUpdater.java` (local dev seeding + SHA-256 manifest foundation).
- Legacy OpenRSC updater/classes remain for compatibility but are not the visible startup flow.
- Version stamping: `launcher/Utils/Defaults.java` `_CURRENT_VERSION` set at build time.

For client architecture, see `docs/subsystems/client-cache.md`.
