# AI Workbench

The AI workbench is a dev-only PC client helper for visual iteration and local automation. It is disabled by default and only starts when the client is launched with `-Dvoidscape.workbench=true`, normally through `scripts/run-workbench-client.sh`.

## Entry Points

- `scripts/run-workbench-client.sh` mirrors `scripts/run-client.sh`, writes `Client_Base/Cache/ip.txt` and `port.txt`, then runs the PC client with workbench JVM properties.
- `PC_Client/src/orsc/OpenRSC.java` starts the workbench after the desktop client window and applet are initialized.
- `PC_Client/src/orsc/WorkbenchServer.java` owns the loopback HTTP server, JSON state snapshots, and screenshot file writes.
- `PC_Client/src/orsc/ORSCApplet.java` exposes a synchronized copy of the current rendered game frame and routes the backtick screenshot hotkey through the same capture path.

## Local HTTP Endpoints

Default base URL: `http://127.0.0.1:18787`.

- `GET /health` returns a minimal readiness payload with `ok`, `clientVersion`, `port`, and `generatedAt`.
- `GET /state` returns a JSON snapshot of client state: game dimensions/state, camera rotation/angle/zoom, mouse state, local player basics when logged in, and selected interface state for the Auction House, world map, in-canvas Subscription Shop, native shop window, bank, bestiary, Christmas-cracker reel, and launch cracker-campaign HUD. `interfaces.crackerCampaignHud` exposes remaining count, visibility, exact label, plaque bounds, top-tab bounds, location-plaque bounds, and kill-feed base Y. `interfaces.subscriptionShop` exposes visibility, item `1602`, profile/rate context, the real rendered SHOP-tab center, current top-overlay name, and the Buy, Close, and X centers. When a Void Rift is visible, `game.voidRiftTarget` exposes its projected `screenX`/`screenY` center plus world tile so the real scene picker can be exercised without guessed coordinates.
- `GET /screenshot` saves an exact PNG copy of the current game frame plus a JSON sidecar under `tmp/workbench/screenshots/`, then returns the saved paths and image dimensions.
- `GET /captures/latest` returns the most recent saved capture path and metadata.
- `POST /input/click` accepts `{"x":301,"y":147,"button":"left"}` in unscaled game-frame coordinates and sends a synthetic click through the applet mouse handler.
- `POST /input/drag` accepts `{"fromX":0,"fromY":0,"toX":10,"toY":10,"button":"left","steps":8,"durationMs":450}` and runs a synthetic drag through the applet mouse handler.
- `POST /input/scroll` accepts `{"x":779,"y":193,"amount":4}` in unscaled game-frame coordinates and runs the shared client scroll path at that mouse position.
- `POST /input/key` accepts `{"key":"ENTER"}` or `{"char":"a"}` and sends a synthetic key press through the applet key handler.
- `POST /input/type` accepts `{"text":"hello"}` and types each character through the key handler.
- `POST /input/command` accepts `{"command":"quickauction"}` or `{"command":"::quickauction"}` and sends the existing client command packet.
- `POST /input/npc-action` accepts `{"action":"talk"}` (or `command1`/`command2`, aliases `op1`/`op2`) plus an NPC selector field and sends the matching NPC talk/command packet for the nearest matching visible NPC.
- `POST /dev/ready` uses the saved Existing User credentials to reach an in-game state, waits for the local player to appear, then clears blocking welcome/server-message dialogs and hides dev-iteration panels such as the Auction House and world map. If the server has already opened the one-time character-design modal, this dev-only preflight submits the currently displayed choices through the real appearance packet path before continuing; the helper is gated on that modal being visible and is never called by production client flow.
- `POST /dev/ui-panel` accepts `{"panel":"inventory"}` and opens a named Voidscape HUD/menu state, optionally capturing it. Supported visible panel keys include `hud`, `subscription-shop`, `options-profile`, `options-settings`, `friends`, `ignore`, `magic`, `prayers`, `skills`, `quests`, `loot`, `bestiary`, `minimap`, `inventory`, and `account`. Opening `subscription-shop` captures the in-game catalog without activating its external secure-checkout handoff.
- `POST /dev/viewport` accepts `{"index":0}` and applies a numbered desktop viewport scale preset via `ScaledWindow`.
- `POST /dev/world-reskin` accepts `{"mode":"auto"}`, `{"mode":"authentic"}`, or `{"mode":"void"}`. It updates the client-only world reskin flag, reloads the current region, and reports whether the void profile is active. Add `{"capture":"true"}` to save a screenshot in the same response.
- `POST /dev/reload-entity-sprites` reloads entity sprites through the same client path used at startup, useful after repacking candidate frames while the workbench client is still open.
- `POST /fixture/auction-house` requires the logged-in player to be an admin, dispatches `::workbenchauctionfixture`, and reseeds deterministic Auction House listings plus recent sales through the server DB layer.
- `POST /fixture/christmas-cracker` opens the production Christmas-cracker reel initializer with a validated deterministic `outcome`, `itemId`, `seed`, and named phase (`opening`, `fast`, `near-stop`, or `reveal`); optional `capture:true` saves the exact frame.
- `POST /fixture/cracker-campaign-hud` requires a logged-in, non-blocked game view and accepts `remaining` from `0` through `1,000,000`; optional `capture:true` saves the frame. It drives the shared HUD state locally without a server award or packet and validates the current layout/label before returning structured state.
- `POST /scenario/auction-house-open` opens the Auction House, captures Browse, first-listing selection, My Auctions, Intel, and Food category states, then returns a report with all saved image paths.
- `POST /scenario/christmas-cracker-roll` captures all four named reel phases and fails unless the near-stop still shows a near miss and the final center card exactly matches the authoritative result.
- `POST /scenario/cracker-campaign-hud` requires a logged-in game view, clears supported blocking UI, and captures `1,000`, `999`, `1`, and hidden `0` at desktop viewport presets `4` (`1024x768`) and `5` (`512x346`). It fails on wrong comma/singular grammar, positive/zero visibility, out-of-frame plaque bounds, top-tab or location-plaque overlap, a kill-feed baseline not below the plaque, a capture count other than eight, or failure to restore the original viewport index and prior remaining value in cleanup.
- `POST /scenario/ui-panels` runs `/dev/ready`, opens the visible core Voidscape menu panels one by one, and saves screenshots for the UI concept-art pipeline.
- `POST /scenario/subscription-shop-ui` clicks the real rendered SHOP tab and fails unless the catalog opens, outside clicks leave the player tile unchanged, wheel input leaves camera zoom unchanged, Close and Escape both hide the catalog, the same rendered tab reopens it, and a higher-priority server message replaces it on the next frame while remaining the visible top modal. The response includes open/server-message captures and reports finally-style cleanup of both modal states. It never activates Buy or an external checkout.
- `POST /scenario/subscription-vendor-claim` requires either `launch_subcard_2026:<playerId> = 1` globally or a logged-in account whose save has `web_account_id` plus a matching `starter_card:<webAccountId> = 1` global marker. It teleports to the Lumbridge Void Subscription Vendor, sends the real client NPC command packet for `Subscribe`, verifies no vendor shop opens, and captures before/after screenshots. When both exist, the character launch marker is claimed first.
- `POST /scenario/subscription-card-redeem` requires a logged-in account with item `1602` in inventory, sends the real inventory item command packet for `Redeem`, waits for the card to be consumed, and captures before/after screenshots.
- `POST /scenario/void-dungeon-maps` requires a logged-in admin. It performs the same already-open-only appearance preflight as `/dev/ready`, records the original player tile, uses the existing `::tele` client-command channel to visit the three generated dungeon arrivals, and captures each live in-game minimap. From each minimap it clicks the rendered `World Map` button, asserts automatic F1/F2/F3 selection and a loaded plane image, then clicks and captures the real `Surface`, `F1`, `F2`, and `F3` map tabs. The structured response includes every assertion and PNG/state sidecar path. A finally-style cleanup closes the map and side panel and restores the exact original tile even when a scenario assertion fails.

The server binds to loopback only. It does not touch server packets, opcodes, player saves, DB schema, cache versioning, or gameplay behavior. Fixture seeding uses an admin-only server command and marks rows as `wb-fixture`/`wb-buyer` so they can be cleared and reseeded safely.

For the Void Dungeon map gate, run `tests/workbench-void-dungeon-maps-unit.sh`, start the client against a QA server with `WANT_VOID_DUNGEON=true`, call `POST /dev/ready`, then call `POST /scenario/void-dungeon-maps`. Review all ten returned PNGs individually: three minimaps, three automatically selected floor maps, and the four manual floor-tab states. The response is not a substitute for reviewing the images; its final `cleanup` object must also report that the location was restored and both UI layers were closed.

For the cracker-campaign HUD gate, run `tests/workbench-cracker-campaign-hud-unit.sh`, call `POST /dev/ready`, then call `POST /scenario/cracker-campaign-hud`. The passing eight-capture report is `tmp/workbench-cracker-campaign-4c/scenario-2.json`. Preset `5` is a compact PC frame used to exercise shared responsive layout; it is not a native Android device. The coordinated source version is now `10132`; Android portrait/landscape and TeaVM receipt/rendering remain separate Slice 8 platform gates.

## Configuration

The wrapper script accepts optional environment overrides:

```bash
VOIDSCAPE_WORKBENCH_PORT=18788 scripts/run-workbench-client.sh
VOIDSCAPE_WORKBENCH_DIR=/tmp/voidscape-workbench scripts/run-workbench-client.sh
```

When the workbench is disabled, pressing backtick still saves an exact client-frame screenshot to the default workbench directory. This replaces the earlier macOS full-screen `screencapture` hook and makes screenshots independent of OS window placement.

## Current Boundaries

The workbench currently supports capture, state, synthetic local input, command dispatch, dev-ready session setup, current-region world-reskin toggles, entity sprite reloads, Auction House and Christmas-cracker fixtures/scenarios, cracker-campaign HUD fixture/scenario, Void Dungeon map QA, Lumbridge Subscription Vendor claim/redeem scenarios, and screenshots around those flows. It deliberately has no reusable scenario DSL or new server packet. Workbench scenarios operate the PC client and cannot substitute for native Android or hosted TeaVM proof. Deferred appearance/Paperdoll tooling is not part of the launch RC.
