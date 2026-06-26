# Test iPhone Web Client Locally

Use this recipe when testing the current TeaVM iPhone client from a Mac and a physical iPhone on the same Wi-Fi. This is local QA only; production release still needs hosted HTTPS/WSS verification and the final release audit.

## 1. Start The Game Server

In terminal 1:

```bash
scripts/run-server.sh
```

Wait until the server has finished booting and the WebSocket port is listening. The iPhone QA runner checks port `43496` by default.

## 2. Start The iPhone Web Server

In terminal 2:

```bash
scripts/run-web-teavm-iphone-qa.sh --no-build --out tmp/iphone-web-qa
```

Leave this terminal open. The script prints the current Mac URL, iPhone URL, diagnostics URL, Home Screen URL, reset URLs, and writes:

```text
tmp/iphone-web-qa/iphone-safari-qa-report.md
```

If the printed iPhone URL uses `127.0.0.1`, do not test from the phone with that URL. Find this Mac's Wi-Fi/LAN IP in System Settings or with `ipconfig getifaddr en0`, then rerun:

```bash
scripts/run-web-teavm-iphone-qa.sh --no-build --lan-ip <mac-lan-ip> --out tmp/iphone-web-qa
```

If the TeaVM output is stale or missing, rebuild first:

```bash
scripts/build-web-teavm-spike.sh
scripts/run-web-teavm-iphone-qa.sh --no-build --out tmp/iphone-web-qa
```

## 3. Open On iPhone

On the iPhone:

- Join the same Wi-Fi as the Mac.
- Open the printed `iPhone diagnostics URL` in Safari.
- Confirm the Voidscape login screen appears.
- Tap Share, then Add to Home Screen.
- Launch Voidscape from the Home Screen icon.

The first diagnostics URL saves the local endpoint and diagnostics mode. The Home Screen launch should then use the cleaner printed `Home Screen expected URL after endpoint is saved`, usually:

```text
http://<mac-lan-ip>:8088/index.html?mobile=1
```

## 4. Smoke Test

Run through this minimum pass before reporting the build as usable:

- Login with the iPhone keyboard / `Aa` path.
- Copy blocking-dialog diagnostics before closing the welcome or wilderness modal.
- Close the modal.
- Tap-to-move.
- Tap the real canvas top tabs, then try Inventory, Map, Magic, Prayer, Skills, Quests, Friends, and Options.
- Tap the real canvas chat tabs for All, Chat, Quest, and Private, then use `Aa` to type/send chat.
- Send an in-game chat message, press browser Back once and confirm only the keyboard closes, then press browser Back again and confirm shared mobile Back behavior.
- Open the world map, pan, zoom, search, and tap a nearby destination; confirm the `...` and `Aa` helpers disappear while the map is open.
- Pinch zoom the world map in/out and confirm it does not start walking unless you intentionally tap a destination.
- With world-map search focused, press browser Back and confirm the search field closes while the map stays open.
- After a world-map route succeeds, keep the map open, copy diagnostics, and paste that JSON into the report's `World Map Diagnostics` section.
- Long-press or use the compact `...` context helper for a context action.
- Rotate portrait and landscape.
- Background the Home Screen app, return to it, then move or chat again.
- Open diagnostics with `i`, tap `copy`, and paste the final JSON into the QA report.

If Safari changes `copy` to `select`, direct clipboard access was blocked. Select the JSON field that appears and copy it manually.

## 5. Validate The Report

After filling device fields, checking the local smoke checklist, and pasting diagnostics JSON:

```bash
scripts/validate-web-teavm-iphone-qa-report.py \
  --allow-no-deployment-verification \
  tmp/iphone-web-qa/iphone-safari-qa-report.md
```

Do not use `--allow-no-deployment-verification` for production release reports. It exists only for local LAN testing before upload.

## Troubleshooting

- If the iPhone cannot open the URL, confirm both devices are on the same Wi-Fi and the Mac firewall allows ports `8088` and `43496`.
- If the printed iPhone URL contains `127.0.0.1`, rerun the QA helper with `--lan-ip <mac-lan-ip>`; localhost points at the iPhone itself, not the Mac.
- If the login screen opens but login cannot connect, confirm `scripts/run-server.sh` is still running and the QA runner printed `WebSocket: listening on TCP 43496`.
- If Home Screen launches the wrong environment, open the printed reset endpoint URL, then reopen the diagnostics URL.
- If account or recovery links go to stale tester URLs, open the printed reset portal URL.
- If the page reloads but controls look old, stop the QA runner, rebuild with `scripts/build-web-teavm-spike.sh`, and restart the QA runner.
