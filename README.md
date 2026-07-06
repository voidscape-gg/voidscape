# Voidscape

Voidscape is a private RuneScape Classic server focused on an authentic classic feel with quality-of-life improvements, prelaunch account tools, and small custom content.

The project is AGPLv3 licensed. Public source disclosure for distributed builds is available at:

https://github.com/voidscape-gg/voidscape

## Quick Start

Use the wrapper scripts in `scripts/`; they encode the supported local workflow.

```bash
scripts/build.sh
scripts/run-server.sh
scripts/run-client.sh
```

For a clean local database:

```bash
scripts/reset-db.sh
```

For detailed setup, Java version notes, Android builds, web client packaging, and operations recipes, start with:

- `docs/DEVELOPMENT.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEMAP.md`
- `docs/CONFIG-MATRIX.md`
- `docs/OPERATIONS.md`
- `docs/RELEASE-CHECKLIST.md`

## Project Layout

- `server/` - game server, plugins, static definitions, and persistence code.
- `Client_Base/` and `PC_Client/` - shared classic client and desktop client.
- `Android_Client/` - Android wrapper/client build.
- `Web_Client_TeaVM/` - browser/mobile web client target.
- `PC_Launcher/` - Voidscape desktop launcher and updater.
- `web/portal/` - public landing, account portal, downloads, and trust pages.
- `docs/` - architecture, subsystem notes, recipes, release and operations guides.
- `scripts/` - canonical build, run, package, smoke, and deployment entry points.

## Build And Test

The normal validation gate is:

```bash
scripts/build.sh
```

For game interaction tests, use `tools/voidbot/voidbot` instead of manual clicking. The full bot protocol is documented in `docs/bot-api.md`.

For client UI verification, use the AI workbench route documented in `docs/subsystems/ai-workbench.md`.

## Support And Security

For support, account, privacy, or security issues, email:

support@voidscape.gg

Please include enough detail to reproduce the issue, and mark security-sensitive reports clearly in the subject line.

## License

Voidscape is distributed under the GNU Affero General Public License v3.0 or later. See `LICENSE`.
