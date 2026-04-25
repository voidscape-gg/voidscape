# PC_Launcher/ — desktop launcher / auto-updater

Splash + auto-updater + multi-world picker. Voidscape rarely touches this.

- Entry: `src/main/java/launcher/Main.java`.
- Build: Ant (Java 1.8). Output: `OpenRSC.jar`.
- Cache dir: defaults to `./Cache` (CLI: `--dir`, `-d`).
- Auto-updater: `launcher/Gameupdater/ClientUpdater.java` (MD5 verification).
- Version stamping: `launcher/Utils/Defaults.java` `_CURRENT_VERSION` set at build time.

For client architecture, see `docs/subsystems/client-cache.md`.
