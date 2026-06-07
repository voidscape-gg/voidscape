# Android Emulation QA

- `voidscape_atd35` is reliable for headless log-driven Android smoke assertions, but `screencap` output is black in this environment.
- `voidscape_api35` (Google APIs) produces usable screenshots, but long headless runs can hit System UI/Google-service ANRs.
- Use ATD for repeatable input/action proofs; use a display-capable emulator/browser-visible session for visual QA screenshots.
- DB fixtures inserted after server boot should use high `itemstatuses.itemID` values; the server caches max item ID at startup.
- `voidscape_api35` also ANRed during repeated headless credential entry; prefer real devices for release-grade visuals.
- Voidscape currently has `want_equipment_tab=false`; Android equipment/worn-item smoke should assert inventory wield/remove (`equipped=true/false`) unless that server feature is intentionally re-enabled and fixed.
- Android magic/prayer list taps can be consumed before `drawUiTabMagic`; `InputImpl.recordAndroidTap(...)` plus the panel-local tap consumer is the intended fix. The focused smoke validates Home teleport selection/self-cast UI and Thick Skin on/off, but treats the saved-position Home teleport effect as a warning because the server-side self-cast teleport path still needs separate protocol/mechanics investigation.
- Android world-map PNGs must decode through `mudclient.clientPort.getSpriteFromByteArray(...)` as a fallback because `javax.imageio.ImageIO` is absent on Android; `PngSpriteLoader` alone makes the map show "image missing".
- Focused world-map smoke should clear the auth fixture's `player_cache` key `tutorial_appearance` when `ANDROID_SMOKE_AUTH_DB` is set; otherwise first-login character creation can sit over the game and make screenshots misleading.
- `--only-auth-world-map` verifies open/zoom/pan/search/Back-close through log assertions on ATD and readable visual frames on `voidscape_api35`; final known-good visual output was `/tmp/voidscape_android_world_map_api35_v5`.
- `--only-auth-settings` snapshots/restores `cameraauto/onemouse/soundoff`, toggles camera/mouse through packet 111, asserts DB persistence, and relogs; the settings smoke must park the mouse inside the options panel or the classic tab code can close it before render.
- `--only-auth-ground-loot` snapshots/restores inventory plus loot-display cache keys, seeds item 93, drops it through the smoke-only `G` key path, asserts `DROP_KEY`, `BEAM`, and `LABEL`, and captures readable proof such as `/tmp/voidscape_android_ground_loot_api35_v7/102-auth-ground-loot-readable.png`.
- Direct `am start` of `GameActivity` is invalid because only `ApplicationUpdater` is exported; Android smoke should launch through the wrapper/launcher flow.
- During Android smoke login, avoid `KEYCODE_BACK` after credentials and use hardware Enter to submit because the software keyboard can cover the `Ok` button.
- Use `scripts/android-smoke.sh --no-build --only-auth-login --out /tmp/voidscape-android-login` as the first Android auth diagnostic. It defaults to `AndroidMap/androidmap1` in `server/inc/sqlite/voidscape.db`, verifies the local password hash, checks the launcher selected `10.0.2.2:43596`, waits for `login response:64`, catches immediate Android runtime crashes, then force-stops so post-login relaunch bugs do not masquerade as bad credentials.
