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
  --discord-url https://discord.gg/example
```

The script emits `dist/friend-beta/VoidscapeLauncher.jar` for testers and `dist/friend-beta/update/` for static hosting. On first Play, the launcher downloads `Open_RSC_Client.jar` plus cache files from the manifest and writes the configured host/port into the runtime cache.

Configuration precedence is system properties, environment variables, sidecar `voidscape-launcher.properties`, then the bundled properties generated during packaging. Useful keys:

```properties
voidscape.serverHost=play.example.com
voidscape.serverPort=43596
voidscape.manifestUrl=https://play.example.com/voidscape/update/manifest.properties
voidscape.websiteUrl=
voidscape.portalUrl=
voidscape.discordUrl=https://discord.gg/example
```

The Account launcher icon opens the configured portal at `#dashboard`, leaving Google sign-in, account recovery, and the 10-character manager in the browser. The Play button still launches the classic client directly; browser-session-to-game-login handoff is intentionally deferred until the portal account model is production-ready.
