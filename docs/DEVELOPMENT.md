# Development

How to build, run, and reset voidscape on macOS. Voidscape preserves OpenRSC's tooling — voidscape adds standardized wrapper scripts in `scripts/` so we don't reinvent invocations.

## Build system inventory

OpenRSC's build is **Ant-driven**, with Gradle as an IDE convenience layer. The hierarchy:

| File | Role |
|---|---|
| `Makefile` (repo root) | Top-level orchestration — calls Ant targets. **The canonical entry point.** |
| `server/build.xml` | **Authoritative server build.** Targets: `compile_core`, `compile_plugins`, `runserver`, `runserverzgc`. Java 1.8 source/target. |
| `server/build.gradle` | Imports `build.xml` via `ant.importBuild()`. IDE support only — Ant runs the actual compile. |
| `server/gradlew` | Gradle wrapper. Not used in primary flow. |
| `Client_Base/build.xml` | PC client. Targets: `compile`, `runclient`, `compile-and-run`. |
| `PC_Client/build.xml` | Standalone PC client wrapper. |
| `PC_Launcher/build.xml` | Launcher app. |
| `Android_Client/build.gradle` | Android — Gradle (separate ecosystem). |
| `Deployment_Scripts/unmaintained/Makefile` | Old prod scripts. **Ignore.** |

**Verdict**: voidscape uses `Makefile` + Ant for everything except Android. Voidscape's `scripts/*.sh` wrap these.

## Java target and macOS Java 25 compatibility

**Targets**:
- Server, PC client, launcher: Java **1.8** (`source=1.8`, `target=1.8`).
- Android sources still target Java **1.8**, but the Android Gradle plugin must run on **JDK 17** locally.

**Local environment** (verified): `openjdk 25.0.2 2026-01-20` (Homebrew, arm64).

**Will it compile?** **Yes — verified on macOS arm64 + JDK 25.0.2 (Homebrew) at vendor SHA fc74d38e.** Build completes in ~5 seconds across server core, plugins, Client_Base, PC_Client, and PC_Launcher.

Cosmetic warnings to expect (all from upstream code, not voidscape):
- `[options] target value 8 is obsolete and will be removed in a future release` — harmless; switch to JDK 11/17 to silence.
- `[options] bootstrap class path is not set in conjunction with -source 8` — Ant's classpath model predates the module system. Harmless.
- Deprecated APIs in upstream code: `java.applet.Applet` (PC_Client), `URL(String)` constructor (PC_Launcher). Upstream issues, not blockers.

Recommendation: **JDK 11 or 17** for the Ant build. Android should use **JDK 17** through `scripts/build-android.sh`.

### Recommended: SDKMAN + Corretto 11

```bash
brew install sdkman-cli      # if not already installed
sdk install java 11.0.23-amzn
sdk use java 11.0.23-amzn    # sets for current shell
# Persistent default:
sdk default java 11.0.23-amzn
```

Verify:
```bash
java -version   # should show 11.x
ant -version    # should report Ant + JDK 11
```

### Alternatives

- **`jenv`**: `brew install jenv && jenv add /path/to/jdk11 && jenv local 11`
- **Direct Homebrew**: `brew install temurin@11 && export JAVA_HOME=$(/usr/libexec/java_home -v 11)`

### Use Java 25 anyway?

Possible but not recommended. You'd need to add `--release 8` to the javac calls in each `build.xml` — currently untested. If you go this route, document the change in `docs/DIVERGENCE.md`.

## Prerequisites

- **Java 11+** (Java 11 strongly recommended).
- **Apache Ant**: `brew install ant`.
- **SQLite**: built into macOS.
- **MariaDB / MySQL**: only if you want Docker-backed dev DB. Otherwise SQLite is fine.
- **Git**: present.

Verify:
```bash
java -version
ant -version
sqlite3 --version
```

## Canonical commands

Voidscape's wrappers in `scripts/` are the canonical entry points. Direct Ant/Make calls work too — the wrappers just standardize defaults and reduce drift.

### Voidscape wrappers (preferred)

```bash
scripts/build.sh              # compile server core + plugins + client
scripts/run-server.sh         # run server with the voidscape preset
scripts/run-client.sh         # run PC client against local server
scripts/build-android.sh      # build Android APK; requires Android SDK
scripts/reset-db.sh           # wipe + reseed dev DB
scripts/fetch-upstream-snapshot.sh   # recreate upstream/openrsc-snapshot/
```

### Direct Ant (when wrappers don't fit)

Clean build server:
```bash
cd server
ant clean compile_core compile_plugins
# Produces: core.jar, plugins.jar in server/
```

Run server with a specific preset:
```bash
cd server
ant runserver -DconfFile=preservation
# Or: ant runserver -DconfFile=local   (your customized local.conf)
```

Run with ZGC (Java 17+ only):
```bash
ant runserverzgc -DconfFile=local
```

Build PC client:
```bash
cd Client_Base
ant compile
```

Build Android client:
```bash
# Requires Android SDK via ANDROID_HOME, ANDROID_SDK_ROOT, Android_Client/local.properties,
# or Homebrew android-commandlinetools at /opt/homebrew/share/android-commandlinetools.
scripts/build-android.sh
```

Run PC client:
```bash
cd Client_Base
ant runclient
```

Compile plugins standalone:
```bash
cd server
ant compile_plugins
```

### Top-level Makefile

The repo root `Makefile` orchestrates these. Common targets:
- `make compile` — server + plugins + client
- `make run-server` — start server (default preset)
- `make run-client` — start client
- `make import-authentic-sqlite db=preservation` — load core schema + authentic data into SQLite
- `make import-custom-sqlite db=cabbage` — load with custom additions (auction house, clans, etc.)
- `make import-authentic-mariadb db=...` — same for MariaDB
- `make rank-sqlite` / `make rank-mysql` — adjust player ranks
- `make create-mariadb db=...` — create MariaDB DB

Inspect the `Makefile` directly for the full target list when in doubt.

## Server preset selection

Voidscape's working preset lives at `server/local.conf` (gitignored — copy from a template). The server picks `local.conf` when invoked with `-DconfFile=local`.

Setup:
```bash
# Copy a template — see docs/SERVER-PRESETS.md for which to choose
cp server/preservation.conf server/local.conf

# Edit for voidscape's QoL overrides
$EDITOR server/local.conf

# Run
cd server && ant runserver -DconfFile=local
```

See `docs/SERVER-PRESETS.md` for what each template represents and voidscape's recommended overrides.

## Database — SQLite (recommended for dev)

File-based. Zero setup. Default DB engine in `DatabaseType.java` is SQLite.

Setup:
```bash
make import-authentic-sqlite db=preservation
# Imports core RSC schema + data into server/inc/sqlite/preservation.db
```

Or with addons (auction house, clans, runecrafting, presets, …):
```bash
make import-custom-sqlite db=cabbage
```

Configure `server/connections.conf`:
```
db_type: sqlite
db_name: preservation
```

## Database — MariaDB via Docker

For multi-user testing or production simulation. **Not required for dev.**

The repo's `docker-compose.yml` defines a MariaDB-only stack (server + clients run on host).

```yaml
mariadb:
  image: mariadb:latest
  ports: 0.0.0.0:3306:3306
  volumes:
    - ./server/inc/databases:/var/lib/mysql
    - ./server/inc/my.cnf:/etc/mysql/my.cnf
```

Env vars come from `.env` (default scaffold creds — `root/root` for local only):
```
MARIADB_ROOT_USER=root
MARIADB_ROOT_PASSWORD=root
```

Bring up:
```bash
docker-compose up -d
make create-mariadb db=preservation
make import-authentic-mariadb db=preservation
```

Then in `server/connections.conf`:
```
db_type: mysql
db_host: localhost:3306
db_user: root
db_pass: root
```

**macOS arm64 caveat**: MariaDB's official image is amd64; Docker emulates it. Performance hit (~10-15% CPU overhead). Acceptable for dev. For real prod, deploy to Linux.

Stop with `docker-compose down` (preserves data in `server/inc/databases/`).

## Connecting client to server

Default port: 43594 (preservation/default presets). Cabbage uses 43595, 2001scape 43593, openpk 43597, etc.

If client and server use different ports, edit `Client_Base/Cache/port.txt`.

```bash
mkdir -p Client_Base/Cache
echo "127.0.0.1" > Client_Base/Cache/ip.txt
echo "43594"     > Client_Base/Cache/port.txt
cd Client_Base && ant runclient
```

## Reset / wipe

```bash
scripts/reset-db.sh           # voidscape default — wipes dev SQLite + reseeds
make import-authentic-sqlite db=preservation   # equivalent direct
```

To start completely fresh:
```bash
rm -rf server/inc/sqlite/*.db
make import-authentic-sqlite db=preservation
```

## Build outputs

| Project | Output | Location |
|---|---|---|
| Server core | `core.jar` | `server/` |
| Server plugins | `plugins.jar` | `server/` |
| PC client | `Open_RSC_Client.jar` | `Client_Base/` |
| Launcher | `OpenRSC.jar` | `PC_Launcher/` |
| Android | `voidscape.apk` | Gradle build dir |

## Run paths summary

```bash
# Build everything
scripts/build.sh

# Run server (current voidscape preset, normally via local.conf)
scripts/run-server.sh

# In another terminal, run client against local server
scripts/run-client.sh

# Reset DB to authentic preservation baseline
scripts/reset-db.sh
```

## macOS gotchas

1. **Java 25 module warnings.** Harmless but noisy. Switch to JDK 11 to silence.
2. **Ant classpath on arm64.** Rare; SQLite JDBC driver is platform-independent JAR — should work. If you hit native-lib issues, pin to a specific JDBC driver version.
3. **MariaDB Docker emulation.** ~10-15% CPU overhead on arm64. Use SQLite for daily dev.
4. **Client / server port mismatch silently fails.** "Unable to reach server" → check `Client_Base/Cache/port.txt` matches the preset's port (`server/<preset>.conf` has `server_port:`).
5. **`local.conf` not picked up.** Confirm you ran `ant runserver -DconfFile=local` and that `server/local.conf` exists. Restart fully — config isn't hot-reloaded.
6. **`pkill -f "java.*Server"`** to kill a stuck server. Port-bind retries fail if the previous process is still around.
7. **Memory tuning**: `server/build.xml` `runserver` target sets `-Xms800M -Xmx2048M`. Reduce to `-Xmx1024M` on small machines (edit the `<jvmarg>` line).
8. **ZGC needs Java 17+.** `ant runserverzgc` won't work on JDK 11. Use `ant runserver` (G1GC).
9. **DB engine mismatch**. Switching `connections.conf` from SQLite to MySQL without re-importing schema → boot failure. Always re-import after switching.

## Verifying a clean build

```bash
git status                  # confirm no surprise modifications
scripts/build.sh            # full clean build
scripts/run-server.sh       # boot — watch logs for errors
# Then in another terminal:
scripts/run-client.sh       # connect a client and walk around
```

If `scripts/build.sh` fails, check:
1. JDK version (`java -version` should be 11+).
2. `JAVA_HOME` pointing to the right JDK.
3. `ant -version` works.
4. `server/local.conf` exists if scripts default to using it.
